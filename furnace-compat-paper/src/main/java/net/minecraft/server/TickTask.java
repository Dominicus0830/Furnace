package net.minecraft.server;

/** 바닐라 서버 틱 작업(Mojang 이름 shim): 틱 번호와 할 일. */
public class TickTask implements Runnable {

    private final int tick;
    private final Runnable runnable;

    public TickTask(int tick, Runnable runnable) {
        this.tick = tick;
        this.runnable = runnable;
    }

    public int getTick() { return tick; }

    @Override
    public void run() { runnable.run(); }
}
