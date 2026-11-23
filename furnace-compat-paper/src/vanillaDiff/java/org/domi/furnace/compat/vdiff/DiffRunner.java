package org.domi.furnace.compat.vdiff;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 한 클래스의 바닐라·shim 공개 멤버를 같은 입력으로 불러 견준다(T-235).
 * 견주는 것: 반환값(내리기), 던진 예외 종류, 부른 뒤 받는 쪽·인자의 상태(바뀌는 형식 — MutableBlockPos 등).
 * 멤버마다 고정 시드라 실패는 언제나 같은 입력으로 재현된다.
 */
public final class DiffRunner {

    /**
     * Paper 와 같게 둔 차이 하나의 모양: 바닐라가 이 예외를 던지고 shim 이 저 예외를 던지는 입력만 허용한다(대상 파일 paper 줄).
     * when 이 있으면 그 입력 조건(Paper 패치가 실제로 걸리는 입력인지 — {@link #holds})도 맞아야 한다.
     * 그 밖의 차이는 같은 멤버여도 다름이다.
     */
    public record Allowance(String vanillaThrows, String shimThrows, String when) {
        public Allowance {
            if (when != null && !CONDITIONS.contains(when)) throw new IllegalArgumentException("unknown when=" + when + " (known: " + CONDITIONS + ")");
        }

        boolean matches(Object vanillaOutcome, Object shimOutcome) {
            return token(vanillaThrows, vanillaOutcome) && token(shimThrows, shimOutcome);
        }

        /** "value:X" 는 결과 값 X(열거 상수 이름·숫자·불리언·글), 그 밖은 던진 예외 클래스 이름. */
        private static boolean token(String t, Object outcome) {
            if (!t.startsWith("value:")) return ("throws " + t).equals(outcome);
            String v = t.substring(6);
            if (outcome instanceof List<?> l && l.size() == 3 && "enum".equals(l.get(0))) return v.equals(l.get(2));
            return (outcome instanceof Number || outcome instanceof Boolean || outcome instanceof String s && !s.startsWith("throws ")) && v.equals(String.valueOf(outcome));
        }
    }

    /** 입력 조건 이름들(대상 파일 when=). */
    public static final java.util.Set<String> CONDITIONS = java.util.Set.of(NbtKit.ARRAY_LIMIT, GameKit.PAPER_INDESTRUCTIBLE);

    /** 입력 조건이 이 입력에서 성립하나. 판정 못 하면(올리기 실패 등) 성립하지 않는다 — 허용을 넓히지 않는 쪽. */
    static boolean holds(String when, String key, Object recvSeed, Object[] argSeeds) {
        if (when == null) return true;
        try {
            return switch (when) {
                case NbtKit.ARRAY_LIMIT -> NbtKit.tripsArrayLimit(key, argSeeds);
                case GameKit.PAPER_INDESTRUCTIBLE -> GameKit.paperIndestructible(recvSeed);
                default -> false;
            };
        } catch (Exception e) {
            return false;
        }
    }

    /** 멤버 하나의 결과. */
    /** stoppedAt = 시간 예산으로 멈춘 입력 번호(멈추지 않았으면 -1), planned = 계획한 입력 수. */
    public record MemberResult(String key, Status status, long calls, long skipped, String detail, List<String> examples, int stoppedAt, int planned) {
        public MemberResult(String key, Status status, long calls, long skipped, String detail, List<String> examples) {
            this(key, status, calls, skipped, detail, examples, -1, 0);
        }
    }

    public enum Status {
        /** 모든 입력에서 같았다. */
        SAME,
        /** 다른 입력이 있었다(examples 에 앞 3개). */
        DIFF,
        /** 형식 꾸러미에 없는 형식이라 시험하지 못했다(detail 에 형식). */
        UNPROVED,
        /** 바닐라에만 있다 — 연결되지 않은 멤버(서명 관문 몫). */
        NOT_IN_SHIM,
        /** 대상 파일이 이유와 함께 뺐다. */
        EXCLUDED,
        /** 바닐라와 다르지만 Paper 와 같게 둔 것(대상 파일의 paper 줄 — 프로젝트 원칙: 호환 면은 Paper 와 같게). 관문을 막지 않는다. */
        PAPER
    }

    private final ClassLoader vanilla;
    private final ClassLoader shim;
    private final long seed;
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "vanilla-diff");
        t.setDaemon(true);
        return t;
    });

    public DiffRunner(ClassLoader vanilla, ClassLoader shim, long seed) {
        this.vanilla = vanilla;
        this.shim = shim;
        this.seed = seed;
    }

    /** 바닐라 클래스의 시험 대상 멤버 열쇠(이름(인자 형식,…)). 필드는 이름만. */
    public static String key(Executable e) {
        String name = e instanceof Constructor<?> ? "<init>" : e.getName();
        StringBuilder sb = new StringBuilder(name).append('(');
        Class<?>[] ps = e.getParameterTypes();
        for (int i = 0; i < ps.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(ps[i].getTypeName());
        }
        return sb.append(')').toString();
    }

    /** 시험 대상: 공개 정적 필드, 공개 생성자, 공개 메서드(상위에서 물려받은 net.minecraft 멤버 포함 — 받는 쪽은 이 클래스). */
    public static List<Object> members(Class<?> vc) {
        List<Object> out = new ArrayList<>();
        for (Field f : vc.getDeclaredFields()) {
            int m = f.getModifiers();
            if (Modifier.isPublic(m) && Modifier.isStatic(m) && !f.isSynthetic()) out.add(f);
        }
        if (!Modifier.isAbstract(vc.getModifiers()) && !vc.isEnum()) {
            for (Constructor<?> k : vc.getConstructors()) if (!k.isSynthetic()) out.add(k);
        }
        for (Method m : vc.getMethods()) {
            if (m.isSynthetic() || m.isBridge()) continue;
            String owner = m.getDeclaringClass().getName();
            boolean mc = owner.startsWith("net.minecraft.") || owner.startsWith("com.mojang.");
            boolean objectish = switch (m.getName()) {
                case "equals", "hashCode", "toString", "compareTo" -> !owner.startsWith("java.");
                default -> false;
            };
            if (mc || objectish) out.add(m);
        }
        out.sort(Comparator.comparing(DiffRunner::memberKey));
        return out;
    }

    public static String memberKey(Object member) {
        return member instanceof Field f ? f.getName() : key((Executable) member);
    }

    /**
     * 시간 예산(대상 파일의 class 줄 budget=<초> 로 켠 느린 클래스만 — NbtIo 처럼 바닐라 쪽이 비싼 것). 경계값 입력(앞 400개, 고정 훑기 포함)은
     * 예산과 무관하게 다 돌고, 그 뒤 난수 입력에서 예산을 넘으면 남은 입력을 멈춘다. 멈춘 멤버는 결과(stoppedAt)와 보고서 머리에 드러난다.
     * 까닭: CI 에서는 바닐라 NBT 읽기 실패 한 번이 수십~백 ms 다 — CrashReport 의 SystemReport 가 매번 OSHI 로 하드웨어를 훑는다(11-23 PR 563).
     * 예산이 없는 클래스(값 클래스 등)는 멈추지 않고, HARD_WATCHDOG 를 넘기면 다름(실패)이다. -PvdiffBudget 은 예산이 켜진 클래스의 초를 바꾼다.
     */
    static final long HARD_WATCHDOG_SECONDS = 300;

    /** iterations = 입력 수(경계값 쪽 400개까지 + 나머지 난수). */
    public MemberResult run(Class<?> vc, Class<?> sc, Object member, int iterations) {
        return run(vc, sc, member, iterations, List.of());
    }

    /** allowed = 이 멤버에서 허용하는 Paper 차이 모양(없으면 빈 목록). */
    public MemberResult run(Class<?> vc, Class<?> sc, Object member, int iterations, List<Allowance> allowed) {
        return run(vc, sc, member, iterations, allowed, 0);
    }

    /** budgetSeconds > 0 이면 시간 예산을 켠다(위 HARD_WATCHDOG 설명). */
    public MemberResult run(Class<?> vc, Class<?> sc, Object member, int iterations, List<Allowance> allowed, long budgetSeconds) {
        String key = memberKey(member);
        try {
            if (member instanceof Field f) return field(key, f, sc);
            Future<MemberResult> fut = exec.submit(() -> executable(key, vc, sc, (Executable) member, iterations, allowed, budgetSeconds));
            try {
                return fut.get(HARD_WATCHDOG_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                fut.cancel(true);
                return new MemberResult(key, Status.DIFF, 0, 0, "timeout (" + HARD_WATCHDOG_SECONDS + "s)", List.of());
            } catch (ExecutionException e) {
                return new MemberResult(key, Status.DIFF, 0, 0, "harness error: " + e.getCause(), List.of());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public void close() {
        exec.shutdownNow();
    }

    private MemberResult field(String key, Field vf, Class<?> sc) {
        Field sf;
        try {
            sf = sc.getField(vf.getName());
        } catch (NoSuchFieldException e) {
            return new MemberResult(key, Status.NOT_IN_SHIM, 0, 0, null, List.of());
        }
        try {
            Object a = TypeKit.lower(withLoader(vanilla, () -> vf.get(null)));
            Object b = TypeKit.lower(sf.get(null));
            if (Objects.equals(a, b)) return new MemberResult(key, Status.SAME, 1, 0, null, List.of());
            return new MemberResult(key, Status.DIFF, 1, 0, null, List.of("vanilla=" + a + " shim=" + b));
        } catch (TypeKit.Unsupported u) {
            return new MemberResult(key, Status.UNPROVED, 0, 0, u.getMessage(), List.of());
        } catch (Throwable t) {
            return new MemberResult(key, Status.DIFF, 1, 0, "read failed: " + t, List.of());
        }
    }

    private MemberResult executable(String key, Class<?> vc, Class<?> sc, Executable ve, int iterations, List<Allowance> allowed, long budgetSeconds) throws Exception {
        boolean isStatic = ve instanceof Constructor<?> || Modifier.isStatic(ve.getModifiers());
        Class<?>[] vps = ve.getParameterTypes();
        Class<?>[] sps = new Class<?>[vps.length];
        Executable se;
        try {
            for (int i = 0; i < vps.length; i++) sps[i] = TypeKit.resolve(vps[i].getName(), shim);
            se = ve instanceof Constructor<?> ? sc.getConstructor(sps) : sc.getMethod(ve.getName(), sps);
        } catch (NoSuchMethodException | ClassNotFoundException e) {
            return new MemberResult(key, Status.NOT_IN_SHIM, 0, 0, null, List.of());
        }
        if (isStatic != (se instanceof Constructor<?> || Modifier.isStatic(se.getModifiers()))) {
            return new MemberResult(key, Status.DIFF, 0, 0, "static-ness differs", List.of());
        }
        String self = vc.getName();
        if (!isStatic) {
            String why = TypeKit.unsupportedReason(vc, self);
            if (why != null) return new MemberResult(key, Status.UNPROVED, 0, 0, "receiver " + why, List.of());
        }
        java.lang.reflect.Type[] gps = ve.getGenericParameterTypes();
        if (gps.length != vps.length) gps = vps; // 안쪽 클래스 생성자 등 — 제네릭 정보 없이
        for (int k = 0; k < vps.length; k++) {
            String why = TypeKit.unsupportedReason(gps[k], vps[k], self);
            if (why != null) return new MemberResult(key, Status.UNPROVED, 0, 0, "param " + why, List.of());
        }

        SplittableRandom r = new SplittableRandom(seed ^ key.hashCode() * 0x9E3779B97F4A7C15L ^ vc.getName().hashCode());
        int edgeTrials = Math.min(iterations, 400);
        long calls = 0, skipped = 0, diffs = 0, lowered = 0, threw = 0, paper = 0;
        String unlowerable = null;
        List<String> examples = new ArrayList<>();
        List<String> paperExamples = new ArrayList<>();
        Object lastOutcome = null;
        long start = System.nanoTime();
        int stoppedAt = -1;
        for (int i = 0; i < iterations; i++) {
            if (budgetSeconds > 0 && i >= edgeTrials && System.nanoTime() - start > budgetSeconds * 1_000_000_000L) {
                stoppedAt = i;
                break;
            }
            double edgeP = i < edgeTrials ? 0.8 : 0.1;
            // 처음 SWEEP 개는 모든 자리가 경계값 i 번 — 입력 수·씨앗 순서와 무관하게 늘 같은 입력(고정 근거 입력이 여기 있다)
            Object recvSeed = isStatic ? null : TypeKit.seed(vc, r, i < SWEEP ? i : pick(r, edgeP), self);
            Object[] argSeeds = new Object[vps.length];
            for (int k = 0; k < vps.length; k++) argSeeds[k] = TypeKit.seed(gps[k], vps[k], r, i < SWEEP ? i : pick(r, edgeP), self);
            List<Object> va, sb;
            try {
                va = call(ve, recvSeed, argSeeds, vanilla);
            } catch (TypeKit.RaiseFailed f) {
                skipped++; // 바닐라도 못 만드는 입력 — 시험 입력이 아니다
                continue;
            }
            try {
                sb = call(se, recvSeed, argSeeds, shim);
            } catch (TypeKit.RaiseFailed f) {
                if (f.getCause() instanceof OutOfMemoryError) {
                    skipped++;
                    continue;
                }
                // 바닐라는 만든 입력을 shim 이 못 만든다(예: shim 레지스트리에 없는 블록) — 관측 가능한 차이
                sb = List.of("shim cannot build input: " + f.getMessage() + " (" + f.getCause() + ")");
            }
            List<Object> sa = sb;
            calls++;
            if (va.get(0) instanceof Unlowerable u) {
                unlowerable = u.what;
                continue;
            }
            lowered++;
            lastOutcome = va.get(0);
            if (va.get(0) instanceof String o && o.startsWith("throws ")) threw++;
            boolean harness = isHarness(va.get(0)) || isHarness(sa.get(0));
            if (harness || !Objects.equals(va, sa)) {
                String ex = "input " + describe(recvSeed, argSeeds) + " -> vanilla=" + clip(va) + " shim=" + clip(sa);
                if (!harness && allowed.stream().anyMatch(a -> a.matches(va.get(0), sa.get(0)) && holds(a.when(), key, recvSeed, argSeeds))) {
                    paper++;
                    if (paperExamples.size() < 3) paperExamples.add(ex);
                } else {
                    diffs++;
                    if (examples.size() < 3) examples.add(ex);
                }
            }
        }
        String budget = stoppedAt < 0 ? "" : " [time budget " + budgetSeconds + "s: ran " + stoppedAt + " of " + iterations + " inputs]";
        if (stoppedAt >= 0) System.out.println("[vanilla-diff] " + vc.getName() + " " + key + budget);
        if (diffs > 0) {
            String d = diffs + " of " + lowered + (paper > 0 ? " (+" + paper + " allowed Paper-shaped)" : "") + budget;
            return new MemberResult(key, Status.DIFF, calls, skipped, d, examples, stoppedAt, iterations);
        }
        if (paper > 0) return new MemberResult(key, Status.PAPER, calls, skipped, paper + " of " + lowered + " allowed Paper-shaped" + budget, paperExamples, stoppedAt, iterations);
        if (lowered == 0) {
            String why = unlowerable != null ? "result " + unlowerable : "no input accepted by both sides (" + skipped + " skipped)";
            return new MemberResult(key, Status.UNPROVED, calls, skipped, why, List.of(), stoppedAt, iterations);
        }
        // 모든 입력에서 양쪽이 같은 예외를 던졌다면 같음이지만 값은 본 적이 없다 — 보고서에 따로 드러낸다
        String note = threw == lowered ? "all inputs threw on both sides (" + lastOutcome + ")" + budget : budget.isEmpty() ? null : budget.strip();
        return new MemberResult(key, Status.SAME, calls, skipped, note, List.of(), stoppedAt, iterations);
    }


    /** 경계값 고정 훑기 입력 수. */
    static final int SWEEP = 16;

    /** 리플렉션 실패(접근 불가 등)는 양쪽이 같아도 증명이 아니다 — 늘 다름으로 센다. */
    private static boolean isHarness(Object outcome) {
        return outcome instanceof String s && s.startsWith("harness ");
    }

    private static int pick(SplittableRandom r, double edgeP) {
        return r.nextDouble() < edgeP ? r.nextInt(1 << 16) : -1;
    }

    private record Unlowerable(String what) {}

    /** 한쪽 호출: [결과 또는 예외, 부른 뒤 받는 쪽, 부른 뒤 인자들]. 올리기 실패는 RaiseFailed. */
    private List<Object> call(Executable e, Object recvSeed, Object[] argSeeds, ClassLoader loader) throws TypeKit.RaiseFailed {
        Object recv = recvSeed == null ? null : TypeKit.raise(recvSeed, loader);
        Object[] args = new Object[argSeeds.length];
        for (int k = 0; k < args.length; k++) args[k] = TypeKit.raise(argSeeds[k], loader);
        if (recvSeed != null && recv == null) throw new TypeKit.RaiseFailed("null receiver", null);
        Object outcome;
        Thread t = Thread.currentThread();
        ClassLoader old = t.getContextClassLoader();
        t.setContextClassLoader(loader);
        try {
            Object res = e instanceof Constructor<?> k ? k.newInstance(args) : ((Method) e).invoke(recv, args);
            boolean isVoid = e instanceof Method m && m.getReturnType() == void.class;
            outcome = isVoid ? "void" : lowerOr(res);
        } catch (InvocationTargetException ex) {
            // 메모리 모자람은 입력의 관측값이 아니라 실행 환경(힙 크기)이다 — 그 입력은 건너뛴다
            if (ex.getCause() instanceof OutOfMemoryError) throw new TypeKit.RaiseFailed("out of memory", ex.getCause());
            outcome = "throws " + ex.getCause().getClass().getName();
        } catch (ReflectiveOperationException ex) {
            outcome = "harness " + ex;
        } finally {
            t.setContextClassLoader(old);
        }
        List<Object> out = new ArrayList<>(args.length + 2);
        out.add(outcome);
        if (recv != null) out.add(lowerQuiet(recv));
        for (Object a : args) out.add(lowerQuiet(a));
        return out;
    }

    private static Object lowerOr(Object res) {
        try {
            return TypeKit.lower(res);
        } catch (TypeKit.Unsupported u) {
            return new Unlowerable(u.getMessage());
        } catch (RuntimeException ex) {
            return "lower threw " + ex.getClass().getName();
        }
    }

    /** 상태 비교용 — 내리지 못하는 인자(RandomSource 등)는 자리만 둔다. */
    private static Object lowerQuiet(Object v) {
        try {
            return TypeKit.lower(v);
        } catch (TypeKit.Unsupported | RuntimeException u) {
            return "?";
        }
    }

    private static String describe(Object recv, Object[] args) {
        String a = Arrays.deepToString(args);
        return recv == null ? a : recv + "." + a;
    }

    private static String clip(Object o) {
        String s = String.valueOf(o);
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }

    private interface Body {
        Object get() throws Exception;
    }

    private static Object withLoader(ClassLoader cl, Body b) throws Exception {
        Thread t = Thread.currentThread();
        ClassLoader old = t.getContextClassLoader();
        t.setContextClassLoader(cl);
        try {
            return b.get();
        } finally {
            t.setContextClassLoader(old);
        }
    }
}
