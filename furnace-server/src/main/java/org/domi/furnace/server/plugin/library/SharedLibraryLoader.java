// Paper/Bukkit 호환 플러그인들이 함께 쓰는 서버 제공 라이브러리(ServerLibraries) 로더. 호환 플러그인 클래스로더의 부모가 된다.
// Paper 에서는 Guava·Adventure 등이 서버 클래스로더에 있어 모든 플러그인이 같은 Class 를 본다 — 여기서도 플러그인마다 따로 붙이지 않고
// 이 로더 하나에 붙여 같은 Class 를 나눠 쓰게 한다(플러그인 사이에 Component·ImmutableList 를 주고받아도 ClassCastException 이 나지 않는다).
// 부모 우선(URLClassLoader 기본): 서버 클래스패스 → 이 로더의 jar. 플러그인 클래스로더는 부모 우선이라 이 jar 의 클래스가 플러그인이 품은
// 같은 이름 클래스보다 먼저다(Paper 와 같다 — 플러그인이 라이브러리를 품으려면 리로케이션한다).
package org.domi.furnace.server.plugin.library;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public final class SharedLibraryLoader extends URLClassLoader {
    static {
        ClassLoader.registerAsParallelCapable();
    }

    private final Set<Path> added = new HashSet<>();

    public SharedLibraryLoader(ClassLoader parent) { super("furnace-server-libraries", new URL[0], parent); }

    /** jar 를 뒤에 붙인다(이미 붙인 것은 무시). 핫 리로드로 새 묶음이 필요해져도 붙일 수 있다. */
    public synchronized void add(Path jar) {
        Path key = jar.toAbsolutePath().normalize();
        if (!added.add(key)) return;
        try {
            addURL(key.toUri().toURL());
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(jar.toString(), e);
        }
    }

    public synchronized int size() { return added.size(); }
}
