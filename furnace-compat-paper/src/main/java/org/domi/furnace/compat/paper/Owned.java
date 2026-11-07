// 소유 스레드 규칙 — Bukkit 의 동기 호출을 Furnace 의 비동기 API 위에 올릴 때 쓴다.
// 소유 스레드(또는 서버가 스레드 안전을 보장하는 읽기)면 Future 가 곧바로 끝나므로 그 결과를 그대로 돌려주고,
// 아니면 읽기는 ThreadOwnershipException, 쓰기는 소유 스레드로 예약해 두고 돌아간다(결과를 돌려줄 필요가 없는 쓰기만).
package org.domi.furnace.compat.paper;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.domi.furnace.api.concurrent.ThreadOwner;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.sched.TaskScheduler;

final class Owned {

    private static final System.Logger LOG = System.getLogger("FurnaceCompat");

    private Owned() {}

    /** 지금 스레드가 그 스케줄러의 소유 스레드인가(소유자를 알 수 없는 스케줄러면 false). */
    static boolean owns(TaskScheduler s) { return s instanceof ThreadOwner o && o.isOwnerThread(); }

    /** 곧바로 끝난 Future 의 값. 아직 안 끝났으면 소유 스레드가 아니라는 뜻이라 ThreadOwnershipException. */
    static <T> T read(CompletableFuture<T> f, String what) {
        if (!f.isDone()) throw notOwner(what);
        return join(f);
    }

    /** 곧바로 끝났으면 실패를 던지고, 아니면 예약된 것으로 보고 실패는 로그만 남긴다. */
    static void write(CompletableFuture<?> f, String what) {
        if (f.isDone()) {
            join(f);
            return;
        }
        f.whenComplete((v, e) -> {
            if (e != null) LOG.log(System.Logger.Level.WARNING, what + " 예약 작업이 실패했다", e);
        });
    }

    /** 결과를 돌려줘야 하는 읽고-고치는 작업의 전제: 소유 스레드여야 한다. */
    static void requireOwner(boolean owner, String what) {
        if (!owner) throw notOwner(what);
    }

    static ThreadOwnershipException notOwner(String what) {
        return new ThreadOwnershipException(what + " 는 그 대상을 가진 region 스레드에서만 부를 수 있다 (현재 " + Thread.currentThread().getName()
                + ") — Furnace 에는 Bukkit 의 단일 주 스레드가 없다. 이벤트 처리기·entityScheduler 작업 안에서 부르거나 Furnace 비동기 API 를 쓴다");
    }

    private static <T> T join(CompletableFuture<T> f) {
        try {
            return f.join();
        } catch (CompletionException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException r) throw r;
            if (c instanceof Error er) throw er;
            throw new IllegalStateException(c);
        } catch (java.util.concurrent.CancellationException e) {
            throw e;
        }
    }

}
