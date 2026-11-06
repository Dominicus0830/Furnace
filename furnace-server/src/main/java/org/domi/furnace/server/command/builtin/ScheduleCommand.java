// 바닐라 ScheduleCommand(aod): /schedule function <함수|#태그> <시간> [append|replace], /schedule clear <이름>.
// 기준 시각은 서버 시계의 gameTime(모든 차원 공유 — region 틱이 아니다). /tick freeze 중에는 gameTime 이 멈추므로 같이 멈춘다.
// 큐는 ServerCore.scheduledEvents(바닐라는 오버월드 TimerQueue). 명령 본문은 전역 스레드에서 돈다.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.SharedSuggestions;
import org.domi.furnace.server.command.arguments.FunctionArgument;
import org.domi.furnace.server.command.arguments.TimeArgument;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.DynamicCommandExceptionType;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.command.brigadier.SuggestionProvider;
import org.domi.furnace.server.function.ScheduledEvents;

public final class ScheduleCommand {
    private ScheduleCommand() { }

    private static final SimpleCommandExceptionType ERROR_SAME_TICK = new SimpleCommandExceptionType(Text.translatable("commands.schedule.same_tick"));
    private static final DynamicCommandExceptionType ERROR_CANT_REMOVE =
            new DynamicCommandExceptionType(name -> Text.translatable("commands.schedule.cleared.failure", name));
    private static final SuggestionProvider<CommandSource> SUGGEST_SCHEDULE =
            (c, b) -> SharedSuggestions.suggest(c.getSource().getServer().scheduledEvents().names(), b);

    public static void register(CommandDispatcher<CommandSource> d) {
        d.register(literal("schedule").requires(s -> s.hasPermission(2))
                .then(literal("function")
                        .then(argument("function", FunctionArgument.functions()).suggests(FunctionCommands.SUGGEST_FUNCTION)
                                .then(argument("time", TimeArgument.time())
                                        .executes(c -> schedule(c, true))
                                        .then(literal("append").executes(c -> schedule(c, false)))
                                        .then(literal("replace").executes(c -> schedule(c, true))))))
                .then(literal("clear")
                        .then(argument("function", Arguments.greedyString()).suggests(SUGGEST_SCHEDULE)
                                .executes(c -> clear(c.getSource(), c.getArgument("function", String.class))))));
    }

    private static int schedule(CommandContext<CommandSource> c, boolean replace) throws CommandSyntaxException {
        int time = c.getArgument("time", Integer.class);
        if (time == 0) throw ERROR_SAME_TICK.create();
        CommandSource source = c.getSource();
        long trigger = source.getServer().clock().gameTime() + time;
        // 없는 함수는 인자 해석이 arguments.function.unknown 으로 막고, 태그는 빈 목록이어도 통과한다(바닐라와 같다)
        FunctionArgument.Resolved r = FunctionArgument.getFunctionCollection(c, "function");
        boolean tag = c.getArgument("function", FunctionArgument.Ref.class).tag();
        ScheduledEvents events = source.getServer().scheduledEvents();
        String name = (tag ? "#" : "") + r.id();
        if (replace) events.remove(name);
        events.schedule(name, trigger, new ScheduledEvents.Callback(tag, r.id()));
        source.sendSuccess(() -> Text.translatable(tag ? "commands.schedule.created.tag" : "commands.schedule.created.function",
                Text.text(r.id().toString()), time, trigger), true);
        return (int) Math.floorMod(trigger, (long) Integer.MAX_VALUE);
    }

    private static int clear(CommandSource source, String name) throws CommandSyntaxException {
        int n = source.getServer().scheduledEvents().remove(name);
        if (n == 0) throw ERROR_CANT_REMOVE.create(name);
        source.sendSuccess(() -> Text.translatable("commands.schedule.cleared.success", n, name), true);
        return n;
    }
}
