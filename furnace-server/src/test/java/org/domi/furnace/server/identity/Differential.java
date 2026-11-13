// 차분 시험 도우미: 같은 입력을 옛 구현(오라클)과 새 구현에 넣어 결과가 같은지 본다(O15 보고서 3.2, T-O15-02).
//
//   Differential.of("palette-load", r -> randomPalette(r))
//       .seed(0x10ADL).iterations(3000)
//       .describe(Input::summary)
//       .check(LegacyPalette::load, PaletteStorage::loadNew);
//
// - 입력: 반복마다 (시드, 반복 번호)에서 따로 만든 SplittableRandom 으로 생성한다. 그래서 반복 k 하나만 다시 돌릴 수 있다.
//   생성기는 그 난수만 써야 한다(결정적). 옛·새 구현에 각각 새로 만든 입력을 주므로 구현이 입력을 고쳐도 서로 섞이지 않는다.
// - 결과: 값이 같거나(배열은 내용 비교) 둘 다 같은 종류의 예외를 던지면 같다. 비교 방법은 compare(...) 로 바꿀 수 있다.
// - 실패 메시지: 이름·시드·반복 번호·입력 요약과 재현 명령(-Dfurnace.identity.seed=… -Dfurnace.identity.iter=…).
// - 반복 수: 시험이 정한 기본값 × -Dfurnace.identity.scale(기본 1, 야간 100 권장).
//   -Dfurnace.identity.randomSeeds=N 이면 고정 시드 뒤에 무작위 시드 N 개를 더 돌리고 시드를 표준 출력에 남긴다(야간용 —
//   실패한 시드는 고정 시드 시험에 추가한다). 평소 CI 는 고정 시드만이라 재시도에 가려지는 확률 실패가 아니다.
// - 모든 반복에서 양쪽이 같은 예외로 끝나면 실패한다(생성기·오라클 고장으로 아무것도 비교하지 않는 빈 시험).
//   의도한 경우만 allowAllExceptions().
// - 변이 점검 assertDetects(oracle, mutant): 일부러 틀린 구현을 넣어도 이 차분이 잡는지 확인한다(항상 통과하는 빈 시험 방지).
package org.domi.furnace.server.identity;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.function.Function;

public final class Differential<I, O> {

    /** 결과 비교. 다르면 사람이 읽을 설명을, 같으면 null 을 돌려준다. */
    @FunctionalInterface
    public interface Comparison<O> {
        String difference(O expected, O actual);
    }

    /** 예외를 던질 수 있는 구현. */
    @FunctionalInterface
    public interface Impl<I, O> {
        O apply(I input) throws Exception;
    }

    private final String name;
    private final Function<SplittableRandom, I> generator;
    private long seed;
    private int iterations = 1000;
    private Function<? super I, String> describe = Differential::shortString;
    private Comparison<? super O> comparison = Differential::deepDifference;
    private boolean exceptionsAreResults = true;
    private boolean allowAllExceptions;
    private boolean honorProperties = true;

    private Differential(String name, Function<SplittableRandom, I> generator) {
        this.name = Objects.requireNonNull(name);
        this.generator = Objects.requireNonNull(generator);
        this.seed = name.hashCode() * 0x9E3779B97F4A7C15L;
    }

    public static <I, O> Differential<I, O> of(String name, Function<SplittableRandom, I> generator) {
        return new Differential<>(name, generator);
    }

    public Differential<I, O> seed(long seed) { this.seed = seed; return this; }

    /** 기본 반복 수(-Dfurnace.identity.scale 로 곱해진다). */
    public Differential<I, O> iterations(int n) {
        if (n <= 0) throw new IllegalArgumentException("iterations " + n);
        this.iterations = n;
        return this;
    }

    public Differential<I, O> describe(Function<? super I, String> describe) { this.describe = describe; return this; }

    public Differential<I, O> compare(Comparison<? super O> comparison) { this.comparison = comparison; return this; }

    /** 기본은 예외도 결과로 비교한다. false 면 어느 쪽이든 예외가 나면 그대로 실패한다. */
    public Differential<I, O> exceptionsAreResults(boolean v) { this.exceptionsAreResults = v; return this; }

    /** 모든 반복이 양쪽 같은 예외로 끝나도 통과시킨다(예외 동작만 보는 차분일 때). */
    public Differential<I, O> allowAllExceptions() { this.allowAllExceptions = true; return this; }

    /** furnace.identity.* 속성(scale·seed·iter·randomSeeds)을 무시한다 — 장치 자기 시험이 야간·재현 속성에 흔들리지 않게. */
    Differential<I, O> ignoreProperties() { this.honorProperties = false; return this; }

    /** 반복 k 의 입력 난수. (시드, k) 가 같으면 같은 난수다. */
    public static SplittableRandom randomFor(long seed, long iteration) {
        return new SplittableRandom(new SplittableRandom(seed ^ (iteration * 0xBF58476D1CE4E5B9L)).nextLong() ^ iteration);
    }

    public int effectiveIterations() {
        int scale = honorProperties ? Math.max(1, Integer.getInteger("furnace.identity.scale", 1)) : 1;
        long n = (long) iterations * scale;
        return (int) Math.min(Integer.MAX_VALUE, n);
    }

    /** 옛 구현과 새 구현의 결과가 모든 반복에서 같아야 한다. 돌린 반복 수를 돌려준다. */
    public int check(Impl<? super I, ? extends O> oracle, Impl<? super I, ? extends O> candidate) {
        Long onlySeed = honorProperties ? Long.getLong("furnace.identity.seed") : null;
        Integer onlyIter = honorProperties ? Integer.getInteger("furnace.identity.iter") : null;
        if (onlySeed != null || onlyIter != null) {
            // 재현 모드: 실패 메시지가 알려 준 시드·반복 하나만
            long s = onlySeed != null ? onlySeed : seed;
            if (onlyIter != null) {
                AssertionError diff = runOne(s, onlyIter, oracle, candidate);
                if (diff != null) throw diff;
                return 1;
            }
            return runAll(s, oracle, candidate);
        }
        int ran = runAll(seed, oracle, candidate);
        int extra = honorProperties ? Integer.getInteger("furnace.identity.randomSeeds", 0) : 0;
        for (int i = 0; i < extra; i++) {
            long s = System.nanoTime() ^ (0x632BE59BD9B4E019L * (i + 1));
            System.out.printf("IDENTITY %s randomSeed=%d (0x%x)%n", name, s, s);
            ran += runAll(s, oracle, candidate);
        }
        return ran;
    }

    /**
     * 변이 점검: mutant(일부러 틀린 구현)를 새 구현 자리에 넣으면 이 차분이 어느 반복에서든 차이를 찾아야 한다.
     * 못 찾으면 실패한다 — 입력 생성기가 그 틀림을 건드리지 못한다는 뜻이다. 차이를 찾은 첫 반복 번호를 돌려준다.
     */
    public int assertDetects(Impl<? super I, ? extends O> oracle, Impl<? super I, ? extends O> mutant) {
        int n = effectiveIterations();
        for (int k = 0; k < n; k++) {
            if (runOne(seed, k, oracle, mutant) != null) return k;
        }
        throw new AssertionError("[" + name + "] 변이를 " + n + "번 반복 안에 못 잡았다(seed=0x" + Long.toHexString(seed)
                + ") — 입력 생성기가 이 틀린 경우를 만들지 못한다. 생성기를 넓히거나 반복을 늘린다.");
    }

    private int runAll(long s, Impl<? super I, ? extends O> oracle, Impl<? super I, ? extends O> candidate) {
        int n = effectiveIterations();
        bothThrew = 0;
        for (int k = 0; k < n; k++) {
            AssertionError diff = runOne(s, k, oracle, candidate);
            if (diff != null) throw diff;
        }
        if (bothThrew == n && !allowAllExceptions) {
            throw new AssertionError("[" + name + "] " + n + "번 반복 모두 옛·새 구현이 예외로 끝났다(seed=0x" + Long.toHexString(s)
                    + ") — 아무 결과도 비교하지 않은 빈 시험이다. 생성기·오라클을 확인하고, 예외만 보는 차분이면 allowAllExceptions().");
        }
        return n;
    }

    private int bothThrew;

    /** 반복 하나. 같으면 null, 다르면 던질 실패. */
    private AssertionError runOne(long s, int k, Impl<? super I, ? extends O> oracle, Impl<? super I, ? extends O> candidate) {
        I forOracle = generator.apply(randomFor(s, k));
        I forCandidate = generator.apply(randomFor(s, k));
        Outcome<O> expected = call(oracle, forOracle);
        Outcome<O> actual = call(candidate, forCandidate);
        String diff = differ(expected, actual);
        if (diff == null) {
            if (expected.error != null) bothThrew++;
            return null;
        }
        String input;
        try {
            input = describe.apply(generator.apply(randomFor(s, k)));
        } catch (RuntimeException e) {
            input = "(입력 요약 실패: " + e + ")";
        }
        AssertionError err = new AssertionError("[" + name + "] 옛 구현과 결과가 다르다 seed=0x" + Long.toHexString(s) + " iter=" + k
                + "\n  입력: " + input + "\n  차이: " + diff
                + "\n  재현: -Dfurnace.identity.seed=" + s + " -Dfurnace.identity.iter=" + k);
        if (expected.error != null) err.addSuppressed(expected.error);
        if (actual.error != null) err.addSuppressed(actual.error);
        return err;
    }

    private record Outcome<O>(O value, Throwable error) {}

    @SuppressWarnings("unchecked")
    private Outcome<O> call(Impl<? super I, ? extends O> impl, I input) {
        try {
            return new Outcome<>(impl.apply(input), null);
        } catch (Exception | AssertionError e) {
            // AssertionError: Owned 가드·assert 도 결과로 본다
            if (!exceptionsAreResults) throw new AssertionError("[" + name + "] 구현이 예외를 던졌다", e);
            return new Outcome<>(null, e);
        }
    }

    private String differ(Outcome<O> expected, Outcome<O> actual) {
        if (expected.error != null || actual.error != null) {
            if (expected.error != null && actual.error != null) {
                return expected.error.getClass() == actual.error.getClass() ? null
                        : "예외 종류: 옛 " + expected.error.getClass().getName() + " / 새 " + actual.error.getClass().getName();
            }
            return expected.error != null ? "옛 구현만 예외: " + expected.error + " / 새 결과 " + shortString(actual.value)
                    : "새 구현만 예외: " + actual.error + " / 옛 결과 " + shortString(expected.value);
        }
        return comparison.difference(expected.value, actual.value);
    }

    /** 기본 비교: Objects.deepEquals(배열은 내용, 중첩 배열 포함). 다르면 배열은 첫 차이 위치를 알려 준다. */
    public static String deepDifference(Object expected, Object actual) {
        if (Objects.deepEquals(expected, actual)) return null;
        if (expected != null && actual != null && expected.getClass().isArray() && actual.getClass().isArray()) {
            int la = Array.getLength(expected), lb = Array.getLength(actual);
            for (int i = 0; i < Math.min(la, lb); i++) {
                Object a = Array.get(expected, i), b = Array.get(actual, i);
                if (!Objects.deepEquals(a, b)) {
                    return "배열 [" + i + "] 옛=" + shortString(a) + " 새=" + shortString(b) + (la != lb ? " (길이 " + la + "/" + lb + ")" : "");
                }
            }
            return "배열 길이 옛=" + la + " 새=" + lb;
        }
        return "옛=" + shortString(expected) + " 새=" + shortString(actual);
    }

    public static String shortString(Object o) {
        String s;
        if (o == null) s = "null";
        else if (o instanceof int[] a) s = Arrays.toString(a);
        else if (o instanceof long[] a) s = Arrays.toString(a);
        else if (o instanceof byte[] a) s = Arrays.toString(a);
        else if (o instanceof double[] a) s = Arrays.toString(a);
        else if (o instanceof boolean[] a) s = Arrays.toString(a);
        else if (o instanceof short[] a) s = Arrays.toString(a);
        else if (o instanceof char[] a) s = Arrays.toString(a);
        else if (o instanceof float[] a) s = Arrays.toString(a);
        else if (o instanceof Object[] a) s = Arrays.deepToString(a);
        else s = String.valueOf(o);
        return s.length() > 300 ? s.substring(0, 300) + "…(" + s.length() + "자)" : s;
    }
}
