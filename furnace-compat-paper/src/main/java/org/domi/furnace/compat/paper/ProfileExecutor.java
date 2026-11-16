package org.domi.furnace.compat.paper;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 프로필 조회({@code PlayerProfile#update})를 돌리는 공용 스레드 풀(Paper Util.PROFILE_EXECUTOR 처럼 고정 크기).
 * Spigot형·Paper형 프로필이 함께 쓴다. 이름 수백 개를 한꺼번에 {@code update()} 해도 스레드와 Mojang 요청이 몰리지 않는다(몰리면 429).
 */
public final class ProfileExecutor {

    /** 동시에 도는 조회 수(남는 요청은 대기열에서 차례를 기다린다). */
    public static final int THREADS = 4;

    private static final Executor INSTANCE = create();

    private ProfileExecutor() {}

    public static Executor get() { return INSTANCE; }

    private static Executor create() {
        AtomicInteger n = new AtomicInteger();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(THREADS, THREADS, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
            Thread t = new Thread(r, "Profile Lookup Executor #" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }
}
