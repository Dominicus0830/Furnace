package org.domi.furnace.compat.vdiff;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.stream.BaseStream;

/**
 * 형식별로 한 번만 쓰는 세 가지(T-235):
 * <ul>
 *   <li><b>씨앗 만들기</b> — 고정 시드 난수·경계값에서 중립 값(숫자·글·목록)을 만든다. 양쪽에 같은 씨앗이 간다.</li>
 *   <li><b>올리기</b> — 씨앗으로 한쪽 로더의 실제 객체를 만든다(생성자·정적 공장, 열거형은 이름).</li>
 *   <li><b>내리기</b> — 결과 객체를 중립 값으로 바꾼다. 바닐라 객체와 shim 객체는 다른 클래스라 직접 equals 를 못 하므로 이것끼리 견준다.</li>
 * </ul>
 * 형식이 여기 없으면 그 멤버는 "증명 없음"(이유와 함께 보고)이다 — 형식 하나를 더하면 그 형식을 쓰는 멤버가 모두 시험된다.
 */
public final class TypeKit {

    /** 씨앗을 만들 수 없는 형식. 멤버는 증명 없음으로 간다. */
    public static final class Unsupported extends Exception {
        public Unsupported(String what) {
            super(what, null, false, false);
        }
    }

    /** 이 입력은 올리기 단계에서 한쪽이라도 거절했다(시험 입력이 아님 — 건너뛰고 센다). */
    public static final class RaiseFailed extends Exception {
        public RaiseFailed(String what, Throwable cause) {
            super(what, cause, false, false);
        }
    }

    private interface Seeder {
        Object seed(SplittableRandom r, int boundaryIndex);
    }

    /** 올리기: 생성자 또는 정적 공장(이름이 "<init>" 이면 생성자). 인자 형식은 이름으로 적어 양쪽 로더에서 푼다. */
    private record Factory(String member, String[] params, Seeder[] parts) {}

    private static final Map<String, Seeder> PRIMS = new HashMap<>();
    private static final Map<String, Factory> FACTORIES = new LinkedHashMap<>();
    /** 인터페이스·추상 형식 → 그 자리에 넣을 구체 형식(예: Position → Vec3). */
    private static final Map<String, String> STAND_INS = new HashMap<>();
    /** 내리기: 클래스 이름 → 접근자(메서드 "name()" 또는 필드 "name"). 결과는 [형식 이름, 값…]. */
    private static final Map<String, String[]> LOWERERS = new HashMap<>();
    /** 글 씨앗: 자원 위치·이름처럼 바닐라가 실제로 받는 꼴과 깨진 꼴을 섞는다. */
    private static final String[] STRINGS = {
        "", "minecraft:stone", "stone", "minecraft:", ":stone", "Minecraft:Stone", "a:b/c", "a:b:c", "furnace:x.y_z-0",
        "a/b", "../x", " ", "minecraft:a b", "ns:path/with/slashes", "UPPER", "é", "1234", "-", "north", "y", "x", "z",
        // 열거형 valueOf 가 값을 돌려주는 입력도 있게(없으면 모든 입력이 예외라 값을 본 적이 없다)
        "DOWN", "UP", "NORTH", "SOUTH", "WEST", "EAST", "X", "Y", "Z", "POSITIVE", "NEGATIVE", "HORIZONTAL", "VERTICAL"
    };

    static final int[] INT_EDGES = {0, 1, -1, 2, -2, 15, 16, -16, 17, 31, 32, -33, 255, 256, 1023, -1024, 2047, -2048,
        30_000_000, -30_000_000, 33_554_431, -33_554_432, Integer.MAX_VALUE, Integer.MIN_VALUE, 65535, 65536, 1 << 30};
    static final long[] LONG_EDGES = {0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE,
        1L << 32, (1L << 32) - 1, 0x7FFFFFFFFFFL, 0xFFFFFFFFL << 32, 1875066L | 1875066L << 32};
    static final double[] DOUBLE_EDGES = {0.0, -0.0, 1.0, -1.0, 0.5, -0.5, 0.49999999999999994, 1e-7, -1e-7, 15.999999, 16.0,
        Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.MIN_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE,
        2147483647.5, -2147483648.5, 9.223372036854776E18, -9.223372036854776E18, 3.0E7, -3.0E7, Math.PI, 360.0, -180.0, 180.0, 540.0};
    static final float[] FLOAT_EDGES = {0f, -0f, 1f, -1f, 0.5f, -0.5f, 1e-5f, -1e-5f, 15.999f, 16f, Float.NaN,
        Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.MIN_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE,
        2.14748365E9f, -2.14748365E9f, 3.0E7f, (float) Math.PI, 360f, -180f, 180f, 540f};

    static {
        PRIMS.put("int", (r, b) -> b >= 0 ? INT_EDGES[b % INT_EDGES.length] : randomInt(r));
        PRIMS.put("long", (r, b) -> b >= 0 ? LONG_EDGES[b % LONG_EDGES.length] : randomLong(r));
        PRIMS.put("double", (r, b) -> b >= 0 ? DOUBLE_EDGES[b % DOUBLE_EDGES.length] : randomDouble(r));
        PRIMS.put("float", (r, b) -> b >= 0 ? FLOAT_EDGES[b % FLOAT_EDGES.length] : (float) randomDouble(r));
        PRIMS.put("short", (r, b) -> (short) (b >= 0 ? INT_EDGES[b % INT_EDGES.length] : randomInt(r)));
        PRIMS.put("byte", (r, b) -> (byte) (b >= 0 ? INT_EDGES[b % INT_EDGES.length] : randomInt(r)));
        PRIMS.put("char", (r, b) -> (char) (b >= 0 ? INT_EDGES[b % INT_EDGES.length] : r.nextInt(0x3000)));
        PRIMS.put("boolean", (r, b) -> b >= 0 ? (b % 2 == 0) : r.nextBoolean());
        PRIMS.put("java.lang.String", (r, b) -> b >= 0 ? STRINGS[b % STRINGS.length] : randomString(r));
        for (String p : List.of("int", "long", "double", "float", "short", "byte", "char", "boolean")) {
            PRIMS.put(box(p), PRIMS.get(p));
        }

        Seeder i = PRIMS.get("int"), d = PRIMS.get("double");
        factory("net.minecraft.core.Vec3i", "<init>", new String[]{"int", "int", "int"}, i, i, i);
        factory("net.minecraft.core.BlockPos", "<init>", new String[]{"int", "int", "int"}, i, i, i);
        factory("net.minecraft.core.BlockPos$MutableBlockPos", "<init>", new String[]{"int", "int", "int"}, i, i, i);
        factory("net.minecraft.core.SectionPos", "of", new String[]{"int", "int", "int"}, i, i, i);
        factory("net.minecraft.world.level.ChunkPos", "<init>", new String[]{"int", "int"}, i, i);
        factory("net.minecraft.world.phys.Vec3", "<init>", new String[]{"double", "double", "double"}, d, d, d);
        factory("net.minecraft.world.phys.Vec2", "<init>", new String[]{"float", "float"}, PRIMS.get("float"), PRIMS.get("float"));
        factory("net.minecraft.world.phys.AABB", "<init>", new String[]{"double", "double", "double", "double", "double", "double"}, d, d, d, d, d, d);
        factory("net.minecraft.resources.ResourceLocation", "parse", new String[]{"java.lang.String"},
                (r, b) -> b >= 0 ? new String[]{"minecraft:stone", "air", "furnace:a/b", "x:y.z", "minecraft:block/oak_log"}[b % 5] : randomId(r));
        factory("com.mojang.brigadier.StringReader", "<init>", new String[]{"java.lang.String"}, PRIMS.get("java.lang.String"));
        factory("net.minecraft.nbt.NbtAccounter", "create", new String[]{"long"},
                // 고정 훑기(경계값 0~15)는 정해진 한도: 0·8 은 무제한, 4·12 는 1MiB(이름표는 지나고 큰 배열에서 걸림) — NbtKit 큰 배열 입력과 짝
                (r, b) -> b >= 0 && b < 16 ? (b % 8 == 0 ? Long.MAX_VALUE : 1L << 20)
                        : switch (r.nextInt(3)) { case 0 -> Long.MAX_VALUE; case 1 -> (long) r.nextInt(0, 64); default -> (long) r.nextInt(0, 4096); });
        factory("java.util.UUID", "<init>", new String[]{"long", "long"}, PRIMS.get("long"), PRIMS.get("long"));
        factory("net.minecraft.util.RandomSource", "create", new String[]{"long"}, PRIMS.get("long"));
        STAND_INS.put("net.minecraft.core.Position", "net.minecraft.world.phys.Vec3");
        STAND_INS.put("net.minecraft.core.Vec3i", "net.minecraft.core.Vec3i");
        STAND_INS.put("java.lang.Object", "@self");

        LOWERERS.put("net.minecraft.core.Vec3i", new String[]{"getX()", "getY()", "getZ()"});
        LOWERERS.put("net.minecraft.world.level.ChunkPos", new String[]{"x", "z"});
        LOWERERS.put("net.minecraft.world.phys.Vec3", new String[]{"x", "y", "z"});
        LOWERERS.put("net.minecraft.world.phys.Vec2", new String[]{"x", "y"});
        LOWERERS.put("net.minecraft.world.phys.AABB", new String[]{"minX", "minY", "minZ", "maxX", "maxY", "maxZ"});
        LOWERERS.put("java.util.UUID", new String[]{"toString()"});
        LOWERERS.put("net.minecraft.nbt.TagType", new String[]{"getName()", "getPrettyName()", "isValue()"});
        LOWERERS.put("net.minecraft.nbt.NbtAccounter", new String[]{"getUsage()", "getDepth()"});
        LOWERERS.put("com.mojang.brigadier.StringReader", new String[]{"getString()", "getCursor()"});
        LOWERERS.put("net.minecraft.resources.ResourceLocation", new String[]{"getNamespace()", "getPath()"});
    }

    private TypeKit() {}

    private static void factory(String type, String member, String[] params, Seeder... parts) {
        FACTORIES.put(type, new Factory(member, params, parts));
    }

    private static String box(String p) {
        return switch (p) {
            case "int" -> "java.lang.Integer";
            case "char" -> "java.lang.Character";
            default -> "java.lang." + Character.toUpperCase(p.charAt(0)) + p.substring(1);
        };
    }

    private static int randomInt(SplittableRandom r) {
        return switch (r.nextInt(4)) {
            case 0 -> r.nextInt(-64, 65);
            case 1 -> r.nextInt(-30_000_000, 30_000_001);
            case 2 -> r.nextInt(-4096, 4097);
            default -> r.nextInt();
        };
    }

    private static long randomLong(SplittableRandom r) {
        return r.nextInt(3) == 0 ? randomInt(r) : r.nextLong();
    }

    /** 원형 시제품과 같은 분포: (난수-0.5)×10^(0..11). */
    private static double randomDouble(SplittableRandom r) {
        return (r.nextDouble() - 0.5) * Math.pow(10, r.nextInt(12));
    }

    private static String randomString(SplittableRandom r) {
        String alphabet = "abcxyz019_-./:ABZ ";
        int n = r.nextInt(13);
        StringBuilder sb = new StringBuilder(n);
        for (int k = 0; k < n; k++) sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
        return sb.toString();
    }

    private static String randomId(SplittableRandom r) {
        String alphabet = "abcxyz019_-./";
        StringBuilder sb = new StringBuilder();
        if (r.nextBoolean()) sb.append(r.nextBoolean() ? "minecraft:" : "furnace:");
        int n = 1 + r.nextInt(10);
        for (int k = 0; k < n; k++) {
            char c = alphabet.charAt(r.nextInt(alphabet.length()));
            if (k == 0 && c == '/') c = 'a';
            sb.append(c);
        }
        return sb.toString();
    }

    // ---- 씨앗 ----

    /**
     * 형식 이름의 씨앗. boundaryIndex ≥ 0 이면 경계값 쪽, 음수면 난수. self 는 Object 인자 자리에 넣을 형식(받는 쪽 클래스).
     * 열거형은 바닐라 쪽 상수 이름에서 고른다.
     */
    public static Object seed(Class<?> vanillaType, SplittableRandom r, int boundaryIndex, String self) throws Unsupported {
        String name = vanillaType.getName();
        String stand = STAND_INS.get(name);
        if ("@self".equals(stand)) {
            if (self == null || r.nextInt(8) == 0) return new NullSeed();
            name = self;
        } else if (stand != null && !FACTORIES.containsKey(name)) {
            name = stand;
        }
        Object nbt = NbtKit.seed(name, r, boundaryIndex);
        if (nbt != null) return nbt;
        if (vanillaType.isArray()) throw new Unsupported("array " + name);
        Seeder p = PRIMS.get(name);
        if (p != null) return p.seed(r, boundaryIndex);
        if (vanillaType.isEnum() && STAND_INS.get(vanillaType.getName()) == null) {
            Object[] cs = vanillaType.getEnumConstants();
            Enum<?> e = (Enum<?>) cs[boundaryIndex >= 0 ? boundaryIndex % cs.length : r.nextInt(cs.length)];
            return new EnumSeed(vanillaType.getName(), e.name());
        }
        Factory f = FACTORIES.get(name);
        if (f == null) throw new Unsupported(vanillaType.getName());
        Object[] parts = new Object[f.parts.length];
        for (int k = 0; k < parts.length; k++) {
            // 여러 칸이면 칸마다 다른 경계값으로 섞고, 한 칸이면 경계값 번호를 그대로(고정 훑기 번호를 기대는 씨앗이 있다 — NbtAccounter)
            parts[k] = f.parts[k].seed(r, boundaryIndex < 0 ? -1 : f.parts.length == 1 ? boundaryIndex : boundaryIndex * 7 + k * 3);
        }
        return new ObjSeed(name, parts);
    }

    /**
     * 제네릭 인자를 아는 자리(메서드 인자)의 씨앗. {@code List<Byte>}·{@code List<Integer>}·{@code List<Long>}·{@code List<String>} 처럼
     * 원소 형식이 꾸러미에 있는 목록은 0~5개짜리 목록 씨앗을 만든다. 그 밖은 원시 형식으로.
     */
    public static Object seed(java.lang.reflect.Type generic, Class<?> raw, SplittableRandom r, int boundaryIndex, String self) throws Unsupported {
        if (generic instanceof java.lang.reflect.ParameterizedType pt
                && (raw == List.class || raw == java.util.Collection.class || raw == Iterable.class)
                && pt.getActualTypeArguments()[0] instanceof Class<?> el) {
            int n = boundaryIndex >= 0 ? boundaryIndex % 6 : r.nextInt(6);
            List<Object> items = new ArrayList<>(n);
            for (int k = 0; k < n; k++) items.add(seed(el, r, boundaryIndex >= 0 ? boundaryIndex + k : -1, self));
            return new ListSeed(items);
        }
        return seed(raw, r, boundaryIndex, self);
    }

    public static String unsupportedReason(java.lang.reflect.Type generic, Class<?> raw, String self) {
        try {
            seed(generic, raw, new SplittableRandom(0), -1, self);
            return null;
        } catch (Unsupported u) {
            return u.getMessage();
        } catch (RuntimeException e) {
            return raw.getName() + ": " + e;
        }
    }

    /** 이 형식의 씨앗을 만들 수 있나(시험 전에 멤버를 증명 가능/없음으로 나눈다). */
    public static String unsupportedReason(Class<?> vanillaType, String self) {
        try {
            seed(vanillaType, new SplittableRandom(0), -1, self);
            return null;
        } catch (Unsupported u) {
            return u.getMessage();
        } catch (RuntimeException e) {
            return vanillaType.getName() + ": " + e;
        }
    }

    record NullSeed() {}

    record ListSeed(List<Object> items) {
        @Override
        public String toString() {
            return items.toString();
        }
    }

    record EnumSeed(String type, String constant) {}

    record ObjSeed(String type, Object[] parts) {
        @Override
        public String toString() {
            return type.substring(type.lastIndexOf('.') + 1) + java.util.Arrays.toString(parts);
        }
    }

    // ---- 올리기 ----

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object raise(Object seed, ClassLoader loader) throws RaiseFailed {
        if (seed instanceof NullSeed) return null;
        if (seed instanceof ListSeed l) {
            List<Object> out = new ArrayList<>(l.items.size());
            for (Object o : l.items) out.add(raise(o, loader));
            return out;
        }
        if (seed instanceof NbtKit.TagSeed || seed instanceof NbtKit.OutSeed || seed instanceof NbtKit.InSeed || seed instanceof NbtKit.PathSeed
                || seed instanceof byte[] || seed instanceof int[] || seed instanceof long[]) {
            try {
                return NbtKit.raise(seed, loader);
            } catch (InvocationTargetException ex) {
                throw new RaiseFailed(String.valueOf(seed), ex.getCause());
            } catch (ReflectiveOperationException | java.io.IOException | RuntimeException ex) {
                throw new RaiseFailed(String.valueOf(seed), ex);
            }
        }
        if (seed instanceof EnumSeed e) {
            try {
                return Enum.valueOf((Class) Class.forName(e.type, true, loader), e.constant);
            } catch (ReflectiveOperationException | IllegalArgumentException ex) {
                throw new RaiseFailed(e.type + "." + e.constant, ex);
            }
        }
        if (seed instanceof ObjSeed o) {
            Factory f = FACTORIES.get(o.type);
            try {
                Class<?> c = Class.forName(o.type, true, loader);
                Class<?>[] ps = new Class<?>[f.params.length];
                for (int k = 0; k < ps.length; k++) ps[k] = resolve(f.params[k], loader);
                if (f.member.equals("<init>")) {
                    Constructor<?> k = c.getConstructor(ps);
                    return k.newInstance(o.parts);
                }
                Method m = c.getMethod(f.member, ps);
                return m.invoke(null, o.parts);
            } catch (InvocationTargetException ex) {
                throw new RaiseFailed(o.toString(), ex.getCause());
            } catch (ReflectiveOperationException | RuntimeException ex) {
                throw new RaiseFailed(o.toString(), ex);
            }
        }
        return seed; // 원시값·글
    }

    public static Class<?> resolve(String name, ClassLoader loader) throws ClassNotFoundException {
        return switch (name) {
            case "int" -> int.class;
            case "long" -> long.class;
            case "double" -> double.class;
            case "float" -> float.class;
            case "short" -> short.class;
            case "byte" -> byte.class;
            case "char" -> char.class;
            case "boolean" -> boolean.class;
            case "void" -> void.class;
            default -> Class.forName(name, false, loader);
        };
    }

    // ---- 내리기 ----

    /** 결과·상태를 중립 값으로. 모르는 형식이면 Unsupported. 목록·흐름은 앞 64개만 본다. */
    public static Object lower(Object v) throws Unsupported {
        return lower(v, 0);
    }

    private static Object lower(Object v, int depth) throws Unsupported {
        if (v == null) return null;
        if (depth > 6) throw new Unsupported("nesting too deep");
        if (v instanceof Number || v instanceof Boolean || v instanceof Character || v instanceof String) return v;
        Class<?> c = v.getClass();
        if (v instanceof Enum<?> e) return List.of("enum", e.getDeclaringClass().getName(), e.name());
        Object cap = NbtKit.lowerCapture(v);
        if (cap != null) return cap;
        if (NbtKit.isTag(c)) return NbtKit.lowerTag(v, depth);
        if (c.isArray()) {
            List<Object> out = new ArrayList<>();
            out.add("array:" + c.getComponentType().getName());
            int n = Array.getLength(v);
            for (int k = 0; k < Math.min(n, 64); k++) out.add(lower(Array.get(v, k), depth + 1));
            out.add("len=" + n);
            return out;
        }
        if (v instanceof Optional<?> o) return o.isPresent() ? List.of("Optional", lower(o.get(), depth + 1)) : List.of("Optional.empty");
        if (v instanceof BaseStream<?, ?> s) return iter("stream", s.iterator(), depth);
        if (v instanceof Iterable<?> it && !isMinecraft(c)) return iter(listKind(v), it.iterator(), depth);
        if (v instanceof Iterator<?> it) return iter("iterator", it, depth);
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            List<Class<?>> owners = new ArrayList<>();
            owners.add(k);
            allInterfaces(k, owners);
            for (Class<?> o : owners) {
                String[] acc = LOWERERS.get(o.getName());
                if (acc == null) continue;
                List<Object> out = new ArrayList<>(acc.length + 1);
                // 형식 이름은 net.minecraft 쪽만 견준다(익명 TagType 처럼 바닐라 안쪽 클래스 번호는 관측값이 아니다)
                out.add(isMinecraft(publicName(c)) && !c.isAnonymousClass() ? publicName(c) : o.getName());
                for (String a : acc) out.add(lower(read(v, o, a), depth + 1));
                return out;
            }
        }
        if (isMinecraft(c) && hasNoState(c)) return List.of(publicName(c)); // 상태 없는 객체(유틸 클래스의 생성자 결과 등)
        throw new Unsupported("lower " + c.getName());
    }

    private static String listKind(Object v) {
        if (v instanceof java.util.Set<?>) return "set";
        if (v instanceof List<?>) return "list";
        return "iterable";
    }

    private static List<Object> iter(String kind, Iterator<?> it, int depth) throws Unsupported {
        List<Object> out = new ArrayList<>();
        out.add(kind);
        int n = 0;
        while (it.hasNext() && n < 64) {
            out.add(lower(it.next(), depth + 1));
            n++;
        }
        if (it.hasNext()) out.add("...");
        return out;
    }

    /** 익명·숨은 하위 클래스는 처음 보이는 이름 있는 상위로. Furnace 쪽 다른 클래스가 나오면 그 이름이 남아 차이로 잡힌다. */
    private static String publicName(Class<?> c) {
        Class<?> k = c;
        while (k.isAnonymousClass() || k.isSynthetic() || k.isHidden()) k = k.getSuperclass();
        return k.getName();
    }

    private static boolean isMinecraft(Class<?> c) {
        return isMinecraft(c.getName());
    }

    private static boolean isMinecraft(String n) {
        return n.startsWith("net.minecraft.");
    }

    private static void allInterfaces(Class<?> c, List<Class<?>> out) {
        for (Class<?> i : c.getInterfaces()) {
            out.add(i);
            allInterfaces(i, out);
        }
    }

    private static boolean hasNoState(Class<?> c) {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) if (!Modifier.isStatic(f.getModifiers())) return false;
        }
        return true;
    }

    private static Object read(Object v, Class<?> owner, String accessor) throws Unsupported {
        try {
            if (accessor.endsWith("()")) {
                Method m = owner.getMethod(accessor.substring(0, accessor.length() - 2));
                return m.invoke(v);
            }
            Field f = owner.getField(accessor);
            if (Modifier.isStatic(f.getModifiers())) throw new Unsupported("static " + accessor);
            return f.get(v);
        } catch (ReflectiveOperationException e) {
            throw new Unsupported("lower " + owner.getName() + "." + accessor + ": " + e);
        }
    }
}
