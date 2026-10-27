// 바닐라 ExecutionControl(ht) — 사용자 정의 실행기가 같은 프레임으로 항목을 넣을 때 쓰는 손잡이.
package org.domi.furnace.server.command.exec;

public interface ExecutionControl {

    void queueNext(EntryAction action);

    Frame currentFrame();

    ExecutionContext context();

    static ExecutionControl create(ExecutionContext ctx, Frame frame) {
        return new ExecutionControl() {
            public void queueNext(EntryAction action) { ctx.queueNext(new CommandQueueEntry(frame, action)); }

            public Frame currentFrame() { return frame; }

            public ExecutionContext context() { return ctx; }
        };
    }
}
