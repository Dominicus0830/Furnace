// 바닐라 DebugCommand: /debug start | stop — 틱 프로파일(시작부터 멈출 때까지의 시간·틱 수)을 알린다.
// 바닐라 1.21.1 의 stop 은 파일을 쓰지 않고 시간·틱 수·초당 틱만 메시지로 알린다. 시작은 다음 틱 머리에서 이뤄진다(ServerCore.beginServerTick).
// /debug function <이름>: 함수를 돌리며 명령·결과·함수 호출·오류를 debug/debug-trace-<날짜>.txt 에 적는다(바닐라 Tracer 형식).
// 추적 중 명령의 출력은 채팅이 아니라 파일의 "[M]" 줄로 간다(CommandSource.withTraceOutput).
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Locale;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.arguments.FunctionArgument;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.ContextChain;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.command.exec.CallFunction;
import org.domi.furnace.server.command.exec.ChainModifiers;
import org.domi.furnace.server.command.exec.CustomCommandExecutor;
import org.domi.furnace.server.command.exec.ExecutionContext;
import org.domi.furnace.server.command.exec.ExecutionControl;
import org.domi.furnace.server.command.exec.Frame;
import org.domi.furnace.server.command.exec.ResultCallback;
import org.domi.furnace.server.command.exec.TraceCallbacks;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.function.CommandFunction;
import org.domi.furnace.server.function.FunctionInstantiationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DebugCommand {
    private DebugCommand() { }

    private static final Logger log = LoggerFactory.getLogger(DebugCommand.class);
    private static final SimpleCommandExceptionType ERROR_NOT_RUNNING = new SimpleCommandExceptionType(Text.translatable("commands.debug.notRunning"));
    private static final SimpleCommandExceptionType ERROR_ALREADY_RUNNING = new SimpleCommandExceptionType(Text.translatable("commands.debug.alreadyRunning"));
    private static final SimpleCommandExceptionType ERROR_TRACE_FUNCTION_NO_RECURSION =
            new SimpleCommandExceptionType(Text.translatable("commands.debug.function.noRecursion"));
    private static final SimpleCommandExceptionType ERROR_CANT_RUN_RETURN_RUN =
            new SimpleCommandExceptionType(Text.translatable("commands.debug.function.noReturnRun"));
    /** 바닐라 Util.FILENAME_DATE_TIME_FORMATTER. */
    private static final DateTimeFormatter FILENAME_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss", Locale.ROOT);

    public static void register(CommandDispatcher<CommandSource> d) {
        d.register(literal("debug").requires(s -> s.hasPermission(3))
                .then(literal("start").executes(c -> start(c.getSource())))
                .then(literal("stop").executes(c -> stop(c.getSource())))
                .then(literal("function").requires(s -> s.hasPermission(3))
                        .then(argument("name", FunctionArgument.functions()).suggests(FunctionCommands.SUGGEST_FUNCTION)
                                .executes(new TraceFunction()))));
    }

    private static int start(CommandSource source) throws CommandSyntaxException {
        ServerCore core = source.getServer();
        if (core.isTickProfiling()) throw ERROR_ALREADY_RUNNING.create();
        core.requestTickProfile();
        source.sendSuccess(() -> Text.translatable("commands.debug.started"), true);
        return 0;
    }

    private static int stop(CommandSource source) throws CommandSyntaxException {
        ServerCore core = source.getServer();
        if (!core.isTickProfiling()) throw ERROR_NOT_RUNNING.create();
        long[] result = core.stopTickProfile();
        long nanos = result[0];
        int ticks = (int) result[1];
        double seconds = (double) nanos / 1.0E9;
        double ticksPerSecond = ticks / seconds;   // 시작 틱 전에 멈추면 0/0.0 = NaN → 반환 0, 메시지 "NaN"(바닐라와 같다)
        String shownSeconds = String.format(Locale.ROOT, "%.2f", seconds);
        String shownTps = String.format(Locale.ROOT, "%.2f", ticksPerSecond);
        source.sendSuccess(() -> Text.translatable("commands.debug.stopped", shownSeconds, ticks, shownTps), true);
        return (int) ticksPerSecond;
    }

    /** 바닐라 amk.a: 큐를 직접 다루는 실행기. 오류는 handleError → 소스 콜백 onFailure 규칙(WithErrorHandling). */
    static final class TraceFunction extends CustomCommandExecutor.WithErrorHandling implements CustomCommandExecutor.CommandAdapter {
        @Override
        protected void runGuarded(CommandSource source, ContextChain<CommandSource> chain, ChainModifiers modifiers, ExecutionControl control)
                throws CommandSyntaxException {
            if (modifiers.isReturn()) throw ERROR_CANT_RUN_RETURN_RUN.create();
            if (control.tracer() != null) throw ERROR_TRACE_FUNCTION_NO_RECURSION.create();
            Collection<CommandFunction> functions = FunctionArgument.getFunctions(chain.getTopContext().copyFor(source), "name");
            String fileName = "debug-trace-" + FILENAME_DATE_TIME.format(ZonedDateTime.now()) + ".txt";
            var dispatcher = source.getServer().commands().getDispatcher();
            int commandCount = 0;
            try {
                Path dir = Path.of("debug");
                Files.createDirectories(dir);
                PrintWriter out = new PrintWriter(Files.newBufferedWriter(dir.resolve(fileName), StandardCharsets.UTF_8));
                FileTracer tracer = new FileTracer(out);
                control.context().tracer(tracer);
                for (CommandFunction function : functions) {
                    try {
                        CommandSource traced = source.withTraceOutput(tracer::message).withMaximumPermission(2);
                        CommandFunction.PlainTextFunction instantiated = function.instantiate(null, dispatcher);
                        ResourceId id = function.id();
                        // 바닐라: 호출 항목이 돌 때 먼저 함수 id 한 줄을 적고 부른다
                        var call = new CallFunction(instantiated, ResultCallback.EMPTY, false);
                        control.queueNext((context, frame) -> {
                            out.println(id);
                            call.execute(traced, context, frame);
                        });
                        commandCount += instantiated.entries().size();
                    } catch (FunctionInstantiationException e) {
                        source.sendFailure(e.messageComponent());
                    }
                }
            } catch (IOException | UncheckedIOException e) {
                log.warn("Tracing failed", e);
                source.sendFailure(Text.translatable("commands.debug.function.traceFailed"));
            }
            int total = commandCount;
            control.queueNext((context, frame) -> {
                if (functions.size() == 1) {
                    source.sendSuccess(() -> Text.translatable("commands.debug.function.success.single", total,
                            Text.text(functions.iterator().next().id().toString()), fileName), true);
                } else {
                    source.sendSuccess(() -> Text.translatable("commands.debug.function.success.multiple", total, functions.size(), fileName), true);
                }
            });
        }
    }

    /** 바닐라 amk.b(Tracer): 추적 파일 쓰기. 들여쓰기는 깊이마다 4칸, 결과는 직전 [C] 줄 끝에 " -> n" 으로 잇는다. */
    static final class FileTracer implements TraceCallbacks {
        private final PrintWriter out;
        private int lastIndent;
        private boolean waitingResult;

        FileTracer(PrintWriter out) { this.out = out; }

        private void indentAndSave(int depth) {
            indent(depth);
            lastIndent = depth;
        }

        private void indent(int depth) {
            for (int i = 0; i < depth + 1; i++) out.write("    ");
        }

        private void newLine() {
            if (waitingResult) {
                out.println();
                waitingResult = false;
            }
        }

        @Override
        public void onCommand(int depth, String command) {
            newLine();
            indentAndSave(depth);
            out.print("[C] ");
            out.print(command);
            waitingResult = true;
        }

        @Override
        public void onReturn(int depth, String command, int result) {
            if (waitingResult) {
                out.print(" -> ");
                out.println(result);
                waitingResult = false;
            } else {
                indentAndSave(depth);
                out.print("[R = ");
                out.print(result);
                out.print("] ");
                out.println(command);
            }
        }

        @Override
        public void onCall(int depth, ResourceId function, int size) {
            newLine();
            indentAndSave(depth);
            out.print("[F] ");
            out.print(function);
            out.print(" size=");
            out.println(size);
        }

        /** 바닐라는 여기서 줄을 끝내지 않는다(다음 출력이 같은 줄에 이어질 수 있다). 그대로 따른다. */
        @Override
        public void onError(String message) {
            newLine();
            indentAndSave(lastIndent + 1);
            out.print("[E] ");
            out.print(message);
        }

        /** 추적 소스로 간 명령 출력(TracerSource.sendSystemMessage). */
        void message(Text message) {
            newLine();
            indent(lastIndent + 1);
            out.print("[M] ");
            out.println(message.plainText(Locale.US));
        }

        @Override
        public void close() { out.close(); }
    }
}
