// 서버 전역 엔티티 id 발급(1부터 증가). join이 IO 스레드서 올 수 있어 atomic.
package org.domi.furnace.server.entity;

import java.util.concurrent.atomic.AtomicInteger;

public final class EntityIdAllocator {

    private final AtomicInteger next = new AtomicInteger(0);

    public int next() {
        return next.incrementAndGet();
    }
}
