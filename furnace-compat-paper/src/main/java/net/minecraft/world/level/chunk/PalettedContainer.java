package net.minecraft.world.level.chunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.BitStorage;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;

/**
 * 팔레트 컨테이너 shim(Mojang 이름). 두 가지가 있다.
 * <ul>
 * <li><b>진짜 컨테이너</b>(N2b-1): 생성자로 만든 것과 {@link #copy()}. 바닐라 1.21.1 포팅 그대로 — 필드 {@code registry}·{@code data}(volatile)·
 *     {@code strategy}, 팔레트 늘리기·줄이지 않기·번호 매기는 순서, {@code getRaw()} 배치, 직렬화({@code read}·{@code write}·{@code getSerializedSize})가 같다.
 *     서버 청크와 무관한 떨어진 객체다. 플러그인이 로드된 청크의 섹션에 넣으면 주인이 그 내용을 들여오고(N2b-4) 그 자리의 보기로 붙인다.</li>
 * <li><b>보기</b>(N2a, {@code LevelChunkSection#getStates()}·{@code getBiomes()} of 로드된 청크): 읽을 때마다 서버 청크의 지금 값을 본다(주인 region
 *     스레드는 살아 있는 값, 그 밖은 공개 스냅샷 + 자기 쓰기). 칸 쓰기({@code set}·{@code getAndSet}·{@code getAndSetUnchecked}, N2b-2)는 바닐라 살아 있는
 *     컨테이너처럼 그 칸만 쓴다 — 주인 스레드는 서버에 바로, 그 밖은 거울 칸 일지에 쌓아 주인이 1틱 안에 재생한다(N2b-4, 결정 D1). 섹션 개수·하이트맵·빛·
 *     저장 표시·클라이언트는 그대로(바닐라도 컨테이너 쓰기는 아무것도 맞추지 않는다). 필드 {@code data} 는 리플렉션으로 처음 읽힐 때 그 순간 내용으로
 *     바닐라 배치를 실체화한다(N2b-4 장벽, 내용이 바뀌면 다시). 플러그인이 {@code data} 를 바꾸거나 {@code read} 하면 들여오기 전까지 그 data 를 바닐라처럼
 *     읽고 쓰고, 주인이 그 내용을 서버로 들여온다. copy() 는 디스크에서 읽은 바닐라 섹션처럼(팔레트 = 처음 나온 순서, 비트 수 = 직렬화 규칙) 진짜 컨테이너를 만든다.</li>
 * </ul>
 * 바닐라와 다를 수 있는 것: {@link #count} 가 값마다 부르는 순서(바닐라는 fastutil 해시 순서, 여기는 처음 나온 순서 — 개수는 같다),
 * 보기의 팔레트 자체(항목 순서·비트 수 — 살아 있는 섹션의 바닐라 팔레트는 쓰기 이력에 달려 있다). 디버그 잠금(ThreadingDetector)은 두지 않는다 —
 * acquire·release 는 아무것도 하지 않는다(가정: Paper 1.21.1 도 ThreadingDetector 를 끈다, 노트북 대조 V6 에서 확인).
 * 코덱(codecRW·codecRO·pack)은 DataFixerUpper shim 이 없어 두지 않았다.
 */
public class PalettedContainer<T> implements PaletteResize<T>, PalettedContainerRO<T> {

    private static final int MIN_PALETTE_BITS = 0;

    private final PaletteResize<T> dummyPaletteResize = (bits, value) -> 0;
    /** Paper 는 공개 필드(FAWE 가 바로 읽는다). */
    public final IdMap<T> registry;
    private volatile Data<T> data;
    private final Strategy strategy;
    /**
     * Furnace: 서버 섹션에 붙은 보기면 번호 공급원(진짜·떨어진 컨테이너면 null). 거울 들여오기(N2b-4)가 플러그인이 만든 컨테이너를 그 자리에 붙이고
     * (그 뒤로 서버 섹션의 보기), 배열에서 빠진 옛 보기를 떼어 낸다(그 뒤로 떨어진 컨테이너).
     */
    private volatile FurnaceSource furnaceSource;
    /**
     * Furnace: 붙은 보기에서 Furnace 가 마지막으로 놓은 {@code data}(실체화했거나 들여온 것, 설계 3.2 placed). {@code data} 가 이것과 다르면 플러그인이
     * 필드를 바꾼 것이다 — 들여오기 전까지 이 컨테이너는 그 data 를 그대로 읽고 쓴다(바닐라처럼). 형을 Object 로 둔다(형으로 Data 필드를 찾는 플러그인이
     * 이 칸을 고르지 않게). 쓰기는 거울 잠금 아래.
     */
    private volatile Object furnacePlaced;
    /** Furnace: furnacePlaced 를 실체화한 판(서버 내용 판 번호, 다른 스레드는 공개 스냅샷의 것). 거울 잠금 아래. */
    private long furnacePlacedStamp = Long.MIN_VALUE;
    /**
     * Furnace: 거울이 들여오려고 잡은 떨어진 컨테이너면 그 거울(아니면 null, PR #396 검토 R3). 잡힌 동안 바닐라 길의 칸 쓰기는 거울 잠금 아래에서 한다 —
     * 주인이 잠금 안에서 내용을 읽고·들여오고·붙이는 사이에 쓴 칸을 잃지 않게. 붙으면 공급원이, 떼어 내면 null 이 이어받는다. 형을 Object 로 둔다.
     */
    private volatile Object furnaceClaim;

    /** 공급원 쓰기가 그 사이 떨어진 컨테이너라 하지 못했다(부른 쪽이 바닐라 길로 이 객체에 쓴다). */
    static final int FURNACE_DETACHED = Integer.MIN_VALUE;
    private static final Object DETACHED_VALUE = new Object();

    private static final java.lang.invoke.VarHandle DATA;
    static {
        try {
            DATA = java.lang.invoke.MethodHandles.lookup().findVarHandle(PalettedContainer.class, "data", Data.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** 붙은 보기의 서버 번호 공급원(호환 모듈 전용 — 청크 shim 의 거울을 거쳐 주인 스레드는 살아 있는 값, 그 밖은 공개 스냅샷 + 자기 쓰기). */
    interface FurnaceSource {
        int idAt(int index);

        int[] ids();

        /**
         * 칸 하나를 쓰고 옛 번호(주인 스레드는 서버에 바로, 그 밖은 거울 칸 일지 — N2b-4). self 가 그 사이 떼어 내져 이 공급원의 보기가 아니면
         * {@link #FURNACE_DETACHED}(부른 쪽이 self 에 바닐라로 쓴다 — 배열에서 빠진 컨테이너에 쓴 칸은 월드에 닿지 않는다, PR #396 검토 R2).
         */
        int setId(PalettedContainer<?> self, int index, int id);

        /** 이 공급원의 거울(잡힌 컨테이너의 바닐라 쓰기가 그 잠금 아래에서 한다). */
        FurnaceChunkMirror mirror();

        /** 실체화용: 그 순간의 칸 번호 전부와 그 판 번호(stampOut[0]) — 한 출처에서. */
        int[] idsStamped(long[] stampOut);

        /** 거울 잠금(Furnace 전용 객체 — 플러그인이 볼 수 있는 객체의 모니터를 쓰지 않는다, 설계 R12). */
        Object lock();

        /** 플러그인이 이 컨테이너의 필드를 바꿨다(장벽 알림) — 거울이 다음 맞춤 지점에서 본다. */
        void fieldWritten();
    }

    // ------------------------------------------------------------------ 생성자(바닐라)

    /** 바닐라(Paper 는 공개): 주어진 설정·저장소·팔레트 항목으로 만든다. 저장소를 복사하지 않고 그대로 쓴다. */
    public PalettedContainer(IdMap<T> registry, Strategy strategy, Configuration<T> configuration, BitStorage storage, List<T> values) {
        this.registry = registry;
        this.strategy = strategy;
        this.furnaceSource = null;
        this.data = new Data<>(configuration, storage, configuration.factory().create(configuration.bits(), registry, this, values));
    }

    private PalettedContainer(IdMap<T> registry, Strategy strategy, Data<T> data) {
        this.registry = registry;
        this.strategy = strategy;
        this.furnaceSource = null;
        this.data = data;
    }

    /** 바닐라: 모든 칸이 value 인 컨테이너(값 하나 팔레트, 0 비트). */
    public PalettedContainer(IdMap<T> registry, T value, Strategy strategy) {
        this.strategy = strategy;
        this.registry = registry;
        this.furnaceSource = null;
        this.data = createOrReuseData(null, 0);
        this.data.palette().idFor(value);
    }

    private PalettedContainer(IdMap<T> registry, Strategy strategy, FurnaceSource source) {
        this.registry = registry;
        this.strategy = strategy;
        this.furnaceSource = source;
        this.data = null;
    }

    /** 호환 모듈 전용: 서버 섹션 보기(칸 번호는 바닐라 {@link Strategy#getIndex}). data 필드는 처음 읽힐 때 실체화한다. */
    static <T> PalettedContainer<T> furnaceView(IdMap<T> registry, Strategy strategy, FurnaceSource source) {
        return new PalettedContainer<>(registry, strategy, source);
    }

    /** 바닐라 SimpleBitStorage 의 칸 번호 검사(commons-lang3 Validate.inclusiveBetween 문구). */
    private static void checkIndex(int index, int size) {
        if (index < 0 || index >= size) {
            throw new IllegalArgumentException(String.format("The value %s is not in the specified inclusive range of %s to %s", (long) index, 0L, (long) (size - 1)));
        }
    }

    /**
     * 보기 칸 쓰기: 값의 레지스트리 번호로 쓰고 옛 값. 이 서버 레지스트리에 없는 값은 IllegalArgumentException(서버 칸에 넣을 수 없다).
     * 그 사이 떼어 내졌으면 {@link #DETACHED_VALUE}(부른 쪽이 바닐라 길로).
     */
    @SuppressWarnings("unchecked")
    private T furnaceSet(FurnaceSource src, int index, T value) {
        checkIndex(index, strategy.size());
        int id = registry.getId(value);
        if (id < 0) throw new IllegalArgumentException("이 서버 레지스트리에 없는 값이라 로드된 청크에 쓸 수 없다: " + value);
        int old = src.setId(this, index, id);
        return old == FURNACE_DETACHED ? (T) DETACHED_VALUE : registry.byId(old);
    }

    /**
     * 칸 쓰기 공통(N2b-4): 붙은 보기면 공급원으로(그 사이 떨어졌으면 바닐라로), 거울이 잡은 컨테이너(들여오는 중·플러그인이 data 를 바꾼 붙은 보기)면
     * 거울 잠금 아래에서 바닐라로, 아니면 바닐라. 잡히기 직전에 잠금 없이 쓴 칸은 거울에 넘긴다(드문 경쟁 — {@code FurnaceChunkMirror#lateWrite}).
     */
    @SuppressWarnings("unchecked")
    private T furnaceWrite(int index, T value, boolean wantOld) {
        FurnaceSource src = liveSource();
        if (src != null) {
            T r = furnaceSet(src, index, value);
            if (r != DETACHED_VALUE) return r;
        }
        FurnaceChunkMirror m = furnaceClaimant();
        if (m != null) {
            synchronized (m.lock) {
                if (furnaceClaimant() == m) {
                    src = liveSource();   // 기다리는 사이 붙었다 — 보기 길로(잠금은 다시 들어간다)
                    if (src != null) {
                        T r = furnaceSet(src, index, value);
                        if (r != DETACHED_VALUE) return r;
                    }
                    return wantOld ? getAndSet(index, value) : setAndNull(index, value);
                }
            }
        }
        Data<T> before = data;   // 검토 재검토 C1: 쓰기 전 data — 크기 바꿈으로 바뀌어도 그 쓰기가 붙은 기준에 든 것인지 안다
        T old = wantOld ? getAndSet(index, value) : setAndNull(index, value);
        m = furnaceClaimant();
        if (m != null) m.lateWrite(this, before, index, value);   // 쓰는 사이 잡혔다 — 거울이 그 data 가 기준이 됐는지 본다
        return old;
    }

    private T setAndNull(int index, T value) {
        set(index, value);
        return null;
    }

    /** 이 컨테이너를 잡은 거울: 들여오려고 잡은 것, 아니면 붙은 공급원의 것(떨어진 보통 컨테이너면 null). */
    FurnaceChunkMirror furnaceClaimant() {
        Object c = furnaceClaim;
        if (c != null) return (FurnaceChunkMirror) c;
        FurnaceSource src = furnaceSource;
        return src == null ? null : src.mirror();
    }

    /** 들여오기가 이 떨어진 컨테이너를 잡는다/놓는다(주인, 거울 잠금 아래). */
    void furnaceClaim(Object mirror) { furnaceClaim = mirror; }

    /**
     * 붙은 보기의 읽기·쓰기 출처: 플러그인이 {@code data} 필드를 바꿨으면(들여오기 전) null — 이 컨테이너는 그 data 를 바닐라처럼 읽고 쓴다(자기 쓰기를 읽고,
     * 들여오기가 그 내용을 가져간다). 아니면 서버 공급원. 떨어진 컨테이너도 null.
     */
    private FurnaceSource liveSource() {
        FurnaceSource src = furnaceSource;
        if (src == null) return null;
        Data<T> d = data;
        return d == null || d == furnacePlaced ? src : null;
    }

    // ------------------------------------------------------------------ 거울 상태(N2b-4, 호환 모듈 전용)

    /** 붙은 보기인가. */
    boolean furnaceAttached() { return furnaceSource != null; }

    /** 칸 읽기·쓰기가 서버로 가는가(붙은 보기이고 플러그인이 data 를 바꾸지 않았다). */
    boolean furnaceLiveNow() { return liveSource() != null; }

    /** 공급원(없으면 null). */
    Object furnaceSourceObject() { return furnaceSource; }

    /** 거울 기준: Furnace 가 놓은 data(없으면 null). */
    Object furnacePlaced() { return furnacePlaced; }

    /** 지금 data(volatile 읽기). */
    Data<T> furnaceData() { return data; }

    /**
     * 장벽(Field.get 앞, {@code NmsReflect}) — 붙은 보기의 {@code data} 필드를 그 순간 서버 내용(다른 스레드는 공개 스냅샷 + 자기 쓰기)으로 실체화한다.
     * 바닐라 배치(디스크에서 읽은 섹션과 같은 팔레트·비트 수)다. 내용 판이 그대로면 앞서 만든 것을 그대로 둔다. 플러그인이 data 를 바꿨으면 손대지 않는다.
     * 바꿔 넣기는 CAS 라 플러그인의 대입을 덮지 않는다(설계 3.2 규칙 2).
     */
    void furnaceMaterializeData() {
        FurnaceSource src = furnaceSource;
        if (src == null) return;
        Data<T> cur = data;
        if (cur != furnacePlaced) return;   // 플러그인이 바꾼 data — 그대로
        long[] stamp = {Long.MIN_VALUE};
        int[] ids = src.idsStamped(stamp);
        synchronized (src.lock()) {
            if (cur != null && stamp[0] == furnacePlacedStamp && stamp[0] != Long.MIN_VALUE) return;
            if (data != cur || furnacePlaced != cur || furnaceSource != src) return;   // 그 사이 바뀌었다 — 다음 읽기가 다시 본다
            Data<T> next = furnaceDataFromIds(registry, strategy, ids, this);
            if (DATA.compareAndSet(this, cur, next)) {
                furnacePlaced = next;
                furnacePlacedStamp = stamp[0];
            }
        }
    }

    /** 들여온 뒤(주인, 거울 잠금 아래): 지금 data 를 기준으로 삼는다. */
    void furnaceMarkPlaced(Object placed, long stamp) {
        furnacePlaced = placed;
        furnacePlacedStamp = stamp;
    }

    /**
     * 들여오기가 이 컨테이너를 서버 섹션의 보기로 붙인다(주인, 거울 잠금 아래). placed(들여온 내용을 읽은 data)가 기준이 된다 — 그 사이 플러그인이 data 를
     * 바꿨으면 그 data 를 그대로 읽고 쓰다 다음 맞추기가 들여온다. 공급원 쓰기가 마지막이다(그 뒤로 읽기가 서버로 — 부르는 쪽은 그 전에 새 판을 낸다, 검토 R1).
     */
    void furnaceAttach(FurnaceSource src, Object placed, long stamp) {
        furnacePlaced = placed;
        furnacePlacedStamp = stamp;
        furnaceClaim = null;
        furnaceSource = src;
    }

    /**
     * 배열·필드에서 빠진 보기를 떼어 낸다(주인, 거울 잠금 아래): 그 순간의 내용을 data 로 실체화하고(플러그인이 바꾼 data 면 그것 그대로) 공급원을 버린다 —
     * 그 뒤 쓰기는 이 컨테이너에만 남는다(바닐라: 청크에서 빠진 컨테이너).
     */
    void furnaceDetach() {
        FurnaceSource src = furnaceSource;
        if (src == null) return;
        Data<T> d = data;
        if (d == null || d == furnacePlaced) {
            Data<T> next = furnaceDataFromIds(registry, strategy, src.ids(), this);
            DATA.compareAndSet(this, d, next);
        }
        furnacePlaced = null;
        furnaceClaim = null;
        furnaceSource = null;
    }

    /**
     * 들여오기용: 칸마다 서버 번호(값 → 서버 레지스트리 번호는 toId). 값이 null 이거나 번호가 없으면 IllegalStateException(그 섹션은 들여오지 않는다).
     * 붙은 보기면 그 공급원(또는 플러그인이 바꾼 data)에서.
     */
    int[] furnaceReadIds(java.util.function.ToIntFunction<T> toId) { return furnaceReadIds(toId, null); }

    /** 위와 같고, 이 컨테이너의 data 에서 읽었으면 그 data 를 dataOut[0] 에(공급원에서 읽었으면 null) — 들여온 뒤 그 data 를 기준으로 삼는다. */
    int[] furnaceReadIds(java.util.function.ToIntFunction<T> toId, Object[] dataOut) {
        FurnaceSource src = liveSource();
        if (src != null) return src.ids();
        Data<T> d = data;
        if (dataOut != null) dataOut[0] = d;
        if (d == null) throw new IllegalStateException("data 가 null 이다");
        int size = strategy.size();
        if (d.storage().getSize() != size) throw new IllegalStateException("저장소 칸 수가 " + d.storage().getSize() + " 이다(바닐라 " + size + ")");
        int[] out = new int[size];
        Palette<T> p = d.palette();
        BitStorage st = d.storage();
        for (int i = 0; i < size; i++) {
            T v = p.valueFor(st.get(i));
            if (v == null) throw new IllegalStateException("팔레트 항목이 null 이다(칸 " + i + ")");
            int id = toId.applyAsInt(v);
            if (id < 0) throw new IllegalStateException("서버 레지스트리에 없는 값이다: " + v + " (칸 " + i + ")");
            out[i] = id;
        }
        return out;
    }

    /** 장벽(Field.set 뒤): 붙은 보기의 필드를 플러그인이 바꿨다 — 거울에 알린다. */
    void furnaceFieldWritten() {
        FurnaceSource src = furnaceSource;
        if (src != null) src.fieldWritten();
    }

    /**
     * 호환 모듈 전용: 칸별 레지스트리 번호로 진짜 컨테이너를 만든다 — 바닐라가 디스크에서 섹션을 읽을 때(unpack)와 같은 모양: 팔레트 항목은 처음 나온
     * 순서, 비트 수는 {@code calculateBitsForSerialization}, 전역 팔레트면 저장소에 레지스트리 번호.
     */
    public static <T> PalettedContainer<T> furnaceFromIds(IdMap<T> registry, Strategy strategy, int[] registryIds) {
        PalettedContainer<T> c = new PalettedContainer<>(registry, strategy, (Data<T>) null);
        c.data = furnaceDataFromIds(registry, strategy, registryIds, c);
        return c;
    }

    /** 서버 번호 배열 → 바닐라 배치의 data. 팔레트 크기 바꿈 처리기는 owner(그 data 를 쥘 컨테이너 — 다른 컨테이너면 커질 때 그쪽이 커진다). */
    private static <T> Data<T> furnaceDataFromIds(IdMap<T> registry, Strategy strategy, int[] registryIds, PalettedContainer<T> owner) {
        int size = strategy.size();
        List<T> entries = new ArrayList<>();
        Map<Integer, Integer> local = new HashMap<>();
        int[] cells = new int[size];
        for (int i = 0; i < size; i++) {
            int id = registryIds[i];
            Integer k = local.get(id);
            if (k == null) {
                T v = registry.byId(id);
                if (v == null) throw new IllegalStateException("레지스트리에 없는 번호: " + id);
                local.put(id, k = entries.size());
                entries.add(v);
            }
            cells[i] = k;
        }
        int bits = strategy.calculateBitsForSerialization(registry, entries.size());
        Configuration<T> c = strategy.getConfiguration(registry, bits);
        BitStorage storage;
        if (bits == 0) {
            storage = new ZeroBitStorage(size);
        } else if (c.factory() == Strategy.GLOBAL_PALETTE_FACTORY) {
            int[] global = new int[size];
            for (int i = 0; i < size; i++) global[i] = registry.getId(entries.get(cells[i]));
            storage = new SimpleBitStorage(c.bits(), size, global);
        } else {
            storage = new SimpleBitStorage(c.bits(), size, cells);
        }
        return new Data<>(c, storage, c.factory().create(c.bits(), registry, owner, entries));
    }

    /** Furnace: 보기(서버 청크를 읽는 것)인가, 진짜 컨테이너인가. */
    public boolean furnaceIsView() { return furnaceSource != null; }

    /** 보기의 그 순간 값으로 만든 진짜 컨테이너(바닐라 배치가 필요한 읽기용). */
    private PalettedContainer<T> furnaceMaterialize(FurnaceSource src) { return furnaceFromIds(registry, strategy, src.ids()); }

    // ------------------------------------------------------------------ 바닐라 동작

    /** 바닐라 디버그 잠금(ThreadingDetector) — 아무것도 하지 않는다(클래스 설명 참고). */
    public void acquire() {}

    public void release() {}

    private Data<T> createOrReuseData(Data<T> old, int bits) {
        Configuration<T> c = strategy.getConfiguration(registry, bits);
        return old != null && c.equals(old.configuration()) ? old : c.createData(registry, this, strategy.size());
    }

    /** 팔레트가 꽉 찼을 때(바닐라): 새 설정의 데이터에 옛 칸을 옮겨 담고 바꾼다. */
    @Override
    public int onResize(int bits, T value) {
        Data<T> old = data;
        Data<T> next = createOrReuseData(old, bits);
        next.copyFrom(old.palette(), old.storage());
        data = next;
        return next.palette().idFor(value);
    }

    public T getAndSet(int x, int y, int z, T value) {
        acquire();
        try {
            return furnaceWrite(strategy.getIndex(x, y, z), value, true);
        } finally {
            release();
        }
    }

    public T getAndSetUnchecked(int x, int y, int z, T value) { return furnaceWrite(strategy.getIndex(x, y, z), value, true); }

    private T getAndSet(int index, T value) {
        int id = data.palette().idFor(value);
        int old = data.storage().getAndSet(index, id);
        return data.palette().valueFor(old);
    }

    public void set(int x, int y, int z, T value) {
        acquire();
        try {
            furnaceWrite(strategy.getIndex(x, y, z), value, false);
        } finally {
            release();
        }
    }

    private void set(int index, T value) {
        int id = data.palette().idFor(value);
        data.storage().set(index, id);
    }

    @Override
    public T get(int x, int y, int z) { return get(strategy.getIndex(x, y, z)); }

    /** Paper 는 공개(바닐라는 protected) — FAWE 가 바로 부른다. */
    public T get(int index) {
        FurnaceSource src = liveSource();
        if (src != null) {
            checkIndex(index, strategy.size());
            return registry.byId(src.idAt(index));
        }
        Data<T> d = data;
        return d.palette().valueFor(d.storage().get(index));
    }

    /** 칸에 실제로 있는 값마다 한 번(바닐라: 저장소에서 처음 나온 팔레트 번호 순서). */
    @Override
    public void getAll(Consumer<T> consumer) {
        FurnaceSource src = liveSource();
        if (src != null) {
            int[] v = src.ids();
            java.util.BitSet seen = new java.util.BitSet();
            for (int id : v) {
                if (seen.get(id)) continue;
                seen.set(id);
                consumer.accept(registry.byId(id));
            }
            return;
        }
        Palette<T> palette = data.palette();
        List<Integer> order = new ArrayList<>();
        java.util.BitSet seen = new java.util.BitSet();
        data.storage().getAll(id -> {
            if (!seen.get(id)) {
                seen.set(id);
                order.add(id);
            }
        });
        for (int id : order) consumer.accept(palette.valueFor(id));
    }

    /**
     * 바닐라: 비트 수 한 바이트로 설정을 고르고(같으면 데이터 재사용), 팔레트와 long 배열을 읽는다. 길이가 다른 배열은 바닐라처럼 버려진다.
     * 붙은 보기는 늘 새 data 에 읽는다(N2b-4 — data 필드가 바뀌어 들여오기가 그 내용을 서버로 가져간다, 그때까지 이 컨테이너는 그 data 를 읽는다).
     */
    public void read(FriendlyByteBuf buf) {
        FurnaceChunkMirror m = furnaceClaimant();
        if (m == null) {
            readData(buf, false);
            return;
        }
        // 거울이 잡은 컨테이너(붙은 보기·들여오는 중): 늘 새 data 에, 거울 잠금 아래에서 — 붙은 보기의 기준 data 를 고치지 않고, 주인이 잠금 안에서 읽는 내용이 찢기지 않게
        synchronized (m.lock) {
            readData(buf, true);
        }
        FurnaceSource src = furnaceSource;
        if (src != null) src.fieldWritten();
    }

    private void readData(FriendlyByteBuf buf, boolean fresh) {
        acquire();
        try {
            int bits = buf.readByte();
            Data<T> d = createOrReuseData(fresh ? null : data, bits);
            d.palette().read(buf);
            readLongArray(buf, d.storage().getRaw());
            data = d;
        } finally {
            release();
        }
    }

    /** 바닐라 FriendlyByteBuf.readLongArray(long[]) — 길이가 같으면 그 배열에, 다르면 새 배열에 읽는다(새 배열은 버려진다). */
    private static void readLongArray(FriendlyByteBuf buf, long[] into) {
        int max = buf.readableBytes() / 8;
        int n = buf.readVarInt();
        long[] target = into;
        if (target == null || target.length != n) {
            if (n > max) throw new io.netty.handler.codec.DecoderException("LongArray with size " + n + " is bigger than allowed " + max);
            target = new long[n];
        }
        for (int i = 0; i < target.length; i++) target[i] = buf.readLong();
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        FurnaceSource src = liveSource();
        if (src != null) {
            furnaceMaterialize(src).write(buf);
            return;
        }
        acquire();
        try {
            data.write(buf);
        } finally {
            release();
        }
    }

    @Override
    public int getSerializedSize() {
        FurnaceSource src = liveSource();
        if (src != null) return furnaceMaterialize(src).getSerializedSize();
        return data.getSerializedSize();
    }

    /** 바닐라: 팔레트에 들어 있는 값 중 하나라도 맞는가(지워진 칸의 옛 항목도 남아 있으면 참). 보기는 지금 칸에 실제로 있는지로 답한다. */
    @Override
    public boolean maybeHas(Predicate<T> predicate) {
        FurnaceSource src = liveSource();
        if (src != null) {
            int[] v = src.ids();
            java.util.BitSet seen = new java.util.BitSet();
            for (int id : v) {
                if (seen.get(id)) continue;
                seen.set(id);
                if (predicate.test(registry.byId(id))) return true;
            }
            return false;
        }
        return data.palette().maybeHas(predicate);
    }

    /**
     * 사본(바닐라: 데이터 사본). 팔레트 용량 안에서만 독립이다 — 사본의 팔레트는 원본의 resizeHandler 를 쥐어, 사본을 용량 너머로 키우면
     * 원본이 커지고 사본 쓰기는 IllegalArgumentException(바닐라 1.21.1 그대로). 보기면 그 순간 값의 진짜 컨테이너.
     */
    public PalettedContainer<T> copy() {
        FurnaceSource src = liveSource();
        if (src != null) return furnaceMaterialize(src);
        return new PalettedContainer<>(registry, strategy, data.copy());
    }

    /** 바닐라: 팔레트 0번 값으로 채운 새 컨테이너. */
    @Override
    public PalettedContainer<T> recreate() {
        FurnaceSource src = liveSource();
        if (src != null) return furnaceMaterialize(src).recreate();
        return new PalettedContainer<>(registry, data.palette().valueFor(0), strategy);
    }

    /** 값마다 칸 수(0 인 값은 알리지 않는다). 부르는 순서는 처음 나온 순서다(바닐라는 해시 순서). */
    @Override
    public void count(CountConsumer<T> consumer) {
        FurnaceSource src = liveSource();
        if (src != null) {
            int[] v = src.ids();
            LinkedHashMap<Integer, int[]> counts = new LinkedHashMap<>();
            for (int id : v) counts.computeIfAbsent(id, k -> new int[1])[0]++;
            for (var e : counts.entrySet()) consumer.accept(registry.byId(e.getKey()), e.getValue()[0]);
            return;
        }
        Data<T> d = data;
        if (d.palette().getSize() == 1) {
            consumer.accept(d.palette().valueFor(0), d.storage().getSize());
            return;
        }
        LinkedHashMap<Integer, int[]> counts = new LinkedHashMap<>();
        d.storage().getAll(id -> counts.computeIfAbsent(id, k -> new int[1])[0]++);
        for (var e : counts.entrySet()) consumer.accept(d.palette().valueFor(e.getKey()), e.getValue()[0]);
    }

    // ------------------------------------------------------------------ 바닐라 중첩 형

    /** 크기(칸 수)와 비트 수 → 팔레트 종류 규칙(바닐라 1.21.1 그대로). */
    public abstract static class Strategy {
        public static final Palette.Factory SINGLE_VALUE_PALETTE_FACTORY = SingleValuePalette::create;
        public static final Palette.Factory LINEAR_PALETTE_FACTORY = LinearPalette::create;
        public static final Palette.Factory HASHMAP_PALETTE_FACTORY = HashMapPalette::create;
        static final Palette.Factory GLOBAL_PALETTE_FACTORY = GlobalPalette::create;

        /** 블록 상태(16³): 0 비트 = 값 하나, 1~4 = 배열(늘 4 비트), 5~8 = 해시, 그 밖 = 전역(레지스트리 크기의 ceillog2). */
        public static final Strategy SECTION_STATES = new Strategy(4) {
            @Override
            public <A> Configuration<A> getConfiguration(IdMap<A> registry, int bits) {
                return switch (bits) {
                    case 0 -> new Configuration<>(SINGLE_VALUE_PALETTE_FACTORY, bits);
                    case 1, 2, 3, 4 -> new Configuration<>(LINEAR_PALETTE_FACTORY, 4);
                    case 5, 6, 7, 8 -> new Configuration<>(HASHMAP_PALETTE_FACTORY, bits);
                    default -> new Configuration<>(GLOBAL_PALETTE_FACTORY, ceillog2(registry.size()));
                };
            }
        };

        /** 바이옴(4³): 0 비트 = 값 하나, 1~3 = 배열(그 비트), 그 밖 = 전역. */
        public static final Strategy SECTION_BIOMES = new Strategy(2) {
            @Override
            public <A> Configuration<A> getConfiguration(IdMap<A> registry, int bits) {
                return switch (bits) {
                    case 0 -> new Configuration<>(SINGLE_VALUE_PALETTE_FACTORY, bits);
                    case 1, 2, 3 -> new Configuration<>(LINEAR_PALETTE_FACTORY, bits);
                    default -> new Configuration<>(GLOBAL_PALETTE_FACTORY, ceillog2(registry.size()));
                };
            }
        };

        private final int sizeBits;

        Strategy(int sizeBits) { this.sizeBits = sizeBits; }

        public int size() { return 1 << sizeBits * 3; }

        /** 칸 번호: (y·크기 + z)·크기 + x — 좌표를 자르지 않는다(바닐라와 같다). */
        public int getIndex(int x, int y, int z) { return (y << sizeBits | z) << sizeBits | x; }

        public abstract <A> Configuration<A> getConfiguration(IdMap<A> registry, int bits);

        <A> int calculateBitsForSerialization(IdMap<A> registry, int paletteSize) {
            int bits = ceillog2(paletteSize);
            Configuration<A> c = getConfiguration(registry, bits);
            return c.factory() == GLOBAL_PALETTE_FACTORY ? bits : c.bits();
        }
    }

    /** 바닐라 Mth.ceillog2(0 과 1 은 0). */
    static int ceillog2(int n) { return n <= 1 ? 0 : 32 - Integer.numberOfLeadingZeros(n - 1); }

    /** 설정·저장소·팔레트 한 벌(바닐라 레코드, Paper 처럼 공개). 필드 이름이 바닐라와 같아 리플렉션으로 읽힌다. */
    public static record Data<T>(Configuration<T> configuration, BitStorage storage, Palette<T> palette) {

        /** 다른 팔레트·저장소의 칸 값을 이 데이터로 옮겨 담는다(이 팔레트에 차례로 번호를 매긴다). */
        public void copyFrom(Palette<T> fromPalette, BitStorage fromStorage) {
            for (int i = 0; i < fromStorage.getSize(); i++) {
                T v = fromPalette.valueFor(fromStorage.get(i));
                storage.set(i, palette.idFor(v));
            }
        }

        public int getSerializedSize() {
            return 1 + palette.getSerializedSize() + FriendlyByteBuf.getVarIntSize(storage.getRaw().length) + storage.getRaw().length * 8;
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeByte(storage.getBits());
            palette.write(buf);
            buf.writeLongArray(storage.getRaw());
        }

        public Data<T> copy() { return new Data<>(configuration, storage.copy(), palette.copy()); }
    }

    /** 팔레트 종류와 비트 수(바닐라 레코드, Paper 처럼 공개). 같은 (종류, 비트)면 equals. */
    public static record Configuration<T>(Palette.Factory factory, int bits) {

        public Data<T> createData(IdMap<T> registry, PaletteResize<T> resizeHandler, int size) {
            BitStorage storage = bits == 0 ? new ZeroBitStorage(size) : new SimpleBitStorage(bits, size);
            Palette<T> palette = factory.create(bits, registry, resizeHandler, List.of());
            return new Data<>(this, storage, palette);
        }
    }

    @FunctionalInterface
    public interface CountConsumer<T> {
        void accept(T value, int count);
    }
}
