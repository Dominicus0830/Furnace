package org.bukkit.persistence;

import java.util.ArrayList;
import java.util.List;

/** {@link PersistentDataType#LIST} — 목록 종류를 만든다(Paper {@code ListPersistentDataTypeProvider} 와 같은 이름·꼴). */
public final class ListPersistentDataTypeProvider {

    private static final ListPersistentDataType<Byte, Byte> BYTE = new ListImpl<>(PersistentDataType.BYTE);
    private static final ListPersistentDataType<Short, Short> SHORT = new ListImpl<>(PersistentDataType.SHORT);
    private static final ListPersistentDataType<Integer, Integer> INTEGER = new ListImpl<>(PersistentDataType.INTEGER);
    private static final ListPersistentDataType<Long, Long> LONG = new ListImpl<>(PersistentDataType.LONG);
    private static final ListPersistentDataType<Float, Float> FLOAT = new ListImpl<>(PersistentDataType.FLOAT);
    private static final ListPersistentDataType<Double, Double> DOUBLE = new ListImpl<>(PersistentDataType.DOUBLE);
    private static final ListPersistentDataType<Byte, Boolean> BOOLEAN = new ListImpl<>(PersistentDataType.BOOLEAN);
    private static final ListPersistentDataType<String, String> STRING = new ListImpl<>(PersistentDataType.STRING);
    private static final ListPersistentDataType<byte[], byte[]> BYTE_ARRAY = new ListImpl<>(PersistentDataType.BYTE_ARRAY);
    private static final ListPersistentDataType<int[], int[]> INTEGER_ARRAY = new ListImpl<>(PersistentDataType.INTEGER_ARRAY);
    private static final ListPersistentDataType<long[], long[]> LONG_ARRAY = new ListImpl<>(PersistentDataType.LONG_ARRAY);
    private static final ListPersistentDataType<PersistentDataContainer, PersistentDataContainer> DATA_CONTAINER =
            new ListImpl<>(PersistentDataType.TAG_CONTAINER);

    ListPersistentDataTypeProvider() {}

    public ListPersistentDataType<Byte, Byte> bytes() { return BYTE; }

    public ListPersistentDataType<Short, Short> shorts() { return SHORT; }

    public ListPersistentDataType<Integer, Integer> integers() { return INTEGER; }

    public ListPersistentDataType<Long, Long> longs() { return LONG; }

    public ListPersistentDataType<Float, Float> floats() { return FLOAT; }

    public ListPersistentDataType<Double, Double> doubles() { return DOUBLE; }

    public ListPersistentDataType<Byte, Boolean> booleans() { return BOOLEAN; }

    public ListPersistentDataType<String, String> strings() { return STRING; }

    public ListPersistentDataType<byte[], byte[]> byteArrays() { return BYTE_ARRAY; }

    public ListPersistentDataType<int[], int[]> integerArrays() { return INTEGER_ARRAY; }

    public ListPersistentDataType<long[], long[]> longArrays() { return LONG_ARRAY; }

    public ListPersistentDataType<PersistentDataContainer, PersistentDataContainer> dataContainers() { return DATA_CONTAINER; }

    /** 임의의 원소 종류로 만든 목록 종류(원소가 또 목록이면 저장할 때 IllegalArgumentException). */
    public <P, C> ListPersistentDataType<P, C> listTypeFrom(PersistentDataType<P, C> elementType) {
        return new ListImpl<>(java.util.Objects.requireNonNull(elementType, "elementType"));
    }

    private static final class ListImpl<P, C> implements ListPersistentDataType<P, C> {
        private final PersistentDataType<P, C> inner;

        private ListImpl(PersistentDataType<P, C> inner) { this.inner = inner; }

        @SuppressWarnings("unchecked")
        @Override
        public Class<List<P>> getPrimitiveType() { return (Class<List<P>>) (Object) List.class; }

        @SuppressWarnings("unchecked")
        @Override
        public Class<List<C>> getComplexType() { return (Class<List<C>>) (Object) List.class; }

        @Override
        public List<P> toPrimitive(List<C> complex, PersistentDataAdapterContext context) {
            List<P> out = new ArrayList<>(complex.size());
            for (C c : complex) out.add(inner.toPrimitive(c, context));
            return out;
        }

        @Override
        public List<C> fromPrimitive(List<P> primitive, PersistentDataAdapterContext context) {
            List<C> out = new ArrayList<>(primitive.size());
            for (P p : primitive) out.add(inner.fromPrimitive(p, context));
            return out;
        }

        @Override
        public PersistentDataType<P, C> elementType() { return inner; }

        @Override
        public String toString() { return "PersistentDataType.LIST<" + inner + ">"; }
    }
}
