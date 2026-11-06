// 바닐라 FallthroughTask(ic) — 실패를 알리고 프레임을 버린다(return run 의 소스가 비었을 때 등).
package org.domi.furnace.server.command.exec;

public final class FallthroughTask implements EntryAction {

    public static final FallthroughTask INSTANCE = new FallthroughTask();

    private FallthroughTask() {}

    @Override
    public void execute(ExecutionContext context, Frame frame) {
        frame.returnFailure();
        frame.discard();
    }
}
