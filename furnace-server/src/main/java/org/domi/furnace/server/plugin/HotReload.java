// 개발 모드 핫 리로드 보조: jar 사본, 옛 클래스로더 늦게 닫기·누수 경고, plugins/*.jar 변경 감시.
// 사본: 개발 모드에서는 plugins/.dev-cache/ 에 복사한 jar 를 읽는다. 그래야 원본 jar 를 덮어써도(윈도우의 파일 잠금 포함)
// 아직 돌고 있는 옛 클래스로더가 깨진 jar 를 읽지 않는다. 개발 모드가 아니면 쓰이지 않는다.
package org.domi.furnace.server.plugin;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class HotReload {

    private static final Logger log = LoggerFactory.getLogger("Furnace");
    /** jar 사본 폴더(plugins 아래, jar 찾기는 plugins 바로 아래만 보므로 여기 것은 플러그인으로 잡히지 않는다). */
    static final String SHADOW_DIR = ".dev-cache";
    private static final AtomicLong SEQ = new AtomicLong();

    private HotReload() {}

    // ------------------------------------------------------------------ jar 사본

    /** 원본 jar 를 사본 폴더에 새 이름으로 복사해 그 경로를 돌려준다. */
    static Path shadowCopy(Path pluginsDir, Path jar) throws IOException {
        Path d = Files.createDirectories(pluginsDir.resolve(SHADOW_DIR));
        String base = jar.getFileName().toString();
        if (base.endsWith(".jar")) base = base.substring(0, base.length() - 4);
        Path out = d.resolve(base + "-" + ProcessHandle.current().pid() + "-" + SEQ.incrementAndGet() + ".jar");
        Files.copy(jar, out, StandardCopyOption.REPLACE_EXISTING);
        return out;
    }

    /** 시작할 때·끝날 때 사본 폴더를 비운다(지울 수 없는 파일은 남긴다). */
    static void clearShadowDir(Path pluginsDir) {
        Path d = pluginsDir.resolve(SHADOW_DIR);
        if (!Files.isDirectory(d)) return;
        try (Stream<Path> s = Files.list(d)) {
            for (Path p : s.toList()) deleteQuietly(p);
        } catch (IOException ignored) {
        }
    }

    static void deleteQuietly(Path p) {
        if (p == null) return;
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
        }
    }

    // ------------------------------------------------------------------ 옛 클래스로더

    /** 옛 클래스로더를 닫고 사본을 지운 뒤, 회수되는지 지켜본다. */
    static void retire(String plugin, PluginManagerImpl.PluginClassLoader loader, long checkDelayMillis) {
        warnLiveThreads(plugin, loader);
        Path shadow = loader.shadow();
        PluginManagerImpl.closeQuietly(loader);
        deleteQuietly(shadow);
        if (checkDelayMillis < 0) return;
        WeakReference<ClassLoader> ref = new WeakReference<>(loader);
        Thread.ofVirtual().name("furnace-reload-leakcheck-" + plugin).start(() -> {
            try {
                Thread.sleep(checkDelayMillis);
                if (ref.get() == null) return;
                System.gc();   // 개발 모드에서만 오는 길 — 회수 기회를 한 번 준다
                Thread.sleep(1000);
                if (ref.get() != null) {
                    log.warn("[핫 리로드] {} 의 옛 클래스로더가 아직 회수되지 않았다 — 다른 플러그인의 static 필드, 끝나지 않은 스레드, "
                            + "ThreadLocal, 서버에 맡기고 풀지 않은 콜백이 옛 클래스를 붙잡고 있을 수 있다. 다시 읽을수록 메모리가 는다", plugin);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /** 옛 클래스로더를 문맥 로더로 가진 채 살아 있는 스레드 — 플러그인이 직접 만든 스레드를 안 끝냈을 가능성이 크다. */
    static List<String> liveThreads(ClassLoader loader) {
        List<String> out = new ArrayList<>();
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.isAlive() && t.getContextClassLoader() == loader) out.add(t.getName());
        }
        return out;
    }

    private static void warnLiveThreads(String plugin, ClassLoader loader) {
        List<String> threads = liveThreads(loader);
        if (!threads.isEmpty()) {
            log.warn("[핫 리로드] {} 가 만든 스레드가 꺼진 뒤에도 돌고 있다: {} — onDisable 에서 끝내야 한다", plugin, threads);
        }
    }

    // ------------------------------------------------------------------ jar 변경 감시

    /**
     * plugins/*.jar 를 주기적으로 훑어(크기·수정 시각) 바뀐 jar 를 알린다. 복사 도중의 jar 를 읽지 않도록
     * 한 번 훑을 때와 다음 번이 같아야(쓰기가 멈춰야) 알린다. 새로 생긴 jar·사라진 jar 도 알린다.
     */
    static final class Watcher {
        private record Stamp(long size, FileTime modified) {}

        private final Path dir;
        private final long periodMillis;
        private final Consumer<Path> changed;
        /** 마지막으로 알린(또는 처음 본) 상태. */
        private final Map<Path, Stamp> settled = new HashMap<>();
        /** 지난번 훑었을 때 본 상태. */
        private Map<Path, Stamp> last = new HashMap<>();
        private volatile boolean running = true;
        private Thread thread;

        Watcher(Path dir, long periodMillis, Consumer<Path> changed) {
            this.dir = dir;
            this.periodMillis = periodMillis;
            this.changed = changed;
            Map<Path, Stamp> now = scan();
            settled.putAll(now);
            last = now;
        }

        void start() {
            thread = Thread.ofPlatform().daemon().name("furnace-plugin-watcher").start(() -> {
                while (running) {
                    try {
                        Thread.sleep(periodMillis);
                    } catch (InterruptedException e) {
                        return;
                    }
                    if (running) poll();
                }
            });
        }

        void stop() {
            running = false;
            if (thread != null) thread.interrupt();
        }

        /** 한 번 훑는다(시험은 직접 부른다). */
        void poll() {
            Map<Path, Stamp> now = scan();
            List<Path> out = new ArrayList<>();
            for (var e : now.entrySet()) {
                Path p = e.getKey();
                Stamp st = e.getValue();
                if (st.equals(last.get(p)) && !st.equals(settled.get(p))) {
                    settled.put(p, st);
                    out.add(p);
                }
            }
            for (Path p : new ArrayList<>(settled.keySet())) {
                if (!now.containsKey(p)) {
                    settled.remove(p);
                    out.add(p);
                }
            }
            last = now;
            for (Path p : out) {
                try {
                    changed.accept(p);
                } catch (Throwable t) {
                    log.error("[핫 리로드] {} 변경 처리 실패", p, t);
                }
            }
        }

        private Map<Path, Stamp> scan() {
            Map<Path, Stamp> out = new HashMap<>();
            try (Stream<Path> s = Files.list(dir)) {
                for (Path p : s.toList()) {
                    if (!p.toString().endsWith(".jar") || !Files.isRegularFile(p)) continue;
                    try {
                        out.put(p, new Stamp(Files.size(p), Files.getLastModifiedTime(p)));
                    } catch (IOException ignored) {
                    }
                }
            } catch (IOException ignored) {
            }
            return out;
        }
    }
}
