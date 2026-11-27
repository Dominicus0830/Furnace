// 이식 다리(docs/roadmap/port-bridge-2026-11-27.md 5장) 찾기 — 호환 계층의 생성 스텁이 서버의 생성 구현을 이름 없이 얻는다.
package org.domi.furnace.api.internal.port;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * {@code <Area>Port} 인터페이스의 서버 구현을 {@link ServiceLoader} 로 한 번 찾아 둔다.
 * <p>찾는 로더는 늘 <b>그 인터페이스를 실은 클래스 로더</b>다(스레드 문맥 로더를 쓰지 않는다 — 플러그인 스레드가 처음 부르면
 * 그 플러그인 로더로 찾게 되어 구현을 못 보거나 다른 사본을 볼 수 있다). 서버 fatJar 는 api 와 서버를 한 로더에 싣는다.
 * <p>서버 구현은 상태 없는 싱글턴이라 언제 찾든(시험이 서버를 여러 번 띄워도) 같은 구현이다. 구현이 없거나 둘 이상이면
 * 그 자리에서 {@link IllegalStateException} — 조용한 null 은 없다.
 */
public final class PortBridges {

    private static final ClassValue<Object> FOUND = new ClassValue<>() {
        @Override
        protected Object computeValue(Class<?> type) { return load(type, type.getClassLoader()); }
    };

    private PortBridges() {}

    /** type 의 서버 구현. */
    public static <T> T get(Class<T> type) { return type.cast(FOUND.get(type)); }

    /** loader 에서 type 의 구현 하나를 찾는다(시험용으로 열어 둠). */
    static Object load(Class<?> type, ClassLoader loader) {
        List<Object> found = new ArrayList<>(1);
        for (Object o : ServiceLoader.load(type, loader)) found.add(o);
        if (found.isEmpty()) {
            throw new IllegalStateException("no port bridge implementation for " + type.getName()
                    + " - the compat layer was started without the Furnace server (or the server build left out the generated ports)");
        }
        if (found.size() > 1) {
            List<String> names = new ArrayList<>();
            for (Object o : found) names.add(o.getClass().getName());
            throw new IllegalStateException("several port bridge implementations for " + type.getName() + ": " + names);
        }
        return found.get(0);
    }
}
