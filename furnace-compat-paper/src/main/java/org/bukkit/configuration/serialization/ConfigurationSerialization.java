// Bukkit ConfigurationSerialization — 공개·protected 서명과 예외·로그 문구(관측 가능한 것)만 Bukkit 에 맞추고 몸통은 Furnace 가 새로 썼다.
package org.bukkit.configuration.serialization;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.inventory.ItemStack;

/**
 * {@link ConfigurationSerializable} 의 별칭 등록부와 되읽기. 별칭 → 클래스 표는 서버 전체가 하나다(Bukkit 과 같다).
 * 기본으로 ItemStack·ItemMeta(별칭 "ItemMeta")·Color·FireworkEffect·AttributeModifier·Vector·BlockVector·BoundingBox·PotionEffect·Location·SpawnRule·OfflinePlayer(별칭 "Player")가 등록돼 있다.
 * 배너 무늬(Pattern)는 호환 계층에 배너 API 가 아직 없어 빠져 있다.
 * 되읽기 순서: 정적 {@code deserialize(Map)} → 정적 {@code valueOf(Map)} → {@code (Map)} 생성자. 앞의 것이 없거나 null·예외면 다음으로 간다.
 */
public class ConfigurationSerialization {

    /** 직렬화한 맵에서 별칭을 담는 키. */
    public static final String SERIALIZED_TYPE_KEY = "==";

    private static final Map<String, Class<? extends ConfigurationSerializable>> REGISTRY = new ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicInteger EPOCH = new java.util.concurrent.atomic.AtomicInteger();

    /** 호환 모듈 전용: 등록이 바뀔 때마다 늘어나는 번호(읽을 때 모르던 별칭을 다시 시도할지 가리는 데 쓴다). */
    public static int epoch() { return EPOCH.get(); }
    private static final Logger LOG = Logger.getLogger(ConfigurationSerialization.class.getName());

    static {
        registerClass(ItemStack.class);
        registerClass(org.bukkit.craftbukkit.inventory.SerializableMeta.class);
        registerClass(org.bukkit.Color.class);
        registerClass(org.bukkit.FireworkEffect.class);
        registerClass(org.bukkit.attribute.AttributeModifier.class);
        // Paper 기본 등록 가운데 호환 계층에 있는 것(A15-01) — 별칭과 클래스 이름 둘 다로 되읽힌다
        registerClass(org.bukkit.util.Vector.class);
        registerClass(org.bukkit.potion.PotionEffect.class);
        registerClass(org.bukkit.Location.class);
        registerClass(org.bukkit.util.BlockVector.class);
        registerClass(org.bukkit.util.BoundingBox.class);
        registerClass(org.bukkit.block.spawner.SpawnRule.class);
        registerClass(org.bukkit.craftbukkit.CraftOfflinePlayer.class);   // 별칭 Player
    }

    private final Class<? extends ConfigurationSerializable> clazz;

    protected ConfigurationSerialization(Class<? extends ConfigurationSerializable> clazz) { this.clazz = clazz; }

    /** 이 클래스가 직접 선언한 {@code name(Map)} 메서드 — 반환형이 ConfigurationSerializable 이고 정적 여부가 맞을 때만. */
    protected Method getMethod(String name, boolean isStatic) {
        for (Method m : declaredMethodsSafely()) {
            if (!m.getName().equals(name)) continue;
            Class<?>[] params = m.getParameterTypes();
            boolean shape = params.length == 1 && params[0] == Map.class;
            if (shape && Modifier.isStatic(m.getModifiers()) == isStatic && ConfigurationSerializable.class.isAssignableFrom(m.getReturnType())) return m;
        }
        return null;
    }

    private Method[] declaredMethodsSafely() {
        try {
            return clazz.getDeclaredMethods();
        } catch (SecurityException e) {
            return new Method[0];
        }
    }

    /** 공개 {@code (Map)} 생성자, 없으면 null. */
    protected Constructor<? extends ConfigurationSerializable> getConstructor() {
        for (Constructor<?> c : constructorsSafely()) {
            Class<?>[] params = c.getParameterTypes();
            if (params.length == 1 && params[0] == Map.class) {
                @SuppressWarnings("unchecked")
                Constructor<? extends ConfigurationSerializable> typed = (Constructor<? extends ConfigurationSerializable>) c;
                return typed;
            }
        }
        return null;
    }

    private Constructor<?>[] constructorsSafely() {
        try {
            return clazz.getConstructors();
        } catch (SecurityException e) {
            return new Constructor<?>[0];
        }
    }

    protected ConfigurationSerializable deserializeViaMethod(Method method, Map<String, ?> args) {
        String what = "Could not call method '" + method + "' of " + clazz + " for deserialization";
        Object made;
        try {
            made = method.invoke(null, args);
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, what, unwrap(t));
            return null;
        }
        if (made == null) LOG.log(Level.SEVERE, what + ": method returned null");
        return (ConfigurationSerializable) made;
    }

    protected ConfigurationSerializable deserializeViaCtor(Constructor<? extends ConfigurationSerializable> ctor, Map<String, ?> args) {
        try {
            return ctor.newInstance(args);
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "Could not call constructor '" + ctor + "' of " + clazz + " for deserialization", unwrap(t));
            return null;
        }
    }

    private static Throwable unwrap(Throwable t) { return t instanceof InvocationTargetException ite ? ite.getCause() : t; }

    /** 되읽기 순서대로 시도한다. 되읽지 못하면 null(원인은 SEVERE 로그). */
    public ConfigurationSerializable deserialize(Map<String, ?> args) {
        if (args == null) throw new IllegalArgumentException("Args must not be null");
        for (String factory : new String[] {"deserialize", "valueOf"}) {
            Method m = getMethod(factory, true);
            ConfigurationSerializable made = m == null ? null : deserializeViaMethod(m, args);
            if (made != null) return made;
        }
        Constructor<? extends ConfigurationSerializable> ctor = getConstructor();
        return ctor == null ? null : deserializeViaCtor(ctor, args);
    }

    /** 클래스를 정해 되읽는다. */
    public static ConfigurationSerializable deserializeObject(Map<String, ?> args, Class<? extends ConfigurationSerializable> clazz) {
        return new ConfigurationSerialization(clazz).deserialize(args);
    }

    /** {@code ==} 키의 별칭으로 클래스를 찾아 되읽는다. 키가 없거나 별칭을 모르면 IllegalArgumentException. */
    public static ConfigurationSerializable deserializeObject(Map<String, ?> args) {
        if (!args.containsKey(SERIALIZED_TYPE_KEY)) throw new IllegalArgumentException("Args doesn't contain type key ('" + SERIALIZED_TYPE_KEY + "')");
        Object alias = args.get(SERIALIZED_TYPE_KEY);
        if (alias == null) throw new IllegalArgumentException("Cannot have null alias");
        Class<? extends ConfigurationSerializable> target = getClassByAlias((String) alias);
        if (target == null) throw new IllegalArgumentException("Specified class does not exist ('" + alias + "')");
        return deserializeObject(args, target);
    }

    /** 별칭과 클래스 이름 두 이름으로 등록한다. DelegateDeserialization 이 붙은 클래스는 맡은 클래스가 대신하므로 등록하지 않는다. */
    public static void registerClass(Class<? extends ConfigurationSerializable> clazz) {
        if (clazz.isAnnotationPresent(DelegateDeserialization.class)) return;
        for (String name : new String[] {getAlias(clazz), clazz.getName()}) registerClass(clazz, name);
    }

    public static void registerClass(Class<? extends ConfigurationSerializable> clazz, String alias) { REGISTRY.put(alias, clazz); EPOCH.incrementAndGet(); }

    public static void unregisterClass(String alias) { REGISTRY.remove(alias); EPOCH.incrementAndGet(); }

    /** 그 클래스로 등록된 별칭을 모두 지운다. */
    public static void unregisterClass(Class<? extends ConfigurationSerializable> clazz) { REGISTRY.values().removeIf(c -> c == clazz); EPOCH.incrementAndGet(); }

    /** 별칭의 클래스. 없으면 null. */
    public static Class<? extends ConfigurationSerializable> getClassByAlias(String alias) { return REGISTRY.get(alias); }

    /** 클래스의 별칭: DelegateDeserialization 이 있으면 맡은 클래스의 것, 다음 SerializableAs, 없으면 클래스 이름. */
    public static String getAlias(Class<? extends ConfigurationSerializable> clazz) {
        Class<? extends ConfigurationSerializable> c = clazz;
        DelegateDeserialization d;
        while ((d = c.getAnnotation(DelegateDeserialization.class)) != null && d.value() != c) c = d.value();
        SerializableAs as = c.getAnnotation(SerializableAs.class);
        return as != null ? as.value() : c.getName();
    }
}
