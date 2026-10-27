// 바닐라 TriggerCommand: /trigger <objective> [add|set <value>] — trigger 기준 목표의 열린(잠기지 않은) 점수만 바꾸고 다시 잠근다.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.SharedSuggestions;
import org.domi.furnace.server.command.arguments.ScoreboardArguments;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.command.brigadier.Suggestions;
import org.domi.furnace.server.command.brigadier.SuggestionsBuilder;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.scores.Objective;
import org.domi.furnace.server.scores.ObjectiveCriteria;
import org.domi.furnace.server.scores.Score;
import org.domi.furnace.server.scores.ScoreAccess;
import org.domi.furnace.server.scores.ScoreHolder;
import org.domi.furnace.server.scores.Scoreboard;

public final class TriggerCommand {

    private TriggerCommand() {}

    private static final SimpleCommandExceptionType ERROR_NOT_PRIMED = new SimpleCommandExceptionType(Text.translatable("commands.trigger.failed.unprimed"));
    private static final SimpleCommandExceptionType ERROR_INVALID_OBJECTIVE = new SimpleCommandExceptionType(Text.translatable("commands.trigger.failed.invalid"));

    public static void register(CommandDispatcher<CommandSource> d) {
        d.register(literal("trigger")
                .then(argument("objective", ScoreboardArguments.objective())
                        .suggests((c, b) -> suggestObjectives(c.getSource(), b))
                        .executes(c -> simpleTrigger(c.getSource(), c.getSource().getPlayerOrException(), ScoreboardArguments.getObjective(c, "objective")))
                        .then(literal("add").then(argument("value", Arguments.integer()).executes(c -> addValue(c.getSource(),
                                c.getSource().getPlayerOrException(), ScoreboardArguments.getObjective(c, "objective"), c.getArgument("value", Integer.class)))))
                        .then(literal("set").then(argument("value", Arguments.integer()).executes(c -> setValue(c.getSource(),
                                c.getSource().getPlayerOrException(), ScoreboardArguments.getObjective(c, "objective"), c.getArgument("value", Integer.class)))))));
    }

    /** 실행 주체 엔티티가 열어 둔(잠기지 않은) trigger 목표들. */
    public static CompletableFuture<Suggestions> suggestObjectives(CommandSource source, SuggestionsBuilder builder) {
        List<String> out = new ArrayList<>();
        if (source.entity() != null) {
            ScoreHolder holder = ScoreHolder.of(source.entity());
            Scoreboard board = source.getServer().scoreboard();
            if (board != null) for (Objective o : board.getObjectives()) {
                if (o.criteria() == ObjectiveCriteria.TRIGGER) {
                    Score info = board.getPlayerScoreInfo(holder, o);
                    if (info != null && !info.isLocked()) out.add(o.name());
                }
            }
        }
        return SharedSuggestions.suggest(out, builder);
    }

    private static int addValue(CommandSource source, Player player, Objective objective, int amount) throws CommandSyntaxException {
        ScoreAccess score = getScore(source.getServer().scoreboard(), ScoreHolder.of(player), objective);
        int v = score.add(amount);
        source.sendSuccess(() -> Text.translatable("commands.trigger.add.success", objective.formattedDisplayName(), amount), true);
        return v;
    }

    private static int setValue(CommandSource source, Player player, Objective objective, int amount) throws CommandSyntaxException {
        ScoreAccess score = getScore(source.getServer().scoreboard(), ScoreHolder.of(player), objective);
        score.set(amount);
        source.sendSuccess(() -> Text.translatable("commands.trigger.set.success", objective.formattedDisplayName(), amount), true);
        return amount;
    }

    private static int simpleTrigger(CommandSource source, Player player, Objective objective) throws CommandSyntaxException {
        ScoreAccess score = getScore(source.getServer().scoreboard(), ScoreHolder.of(player), objective);
        int v = score.add(1);
        source.sendSuccess(() -> Text.translatable("commands.trigger.simple.success", objective.formattedDisplayName()), true);
        return v;
    }

    /** trigger 목표가 아니면 invalid, 점수가 없거나 잠겼으면 unprimed. 통과하면 다시 잠근 손잡이. */
    private static ScoreAccess getScore(Scoreboard board, ScoreHolder holder, Objective objective) throws CommandSyntaxException {
        if (board == null) throw ERROR_NOT_PRIMED.create();
        if (objective.criteria() != ObjectiveCriteria.TRIGGER) throw ERROR_INVALID_OBJECTIVE.create();
        Score info = board.getPlayerScoreInfo(holder, objective);
        if (info == null || info.isLocked()) throw ERROR_NOT_PRIMED.create();
        ScoreAccess access = board.getOrCreatePlayerScore(holder, objective);
        access.lock();
        return access;
    }
}
