// 호환 기준 장면의 벤치 모드(T-075): 장면마다 FULL 크기로 돌려 걸음(묶음) 시간·할당을 재고 해시를 JSON 에 남긴다. 수동 실행(O15 결정 — 야간 벤치 미승인).
//   ./gradlew -PskipOracle=true :furnace-compat-paper:compatSceneBench                                              # 전부
//   ./gradlew -PskipOracle=true :furnace-compat-paper:compatSceneBench -Dfurnace.bench.scenes=compat-held-item,compat-block-reads
// 결과: furnace-compat-paper/build/bench/CompatScene-<장면>.json (step_mean_ms·step_p50/p95/max_ms·total_ms·allocBytesPerStep·hash·lines)
//       + CompatScene-<장면>.lines.txt (줄 전부 — 옛·새 커밋 출력을 diff).
// 서버 쪽 SceneLibraryBench 와 같은 방식이다. 서버를 띄우는 장면(freshJvm)은 장면마다 새 JVM(-Dfurnace.test.seeded=true)에서 돈다.
// 절대 시간으로 CI 를 막지 않는다 — 같은 hash(= 같은 의미)일 때 옛·새 커밋을 번갈아 재서 비교한다.
package org.domi.furnace.compat.paper;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.domi.furnace.server.bench.BenchReport;
import org.domi.furnace.server.identity.SceneHash;
import org.domi.furnace.server.scene.LibraryScene;
import org.domi.furnace.server.scene.Probe;
import org.domi.furnace.server.scene.Size;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CompatSceneBench {

    static Stream<LibraryScene> scenes() {
        String only = System.getProperty("furnace.bench.scenes");
        java.util.Set<String> want = only == null || only.isBlank() ? null : new java.util.HashSet<>(Arrays.asList(only.split(",")));
        return CompatSceneLibrary.all().stream().filter(s -> want == null || want.contains(s.name()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenes")
    void bench(LibraryScene scene) throws Exception {
        if (scene.freshJvm()) runInChildJvm(scene);
        else Child.run(scene);
    }

    private static void runInChildJvm(LibraryScene scene) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        for (String a : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (a.startsWith("-javaagent:") || a.startsWith("-agentlib:") || a.startsWith("-Dorg.gradle.") || a.startsWith("-Dfurnace.test.seeded")) continue;
            cmd.add(a);
        }
        cmd.addAll(List.of("-Dfurnace.test.seeded=true", "-Dstdout.encoding=UTF-8", "-cp", System.getProperty("java.class.path"), Child.class.getName(), scene.name()));
        Process p = new ProcessBuilder(cmd).inheritIO().start();
        if (!p.waitFor(30, java.util.concurrent.TimeUnit.MINUTES)) {
            p.destroyForcibly();
            throw new AssertionError("장면 JVM 이 30분 안에 안 끝났다: " + scene.name());
        }
        if (p.exitValue() != 0) throw new AssertionError("장면 JVM 실패(exit " + p.exitValue() + "): " + scene.name());
    }

    /** 장면 하나를 이 JVM 에서 FULL 크기로 돌려 재고 JSON 을 쓴다. 새 JVM 쪽 입구이기도 하다. */
    public static final class Child {
        public static void main(String[] args) throws Exception {
            LibraryScene scene = CompatSceneLibrary.all().stream().filter(s -> s.name().equals(args[0])).findFirst().orElseThrow();
            run(scene);
            System.exit(0);   // 장면이 남긴 비데몬 스레드를 기다리지 않는다
        }

        static void run(LibraryScene scene) throws Exception {
            Timer timer = new Timer();
            long t0 = System.nanoTime();
            List<String> lines = SceneHash.record(rec -> scene.play(Size.FULL, rec, timer));
            double totalMs = (System.nanoTime() - t0) / 1e6;
            String total = lines.get(lines.size() - 1);   // "TOTAL <16자리> lines=N"
            int n = timer.count;
            int skip = n >= 8 ? n / 4 : 0;   // 앞 구간(JIT 예열)은 통계에서 뺀다
            BenchReport rep = BenchReport.of("CompatScene").variant(scene.name()).scene(scene.name()).put("unit", scene.unit())
                    .put("size", "FULL").put("steps", n).put("skip", skip).put("total_ms", totalMs)
                    .put("hash", total.split(" ")[1]).put("lines", lines.size() - 1)
                    .put("freshJvm", scene.freshJvm()).put("seeded", Boolean.getBoolean("furnace.test.seeded"));
            if (n > 0) rep.timesNs("step_", Arrays.copyOf(timer.nanos, n), skip, n);
            if (n > skip && timer.alloc[skip] >= 0) {
                long sum = 0;
                for (int i = skip; i < n; i++) sum += timer.alloc[i];
                rep.put("allocBytesPerStep", sum / (double) (n - skip));   // 장면 스레드 할당만(결정적 값 아님 — docs/testing/bench.md)
            }
            Path out = rep.write();
            Files.writeString(out.resolveSibling("CompatScene-" + scene.name() + ".lines.txt"), String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
            System.out.printf("COMPATSCENEBENCH %s %s steps=%d mean=%.3fms p95=%.3fms total=%.0fms %s%n", scene.name(), scene.unit(), n,
                    n > skip ? Arrays.stream(timer.nanos, skip, n).average().orElse(0) / 1e6 : 0.0,
                    n > skip ? p95(timer.nanos, skip, n) / 1e6 : 0.0, totalMs, total);
        }

        private static long p95(long[] a, int from, int to) {
            long[] s = Arrays.copyOfRange(a, from, to);
            Arrays.sort(s);
            return s[Math.min(s.length - 1, s.length * 95 / 100)];
        }
    }

    /** 걸음마다 nanoTime·장면 스레드 할당 바이트를 잰다. */
    static final class Timer implements Probe {
        long[] nanos = new long[1024], alloc = new long[1024];
        int count;
        private long t0, a0;

        @Override
        public void before(int tick) {
            a0 = BenchReport.threadAllocatedBytes();
            t0 = System.nanoTime();
        }

        @Override
        public void after(int tick) {
            long dt = System.nanoTime() - t0, a = BenchReport.threadAllocatedBytes() - a0;
            if (count == nanos.length) { nanos = Arrays.copyOf(nanos, count * 2); alloc = Arrays.copyOf(alloc, count * 2); }
            nanos[count] = dt;
            alloc[count++] = a < 0 ? -1 : a;
        }
    }
}
