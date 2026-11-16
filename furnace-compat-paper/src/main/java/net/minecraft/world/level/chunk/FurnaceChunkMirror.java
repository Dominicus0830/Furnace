package net.minecraft.world.level.chunk;

import org.domi.furnace.compat.i18n.CompatLang;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.domi.furnace.api.internal.ChunkInternals;
import org.domi.furnace.api.world.World;
import org.domi.furnace.compat.nms.NmsChunks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 청크 shim 의 거울(N2b-4, 설계 3.2~3.4) — 호환 모듈 내부 객체(플러그인에 보이지 않는다). 청크 shim 하나에 하나.
 * <p>플러그인 스레드는 거울(섹션 배열·섹션·컨테이너의 진짜 필드)과 주인이 낸 공개 스냅샷만 만진다(기다리지 않음). 주인 region 스레드가 맞춤 지점마다
 * {@link #sync()} 로 바뀐 부분을 서버 청크에 들여온다:
 * <ol>
 *   <li><b>칸 일지</b>(결정 D1): 주인이 아닌 스레드의 메서드 쓰기({@code LevelChunkSection#setBlockState}·컨테이너 {@code set}·바이옴 칸·
 *       {@code LevelChunk#setBlockState}·하이트맵·{@code setUnsaved})는 순서대로 쌓고 주인이 그 칸만 재생한다(바닐라 원시 의미 — 그 사이 게임이 바꾼 다른 칸은 그대로).
 *       쓴 스레드는 자기 쓰기를 덧칠로 곧바로 읽는다.</li>
 *   <li><b>훑기</b>(정식 경로): 배열 칸 객체, 섹션의 {@code biomes}, 컨테이너의 {@code data} 를 기준(placed)과 정체성으로, 개수 필드 셋을 값으로 비교해
 *       바뀐 구성 요소만 들여온다({@code ChunkInternals#importSectionNow} — 저장 표시·빛·하이트맵 재계산·패킷 없음). 장벽(VarHandle·Field 알림)은 지연을 줄일 뿐이다.</li>
 * </ol>
 * 재생이 훑기보다 먼저다 — 배열에서 빠진 옛 섹션에 쓴 칸은 들여온 새 섹션이 덮는다(Paper 에서도 교체된 섹션에 쓴 것은 남지 않는다). 계획 뒤 떼어 내기 전에
 * 옛 섹션에 쓴 칸도 떼어 내기 직전에 먼저 재생해 옛 객체에 남긴다(일지 줄마다 쓴 객체를 단다 — PR #396 검토 R2).
 * 들여온 플러그인 섹션·컨테이너는 그 자리의 보기로 붙이고(정체성 유지 — FAWE 의 다음 CAS 기대값), 빠진 옛 보기는 떼어 낸다(그 뒤 쓰기는 월드에 닿지 않는다).
 * 들여올 떨어진 컨테이너는 계획 때 잡는다 — 그 뒤 그 컨테이너의 바닐라 칸 쓰기는 잠금 아래에서 해, 읽기·들여오기·공개·붙이기(잠금 한 번)에 끼지 못한다(검토 R1·R3).
 * <p>잠금: Furnace 전용 {@link #lock} 하나 — 플러그인이 볼 수 있는 객체(청크·섹션·컨테이너)의 모니터는 쓰지 않는다(설계 R12, FAWE 는 그것들에 synchronized 한다).
 * 잠금 안에서 다른 스레드를 기다리지 않는다(주인은 잠금 안에서 자기 서버 청크만 고친다 — 들여오기·재생·공개).
 */
final class FurnaceChunkMirror implements ChunkInternals.ChunkMirror {

    private static final Logger LOG = LoggerFactory.getLogger("Furnace");
    private static final VarHandle SECTIONS = MethodHandles.arrayElementVarHandle(LevelChunkSection[].class);

    /** 장벽 알림용: 섹션 배열 → 거울(약한 키·약한 값 — 거울이 배열을 강하게 쥐므로 값도 약하게). */
    private static final Map<LevelChunkSection[], WeakReference<FurnaceChunkMirror>> ARRAYS = Collections.synchronizedMap(new WeakHashMap<>());

    private static final int SECTION_SET = 0, CONTAINER_SET = 1, BIOME_SET = 2, CHUNK_SET = 3, HM_RAW = 4, HM_UPDATE = 5, UNSAVED = 6;
    /** 블록 엔티티 일지(T-198): 지우기·만들기(IMMEDIATE)·싣기(loadWithComponents)·setChanged. target 은 그 보기, extra 는 실을 NBT. */
    private static final int BE_REMOVE = 7, BE_CREATE = 8, BE_LOAD = 9, BE_CHANGED = 10;
    /** 블록 엔티티 덧칠의 "지웠다" 표시. */
    static final Object BE_REMOVED = new Object();

    /**
     * 일지 한 줄. target 은 쓴 객체(섹션 쓰기·청크 쓰기 = 그때 그 자리 섹션, 컨테이너·바이옴 쓰기 = 그 컨테이너, 그 밖 null) — 재생 전에 그 객체가 배열에서
     * 빠지면 떼어 내기 직전에 먼저 재생한다(검토 R2). seq 는 덧칠 순번.
     */
    private record Op(int kind, int slot, int cell, int id, int x, int y, int z, boolean flag, Heightmap.Types type, long[] raw, Object target, long seq, Object extra) {}

    final ChunkAccess chunk;
    final World world;
    final int cx, cz;
    /** Furnace 전용 잠금(플러그인에 보이지 않음). */
    final Object lock = new Object();
    private final LevelChunkSection[] arr;
    private final int n;

    // ---- 기준(placed): Furnace 가 마지막으로 놓은 것 — 주인이 lock 아래에서 고친다
    private final LevelChunkSection[] placed;
    private final Object[] placedBiomes;
    /** placed[i] 의 개수 필드에 Furnace 가 놓은 값(3 × n). 다른 스레드의 일지 ±1 도 함께 옮겨 플러그인 대입으로 오인하지 않게 한다. */
    private final int[] expected;

    // ---- 일지·덧칠(lock)
    private ArrayList<Op> journal = new ArrayList<>();
    private long seq;
    /** 주인: 재생한 마지막 순번 — 공개 뒤 이 순번 이하의 덧칠을 지운다. */
    private long replayedSeq;
    private int[][] cellOver;
    private long[][] cellOverSeq;
    private int[][] biomeOver;
    private long[][] biomeOverSeq;
    /** 덧칠 칸 수(0 이면 읽기가 잠그지 않는다). */
    private volatile int overlayCount;
    /** 블록 엔티티 덧칠(T-198): 칸(BlockPos.asLong) → 다른 스레드가 만든 보기 또는 {@link #BE_REMOVED}. 재생돼 공개되면 지운다. */
    private java.util.HashMap<Long, Object> beOver;
    private java.util.HashMap<Long, Long> beOverSeq;
    private volatile int beOverlayCount;
    private long lastCountsVersion = Long.MIN_VALUE;

    /** 들여오지 않은 쓰기가 있다(일지·장벽 알림). 세운 쪽이 주인에게 부탁한다. */
    private volatile boolean dirty;
    /** 이 로드가 끝났다(내렸거나 부탁이 거절됨) — 쓰기는 월드에 닿지 않는다. */
    private volatile boolean dead;
    /** 주인: 맞추는 중. */
    private boolean syncing;
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    FurnaceChunkMirror(ChunkAccess chunk, LevelChunkSection[] arr) {
        this.chunk = chunk;
        this.world = chunk.furnaceWorld();
        this.cx = chunk.getPos().x;
        this.cz = chunk.getPos().z;
        this.arr = arr;
        this.n = arr.length;
        this.placed = arr.clone();
        this.placedBiomes = new Object[n];
        this.expected = new int[3 * n];
        for (int i = 0; i < n; i++) {
            placedBiomes[i] = arr[i].furnaceBiomesField();
            arr[i].furnaceReadCountFields(expected, 3 * i);
        }
        ARRAYS.put(arr, new WeakReference<>(this));
    }

    // ------------------------------------------------------------------ 장벽 알림(아무 스레드)

    /** 쓰기 장벽: 이 배열이 거울의 섹션 배열이면 알린다(VarHandle 배열 칸 쓰기 뒤, {@code NmsArrays}). */
    static void arrayWritten(Object[] array) {
        if (!(array instanceof LevelChunkSection[] a)) return;
        WeakReference<FurnaceChunkMirror> r = ARRAYS.get(a);
        FurnaceChunkMirror m = r == null ? null : r.get();
        if (m != null) m.noteWrite();
    }

    /** 플러그인 쓰기가 생겼다 — 주인에게 맞추기를 부탁한다(이미 부탁했으면 아무것도 안 함). */
    void noteWrite() {
        if (dead || dirty) return;
        dirty = true;
        request();
    }

    private void request() {
        try {
            if (!NmsChunks.bridge().requestMirrorSync(world, cx, cz, this)) dead = true;   // 내린 청크·옛 로드의 거울 — 쓰기는 월드에 닿지 않는다
        } catch (RuntimeException e) {
            warnOnce("request", CompatLang.t("compat.mirror.request-failed", e));
        }
    }

    boolean dead() { return dead; }

    boolean owner() { return NmsChunks.bridge().ownsChunk(world, cx, cz); }

    // ------------------------------------------------------------------ 주인 스레드의 맞춤 지점(호환 모듈)

    /** 주인: 그 섹션 자리가 바뀌었거나 일지가 있으면 맞춘다(그 칸을 읽고 쓰기 전). 비용은 정체성 비교 몇 개. */
    void ownerSync(int slot) {
        if (dead || syncing) return;
        if (dirty || slotChanged(slot)) syncFromCompat();
    }

    /** 주인: 일지가 있으면 맞춘다(하이트맵·블록 엔티티 읽기 전). */
    void ownerSyncIfDirty() {
        if (!dead && !syncing && dirty) syncFromCompat();
    }

    /** 주인: 다 훑어 맞춘다(setUnsaved 등 바닐라 의미가 있는 맞춤 지점). */
    void ownerSyncAll() {
        if (!dead && !syncing) syncFromCompat();
    }

    private void syncFromCompat() {
        if (sync()) request();   // 맞추는 동안 들어온 쓰기 — 서버 대기 표시가 이미 내려갔을 수 있어 다시 부탁한다
    }

    /** 주인: 자리 i 가 기준과 다른가(잠금 없이 — 기준은 주인만 고친다). */
    private boolean slotChanged(int i) {
        if (i < 0 || i >= n) return false;
        LevelChunkSection cur = (LevelChunkSection) SECTIONS.getAcquire(arr, i);
        if (cur != placed[i]) return true;
        if (cur == null) return false;
        if (cur.furnaceChunk() != chunk) return cur.furnaceChunk() == null;   // 제자리에서 떨어진 섹션 — 다시 붙인다
        if (cur.furnaceIndex() != i) return false;
        PalettedContainer<BlockState> st = cur.getStates();
        if (st.furnaceData() != st.furnacePlaced()) return true;
        Object bf = cur.furnaceBiomesField();
        if (bf != placedBiomes[i]) return true;
        if (bf instanceof PalettedContainer<?> pb && pb.furnaceAttached() && pb.furnaceData() != pb.furnacePlaced()) return true;
        return cur.furnaceCountFieldsDiffer(expected, 3 * i);
    }

    // ------------------------------------------------------------------ ChunkMirror(서버가 주인 스레드에서 부른다)

    /** 한 자리의 들여오기 계획(잠금 안에서 정한다 — 일지를 떼어 내는 순간과 같은 순간). counts·expectedAtPlan 은 섹션을 그대로 둔 개수 대입에만. */
    private record Plan(int slot, LevelChunkSection was, LevelChunkSection cur, boolean replace, boolean states, boolean biomeField, boolean biomeData,
                        int[] counts, int[] expectedAtPlan) {}

    /** 들여온 한 자리(잠금 안): 읽은 data(그것이 기준이 된다 — 공급원에서 읽었으면 null)·바이옴 컨테이너·개수. */
    private record Imported(Plan plan, Object statesData, Object biomes, Object biomesData, int[] counts) {}

    /**
     * 시험 이음매(O15 허용): 맞추기가 일지를 떼어 내고 계획한 뒤(잠금 밖, 재생 뒤) 들여오기 잠금 전에 주인 스레드에서 부른다 — 그 틈에 다른 스레드의 쓰기를
     * 결정적으로 끼워 넣는 시험용(검토 R2·R3·M3). 운영에서는 늘 null(읽기 하나).
     */
    static volatile java.util.function.Consumer<ChunkAccess> testAfterPlan;

    /** 이번 맞추기가 잡은 떨어진 컨테이너(붙이지 못한 것은 끝에 놓는다). 주인 전용. */
    private final ArrayList<PalettedContainer<?>> claimed = new ArrayList<>();

    /**
     * 서버가 주인 스레드에서 부른다. 다른 스레드에서 부르면 아무것도 안 한다({@code ChunkAccess#furnaceMirror()} 는 공개라 플러그인도 부를 수 있다).
     * <ol>
     *   <li>잠금 안: 일지를 떼어 내고 훑기 계획을 정한다(들여올 떨어진 컨테이너를 잡는다 — 그 뒤 바닐라 길 쓰기는 잠금 아래).</li>
     *   <li>잠금 밖: 일지 재생(그 칸만, 바닐라 원시 의미).</li>
     *   <li>잠금 한 번에: 자리마다 옛 보기 떼어 내기(계획 뒤 그 객체 앞으로 든 쓰기는 먼저 재생) → 새 내용 읽기 → 들여오기, 다 들여온 뒤 새 판 공개 →
     *       그 판으로 붙이기. 읽기부터 붙이기까지 잡힌 컨테이너 쓰기가 끼지 못하고(검토 R3), 붙은 보기를 읽는 다른 스레드는 늘 들여온 내용을 본다(검토 R1).</li>
     * </ol>
     */
    @Override
    public boolean sync() {
        if (dead || !owner() || syncing) return false;
        syncing = true;
        try {
            dirty = false;   // 먼저 — 이 뒤의 쓰기는 다시 세운다
            ArrayList<Op> ops;
            long upTo;
            List<Plan> plans;
            synchronized (lock) {   // 일지 떼어 내기와 훑기 계획을 한 순간에(개수 필드 ±1 이 둘 중 한 곳에만 든다)
                ops = journal;
                journal = new ArrayList<>();
                upTo = seq;
                plans = plan();
            }
            // 재생이 먼저 — 그 뒤 들여오는 섹션이 덮는다(교체 전 옛 섹션에 쓴 칸은 남지 않는다). 블록 엔티티 만들기가 아직 들여오지 않은 섹션의 블록을
            // 기다리면 그 칸의 블록 엔티티 일지를 들여오기 뒤로 미룬다(T-198 — 플러그인 섹션에 놓인 상자에 IMMEDIATE)
            ArrayList<Op> deferred = null;
            java.util.Set<Long> deferredPos = null;
            for (Op op : ops) {
                if (op.kind >= BE_REMOVE && deferredPos != null && deferredPos.contains(beKey(op))) {
                    deferred.add(op);
                } else if (!replay(op)) {
                    if (deferred == null) {
                        deferred = new ArrayList<>();
                        deferredPos = new java.util.HashSet<>();
                    }
                    deferred.add(op);
                    deferredPos.add(beKey(op));
                }
            }
            if (!ops.isEmpty()) replayedSeq = upTo;
            var seam = testAfterPlan;
            if (seam != null) seam.accept(chunk);   // 시험 이음매(O15) — 운영에서는 null
            if (plans.isEmpty()) {
                replayDeferred(deferred);
                return dirty;
            }
            ChunkInternals.ChunkView view = null;
            synchronized (lock) {
                try {
                    List<Imported> done = new ArrayList<>(plans.size());
                    for (Plan p : plans) {
                        if (dead) break;
                        Imported d = execute(p);
                        if (d != null) done.add(d);
                    }
                    if (!done.isEmpty() && !dead) {
                        // 붙이기 전에 새 판을 낸다 — 붙은 보기를 다른 스레드가 읽는 순간 들여온 내용이 보이게(그 전에는 그 객체 자신의 data, 검토 R1)
                        var b = NmsChunks.bridge();
                        view = b.publishChunkViewNow(world, cx, cz);
                        long stamp = view != null ? view.contentStamp() : b.contentStampNow(world, cx, cz);
                        for (Imported d : done) attach(d, stamp);
                    }
                } finally {
                    for (PalettedContainer<?> c : claimed) if (!c.furnaceAttached() && c.furnaceClaimant() == this) c.furnaceClaim(null);
                    claimed.clear();
                }
            }
            replayDeferred(deferred);
            if (view != null) published(view);
            return dirty;
        } finally {
            syncing = false;
        }
    }

    private static long beKey(Op op) { return net.minecraft.core.BlockPos.asLong(op.x, op.y, op.z); }

    /** 주인: 들여오기 뒤로 미룬 블록 엔티티 일지(만들 블록이 들여온 섹션에 있었다). 그래도 블록 엔티티를 만들 수 없으면 그 보기는 지워진 것이 된다. */
    private void replayDeferred(ArrayList<Op> deferred) {
        if (deferred == null) return;
        for (Op op : deferred) {
            if (!replay(op) && op.target instanceof net.minecraft.world.level.block.entity.BlockEntity v) v.setRemoved();
        }
    }

    /** 잠금 안: 자리마다 바뀐 구성 요소. 들여올 떨어진 컨테이너는 이때 잡는다(그 뒤 그 컨테이너의 바닐라 쓰기는 이 잠금 아래). */
    private List<Plan> plan() {
        List<Plan> out = null;
        for (int i = 0; i < n; i++) {
            LevelChunkSection cur = (LevelChunkSection) SECTIONS.getAcquire(arr, i);
            LevelChunkSection was = placed[i];
            Plan p = null;
            if (cur == null) {
                if (was != null) warnOnce("null:" + i, CompatLang.t("compat.mirror.null-section", i));
                continue;
            }
            if (cur != was || cur.furnaceChunk() == null) {
                p = new Plan(i, was, cur, true, true, true, false, null, null);
                claim(cur.getStates());
                claim(cur.furnaceBiomesField());
            } else if (cur.furnaceChunk() == chunk && cur.furnaceIndex() == i) {
                PalettedContainer<BlockState> st = cur.getStates();
                boolean states = st.furnaceData() != st.furnacePlaced();
                Object bf = cur.furnaceBiomesField();
                boolean biomeField = bf != placedBiomes[i];
                boolean biomeData = !biomeField && bf instanceof PalettedContainer<?> pb && pb.furnaceAttached() && pb.furnaceData() != pb.furnacePlaced();
                if (biomeField) claim(bf);
                int[] counts = null;
                if (cur.furnaceCountFieldsDiffer(expected, 3 * i)) {
                    counts = new int[3];
                    cur.furnaceReadCountFields(counts, 0);
                }
                if (states || biomeField || biomeData || counts != null) {
                    int[] e = counts == null ? null : java.util.Arrays.copyOfRange(expected, 3 * i, 3 * i + 3);
                    p = new Plan(i, was, cur, false, states, biomeField, biomeData, counts, e);
                }
            }
            if (p != null) {
                if (out == null) out = new ArrayList<>();
                out.add(p);
            }
        }
        return out == null ? List.of() : out;
    }

    /** 잠금 안: 들여올 떨어진 컨테이너를 잡는다(붙은 보기·다른 거울이 잡은 것은 그대로). */
    private void claim(Object o) {
        if (o instanceof PalettedContainer<?> c && c.furnaceClaimant() == null) {
            c.furnaceClaim(this);
            claimed.add(c);
        }
    }

    /**
     * 주인, 잠금 안: 계획 하나를 들여온다(붙이기는 공개 뒤 {@link #attach}). 빠진 옛 보기를 먼저 그 순간(옛) 내용으로 굳혀 떼어 낸다 — 그 뒤 쓰기는 월드에
     * 닿지 않고, 다시 넣으면 옛 내용이 들어간다(Paper 그대로). 플러그인 내용이 잘못됐으면(팔레트 null·레지스트리에 없는 값) 들여오지 않고 한 번 경고한다.
     */
    private Imported execute(Plan p) {
        int i = p.slot;
        LevelChunkSection cur = p.cur;
        if (p.replace && p.was != null && p.was.furnaceChunk() == chunk && p.was.furnaceIndex() == i) {
            Object wb = p.was.furnaceBiomesField();
            replayTargeted(p.was, p.was.getStates(), wb instanceof PalettedContainer<?> c && p.was.furnaceOwnsBiomeSource(c) ? c : null);
            p.was.furnaceDetach();   // 배열에서 빠진 옛 섹션(바이옴 컨테이너를 새 섹션이 나눠 쥐면 아래에서 다시 잡아 붙인다 — FAWE)
        } else if (p.biomeField && placedBiomes[i] instanceof PalettedContainer<?> oc && cur.furnaceOwnsBiomeSource(oc)) {
            replayTargeted(null, null, oc);
            oc.furnaceDetach();   // biomes 필드에서 빠진 옛 바이옴 컨테이너
        }
        int[] states = null, biomes = null, counts = p.counts;
        Object[] sd = new Object[1], bd = new Object[1];
        Object bc = null;
        try {
            if (p.states) states = cur.getStates().furnaceReadIds(BlockState::furnaceStateId, sd);
            if (p.replace || p.biomeField || p.biomeData) {
                bc = cur.furnaceBiomesField();
                claim(bc);   // 떼어 낸 옛 섹션과 나눠 쥔 것(FAWE)이면 방금 떨어졌다 — 다시 잡는다
                biomes = readBiomes(bc, bd);
            }
            if (p.replace) {
                counts = new int[3];
                cur.furnaceReadCountFields(counts, 0);   // 잡힌 섹션은 일지가 없다 — 지금 필드가 그 섹션의 개수(Paper)
            }
        } catch (RuntimeException e) {
            warnOnce("invalid:" + i + ":" + e.getMessage(), CompatLang.t("compat.mirror.invalid-section", i, e.getMessage()));
            return null;
        }
        if (!NmsChunks.bridge().importSectionNow(world, cx, i, cz, states, biomes, counts)) {
            dead = true;   // 그 사이 내려감
            return null;
        }
        return new Imported(p, sd[0], bc, bd[0], counts);
    }

    /** 주인, 잠금 안, 공개 뒤: 들여온 객체를 그 자리의 보기로 붙이고 기준을 옮긴다. */
    private void attach(Imported d, long stamp) {
        Plan p = d.plan;
        int i = p.slot;
        LevelChunkSection cur = p.cur;
        if (p.replace) {
            placed[i] = cur;
            placedBiomes[i] = d.biomes;   // 들여온 바이옴 컨테이너 — 그 사이 필드가 바뀌었으면 다음 맞추기가 들여온다
            if (cur.furnaceChunk() == null) cur.furnaceAttach(chunk, i, d.statesData, d.biomes, d.biomesData, stamp);   // 그 자리의 보기로(정체성 유지). 다른 곳에 붙은 섹션은 내용만
            System.arraycopy(d.counts, 0, expected, 3 * i, 3);
            return;
        }
        if (p.states && d.statesData != null) cur.getStates().furnaceMarkPlaced(d.statesData, stamp);
        if (p.biomeField) {
            placedBiomes[i] = d.biomes;
            if (d.biomes instanceof PalettedContainer<?> nb && !nb.furnaceAttached()) cur.furnaceAttachBiomes(nb, d.biomesData, stamp);
        } else if (p.biomeData && d.biomes instanceof PalettedContainer<?> pb && d.biomesData != null && pb.furnaceAttached()) {
            pb.furnaceMarkPlaced(d.biomesData, stamp);
        }
        if (p.counts != null) {
            // 계획 뒤 일지 ±1 은 필드와 기준에 함께 더해졌고 서버에는 재생으로 더해진다 — 들여온 대입량만큼만 기준을 옮긴다(필드 - 기준 이 0 이 되게)
            for (int k = 0; k < 3; k++) expected[3 * i + k] += p.counts[k] - p.expectedAtPlan[k];
        }
    }

    /**
     * 주인, 잠금 안, 떼어 내기 직전: 계획 뒤 그 객체(섹션·블록 컨테이너·바이옴 컨테이너) 앞으로 일지에 든 쓰기를 지금(옛 내용) 서버에 재생하고 일지·덧칠에서 뺀다 —
     * 떼어 낸 객체에 남고 들여오는 새 내용이 그 자리를 덮는다(Paper: 교체 전 옛 섹션에 쓴 것. 검토 R2 — 다음 맞추기에서 새 섹션 위에 재생되지 않게).
     */
    private void replayTargeted(Object section, Object states, Object biomes) {
        if (journal.isEmpty()) return;
        ArrayList<Op> keep = null;
        for (int k = 0; k < journal.size(); k++) {
            Op op = journal.get(k);
            Object t = op.target;
            if (t != null && (t == section || t == states || t == biomes)) {
                if (keep == null) keep = new ArrayList<>(journal.subList(0, k));
                replay(op);
                dropOverlay(op);
            } else if (keep != null) {
                keep.add(op);
            }
        }
        if (keep != null) journal = keep;
    }

    /** 잠금 안: 그 일지 줄의 덧칠을 지운다(뒤에 같은 칸을 덮은 쓰기가 없을 때만). */
    private void dropOverlay(Op op) {
        if (op.slot < 0) return;
        if (op.kind == BIOME_SET) {
            if (biomeOver != null && biomeOver[op.slot] != null && biomeOver[op.slot][op.cell] >= 0 && biomeOverSeq[op.slot][op.cell] == op.seq) {
                biomeOver[op.slot][op.cell] = -1;
                overlayCount--;
            }
        } else if (cellOver != null && cellOver[op.slot] != null && cellOver[op.slot][op.cell] >= 0 && cellOverSeq[op.slot][op.cell] == op.seq) {
            cellOver[op.slot][op.cell] = -1;
            overlayCount--;
        }
    }

    @SuppressWarnings("unchecked")
    private static int[] readBiomes(Object container, Object[] dataOut) {
        if (container instanceof PalettedContainer<?> pc) {
            return ((PalettedContainer<Holder<Biome>>) pc).furnaceReadIds(NmsChunks.BIOME_IDS::getId, dataOut);
        }
        if (!(container instanceof PalettedContainerRO<?> ro)) throw new IllegalStateException("biomes is null");
        int[] out = new int[64];
        for (int i = 0; i < 64; i++) {
            Object v = ro.get(i & 3, i >> 4, i >> 2 & 3);
            int id = v instanceof Holder<?> h ? NmsChunks.BIOME_IDS.getId((Holder<Biome>) h) : -1;
            if (id < 0) throw new IllegalStateException("value missing from the server biome registry: " + v + " (slot " + i + ")");
            out[i] = id;
        }
        return out;
    }

    /**
     * 주인: 일지 한 줄 재생(바닐라 원시 의미). 실패는 한 번 경고하고 다음 줄로. 블록 엔티티 만들기가 그 칸 블록에 블록 엔티티가 없어 만들지 못했으면 false
     * (들여오기 뒤에 다시 — {@link #sync}).
     */
    private boolean replay(Op op) {
        var b = NmsChunks.bridge();
        try {
            switch (op.kind) {
                case BE_CREATE -> {
                    // 그 스레드가 본 블록(자기가 바꾼 섹션 포함)이 아직 서버 칸에 없으면 — 들여오기 전 옛 블록으로 만들지 않는다(검토 R3)
                    var v = (net.minecraft.world.level.block.entity.BlockEntity) op.target;
                    int now = b.stateIdNow(world, op.x, op.y, op.z);
                    if (now < 0 || net.minecraft.world.level.block.Block.stateById(now).getBlock() != v.getBlockState().getBlock()) return false;
                    Object h = b.blockEntityNow(world, op.x, op.y, op.z, true);
                    if (h == null) return false;
                    v.furnaceBind(h);
                }
                case BE_REMOVE -> {
                    if (op.target instanceof net.minecraft.world.level.block.entity.BlockEntity v) {
                        // 지우기 전에 묶는다 — 지운 뒤에도 마지막 내용을 저장한다. 그 꼴을 여기서 떠 두면 지운 스레드(FAWE 되돌리기 기록)가 타일마다 주인을
                        // 기다리지 않는다(검토 위험 1 — 지운 객체는 더 바뀌지 않는다)
                        Object h = ChunkAccess.furnaceBindNow(world, v);
                        if (h != null && v.furnaceSnapshot() == null) v.furnaceSnapshot(b.blockEntitySaveNow(h));
                    }
                    b.removeBlockEntityNow(world, op.x, op.y, op.z);
                }
                case BE_LOAD -> {
                    Object h = ChunkAccess.furnaceBindNow(world, (net.minecraft.world.level.block.entity.BlockEntity) op.target);
                    if (h != null) b.blockEntityLoadNow(world, h, (org.domi.furnace.api.nbt.NbtCompound) op.extra);
                }
                case BE_CHANGED -> b.blockEntityChangedNow(world, op.x, op.y, op.z);
                case SECTION_SET -> b.setSectionStateRawNow(world, cx, op.slot, cz, op.cell, op.id, true);
                case CONTAINER_SET -> b.setSectionStateRawNow(world, cx, op.slot, cz, op.cell, op.id, false);
                case BIOME_SET -> b.setSectionBiomeRawNow(world, cx, op.slot, cz, op.cell, op.id);
                case CHUNK_SET -> b.setBlockStateNow(world, op.x, op.y, op.z, op.id, op.flag);
                case HM_RAW -> b.setHeightmapRawNow(world, cx, cz, op.type.getSerializationKey(), op.raw);
                case HM_UPDATE -> b.updateHeightmapNow(world, op.type.getSerializationKey(), (cx << 4) + op.x, op.y, (cz << 4) + op.z, op.id);
                case UNSAVED -> b.markUnsavedNow(world, cx, cz);
                default -> throw new IllegalStateException("unknown journal kind " + op.kind);
            }
        } catch (RuntimeException e) {
            warnOnce("replay:" + op.kind + ":" + e.getClass().getName(), CompatLang.t("compat.mirror.replay-failed", e));
        }
        return true;
    }

    private void bindViews() {
        try {
            chunk.furnaceBindViews();
        } catch (RuntimeException e) {
            warnOnce("bind:" + e.getClass().getName(), CompatLang.t("compat.mirror.bind-failed", e));
        }
    }

    @Override
    public void published(ChunkInternals.ChunkView view) {
        if (dead || view == null || !owner()) return;
        bindViews();   // 다른 스레드가 얻은 보기를 지금 서버 객체에(검토 R2)
        synchronized (lock) {
            if ((overlayCount > 0 || beOverlayCount > 0) && replayedSeq > 0) clearOverlays(replayedSeq);
            // 개수 필드: 붙은 섹션의 필드가 기준과 같으면(플러그인 대입 없음) 공개 판의 바닐라 개수로 — 아직 재생하지 않은 일지가 있으면 다음 판에
            if (seq > replayedSeq || view.version() == lastCountsVersion) return;
            lastCountsVersion = view.version();
            int count = Math.min(n, view.sectionCount());
            for (int i = 0; i < count; i++) {
                LevelChunkSection s = placed[i];
                if (s == null || s.furnaceChunk() != chunk || s.furnaceIndex() != i) continue;
                int[] c = view.sectionCounts(i);
                if (s.furnaceCasCountFields(expected, 3 * i, c)) System.arraycopy(c, 0, expected, 3 * i, 3);
            }
        }
    }

    @Override
    public void unloaded() {
        if (!owner()) return;
        if (!dead) bindViews();   // 내린 뒤에도 보기가 그 객체의 내용을 저장하게(검토 R2 — FAWE 게으른 타일 저장)
        synchronized (lock) {
            dead = true;   // 잠금 안에서 — 그 뒤 일지 쓰기는 버려진다
            journal = new ArrayList<>();
            cellOver = null;
            cellOverSeq = null;
            biomeOver = null;
            biomeOverSeq = null;
            overlayCount = 0;
            beOver = null;
            beOverSeq = null;
            beOverlayCount = 0;
            // 붙은 섹션을 그 순간 내용으로 굳혀 떼어 낸다 — 죽은 LevelChunk 처럼 그 뒤 읽기는 그 내용, 쓰기는 월드에 닿지 않는다
            for (int i = 0; i < n; i++) {
                for (LevelChunkSection s : new LevelChunkSection[] {arr[i], placed[i]}) {
                    if (s != null && s.furnaceChunk() == chunk) {
                        try {
                            s.furnaceDetach();
                        } catch (RuntimeException e) {
                            warnOnce("unload", CompatLang.t("compat.mirror.unload-failed", e));
                        }
                    }
                }
            }
        }
        ARRAYS.remove(arr);
    }

    // ------------------------------------------------------------------ 다른 스레드의 쓰기(일지, 결정 D1)

    /**
     * LevelChunkSection#setBlockState(붙은 보기, 주인이 아닌 스레드): 일지 + 덧칠 + 개수 필드 ±1(바닐라 그대로). 옛 상태 번호. 그 사이 섹션이 배열에서 빠져
     * 떼어 내졌으면 {@link PalettedContainer#FURNACE_DETACHED} — 부른 쪽이 그 섹션에 바닐라로 쓴다(검토 R2).
     */
    int sectionSet(LevelChunkSection s, int slot, int cell, int id, int oldId) {
        synchronized (lock) {
            if (s.furnaceChunk() != chunk || s.furnaceIndex() != slot) return PalettedContainer.FURNACE_DETACHED;
            if (dead) return oldId;   // 내린 청크(떼어 내는 사이에 온 쓰기) — 월드에 닿지 않는다
            long q = ++seq;
            journal.add(new Op(SECTION_SET, slot, cell, id, 0, 0, 0, false, null, null, s, q, null));
            overlayCell(slot, cell, id, q);
            int[] d = LevelChunkSection.furnaceCountDelta(oldId, id);
            s.furnaceAddCountFields(d);
            for (int k = 0; k < 3; k++) expected[3 * slot + k] += d[k];
        }
        noteWrite();
        return oldId;
    }

    /** 블록 컨테이너 칸 쓰기(붙은 보기 self, 주인이 아닌 스레드): 일지 + 덧칠(개수 그대로). self 가 그 사이 그 공급원에서 떼어 내졌으면 false. */
    boolean containerSet(PalettedContainer<?> self, Object src, int slot, int cell, int id) {
        synchronized (lock) {
            if (self.furnaceSourceObject() != src) return false;
            if (dead) return true;
            long q = ++seq;
            journal.add(new Op(CONTAINER_SET, slot, cell, id, 0, 0, 0, false, null, null, self, q, null));
            overlayCell(slot, cell, id, q);
        }
        noteWrite();
        return true;
    }

    /** 바이옴 칸 쓰기(붙은 보기 self, 주인이 아닌 스레드). self 가 그 사이 떼어 내졌으면 false. */
    boolean biomeSet(PalettedContainer<?> self, Object src, int slot, int cell, int id) {
        synchronized (lock) {
            if (self.furnaceSourceObject() != src) return false;
            if (dead) return true;
            long q = ++seq;
            journal.add(new Op(BIOME_SET, slot, cell, id, 0, 0, 0, false, null, null, self, q, null));
            overlayBiome(slot, cell, id, q);
        }
        noteWrite();
        return true;
    }

    /**
     * 잡힌 컨테이너에 잠금 없이 쓴 직후 잡혔다(드문 경쟁, {@code PalettedContainer#furnaceWrite}): 그 사이 붙어 그 data 가 기준이 됐으면 서버에 닿게 일지로
     * 넘긴다(들여오기가 읽었으면 같은 값을 한 번 더 쓴다). 아직 들여오는 중이면 들여오기가 그 data 를 읽는다. writtenData 는 쓰기 <b>전</b> data 다 — 그 쓰기가
     * 팔레트를 키워 data 가 바뀌었으면 새 data 를 기준으로 옮긴다(붙일 때 사본에서 나온 것이라 플러그인 대입이 아니다 — 들여오면 붙인 뒤 서버 변경을 되돌린다, 재검토 C1).
     */
    @SuppressWarnings("unchecked")
    void lateWrite(PalettedContainer<?> c, Object writtenData, int index, Object value) {
        synchronized (lock) {
            if (dead || !(c.furnaceSourceObject() instanceof FurnaceSectionSource src) || src.chunk != chunk || writtenData != c.furnacePlaced()) return;
            // 검토 재검토 C1: 늦은 쓰기가 팔레트를 키워 data 가 바뀌었다 — 붙일 때 사본에서 나온 것이라 들여오지 않는다(이 칸은 아래 일지로, 읽기는 다시 공급원으로)
            if (c.furnaceData() != writtenData) c.furnaceMarkPlaced(c.furnaceData(), Long.MIN_VALUE);
            int id = src.biome ? (value instanceof Holder<?> h ? NmsChunks.BIOME_IDS.getId((Holder<Biome>) h) : -1)
                    : (value instanceof BlockState st ? st.furnaceStateId() : -1);
            if (id < 0) return;
            long q = ++seq;
            journal.add(new Op(src.biome ? BIOME_SET : CONTAINER_SET, src.index, index, id, 0, 0, 0, false, null, null, c, q, null));
            if (src.biome) overlayBiome(src.index, index, id, q);
            else overlayCell(src.index, index, id, q);
        }
        noteWrite();
    }

    /**
     * LevelChunk#setBlockState(주인이 아닌 스레드, 그 자리가 붙은 보기): 일지(주인이 바닐라 LevelChunk.setBlockState 를 그대로 — 블록 엔티티·빛·하이트맵·
     * 저장 표시까지) + 덧칠 + 그 섹션 개수 필드 ±1. 그 사이 그 섹션이 떼어 내졌으면 false(부른 쪽이 그 섹션에 바닐라로 쓴다).
     */
    boolean chunkSet(LevelChunkSection s, int slot, int x, int y, int z, int id, int oldId, boolean moved) {
        int cell = (y & 15) << 8 | (z & 15) << 4 | (x & 15);
        synchronized (lock) {
            if (s.furnaceChunk() != chunk || s.furnaceIndex() != slot || !s.getStates().furnaceLiveNow()) return false;
            if (dead) return true;
            long q = ++seq;
            journal.add(new Op(CHUNK_SET, slot, cell, id, x, y, z, moved, null, null, s, q, null));
            overlayCell(slot, cell, id, q);
            int[] d = LevelChunkSection.furnaceCountDelta(oldId, id);
            s.furnaceAddCountFields(d);
            for (int k = 0; k < 3; k++) expected[3 * slot + k] += d[k];
        }
        noteWrite();
        return true;
    }

    void heightmapRaw(Heightmap.Types type, long[] raw) {
        synchronized (lock) {
            if (dead) return;
            journal.add(new Op(HM_RAW, -1, 0, 0, 0, 0, 0, false, type, raw.clone(), null, 0, null));
            seq++;
        }
        noteWrite();
    }

    void heightmapUpdate(Heightmap.Types type, int x, int y, int z, int id) {
        synchronized (lock) {
            if (dead) return;
            journal.add(new Op(HM_UPDATE, -1, 0, id, x, y, z, false, type, null, null, 0, null));
            seq++;
        }
        noteWrite();
    }

    void unsaved() {
        synchronized (lock) {
            if (dead) return;
            journal.add(new Op(UNSAVED, -1, 0, 0, 0, 0, 0, false, null, null, null, 0, null));
            seq++;
        }
        noteWrite();
    }

    // ------------------------------------------------------------------ 다른 스레드의 블록 엔티티 쓰기(T-198)

    /** LevelChunk#getBlockEntity(IMMEDIATE)·다른 스레드: 주인이 그 칸에 블록 엔티티를 만들고 이 보기를 묶는다. 내린 청크면 false. */
    boolean blockEntityCreate(int x, int y, int z, net.minecraft.world.level.block.entity.BlockEntity view) {
        synchronized (lock) {
            if (dead) return false;
            long q = ++seq;
            journal.add(new Op(BE_CREATE, -1, 0, 0, x, y, z, false, null, null, view, q, null));
            overlayBe(x, y, z, view, q);
        }
        noteWrite();
        return true;
    }

    /** LevelChunk#removeBlockEntity·다른 스레드: 주인이 그 칸의 블록 엔티티를 지운다(view 는 그때 그 칸의 보기 — 지우기 전에 묶는다). */
    void blockEntityRemove(int x, int y, int z, net.minecraft.world.level.block.entity.BlockEntity view) {
        synchronized (lock) {
            if (dead) return;
            long q = ++seq;
            journal.add(new Op(BE_REMOVE, -1, 0, 0, x, y, z, false, null, null, view, q, null));
            overlayBe(x, y, z, BE_REMOVED, q);
        }
        noteWrite();
    }

    /** BlockEntity#loadWithComponents·다른 스레드: 주인이 그 보기의 블록 엔티티에 tag(사본)를 싣는다. */
    void blockEntityLoad(net.minecraft.world.level.block.entity.BlockEntity view, org.domi.furnace.api.nbt.NbtCompound tag) {
        var p = view.getBlockPos();
        synchronized (lock) {
            if (dead) return;
            journal.add(new Op(BE_LOAD, -1, 0, 0, p.getX(), p.getY(), p.getZ(), false, null, null, view, ++seq, tag));
        }
        noteWrite();
    }

    /** BlockEntity#setChanged·다른 스레드: 주인이 그 칸에 바닐라 setChanged(level, pos, state). */
    void blockEntityChanged(int x, int y, int z) {
        synchronized (lock) {
            if (dead) return;
            journal.add(new Op(BE_CHANGED, -1, 0, 0, x, y, z, false, null, null, null, ++seq, null));
        }
        noteWrite();
    }

    private void overlayBe(int x, int y, int z, Object v, long q) {
        if (beOver == null) {
            beOver = new java.util.HashMap<>();
            beOverSeq = new java.util.HashMap<>();
        }
        long k = net.minecraft.core.BlockPos.asLong(x, y, z);
        beOver.put(k, v);
        beOverSeq.put(k, q);
        beOverlayCount = beOver.size();
    }

    /** 다른 스레드: 그 칸의 블록 엔티티 덧칠(보기·{@link #BE_REMOVED}, 없으면 null). */
    Object blockEntityOverlayAt(long pos) {
        if (beOverlayCount == 0) return null;
        synchronized (lock) {
            return beOver == null ? null : beOver.get(pos);
        }
    }

    /** 다른 스레드: 블록 엔티티 덧칠 전체의 사본. */
    Map<Long, Object> blockEntityOverlay() {
        if (beOverlayCount == 0) return Map.of();
        synchronized (lock) {
            return beOver == null ? Map.of() : new java.util.HashMap<>(beOver);
        }
    }

    private void overlayCell(int slot, int cell, int id, long s) {
        if (cellOver == null) {
            cellOver = new int[n][];
            cellOverSeq = new long[n][];
        }
        if (cellOver[slot] == null) {
            cellOver[slot] = new int[4096];
            java.util.Arrays.fill(cellOver[slot], -1);
            cellOverSeq[slot] = new long[4096];
        }
        if (cellOver[slot][cell] < 0) overlayCount++;
        cellOver[slot][cell] = id;
        cellOverSeq[slot][cell] = s;
    }

    private void overlayBiome(int slot, int cell, int id, long s) {
        if (biomeOver == null) {
            biomeOver = new int[n][];
            biomeOverSeq = new long[n][];
        }
        if (biomeOver[slot] == null) {
            biomeOver[slot] = new int[64];
            java.util.Arrays.fill(biomeOver[slot], -1);
            biomeOverSeq[slot] = new long[64];
        }
        if (biomeOver[slot][cell] < 0) overlayCount++;
        biomeOver[slot][cell] = id;
        biomeOverSeq[slot][cell] = s;
    }

    /** lock 안: 재생돼 공개 판에 든 덧칠을 지운다. */
    private void clearOverlays(long upTo) {
        if (beOver != null) {
            beOver.entrySet().removeIf(e -> beOverSeq.get(e.getKey()) <= upTo);
            beOverSeq.keySet().retainAll(beOver.keySet());
            beOverlayCount = beOver.size();
        }
        int left = 0;
        for (int i = 0; i < n; i++) {
            if (cellOver != null && cellOver[i] != null) {
                boolean any = false;
                for (int c = 0; c < 4096; c++) {
                    if (cellOver[i][c] < 0) continue;
                    if (cellOverSeq[i][c] <= upTo) cellOver[i][c] = -1;
                    else {
                        any = true;
                        left++;
                    }
                }
                if (!any) {
                    cellOver[i] = null;
                    cellOverSeq[i] = null;
                }
            }
            if (biomeOver != null && biomeOver[i] != null) {
                boolean any = false;
                for (int c = 0; c < 64; c++) {
                    if (biomeOver[i][c] < 0) continue;
                    if (biomeOverSeq[i][c] <= upTo) biomeOver[i][c] = -1;
                    else {
                        any = true;
                        left++;
                    }
                }
                if (!any) {
                    biomeOver[i] = null;
                    biomeOverSeq[i] = null;
                }
            }
        }
        overlayCount = left;
    }

    // ------------------------------------------------------------------ 다른 스레드의 읽기(자기 쓰기 덧칠)

    /** 그 칸의 덧칠(없으면 -1). */
    int overlayCellAt(int slot, int cell) {
        if (overlayCount == 0) return -1;
        synchronized (lock) {
            int[] o = cellOver == null || slot < 0 || slot >= n ? null : cellOver[slot];
            return o == null ? -1 : o[cell];
        }
    }

    /** 섹션 칸 번호 배열에 덧칠을 입힌다(제자리). */
    int[] overlayCells(int slot, int[] ids) {
        if (overlayCount == 0) return ids;
        synchronized (lock) {
            int[] o = cellOver == null || slot < 0 || slot >= n ? null : cellOver[slot];
            if (o != null) for (int c = 0; c < 4096; c++) if (o[c] >= 0) ids[c] = o[c];
        }
        return ids;
    }

    int[] overlayBiomes(int slot, int[] ids) {
        if (overlayCount == 0) return ids;
        synchronized (lock) {
            int[] o = biomeOver == null || slot < 0 || slot >= n ? null : biomeOver[slot];
            if (o != null) for (int c = 0; c < 64; c++) if (o[c] >= 0) ids[c] = o[c];
        }
        return ids;
    }

    // ------------------------------------------------------------------ 경고

    /** 같은 거울·같은 원인은 한 번만. */
    void warnOnce(String key, String message) {
        if (warned.size() < 64 && warned.add(key)) {
            LOG.warn(CompatLang.t("compat.mirror.line", cx, cz, world.dimension(), message));
        }
    }
}
