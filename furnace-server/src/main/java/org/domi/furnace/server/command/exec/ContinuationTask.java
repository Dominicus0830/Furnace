// 바닐라 ContinuationTask(ia) — 소스 목록을 하나씩 큐에 넣는다(둘 이하면 바로, 셋 이상이면 하나 넣고 자신을 다시 넣는다).
package org.domi.furnace.server.command.exec;

import java.util.List;

public final class ContinuationTask<P> implements EntryAction {

    @FunctionalInterface
    public interface TaskProvider<P> {
        CommandQueueEntry create(Frame frame, P argument);
    }

    private final TaskProvider<P> taskFactory;
    private final List<P> arguments;
    private final CommandQueueEntry selfEntry;
    private int index;

    private ContinuationTask(TaskProvider<P> taskFactory, List<P> arguments, Frame frame) {
        this.taskFactory = taskFactory;
        this.arguments = arguments;
        this.selfEntry = new CommandQueueEntry(frame, this);
    }

    @Override
    public void execute(ExecutionContext context, Frame frame) {
        P argument = arguments.get(index);
        context.queueNext(taskFactory.create(frame, argument));
        if (++index < arguments.size()) context.queueNext(selfEntry);
    }

    public static <P> void schedule(ExecutionContext context, Frame frame, List<P> arguments, TaskProvider<P> taskFactory) {
        switch (arguments.size()) {
            case 0 -> { }
            case 1 -> context.queueNext(taskFactory.create(frame, arguments.get(0)));
            case 2 -> {
                context.queueNext(taskFactory.create(frame, arguments.get(0)));
                context.queueNext(taskFactory.create(frame, arguments.get(1)));
            }
            default -> context.queueNext(new ContinuationTask<>(taskFactory, arguments, frame).selfEntry);
        }
    }
}
