// 이벤트 루프 작업을 넣는 즉시 그 자리에서 돌리는 시험 채널(한 스레드 시험 전용).
// EmbeddedChannel 은 작업 안에서 write 할 때마다 밀린 작업을 끼워 돌려(maybeRunPendingTasks) 한 작업이 여러 패킷을 쓰면 순서가 섞이고,
// 작업이 많이 밀리면 재귀가 깊어진다 — 실서버 NIO 루프에는 없는 일이라, 쓰는 순서를 보는 시험·벤치는 이 채널을 쓴다.
package org.domi.furnace.server;

import io.netty.channel.EventLoop;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public final class InlineEmbeddedChannel extends EmbeddedChannel {

    private EventLoop inline;

    @Override
    public EventLoop eventLoop() {
        EventLoop real = super.eventLoop();
        if (inline == null) {
            inline = (EventLoop) Proxy.newProxyInstance(InlineEmbeddedChannel.class.getClassLoader(), new Class<?>[] {EventLoop.class}, (proxy, m, args) -> {
                if (m.getName().equals("execute") && args != null && args.length == 1 && args[0] instanceof Runnable r) {
                    r.run();
                    return null;
                }
                try {
                    return m.invoke(real, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            });
        }
        return inline;
    }
}
