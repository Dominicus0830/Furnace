// 월드 밖 엔티티의 상태 손잡이 호출 경로(EntityOp.StateCall) 재생. 호출은 API 인터페이스 메서드만 — 서버 구현 클래스의 메서드를 리플렉션으로 부르는 다리가
// 아니라, 공개 API 인터페이스를 기록한 대로 다시 부르는 것뿐이다(월드 밖 엔티티 경로라 성능은 문제없다).
package org.domi.furnace.server.plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import org.domi.furnace.api.internal.ChunkInternals.Call;

final class StateCalls {

    private StateCalls() {}

    /** 이 메서드를 경로에 쓸 수 있는가 — org.domi.furnace.api.entity(와 하위) 인터페이스 또는 Optional 의 메서드. */
    static void check(Method m) {
        Class<?> c = m.getDeclaringClass();
        boolean ok = c == Optional.class || (c.isInterface() && c.getName().startsWith("org.domi.furnace.api.entity."));
        if (!ok) throw new IllegalArgumentException("a state call must be a method of an interface in org.domi.furnace.api.entity: " + m);
    }

    /** 손잡이에서 시작해 경로를 차례로 부르고 마지막 결과를 돌려준다. */
    static Object invoke(Object root, List<Call> path) {
        Object cur = root;
        for (Call c : path) {
            Method m = c.method();
            check(m);
            if (cur instanceof Optional<?> o && m.getDeclaringClass() != Optional.class) {
                cur = o.orElseThrow(() -> new java.util.NoSuchElementException("state call on an empty " + m));
            }
            if (!m.getDeclaringClass().isInstance(cur)) {
                throw new IllegalArgumentException("a state call " + m + " does not fit the entity state of this entity type: " + (cur == null ? null : cur.getClass().getName()));
            }
            try {
                cur = m.invoke(cur, c.args().toArray());
            } catch (InvocationTargetException ex) {
                Throwable t = ex.getCause();
                if (t instanceof RuntimeException r) throw r;
                if (t instanceof Error e) throw e;
                throw new IllegalStateException(t);
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException(ex);
            }
        }
        return cur;
    }
}
