// "주 스레드" 판정 — Bukkit 플러그인이 sync API 를 불러도 되는 곳(Bukkit.isPrimaryThread()).
// Furnace 에는 주 스레드가 따로 없다: 전역 스레드가 가장 가깝고, region 스레드에서 도는 옛 플러그인 코드(이벤트 리스너 등)는
// 호환 계층이 enter() 로 표시한다. 호환 계층이 직접 돌리는 동기 작업·명령은 이미 표시돼 있다.
// 전역 스레드에서 enter() 하면 region 빌림 구간(GlobalScheduler#openRegionAccess)도 함께 연다: 표시된 옛 코드가 블록·엔티티를
// 동기로 만지면 그 region 을 틱 사이에 잠깐 빌려 와(가장 바깥 표시가 닫힐 때 돌려준다) Bukkit 의 주 스레드처럼 곧바로 읽고 쓴다.
package org.domi.furnace.compat.thread;

import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.concurrent.ThreadOwner;
import org.domi.furnace.api.sched.GlobalScheduler;

public final class PrimaryThread {

    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);
    /** 가장 바깥 enter() 가 연 region 빌림 구간(전역 스레드에서만, 아니면 없음). */
    private static final ThreadLocal<GlobalScheduler.RegionAccess> ACCESS = new ThreadLocal<>();

    private PrimaryThread() {}

    /** 이 스레드를 "주 스레드"로 표시한다. try-with-resources 로 닫는다(겹쳐 들어와도 된다). */
    public static Scope enter() {
        if (DEPTH.get()[0]++ == 0) {
            GlobalScheduler.RegionAccess a = openAccess();
            if (a != GlobalScheduler.RegionAccess.NONE) ACCESS.set(a);
        }
        return Scope.INSTANCE;
    }

    /** 전역 스레드면 region 빌림 구간을 연다(서버가 없거나 다른 스레드면 빈 손잡이). */
    private static GlobalScheduler.RegionAccess openAccess() {
        try {
            return Furnace.server().scheduler().global().openRegionAccess();
        } catch (IllegalStateException e) {
            return GlobalScheduler.RegionAccess.NONE;
        }
    }

    /** 표시된 구간 안이거나 전역 스레드인가. 서버가 아직 없으면 표시만 본다. */
    public static boolean isPrimary() {
        if (DEPTH.get()[0] > 0) return true;
        Furnace server;
        try {
            server = Furnace.server();
        } catch (IllegalStateException e) {
            return false;
        }
        return isGlobalThread(server);
    }

    /** 지금 스레드가 전역 스레드인가. */
    public static boolean isGlobalThread(Furnace server) {
        return server.scheduler().global() instanceof ThreadOwner o && o.isOwnerThread();
    }

    /** {@link #enter()} 의 닫는 손잡이. */
    public static final class Scope implements AutoCloseable {
        private static final Scope INSTANCE = new Scope();

        private Scope() {}

        @Override
        public void close() {
            int[] d = DEPTH.get();
            if (d[0] > 0 && --d[0] == 0) {
                GlobalScheduler.RegionAccess a = ACCESS.get();
                if (a != null) {
                    ACCESS.remove();
                    a.close();   // 빌린 region 을 모두 돌려준다
                }
            }
        }
    }
}
