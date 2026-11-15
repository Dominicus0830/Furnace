package net.minecraft.world.level.chunk;

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
 * 재생이 훑기보다 먼저다 — 배열에서 빠진 옛 섹션에 쓴 칸은 들여온 새 섹션이 덮는다(Paper 에서도 교체된 섹션에 쓴 것은 남지 않는다).
 * 들여온 플러그인 섹션·컨테이너는 그 자리의 보기로 붙이고(정체성 유지 — FAWE 의 다음 CAS 기대값), 빠진 옛 보기는 떼어 낸다(그 뒤 쓰기는 월드에 닿지 않는다).
 * <p>잠금: Furnace 전용 {@link #lock} 하나 — 플러그인이 볼 수 있는 객체(청크·섹션·컨테이너)의 모니터는 쓰지 않는다(설계 R12, FAWE 는 그것들에 synchronized 한다).
 * 잠금 안에서 기다리거나 다리로 다른 스레드를 부르지 않는다.
 */
final class FurnaceChunkMirror implements ChunkInternals.ChunkMirror {

    private static final Logger LOG = LoggerFactory.getLogger("Furnace");
    private static final VarHandle SECTIONS = MethodHandles.arrayElementVarHandle(LevelChunkSection[].class);

    /** 장벽 알림용: 섹션 배열 → 거울(약한 키·약한 값 — 거울이 배열을 강하게 쥐므로 값도 약하게). */
    private static final Map<LevelChunkSection[], WeakReference<FurnaceChunkMirror>> ARRAYS = Collections.synchronizedMap(new WeakHashMap<>());

    private static final int SECTION_SET = 0, CONTAINER_SET = 1, BIOME_SET = 2, CHUNK_SET = 3, HM_RAW = 4, HM_UPDATE = 5, UNSAVED = 6;

    /** 일지 한 줄. */
    private record Op(int kind, int slot, int cell, int id, int x, int y, int z, boolean flag, Heightmap.Types type, long[] raw) {}

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
            warnOnce("request", "주인에게 맞추기를 부탁하지 못했다(" + e + ") — 다음 틱 끝 훑기가 맞춘다");
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

    /** 한 자리의 들여오기 계획(잠금 안에서 정한다 — 일지를 떼어 내는 순간과 같은 순간). */
    private record Plan(int slot, LevelChunkSection was, LevelChunkSection cur, boolean replace, boolean states, Object statesData,
                        boolean biomeField, Object biomes, boolean biomeData, Object biomesData, int[] counts, int[] expectedAtPlan) {}

    /** 서버가 주인 스레드에서 부른다. 다른 스레드에서 부르면 아무것도 안 한다({@code ChunkAccess#furnaceMirror()} 는 공개라 플러그인도 부를 수 있다). */
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
            for (Op op : ops) replay(op);   // 재생이 먼저 — 그 뒤 들여오는 섹션이 덮는다(교체된 섹션에 쓴 칸은 남지 않는다)
            if (!ops.isEmpty()) replayedSeq = upTo;
            for (Plan p : plans) execute(p);
            return dirty;
        } finally {
            syncing = false;
        }
    }

    /** 잠금 안: 자리마다 바뀐 구성 요소. */
    private List<Plan> plan() {
        List<Plan> out = null;
        for (int i = 0; i < n; i++) {
            LevelChunkSection cur = (LevelChunkSection) SECTIONS.getAcquire(arr, i);
            LevelChunkSection was = placed[i];
            Plan p = null;
            if (cur == null) {
                if (was != null) warnOnce("null:" + i, "섹션 배열 칸 " + i + " 에 null 이 들어왔다 — 들여오지 않는다(바닐라 로드된 청크의 섹션은 null 이 아니다)");
                continue;
            }
            if (cur != was || cur.furnaceChunk() == null) {
                int[] f = new int[3];
                cur.furnaceReadCountFields(f, 0);
                p = new Plan(i, was, cur, true, true, null, true, null, false, null, f, null);
            } else if (cur.furnaceChunk() == chunk && cur.furnaceIndex() == i) {
                PalettedContainer<BlockState> st = cur.getStates();
                Object sd = st.furnaceData();
                boolean states = sd != st.furnacePlaced();
                Object bf = cur.furnaceBiomesField();
                boolean biomeField = bf != placedBiomes[i];
                boolean biomeData = false;
                Object bd = null;
                if (!biomeField && bf instanceof PalettedContainer<?> pb && pb.furnaceAttached()) {
                    bd = pb.furnaceData();
                    biomeData = bd != pb.furnacePlaced();
                }
                int[] counts = null;
                if (cur.furnaceCountFieldsDiffer(expected, 3 * i)) {
                    counts = new int[3];
                    cur.furnaceReadCountFields(counts, 0);
                }
                if (states || biomeField || biomeData || counts != null) {
                    int[] e = counts == null ? null : java.util.Arrays.copyOfRange(expected, 3 * i, 3 * i + 3);
                    p = new Plan(i, was, cur, false, states, sd, biomeField, bf, biomeData, bd, counts, e);
                }
            }
            if (p != null) {
                if (out == null) out = new ArrayList<>();
                out.add(p);
            }
        }
        return out == null ? List.of() : out;
    }

    /** 주인: 계획 하나를 들여온다. 플러그인 내용이 잘못됐으면(팔레트 null·레지스트리에 없는 값) 들여오지 않고 한 번 경고한다. */
    private void execute(Plan p) {
        var b = NmsChunks.bridge();
        int i = p.slot;
        LevelChunkSection cur = p.cur;
        int[] states = null, biomes = null;
        try {
            if (p.states) states = cur.getStates().furnaceReadIds(BlockState::furnaceStateId);
            if (p.biomeField || p.biomeData) biomes = readBiomes(p.replace ? cur.furnaceBiomesField() : p.biomes);
        } catch (RuntimeException e) {
            warnOnce("invalid:" + i + ":" + e.getMessage(), "섹션 " + i + " 의 내용을 들여오지 않는다: " + e.getMessage()
                    + " — 바닐라에서도 그런 섹션은 읽을 때 실패한다. 플러그인이 만든 섹션·컨테이너를 확인할 것");
            return;
        }
        // 빠진 옛 보기는 들여오기 <b>전</b>에 그 순간(옛) 내용으로 굳혀 떼어 낸다 — 그 뒤 쓰기는 월드에 닿지 않고, 다시 넣으면 옛 내용이 들어간다(Paper 그대로)
        if (p.replace && p.was != null && p.was.furnaceChunk() == chunk && p.was.furnaceIndex() == i) {
            synchronized (lock) {
                p.was.furnaceDetach();   // 배열에서 빠진 옛 섹션
            }
        } else if (p.biomeField && placedBiomes[i] instanceof PalettedContainer<?> oc && cur.furnaceOwnsBiomeSource(oc)) {
            synchronized (lock) {
                oc.furnaceDetach();   // biomes 필드에서 빠진 옛 바이옴 컨테이너
            }
        }
        if (!b.importSectionNow(world, cx, i, cz, states, biomes, p.counts)) {
            dead = true;   // 그 사이 내려감
            return;
        }
        long stamp = b.contentStampNow(world, cx, cz);
        synchronized (lock) {
            if (p.replace) {
                placed[i] = cur;
                Object bf = cur.furnaceBiomesField();
                placedBiomes[i] = bf;
                if (cur.furnaceChunk() == null) cur.furnaceAttach(chunk, i, stamp);   // 그 자리의 보기로(정체성 유지). 다른 곳에 붙은 섹션은 내용만
                System.arraycopy(p.counts, 0, expected, 3 * i, 3);
                return;
            }
            if (p.states) cur.getStates().furnaceMarkPlaced(p.statesData, stamp);
            if (p.biomeField) {
                placedBiomes[i] = p.biomes;
                if (p.biomes instanceof PalettedContainer<?> nb && !nb.furnaceAttached()) cur.furnaceAttachBiomes(nb, stamp);
            } else if (p.biomeData && p.biomes instanceof PalettedContainer<?> pb) {
                pb.furnaceMarkPlaced(p.biomesData, stamp);
            }
            if (p.counts != null) {
                // 계획 뒤 일지 ±1 은 필드와 기준에 함께 더해졌고 서버에는 재생으로 더해진다 — 들여온 대입량만큼만 기준을 옮긴다(필드 - 기준 이 0 이 되게)
                for (int k = 0; k < 3; k++) expected[3 * i + k] += p.counts[k] - p.expectedAtPlan[k];
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static int[] readBiomes(Object container) {
        if (container instanceof PalettedContainer<?> pc) {
            return ((PalettedContainer<Holder<Biome>>) pc).furnaceReadIds(NmsChunks.BIOME_IDS::getId);
        }
        if (!(container instanceof PalettedContainerRO<?> ro)) throw new IllegalStateException("biomes 가 null 이다");
        int[] out = new int[64];
        for (int i = 0; i < 64; i++) {
            Object v = ro.get(i & 3, i >> 4, i >> 2 & 3);
            int id = v instanceof Holder<?> h ? NmsChunks.BIOME_IDS.getId((Holder<Biome>) h) : -1;
            if (id < 0) throw new IllegalStateException("서버 바이옴 레지스트리에 없는 값이다: " + v + " (칸 " + i + ")");
            out[i] = id;
        }
        return out;
    }

    /** 주인: 일지 한 줄 재생(바닐라 원시 의미). 실패는 한 번 경고하고 다음 줄로. */
    private void replay(Op op) {
        var b = NmsChunks.bridge();
        try {
            switch (op.kind) {
                case SECTION_SET -> b.setSectionStateRawNow(world, cx, op.slot, cz, op.cell, op.id, true);
                case CONTAINER_SET -> b.setSectionStateRawNow(world, cx, op.slot, cz, op.cell, op.id, false);
                case BIOME_SET -> b.setSectionBiomeRawNow(world, cx, op.slot, cz, op.cell, op.id);
                case CHUNK_SET -> b.setBlockStateNow(world, op.x, op.y, op.z, op.id, op.flag);
                case HM_RAW -> b.setHeightmapRawNow(world, cx, cz, op.type.getSerializationKey(), op.raw);
                case HM_UPDATE -> b.updateHeightmapNow(world, op.type.getSerializationKey(), (cx << 4) + op.x, op.y, (cz << 4) + op.z, op.id);
                case UNSAVED -> b.markUnsavedNow(world, cx, cz);
                default -> throw new IllegalStateException("일지 종류 " + op.kind);
            }
        } catch (RuntimeException e) {
            warnOnce("replay:" + op.kind + ":" + e.getClass().getName(), "다른 스레드가 쓴 칸을 재생하지 못했다(" + e + ") — 그 쓰기는 버린다");
        }
    }

    @Override
    public void published(ChunkInternals.ChunkView view) {
        if (dead || view == null || !owner()) return;
        synchronized (lock) {
            if (overlayCount > 0 && replayedSeq > 0) clearOverlays(replayedSeq);
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
        synchronized (lock) {
            dead = true;   // 잠금 안에서 — 그 뒤 일지 쓰기는 버려진다
            journal = new ArrayList<>();
            cellOver = null;
            cellOverSeq = null;
            biomeOver = null;
            biomeOverSeq = null;
            overlayCount = 0;
            // 붙은 섹션을 그 순간 내용으로 굳혀 떼어 낸다 — 죽은 LevelChunk 처럼 그 뒤 읽기는 그 내용, 쓰기는 월드에 닿지 않는다
            for (int i = 0; i < n; i++) {
                for (LevelChunkSection s : new LevelChunkSection[] {arr[i], placed[i]}) {
                    if (s != null && s.furnaceChunk() == chunk) {
                        try {
                            s.furnaceDetach();
                        } catch (RuntimeException e) {
                            warnOnce("unload", "내리는 청크의 섹션을 굳히지 못했다(" + e + ")");
                        }
                    }
                }
            }
        }
        ARRAYS.remove(arr);
    }

    // ------------------------------------------------------------------ 다른 스레드의 쓰기(일지, 결정 D1)

    /** LevelChunkSection#setBlockState(붙은 보기, 주인이 아닌 스레드): 일지 + 덧칠 + 개수 필드 ±1(바닐라 그대로). 옛 상태 번호. */
    int sectionSet(LevelChunkSection s, int slot, int cell, int id, int oldId) {
        synchronized (lock) {
            if (dead) return oldId;   // 내린 청크(떼어 내는 사이에 온 쓰기) — 월드에 닿지 않는다
            journal.add(new Op(SECTION_SET, slot, cell, id, 0, 0, 0, false, null, null));
            overlayCell(slot, cell, id, ++seq);
            int[] d = LevelChunkSection.furnaceCountDelta(oldId, id);
            s.furnaceAddCountFields(d);
            for (int k = 0; k < 3; k++) expected[3 * slot + k] += d[k];
        }
        noteWrite();
        return oldId;
    }

    /** 블록 컨테이너 칸 쓰기(붙은 보기, 주인이 아닌 스레드): 일지 + 덧칠(개수 그대로). */
    void containerSet(int slot, int cell, int id) {
        synchronized (lock) {
            if (dead) return;
            journal.add(new Op(CONTAINER_SET, slot, cell, id, 0, 0, 0, false, null, null));
            overlayCell(slot, cell, id, ++seq);
        }
        noteWrite();
    }

    /** 바이옴 칸 쓰기(붙은 보기, 주인이 아닌 스레드). */
    void biomeSet(int slot, int cell, int id) {
        synchronized (lock) {
            if (dead) return;
            journal.add(new Op(BIOME_SET, slot, cell, id, 0, 0, 0, false, null, null));
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
            biomeOverSeq[slot][cell] = ++seq;
        }
        noteWrite();
    }

    /**
     * LevelChunk#setBlockState(주인이 아닌 스레드, 그 자리가 붙은 보기): 일지(주인이 바닐라 LevelChunk.setBlockState 를 그대로 — 블록 엔티티·빛·하이트맵·
     * 저장 표시까지) + 덧칠 + 그 섹션 개수 필드 ±1.
     */
    void chunkSet(LevelChunkSection s, int slot, int x, int y, int z, int id, int oldId, boolean moved) {
        int cell = (y & 15) << 8 | (z & 15) << 4 | (x & 15);
        synchronized (lock) {
            if (dead) return;
            journal.add(new Op(CHUNK_SET, slot, cell, id, x, y, z, moved, null, null));
            overlayCell(slot, cell, id, ++seq);
            int[] d = LevelChunkSection.furnaceCountDelta(oldId, id);
            s.furnaceAddCountFields(d);
            for (int k = 0; k < 3; k++) expected[3 * slot + k] += d[k];
        }
        noteWrite();
    }

    void heightmapRaw(Heightmap.Types type, long[] raw) {
        synchronized (lock) {
            if (dead) return;
            journal.add(new Op(HM_RAW, -1, 0, 0, 0, 0, 0, false, type, raw.clone()));
            seq++;
        }
        noteWrite();
    }

    void heightmapUpdate(Heightmap.Types type, int x, int y, int z, int id) {
        synchronized (lock) {
            if (dead) return;
            journal.add(new Op(HM_UPDATE, -1, 0, id, x, y, z, false, type, null));
            seq++;
        }
        noteWrite();
    }

    void unsaved() {
        synchronized (lock) {
            if (dead) return;
            journal.add(new Op(UNSAVED, -1, 0, 0, 0, 0, 0, false, null, null));
            seq++;
        }
        noteWrite();
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

    /** lock 안: 재생돼 공개 판에 든 덧칠을 지운다. */
    private void clearOverlays(long upTo) {
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
            LOG.warn("[NMS] 청크 {}, {} ({}) 거울: {}", cx, cz, world.dimension(), message);
        }
    }
}
