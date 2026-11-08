// "이 스레드는 지금 전역 스레드의 결과를 막고 기다린다" 표시. 전역 스레드가 어떤 region 을 빌리려고 그 틱이 끝나기를 기다릴 때,
// 그 region 스레드가 이 표시를 달고 있으면 서로 기다리는 것이라 곧바로 포기한다(GlobalScheduler#openRegionAccess).
// 서버와 호환 계층이 전역 결과를 기다리는 Future 의 get/join 에서 단다. 플러그인이 직접 쓸 일은 없다.
package org.domi.furnace.api.concurrent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class GlobalWait {

    private static final Set<Thread> WAITING = ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private GlobalWait() {}

    /** 표시를 단다. try-with-resources 로 닫는다(겹쳐 달아도 된다). */
    public static Mark begin() {
        if (DEPTH.get()[0]++ == 0) WAITING.add(Thread.currentThread());
        return Mark.INSTANCE;
    }

    /** t 가 지금 전역 결과를 기다리는 중인가. */
    public static boolean isWaiting(Thread t) { return t != null && WAITING.contains(t); }

    /** {@link #begin()} 의 닫는 손잡이. 단 스레드에서 닫는다. */
    public static final class Mark implements AutoCloseable {
        private static final Mark INSTANCE = new Mark();

        private Mark() {}

        @Override
        public void close() {
            int[] d = DEPTH.get();
            if (d[0] > 0 && --d[0] == 0) WAITING.remove(Thread.currentThread());
        }
    }
}
