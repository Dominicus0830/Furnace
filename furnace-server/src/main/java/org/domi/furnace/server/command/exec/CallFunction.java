// 바닐라 CallFunction(hz) — 함수 호출: 예산 1 감소, 깊이+1 프레임(콜백 = 이 호출의 결과 콜백), 줄들을 ContinuationTask 로 하나씩.
// returnParentFrame(isReturn·if function)이면 discard 는 바깥 프레임 것을 물려받아 /return 이 바깥 대기 항목까지 버린다.
package org.domi.furnace.server.command.exec;

import java.util.List;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.function.CommandFunction;

public final class CallFunction implements UnboundEntryAction {

    private final CommandFunction function;
    private final ResultCallback resultCallback;
    private final boolean returnParentFrame;

    public CallFunction(CommandFunction function, ResultCallback resultCallback, boolean returnParentFrame) {
        this.function = function;
        this.resultCallback = resultCallback;
        this.returnParentFrame = returnParentFrame;
    }

    @Override
    public void execute(CommandSource source, ExecutionContext context, Frame frame) {
        context.incrementCost();
        List<UnboundEntryAction> entries = function.entries();
        TraceCallbacks tracer = context.tracer();
        if (tracer != null) tracer.onCall(frame.depth(), function.id(), entries.size());
        int depth = frame.depth() + 1;
        Frame.FrameControl control = returnParentFrame ? frame.frameControl() : context.frameControlForDepth(depth);
        Frame newFrame = new Frame(depth, resultCallback, control);
        ContinuationTask.schedule(context, newFrame, entries, (f, e) -> new CommandQueueEntry(f, e.bind(source)));
    }

    /** 바닐라 ExecutionContext.queueInitialFunctionCall: 최상위 프레임(콜백 frameResult)에서 함수를 부른다(호출 콜백은 소스 콜백). */
    public static void queueInitial(ExecutionContext context, CommandFunction function, CommandSource source, ResultCallback frameResult) {
        ExecutionContext.queueInitialAction(context, new CallFunction(function, source.callback(), false).bind(source), frameResult);
    }
}
