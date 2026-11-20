package net.minecraft.nbt;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * NBT 용 DataFixerUpper 연산(Mojang 이름 shim, T-191/T-200). 바닐라 1.21.1 {@code NbtOps} 를 바이트코드와 대조해 옮겼다: 수·문자열 만들기, 리스트 모으기
 * (처음이 byte·int·long 이면 배열 태그, 섞이면 {@code {"": 값}} 으로 감싼 묶음 리스트), 묶음 합치기·훑기, {@code convertTo}. 이 클래스는 DataFixerUpper 가
 * 있어야 올라간다(없는 서버에서는 쓸 일이 없다 — FAWE 처럼 DFU 를 쓰는 플러그인이 있으면 서버가 그 묶음을 붙인다). 바닐라 {@code NbtRecordBuilder} 대신
 * 기본 {@code mapBuilder()} 를 쓴다(결과 태그는 같다 — 키 순서는 둘 다 HashMap).
 */
public class NbtOps implements DynamicOps<Tag> {

    public static final NbtOps INSTANCE = new NbtOps();

    private static final String WRAPPER_MARKER = "";

    protected NbtOps() {}

    @Override
    public Tag empty() { return EndTag.INSTANCE; }

    @Override
    public <U> U convertTo(DynamicOps<U> out, Tag tag) {
        return switch (tag.getId()) {
            case 0 -> out.empty();
            case 1 -> out.createByte(((NumericTag) tag).getAsByte());
            case 2 -> out.createShort(((NumericTag) tag).getAsShort());
            case 3 -> out.createInt(((NumericTag) tag).getAsInt());
            case 4 -> out.createLong(((NumericTag) tag).getAsLong());
            case 5 -> out.createFloat(((NumericTag) tag).getAsFloat());
            case 6 -> out.createDouble(((NumericTag) tag).getAsDouble());
            case 7 -> out.createByteList(ByteBuffer.wrap(((ByteArrayTag) tag).getAsByteArray()));
            case 8 -> out.createString(tag.getAsString());
            case 9 -> this.convertList(out, tag);
            case 10 -> this.convertMap(out, tag);
            case 11 -> out.createIntList(Arrays.stream(((IntArrayTag) tag).getAsIntArray()));
            case 12 -> out.createLongList(Arrays.stream(((LongArrayTag) tag).getAsLongArray()));
            default -> throw new IllegalStateException("Unknown tag type: " + tag);
        };
    }

    @Override
    public DataResult<Number> getNumberValue(Tag tag) {
        if (tag instanceof NumericTag n) return DataResult.success(n.getAsNumber());
        return DataResult.error(() -> "Not a number");
    }

    @Override
    public Tag createNumeric(Number n) { return DoubleTag.valueOf(n.doubleValue()); }

    @Override
    public Tag createByte(byte v) { return ByteTag.valueOf(v); }

    @Override
    public Tag createShort(short v) { return ShortTag.valueOf(v); }

    @Override
    public Tag createInt(int v) { return IntTag.valueOf(v); }

    @Override
    public Tag createLong(long v) { return LongTag.valueOf(v); }

    @Override
    public Tag createFloat(float v) { return FloatTag.valueOf(v); }

    @Override
    public Tag createDouble(double v) { return DoubleTag.valueOf(v); }

    @Override
    public Tag createBoolean(boolean v) { return ByteTag.valueOf(v); }

    @Override
    public DataResult<String> getStringValue(Tag tag) {
        if (tag instanceof StringTag s) return DataResult.success(s.getAsString());
        return DataResult.error(() -> "Not a string");
    }

    @Override
    public Tag createString(String s) { return StringTag.valueOf(s); }

    @Override
    public DataResult<Tag> mergeToList(Tag list, Tag value) {
        return createCollector(list)
                .map(c -> DataResult.success(c.accept(value).result()))
                .orElseGet(() -> DataResult.error(() -> "mergeToList called with not a list: " + list, list));
    }

    @Override
    public DataResult<Tag> mergeToList(Tag list, List<Tag> values) {
        return createCollector(list)
                .map(c -> DataResult.success(c.acceptAll(values).result()))
                .orElseGet(() -> DataResult.error(() -> "mergeToList called with not a list: " + list, list));
    }

    @Override
    public DataResult<Tag> mergeToMap(Tag map, Tag key, Tag value) {
        if (!(map instanceof CompoundTag) && !(map instanceof EndTag)) return DataResult.error(() -> "mergeToMap called with not a map: " + map, map);
        if (!(key instanceof StringTag)) return DataResult.error(() -> "key is not a string: " + key, map);
        CompoundTag out = map instanceof CompoundTag c ? c.shallowCopy() : new CompoundTag();
        out.put(key.getAsString(), value);
        return DataResult.success(out);
    }

    @Override
    public DataResult<Tag> mergeToMap(Tag map, MapLike<Tag> values) {
        if (!(map instanceof CompoundTag) && !(map instanceof EndTag)) return DataResult.error(() -> "mergeToMap called with not a map: " + map, map);
        CompoundTag out = map instanceof CompoundTag c ? c.shallowCopy() : new CompoundTag();
        java.util.ArrayList<Tag> missed = new java.util.ArrayList<>();
        values.entries().forEach(e -> {
            Tag key = e.getFirst();
            if (key instanceof StringTag) out.put(key.getAsString(), e.getSecond());
            else missed.add(key);
        });
        if (!missed.isEmpty()) return DataResult.error(() -> "some keys are not strings: " + missed, out);
        return DataResult.success(out);
    }

    @Override
    public DataResult<Tag> mergeToMap(Tag map, Map<Tag, Tag> values) {
        if (!(map instanceof CompoundTag) && !(map instanceof EndTag)) return DataResult.error(() -> "mergeToMap called with not a map: " + map, map);
        CompoundTag out = map instanceof CompoundTag c ? c.shallowCopy() : new CompoundTag();
        java.util.ArrayList<Tag> missed = new java.util.ArrayList<>();
        for (Map.Entry<Tag, Tag> e : values.entrySet()) {
            Tag key = e.getKey();
            if (key instanceof StringTag) out.put(key.getAsString(), e.getValue());
            else missed.add(key);
        }
        if (!missed.isEmpty()) return DataResult.error(() -> "some keys are not strings: " + missed, out);
        return DataResult.success(out);
    }

    @Override
    public DataResult<Stream<Pair<Tag, Tag>>> getMapValues(Tag tag) {
        if (tag instanceof CompoundTag c) return DataResult.success(c.entrySet().stream().map(e -> Pair.of(createString(e.getKey()), e.getValue())));
        return DataResult.error(() -> "Not a map: " + tag);
    }

    @Override
    public DataResult<Consumer<BiConsumer<Tag, Tag>>> getMapEntries(Tag tag) {
        if (tag instanceof CompoundTag c) {
            return DataResult.success(consumer -> {
                for (Map.Entry<String, Tag> e : c.entrySet()) consumer.accept(createString(e.getKey()), e.getValue());
            });
        }
        return DataResult.error(() -> "Not a map: " + tag);
    }

    @Override
    public DataResult<MapLike<Tag>> getMap(Tag tag) {
        if (tag instanceof CompoundTag c) {
            return DataResult.success(new MapLike<Tag>() {
                @Override
                public Tag get(Tag key) { return c.get(key.getAsString()); }

                @Override
                public Tag get(String key) { return c.get(key); }

                @Override
                public Stream<Pair<Tag, Tag>> entries() {
                    return c.entrySet().stream().map(e -> Pair.of(NbtOps.this.createString(e.getKey()), e.getValue()));
                }

                @Override
                public String toString() { return "MapLike[" + c + "]"; }
            });
        }
        return DataResult.error(() -> "Not a map: " + tag);
    }

    @Override
    public Tag createMap(Stream<Pair<Tag, Tag>> map) {
        CompoundTag out = new CompoundTag();
        map.forEach(p -> out.put(p.getFirst().getAsString(), p.getSecond()));
        return out;
    }

    private static Tag tryUnwrap(CompoundTag tag) {
        Tag inner;
        if (tag.size() == 1 && (inner = tag.get(WRAPPER_MARKER)) != null) return inner;
        return tag;
    }

    @Override
    public DataResult<Stream<Tag>> getStream(Tag tag) {
        if (tag instanceof ListTag l) {
            if (l.getElementType() == Tag.TAG_COMPOUND) return DataResult.success(l.stream().map(t -> tryUnwrap((CompoundTag) t)));
            return DataResult.success(l.stream());
        }
        if (tag instanceof CollectionTag<?> c) return DataResult.success(c.stream().map(t -> (Tag) t));
        return DataResult.error(() -> "Not a list");
    }

    @Override
    public DataResult<Consumer<Consumer<Tag>>> getList(Tag tag) {
        if (tag instanceof ListTag l) {
            if (l.getElementType() == Tag.TAG_COMPOUND) {
                return DataResult.success(consumer -> {
                    for (Tag t : l) consumer.accept(tryUnwrap((CompoundTag) t));
                });
            }
            return DataResult.success(l::forEach);
        }
        if (tag instanceof CollectionTag<?> c) return DataResult.success(consumer -> c.forEach(t -> consumer.accept(t)));
        return DataResult.error(() -> "Not a list: " + tag);
    }

    @Override
    public DataResult<ByteBuffer> getByteBuffer(Tag tag) {
        if (tag instanceof ByteArrayTag a) return DataResult.success(ByteBuffer.wrap(a.getAsByteArray()));
        return DynamicOps.super.getByteBuffer(tag);
    }

    @Override
    public Tag createByteList(ByteBuffer buffer) {
        ByteBuffer dup = buffer.duplicate().clear();
        byte[] data = new byte[buffer.capacity()];
        dup.get(0, data, 0, data.length);
        return new ByteArrayTag(data);
    }

    @Override
    public DataResult<IntStream> getIntStream(Tag tag) {
        if (tag instanceof IntArrayTag a) return DataResult.success(Arrays.stream(a.getAsIntArray()));
        return DynamicOps.super.getIntStream(tag);
    }

    @Override
    public Tag createIntList(IntStream stream) { return new IntArrayTag(stream.toArray()); }

    @Override
    public DataResult<LongStream> getLongStream(Tag tag) {
        if (tag instanceof LongArrayTag a) return DataResult.success(Arrays.stream(a.getAsLongArray()));
        return DynamicOps.super.getLongStream(tag);
    }

    @Override
    public Tag createLongList(LongStream stream) { return new LongArrayTag(stream.toArray()); }

    @Override
    public Tag createList(Stream<Tag> stream) {
        ListCollector collector = InitialListCollector.INSTANCE;
        for (var it = stream.iterator(); it.hasNext(); ) collector = collector.accept(it.next());
        return collector.result();
    }

    @Override
    public Tag remove(Tag tag, String key) {
        if (tag instanceof CompoundTag c) {
            CompoundTag copy = c.shallowCopy();
            copy.remove(key);
            return copy;
        }
        return tag;
    }

    @Override
    public String toString() { return "NBT"; }

    private static Optional<ListCollector> createCollector(Tag tag) {
        if (tag instanceof EndTag) return Optional.of(InitialListCollector.INSTANCE);
        if (tag instanceof CollectionTag<?> c) {
            if (c.isEmpty()) return Optional.of(InitialListCollector.INSTANCE);
            if (c instanceof ListTag l) {
                return switch (l.getElementType()) {
                    case 0 -> Optional.of(InitialListCollector.INSTANCE);
                    case 10 -> Optional.of(new HeterogenousListCollector(l));
                    default -> Optional.of(new HomogenousListCollector(l));
                };
            }
            if (c instanceof ByteArrayTag a) return Optional.of(new ByteListCollector(a.getAsByteArray()));
            if (c instanceof IntArrayTag a) return Optional.of(new IntListCollector(a.getAsIntArray()));
            if (c instanceof LongArrayTag a) return Optional.of(new LongListCollector(a.getAsLongArray()));
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ 리스트 모으기(바닐라 NbtOps$*ListCollector)

    interface ListCollector {
        ListCollector accept(Tag tag);

        default ListCollector acceptAll(Iterable<Tag> tags) {
            ListCollector c = this;
            for (Tag t : tags) c = c.accept(t);
            return c;
        }

        Tag result();
    }

    static final class InitialListCollector implements ListCollector {
        static final InitialListCollector INSTANCE = new InitialListCollector();

        private InitialListCollector() {}

        @Override
        public ListCollector accept(Tag tag) {
            if (tag instanceof CompoundTag c) return new HeterogenousListCollector().accept(c);
            if (tag instanceof ByteTag b) return new ByteListCollector(b.getAsByte());
            if (tag instanceof IntTag i) return new IntListCollector(i.getAsInt());
            if (tag instanceof LongTag l) return new LongListCollector(l.getAsLong());
            return new HomogenousListCollector(tag);
        }

        @Override
        public Tag result() { return new ListTag(); }
    }

    static final class HeterogenousListCollector implements ListCollector {
        private final ListTag result = new ListTag();

        HeterogenousListCollector() {}

        HeterogenousListCollector(Collection<Tag> tags) { result.addAll(tags); }

        private static boolean isWrapper(CompoundTag tag) { return tag.size() == 1 && tag.contains(WRAPPER_MARKER); }

        private static Tag wrapIfNeeded(Tag tag) {
            if (tag instanceof CompoundTag c && !isWrapper(c)) return c;
            return wrapElement(tag);
        }

        private static CompoundTag wrapElement(Tag tag) {
            CompoundTag c = new CompoundTag();
            c.put(WRAPPER_MARKER, tag);
            return c;
        }

        /** 바이트·정수·롱 모으개가 다른 종류를 만나 넘어올 때: 지금까지 모은 값을 하나씩 감싼다. */
        static HeterogenousListCollector ofWrapped(List<? extends Tag> tags) {
            HeterogenousListCollector c = new HeterogenousListCollector();
            for (Tag t : tags) c.result.add(wrapElement(t));
            return c;
        }

        @Override
        public ListCollector accept(Tag tag) {
            result.add(wrapIfNeeded(tag));
            return this;
        }

        @Override
        public Tag result() { return result; }
    }

    static final class HomogenousListCollector implements ListCollector {
        private final ListTag result = new ListTag();

        HomogenousListCollector(Tag first) { result.add(first); }

        HomogenousListCollector(ListTag list) { result.addAll(list); }

        @Override
        public ListCollector accept(Tag tag) {
            if (tag.getId() != result.getElementType()) return new HeterogenousListCollector().acceptAll(result).accept(tag);
            result.add(tag);
            return this;
        }

        @Override
        public Tag result() { return result; }
    }

    static final class ByteListCollector implements ListCollector {
        private byte[] values;
        private int size;

        ByteListCollector(byte first) {
            values = new byte[8];
            values[size++] = first;
        }

        ByteListCollector(byte[] all) {
            values = Arrays.copyOf(all, Math.max(all.length, 8));
            size = all.length;
        }

        @Override
        public ListCollector accept(Tag tag) {
            if (tag instanceof ByteTag b) {
                if (size == values.length) values = Arrays.copyOf(values, size * 2);
                values[size++] = b.getAsByte();
                return this;
            }
            java.util.ArrayList<Tag> wrapped = new java.util.ArrayList<>(size);
            for (int i = 0; i < size; i++) wrapped.add(ByteTag.valueOf(values[i]));
            return HeterogenousListCollector.ofWrapped(wrapped).accept(tag);
        }

        @Override
        public Tag result() { return new ByteArrayTag(Arrays.copyOf(values, size)); }
    }

    static final class IntListCollector implements ListCollector {
        private int[] values;
        private int size;

        IntListCollector(int first) {
            values = new int[8];
            values[size++] = first;
        }

        IntListCollector(int[] all) {
            values = Arrays.copyOf(all, Math.max(all.length, 8));
            size = all.length;
        }

        @Override
        public ListCollector accept(Tag tag) {
            if (tag instanceof IntTag i) {
                if (size == values.length) values = Arrays.copyOf(values, size * 2);
                values[size++] = i.getAsInt();
                return this;
            }
            java.util.ArrayList<Tag> wrapped = new java.util.ArrayList<>(size);
            for (int i = 0; i < size; i++) wrapped.add(IntTag.valueOf(values[i]));
            return HeterogenousListCollector.ofWrapped(wrapped).accept(tag);
        }

        @Override
        public Tag result() { return new IntArrayTag(Arrays.copyOf(values, size)); }
    }

    static final class LongListCollector implements ListCollector {
        private long[] values;
        private int size;

        LongListCollector(long first) {
            values = new long[8];
            values[size++] = first;
        }

        LongListCollector(long[] all) {
            values = Arrays.copyOf(all, Math.max(all.length, 8));
            size = all.length;
        }

        @Override
        public ListCollector accept(Tag tag) {
            if (tag instanceof LongTag l) {
                if (size == values.length) values = Arrays.copyOf(values, size * 2);
                values[size++] = l.getAsLong();
                return this;
            }
            java.util.ArrayList<Tag> wrapped = new java.util.ArrayList<>(size);
            for (int i = 0; i < size; i++) wrapped.add(LongTag.valueOf(values[i]));
            return HeterogenousListCollector.ofWrapped(wrapped).accept(tag);
        }

        @Override
        public Tag result() { return new LongArrayTag(Arrays.copyOf(values, size)); }
    }
}
