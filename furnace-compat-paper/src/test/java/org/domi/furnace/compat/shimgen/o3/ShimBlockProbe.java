package org.domi.furnace.compat.shimgen.o3;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.domi.furnace.compat.CompatTestPlugin;
import org.domi.furnace.test.FurnaceTestServer;

/**
 * T-263 S4 블록 상태 정답기의 shim 쪽. 시험이 따로 된 로더(전체 껍데기 jar 가 앞)에 올리고, 시험 서버를 띄워 블록 표(서버 블록 종류)를 만든다.
 * 아래 몸통은 바닐라 쪽 VanillaBlockProbe(시험 자원)와 글자 그대로 같다.
 */
public final class ShimBlockProbe {
    static final ClassLoader LOADER = ShimBlockProbe.class.getClassLoader();
    static FurnaceTestServer server;

    private ShimBlockProbe() {}

    public static void init() throws Exception {
        server = FurnaceTestServer.builder().failFast(false).exposeCompatToNative(true).plugin(CompatTestPlugin.class).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        server.tick(1);
    }

    public static void close() {
        if (server != null) server.close();
        server = null;
    }

    static final String OPAQUE = "<?>";
    static final int[] INTS = {0, 1, 7, 15};

    /** 그 멤버가 소유 클래스의 선언에 닿는 블록(등록 이름, 등록 순). 정적 멤버면 "-". 없으면 "". */
    public static String receivers(String owner, String name, String desc) throws Exception {
        Class<?> want = Class.forName(owner, false, LOADER);
        Method decl = find(want, name, desc);
        if (decl == null) return "";
        if (Modifier.isStatic(decl.getModifiers())) return "-";
        StringBuilder sb = new StringBuilder();
        for (Block b : BuiltInRegistries.BLOCK) {
            if (!want.isInstance(b)) continue;
            Method m = find(b.getClass(), name, desc);
            if (m == null || m.getDeclaringClass() != want) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(BuiltInRegistries.BLOCK.getKey(b));
        }
        return sb.toString();
    }

    /** 모든 블록(등록 이름, 등록 순). */
    public static String allBlocks() {
        StringBuilder sb = new StringBuilder();
        for (Block b : BuiltInRegistries.BLOCK) {
            if (sb.length() > 0) sb.append(',');
            sb.append(BuiltInRegistries.BLOCK.getKey(b));
        }
        return sb.toString();
    }

    /**
     * 받는 쪽(블록 이름 또는 "-")의 멤버를 stateBlocks(쉼표로 이은 블록 이름)의 모든 상태로 부른다. 상태가 아닌 인자: 정수는 INTS 하나씩,
     * 참거짓은 둘 다, 그 밖 참조는 null(이 정답기가 다루는 몸통은 상태 말고는 인자를 쓰지 않는다). 줄마다 "입력\t결과".
     */
    public static String run(String owner, String name, String desc, String receiver, String stateBlocks) throws Exception {
        Class<?> oc = Class.forName(owner, false, LOADER);
        Method m = find(oc, name, desc);
        if (m == null) return "NOMEMBER";
        m.setAccessible(true);
        Object self = receiver.equals("-") ? null : block(receiver);
        Class<?>[] ps = m.getParameterTypes();
        int prim = -1;
        for (int i = 0; i < ps.length; i++) if (ps[i] == int.class || ps[i] == boolean.class) prim = i;
        StringBuilder out = new StringBuilder();
        for (String key : stateBlocks.split(",")) {
            for (Object st : states(block(key))) {
                BlockState s = (BlockState) st;
                Object[] vals = prim < 0 ? new Object[] {null} : ps[prim] == int.class ? new Object[] {0, 1, 7, 15} : new Object[] {false, true};
                for (Object pv : vals) {
                    Object[] args = new Object[ps.length];
                    for (int i = 0; i < ps.length; i++) args[i] = ps[i] == BlockState.class ? s : i == prim ? pv : null;
                    String r;
                    try {
                        r = canon(m.invoke(self, args));
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        r = "THREW:" + e.getCause().getClass().getName();
                    }
                    out.append(stateKey(s)).append(prim < 0 ? "" : "/" + pv).append('\t').append(r).append('\n');
                }
            }
        }
        return out.toString();
    }

    static Block block(String key) {
        Block b = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(key));
        if (b == null || !BuiltInRegistries.BLOCK.getKey(b).toString().equals(key)) throw new IllegalArgumentException("no block " + key);
        return b;
    }

    static java.util.List<?> states(Block b) throws Exception {
        Object def = b.getStateDefinition();
        return (java.util.List<?>) def.getClass().getMethod("getPossibleStates").invoke(def);
    }

    /** 상태 표기: 블록 이름 + 속성 이름순 "이름=값". */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static String stateKey(BlockState s) {
        java.util.TreeMap<String, String> props = new java.util.TreeMap<>();
        for (Map.Entry<Property<?>, Comparable<?>> e : s.getValues().entrySet()) {
            Property p = e.getKey();
            props.put(p.getName(), p.getName((Comparable) e.getValue()));
        }
        return BuiltInRegistries.BLOCK.getKey(s.getBlock()) + props.toString();
    }

    static String canon(Object v) {
        if (v == null) return "null";
        if (v instanceof Boolean || v instanceof Integer || v instanceof Long || v instanceof Short || v instanceof Byte) return v.toString();
        if (v instanceof Float f) return "f" + Float.floatToIntBits(f);
        if (v instanceof Double d) return "d" + Double.doubleToLongBits(d);
        if (v instanceof String s) return "\"" + s + "\"";
        if (v instanceof Enum<?> e) return e.getDeclaringClass().getName() + "." + e.name();
        if (v instanceof BlockState s) return "state:" + stateKey(s);
        if (v instanceof Block b) return "block:" + BuiltInRegistries.BLOCK.getKey(b);
        return OPAQUE;
    }

    /** 이름·서술자가 같은 메서드(선언 클래스에서 위로). */
    static Method find(Class<?> c, String name, String desc) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            for (Method m : k.getDeclaredMethods()) if (m.getName().equals(name) && desc(m).equals(desc)) return m;
        }
        return null;
    }

    static String desc(Method m) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> p : m.getParameterTypes()) sb.append(d(p));
        return sb.append(')').append(d(m.getReturnType())).toString();
    }

    static String d(Class<?> c) {
        if (c.isArray()) return "[" + d(c.getComponentType());
        if (c == int.class) return "I";
        if (c == boolean.class) return "Z";
        if (c == void.class) return "V";
        if (c == long.class) return "J";
        if (c == float.class) return "F";
        if (c == double.class) return "D";
        if (c == byte.class) return "B";
        if (c == short.class) return "S";
        if (c == char.class) return "C";
        return "L" + c.getName().replace('.', '/') + ";";
    }
}
