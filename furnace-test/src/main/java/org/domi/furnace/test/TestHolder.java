// Furnace.server() 정적 진입점은 JVM 에 한 번만 정할 수 있다(Furnace.setServer). 시험 서버는 차례로 여러 번 뜨므로
// 지금 떠 있는 시험 서버가 맡고, 닫을 때 비워 다음 서버가 다시 정하게 한다. 둘을 동시에 띄우면 먼저 뜬 쪽이 맡는다.
package org.domi.furnace.test;

import java.lang.reflect.Field;
import org.domi.furnace.api.Furnace;

final class TestHolder {
    private TestHolder() {}

    private static Furnace owner;

    /** 비어 있으면 이 서버로 정한다. 정했으면 참. */
    static synchronized boolean claim(Furnace server) {
        if (current() != null) return false;
        Furnace.setServer(server);
        owner = server;
        return true;
    }

    /** 이 서버가 맡고 있었으면 비운다. */
    static synchronized void release(Furnace server) {
        if (server == null || owner != server) return;
        owner = null;
        try {
            holderField().set(null, null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Furnace.server() 를 비우지 못했다", e);
        }
    }

    private static Furnace current() {
        try {
            return (Furnace) holderField().get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Field holderField() throws ReflectiveOperationException {
        Class<?> holder = Class.forName("org.domi.furnace.api.Furnace$Holder");
        Field f = holder.getDeclaredField("server");
        f.setAccessible(true);
        return f;
    }
}
