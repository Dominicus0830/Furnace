// 장면 해시: 정해진 시작 상태와 입력으로 장면을 돌리며 "이름 해시" 줄을 쌓고, 금 파일(Golden)과 비교한다(O15 보고서 3.3).
//
//   SceneHash.assertGolden("worldgen-noise-quick", rec -> {
//       for (...) rec.line("chunk overworld 12345 0 0", hashColumn(g));
//   });
//
// 비교 전에 **결정성 자기 점검**을 한다: 같은 빌드·같은 JVM 에서 장면을 두 번(-Dfurnace.identity.determinismRuns) 돌려
// 줄이 하나라도 다르면 "결정적이지 않음"으로 실패한다 — 금 파일과의 차이가 최적화 탓인지 장면의 비결정(정체 해시 순서·
// 시각·nanoTime 씨앗 난수·여러 region 스레드 순서) 탓인지 섞이지 않게. 장면은 매번 새 상태(새 월드·새 소스)로 시작해야 한다.
//
// **같은 JVM 두 번이 같다는 보장이 없는 장면**(엔티티가 든 장면): 시험 레벨의 엔티티 id 카운터(ChunkLevel.FALLBACK_IDS)와
// 정적 난수(Sensor·ShufflingList)가 JVM 전역이라, 같은 JVM 의 두 번째 실행·앞서 돈 다른 시험에 따라 UUID·난수가 달라진다
// (PR #272 검토 R2, docs/feature-parity/context-notes/2026-11-12-random-seeds-seam.md). 그런 장면은 assertGoldenInFreshJvm 으로
// 실행마다 새 JVM(-Dfurnace.test.seeded=true)에서 돌린다. 같은 JVM 모드는 정적 상태를 안 건드리는 장면(월드젠·순수 계산)용이다.
//
// 장면 줄은 나중에 금 파일 diff 로 읽히므로 "무엇 어디" 를 앞에, 해시를 뒤에 둔다. 마지막에 TOTAL 줄이 자동으로 붙는다.
package org.domi.furnace.server.identity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SceneHash {

    @FunctionalInterface
    public interface Scene {
        void run(Recorder rec) throws Exception;
    }

    /** 장면이 결과를 적는 곳. */
    public static final class Recorder {
        private final List<String> lines = new ArrayList<>();
        private final IdentityHash total = new IdentityHash();

        /** "라벨 해시16자리". */
        public Recorder line(String label, long hash) {
            return text(label + " " + IdentityHash.hex(hash));
        }

        /** 빈 해시(아무것도 섞지 않음)는 장면이 아무것도 안 본 것이라 실패시킨다. */
        public Recorder line(String label, IdentityHash h) {
            if (h.count() == 0) throw new AssertionError("장면 줄 '" + label + "' 의 해시에 섞인 값이 없다");
            return line(label, h.value());
        }

        /** 해시가 아닌 값(개수·짧은 상태)을 그대로 적는다. */
        public Recorder text(String line) {
            if (line.isBlank() || line.startsWith("#") || line.startsWith("TOTAL ") || line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0) {
                throw new IllegalArgumentException("장면 줄로 쓸 수 없다: '" + line + "'");
            }
            lines.add(line);
            total.add(line);
            return this;
        }

        public int size() { return lines.size(); }

        List<String> finish() {
            List<String> out = new ArrayList<>(lines);
            out.add("TOTAL " + total.hex() + " lines=" + lines.size());
            return Collections.unmodifiableList(out);
        }
    }

    private SceneHash() {}

    /** 장면을 한 번 돌려 줄 목록(끝에 TOTAL)을 돌려준다. */
    public static List<String> record(Scene scene) {
        Recorder rec = new Recorder();
        try {
            scene.run(rec);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (rec.size() == 0) throw new AssertionError("장면이 줄을 하나도 적지 않았다");
        return rec.finish();
    }

    public static int determinismRuns() {
        return Math.max(2, Integer.getInteger("furnace.identity.determinismRuns", 2));
    }

    /** 결정성 자기 점검: 여러 번 돌려 모두 같아야 한다. 첫 결과를 돌려준다. */
    public static List<String> deterministic(String name, Scene scene) {
        return deterministic(name, scene, determinismRuns());
    }

    public static List<String> deterministic(String name, Scene scene, int runs) {
        List<String> first = record(scene);
        for (int run = 2; run <= runs; run++) {
            List<String> again = record(scene);
            if (!first.equals(again)) {
                int i = 0;
                while (i < Math.min(first.size(), again.size()) && first.get(i).equals(again.get(i))) i++;
                throw new AssertionError("[" + name + "] 장면이 결정적이지 않다 — 같은 빌드에서 " + run + "번째 실행이 첫 실행과 다르다(금 파일 비교 전 점검)"
                        + "\n  첫 차이 " + (i + 1) + "번째 줄\n    1회: " + (i < first.size() ? first.get(i) : "(없음)")
                        + "\n    " + run + "회: " + (i < again.size() ? again.get(i) : "(없음)")
                        + "\n  흔한 원인: 정체 해시 순서(HashMap<객체>), ConcurrentHashMap 순회, nanoTime 씨앗 난수, 여러 region 스레드, 이전 실행이 남긴 정적 캐시");
            }
        }
        return first;
    }

    /** 결정성 점검 → 금 파일 비교(같은 JVM 에서 돌린다 — 정적 상태를 안 건드리는 장면용). */
    public static List<String> assertGolden(String name, Scene scene) {
        Golden.checkName(name);
        List<String> lines = deterministic(name, scene);
        Golden.assertMatches(name, lines);
        return lines;
    }

    /**
     * 실행마다 새 JVM 에서 장면을 돌려(-Dfurnace.test.seeded=true, 시험 클래스패스 그대로) 결정성 점검 → 금 파일 비교.
     * scene 은 public 이고 인자 없는 public 생성자가 있어야 한다. 시험 JVM 의 furnace.* 속성은 넘기지 않는다
     * (장면 결과가 바깥 속성에 기대지 않게) — 장면에 필요한 속성은 jvmArgs 로 적는다.
     */
    public static List<String> assertGoldenInFreshJvm(String name, Class<? extends Scene> scene, String... jvmArgs) {
        Golden.checkName(name);
        List<String> lines = deterministicInFreshJvm(name, scene, jvmArgs);
        Golden.assertMatches(name, lines);
        return lines;
    }

    public static List<String> deterministicInFreshJvm(String name, Class<? extends Scene> scene, String... jvmArgs) {
        List<String> first = recordInFreshJvm(scene, jvmArgs);
        for (int run = 2; run <= determinismRuns(); run++) {
            List<String> again = recordInFreshJvm(scene, jvmArgs);
            if (!first.equals(again)) {
                int i = 0;
                while (i < Math.min(first.size(), again.size()) && first.get(i).equals(again.get(i))) i++;
                throw new AssertionError("[" + name + "] 장면이 결정적이지 않다 — 새 JVM " + run + "번째 실행이 첫 실행과 다르다"
                        + "\n  첫 차이 " + (i + 1) + "번째 줄\n    1회: " + (i < first.size() ? first.get(i) : "(없음)")
                        + "\n    " + run + "회: " + (i < again.size() ? again.get(i) : "(없음)")
                        + "\n  새 JVM 이라도 남는 원인: 이음매 밖 난수(ThreadLocalRandom·Math.random·UUID.randomUUID·new Random()), 시각, 여러 스레드 순서");
            }
        }
        return first;
    }

    /** 새 JVM 하나에서 장면을 한 번 돌린 줄(끝에 TOTAL). */
    public static List<String> recordInFreshJvm(Class<? extends Scene> scene, String... jvmArgs) {
        if (!java.lang.reflect.Modifier.isPublic(scene.getModifiers())) throw new IllegalArgumentException("장면 클래스는 public: " + scene.getName());
        java.nio.file.Path out = null;
        try {
            out = java.nio.file.Files.createTempFile("scene-", ".txt");
            String javaBin = java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java").toString();
            List<String> cmd = new ArrayList<>(List.of(javaBin, "-ea", "-Xmx1g", "-Dfurnace.test.seeded=true",
                    "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8"));
            cmd.addAll(List.of(jvmArgs));
            cmd.addAll(List.of("-cp", System.getProperty("java.class.path"), ForkMain.class.getName(), scene.getName(), out.toString()));
            ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
            Process p = pb.start();
            // 출력은 실패 메시지용으로만 모은다(로그가 섞여도 결과는 파일로 받는다)
            byte[] log = p.getInputStream().readAllBytes();
            if (!p.waitFor(240, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new AssertionError("장면 JVM 이 240초 안에 안 끝났다: " + scene.getName());
            }
            if (p.exitValue() != 0) {
                String text = new String(log, java.nio.charset.StandardCharsets.UTF_8);
                throw new AssertionError("장면 JVM 실패(exit " + p.exitValue() + "): " + scene.getName() + "\n"
                        + (text.length() > 4000 ? text.substring(text.length() - 4000) : text));
            }
            return List.copyOf(java.nio.file.Files.readAllLines(out, java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("중단됨", e);
        } finally {
            if (out != null) try { java.nio.file.Files.deleteIfExists(out); } catch (java.io.IOException ignored) {}
        }
    }

    /** 새 JVM 쪽 입구: 장면 클래스 이름, 결과 파일. */
    public static final class ForkMain {
        public static void main(String[] args) throws Exception {
            Scene scene = Class.forName(args[0]).asSubclass(Scene.class).getDeclaredConstructor().newInstance();
            List<String> lines = record(scene);
            java.nio.file.Files.write(java.nio.file.Path.of(args[1]), lines, java.nio.charset.StandardCharsets.UTF_8);
            System.exit(0);   // 장면이 남긴 비데몬 스레드(스케줄러 등)를 기다리지 않는다
        }
    }
}
