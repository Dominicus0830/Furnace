package org.bukkit.craftbukkit.util;

import java.util.concurrent.ExecutionException;

/** 한 번 계산한 값을 다른 스레드가 기다려 받는 실행 단위. 계산 중 예외는 {@link #get()} 이 {@link ExecutionException} 으로 다시 던진다. */
public abstract class Waitable<T> implements Runnable {
    private enum Status {
        WAITING,
        RUNNING,
        FINISHED,
    }

    Throwable t = null;
    T value = null;
    Status status = Status.WAITING;

    @Override
    public final void run() {
        synchronized (this) {
            if (status != Status.WAITING) {
                throw new IllegalStateException("Invalid state " + status);
            }
            status = Status.RUNNING;
        }
        try {
            value = evaluate();
        } catch (Throwable t) {
            this.t = t;
        } finally {
            synchronized (this) {
                status = Status.FINISHED;
                this.notifyAll();
            }
        }
    }

    protected abstract T evaluate();

    public synchronized T get() throws InterruptedException, ExecutionException {
        while (status != Status.FINISHED) {
            this.wait();
        }
        if (t != null) {
            throw new ExecutionException(t);
        }
        return value;
    }
}
