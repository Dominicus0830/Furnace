package net.minecraft.util.thread;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * 바닐라 블록 가능한 이벤트 루프(Mojang 이름 shim, 효과·프로필·속성 묶음) — {@code MinecraftServer} 의 윗클래스. 바닐라는 서버 스레드 하나가 대기열의 작업을
 * 돌리지만 Furnace 에는 그런 스레드가 없다: 작업은 전역 스레드에 맡기고({@link #execute}), 그 대기열은 이 클래스가 들지 않는다.
 * {@link #managedBlock} 은 바닐라처럼 조건이 참이 될 때까지 기다리되 기다리는 동안 대기열 작업을 돌리지 못한다(없다) — 조건이 다른 스레드가 하는 일(청크 미래 등)을 기다리는
 * 용도(FAWE 의 되살리기 대기)면 바닐라와 같다. 틱 스레드(전역·region)에서는 다른 region 을 기다리는 교착을 피해 {@code ThreadOwnershipException}.
 * 대기열 쪽 멤버({@code pollTask}·{@code runAllTasks}·{@code pendingRunnables}·{@code submit}…)는 두지 않았다(부르면 없는 멤버).
 */
public abstract class BlockableEventLoop<R extends Runnable> implements Executor {

    private final String name;

    protected BlockableEventLoop(String name) { this.name = name; }

    public String name() { return name; }

    /** 바닐라: 이 스레드가 루프를 돌리는 스레드인가. Furnace: 틱을 도는 스레드(전역·region)나 Bukkit 주 스레드로 표시한 구간이면 참. */
    public boolean isSameThread() {
        if (org.domi.furnace.compat.thread.PrimaryThread.isPrimary()) return true;
        try {
            return org.domi.furnace.api.Furnace.server().scheduler().isTickThread();
        } catch (IllegalStateException notRunning) {
            return false;
        }
    }

    /** 바닐라: 작업을 루프에 맡긴다 — Furnace: 전역 스레드. */
    @Override
    public void execute(Runnable task) { org.domi.furnace.api.Furnace.server().scheduler().global().execute(task); }

    public CompletableFuture<Void> submit(Runnable task) { return CompletableFuture.runAsync(task, this); }

    public <V> CompletableFuture<V> submit(Supplier<V> task) { return CompletableFuture.supplyAsync(task, this); }

    /**
     * 바닐라: 조건이 참이 될 때까지(그동안 이 루프의 작업을 돌리며) 기다린다. 틱 스레드면 ThreadOwnershipException, 아니면 1ms 간격으로 조건을 본다.
     * 기다리다 인터럽트되면 인터럽트 표시를 되살리고 나온다(서버 종료가 막히지 않게).
     */
    public void managedBlock(BooleanSupplier condition) {
        if (condition.getAsBoolean()) return;
        if (isSameThread()) {
            throw new org.domi.furnace.api.concurrent.ThreadOwnershipException("managedBlock on a region or global thread would wait for work that may need"
                    + " this very thread (deadlock) - wait on a non-tick thread (current thread: " + Thread.currentThread().getName() + ")");
        }
        while (!condition.getAsBoolean()) {
            LockSupport.parkNanos(1_000_000L);
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
