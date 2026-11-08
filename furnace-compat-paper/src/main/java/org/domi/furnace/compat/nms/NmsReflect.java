package org.domi.furnace.compat.nms;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 리플렉션 멤버 찾기 다리(설계 H5 5.2, H7a). 변환된 플러그인 바이트코드의 {@code Class#getDeclaredField} 류와
 * {@code MethodHandles.Lookup#find*} 호출이 여기로 온다. 하는 일은 원래 호출 그대로이고, NMS·CraftBukkit 클래스에서 찾지 못했을 때만
 * "플러그인 X 가 NMS 멤버 Y 를 찾았으나 없음"을 (플러그인, 대상)마다 한 번 WARN 으로 남긴 뒤 <b>원래 예외를 그대로</b> 던진다
 * (가짜로 있는 척하지 않는다 — {@code try { 새 이름 } catch { 옛 이름 }} 식 기능 감지를 지킨다).
 *
 * <p>버전 접미사로 알리기(옵션 {@code plugins.compat.nms.version-package=versioned})의 이름 읽기 다리도 여기 있다.
 */
public final class NmsReflect {

    private static final Logger LOG = LoggerFactory.getLogger("Furnace");
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private NmsReflect() {}

    // ------------------------------------------------------------------ 클래스 이름 찾기 실패 기록 (CraftNames 가 부른다)

    /** NMS 이름 클래스를 찾지 못했다. 다른 버전 접미사·1.17 이전 패키지(버전 모듈 고르기 탐색)는 DEBUG. */
    static void classMissing(String plugin, String name) {
        if (plugin == null || name == null) return;
        String n = name.replace('.', '/');
        if (!NmsSymbols.isNms(n)) return;
        boolean probe = n.startsWith("net/minecraft/server/v1_") || n.startsWith("org/bukkit/craftbukkit/v1_");
        if (!probe && WARNED.add(plugin + "\0" + name)) {
            LOG.warn("플러그인 {} 이(가) NMS 클래스 {} 를 리플렉션으로 찾았으나 이 서버에 없다 — {} (ClassNotFoundException 을 그대로 준다)",
                    plugin, name, NmsGrades.reason(n));
        } else {
            LOG.debug("플러그인 {}: NMS 클래스 {} 없음", plugin, name);
        }
    }

    private static void memberMissing(String plugin, Class<?> owner, String member) {
        if (owner == null || !NmsSymbols.isNms(owner.getName().replace('.', '/'))) return;
        String target = owner.getName() + "#" + member;
        if (WARNED.add(plugin + "\0" + target)) {
            LOG.warn("플러그인 {} 이(가) NMS 멤버 {} 를 리플렉션으로 찾았으나 shim 에 없다 — 원래 예외를 그대로 준다", plugin, target);
        } else {
            LOG.debug("플러그인 {}: NMS 멤버 {} 없음", plugin, target);
        }
    }

    // ------------------------------------------------------------------ Spigot 이름 (H7d)

    /** 그 플러그인이 Spigot 이름 대응표를 쓰면 Spigot 필드 이름 → Mojang 이름(모르면 그대로). */
    private static String field(Class<?> c, String name, String plugin, boolean declared) {
        SpigotMappings m = SpigotMappings.forPlugin(plugin);
        return m == null || name == null ? name : m.reflectField(c, name, declared);
    }

    private static String method(Class<?> c, String name, Class<?>[] params, String plugin, boolean declared) {
        SpigotMappings m = SpigotMappings.forPlugin(plugin);
        return m == null || name == null ? name : m.reflectMethod(c, name, params, declared);
    }

    private static String method(Class<?> c, String name, MethodType t, String plugin) {
        SpigotMappings m = SpigotMappings.forPlugin(plugin);
        return m == null || name == null || t == null ? name : m.reflectMethod(c, name, t.toMethodDescriptorString(), false);
    }

    // ------------------------------------------------------------------ Class#get*

    public static Field getDeclaredField(Class<?> c, String name, String plugin) throws NoSuchFieldException {
        try {
            return c.getDeclaredField(field(c, name, plugin, true));
        } catch (NoSuchFieldException e) {
            memberMissing(plugin, c, name);
            throw e;
        }
    }

    public static Field getField(Class<?> c, String name, String plugin) throws NoSuchFieldException {
        try {
            return c.getField(field(c, name, plugin, false));
        } catch (NoSuchFieldException e) {
            memberMissing(plugin, c, name);
            throw e;
        }
    }

    public static Method getDeclaredMethod(Class<?> c, String name, Class<?>[] params, String plugin) throws NoSuchMethodException {
        try {
            return c.getDeclaredMethod(method(c, name, params, plugin, true), params);
        } catch (NoSuchMethodException e) {
            memberMissing(plugin, c, name + "(..)");
            throw e;
        }
    }

    public static Method getMethod(Class<?> c, String name, Class<?>[] params, String plugin) throws NoSuchMethodException {
        try {
            return c.getMethod(method(c, name, params, plugin, false), params);
        } catch (NoSuchMethodException e) {
            memberMissing(plugin, c, name + "(..)");
            throw e;
        }
    }

    public static Constructor<?> getDeclaredConstructor(Class<?> c, Class<?>[] params, String plugin) throws NoSuchMethodException {
        try {
            return c.getDeclaredConstructor(params);
        } catch (NoSuchMethodException e) {
            memberMissing(plugin, c, "<init>(..)");
            throw e;
        }
    }

    public static Constructor<?> getConstructor(Class<?> c, Class<?>[] params, String plugin) throws NoSuchMethodException {
        try {
            return c.getConstructor(params);
        } catch (NoSuchMethodException e) {
            memberMissing(plugin, c, "<init>(..)");
            throw e;
        }
    }

    // ------------------------------------------------------------------ Lookup#find*

    public static MethodHandle findVirtual(MethodHandles.Lookup l, Class<?> c, String name, MethodType t, String plugin)
            throws NoSuchMethodException, IllegalAccessException {
        try {
            return l.findVirtual(c, method(c, name, t, plugin), t);
        } catch (NoSuchMethodException e) {
            memberMissing(plugin, c, name + t);
            throw e;
        }
    }

    public static MethodHandle findStatic(MethodHandles.Lookup l, Class<?> c, String name, MethodType t, String plugin)
            throws NoSuchMethodException, IllegalAccessException {
        try {
            return l.findStatic(c, method(c, name, t, plugin), t);
        } catch (NoSuchMethodException e) {
            memberMissing(plugin, c, name + t);
            throw e;
        }
    }

    public static MethodHandle findConstructor(MethodHandles.Lookup l, Class<?> c, MethodType t, String plugin)
            throws NoSuchMethodException, IllegalAccessException {
        try {
            return l.findConstructor(c, t);
        } catch (NoSuchMethodException e) {
            memberMissing(plugin, c, "<init>" + t);
            throw e;
        }
    }

    public static MethodHandle findGetter(MethodHandles.Lookup l, Class<?> c, String name, Class<?> type, String plugin)
            throws NoSuchFieldException, IllegalAccessException {
        try {
            return l.findGetter(c, field(c, name, plugin, false), type);
        } catch (NoSuchFieldException e) {
            memberMissing(plugin, c, name);
            throw e;
        }
    }

    public static MethodHandle findSetter(MethodHandles.Lookup l, Class<?> c, String name, Class<?> type, String plugin)
            throws NoSuchFieldException, IllegalAccessException {
        try {
            return l.findSetter(c, field(c, name, plugin, false), type);
        } catch (NoSuchFieldException e) {
            memberMissing(plugin, c, name);
            throw e;
        }
    }

    public static MethodHandle findStaticGetter(MethodHandles.Lookup l, Class<?> c, String name, Class<?> type, String plugin)
            throws NoSuchFieldException, IllegalAccessException {
        try {
            return l.findStaticGetter(c, field(c, name, plugin, false), type);
        } catch (NoSuchFieldException e) {
            memberMissing(plugin, c, name);
            throw e;
        }
    }

    public static MethodHandle findStaticSetter(MethodHandles.Lookup l, Class<?> c, String name, Class<?> type, String plugin)
            throws NoSuchFieldException, IllegalAccessException {
        try {
            return l.findStaticSetter(c, field(c, name, plugin, false), type);
        } catch (NoSuchFieldException e) {
            memberMissing(plugin, c, name);
            throw e;
        }
    }

    // ------------------------------------------------------------------ version-package=versioned 의 이름 읽기

    /** {@code Class#getName()} 대신: CraftBukkit 클래스면 이 서버 버전 접미사를 붙인 이름. */
    public static String versionedName(Class<?> c) { return CraftNames.versioned(c.getName()); }

    /** {@code Class#getPackageName()} 대신. */
    public static String versionedPackageName(Class<?> c) { return CraftNames.versioned(c.getPackageName()); }

    /** {@code Package#getName()} 대신. */
    public static String versionedPackage(Package p) { return CraftNames.versioned(p.getName()); }

    /** 시험용. */
    public static void resetWarnings() { WARNED.clear(); }
}
