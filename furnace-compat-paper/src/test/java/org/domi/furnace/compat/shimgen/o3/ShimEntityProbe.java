package org.domi.furnace.compat.shimgen.o3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.domi.furnace.compat.CompatTestPlugin;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.test.FurnaceTestServer;

/**
 * T-263 S3 월드 밖 엔티티 정답기(O3) — shim 쪽. {@link EntityOracleTest} 가 실행 때와 같은 따로 된 로더(전체 껍데기 jar 가 앞)에 이 클래스를
 * 따로 올려 부른다(연결 줄 몸통은 껍데기 jar 에만 있다). 월드 밖 엔티티는 플러그인이 만드는 길과 같다({@code EntityType#create}).
 * 주고받는 것은 문자열뿐이고, 값 표기는 바닐라 쪽(시험 자원 o3/VanillaEntityProbe.java)과 글자 그대로 같다.
 */
public final class ShimEntityProbe {
    static FurnaceTestServer server;
    static ServerLevel level;
    static final Map<String, EntityType<?>> TYPES = new TreeMap<>();

    private ShimEntityProbe() {}

    public static void init() throws Exception {
        server = FurnaceTestServer.builder().failFast(false).exposeCompatToNative(true).plugin(CompatTestPlugin.class).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        var tp = server.join("Oracle");
        server.tick(3);
        level = ((CraftWorld) Compat.player(tp.player()).getWorld()).getHandle();
        for (Field f : EntityType.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == EntityType.class) TYPES.put(f.getName(), (EntityType<?>) f.get(null));
        }
    }

    public static void close() {
        if (server != null) server.close();
        server = null;
    }

    /** 바닐라 쪽 probe 와 같은 뜻·같은 표기. */
    public static String probe(String type, String owner, String name, String desc, String state, String args) throws Exception {
        EntityType<?> t = TYPES.get(type);
        if (t == null) return "NOTYPE";
        Entity e;
        try {
            e = t.create(level);
        } catch (Throwable x) {
            return "NOCREATE:" + x.getClass().getName();
        }
        if (e == null) return "NOCREATE:null";
        SynchedEntityData d = e.getEntityData();
        Object[] items = itemsById(d);
        try {
            apply(e, d, items, state);
        } catch (Throwable x) {
            return "SKIP:state " + x.getClass().getName();
        }
        Method m = find(Class.forName(owner, false, ShimEntityProbe.class.getClassLoader()), name, desc);
        Object[] a = parseArgs(m.getParameterTypes(), args);
        if (a == null) return "SKIP:args";
        String[] before = new String[items.length];
        for (int i = 0; i < items.length; i++) before[i] = canon(field(items[i], "value"));
        Object r;
        try {
            m.setAccessible(true);
            r = m.invoke(e, a);
        } catch (java.lang.reflect.InvocationTargetException x) {
            // shim 은 뜻을 알 수 있게 메시지를 덧붙인다(바닐라가 던진 경우는 견주지 않으므로 표기가 달라도 된다)
            return "THREW:" + x.getCause().getClass().getName() + " # " + x.getCause().getMessage();
        }
        StringBuilder sb = new StringBuilder("ret=").append(m.getReturnType() == void.class ? "void" : canon(r)).append("|changed=");
        for (int i = 0; i < items.length; i++) {
            String now = canon(field(items[i], "value"));
            if (!now.equals(before[i])) sb.append(i).append('=').append(now).append(',');
        }
        return sb.toString();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void apply(Entity e, SynchedEntityData d, Object[] items, String state) throws Exception {
        if (state.isEmpty()) return;
        for (String kv : state.split(";")) {
            int eq = kv.indexOf('=');
            if (kv.startsWith("P=")) {   // 자리(x,y,z)와 방향(yRot,xRot)
                String[] p = kv.substring(2).split(",");
                e.setPos(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]));
                e.setYRot(Float.parseFloat(p[3]));
                e.setXRot(Float.parseFloat(p[4]));
                continue;
            }
            int id = Integer.parseInt(kv.substring(0, eq));
            var acc = (EntityDataAccessor) field(items[id], "accessor");
            Object cur = field(items[id], "value");
            d.set(acc, parse(cur.getClass(), cur, kv.substring(eq + 1)));
        }
    }

    static Object parse(Class<?> t, Object like, String s) {
        Object o = token(s);
        if (o != NONE) return o;
        if (t == Byte.class || t == byte.class) return (byte) Integer.parseInt(s);
        if (t == Integer.class || t == int.class) return Integer.parseInt(s);
        if (t == Float.class || t == float.class) return Float.parseFloat(s);
        if (t == Double.class || t == double.class) return Double.parseDouble(s);
        if (t == Long.class || t == long.class) return Long.parseLong(s);
        if (t == Boolean.class || t == boolean.class) return Boolean.parseBoolean(s);
        if (t == Short.class || t == short.class) return (short) Integer.parseInt(s);
        Class<?> en = like instanceof Enum<?> x ? x.getDeclaringClass() : t.isEnum() ? t : null;
        if (en != null) return en.getEnumConstants()[Math.floorMod(Integer.parseInt(s), en.getEnumConstants().length)];
        return null;
    }

    static final Object NONE = new Object();

    /** 객체 값 표기(시험이 만든 것): item:&lt;키&gt;*&lt;수&gt;, block:&lt;키&gt;(기본 상태), pos:x,y,z, text:&lt;글&gt;, opos:…/otext:…(- 는 빈 값). 아니면 NONE. */
    static Object token(String s) {
        int c = s.indexOf(':');
        if (c < 0) return NONE;
        String k = s.substring(0, c), v = s.substring(c + 1);
        switch (k) {
            case "item": {
                int star = v.lastIndexOf('*');
                return new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(v.substring(0, star))),
                        Integer.parseInt(v.substring(star + 1)));
            }
            case "block":
                return net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse(v)).defaultBlockState();
            case "pos": {
                String[] p = v.split(",");
                return new net.minecraft.core.BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
            }
            case "text":
                return net.minecraft.network.chat.Component.literal(v);
            case "opos":
                return v.equals("-") ? java.util.Optional.empty() : java.util.Optional.of(token("pos:" + v));
            case "otext":
                return v.equals("-") ? java.util.Optional.empty() : java.util.Optional.of(token("text:" + v));
            default:
                return NONE;
        }
    }

    static Object[] parseArgs(Class<?>[] ps, String args) {
        String[] v = args.isEmpty() ? new String[0] : args.split(";");
        if (v.length != ps.length) return null;
        Object[] out = new Object[ps.length];
        for (int i = 0; i < ps.length; i++) {
            out[i] = parse(ps[i], null, v[i]);
            if (out[i] == null) return null;
        }
        return out;
    }

    static Method find(Class<?> c, String name, String desc) throws NoSuchMethodException {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            for (Method m : k.getDeclaredMethods()) if (m.getName().equals(name) && org.objectweb.asm.Type.getMethodDescriptor(m).equals(desc)) return m;
        }
        throw new NoSuchMethodException(c.getName() + "." + name + desc);
    }

    /** 값 표기 — 바닐라 쪽 canon 과 같아야 한다. 비교할 수 없는 값은 "?". */
    static String canon(Object v) {
        if (v == null) return "null";
        if (v instanceof java.util.Optional<?> o) return o.isPresent() ? "opt(" + canon(o.get()) + ")" : "empty";
        if (v instanceof java.util.OptionalInt o) return o.isPresent() ? "opt(int:" + o.getAsInt() + ")" : "empty";
        if (v instanceof Float f) return "f:" + Float.floatToIntBits(f);
        if (v instanceof Double f) return "d:" + Double.doubleToLongBits(f);
        if (v instanceof Number || v instanceof Boolean || v instanceof String || v instanceof Character || v instanceof java.util.UUID) {
            return v.getClass().getSimpleName() + ":" + v;
        }
        if (v instanceof Enum<?> e) return "enum:" + e.getDeclaringClass().getSimpleName() + "." + e.name();
        if (v instanceof net.minecraft.network.chat.Component c) return "text:" + c.getString();
        if (v instanceof net.minecraft.world.item.ItemStack st) {
            return st.isEmpty() ? "item:empty" : "item:" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()) + "*" + st.getCount();
        }
        if (v instanceof net.minecraft.world.level.block.state.BlockState st) {
            return "block:" + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()) + (st == st.getBlock().defaultBlockState() ? "" : "+");
        }
        if (v instanceof net.minecraft.core.Holder<?> h) return "holder:" + h.unwrapKey().map(x -> x.location().toString()).orElse("direct");
        if (v instanceof net.minecraft.world.phys.Vec3 p) return "vec:" + Double.doubleToLongBits(p.x) + "," + Double.doubleToLongBits(p.y) + "," + Double.doubleToLongBits(p.z);
        if (v instanceof net.minecraft.core.BlockPos p) return "pos:" + p.getX() + "," + p.getY() + "," + p.getZ();
        if (v instanceof net.minecraft.core.Rotations r) return "rot:" + Float.floatToIntBits(r.getX()) + "," + Float.floatToIntBits(r.getY()) + "," + Float.floatToIntBits(r.getZ());
        return "?";
    }

    static Object[] itemsById(SynchedEntityData d) throws Exception {
        Field f = SynchedEntityData.class.getDeclaredField("itemsById");
        f.setAccessible(true);
        return (Object[]) f.get(d);
    }

    static Object field(Object o, String name) throws Exception {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(o);
            } catch (NoSuchFieldException ignored) {
                // 위 부류에서 찾는다
            }
        }
        throw new NoSuchFieldException(o.getClass().getName() + "." + name);
    }
}
