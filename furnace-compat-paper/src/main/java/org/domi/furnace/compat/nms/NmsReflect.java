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
        if (probe) {
            LOG.debug("플러그인 {}: NMS 클래스 {} 없음(다른 버전 모듈 탐색)", plugin, name);
            return;
        }
        if (NmsGrades.gradeC(n) != null || (ENABLED.contains(plugin) && missingClassLog(plugin) == MissingClassLog.EACH)) {
            // 등급 C(Paper 1.21.1 에 있지만 Furnace 가 일부러 주지 않는 클래스)는 진짜 차이라 하나씩 경고한다. each 설정이면 켠 뒤 실패도 하나씩
            if (WARNED.add(plugin + "\0" + name)) {
                LOG.warn("플러그인 {} 이(가) NMS 클래스 {} 를 리플렉션으로 찾았으나 이 서버에 없다 — {} (ClassNotFoundException 을 그대로 준다)",
                        plugin, name, NmsGrades.reason(n));
            } else {
                LOG.debug("플러그인 {}: NMS 클래스 {} 없음", plugin, name);
            }
            return;
        }
        // 그 밖은 대개 기능 감지다(ProtocolLib 은 Spigot 이름 → Mojang 이름 → 옛 이름을 차례로 찔러 본다 — Paper 1.21.1 도 같은 ClassNotFoundException 을
        // 주고 아무것도 찍지 않는다). 하나씩 경고하면 첫 접속에 수십 줄이라 모았다가 플러그인마다 한 줄로 알린다(D1, 2026-11-15). 이름은 DEBUG 와 each 설정으로 본다.
        if (!WARNED.add(plugin + "\0" + name)) {
            LOG.debug("플러그인 {}: NMS 클래스 {} 없음", plugin, name);
            return;
        }
        Set<String> batch = MISSES.computeIfAbsent(plugin, k -> java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>()));
        batch.add(name);
        LOG.debug("플러그인 {}: NMS 클래스 {} 없음 — 모아서 한 줄로 알린다", plugin, name);
        // 켜는 중이면 켜기 끝(pluginEnabled)에 알린다. 켠 뒤면 잠시 모았다가(첫 접속 때처럼 한꺼번에 찔러 보는 묶음을 한 줄로) 알린다
        if (ENABLED.contains(plugin) && PENDING.add(plugin)) {
            java.util.concurrent.CompletableFuture.delayedExecutor(BATCH_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .execute(() -> { PENDING.remove(plugin); flush(plugin, false); });
        }
    }

    /** 켠 뒤 클래스 찾기 실패를 모으는 시간. ProtocolLib 이 첫 접속 때 찔러 보는 묶음은 수 ms 안에 끝난다. */
    static final long BATCH_MILLIS = 2_000;

    /** 켜기를 마친 플러그인 — 그 뒤의 실패는 잠시 모아 알린다(each 설정이면 하나씩). */
    private static final Set<String> ENABLED = ConcurrentHashMap.newKeySet();
    /** 아직 알리지 않은 클래스 찾기 실패(플러그인 → 이름, 처음 본 순서). */
    private static final java.util.Map<String, Set<String>> MISSES = new ConcurrentHashMap<>();
    /** 켠 뒤 모으는 중인(알림이 예약된) 플러그인. */
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
    /** 플러그인별 알림 방식(설정 plugins.compat.nms.missing-class-log). 없으면 SUMMARY. */
    private static final java.util.Map<String, MissingClassLog> MISSING_CLASS_LOG = new ConcurrentHashMap<>();

    /** 설정 키 이름. */
    public static final String MISSING_CLASS_LOG_KEY = "plugins.compat.nms.missing-class-log";

    /** 리플렉션으로 찾다 없는 NMS 클래스를 어떻게 알릴까. */
    public enum MissingClassLog {
        /** 기본: 모았다가 플러그인마다 한 줄(개수·앞 몇 개 이름), 전체 이름은 DEBUG. */
        SUMMARY,
        /** 이름마다 WARN 한 줄(플러그인 개발자가 어느 이름에서 실패하는지 볼 때). 켜는 동안의 실패는 그래도 켜기 끝에 한 줄로 모은다. */
        EACH;

        /** 설정 값(summary|each, 대소문자 무시) → 값. 모르는 값이면 null. */
        public static MissingClassLog parse(String v) {
            if (v == null) return null;
            return switch (v.strip().toLowerCase(java.util.Locale.ROOT)) {
                case "summary" -> SUMMARY;
                case "each" -> EACH;
                default -> null;
            };
        }
    }

    /** 이 플러그인의 알림 방식을 정한다(호환 형식이 플러그인을 올릴 때 설정 값으로 부른다). null 이면 기본값. */
    public static void missingClassLog(String plugin, MissingClassLog mode) {
        if (plugin == null) return;
        if (mode == null || mode == MissingClassLog.SUMMARY) MISSING_CLASS_LOG.remove(plugin);
        else MISSING_CLASS_LOG.put(plugin, mode);
    }

    static MissingClassLog missingClassLog(String plugin) { return MISSING_CLASS_LOG.getOrDefault(plugin, MissingClassLog.SUMMARY); }

    /**
     * 플러그인이 켜기(onEnable)를 마쳤다(실패했어도). 그때까지 리플렉션으로 찾다 없던 NMS 클래스를 WARN 한 줄로 알린다 — ProtocolLib 처럼 이름을
     * 차례로 찔러 보는 플러그인은 Paper 1.21.1 에서도 같은 ClassNotFoundException 을 받으므로 하나씩 경고하면 소음이지만, 기능이 안 될 때 운영자가
     * 볼 단서라 한 줄은 남긴다. 그 뒤의 실패도 잠시 모아 한 줄로 알린다(each 설정이면 하나씩).
     */
    public static void pluginEnabled(String plugin) {
        if (plugin == null) return;
        ENABLED.add(plugin);
        flush(plugin, true);
    }

    /** 플러그인을 껐다(켜기에 이르지 못하고 꺼진 것 포함) — 남은 목록을 알리고, 다시 켜면(핫 리로드) 다시 켜는 중으로 모은다. */
    public static void pluginDisabled(String plugin) {
        if (plugin == null) return;
        flush(plugin, !ENABLED.contains(plugin));
        ENABLED.remove(plugin);
    }

    /** 요약 줄에 보여 줄 앞 이름 수. */
    static final int SHOWN = 5;

    private static void flush(String plugin, boolean starting) {
        Set<String> misses = MISSES.remove(plugin);
        if (misses == null || misses.isEmpty()) return;
        java.util.List<String> names;
        synchronized (misses) { names = java.util.List.copyOf(misses); }
        LOG.warn(summary(plugin, names, starting));
        LOG.debug("플러그인 {}: 없던 NMS 클래스 전체 {}개: {}", plugin, names.size(), names);
    }

    /** 요약 한 줄. 운영자에게는 "대개 정상", 플러그인 개발자에게는 이름을 보는 두 길(DEBUG·each)을 알린다. */
    static String summary(String plugin, java.util.List<String> names, boolean starting) {
        String shown = names.size() <= SHOWN ? String.join(", ", names)
                : String.join(", ", names.subList(0, SHOWN)) + " 외 " + (names.size() - SHOWN) + "개";
        return "플러그인 " + plugin + " 이(가) " + (starting ? "켜지는 동안 " : "") + "리플렉션으로 찾은 NMS 클래스 " + names.size()
                + "개가 이 서버에 없어 ClassNotFoundException 을 그대로 줬다: " + shown
                + " — 대개 다른 버전·Spigot 이름을 차례로 시도하는 기능 감지라 Paper 1.21.1 에서도 없고 정상이다. 그 플러그인 기능이 안 될 때만: 이름 전체는 DEBUG 로그, "
                + "이름마다 한 줄은 " + MISSING_CLASS_LOG_KEY + "=each, 지원 상태는 docs/feature-parity/compat-h7-2026-11-08.md";
    }

    /** 시험용: 모은 것을 지금 알린다(켠 뒤 예약을 기다리지 않고). */
    static void flushNow(String plugin) {
        PENDING.remove(plugin);
        flush(plugin, false);
    }

    /** 시험용. */
    static void resetStartupMisses() {
        ENABLED.clear();
        MISSES.clear();
        PENDING.clear();
        MISSING_CLASS_LOG.clear();
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
