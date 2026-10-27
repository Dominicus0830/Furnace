// 바닐라 FunctionCommand(amy) · ReturnCommand(anx) · ExecuteCommand 의 if|unless function(amt.d, amt.a).
// 셋 다 큐를 직접 다루는 사용자 정의 실행기다(CustomCommandExecutor / CustomModifierExecutor) — 결과는 프레임 콜백으로 흐른다.
// 매크로 인자(function <name> <arguments> / with …)는 S7 — 트리에 아직 없다(VanillaCommandTreeTest.NOT_YET).
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.IntPredicate;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.ComponentUtils;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.SharedSuggestions;
import org.domi.furnace.server.command.arguments.FunctionArgument;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandNode;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.ContextChain;
import org.domi.furnace.server.command.brigadier.DynamicCommandExceptionType;
import org.domi.furnace.server.command.brigadier.SuggestionProvider;
import org.domi.furnace.server.command.exec.BuildContexts;
import org.domi.furnace.server.command.exec.CallFunction;
import org.domi.furnace.server.command.exec.CallbackAwait;
import org.domi.furnace.server.command.exec.ChainModifiers;
import org.domi.furnace.server.command.exec.CustomCommandExecutor;
import org.domi.furnace.server.command.exec.CustomModifierExecutor;
import org.domi.furnace.server.command.exec.ExecutionControl;
import org.domi.furnace.server.command.exec.ExecutionContext;
import org.domi.furnace.server.command.exec.FallthroughTask;
import org.domi.furnace.server.command.exec.Frame;
import org.domi.furnace.server.command.exec.IsolatedCall;
import org.domi.furnace.server.command.exec.ResultCallback;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.function.CommandFunction;
import org.domi.furnace.server.function.FunctionLibrary;

public final class FunctionCommands {

    private FunctionCommands() {}

    static final DynamicCommandExceptionType ERROR_NO_FUNCTIONS =
            new DynamicCommandExceptionType(id -> Text.translatable("commands.function.scheduled.no_functions", id));

    /** 바닐라 amy.b: 태그(# 접두) 다음 함수 id. */
    public static final SuggestionProvider<CommandSource> SUGGEST_FUNCTION = (c, b) -> {
        FunctionLibrary lib = c.getSource().getServer().functions().library();
        SharedSuggestions.suggestResource(lib.tagIds(), b, "#");
        return SharedSuggestions.suggestResource(lib.functions().keySet(), b);
    };

    // ---------------------------------------------------------------- /function

    public static void registerFunction(CommandDispatcher<CommandSource> d) {
        d.register(literal("function").requires(s -> s.hasPermission(2))
                .then(argument("name", FunctionArgument.functions()).suggests(SUGGEST_FUNCTION).executes(new FunctionExecutor())));
    }

    /** 바닐라 amy.a(et): 호출 소스는 출력 숨김 + 권한을 2 이상으로. */
    public static CommandSource modifySenderForExecution(CommandSource source) { return source.withSilent().withMaximumPermission(2); }

    /** 바닐라 amy.c: 함수 목록을 풀고 "Running function …" 을 먼저 알린 뒤 호출을 큐에 넣는다. */
    static final class FunctionExecutor extends CustomCommandExecutor.WithErrorHandling implements CustomCommandExecutor.CommandAdapter {
        @Override
        protected void runGuarded(CommandSource source, ContextChain<CommandSource> chain, ChainModifiers modifiers, ExecutionControl control)
                throws CommandSyntaxException {
            CommandContext<CommandSource> ctx = chain.getTopContext().copyFor(source);
            FunctionArgument.Resolved r = FunctionArgument.getFunctionCollection(ctx, "name");
            Collection<CommandFunction> functions = r.functions();
            if (functions.isEmpty()) throw ERROR_NO_FUNCTIONS.create(r.id().toString());
            CommandSource callSource = modifySenderForExecution(source);
            if (functions.size() == 1) {
                source.sendSuccess(() -> Text.translatable("commands.function.scheduled.single", Text.text(functions.iterator().next().id().toString())), true);
            } else {
                List<Text> names = functions.stream().map(f -> Text.text(f.id().toString())).toList();
                source.sendSuccess(() -> Text.translatable("commands.function.scheduled.multiple", formatList(names)), true);
            }
            queueFunctions(functions, source, callSource, control, modifiers);
        }
    }

    private static Text formatList(List<Text> items) {
        Text out = Text.empty();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) out = out.append(ComponentUtils.defaultSeparator());
            out = out.append(items.get(i));
        }
        return out;
    }

    /** 바닐라 amy.a(Collection, ub, T, T, ht, b, hn): isReturn 이면 프레임으로 되돌리는 호출, 아니면 합산 호출. */
    public static void queueFunctions(Collection<CommandFunction> functions, CommandSource originalSource, CommandSource functionSource,
                                      ExecutionControl control, ChainModifiers modifiers) {
        if (modifiers.isReturn()) queueFunctionsAsReturn(functions, originalSource, functionSource, control);
        else queueFunctionsNoReturn(functions, originalSource, functionSource, control);
    }

    /** 바닐라 amy.a(T, b, akr, eq): 원 소스가 조용하지 않으면 함수마다 "Function … returned …" 을 원 소스에 알린 뒤 콜백. */
    private static ResultCallback decorateOutputIfNeeded(CommandSource originalSource, ResourceId id, ResultCallback callback) {
        if (originalSource.isSilent()) return callback;
        return (success, result) -> {
            originalSource.sendSuccess(() -> Text.translatable("commands.function.result", Text.text(id.toString()), result), true);
            return callback.onResult(success, result);
        };
    }

    private static void queueFunctionsAsReturn(Collection<CommandFunction> functions, CommandSource originalSource, CommandSource functionSource,
                                               ExecutionControl control) {
        CommandSource src = functionSource.clearCallbacks();
        ResultCallback functionCommandOutputCallback = ResultCallback.chain(originalSource.callback(), control.currentFrame().returnValueConsumer());
        for (CommandFunction f : functions) {
            ResultCallback cb = decorateOutputIfNeeded(originalSource, f.id(), functionCommandOutputCallback);
            control.queueNext(new CallFunction(f, cb, true).bind(src));
        }
        control.queueNext(FallthroughTask.INSTANCE);
    }

    private static void queueFunctionsNoReturn(Collection<CommandFunction> functions, CommandSource originalSource, CommandSource functionSource,
                                               ExecutionControl control) {
        CommandSource src = functionSource.clearCallbacks();
        ResultCallback functionCommandOutputCallback = originalSource.callback();
        if (functions.isEmpty()) return;
        if (functions.size() == 1) {
            CommandFunction f = functions.iterator().next();
            control.queueNext(new CallFunction(f, decorateOutputIfNeeded(originalSource, f.id(), functionCommandOutputCallback), false).bind(src));
        } else if (functionCommandOutputCallback == ResultCallback.EMPTY) {
            for (CommandFunction f : functions) {
                control.queueNext(new CallFunction(f, decorateOutputIfNeeded(originalSource, f.id(), functionCommandOutputCallback), false).bind(src));
            }
        } else {
            // 바닐라 Accumulator: success 를 무시하고 결과를 더한다. 하나라도 결과가 왔으면 마지막에 onSuccess(합)
            final class Accumulator {
                boolean anyResult;
                int sum;
            }
            Accumulator acc = new Accumulator();
            ResultCallback partial = (success, result) -> {
                acc.anyResult = true;
                acc.sum += result;
                return ResultCallback.DONE;
            };
            for (CommandFunction f : functions) {
                control.queueNext(new CallFunction(f, decorateOutputIfNeeded(originalSource, f.id(), partial), false).bind(src));
            }
            control.queueNext((context, frame) -> {
                if (acc.anyResult) CallbackAwait.then(context, functionCommandOutputCallback.onSuccess(acc.sum), () -> {});
            });
        }
    }

    // ---------------------------------------------------------------- /return

    public static void registerReturn(CommandDispatcher<CommandSource> d) {
        d.register(literal("return").requires(s -> s.hasPermission(2))
                .then(argument("value", Arguments.integer()).executes(new ReturnValue()))
                .then(literal("fail").executes(new ReturnFail()))
                .then(literal("run").forward(d.getRoot(), new ReturnFromCommand(), false)));
    }

    /** 바닐라 anx.c: ① 소스 콜백 → ② 프레임 콜백 → ③ discard(설계 §2.4 I1). */
    static final class ReturnValue implements CustomCommandExecutor.CommandAdapter {
        @Override
        public void run(CommandSource source, ContextChain<CommandSource> chain, ChainModifiers modifiers, ExecutionControl control) {
            int value = chain.getTopContext().getArgument("value", Integer.class);
            Frame frame = control.currentFrame();
            ExecutionContext context = control.context();
            CallbackAwait.then(context, source.callback().onSuccess(value),
                    () -> CallbackAwait.then(context, frame.returnSuccess(value), frame::discard));
        }
    }

    /** 바닐라 anx.a: ① 소스 콜백 실패 → ② 프레임 실패 → ③ discard. */
    static final class ReturnFail implements CustomCommandExecutor.CommandAdapter {
        @Override
        public void run(CommandSource source, ContextChain<CommandSource> chain, ChainModifiers modifiers, ExecutionControl control) {
            Frame frame = control.currentFrame();
            ExecutionContext context = control.context();
            CallbackAwait.then(context, source.callback().onFailure(), () -> CallbackAwait.then(context, frame.returnFailure(), frame::discard));
        }
    }

    /** 바닐라 anx.b: 소스가 없으면(이미 isReturn 일 때만) 실패, 있으면 먼저 discard 한 뒤 isReturn 으로 다음 단계를 잇는다. */
    static final class ReturnFromCommand implements CustomModifierExecutor.ModifierAdapter {
        @Override
        public void apply(CommandSource originalSource, List<CommandSource> currentSources, ContextChain<CommandSource> currentStep,
                          ChainModifiers modifiers, ExecutionControl control) {
            if (currentSources.isEmpty()) {
                if (modifiers.isReturn()) control.queueNext(FallthroughTask.INSTANCE);
                return;
            }
            control.currentFrame().discard();
            ContextChain<CommandSource> next = currentStep.nextStage();
            String input = next.getTopContext().getInput();
            control.queueNext(new BuildContexts.Continuation(input, next, modifiers.setReturn(), originalSource, currentSources));
        }
    }

    // ---------------------------------------------------------------- /execute if|unless function

    /** execute if/unless 아래에 function 분기를 단다(ExecuteCommands 를 건드리지 않도록 등록 뒤에 붙인다). */
    public static void registerExecuteBranch(CommandDispatcher<CommandSource> d) {
        CommandNode<CommandSource> execute = d.getRoot().getChild("execute");
        for (String name : List.of("if", "unless")) {
            boolean positive = name.equals("if");
            execute.getChild(name).addChild(literal("function")
                    .then(argument("name", FunctionArgument.functions()).suggests(SUGGEST_FUNCTION).fork(execute, new ExecuteIfFunction(positive)))
                    .build());
        }
    }

    /** 바닐라 amt.d + amt.a: 소스마다 격리 호출로 함수(들)를 돌리고 반환값(return 없음 = 0)으로 소스를 거른다. */
    static final class ExecuteIfFunction implements CustomModifierExecutor.ModifierAdapter {
        private final IntPredicate check;

        ExecuteIfFunction(boolean positive) { this.check = positive ? v -> v != 0 : v -> v == 0; }

        @Override
        public void apply(CommandSource originalSource, List<CommandSource> currentSources, ContextChain<CommandSource> currentStep,
                          ChainModifiers modifiers, ExecutionControl control) {
            List<CommandSource> filtered = new ArrayList<>(currentSources.size());
            Collection<CommandFunction> functions;
            try {
                functions = FunctionArgument.getFunctions(currentStep.getTopContext().copyFor(originalSource), "name");
            } catch (CommandSyntaxException e) {
                originalSource.handleError(e, modifiers.isForked());
                return;
            }
            if (functions.isEmpty()) return;   // 바닐라: 아무것도 큐에 넣지 않는다(다음 단계도 없다)
            List<CommandFunction> instantiated = List.copyOf(functions);   // 매크로가 없어 인스턴스화 = 그대로(S7)
            for (CommandSource source : currentSources) {
                CommandSource newSource = modifySenderForExecution(source.clearCallbacks());
                ResultCallback filter = ResultCallback.sync((success, result) -> {
                    if (check.test(result)) filtered.add(source);
                });
                control.queueNext(new IsolatedCall(c -> {
                    for (CommandFunction f : instantiated) c.queueNext(new CallFunction(f, c.currentFrame().returnValueConsumer(), true).bind(newSource));
                    c.queueNext(FallthroughTask.INSTANCE);
                }, filter));
            }
            ContextChain<CommandSource> next = currentStep.nextStage();
            String input = currentStep.getTopContext().getInput();
            control.queueNext(new BuildContexts.Continuation(input, next, modifiers, originalSource, filtered));
        }
    }
}
