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
 * 기본으로 ItemStack 과 ItemMeta(별칭 "ItemMeta")가 등록돼 있다.
 */
public class ConfigurationSerialization {

    /** 직렬화한 맵에서 별칭을 담는 키. */
    public static final String SERIALIZED_TYPE_KEY = "==";

    private static final Map<String, Class<? extends ConfigurationSerializable>> ALIASES = new ConcurrentHashMap<>();

    static {
        registerClass(ItemStack.class);
        registerClass(org.bukkit.craftbukkit.inventory.SerializableMeta.class);
    }

    private final Class<? extends ConfigurationSerializable> clazz;

    protected ConfigurationSerialization(Class<? extends ConfigurationSerializable> clazz) { this.clazz = clazz; }

    protected Method getMethod(String name, boolean isStatic) {
        try {
            Method method = clazz.getDeclaredMethod(name, Map.class);
            if (!ConfigurationSerializable.class.isAssignableFrom(method.getReturnType())) return null;
            if (Modifier.isStatic(method.getModifiers()) != isStatic) return null;
            return method;
        } catch (NoSuchMethodException | SecurityException e) {
            return null;
        }
    }

    protected Constructor<? extends ConfigurationSerializable> getConstructor() {
        try {
            return clazz.getConstructor(Map.class);
        } catch (NoSuchMethodException | SecurityException e) {
            return null;
        }
    }

    protected ConfigurationSerializable deserializeViaMethod(Method method, Map<String, ?> args) {
        try {
            ConfigurationSerializable result = (ConfigurationSerializable) method.invoke(null, args);
            if (result == null) {
                Logger.getLogger(ConfigurationSerialization.class.getName()).log(Level.SEVERE,
                        "Could not call method '" + method + "' of " + clazz + " for deserialization: method returned null");
            } else {
                return result;
            }
        } catch (Throwable ex) {
            Logger.getLogger(ConfigurationSerialization.class.getName()).log(Level.SEVERE,
                    "Could not call method '" + method + "' of " + clazz + " for deserialization",
                    ex instanceof InvocationTargetException ? ex.getCause() : ex);
        }
        return null;
    }

    protected ConfigurationSerializable deserializeViaCtor(Constructor<? extends ConfigurationSerializable> ctor, Map<String, ?> args) {
        try {
            return ctor.newInstance(args);
        } catch (Throwable ex) {
            Logger.getLogger(ConfigurationSerialization.class.getName()).log(Level.SEVERE,
                    "Could not call constructor '" + ctor + "' of " + clazz + " for deserialization",
                    ex instanceof InvocationTargetException ? ex.getCause() : ex);
        }
        return null;
    }

    /** deserialize → valueOf → 생성자 순으로 시도한다. 되읽지 못하면 null(원인은 SEVERE 로그). */
    public ConfigurationSerializable deserialize(Map<String, ?> args) {
        if (args == null) throw new IllegalArgumentException("Args must not be null");
        ConfigurationSerializable result = null;
        Method method = getMethod("deserialize", true);
        if (method != null) result = deserializeViaMethod(method, args);
        if (result == null) {
            method = getMethod("valueOf", true);
            if (method != null) result = deserializeViaMethod(method, args);
        }
        if (result == null) {
            Constructor<? extends ConfigurationSerializable> ctor = getConstructor();
            if (ctor != null) result = deserializeViaCtor(ctor, args);
        }
        return result;
    }

    /** 클래스를 정해 되읽는다. */
    public static ConfigurationSerializable deserializeObject(Map<String, ?> args, Class<? extends ConfigurationSerializable> clazz) {
        return new ConfigurationSerialization(clazz).deserialize(args);
    }

    /** {@code ==} 키의 별칭으로 클래스를 찾아 되읽는다. 키가 없거나 별칭을 모르면 IllegalArgumentException. */
    public static ConfigurationSerializable deserializeObject(Map<String, ?> args) {
        if (!args.containsKey(SERIALIZED_TYPE_KEY)) throw new IllegalArgumentException("Args doesn't contain type key ('" + SERIALIZED_TYPE_KEY + "')");
        String alias = (String) args.get(SERIALIZED_TYPE_KEY);
        if (alias == null) throw new IllegalArgumentException("Cannot have null alias");
        Class<? extends ConfigurationSerializable> clazz = getClassByAlias(alias);
        if (clazz == null) throw new IllegalArgumentException("Specified class does not exist ('" + alias + "')");
        return new ConfigurationSerialization(clazz).deserialize(args);
    }

    /** 클래스를 별칭과 클래스 이름 두 이름으로 등록한다. DelegateDeserialization 이 붙은 클래스는 등록하지 않는다(맡은 클래스가 대신한다). */
    public static void registerClass(Class<? extends ConfigurationSerializable> clazz) {
        if (clazz.getAnnotation(DelegateDeserialization.class) == null) {
            registerClass(clazz, getAlias(clazz));
            registerClass(clazz, clazz.getName());
        }
    }

    public static void registerClass(Class<? extends ConfigurationSerializable> clazz, String alias) { ALIASES.put(alias, clazz); }

    public static void unregisterClass(String alias) { ALIASES.remove(alias); }

    /** 그 클래스로 등록된 별칭을 모두 지운다. */
    public static void unregisterClass(Class<? extends ConfigurationSerializable> clazz) { ALIASES.values().removeIf(c -> c == clazz); }

    /** 별칭의 클래스. 없으면 null. */
    public static Class<? extends ConfigurationSerializable> getClassByAlias(String alias) { return ALIASES.get(alias); }

    /** 클래스의 별칭: DelegateDeserialization 이 있으면 맡은 클래스의 것, 다음 SerializableAs, 없으면 클래스 이름. */
    public static String getAlias(Class<? extends ConfigurationSerializable> clazz) {
        DelegateDeserialization delegate = clazz.getAnnotation(DelegateDeserialization.class);
        if (delegate != null && delegate.value() != clazz) return getAlias(delegate.value());
        SerializableAs alias = clazz.getAnnotation(SerializableAs.class);
        if (alias != null) return alias.value();
        return clazz.getName();
    }
}
