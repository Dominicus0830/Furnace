// T-263 S3 월드 밖 엔티티 정답기(O3) — 바닐라 쪽. 시험(EntityOracleTest)이 실행 때 바닐라 jar(캐시)에 대고 컴파일해 바닐라 로더에 올린다.
// 저장소에 바닐라 코드를 두지 않는다: 이 파일은 바닐라를 부르기만 한다(ExportEntityDataDefaults 와 같은 가짜 레벨).
// 주고받는 것은 문자열뿐 — 두 로더의 객체가 섞이지 않는다. 값 표기(canon)는 ShimEntityProbe 와 글자 그대로 같아야 한다.
package furnaceo3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class VanillaEntityProbe {
    static RegistryAccess access;
    static Level level;
    /** 엔티티 종류 공개 정적 필드 이름 → 종류. */
    static final Map<String, EntityType<?>> TYPES = new java.util.TreeMap<>();

    public static void init() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        access = registryAccess();
        level = fakeLevel(access);
        for (Field f : EntityType.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == EntityType.class) TYPES.put(f.getName(), (EntityType<?>) f.get(null));
        }
    }

    /**
     * 소유 클래스의 인스턴스를 만드는 종류(필드 이름). 그 멤버를 재정의하지 않은(부르면 소유 클래스의 선언에 닿는) 종류만 — 정확히 그 클래스가 먼저,
     * 없으면 하위 클래스 중 이름순 첫째. 없으면 "-"(하위가 모두 재정의하면 그 줄은 소유 클래스 몸통이 실제로 불리는 엔티티가 없다).
     */
    public static String typeFor(String owner, String name, String desc) throws Exception {
        Class<?> want = Class.forName(owner, false, VanillaEntityProbe.class.getClassLoader());
        String sub = "-";
        for (Map.Entry<String, EntityType<?>> t : TYPES.entrySet()) {
            if (t.getValue() == EntityType.PLAYER) continue;
            Entity e;
            try {
                e = create(t.getValue());
            } catch (Throwable x) {
                continue;
            }
            if (!want.isInstance(e) || find(e.getClass(), name, desc).getDeclaringClass() != want) continue;
            if (e.getClass() == want) return t.getKey();
            if (sub.equals("-")) sub = t.getKey();
        }
        return sub;
    }

    /** 칸 표: "번호:종류" 를 쉼표로. 종류는 무작위 상태를 만들 수 있는 것만 이름(byte·int·float·boolean·enum:<클래스>:<개수>·item·block·pos·opos·text·otext), 나머지 "-". */
    public static String slots(String type) throws Exception {
        Entity e = create(TYPES.get(type));
        StringBuilder sb = new StringBuilder();
        for (Object item : itemsById(e.getEntityData())) {
            var acc = (EntityDataAccessor<?>) field(item, "accessor");
            Object v = field(item, "value");
            var ser = acc.serializer();
            String k = ser == net.minecraft.network.syncher.EntityDataSerializers.ITEM_STACK ? "item"
                    : ser == net.minecraft.network.syncher.EntityDataSerializers.BLOCK_STATE ? "block"
                    : ser == net.minecraft.network.syncher.EntityDataSerializers.BLOCK_POS ? "pos"
                    : ser == net.minecraft.network.syncher.EntityDataSerializers.OPTIONAL_BLOCK_POS ? "opos"
                    : ser == net.minecraft.network.syncher.EntityDataSerializers.COMPONENT ? "text"
                    : ser == net.minecraft.network.syncher.EntityDataSerializers.OPTIONAL_COMPONENT ? "otext"
                    : v instanceof Byte ? "byte" : v instanceof Integer ? "int" : v instanceof Float ? "float" : v instanceof Boolean ? "boolean"
                    : v instanceof Enum<?> en ? "enum:" + en.getDeclaringClass().getName() + ":" + en.getDeclaringClass().getEnumConstants().length : "-";
            if (sb.length() > 0) sb.append(',');
            sb.append(acc.id()).append(':').append(k);
        }
        return sb.toString();
    }

    /**
     * 새 엔티티에 상태(칸 값 "번호=값;…")를 넣고 멤버를 인자("값;…")로 부른 뒤 "ret=<반환>|changed=<바뀐 칸 번호=새 값,…>" 를 돌려준다.
     * 상태 넣기가 던지면 "SKIP:state …", 멤버가 던지면 "THREW:<예외 클래스>".
     */
    public static String probe(String type, String owner, String name, String desc, String state, String args) throws Exception {
        Entity e = create(TYPES.get(type));
        SynchedEntityData d = e.getEntityData();
        Object[] items = itemsById(d);
        try {
            apply(e, d, items, state);
        } catch (Throwable x) {
            return "SKIP:state " + x.getClass().getName();
        }
        Method m = find(Class.forName(owner, false, VanillaEntityProbe.class.getClassLoader()), name, desc);
        Object[] a = parseArgs(m.getParameterTypes(), args);
        if (a == null) return "SKIP:args";
        String[] before = new String[items.length];
        for (int i = 0; i < items.length; i++) before[i] = canon(field(items[i], "value"));
        Object r;
        try {
            m.setAccessible(true);
            r = m.invoke(e, a);
        } catch (java.lang.reflect.InvocationTargetException x) {
            return "THREW:" + x.getCause().getClass().getName();
        }
        // 칸은 바뀐 것만(번호=새 값) — 멤버와 상관없는 칸의 처음 값 차이는 이 멤버의 증명 대상이 아니다
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
            for (Method m : k.getDeclaredMethods()) if (m.getName().equals(name) && descOf(m).equals(desc)) return m;
        }
        throw new NoSuchMethodException(c.getName() + "." + name + desc);
    }

    static String descOf(Method m) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> p : m.getParameterTypes()) sb.append(d(p));
        return sb.append(')').append(d(m.getReturnType())).toString();
    }

    static String d(Class<?> c) {
        if (c.isArray()) return c.getName().replace('.', '/');
        if (c == void.class) return "V";
        if (c == boolean.class) return "Z";
        if (c == byte.class) return "B";
        if (c == char.class) return "C";
        if (c == short.class) return "S";
        if (c == int.class) return "I";
        if (c == long.class) return "J";
        if (c == float.class) return "F";
        if (c == double.class) return "D";
        return "L" + c.getName().replace('.', '/') + ";";
    }

    /** 값 표기 — ShimEntityProbe.canon 과 같아야 한다. 비교할 수 없는 값은 OPAQUE. */
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
        return OPAQUE;
    }

    /** 비교할 수 없는 값의 표기 — 시험이 이것이 든 결과를 증명에서 뺀다. */
    static final String OPAQUE = "<?>";

    @SuppressWarnings("unchecked")
    static Entity create(EntityType<?> t) throws Exception {
        // EntityType.create 는 레벨의 기능 깃발(서버 설정)을 보므로 공장을 바로 부른다(ExportEntityDataDefaults 와 같다)
        Field ff = EntityType.class.getDeclaredField("factory");
        ff.setAccessible(true);
        var factory = (EntityType.EntityFactory<Entity>) ff.get(t);
        Entity e = factory.create((EntityType<Entity>) t, level);
        if (e != null) return e;
        if (t == EntityType.FISHING_BOBBER) {
            var c = net.minecraft.world.entity.projectile.FishingHook.class.getDeclaredConstructor(EntityType.class, Level.class);
            c.setAccessible(true);
            return c.newInstance(t, level);
        }
        throw new IllegalStateException("no factory for " + t);
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    static RegistryAccess registryAccess() {
        var lookup = VanillaRegistries.createLookup();
        Map<ResourceKey<?>, Registry<?>> data = new HashMap<>();
        lookup.listRegistries().forEach(k -> {
            ResourceKey key = (ResourceKey) k;
            if (BuiltInRegistries.REGISTRY.containsKey(key.location())) return;
            net.minecraft.core.HolderLookup.RegistryLookup rl = lookup.lookupOrThrow(key);
            MappedRegistry r = new MappedRegistry(key, com.mojang.serialization.Lifecycle.stable());
            ((java.util.stream.Stream<?>) rl.listElements())
                    .sorted(java.util.Comparator.comparing(ref -> ((Holder.Reference<?>) ref).key().location().toString()))
                    .forEach(ref -> Registry.register(r, ((Holder.Reference) ref).key(), ((Holder.Reference) ref).value()));
            data.put(key, r.freeze());
        });
        List<Registry<?>> all = new ArrayList<>(data.values());
        for (Registry<?> r : BuiltInRegistries.REGISTRY) if (!data.containsKey(r.key())) all.add(r);
        return new RegistryAccess.ImmutableRegistryAccess(all).freeze();
    }

    static Level fakeLevel(RegistryAccess access) throws Exception {
        Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        uf.setAccessible(true);
        sun.misc.Unsafe u = (sun.misc.Unsafe) uf.get(null);
        Level level = (Level) u.allocateInstance(ServerLevel.class);
        put(u, level, Level.class, "random", RandomSource.create(0L));
        put(u, level, Level.class, "threadSafeRandom", RandomSource.createThreadSafe());
        put(u, level, Level.class, "registryAccess", access);
        put(u, level, Level.class, "profiler", (java.util.function.Supplier<?>) () -> InactiveProfiler.INSTANCE);
        put(u, level, Level.class, "damageSources", new DamageSources(access));
        put(u, level, Level.class, "levelData", java.lang.reflect.Proxy.newProxyInstance(VanillaEntityProbe.class.getClassLoader(),
                new Class<?>[] {net.minecraft.world.level.storage.WritableLevelData.class}, (proxy, m, a) -> zero(m.getReturnType())));
        Object server = u.allocateInstance(net.minecraft.server.dedicated.DedicatedServer.class);
        put(u, server, net.minecraft.server.MinecraftServer.class, "scoreboard", new net.minecraft.server.ServerScoreboard((net.minecraft.server.MinecraftServer) server));
        put(u, level, ServerLevel.class, "server", server);
        return level;
    }

    static Object zero(Class<?> t) {
        if (t == long.class) return 0L;
        if (t == int.class) return 0;
        if (t == boolean.class) return false;
        if (t == float.class) return 0F;
        if (t == double.class) return 0D;
        return null;
    }

    static void put(sun.misc.Unsafe u, Object o, Class<?> owner, String name, Object v) throws Exception {
        Field f = owner.getDeclaredField(name);
        u.putObject(o, u.objectFieldOffset(f), v);
    }
}
