// 바닐라 JfrCommand: /jfr start | stop — JFR 녹화를 시작하고, 멈추면 debug/ 에 덤프한 경로를 알린다.
// 바닐라처럼 JFR 을 쓸 수 있는 JVM 에서만 등록한다(JvmProfiler.isAvailable). 녹화 내용의 차이는 profiling/JfrProfiler 머리말.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.literal;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.domi.furnace.api.text.ClickEvent;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.text.TextDecoration;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.DynamicCommandExceptionType;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.profiling.JfrProfiler;

public final class JfrCommand {
    private JfrCommand() { }

    private static final SimpleCommandExceptionType START_FAILED = new SimpleCommandExceptionType(Text.translatable("commands.jfr.start.failed"));
    private static final DynamicCommandExceptionType DUMP_FAILED = new DynamicCommandExceptionType(m -> Text.translatable("commands.jfr.dump.failed", m));

    /** JFR 을 못 쓰는 JVM 이면 바닐라처럼 등록하지 않는다. */
    public static void register(CommandDispatcher<CommandSource> d) {
        if (!JfrProfiler.isAvailable()) return;
        d.register(literal("jfr").requires(s -> s.hasPermission(4))
                .then(literal("start").executes(c -> start(c.getSource())))
                .then(literal("stop").executes(c -> stop(c.getSource()))));
    }

    private static int start(CommandSource source) throws CommandSyntaxException {
        if (!JfrProfiler.instance().start()) throw START_FAILED.create();
        source.sendSuccess(() -> Text.translatable("commands.jfr.started"), false);
        return 1;
    }

    private static int stop(CommandSource source) throws CommandSyntaxException {
        try {
            // 바닐라: 현재 디렉터리 기준 상대 경로. 전용 서버(IDE 실행이 아님)는 클릭 동작에도 상대 경로를 쓴다.
            Path relative = Paths.get(".").relativize(JfrProfiler.instance().stop().normalize());
            String shown = relative.toString();
            Text path = Text.text(shown).decoration(TextDecoration.UNDERLINED, true)
                    .clickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, shown))
                    .hoverText(Text.translatable("chat.copy.click"));
            source.sendSuccess(() -> Text.translatable("commands.jfr.stopped", path), false);
            return 1;
        } catch (Throwable t) {
            throw DUMP_FAILED.create(t.getMessage());
        }
    }
}
