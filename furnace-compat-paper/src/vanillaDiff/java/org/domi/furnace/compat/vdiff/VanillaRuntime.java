package org.domi.furnace.compat.vdiff;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 바닐라 쪽 격리 로더(T-235). `:vanilla-extract:prepareVanillaJars` 가 캐시에 만든 Mojang 이름 서버 jar·라이브러리 목록
 * (`<캐시>/<판>/vanilla-classpath.txt`)을 읽어, 부모가 플랫폼 로더인 별도 로더에 올린다 — shim 이 있는 시험 클래스패스와 섞이지 않는다.
 * 바닐라 jar 는 캐시에서 읽기만 한다(저장소·서버 jar 에 들어가지 않는다, EULA).
 */
public final class VanillaRuntime {
    private static VanillaRuntime instance;

    private final ClassLoader loader;
    private final String mc;

    private VanillaRuntime(ClassLoader loader, String mc) {
        this.loader = loader;
        this.mc = mc;
    }

    /** 시험 JVM 에 하나. 목록 파일이 없으면 건너뛰지 않고 실패한다(규칙 5). */
    public static synchronized VanillaRuntime get() {
        if (instance != null) return instance;
        String list = System.getProperty("furnace.vdiff.classpath");
        if (list == null) throw new IllegalStateException("-Dfurnace.vdiff.classpath missing - run :furnace-compat-paper:vanillaDiffTest");
        Path file = Path.of(list);
        List<URL> urls = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                Path p = Path.of(line.trim());
                if (!Files.isRegularFile(p)) throw new IllegalStateException("vanilla classpath entry missing: " + p);
                urls.add(p.toUri().toURL());
            }
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + file + " - run :vanilla-extract:prepareVanillaJars", e);
        }
        if (urls.isEmpty()) throw new IllegalStateException("empty vanilla classpath: " + file);
        ClassLoader cl = new URLClassLoader("vanilla", urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader());
        VanillaRuntime rt = new VanillaRuntime(cl, System.getProperty("furnace.vdiff.mc", "?"));
        rt.bootstrap();
        instance = rt;
        return rt;
    }

    public ClassLoader loader() {
        return loader;
    }

    public String mc() {
        return mc;
    }

    /** 바닐라 레지스트리를 채운다(vanilla-extract 의 Vanilla.boot 와 같은 두 호출). 정적 초기화가 레지스트리를 보는 클래스가 있어 먼저 한다. */
    private void bootstrap() {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(loader);
        try {
            Class.forName("net.minecraft.SharedConstants", true, loader).getMethod("tryDetectVersion").invoke(null);
            Class.forName("net.minecraft.server.Bootstrap", true, loader).getMethod("bootStrap").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("vanilla bootstrap failed", e);
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }
}
