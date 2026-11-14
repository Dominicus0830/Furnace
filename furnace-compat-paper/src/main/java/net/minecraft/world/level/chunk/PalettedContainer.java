package net.minecraft.world.level.chunk;

import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;

/**
 * 팔레트 컨테이너 shim(Mojang 이름, N2a). 두 가지가 있다.
 * <ul>
 * <li><b>보기</b>({@code LevelChunkSection#getStates()}·{@code getBiomes()}): 읽을 때마다 서버 청크의 지금 값을 본다(소유 region 스레드 전용).
 *     쓰기({@code set}·{@code getAndSet})는 지원하지 않는다(N2b — 서버 청크의 원본 배열을 바깥이 고치는 길은 아직 없다).</li>
 * <li><b>사본</b>({@link #copy()}): 그 순간 값의 독립 사본. 바닐라 사본처럼 읽고 고칠 수 있고 서버 청크에는 닿지 않는다.</li>
 * </ul>
 * 칸 번호는 바닐라 Strategy.getIndex 와 같다((y·크기 + z)·크기 + x, 블록 크기 16, 바이옴 크기 4). 값은 서버 번호 → shim 객체 표로 푼다.
 * 팔레트 자체(항목 순서·비트 수)는 바닐라와 다를 수 있다 — 그래서 {@link #count} 의 부르는 순서와 {@link #maybeHas} 의 "팔레트에 남은 옛 항목"
 * 판정은 바닐라와 같다고 보장하지 않는다(maybeHas 는 지금 칸들에 실제로 있는지로 답한다). 직렬화(write·read·pack)는 없다.
 */
public class PalettedContainer<T> implements PalettedContainerRO<T> {

    /** 서버 번호 공급원. */
    private interface Source {
        int idAt(int index);

        int[] ids();
    }

    private final int sizeBits;
    private final IntFunction<T> decode;
    private final java.util.function.ToIntFunction<T> encode;
    private final Source live;
    private final int[] own;   // 사본이면 칸별 번호, 보기면 null

    private PalettedContainer(int sizeBits, IntFunction<T> decode, java.util.function.ToIntFunction<T> encode, Source live, int[] own) {
        this.sizeBits = sizeBits;
        this.decode = decode;
        this.encode = encode;
        this.live = live;
        this.own = own;
    }

    /**
     * 호환 모듈 전용: 보기. idAt 은 한 칸, ids 는 모든 칸(새 배열)을 서버에서 읽는다. encode 는 사본에 쓸 때 값 → 번호.
     */
    public static <T> PalettedContainer<T> furnaceView(int sizeBits, IntFunction<T> decode, java.util.function.ToIntFunction<T> encode,
                                                     java.util.function.IntUnaryOperator idAt, java.util.function.Supplier<int[]> ids) {
        return new PalettedContainer<>(sizeBits, decode, encode, new Source() {
            @Override
            public int idAt(int index) { return idAt.applyAsInt(index); }

            @Override
            public int[] ids() { return ids.get(); }
        }, null);
    }

    private int size() { return 1 << (sizeBits * 3); }

    private int index(int x, int y, int z) { return (y << sizeBits | z) << sizeBits | x; }

    private int idAt(int index) { return own != null ? own[index] : live.idAt(index); }

    private int[] ids() { return own != null ? own : live.ids(); }

    /** Furnace: 보기(서버 청크를 읽는 것)인가, 사본인가. */
    public boolean furnaceIsView() { return own == null; }

    /** 바닐라 디버그 잠금(ThreadingDetector). Furnace 는 소유 region 스레드 규칙으로 막으므로 아무것도 하지 않는다. */
    public void acquire() {}

    public void release() {}

    @Override
    public T get(int x, int y, int z) { return decode.apply(idAt(index(x, y, z))); }

    protected T get(int index) { return decode.apply(idAt(index)); }

    public T getAndSet(int x, int y, int z, T value) { return getAndSetUnchecked(x, y, z, value); }

    public T getAndSetUnchecked(int x, int y, int z, T value) {
        int[] o = writable("getAndSet");
        int i = index(x, y, z);
        int old = o[i];
        o[i] = encode.applyAsInt(value);
        return decode.apply(old);
    }

    public void set(int x, int y, int z, T value) { writable("set")[index(x, y, z)] = encode.applyAsInt(value); }

    private int[] writable(String what) {
        if (own == null) {
            throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("PalettedContainer#" + what + " (청크 섹션 보기)",
                    "서버 청크 섹션을 직접 고치는 길은 아직 없다(N2b). 블록은 Level#setBlock·LevelChunk#setBlockState 로 바꾸고, 사본(copy())은 고칠 수 있다");
        }
        return own;
    }

    @Override
    public void getAll(Consumer<T> consumer) {
        int[] v = ids();
        for (int i = 0, n = size(); i < n; i++) consumer.accept(decode.apply(v[i]));
    }

    @Override
    public boolean maybeHas(Predicate<T> predicate) {
        int[] v = ids();
        java.util.BitSet seen = new java.util.BitSet();
        for (int i = 0, n = size(); i < n; i++) {
            int id = v[i];
            if (seen.get(id)) continue;
            seen.set(id);
            if (predicate.test(decode.apply(id))) return true;
        }
        return false;
    }

    /** 값마다 칸 수를 알린다(0 인 값은 알리지 않는다). 부르는 순서는 처음 나온 칸 순서다(바닐라는 팔레트 순서). */
    @Override
    public void count(CountConsumer<T> consumer) {
        int[] v = ids();
        java.util.LinkedHashMap<Integer, int[]> counts = new java.util.LinkedHashMap<>();
        for (int i = 0, n = size(); i < n; i++) counts.computeIfAbsent(v[i], k -> new int[1])[0]++;
        for (var e : counts.entrySet()) consumer.accept(decode.apply(e.getKey()), e.getValue()[0]);
    }

    /** 그 순간 값의 독립 사본(고칠 수 있다, 서버 청크에 닿지 않는다). */
    public PalettedContainer<T> copy() {
        int[] v = ids();
        return new PalettedContainer<>(sizeBits, decode, encode, null, own != null ? v.clone() : v);   // 보기의 ids() 는 이미 새 배열
    }

    @FunctionalInterface
    public interface CountConsumer<T> {
        void accept(T value, int count);
    }
}
