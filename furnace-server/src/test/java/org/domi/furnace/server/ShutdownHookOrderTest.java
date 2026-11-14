// /stop 과 JVM 종료 훅의 순서(LuckPerms H2 종료 NoClassDefFoundError 회귀).
// H2 는 DB 를 열 때 JVM 종료 훅(OnExitDatabaseCloser)을 걸고, DB 를 정상으로 닫으면(LuckPerms onDisable "Closing storage") 그 훅을 뗀다.
// LuckPerms 는 이어서 H2 를 올린 자기 라이브러리 로더를 닫는다. Furnace 가 onDisable 을 JVM 종료 훅 안에서 돌리면 훅 떼기가
// "Shutdown in progress" 로 실패하고, 남은 H2 훅이 닫힌 로더에서 org.h2.api.ErrorCode 를 읽다가 NoClassDefFoundError 를 냈다.
// Paper 는 /stop 때 플러그인 끄기를 JVM 종료 전에 마치므로 훅이 떨어지고 오류가 없다. 별도 JVM 에서 그 흐름을 흉내 낸다.
package org.domi.furnace.server;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShutdownHookOrderTest {

    @TempDir Path dir;

    /** /stop: onDisable 이 JVM 종료 전에 돌아 플러그인이 자기 종료 훅을 뗄 수 있고, 닫힌 로더에서 클래스를 읽는 훅이 돌지 않는다. */
    @Test
    void stopDisablesPluginsBeforeJvmShutdownHooks() throws Exception {
        String out = probe("stop");
        assertTrue(out.contains("UNREGISTERED"), out);
        assertFalse(out.contains("UNREGISTER-FAILED"), out);
        assertFalse(out.contains("HOOK-"), "떼어 낸 플러그인 종료 훅이 돌았다:\n" + out);
        assertTrue(out.contains("CLOSED-PLUGINS"), out);
    }

    /** 고치기 전 흐름(바로 System.exit → 종료 훅 안에서 onDisable) — 이 시험이 그 버그를 잡는다는 대조. */
    @Test
    void exitFirstReproducesTheFailure() throws Exception {
        String out = probe("exit-first");
        assertTrue(out.contains("UNREGISTER-FAILED"), "종료 중 훅 떼기가 성공했다 — 대조가 무너졌다:\n" + out);
    }

    /** /stop 이 도는 중에 JVM 종료(Ctrl+C·kill)가 와도 종료 훅은 남은 단계(저장·로더 닫기)가 끝날 때까지 기다린다. */
    @Test
    void signalDuringStopWaitsForRemainingSteps() throws Exception {
        String out = probe("signal-during-stop");
        assertTrue(out.contains("SLOW-DONE"), out);
        assertTrue(out.contains("CLOSED-PLUGINS"), "JVM 이 종료 단계 도중에 끝났다:\n" + out);
    }

    /** 종료 단계 안에서 System.exit 를 불러도(플러그인 onDisable 등) 서로 기다리며 멈추지 않는다. */
    @Test
    void exitInsideStepDoesNotDeadlock() throws Exception {
        String out = probe("exit-in-step");
        assertTrue(out.contains("EXIT-IN-STEP"), out);
    }

    private String probe(String mode) throws Exception {
        Path jar = lazyJar();
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder pb = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), Probe.class.getName(), mode, jar.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        boolean ended = p.waitFor(60, TimeUnit.SECONDS);
        if (!ended) p.destroyForcibly();
        String out = new String(p.getInputStream().readAllBytes());
        assertTrue(ended, "JVM 이 끝나지 않았다(교착):\n" + out);
        assertEquals(0, p.exitValue(), out);
        return out;
    }

    /** 플러그인 라이브러리 jar 흉내 — 종료 훅이 처음 쓸 때 읽는 클래스 하나. */
    private Path lazyJar() throws Exception {
        Path src = dir.resolve("src/lazy");
        Files.createDirectories(src);
        Files.writeString(src.resolve("ErrorCode.java"), "package lazy; public class ErrorCode { public static final int X = 1; }");
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
        assertEquals(0, jc.run(null, null, null, "-d", out.toString(), src.resolve("ErrorCode.java").toString()));
        Path jar = dir.resolve("lib.jar");
        try (JarOutputStream jo = new JarOutputStream(Files.newOutputStream(jar))) {
            jo.putNextEntry(new JarEntry("lazy/ErrorCode.class"));
            jo.write(Files.readAllBytes(out.resolve("lazy/ErrorCode.class")));
            jo.closeEntry();
        }
        return jar;
    }

    /** 별도 JVM: FurnaceMain 과 같은 배선(종료 훅이 단계를 돌고, /stop 은 runThenExit). */
    public static final class Probe {
        public static void main(String[] args) throws Exception {
            String mode = args[0];
            URLClassLoader lib = new URLClassLoader(new URL[] {Path.of(args[1]).toUri().toURL()}, null);
            // H2 OnExitDatabaseCloser 흉내: 처음 쓸 때 라이브러리 로더에서 클래스를 읽는다
            Thread pluginHook = new Thread(() -> {
                try {
                    Class.forName("lazy.ErrorCode", true, lib);
                    System.out.println("HOOK-LOADED");
                } catch (Throwable t) {
                    System.out.println("HOOK-FAILED " + t);
                }
                System.out.flush();
            }, "plugin-hook");
            Runtime.getRuntime().addShutdownHook(pluginHook);

            ShutdownSequence seq = new ShutdownSequence();
            seq.on(ShutdownSequence.Step.DISABLE_PLUGINS, () -> {
                // onDisable: DB 를 닫으며 훅을 뗀다(H2 OnExitDatabaseCloser.unregister) → 라이브러리 로더를 닫는다(LuckPerms)
                try {
                    Runtime.getRuntime().removeShutdownHook(pluginHook);
                    System.out.println("UNREGISTERED");
                } catch (IllegalStateException e) {
                    System.out.println("UNREGISTER-FAILED " + e.getMessage());
                }
                try {
                    lib.close();
                } catch (Exception ignored) {
                }
                if (mode.equals("exit-in-step")) {
                    System.out.println("EXIT-IN-STEP");
                    System.out.flush();
                    System.exit(0);
                }
            });
            java.util.concurrent.CountDownLatch slowStarted = new java.util.concurrent.CountDownLatch(1);
            seq.on(ShutdownSequence.Step.FLUSH_WORLDS, () -> {
                if (!mode.equals("signal-during-stop")) return;
                slowStarted.countDown();
                try {
                    Thread.sleep(1_500);   // 저장 흉내 — 이 사이에 JVM 종료가 온다
                } catch (InterruptedException ignored) {
                }
                System.out.println("SLOW-DONE");
            });
            seq.on(ShutdownSequence.Step.CLOSE_PLUGINS, () -> {
                System.out.println("CLOSED-PLUGINS");
                System.out.flush();
            });
            Runtime.getRuntime().addShutdownHook(new Thread(seq::run, "furnace-shutdown"));

            switch (mode) {
                case "exit-first" -> System.exit(0);
                case "signal-during-stop" -> {
                    new Thread(() -> ShutdownSequence.runThenExit(seq, System::exit), "Server Shutdown Thread").start();
                    slowStarted.await();
                    System.exit(0);   // Ctrl+C 흉내
                }
                default -> {
                    new Thread(() -> ShutdownSequence.runThenExit(seq, System::exit), "Server Shutdown Thread").start();
                    Thread.sleep(Long.MAX_VALUE);
                }
            }
        }
    }
}
