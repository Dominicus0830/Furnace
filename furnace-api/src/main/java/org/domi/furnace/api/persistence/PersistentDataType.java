// 영속 데이터 값의 종류 — NBT 태그 하나에 대응한다(Bukkit PersistentDataType 의 기본 종류와 같은 저장 꼴: BOOLEAN 은 byte 0/1).
package org.domi.furnace.api.persistence;

public final class PersistentDataType<T> {

    public static final PersistentDataType<Byte> BYTE = new PersistentDataType<>("byte", Byte.class);
    public static final PersistentDataType<Short> SHORT = new PersistentDataType<>("short", Short.class);
    public static final PersistentDataType<Integer> INTEGER = new PersistentDataType<>("int", Integer.class);
    public static final PersistentDataType<Long> LONG = new PersistentDataType<>("long", Long.class);
    public static final PersistentDataType<Float> FLOAT = new PersistentDataType<>("float", Float.class);
    public static final PersistentDataType<Double> DOUBLE = new PersistentDataType<>("double", Double.class);
    public static final PersistentDataType<String> STRING = new PersistentDataType<>("string", String.class);
    public static final PersistentDataType<byte[]> BYTE_ARRAY = new PersistentDataType<>("byte_array", byte[].class);
    public static final PersistentDataType<int[]> INTEGER_ARRAY = new PersistentDataType<>("int_array", int[].class);
    public static final PersistentDataType<long[]> LONG_ARRAY = new PersistentDataType<>("long_array", long[].class);
    /** byte 0/1 로 저장한다. 읽을 때는 0 이 아니면 참. */
    public static final PersistentDataType<Boolean> BOOLEAN = new PersistentDataType<>("boolean", Boolean.class);
    /** 중첩 컨테이너(NBT compound). */
    public static final PersistentDataType<PersistentDataContainer> TAG_CONTAINER = new PersistentDataType<>("container", PersistentDataContainer.class);

    private final String name;
    private final Class<T> javaType;
    private final PersistentDataType<?> element;

    private PersistentDataType(String name, Class<T> javaType) { this(name, javaType, null); }

    private PersistentDataType(String name, Class<T> javaType, PersistentDataType<?> element) {
        this.name = name;
        this.javaType = javaType;
        this.element = element;
    }

    /**
     * 같은 종류 값의 목록(NBT 리스트로 저장, Paper 의 PersistentDataType.LIST 와 같은 꼴). 읽은 목록은 고칠 수 없다.
     * 원소가 또 목록인 목록(목록의 목록)도 된다. 서로 다른 종류를 섞을 수는 없다(빈 목록은 어느 목록 종류로도 읽힌다).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <E> PersistentDataType<java.util.List<E>> list(PersistentDataType<E> element) {
        return new PersistentDataType<>("list<" + element.name + ">", (Class) java.util.List.class, element);
    }

    /** 목록 종류면 그 원소 종류, 아니면 null. */
    public PersistentDataType<?> elementType() { return element; }

    @Override
    public boolean equals(Object o) { return o == this || o instanceof PersistentDataType<?> t && element != null && t.element != null && t.element.equals(element); }

    @Override
    public int hashCode() { return element == null ? System.identityHashCode(this) : element.hashCode() * 31 + 1; }

    public String name() { return name; }

    public Class<T> javaType() { return javaType; }

    @Override
    public String toString() { return "PersistentDataType." + name; }

    /** 키 형식 검사: namespace:path (소문자·숫자·._- , path 는 / 도). Bukkit NamespacedKey 와 같은 규칙. 어기면 IllegalArgumentException. */
    public static String checkKey(String key) {
        if (key == null) throw new IllegalArgumentException("키가 null");
        int c = key.indexOf(':');
        if (c <= 0 || c == key.length() - 1 || key.length() > 256) throw new IllegalArgumentException("키는 namespace:path 형식: " + key);
        for (int i = 0; i < key.length(); i++) {
            char ch = key.charAt(i);
            if (i == c) continue;
            boolean ok = ch >= 'a' && ch <= 'z' || ch >= '0' && ch <= '9' || ch == '.' || ch == '_' || ch == '-' || ch == '/' && i > c;
            if (!ok) throw new IllegalArgumentException("키에 쓸 수 없는 문자 '" + ch + "': " + key);
        }
        return key;
    }
}
