// 바닐라 FunctionCommand(amy) · ReturnCommand(anx) · ExecuteCommand 의 if|unless function(amt.d, amt.a).
// 셋 다 큐를 직접 다루는 사용자 정의 실행기다(CustomCommandExecutor / CustomModifierExecutor) — 결과는 프레임 콜백으로 흐른다.
// 매크로 인자: function <name> <arguments> 와 function <name> with (block|entity|storage) <source> [<path>](S7).
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.function.IntPredicate;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.ComponentUtils;
import org.domi.furnace.server.command.EntityOps;
import org.domi.furnace.server.command.arguments.CompoundTagArgument;
import org.domi.furnace.server.command.arguments.NbtPathArgument;
import org.domi.furnace.server.command.brigadier.Dynamic2CommandExceptionType;
import org.domi.furnace.server.function.FunctionInstantiationException;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtTags;
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

    static final DynamicCommandExceptionType ERROR_ARGUMENT_NOT_COMPOUND =
            new DynamicCommandExceptionType(t -> Text.translatable("commands.function.error.argument_not_compound", t));
    static final Dynamic2CommandExceptionType ERROR_FUNCTION_INSTANTATION_FAILURE =
            new Dynamic2CommandExceptionType((id, msg) -> Text.translatable("commands.function.instantiationFailure", id, msg));
    static final Dynamic2CommandExceptionType ERROR_EXECUTE_INSTANTATION_FAILURE =
            new Dynamic2CommandExceptionType((id, msg) -> Text.translatable("commands.execute.function.instantiationFailure", id, msg));

    /** 함수 인자 compound(없으면 null). entity·block 은 region 왕복이라 future — 실패는 CommandSyntaxException 으로 완료된다. */
    @FunctionalInterface
    interface ArgumentsGetter {
        CompletableFuture<NbtCompound> get(CommandContext<CommandSource> ctx) throws CommandSyntaxException;
    }

    public static void registerFunction(CommandDispatcher<CommandSource> d) {
        var with = literal("with");
        for (DataCommands.DataProvider provider : DataCommands.SOURCE_PROVIDERS) {
            provider.wrap(with, b -> b.executes(new FunctionExecutor(c -> provider.with(c, DataCommands.DataAccessor::getData), true))
                    .then(argument("path", NbtPathArgument.nbtPath()).executes(new FunctionExecutor(c -> {
                        NbtPathArgument.NbtPath path = NbtPathArgument.getPath(c, "path");
                        return provider.with(c, a -> getArgumentTag(path, a));
                    }, true))));
        }
        d.register(literal("function").requires(s -> s.hasPermission(2))
                .then(argument("name", FunctionArgument.functions()).suggests(SUGGEST_FUNCTION)
                        .executes(new FunctionExecutor(c -> CompletableFuture.completedFuture(null), false))
                        .then(argument("arguments", CompoundTagArgument.compoundTag())
                                .executes(new FunctionExecutor(c -> CompletableFuture.completedFuture(CompoundTagArgument.getCompoundTag(c, "arguments")), false)))
                        .then(with)));
    }

    /** 바닐라 amy.a(fl.g, apg): 경로의 값이 하나여야 하고(data get 과 같은 오류) compound 여야 한다. */
    static NbtCompound getArgumentTag(NbtPathArgument.NbtPath path, DataCommands.DataAccessor accessor) throws CommandSyntaxException {
        Object tag = DataCommands.getSingleTag(path, accessor);
        if (tag instanceof NbtCompound c) return c;
        throw ERROR_ARGUMENT_NOT_COMPOUND.create(typeName(tag));
    }

    /** 바닐라 TagType.getName. */
    private static String typeName(Object tag) {
        return switch (NbtTags.typeOf(tag)) {
            case 1 -> "BYTE";
            case 2 -> "SHORT";
            case 3 -> "INT";
            case 4 -> "LONG";
            case 5 -> "FLOAT";
            case 6 -> "DOUBLE";
            case 7 -> "BYTE[]";
            case 8 -> "STRING";
            case 9 -> "LIST";
            case 10 -> "COMPOUND";
            case 11 -> "INT[]";
            case 12 -> "LONG[]";
            default -> "END";
        };
    }

    /** 바닐라 amy.a(et): 호출 소스는 출력 숨김 + 권한을 2 이상으로. */
    public static CommandSource modifySenderForExecution(CommandSource source) { return source.withSilent().withMaximumPermission(2); }

    /** 바닐라 amy.c: 함수 목록을 풀고 "Running function …" 을 먼저 알린 뒤 호출을 큐에 넣는다. */
    static final class FunctionExecutor extends CustomCommandExecutor.WithErrorHandling implements CustomCommandExecutor.CommandAdapter {
        private final ArgumentsGetter arguments;
        /** with block|entity|storage: 인자 읽기를 소스마다 항목 하나로 미룬다(아래 runGuarded 주석). */
        private final boolean deferred;

        FunctionExecutor(ArgumentsGetter arguments, boolean deferred) {
            this.arguments = arguments;
            this.deferred = deferred;
        }

        @Override
        protected void runGuarded(CommandSource source, ContextChain<CommandSource> chain, ChainModifiers modifiers, ExecutionControl control)
                throws CommandSyntaxException {
            if (!deferred) {
                resolveAndSchedule(source, chain, modifiers, control);
                return;
            }
            // with …: 바닐라 amy.c 는 소스마다 그 자리에서 인자를 읽고 호출을 넣는다(동기). region 왕복을 멈춤으로 기다리면 남은 소스의
            // Continuation 이 이 소스의 호출보다 앞에 놓여 순서가 뒤집히므로, 소스마다 항목을 넣어 그 항목 안에서 읽고·기다리고·호출한다.
            // 항목은 넣은 순서대로, 각 항목이 넣은 호출은 다음 항목보다 먼저 돈다 → 소스 순서와 "앞 소스의 함수가 끝난 뒤 다음 소스 읽기" 가 바닐라와 같다.
            control.queueNext((context, frame) -> {
                try {
                    resolveAndSchedule(source, chain, modifiers, control);
                } catch (CommandSyntaxException e) {
                    onError(e, source, modifiers, control);
                    source.callback().onFailure();
                }
            });
        }

        private void resolveAndSchedule(CommandSource source, ContextChain<CommandSource> chain, ChainModifiers modifiers, ExecutionControl control)
                throws CommandSyntaxException {
            CommandContext<CommandSource> ctx = chain.getTopContext().copyFor(source);
            FunctionArgument.Resolved r = FunctionArgument.getFunctionCollection(ctx, "name");
            Collection<CommandFunction> functions = r.functions();
            if (functions.isEmpty()) throw ERROR_NO_FUNCTIONS.create(r.id().toString());
            CompletableFuture<NbtCompound> args = arguments.get(ctx);
            if (args.isDone()) {
                NbtCompound a;
                try {
                    a = args.join();
                } catch (CompletionException | CancellationException e) {
                    Throwable t = EntityOps.unwrap(e);
                    if (t instanceof CommandSyntaxException cse) throw cse;
                    failUnexpected(source, t, modifiers, control);
                    return;
                }
                schedule(source, functions, a, modifiers, control);
                return;
            }
            // entity·block 인자: region 왕복을 기다렸다가 전역에서 이어 간다(오류는 hp.b 규칙: handleError → 소스 콜백 onFailure)
            control.context().suspend(args, (a, ex) -> {
                try {
                    if (ex != null) {
                        Throwable t = EntityOps.unwrap(ex);
                        if (t instanceof CommandSyntaxException cse) throw cse;
                        failUnexpected(source, t, modifiers, control);
                        return;
                    }
                    schedule(source, functions, a, modifiers, control);
                } catch (CommandSyntaxException e) {
                    onError(e, source, modifiers, control);
                    source.callback().onFailure();
                }
            });
        }

        /**
         * 문법 오류가 아닌 실패(region 왕복 타임아웃·예상 못한 예외) — 바닐라에는 없는 경로(인자 읽기가 동기).
         * 지연 결과(ExecuteCommand.onDeferredDone)와 같은 규칙: command.failed(타임아웃은 비포크일 때만) + 소스 콜백 onFailure,
         * 컨텍스트는 이어 간다. onFailure 를 불러야 execute store … run function … with entity 의 store 콜백이 빠지지 않는다.
         */
        private static void failUnexpected(CommandSource source, Throwable t, ChainModifiers modifiers, ExecutionControl control) {
            if (t instanceof TimeoutException) {
                if (!modifiers.isForked()) source.sendFailure(Text.translatable("command.failed"));
            } else {
                EntityOps.reportFailure(source, t);
            }
            CallbackAwait.then(control.context(), source.callback().onFailure(), () -> {});
        }

        private void schedule(CommandSource source, Collection<CommandFunction> functions, NbtCompound arguments, ChainModifiers modifiers,
                              ExecutionControl control) throws CommandSyntaxException {
            CommandSource callSource = modifySenderForExecution(source);
            if (functions.size() == 1) {
                source.sendSuccess(() -> Text.translatable("commands.function.scheduled.single", Text.text(functions.iterator().next().id().toString())), true);
            } else {
                List<Text> names = functions.stream().map(f -> Text.text(f.id().toString())).toList();
                source.sendSuccess(() -> Text.translatable("commands.function.scheduled.multiple", formatList(names)), true);
            }
            queueFunctions(functions, arguments, source, callSource, control, modifiers);
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
    public static void queueFunctions(Collection<CommandFunction> functions, NbtCompound arguments, CommandSource originalSource,
                                      CommandSource functionSource, ExecutionControl control, ChainModifiers modifiers) throws CommandSyntaxException {
        if (modifiers.isReturn()) queueFunctionsAsReturn(functions, arguments, originalSource, functionSource, control);
        else queueFunctionsNoReturn(functions, arguments, originalSource, functionSource, control);
    }

    /** 바닐라 amy.a(ub, ht, dispatcher, T, ig, akr, eq, boolean): 인스턴스화(실패는 instantiationFailure) 뒤 호출을 큐에 넣는다. */
    private static void instantiateAndQueue(NbtCompound arguments, ExecutionControl control, CommandSource source, CommandFunction function,
                                            ResultCallback callback, boolean returnParentFrame) throws CommandSyntaxException {
        CommandFunction instantiated;
        try {
            instantiated = function.instantiate(arguments, source.getServer().commands().getDispatcher());
        } catch (FunctionInstantiationException e) {
            throw ERROR_FUNCTION_INSTANTATION_FAILURE.create(Text.text(function.id().toString()), e.messageComponent());
        }
        control.queueNext(new CallFunction(instantiated, callback, returnParentFrame).bind(source));
    }

    /** 바닐라 amy.a(T, b, akr, eq): 원 소스가 조용하지 않으면 함수마다 "Function … returned …" 을 원 소스에 알린 뒤 콜백. */
    private static ResultCallback decorateOutputIfNeeded(CommandSource originalSource, ResourceId id, ResultCallback callback) {
        if (originalSource.isSilent()) return callback;
        return (success, result) -> {
            originalSource.sendSuccess(() -> Text.translatable("commands.function.result", Text.text(id.toString()), result), true);
            return callback.onResult(success, result);
        };
    }

    private static void queueFunctionsAsReturn(Collection<CommandFunction> functions, NbtCompound arguments, CommandSource originalSource,
                                               CommandSource functionSource, ExecutionControl control) throws CommandSyntaxException {
        CommandSource src = functionSource.clearCallbacks();
        ResultCallback functionCommandOutputCallback = ResultCallback.chain(originalSource.callback(), control.currentFrame().returnValueConsumer());
        for (CommandFunction f : functions) {
            ResultCallback cb = decorateOutputIfNeeded(originalSource, f.id(), functionCommandOutputCallback);
            instantiateAndQueue(arguments, control, src, f, cb, true);
        }
        control.queueNext(FallthroughTask.INSTANCE);
    }

    private static void queueFunctionsNoReturn(Collection<CommandFunction> functions, NbtCompound arguments, CommandSource originalSource,
                                               CommandSource functionSource, ExecutionControl control) throws CommandSyntaxException {
        CommandSource src = functionSource.clearCallbacks();
        ResultCallback functionCommandOutputCallback = originalSource.callback();
        if (functions.isEmpty()) return;
        if (functions.size() == 1) {
            CommandFunction f = functions.iterator().next();
            instantiateAndQueue(arguments, control, src, f, decorateOutputIfNeeded(originalSource, f.id(), functionCommandOutputCallback), false);
        } else if (functionCommandOutputCallback == ResultCallback.EMPTY) {
            for (CommandFunction f : functions) {
                instantiateAndQueue(arguments, control, src, f, decorateOutputIfNeeded(originalSource, f.id(), functionCommandOutputCallback), false);
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
                instantiateAndQueue(arguments, control, src, f, decorateOutputIfNeeded(originalSource, f.id(), partial), false);
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
                originalSource.handleError(e, modifiers.isForked(), control.tracer());
                return;
            }
            if (functions.isEmpty()) return;   // 바닐라: 아무것도 큐에 넣지 않는다(다음 단계도 없다)
            // 바닐라 amt.a: 인자 없이 인스턴스화. 실패하면 오류를 알리고 그때까지 된 것만으로 이어 간다(바닐라 그대로)
            List<CommandFunction> instantiated = new ArrayList<>(functions.size());
            try {
                for (CommandFunction f : functions) {
                    try {
                        instantiated.add(f.instantiate(null, originalSource.getServer().commands().getDispatcher()));
                    } catch (FunctionInstantiationException e) {
                        throw ERROR_EXECUTE_INSTANTATION_FAILURE.create(Text.text(f.id().toString()), e.messageComponent());
                    }
                }
            } catch (CommandSyntaxException e) {
                originalSource.handleError(e, modifiers.isForked(), control.tracer());
            }
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
