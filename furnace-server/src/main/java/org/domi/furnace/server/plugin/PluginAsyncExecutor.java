// 플러그인 비동기 실행기(Furnace#asyncExecutor·PluginTasks.async) — furnace.properties plugins.async-threads 로 스레드 종류를 고른다(T-034, 결정 D1).
//   platform(기본): 이름 붙은 플랫폼 스레드 풀 "Furnace Async Thread - N". Paper CraftAsyncScheduler 와 같은 모양(코어 4, 늘어나면 무제한,
//                   대기열 없음, 30초 놀면 정리). synchronized 안에서 소켓·파일을 기다리는 옛 JDBC 드라이버도 다른 작업을 막지 않는다.
//   virtual: 작업마다 가상 스레드(예전 기본). JDK 21 은 synchronized 안에서 블로킹하면 운반 스레드(코어 수)를 붙잡아(JEP 491 은 JDK 24)
//            코어 수만큼 붙잡히면 모든 비동기 작업이 같이 멈춘다 — O12-02 측정 452ms(플랫폼 1ms).
// 플랫폼 스레드는 다시 쓰이므로 작업이 끝날 때 인터럽트 표시와 스레드 이름을 되돌린다(다음 작업이 앞 작업의 인터럽트를 받지 않게).
// 모드는 서버가 설정을 읽은 뒤(PluginManagerImpl.settings) 정해지고, 풀은 처음 쓸 때 만든다.
package org.domi.furnace.server.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PluginAsyncExecutor extends AbstractExecutorService {

    private static final Logger log = LoggerFactory.getLogger("Furnace");

    /** 플랫폼 풀 스레드 이름 앞부분(뒤에 0부터 번호). 스레드 덤프·로그에서 플러그인 비동기 작업을 찾는 표시. */
    public static final String PLATFORM_THREAD_PREFIX = "Furnace Async Thread - ";
    /** 가상 스레드 이름 앞부분(뒤에 0부터 번호). */
    public static final String VIRTUAL_THREAD_PREFIX = "Furnace Async Virtual - ";

    /** Paper CraftAsyncScheduler 와 같은 값. */
    static final int CORE_THREADS = 4;
    static final long KEEP_ALIVE_SECONDS = 30;

    private volatile boolean virtual;
    private volatile ThreadPoolExecutor platformPool;   // 처음 쓸 때 this 의 모니터 안에서 만든다
    private volatile ExecutorService virtualPool;       // 〃
    private boolean shutdown;                           // this 의 모니터로 보호

    /** plugins.async-threads=virtual 이면 true. 이미 받은 작업은 원래 풀에서 끝까지 돈다. */
    public void useVirtualThreads(boolean on) { this.virtual = on; }

    /** 지금 새 작업이 가상 스레드에서 도는가(시험·진단용). */
    public boolean usesVirtualThreads() { return virtual; }

    @Override
    public void execute(Runnable command) {
        if (command == null) throw new NullPointerException("작업이 null 이다");
        ExecutorService p = virtual ? virtualPool : platformPool;   // 빠른 길: 잠금 없이(닫힌 풀은 스스로 거부한다)
        if (p == null) p = create();
        try {
            p.execute(new Reusable(command));
        } catch (RejectedExecutionException e) {
            throw closed();
        }
    }

    private static RejectedExecutionException closed() {
        return new RejectedExecutionException("플러그인 비동기 실행기가 닫혔다(서버 종료 중)");
    }

    private synchronized ExecutorService create() {
        if (shutdown) throw closed();
        if (virtual) {
            if (virtualPool == null) virtualPool = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name(VIRTUAL_THREAD_PREFIX, 0).factory());
            return virtualPool;
        }
        if (platformPool == null) platformPool = newPlatformPool();
        return platformPool;
    }

    private static ThreadPoolExecutor newPlatformPool() {
        AtomicInteger n = new AtomicInteger();
        ThreadPoolExecutor ex = new ThreadPoolExecutor(CORE_THREADS, Integer.MAX_VALUE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new SynchronousQueue<>(), r -> {
                    Thread t = new Thread(r, PLATFORM_THREAD_PREFIX + n.getAndIncrement());
                    t.setDaemon(true);   // 멈춘 플러그인 작업이 JVM 종료를 붙잡지 않게(가상 스레드도 데몬이었다)
                    return t;
                });
        ex.allowCoreThreadTimeOut(true);
        return ex;
    }

    /** 작업 하나: 잡히지 않은 예외·오류(Throwable)는 스레드 이름과 함께 서버 로그에 남기고, 끝나면 인터럽트 표시·이름을 되돌린다(플랫폼 스레드는 다시 쓰인다). */
    private record Reusable(Runnable task) implements Runnable {
        @Override
        public void run() {
            Thread t = Thread.currentThread();
            String name = t.getName();
            try {
                task.run();
            } catch (Throwable e) {   // Paper CraftAsyncTask 처럼 Error(NoClassDefFoundError·StackOverflowError 등)까지 — 일꾼이 죽고 크래시 리포트가 쌓이지 않게
                log.error("플러그인 비동기 작업에서 잡히지 않은 예외(스레드 {})", name, e);
            } finally {
                Thread.interrupted();
                if (!t.getName().equals(name)) t.setName(name);
            }
        }
    }

    @Override
    public synchronized void shutdown() {
        shutdown = true;
        if (platformPool != null) platformPool.shutdown();
        if (virtualPool != null) virtualPool.shutdown();
    }

    @Override
    public synchronized List<Runnable> shutdownNow() {
        shutdown = true;
        List<Runnable> out = new ArrayList<>();
        if (platformPool != null) out.addAll(platformPool.shutdownNow());
        if (virtualPool != null) out.addAll(virtualPool.shutdownNow());
        return out;
    }

    @Override
    public synchronized boolean isShutdown() { return shutdown; }

    @Override
    public synchronized boolean isTerminated() {
        return shutdown && (platformPool == null || platformPool.isTerminated()) && (virtualPool == null || virtualPool.isTerminated());
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        ExecutorService p, v;
        synchronized (this) {
            p = platformPool;
            v = virtualPool;
        }
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        if (p != null && !p.awaitTermination(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) return false;
        if (v != null && !v.awaitTermination(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) return false;
        return isTerminated();
    }
}
