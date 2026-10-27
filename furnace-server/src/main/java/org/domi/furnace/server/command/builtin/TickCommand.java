// 바닐라 TickCommand 중 서버 시계 구조로 가능한 것: /tick query | rate <속도> | freeze | unfreeze | step [시간|stop].
// 없는 것: /tick sprint — Furnace 는 region 마다 따로 틱해 "N 틱을 가능한 빨리 돌리고 끝난 시각을 보고"할 한 줄기 서버 틱이 없다
// (TickState 설명 참고). 속도·정지·step 의 적용 방식과 바닐라와의 차이는 world/TickState.java 머리말에 있다.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.Arrays;
import java.util.Locale;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.SharedSuggestions;
import org.domi.furnace.server.command.arguments.TimeArgument;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.world.TickState;
import org.domi.furnace.server.world.World;

public final class TickCommand {
    private TickCommand() { }

    private static final long NANOS_PER_MILLISECOND = 1_000_000L;

    public static void register(CommandDispatcher<CommandSource> d) {
        d.register(literal("tick").requires(s -> s.hasPermission(3))
                .then(literal("query").executes(c -> tickQuery(c.getSource())))
                .then(literal("rate").then(argument("rate", Arguments.floatArg(1.0F, 10000.0F))
                        .suggests((c, b) -> SharedSuggestions.suggest(new String[] {String.valueOf(20)}, b))
                        .executes(c -> setTickingRate(c.getSource(), c.getArgument("rate", Float.class)))))
                .then(literal("step").executes(c -> step(c.getSource(), 1))
                        .then(literal("stop").executes(c -> stopStepping(c.getSource())))
                        .then(argument("time", TimeArgument.time(1))
                                .suggests((c, b) -> SharedSuggestions.suggest(new String[] {"1t", "1s"}, b))
                                .executes(c -> step(c.getSource(), c.getArgument("time", Integer.class)))))
                .then(literal("unfreeze").executes(c -> setFreeze(c.getSource(), false)))
                .then(literal("freeze").executes(c -> setFreeze(c.getSource(), true))));
    }

    /** String.format("%.1f", (float) nanos / (float) 1_000_000) — 밀리초 한 자리. */
    static String nanosToMillis(long nanos) { return String.format(Locale.ROOT, "%.1f", (float) nanos / (float) NANOS_PER_MILLISECOND); }

    static int setTickingRate(CommandSource source, float rate) {
        ServerCore core = source.getServer();
        TickState ts = core.tickState();
        ts.setRate(rate);
        for (World w : core.worlds()) w.setTickNanos(ts.nanosPerTick());
        core.broadcastTickState();
        String shown = String.format(Locale.ROOT, "%.1f", rate);
        source.sendSuccess(() -> Text.translatable("commands.tick.rate.success", shown), true);
        return (int) rate;
    }

    static int tickQuery(CommandSource source) {
        TickState ts = source.getServer().tickState();
        String rate = String.format(Locale.ROOT, "%.1f", ts.rate());
        long average = ts.averageTickNanos();
        String averageMs = nanosToMillis(average);
        if (ts.frozen()) {
            source.sendSuccess(() -> Text.translatable("commands.tick.status.frozen"), false);
        } else if (ts.nanosPerTick() < average) {
            source.sendSuccess(() -> Text.translatable("commands.tick.status.lagging"), false);
        } else {
            source.sendSuccess(() -> Text.translatable("commands.tick.status.running"), false);
        }
        String target = nanosToMillis(ts.nanosPerTick());
        source.sendSuccess(() -> Text.translatable("commands.tick.query.rate.running", rate, averageMs, target), false);
        long[] sorted = ts.sampleCopy();
        Arrays.sort(sorted);
        String p50 = nanosToMillis(sorted[sorted.length / 2]);
        String p95 = nanosToMillis(sorted[(int) (sorted.length * 0.95)]);
        String p99 = nanosToMillis(sorted[(int) (sorted.length * 0.99)]);
        int count = sorted.length;
        source.sendSuccess(() -> Text.translatable("commands.tick.query.percentiles", p50, p95, p99, count), false);
        return (int) ts.rate();
    }

    static int setFreeze(CommandSource source, boolean freeze) {
        ServerCore core = source.getServer();
        core.tickState().setFrozen(freeze);
        core.broadcastTickState();
        core.broadcastTickStep(0);
        source.sendSuccess(() -> Text.translatable(freeze ? "commands.tick.status.frozen" : "commands.tick.status.running"), true);
        return freeze ? 1 : 0;
    }

    static int step(CommandSource source, int ticks) {
        ServerCore core = source.getServer();
        TickState ts = core.tickState();
        if (!ts.frozen()) {   // 바닐라 stepGameIfPaused: 정지 중이 아니면 실패
            source.sendFailure(Text.translatable("commands.tick.step.fail"));
        } else {
            ts.startStep(ticks);
            core.broadcastTickStep(ticks);
            source.sendSuccess(() -> Text.translatable("commands.tick.step.success", ticks), true);
        }
        return 1;
    }

    static int stopStepping(CommandSource source) {
        ServerCore core = source.getServer();
        if (core.tickState().cancelSteps()) {
            core.broadcastTickStep(0);
            source.sendSuccess(() -> Text.translatable("commands.tick.step.stop.success"), true);
            return 1;
        }
        source.sendFailure(Text.translatable("commands.tick.step.stop.fail"));
        return 0;
    }
}
