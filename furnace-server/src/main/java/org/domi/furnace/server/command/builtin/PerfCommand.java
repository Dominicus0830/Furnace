// 바닐라 PerfCommand: /perf start | stop (권한 4) — 10초(또는 stop 까지) 성능 표본을 모아 debug/profiling 에 zip 보고서를 남긴다.
// 표본·보고서 내용의 차이는 profiling/PerfRecorder 머리말.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.literal;

import java.nio.file.Path;
import java.util.Locale;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.profiling.PerfRecorder;

public final class PerfCommand {
    private PerfCommand() { }

    private static final SimpleCommandExceptionType NOT_RUNNING = new SimpleCommandExceptionType(Text.translatable("commands.perf.notRunning"));
    private static final SimpleCommandExceptionType ALREADY_RUNNING = new SimpleCommandExceptionType(Text.translatable("commands.perf.alreadyRunning"));

    public static void register(CommandDispatcher<CommandSource> d) {
        d.register(literal("perf").requires(s -> s.hasPermission(4))
                .then(literal("start").executes(c -> start(c.getSource())))
                .then(literal("stop").executes(c -> stop(c.getSource()))));
    }

    private static int start(CommandSource source) throws CommandSyntaxException {
        ServerCore core = source.getServer();
        PerfRecorder rec = core.perfRecorder();
        boolean started = rec.start(r -> whenStopped(source, r), path -> saved(source, path), e -> source.sendFailure(Text.translatable("commands.perf.reportFailed")));
        if (!started) throw ALREADY_RUNNING.create();
        source.sendSuccess(() -> Text.translatable("commands.perf.started"), false);
        return 0;
    }

    private static int stop(CommandSource source) throws CommandSyntaxException {
        PerfRecorder rec = source.getServer().perfRecorder();
        if (!rec.isRecording()) throw NOT_RUNNING.create();
        rec.stop();
        return 0;
    }

    /** 바닐라 whenStopped: 걸린 초·틱 수·초당 틱. */
    static void whenStopped(CommandSource source, PerfRecorder.Result r) {
        String seconds = String.format(Locale.ROOT, "%.2f", r.seconds());
        String tps = String.format(Locale.ROOT, "%.2f", r.ticksPerSecond());
        source.sendSuccess(() -> Text.translatable("commands.perf.stopped", seconds, String.valueOf(r.ticks()), tps), false);
    }

    private static void saved(CommandSource source, Path path) {
        String shown = Path.of(".").toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
        source.sendSuccess(() -> Text.translatable("commands.perf.reportSaved", shown), false);
    }
}
