// 바닐라 IsolatedCall(id) — 새 프레임(깊이+1, 콜백 = 판정 콜백)을 열어 그 안에 항목을 넣는다(execute if function 용).
package org.domi.furnace.server.command.exec;

import java.util.function.Consumer;

public final class IsolatedCall implements EntryAction {

    private final Consumer<ExecutionControl> taskProducer;
    private final ResultCallback output;

    public IsolatedCall(Consumer<ExecutionControl> taskProducer, ResultCallback output) {
        this.taskProducer = taskProducer;
        this.output = output;
    }

    @Override
    public void execute(ExecutionContext context, Frame frame) {
        int depth = frame.depth() + 1;
        Frame newFrame = new Frame(depth, output, context.frameControlForDepth(depth));
        taskProducer.accept(ExecutionControl.create(context, newFrame));
    }
}
