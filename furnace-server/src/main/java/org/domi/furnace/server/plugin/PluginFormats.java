// 다른 서버 소프트웨어 플러그인 형식 어댑터(PluginFormat)를 "필요할 때만" 불러오는 손잡이.
//
// 호환 모듈(furnace-compat-paper, Bukkit·Paper API 클래스 0.6MB)은 서버 jar 에 함께 실리지만, Bukkit·Paper 플러그인이 하나도 없으면
// 그 모듈의 클래스는 JVM 에 한 개도 올라오면 안 된다(서버 핵심과 의존이 엉키지 않는다는 약속).
//   - available(): 호환 모듈이 클래스패스에 있는지 — META-INF/services 자원 파일이 있는지만 본다. 클래스를 읽지 않는다.
//   - get(): 어댑터를 실제로 만든다(ServiceLoader). 설명 파일이 Furnace 것이 아닌 jar 를 처음 만났을 때 한 번만 부른다.
// 시험은 어댑터 목록을 직접 넣을 수 있다(of).
package org.domi.furnace.server.plugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.api.plugin.PluginFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class PluginFormats {

    private static final Logger log = LoggerFactory.getLogger("Furnace");
    /** ServiceLoader 가 읽는 자원 이름 — 이 파일이 있는지만 봐서 클래스를 올리지 않고 "호환 모듈이 있다" 를 안다. */
    static final String SERVICES_RESOURCE = "META-INF/services/" + PluginFormat.class.getName();

    private static final PluginFormats NONE = new PluginFormats(false, null, List.of());

    private final boolean available;
    private final ClassLoader loader;
    private volatile List<PluginFormat> resolved;

    private PluginFormats(boolean available, ClassLoader loader, List<PluginFormat> resolved) {
        this.available = available;
        this.loader = loader;
        this.resolved = resolved;
    }

    /** 어댑터 없음(호환이 꺼졌다). */
    static PluginFormats none() { return NONE; }

    /** 이미 만든 어댑터(시험·직접 지정). */
    static PluginFormats of(List<PluginFormat> formats) {
        return formats.isEmpty() ? NONE : new PluginFormats(true, null, List.copyOf(formats));
    }

    /** 클래스패스의 호환 모듈을 찾아 둔다. 클래스는 올리지 않는다 — 어댑터는 get() 에서 처음 만든다. */
    static PluginFormats discover(ClassLoader loader) {
        boolean present;
        try {
            present = loader.getResources(SERVICES_RESOURCE).hasMoreElements();
        } catch (IOException e) {
            log.warn("호환 모듈 자원을 찾지 못했다: {}", e.toString());
            present = false;
        }
        return present ? new PluginFormats(true, loader, null) : NONE;
    }

    /** 호환 모듈이 있는가(클래스를 올리지 않고 안다). */
    boolean available() { return available; }

    /** 어댑터를 이미 만들었는가 — 시험이 "아직 안 올렸다" 를 확인한다. */
    boolean loaded() { return resolved != null; }

    /** 어댑터 목록. 처음 부를 때 만든다(ServiceLoader). 하나가 깨져도 나머지는 쓴다. */
    List<PluginFormat> get() {
        List<PluginFormat> r = resolved;
        if (r != null) return r;
        synchronized (this) {
            if (resolved == null) resolved = load(loader);
            return resolved;
        }
    }

    private static List<PluginFormat> load(ClassLoader loader) {
        List<PluginFormat> out = new ArrayList<>();
        var it = java.util.ServiceLoader.load(PluginFormat.class, loader).iterator();
        while (true) {
            try {
                if (!it.hasNext()) break;
                out.add(it.next());
            } catch (java.util.ServiceConfigurationError e) {
                log.error("플러그인 형식 어댑터를 불러오지 못했다", e);
            }
        }
        return List.copyOf(out);
    }
}
