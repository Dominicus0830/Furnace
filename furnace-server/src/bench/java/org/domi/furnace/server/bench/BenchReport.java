// 벤치 결과를 기계가 읽는 JSON 으로 남기는 도우미(O15 T-O15-03).
// 파일: <furnace.bench.outDir 또는 build/bench>/<벤치>[-<변형>].json — 한 실행에 하나(같은 이름이면 덮어쓴다).
// 공통 칸: commit, bench, variant, scene, seed, jvm{...}. 나머지는 벤치가 put 으로 넣는다(숫자·문자열·불리언만, 단위는 키 끝: _ms, _bytes, _ns).
// 시간 값은 절대 임계값으로 비교하지 않는다(docs/ci.md·docs/testing/bench.md). 해시·틱당 할당 같은 결정적 값만 게이트 후보다.
package org.domi.furnace.server.bench;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BenchReport {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();

    private final String bench;
    private final Map<String, Object> top = new LinkedHashMap<>();
    private final Map<String, Object> values = new LinkedHashMap<>();
    private String variant = "";

    private BenchReport(String bench) {
        this.bench = bench;
    }

    /** 벤치 이름(보통 클래스 단순 이름). */
    public static BenchReport of(String bench) {
        return new BenchReport(bench);
    }

    /** 같은 벤치가 설정을 바꿔 여러 번 돌 때 파일 이름에 붙는 꼬리(예: "dims12"). */
    public BenchReport variant(String variant) {
        this.variant = variant == null ? "" : variant;
        return this;
    }

    public BenchReport scene(String scene) {
        top.put("scene", scene);
        return this;
    }

    public BenchReport seed(long seed) {
        top.put("seed", seed);
        return this;
    }

    /** 숫자·문자열·불리언 한 칸. */
    public BenchReport put(String key, Object value) {
        values.put(key, value);
        return this;
    }

    /** 틱(또는 반복) 시간 표본 ns 배열의 [from, to) 구간 → mean/p50/p95/max(ms). 배열은 정렬하지 않는다(사본을 쓴다). */
    public BenchReport timesNs(String prefix, long[] samples, int from, int to) {
        int n = to - from;
        if (n <= 0) return this;
        long[] s = Arrays.copyOfRange(samples, from, to);
        Arrays.sort(s);
        double sum = 0;
        for (long v : s) sum += v;
        put(prefix + "n", n);
        put(prefix + "mean_ms", sum / n / 1e6);
        put(prefix + "p50_ms", s[n / 2] / 1e6);
        put(prefix + "p95_ms", s[Math.min(n - 1, n * 95 / 100)] / 1e6);
        put(prefix + "max_ms", s[n - 1] / 1e6);
        return this;
    }

    /** 부른 스레드가 지금까지 할당한 바이트(그 스레드의 차분을 잴 때 쓴다). 지원하지 않는 JVM 이면 -1. */
    public static long threadAllocatedBytes() {
        if (ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean t && t.isThreadAllocatedMemorySupported()) {
            return t.getThreadAllocatedBytes(Thread.currentThread().threadId());
        }
        return -1L;
    }

    /** JSON 문자열(시험 가능하게 따로 둔다). */
    public String toJson() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("commit", System.getProperty("furnace.bench.commit", "unknown"));
        out.put("bench", bench);
        if (!variant.isEmpty()) out.put("variant", variant);
        out.putAll(top);
        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("version", System.getProperty("java.version"));
        jvm.put("vm", System.getProperty("java.vm.name"));
        jvm.put("cpus", Runtime.getRuntime().availableProcessors());
        jvm.put("maxHeapBytes", Runtime.getRuntime().maxMemory());
        jvm.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        jvm.put("args", ManagementFactory.getRuntimeMXBean().getInputArguments().stream().filter(a -> !a.startsWith("-Djavax.") && !a.startsWith("-Dhttp") && !a.startsWith("-Djdk.http") && !a.startsWith("-Dorg.gradle.") && !a.startsWith("-javaagent:")).toList());
        out.put("jvm", jvm);
        // NaN·무한대는 JSON 에 쓸 수 없다 → null (파서가 거부하지 않게)
        values.forEach((k, v) -> out.put(k, v instanceof Double d && (d.isNaN() || d.isInfinite()) ? null : v instanceof Float f && (f.isNaN() || f.isInfinite()) ? null : v));
        return GSON.toJson(out);
    }

    /** build/bench/<벤치>[-<변형>].json 에 쓰고 경로를 돌려준다. 쓰기 실패는 벤치를 죽이지 않는다(경고만). */
    public Path write() {
        String dir = System.getProperty("furnace.bench.outDir", "build/bench");
        Path f = Path.of(dir, bench + (variant.isEmpty() ? "" : "-" + variant) + ".json");
        try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, toJson() + "\n", StandardCharsets.UTF_8);
            System.out.println("BENCHJSON " + f);
        } catch (IOException e) {
            System.out.println("BENCHJSON 쓰기 실패: " + f + " — " + e);
        }
        return f;
    }
}
