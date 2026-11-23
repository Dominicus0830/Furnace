package net.minecraft.util.thread;

/** 바닐라 재진입 가능한 이벤트 루프(Mojang 이름 shim): {@link BlockableEventLoop} 에 더해진 것이 없다(재진입 계수는 Furnace 에 루프가 없어 두지 않았다). */
public abstract class ReentrantBlockableEventLoop<R extends Runnable> extends BlockableEventLoop<R> {

    public ReentrantBlockableEventLoop(String name) { super(name); }
}
