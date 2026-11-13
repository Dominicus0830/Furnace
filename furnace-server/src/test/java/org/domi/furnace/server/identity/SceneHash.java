// 장면 해시: 정해진 시작 상태와 입력으로 장면을 돌리며 "이름 해시" 줄을 쌓고, 금 파일(Golden)과 비교한다(O15 보고서 3.3).
//
//   SceneHash.assertGolden("worldgen-noise-quick", rec -> {
//       for (...) rec.line("chunk overworld 12345 0 0", hashColumn(g));
//   });
//
// **결정성 자기 점검**: 장면을 한 번 돌려 금 파일과 같으면 끝이다(커밋된 금 파일과의 일치가 곧 실행 간 결정성 증거).
// 다르거나 금 파일이 없거나 bless 요청이면, 비교·갱신 전에 장면을 더 돌려(-Dfurnace.identity.determinismRuns, 기본 2회째까지)
// 줄이 하나라도 다르면 "결정적이지 않음"으로 실패한다 — 금 파일과의 차이가 최적화 탓인지 장면의 비결정(정체 해시 순서·
// 시각·이음매 밖 난수·여러 region 스레드 순서) 탓인지 섞이지 않게, 그리고 비결정 장면을 금 파일로 굳히지 않게.
// 장면은 매번 새 상태(새 월드·새 소스)로 시작해야 한다.
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

    /** 결정성 자기 점검(같은 JVM): 여러 번 돌려 모두 같아야 한다. 첫 결과를 돌려준다. */
    public static List<String> deterministic(String name, Scene scene) {
        List<String> first = record(scene);
        checkSame(name, first, () -> record(scene), "같은 JVM");
        return first;
    }

    /** 금 파일 비교(같은 JVM 에서 돌린다 — 정적 상태를 안 건드리는 장면용). 다를 때·없을 때·bless 때는 결정성 점검을 먼저. */
    public static List<String> assertGolden(String name, Scene scene) {
        Golden.checkName(name);
        List<String> lines = record(scene);
        if (!matchesGoldenWithoutBless(name, lines)) {
            // 금 파일과 다르거나(·없거나) bless 요청: 두 번째 실행으로 "비결정" 과 "결과가 바뀜" 을 가른 뒤 비교·갱신한다
            checkSame(name, lines, () -> record(scene), "같은 JVM");
            Golden.assertMatches(name, lines);
        }
        return lines;
    }

    /** 금 파일과 같고 bless 요청이 아니면 true — 금 파일과의 일치가 곧 실행 간 결정성 증거라 두 번째 실행을 생략한다. */
    static boolean matchesGoldenWithoutBless(String name, List<String> lines) {
        return !Golden.blessRequested(name) && lines.equals(Golden.expected(name));
    }

    private static void checkSame(String name, List<String> first, java.util.function.Supplier<List<String>> again, String where) {
        for (int run = 2; run <= determinismRuns(); run++) {
            List<String> next = again.get();
            if (!first.equals(next)) {
                int i = 0;
                while (i < Math.min(first.size(), next.size()) && first.get(i).equals(next.get(i))) i++;
                throw new AssertionError("[" + name + "] 장면이 결정적이지 않다 — " + where + " " + run + "번째 실행이 첫 실행과 다르다(금 파일 비교 전 점검)"
                        + "\n  첫 차이 " + (i + 1) + "번째 줄\n    1회: " + (i < first.size() ? first.get(i) : "(없음)")
                        + "\n    " + run + "회: " + (i < next.size() ? next.get(i) : "(없음)")
                        + "\n  흔한 원인: 정체 해시 순서(HashMap<객체>), ConcurrentHashMap 순회, 이음매 밖 난수(ThreadLocalRandom·Math.random·"
                        + "UUID.randomUUID·new Random()), 시각, 여러 region 스레드, 같은 JVM 이면 이전 실행이 남긴 정적 상태(엔티티 id 카운터·정적 캐시)");
            }
        }
    }

    /**
     * 실행마다 새 JVM 에서 장면을 돌려(-Dfurnace.test.seeded=true, 시험 클래스패스 그대로) 결정성 점검 → 금 파일 비교.
     * scene 은 public 이고 인자 없는 public 생성자가 있어야 한다. 시험 JVM 의 furnace.* 속성은 넘기지 않는다
     * (장면 결과가 바깥 속성에 기대지 않게) — 장면에 필요한 속성은 jvmArgs 로 적는다.
     */
    public static List<String> assertGoldenInFreshJvm(String name, Class<? extends Scene> scene, String... jvmArgs) {
        Golden.checkName(name);
        List<String> lines = recordInFreshJvm(scene, jvmArgs);
        if (!matchesGoldenWithoutBless(name, lines)) {
            checkSame(name, lines, () -> recordInFreshJvm(scene, jvmArgs), "새 JVM");
            Golden.assertMatches(name, lines);
        }
        return lines;
    }

    /** 결정성 자기 점검(새 JVM 여러 번). 첫 결과를 돌려준다. */
    public static List<String> deterministicInFreshJvm(String name, Class<? extends Scene> scene, String... jvmArgs) {
        List<String> first = recordInFreshJvm(scene, jvmArgs);
        checkSame(name, first, () -> recordInFreshJvm(scene, jvmArgs), "새 JVM");
        return first;
    }

    /** 새 JVM 장면 하나의 시간 제한(초). 넘으면 자식 JVM 을 죽이고 실패한다. */
    static long FRESH_JVM_TIMEOUT_SECONDS = 240;

    /** 새 JVM 하나에서 장면을 한 번 돌린 줄(끝에 TOTAL). */
    public static List<String> recordInFreshJvm(Class<? extends Scene> scene, String... jvmArgs) {
        if (!java.lang.reflect.Modifier.isPublic(scene.getModifiers())) throw new IllegalArgumentException("장면 클래스는 public: " + scene.getName());
        java.nio.file.Path out = null, logFile = null;
        try {
            out = java.nio.file.Files.createTempFile("scene-", ".txt");
            logFile = java.nio.file.Files.createTempFile("scene-", ".log");
            String javaBin = java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java").toString();
            List<String> cmd = new ArrayList<>(List.of(javaBin, "-ea", "-Xmx1g", "-Dfurnace.test.seeded=true",
                    "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8"));
            cmd.addAll(List.of(jvmArgs));
            cmd.addAll(List.of("-cp", System.getProperty("java.class.path"), ForkMain.class.getName(), scene.getName(), out.toString()));
            // 출력은 파일로 보낸다 — 파이프를 읽느라 막히면 아래 시간 제한이 듣지 않는다. 로그는 실패 메시지용, 결과는 out 파일.
            ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(logFile.toFile());
            Process p = pb.start();
            if (!p.waitFor(FRESH_JVM_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly().waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
                throw new AssertionError("장면 JVM 이 " + FRESH_JVM_TIMEOUT_SECONDS + "초 안에 안 끝났다: " + scene.getName());
            }
            if (p.exitValue() != 0) {
                String text = java.nio.file.Files.readString(logFile, java.nio.charset.StandardCharsets.UTF_8);
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
            for (java.nio.file.Path f : new java.nio.file.Path[]{out, logFile}) {
                if (f != null) try { java.nio.file.Files.deleteIfExists(f); } catch (java.io.IOException ignored) {}
            }
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
