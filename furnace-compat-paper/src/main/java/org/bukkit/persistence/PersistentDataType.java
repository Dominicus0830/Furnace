package org.bukkit.persistence;

/**
 * 영속 데이터 값의 종류. P 는 저장되는 기본 종류, C 는 플러그인이 다루는 종류다.
 * 기본 종류는 BYTE·SHORT·INTEGER·LONG·FLOAT·DOUBLE·STRING·BYTE_ARRAY·INTEGER_ARRAY·LONG_ARRAY·BOOLEAN·TAG_CONTAINER,
 * TAG_CONTAINER_ARRAY(옛 종류, NBT 로는 compound 의 목록), 목록 종류({@link #LIST}).
 */
public interface PersistentDataType<P, C> {

    Class<P> getPrimitiveType();

    Class<C> getComplexType();

    P toPrimitive(C complex, PersistentDataAdapterContext context);

    C fromPrimitive(P primitive, PersistentDataAdapterContext context);

    PersistentDataType<Byte, Byte> BYTE = new Primitive<>(Byte.class);
    PersistentDataType<Short, Short> SHORT = new Primitive<>(Short.class);
    PersistentDataType<Integer, Integer> INTEGER = new Primitive<>(Integer.class);
    PersistentDataType<Long, Long> LONG = new Primitive<>(Long.class);
    PersistentDataType<Float, Float> FLOAT = new Primitive<>(Float.class);
    PersistentDataType<Double, Double> DOUBLE = new Primitive<>(Double.class);
    PersistentDataType<String, String> STRING = new Primitive<>(String.class);
    PersistentDataType<byte[], byte[]> BYTE_ARRAY = new Primitive<>(byte[].class);
    PersistentDataType<int[], int[]> INTEGER_ARRAY = new Primitive<>(int[].class);
    PersistentDataType<long[], long[]> LONG_ARRAY = new Primitive<>(long[].class);
    /** byte 0/1 로 저장한다. */
    PersistentDataType<Byte, Boolean> BOOLEAN = new Mapped<>(Byte.class, Boolean.class,
            (b, ctx) -> b != 0, (v, ctx) -> (byte) (v ? 1 : 0));
    PersistentDataType<PersistentDataContainer, PersistentDataContainer> TAG_CONTAINER =
            new Primitive<>(PersistentDataContainer.class);
    /** 중첩 컨테이너의 배열(NBT 로는 compound 목록). Paper 에서도 쓰지 않기를 권하는 종류다. */
    @Deprecated
    PersistentDataType<PersistentDataContainer[], PersistentDataContainer[]> TAG_CONTAINER_ARRAY =
            new Primitive<>(PersistentDataContainer[].class);
    /** 목록 종류를 만드는 입구: {@code LIST.strings()}, {@code LIST.listTypeFrom(종류)}. */
    ListPersistentDataTypeProvider LIST = new ListPersistentDataTypeProvider();

    /** 기본 종류 그대로 쓰는 종류. */
    final class Primitive<T> implements PersistentDataType<T, T> {
        private final Class<T> type;

        Primitive(Class<T> type) { this.type = type; }

        @Override
        public Class<T> getPrimitiveType() { return type; }

        @Override
        public Class<T> getComplexType() { return type; }

        @Override
        public T toPrimitive(T complex, PersistentDataAdapterContext context) { return complex; }

        @Override
        public T fromPrimitive(T primitive, PersistentDataAdapterContext context) { return primitive; }

        @Override
        public String toString() { return "PersistentDataType." + type.getSimpleName(); }
    }

    /** 기본 종류를 다른 자바 종류로 바꿔 쓰는 종류(BOOLEAN). */
    final class Mapped<P, C> implements PersistentDataType<P, C> {
        interface Fn<A, B> { B apply(A a, PersistentDataAdapterContext ctx); }

        private final Class<P> primitive;
        private final Class<C> complex;
        private final Fn<P, C> from;
        private final Fn<C, P> to;

        Mapped(Class<P> primitive, Class<C> complex, Fn<P, C> from, Fn<C, P> to) {
            this.primitive = primitive;
            this.complex = complex;
            this.from = from;
            this.to = to;
        }

        @Override
        public Class<P> getPrimitiveType() { return primitive; }

        @Override
        public Class<C> getComplexType() { return complex; }

        @Override
        public P toPrimitive(C value, PersistentDataAdapterContext context) { return to.apply(value, context); }

        @Override
        public C fromPrimitive(P value, PersistentDataAdapterContext context) { return from.apply(value, context); }

        @Override
        public String toString() { return "PersistentDataType." + complex.getSimpleName(); }
    }
}
