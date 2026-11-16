package org.domi.furnace.compat.nms;

import org.domi.furnace.compat.i18n.CompatLang;
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

    // ------------------------------------------------------------------ 필드 쓰기 장벽(N2b-4, 설계 3.3-3) — NMS 이름 힌트가 있는 클래스의 Field.get·set 이 온다

    /**
     * {@code Field.get(target)} 앞: 로드된 청크 섹션 보기 컨테이너의 {@code data} 를 읽으면 그 순간 내용으로 바닐라 배치를 실체화한다(FAWE 가 섹션을 읽는 길).
     * 아무것도 던지지 않는다(실패하면 원래 필드 값 그대로).
     */
    public static void beforeFieldGet(Field field, Object target) {
        if (target instanceof net.minecraft.world.level.chunk.PalettedContainer<?>) {
            try {
                net.minecraft.world.level.chunk.ChunkAccess.furnaceBarrierFieldGet(field, target);
            } catch (RuntimeException | LinkageError ignored) {
                // 필드 값 그대로
            }
        }
    }

    /**
     * {@code Field.set*(target, …)} 뒤: 로드된 청크의 섹션·컨테이너 필드를 바꿨으면 그 거울에 알린다(주인이 다음 맞춤 지점에서 들여온다 — 정확성은 틱 끝
     * 훑기가 보장하고 이 알림은 지연을 줄인다). 아무것도 던지지 않는다.
     */
    public static void afterFieldSet(Field field, Object target) {
        if (target instanceof net.minecraft.world.level.chunk.LevelChunkSection || target instanceof net.minecraft.world.level.chunk.PalettedContainer<?>) {
            try {
                net.minecraft.world.level.chunk.ChunkAccess.furnaceBarrierFieldSet(target);
            } catch (RuntimeException | LinkageError ignored) {
                // 훑기가 잡는다
            }
        }
    }

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
        if (NmsGrades.gradeC(n) != null || missingClassLog(plugin) == MissingClassLog.EACH) {
            // 등급 C(Paper 1.21.1 에 있지만 Furnace 가 일부러 주지 않는 클래스)는 진짜 차이라 하나씩 경고한다. each 설정이면 켜는 중·켠 뒤 모두 하나씩(검토 R2)
            if (WARNED.add(plugin + "\0" + name)) {
                LOG.warn(CompatLang.t("compat.nms-class-not-found", plugin, name, NmsGrades.reason(n)));
            } else {
                LOG.debug("플러그인 {}: NMS 클래스 {} 없음", plugin, name);
            }
            return;
        }
        // 그 밖은 대개 기능 감지다(ProtocolLib 은 Spigot 이름 → Mojang 이름 → 옛 이름을 차례로 찔러 본다 — Paper 1.21.1 도 같은 ClassNotFoundException 을
        // 주고 아무것도 찍지 않는다). 하나씩 경고하면 첫 접속에 수십 줄이라 모았다가 플러그인마다 한 줄로 알린다(D1, 2026-11-15). 이름마다 보려면 each 설정.
        if (!WARNED.add(plugin + "\0" + name)) {
            LOG.debug("플러그인 {}: NMS 클래스 {} 없음", plugin, name);
            return;
        }
        // compute 안에서 넣어야 flush 의 MISSES.remove 와 원자적이다(computeIfAbsent 뒤 add 는 막 떼어 낸 묶음에 들어가 사라질 수 있다, 검토 R1)
        MISSES.compute(plugin, (k, s) -> {
            if (s == null) s = new java.util.LinkedHashSet<>();
            s.add(name);
            return s;
        });
        LOG.debug("플러그인 {}: NMS 클래스 {} 없음 — 모아서 한 줄로 알린다", plugin, name);
        // 켜는 중이면 켜기 끝(pluginEnabled)에 알린다. 켠 뒤면 잠시 모았다가(첫 접속 때처럼 한꺼번에 찔러 보는 묶음을 한 줄로) 알린다
        if (ENABLED.contains(plugin) && PENDING.add(plugin)) {
            long gen = generation(plugin);
            java.util.concurrent.CompletableFuture.delayedExecutor(BATCH_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS).execute(() -> {
                // 그 사이 껐다 켰으면(핫 리로드·시험 초기화) 이 예약은 지난 기간의 것이다 — 새 기간의 이름을 "켜지는 동안" 표시 없이 일찍 알리지 않는다(검토 S1)
                if (generation(plugin) != gen) return;
                PENDING.remove(plugin);
                flush(plugin, false);
            });
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
    /** 플러그인마다 켜고 끈 횟수(예약 알림이 자기 기간의 것인지 본다). */
    private static final java.util.Map<String, Long> GENERATION = new ConcurrentHashMap<>();
    /** 시험 초기화 횟수(초기화 전에 건 예약을 무효로). */
    private static final java.util.concurrent.atomic.AtomicLong RESETS = new java.util.concurrent.atomic.AtomicLong();

    private static long generation(String plugin) { return GENERATION.getOrDefault(plugin, 0L) * 1_000_003L + RESETS.get(); }

    /** 새 기간을 연다: 지난 기간의 예약 알림을 무효로 하고 다음 실패가 새로 예약하게 한다. */
    private static void nextGeneration(String plugin) {
        GENERATION.merge(plugin, 1L, Long::sum);
        PENDING.remove(plugin);
    }

    /** 플러그인별 알림 방식(설정 plugins.compat.nms.missing-class-log). 없으면 SUMMARY. */
    private static final java.util.Map<String, MissingClassLog> MISSING_CLASS_LOG = new ConcurrentHashMap<>();

    /** 설정 키 이름. */
    public static final String MISSING_CLASS_LOG_KEY = "plugins.compat.nms.missing-class-log";

    /** 리플렉션으로 찾다 없는 NMS 클래스를 어떻게 알릴까. */
    public enum MissingClassLog {
        /** 기본: 모았다가 플러그인마다 한 줄(개수·앞 몇 개 이름). */
        SUMMARY,
        /** 이름마다 WARN 한 줄 — 켜는 중이든 켠 뒤든(플러그인 개발자가 어느 이름에서 실패하는지 볼 때). */
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
        nextGeneration(plugin);
        ENABLED.add(plugin);
        flush(plugin, true);
    }

    /** 플러그인을 껐다(켜기에 이르지 못하고 꺼진 것 포함) — 남은 목록을 알리고, 다시 켜면(핫 리로드) 다시 켜는 중으로 모은다. */
    public static void pluginDisabled(String plugin) {
        if (plugin == null) return;
        nextGeneration(plugin);
        flush(plugin, !ENABLED.contains(plugin));
        ENABLED.remove(plugin);
    }

    /** 요약 줄에 보여 줄 앞 이름 수. */
    static final int SHOWN = 5;

    private static void flush(String plugin, boolean starting) {
        Set<String> misses = MISSES.remove(plugin);
        if (misses == null || misses.isEmpty()) return;
        java.util.List<String> names = java.util.List.copyOf(misses);   // 떼어 낸 묶음에는 더 넣는 스레드가 없다(R1)
        LOG.warn(summary(plugin, names, starting));
        LOG.debug("플러그인 {}: 없던 NMS 클래스 전체 {}개: {}", plugin, names.size(), names);
    }

    /** 요약 한 줄. 운영자에게는 "대개 정상", 플러그인 개발자에게는 이름을 모두 보는 설정(each)을 알린다(전체 이름 DEBUG 줄은 운영자가 켤 길이 없어 안내하지 않는다, 검토 R3). */
    static String summary(String plugin, java.util.List<String> names, boolean starting) {
        String shown = names.size() <= SHOWN ? String.join(", ", names)
                : String.join(", ", names.subList(0, SHOWN)) + " " + CompatLang.t("compat.nms-missing-more", names.size() - SHOWN);
        return CompatLang.t(starting ? "compat.nms-missing-summary-starting" : "compat.nms-missing-summary", plugin, names.size(), shown, MISSING_CLASS_LOG_KEY);
    }

    /** 시험용: 모은 것을 지금 알린다(켠 뒤 예약을 기다리지 않고). */
    static void flushNow(String plugin) {
        PENDING.remove(plugin);
        flush(plugin, false);
    }

    /** 시험용. */
    static void resetStartupMisses() {
        RESETS.incrementAndGet();
        ENABLED.clear();
        MISSES.clear();
        PENDING.clear();
        GENERATION.clear();
        MISSING_CLASS_LOG.clear();
    }

    private static void memberMissing(String plugin, Class<?> owner, String member) {
        memberMissing(plugin, owner, member, member);
    }

    /** member=플러그인이 쓴 이름(경고에 보임), looked=Spigot 대응표를 거쳐 실제로 찾은 이름(Paper 표 판정에 씀). */
    private static void memberMissing(String plugin, Class<?> owner, String member, String looked) {
        if (owner == null || !NmsSymbols.isNms(owner.getName().replace('.', '/'))) return;
        String target = owner.getName() + "#" + member;
        if (absentInPaper(owner, looked)) {
            // 진짜 Paper 1.21.1 에도 없는 이름(옛 판 이름·기능 감지용 후보)이라 같은 예외가 나는 것이다 — 구멍이 아니므로 경고하지 않는다
            LOG.debug("Plugin {}: the NMS member {} does not exist in Paper 1.21.1 either", plugin, target);
        } else if (WARNED.add(plugin + "\0" + target)) {
            LOG.warn(CompatLang.t("compat.nms-member-not-found", plugin, target));
        } else {
            LOG.debug("플러그인 {}: NMS 멤버 {} 없음", plugin, target);
        }
    }

    /** 그 멤버 이름이 진짜 Paper 1.21.1 의 그 클래스(와 조상)에도 없다고 확실히 아는가(표가 모르면 false). 생성자·알 수 없는 꼴은 늘 false. */
    static boolean absentInPaper(Class<?> owner, String member) {
        if (member.startsWith("<")) return false;
        int paren = member.indexOf('(');
        Boolean has = paren < 0 ? NmsPaperMembers.hasField(owner.getName(), member) : NmsPaperMembers.hasMethod(owner.getName(), member.substring(0, paren));
        return has != null && !has;
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
        String looked = field(c, name, plugin, true);
        try {
            return c.getDeclaredField(looked);
        } catch (NoSuchFieldException e) {
            memberMissing(plugin, c, name, looked);
            throw e;
        }
    }

    public static Field getField(Class<?> c, String name, String plugin) throws NoSuchFieldException {
        String looked = field(c, name, plugin, false);
        try {
            return c.getField(looked);
        } catch (NoSuchFieldException e) {
            memberMissing(plugin, c, name, looked);
            throw e;
        }
    }

    public static Method getDeclaredMethod(Class<?> c, String name, Class<?>[] params, String plugin) throws NoSuchMethodException {
        String looked = method(c, name, params, plugin, true);
        try {
            return c.getDeclaredMethod(looked, params);
        } catch (NoSuchMethodException e) {
            memberMissing(plugin, c, name + "(..)", looked + "(..)");
            throw e;
        }
    }

    public static Method getMethod(Class<?> c, String name, Class<?>[] params, String plugin) throws NoSuchMethodException {
        String looked = method(c, name, params, plugin, false);
        try {
            return c.getMethod(looked, params);
        } catch (NoSuchMethodException e) {
            memberMissing(plugin, c, name + "(..)", looked + "(..)");
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
        String looked = method(c, name, t, plugin);
        try {
            return l.findVirtual(c, looked, t);
        } catch (NoSuchMethodException e) {
            memberMissing(plugin, c, name + t, looked + t);
            throw e;
        }
    }

    public static MethodHandle findStatic(MethodHandles.Lookup l, Class<?> c, String name, MethodType t, String plugin)
            throws NoSuchMethodException, IllegalAccessException {
        String looked = method(c, name, t, plugin);
        try {
            return l.findStatic(c, looked, t);
        } catch (NoSuchMethodException e) {
            memberMissing(plugin, c, name + t, looked + t);
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
        String looked = field(c, name, plugin, false);
        try {
            return l.findGetter(c, looked, type);
        } catch (NoSuchFieldException e) {
            memberMissing(plugin, c, name, looked);
            throw e;
        }
    }

    public static MethodHandle findSetter(MethodHandles.Lookup l, Class<?> c, String name, Class<?> type, String plugin)
            throws NoSuchFieldException, IllegalAccessException {
        String looked = field(c, name, plugin, false);
        try {
            return l.findSetter(c, looked, type);
        } catch (NoSuchFieldException e) {
            memberMissing(plugin, c, name, looked);
            throw e;
        }
    }

    public static MethodHandle findStaticGetter(MethodHandles.Lookup l, Class<?> c, String name, Class<?> type, String plugin)
            throws NoSuchFieldException, IllegalAccessException {
        String looked = field(c, name, plugin, false);
        try {
            return l.findStaticGetter(c, looked, type);
        } catch (NoSuchFieldException e) {
            memberMissing(plugin, c, name, looked);
            throw e;
        }
    }

    public static MethodHandle findStaticSetter(MethodHandles.Lookup l, Class<?> c, String name, Class<?> type, String plugin)
            throws NoSuchFieldException, IllegalAccessException {
        String looked = field(c, name, plugin, false);
        try {
            return l.findStaticSetter(c, looked, type);
        } catch (NoSuchFieldException e) {
            memberMissing(plugin, c, name, looked);
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
