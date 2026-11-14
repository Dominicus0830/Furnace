package net.minecraft.world.level.chunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.function.Supplier;
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
 *     서버 청크와 무관한 떨어진 객체다(플러그인이 청크 섹션 배열에 넣어도 아직 서버에 닿지 않는다 — 거울·들여오기는 N2b-4).</li>
 * <li><b>보기</b>(N2a, {@code LevelChunkSection#getStates()}·{@code getBiomes()} of 로드된 청크): 읽을 때마다 서버 청크의 지금 값을 본다(소유 region
 *     스레드 전용). 쓰기({@code set}·{@code getAndSet}·{@code read})는 UnsupportedNmsCall(살아 있는 섹션 쓰기는 N2b-2). 필드 {@code data} 는 null 이다
 *     (보기의 바닐라 배치 실체화는 N2b-4). copy() 는 디스크에서 읽은 바닐라 섹션처럼(팔레트 = 처음 나온 순서, 비트 수 = 직렬화 규칙) 진짜 컨테이너를 만든다.</li>
 * </ul>
 * 바닐라와 다를 수 있는 것: {@link #count} 가 값마다 부르는 순서(바닐라는 fastutil 해시 순서, 여기는 처음 나온 순서 — 개수는 같다),
 * 보기의 팔레트 자체(항목 순서·비트 수 — 살아 있는 섹션의 바닐라 팔레트는 쓰기 이력에 달려 있다). 디버그 잠금(ThreadingDetector)은 두지 않는다 —
 * acquire·release 는 아무것도 하지 않는다(가정: Paper 1.21.1 도 ThreadingDetector 를 끈다, 노트북 대조 V6 에서 확인).
 * 코덱(codecRW·codecRO·pack)은 DataFixerUpper shim 이 없어 두지 않았다.
 */
public class PalettedContainer<T> implements PaletteResize<T>, PalettedContainerRO<T> {

    private static final int MIN_PALETTE_BITS = 0;

    private final PaletteResize<T> dummyPaletteResize = (bits, value) -> 0;
    private final IdMap<T> registry;
    private volatile Data<T> data;
    private final Strategy strategy;
    /** Furnace: 서버 섹션 보기면 번호 공급원(진짜 컨테이너면 null). */
    private final FurnaceSource furnaceSource;

    /** 보기의 서버 번호 공급원. */
    private interface FurnaceSource {
        int idAt(int index);

        int[] ids();
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

    /**
     * 호환 모듈 전용: 서버 섹션 보기. idAt 은 한 칸(칸 번호는 바닐라 {@link Strategy#getIndex}), ids 는 모든 칸(새 배열)의 레지스트리 번호를 서버에서 읽는다.
     */
    public static <T> PalettedContainer<T> furnaceView(IdMap<T> registry, Strategy strategy, IntUnaryOperator idAt, Supplier<int[]> ids) {
        int size = strategy.size();
        return new PalettedContainer<>(registry, strategy, new FurnaceSource() {
            @Override
            public int idAt(int index) {
                if (index < 0 || index >= size) {
                    throw new IllegalArgumentException(String.format("The value %s is not in the specified inclusive range of %s to %s", (long) index, 0L, (long) (size - 1)));
                }
                return idAt.applyAsInt(index);
            }

            @Override
            public int[] ids() { return ids.get(); }
        });
    }

    /**
     * 호환 모듈 전용: 칸별 레지스트리 번호로 진짜 컨테이너를 만든다 — 바닐라가 디스크에서 섹션을 읽을 때(unpack)와 같은 모양: 팔레트 항목은 처음 나온
     * 순서, 비트 수는 {@code calculateBitsForSerialization}, 전역 팔레트면 저장소에 레지스트리 번호.
     */
    public static <T> PalettedContainer<T> furnaceFromIds(IdMap<T> registry, Strategy strategy, int[] registryIds) {
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
        return new PalettedContainer<>(registry, strategy, c, storage, entries);
    }

    /** Furnace: 보기(서버 청크를 읽는 것)인가, 진짜 컨테이너인가. */
    public boolean furnaceIsView() { return furnaceSource != null; }

    private RuntimeException viewWrite(String what) {
        return new org.domi.furnace.compat.nms.UnsupportedNmsCall("PalettedContainer#" + what + " (청크 섹션 보기)",
                "로드된 청크 섹션을 직접 고치는 길은 아직 없다(N2b-2). 블록은 Level#setBlock·LevelChunk#setBlockState 로 바꾸고, 사본(copy())·새로 만든 컨테이너는 고칠 수 있다");
    }

    /** 보기의 그 순간 값으로 만든 진짜 컨테이너(바닐라 배치가 필요한 읽기용). */
    private PalettedContainer<T> furnaceMaterialize() { return furnaceFromIds(registry, strategy, furnaceSource.ids()); }

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
        if (furnaceSource != null) throw viewWrite("getAndSet");
        acquire();
        try {
            return getAndSet(strategy.getIndex(x, y, z), value);
        } finally {
            release();
        }
    }

    public T getAndSetUnchecked(int x, int y, int z, T value) {
        if (furnaceSource != null) throw viewWrite("getAndSetUnchecked");
        return getAndSet(strategy.getIndex(x, y, z), value);
    }

    private T getAndSet(int index, T value) {
        int id = data.palette().idFor(value);
        int old = data.storage().getAndSet(index, id);
        return data.palette().valueFor(old);
    }

    public void set(int x, int y, int z, T value) {
        if (furnaceSource != null) throw viewWrite("set");
        acquire();
        try {
            set(strategy.getIndex(x, y, z), value);
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

    protected T get(int index) {
        if (furnaceSource != null) return registry.byId(furnaceSource.idAt(index));
        Data<T> d = data;
        return d.palette().valueFor(d.storage().get(index));
    }

    /** 칸에 실제로 있는 값마다 한 번(바닐라: 저장소에서 처음 나온 팔레트 번호 순서). */
    @Override
    public void getAll(Consumer<T> consumer) {
        if (furnaceSource != null) {
            int[] v = furnaceSource.ids();
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

    /** 바닐라: 비트 수 한 바이트로 설정을 고르고(같으면 데이터 재사용), 팔레트와 long 배열을 읽는다. 길이가 다른 배열은 바닐라처럼 버려진다. */
    public void read(FriendlyByteBuf buf) {
        if (furnaceSource != null) throw viewWrite("read");
        acquire();
        try {
            int bits = buf.readByte();
            Data<T> d = createOrReuseData(data, bits);
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
        if (furnaceSource != null) {
            furnaceMaterialize().write(buf);
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
        if (furnaceSource != null) return furnaceMaterialize().getSerializedSize();
        return data.getSerializedSize();
    }

    /** 바닐라: 팔레트에 들어 있는 값 중 하나라도 맞는가(지워진 칸의 옛 항목도 남아 있으면 참). 보기는 지금 칸에 실제로 있는지로 답한다. */
    @Override
    public boolean maybeHas(Predicate<T> predicate) {
        if (furnaceSource != null) {
            int[] v = furnaceSource.ids();
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

    /** 독립 사본(바닐라: 데이터 사본). 보기면 그 순간 값의 진짜 컨테이너. */
    public PalettedContainer<T> copy() {
        if (furnaceSource != null) return furnaceMaterialize();
        return new PalettedContainer<>(registry, strategy, data.copy());
    }

    /** 바닐라: 팔레트 0번 값으로 채운 새 컨테이너. */
    @Override
    public PalettedContainer<T> recreate() {
        if (furnaceSource != null) return furnaceMaterialize().recreate();
        return new PalettedContainer<>(registry, data.palette().valueFor(0), strategy);
    }

    /** 값마다 칸 수(0 인 값은 알리지 않는다). 부르는 순서는 처음 나온 순서다(바닐라는 해시 순서). */
    @Override
    public void count(CountConsumer<T> consumer) {
        if (furnaceSource != null) {
            int[] v = furnaceSource.ids();
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
