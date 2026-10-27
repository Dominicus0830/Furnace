// 바닐라 ScoreboardCommand — /scoreboard objectives add|list|modify|remove|setdisplay, players list|get|set|add|remove|reset|enable|operation|display.
// 차이: 점수 주인에 일반 엔티티 선택자가 있으면 해석이 나중에 끝나 반환값이 1 이고 결과는 메시지로 온다(context-notes "반환값 차이").
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.ComponentUtils;
import org.domi.furnace.server.command.SharedSuggestions;
import org.domi.furnace.server.command.arguments.ComponentArgument;
import org.domi.furnace.server.command.arguments.ScoreHolderArgument;
import org.domi.furnace.server.command.arguments.ScoreboardArguments;
import org.domi.furnace.server.command.brigadier.ArgumentBuilder;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.Dynamic2CommandExceptionType;
import org.domi.furnace.server.command.brigadier.LiteralArgumentBuilder;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.scores.DisplaySlot;
import org.domi.furnace.server.scores.NumberFormat;
import org.domi.furnace.server.scores.Objective;
import org.domi.furnace.server.scores.ObjectiveCriteria;
import org.domi.furnace.server.scores.Score;
import org.domi.furnace.server.scores.ScoreAccess;
import org.domi.furnace.server.scores.ScoreHolder;
import org.domi.furnace.server.scores.ServerScoreboard;

public final class ScoreboardCommand {

    private ScoreboardCommand() {}

    private static final SimpleCommandExceptionType ERROR_OBJECTIVE_ALREADY_EXISTS = err("commands.scoreboard.objectives.add.duplicate");
    private static final SimpleCommandExceptionType ERROR_DISPLAY_SLOT_ALREADY_EMPTY = err("commands.scoreboard.objectives.display.alreadyEmpty");
    private static final SimpleCommandExceptionType ERROR_DISPLAY_SLOT_ALREADY_SET = err("commands.scoreboard.objectives.display.alreadySet");
    private static final SimpleCommandExceptionType ERROR_TRIGGER_ALREADY_ENABLED = err("commands.scoreboard.players.enable.failed");
    private static final SimpleCommandExceptionType ERROR_NOT_TRIGGER = err("commands.scoreboard.players.enable.invalid");
    private static final Dynamic2CommandExceptionType ERROR_NO_VALUE =
            new Dynamic2CommandExceptionType((a, b) -> Text.translatable("commands.scoreboard.players.get.null", a, b));

    private static SimpleCommandExceptionType err(String key) { return new SimpleCommandExceptionType(Text.translatable(key)); }

    @FunctionalInterface
    private interface FormatRunner {
        int run(CommandContext<CommandSource> c, NumberFormat f) throws CommandSyntaxException;
    }

    @FunctionalInterface
    private interface HoldersAction {
        int run(CommandSource s, Collection<ScoreHolder> holders) throws CommandSyntaxException;
    }

    private static ServerScoreboard board(CommandSource s) { return s.getServer().scoreboard(); }

    private static int holders(CommandContext<CommandSource> c, String name, HoldersAction a) throws CommandSyntaxException {
        CommandSource s = c.getSource();
        return ScoreHolderArgument.await(s, ScoreHolderArgument.getNamesWithDefaultWildcard(c, name), h -> a.run(s, h));
    }

    private static int holder(CommandContext<CommandSource> c, String name, HoldersAction a) throws CommandSyntaxException {
        CommandSource s = c.getSource();
        return ScoreHolderArgument.await(s, ScoreHolderArgument.getName(c, name), h -> a.run(s, h));
    }

    public static void register(CommandDispatcher<CommandSource> d) {
        LiteralArgumentBuilder<CommandSource> renderType = literal("rendertype");
        for (ObjectiveCriteria.RenderType rt : ObjectiveCriteria.RenderType.values()) {
            renderType.then(literal(rt.id()).executes(c -> setRenderType(c.getSource(), ScoreboardArguments.getObjective(c, "objective"), rt)));
        }
        d.register(literal("scoreboard").requires(s -> s.hasPermission(2))
                .then(literal("objectives")
                        .then(literal("list").executes(c -> listObjectives(c.getSource())))
                        .then(literal("add").then(argument("objective", Arguments.word())
                                .then(argument("criteria", ScoreboardArguments.criteria())
                                        .executes(c -> addObjective(c.getSource(), c.getArgument("objective", String.class),
                                                c.getArgument("criteria", ObjectiveCriteria.class), Text.text(c.getArgument("objective", String.class))))
                                        .then(argument("displayName", ComponentArgument.textComponent())
                                                .executes(c -> addObjective(c.getSource(), c.getArgument("objective", String.class),
                                                        c.getArgument("criteria", ObjectiveCriteria.class), ComponentArgument.getComponent(c, "displayName")))))))
                        .then(literal("modify").then(argument("objective", ScoreboardArguments.objective())
                                .then(literal("displayname").then(argument("displayName", ComponentArgument.textComponent())
                                        .executes(c -> setDisplayName(c.getSource(), ScoreboardArguments.getObjective(c, "objective"),
                                                ComponentArgument.getComponent(c, "displayName")))))
                                .then(renderType)
                                .then(literal("displayautoupdate").then(argument("value", Arguments.bool())
                                        .executes(c -> setDisplayAutoUpdate(c.getSource(), ScoreboardArguments.getObjective(c, "objective"),
                                                c.getArgument("value", Boolean.class)))))
                                .then(addNumberFormats(literal("numberformat"), (c, f) ->
                                        setObjectiveFormat(c.getSource(), ScoreboardArguments.getObjective(c, "objective"), f)))))
                        .then(literal("remove").then(argument("objective", ScoreboardArguments.objective())
                                .executes(c -> removeObjective(c.getSource(), ScoreboardArguments.getObjective(c, "objective")))))
                        .then(literal("setdisplay").then(argument("slot", ScoreboardArguments.displaySlot())
                                .executes(c -> clearDisplaySlot(c.getSource(), c.getArgument("slot", DisplaySlot.class)))
                                .then(argument("objective", ScoreboardArguments.objective())
                                        .executes(c -> setDisplaySlot(c.getSource(), c.getArgument("slot", DisplaySlot.class),
                                                ScoreboardArguments.getObjective(c, "objective")))))))
                .then(literal("players")
                        .then(literal("list").executes(c -> listTrackedPlayers(c.getSource()))
                                .then(argument("target", ScoreHolderArgument.scoreHolder()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .executes(c -> holder(c, "target", (s, h) -> listTrackedPlayerScores(s, h.iterator().next())))))
                        .then(literal("set").then(argument("targets", ScoreHolderArgument.scoreHolders()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                .then(argument("objective", ScoreboardArguments.objective())
                                        .then(argument("score", Arguments.integer()).executes(c -> {
                                            Objective o = ScoreboardArguments.getWritableObjective(c, "objective");
                                            int v = c.getArgument("score", Integer.class);
                                            return holders(c, "targets", (s, h) -> setScore(s, h, o, v));
                                        })))))
                        .then(literal("get").then(argument("target", ScoreHolderArgument.scoreHolder()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                .then(argument("objective", ScoreboardArguments.objective()).executes(c -> {
                                    Objective o = ScoreboardArguments.getObjective(c, "objective");
                                    return holder(c, "target", (s, h) -> getScore(s, h.iterator().next(), o));
                                }))))
                        .then(literal("add").then(argument("targets", ScoreHolderArgument.scoreHolders()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                .then(argument("objective", ScoreboardArguments.objective())
                                        .then(argument("score", Arguments.integer(0)).executes(c -> {
                                            Objective o = ScoreboardArguments.getWritableObjective(c, "objective");
                                            int v = c.getArgument("score", Integer.class);
                                            return holders(c, "targets", (s, h) -> addScore(s, h, o, v));
                                        })))))
                        .then(literal("remove").then(argument("targets", ScoreHolderArgument.scoreHolders()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                .then(argument("objective", ScoreboardArguments.objective())
                                        .then(argument("score", Arguments.integer(0)).executes(c -> {
                                            Objective o = ScoreboardArguments.getWritableObjective(c, "objective");
                                            int v = c.getArgument("score", Integer.class);
                                            return holders(c, "targets", (s, h) -> removeScore(s, h, o, v));
                                        })))))
                        .then(literal("reset").then(argument("targets", ScoreHolderArgument.scoreHolders()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                .executes(c -> holders(c, "targets", ScoreboardCommand::resetScores))
                                .then(argument("objective", ScoreboardArguments.objective()).executes(c -> {
                                    Objective o = ScoreboardArguments.getObjective(c, "objective");
                                    return holders(c, "targets", (s, h) -> resetScore(s, h, o));
                                }))))
                        .then(literal("enable").then(argument("targets", ScoreHolderArgument.scoreHolders()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                .then(argument("objective", ScoreboardArguments.objective())
                                        .suggests((c, b) -> suggestTriggers(c, b))
                                        .executes(c -> {
                                            Objective o = ScoreboardArguments.getObjective(c, "objective");
                                            return holders(c, "targets", (s, h) -> enableTrigger(s, h, o));
                                        }))))
                        .then(literal("display")
                                .then(literal("name").then(argument("targets", ScoreHolderArgument.scoreHolders()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .then(argument("objective", ScoreboardArguments.objective())
                                                .then(argument("name", ComponentArgument.textComponent()).executes(c -> {
                                                    Objective o = ScoreboardArguments.getObjective(c, "objective");
                                                    Text n = ComponentArgument.getComponent(c, "name");
                                                    return holders(c, "targets", (s, h) -> setScoreDisplay(s, h, o, n));
                                                }))
                                                .executes(c -> {
                                                    Objective o = ScoreboardArguments.getObjective(c, "objective");
                                                    return holders(c, "targets", (s, h) -> setScoreDisplay(s, h, o, null));
                                                }))))
                                .then(literal("numberformat").then(argument("targets", ScoreHolderArgument.scoreHolders()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .then(addNumberFormats(argument("objective", ScoreboardArguments.objective()), (c, f) -> {
                                            Objective o = ScoreboardArguments.getObjective(c, "objective");
                                            return holders(c, "targets", (s, h) -> setScoreNumberFormat(s, h, o, f));
                                        })))))
                        .then(literal("operation").then(argument("targets", ScoreHolderArgument.scoreHolders()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                .then(argument("targetObjective", ScoreboardArguments.objective())
                                        .then(argument("operation", ScoreboardArguments.operation())
                                                .then(argument("source", ScoreHolderArgument.scoreHolders()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                                        .then(argument("sourceObjective", ScoreboardArguments.objective()).executes(c -> {
                                                            Objective to = ScoreboardArguments.getWritableObjective(c, "targetObjective");
                                                            ScoreAccess.Operation op = c.getArgument("operation", ScoreAccess.Operation.class);
                                                            Objective so = ScoreboardArguments.getObjective(c, "sourceObjective");
                                                            CompletableFuture<Collection<ScoreHolder>> t = ScoreHolderArgument.getNamesWithDefaultWildcard(c, "targets");
                                                            CompletableFuture<Collection<ScoreHolder>> src = ScoreHolderArgument.getNamesWithDefaultWildcard(c, "source");
                                                            CommandSource s = c.getSource();
                                                            return ScoreHolderArgument.await(s, t.thenCombine(src, List::of),
                                                                    both -> performOperation(s, both.get(0), to, op, both.get(1), so));
                                                        })))))))));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends ArgumentBuilder<CommandSource, T>> T addNumberFormats(T b, FormatRunner r) {
        return (T) b.then(literal("blank").executes(c -> r.run(c, NumberFormat.BLANK)))
                .then(literal("fixed").then(argument("contents", ComponentArgument.textComponent())
                        .executes(c -> r.run(c, new NumberFormat.Fixed(ComponentArgument.getComponent(c, "contents"))))))
                .then(literal("styled").then(argument("style", ScoreboardArguments.style())
                        .executes(c -> r.run(c, new NumberFormat.Styled(c.getArgument("style", org.domi.furnace.api.text.Style.class))))))
                .executes(c -> r.run(c, null));
    }

    private static java.util.concurrent.CompletableFuture<org.domi.furnace.server.command.brigadier.Suggestions> suggestTriggers(
            CommandContext<CommandSource> c, org.domi.furnace.server.command.brigadier.SuggestionsBuilder b) {
        // 바닐라: 대상 중 하나라도 잠기지 않은(또는 점수 없는) trigger 목표. 대상이 아직 해석되지 않으면(엔티티 선택자) 모든 trigger.
        List<String> out = new ArrayList<>();
        ServerScoreboard board = board(c.getSource());
        Collection<ScoreHolder> holders;
        try {
            var f = ScoreHolderArgument.getNamesWithDefaultWildcard(c, "targets");
            holders = f.isDone() && !f.isCompletedExceptionally() ? f.join() : null;
        } catch (CommandSyntaxException | RuntimeException e) {
            holders = null;
        }
        for (Objective o : board.getObjectives()) {
            if (o.criteria() != ObjectiveCriteria.TRIGGER) continue;
            boolean ok = holders == null;
            if (holders != null) {
                for (ScoreHolder h : holders) {
                    Score sc = board.getPlayerScoreInfo(h, o);
                    if (sc == null || sc.isLocked()) {
                        ok = true;
                        break;
                    }
                }
            }
            if (ok) out.add(o.name());
        }
        return SharedSuggestions.suggest(out, b);
    }

    private static Text firstName(Collection<ScoreHolder> h) { return h.iterator().next().feedbackDisplayName(); }

    private static Text formatList(List<Text> items) {
        Text out = Text.empty();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) out = out.append(ComponentUtils.defaultSeparator());
            out = out.append(items.get(i));
        }
        return out;
    }

    static int getScore(CommandSource s, ScoreHolder h, Objective o) throws CommandSyntaxException {
        Score sc = board(s).getPlayerScoreInfo(h, o);
        if (sc == null) throw ERROR_NO_VALUE.create(o.name(), h.feedbackDisplayName());
        int v = sc.value();
        s.sendSuccess(() -> Text.translatable("commands.scoreboard.players.get.success", h.feedbackDisplayName(), v, o.formattedDisplayName()), false);
        return v;
    }

    static int performOperation(CommandSource s, Collection<ScoreHolder> targets, Objective to, ScoreAccess.Operation op,
                                Collection<ScoreHolder> sources, Objective so) throws CommandSyntaxException {
        ServerScoreboard b = board(s);
        int total = 0;
        for (ScoreHolder t : targets) {
            ScoreAccess ta = b.getOrCreatePlayerScore(t, to);
            for (ScoreHolder src : sources) op.apply(ta, b.getOrCreatePlayerScore(src, so));
            total += ta.get();
        }
        int sum = total;
        if (targets.size() == 1) {
            s.sendSuccess(() -> Text.translatable("commands.scoreboard.players.operation.success.single", to.formattedDisplayName(), firstName(targets), sum), true);
        } else {
            s.sendSuccess(() -> Text.translatable("commands.scoreboard.players.operation.success.multiple", to.formattedDisplayName(), targets.size()), true);
        }
        return total;
    }

    static int enableTrigger(CommandSource s, Collection<ScoreHolder> targets, Objective o) throws CommandSyntaxException {
        if (o.criteria() != ObjectiveCriteria.TRIGGER) throw ERROR_NOT_TRIGGER.create();
        ServerScoreboard b = board(s);
        int n = 0;
        for (ScoreHolder h : targets) {
            ScoreAccess a = b.getOrCreatePlayerScore(h, o);
            if (a.locked()) {
                a.unlock();
                n++;
            }
        }
        if (n == 0) throw ERROR_TRIGGER_ALREADY_ENABLED.create();
        if (targets.size() == 1) s.sendSuccess(() -> Text.translatable("commands.scoreboard.players.enable.success.single", o.formattedDisplayName(), firstName(targets)), true);
        else s.sendSuccess(() -> Text.translatable("commands.scoreboard.players.enable.success.multiple", o.formattedDisplayName(), targets.size()), true);
        return n;
    }

    static int resetScores(CommandSource s, Collection<ScoreHolder> targets) {
        ServerScoreboard b = board(s);
        for (ScoreHolder h : targets) b.resetAllPlayerScores(h);
        if (targets.size() == 1) s.sendSuccess(() -> Text.translatable("commands.scoreboard.players.reset.all.single", firstName(targets)), true);
        else s.sendSuccess(() -> Text.translatable("commands.scoreboard.players.reset.all.multiple", targets.size()), true);
        return targets.size();
    }

    static int resetScore(CommandSource s, Collection<ScoreHolder> targets, Objective o) {
        ServerScoreboard b = board(s);
        for (ScoreHolder h : targets) b.resetSinglePlayerScore(h, o);
        if (targets.size() == 1) s.sendSuccess(() -> Text.translatable("commands.scoreboard.players.reset.specific.single", o.formattedDisplayName(), firstName(targets)), true);
        else s.sendSuccess(() -> Text.translatable("commands.scoreboard.players.reset.specific.multiple", o.formattedDisplayName(), targets.size()), true);
        return targets.size();
    }

    static int setScore(CommandSource s, Collection<ScoreHolder> targets, Objective o, int value) {
        ServerScoreboard b = board(s);
        for (ScoreHolder h : targets) b.getOrCreatePlayerScore(h, o).set(value);
        if (targets.size() == 1) s.sendSuccess(() -> Text.translatable("commands.scoreboard.players.set.success.single", o.formattedDisplayName(), firstName(targets), value), true);
        else s.sendSuccess(() -> Text.translatable("commands.scoreboard.players.set.success.multiple", o.formattedDisplayName(), targets.size(), value), true);
        return value * targets.size();
    }

    static int setScoreDisplay(CommandSource s, Collection<ScoreHolder> targets, Objective o, Text name) {
        ServerScoreboard b = board(s);
        for (ScoreHolder h : targets) b.getOrCreatePlayerScore(h, o).display(name);
        if (name == null) {
            if (targets.size() == 1) s.sendSuccess(() -> Text.translatable("commands.scoreboard.players.display.name.clear.success.single", firstName(targets), o.formattedDisplayName()), true);
            else s.sendSuccess(() -> Text.translatable("commands.scoreboard.players.display.name.clear.success.multiple", targets.size(), o.formattedDisplayName()), true);
        } else if (targets.size() == 1) {
            s.sendSuccess(() -> Text.translatable("commands.scoreboard.players.display.name.set.success.single", name, firstName(targets), o.formattedDisplayName()), true);
        } else {
            s.sendSuccess(() -> Text.translatable("commands.scoreboard.players.display.name.set.success.multiple", name, targets.size(), o.formattedDisplayName()), true);
        }
        return targets.size();
    }

    static int setScoreNumberFormat(CommandSource s, Collection<ScoreHolder> targets, Objective o, NumberFormat f) {
        ServerScoreboard b = board(s);
        for (ScoreHolder h : targets) b.getOrCreatePlayerScore(h, o).numberFormatOverride(f);
        String k = f == null ? "clear" : "set";
        if (targets.size() == 1) s.sendSuccess(() -> Text.translatable("commands.scoreboard.players.display.numberFormat." + k + ".success.single", firstName(targets), o.formattedDisplayName()), true);
        else s.sendSuccess(() -> Text.translatable("commands.scoreboard.players.display.numberFormat." + k + ".success.multiple", targets.size(), o.formattedDisplayName()), true);
        return targets.size();
    }

    static int addScore(CommandSource s, Collection<ScoreHolder> targets, Objective o, int amount) {
        ServerScoreboard b = board(s);
        int total = 0;
        for (ScoreHolder h : targets) {
            ScoreAccess a = b.getOrCreatePlayerScore(h, o);
            a.set(a.get() + amount);
            total += a.get();
        }
        int sum = total;
        if (targets.size() == 1) s.sendSuccess(() -> Text.translatable("commands.scoreboard.players.add.success.single", amount, o.formattedDisplayName(), firstName(targets), sum), true);
        else s.sendSuccess(() -> Text.translatable("commands.scoreboard.players.add.success.multiple", amount, o.formattedDisplayName(), targets.size()), true);
        return total;
    }

    static int removeScore(CommandSource s, Collection<ScoreHolder> targets, Objective o, int amount) {
        ServerScoreboard b = board(s);
        int total = 0;
        for (ScoreHolder h : targets) {
            ScoreAccess a = b.getOrCreatePlayerScore(h, o);
            a.set(a.get() - amount);
            total += a.get();
        }
        int sum = total;
        if (targets.size() == 1) s.sendSuccess(() -> Text.translatable("commands.scoreboard.players.remove.success.single", amount, o.formattedDisplayName(), firstName(targets), sum), true);
        else s.sendSuccess(() -> Text.translatable("commands.scoreboard.players.remove.success.multiple", amount, o.formattedDisplayName(), targets.size()), true);
        return total;
    }

    static int listTrackedPlayers(CommandSource s) {
        List<ScoreHolder> list = board(s).getTrackedPlayers();
        if (list.isEmpty()) {
            s.sendSuccess(() -> Text.translatable("commands.scoreboard.players.list.empty"), false);
        } else {
            List<Text> names = new ArrayList<>();
            for (ScoreHolder h : list) names.add(h.feedbackDisplayName());
            s.sendSuccess(() -> Text.translatable("commands.scoreboard.players.list.success", list.size(), formatList(names)), false);
        }
        return list.size();
    }

    static int listTrackedPlayerScores(CommandSource s, ScoreHolder h) {
        Map<Objective, Integer> scores = board(s).listPlayerScores(h);
        if (scores.isEmpty()) {
            s.sendSuccess(() -> Text.translatable("commands.scoreboard.players.list.entity.empty", h.feedbackDisplayName()), false);
        } else {
            s.sendSuccess(() -> Text.translatable("commands.scoreboard.players.list.entity.success", h.feedbackDisplayName(), scores.size()), false);
            scores.forEach((o, v) -> s.sendSuccess(() -> Text.translatable("commands.scoreboard.players.list.entity.entry", o.formattedDisplayName(), v), false));
        }
        return scores.size();
    }

    static int clearDisplaySlot(CommandSource s, DisplaySlot slot) throws CommandSyntaxException {
        ServerScoreboard b = board(s);
        if (b.getDisplayObjective(slot) == null) throw ERROR_DISPLAY_SLOT_ALREADY_EMPTY.create();
        b.setDisplayObjective(slot, null);
        s.sendSuccess(() -> Text.translatable("commands.scoreboard.objectives.display.cleared", slot.key()), true);
        return 0;
    }

    static int setDisplaySlot(CommandSource s, DisplaySlot slot, Objective o) throws CommandSyntaxException {
        ServerScoreboard b = board(s);
        if (b.getDisplayObjective(slot) == o) throw ERROR_DISPLAY_SLOT_ALREADY_SET.create();
        b.setDisplayObjective(slot, o);
        s.sendSuccess(() -> Text.translatable("commands.scoreboard.objectives.display.set", slot.key(), o.displayName()), true);
        return 0;
    }

    static int setDisplayName(CommandSource s, Objective o, Text name) {
        if (!o.displayName().equals(name)) {
            o.displayName(name);
            s.sendSuccess(() -> Text.translatable("commands.scoreboard.objectives.modify.displayname", o.name(), o.formattedDisplayName()), true);
        }
        return 0;
    }

    static int setDisplayAutoUpdate(CommandSource s, Objective o, boolean v) {
        if (o.displayAutoUpdate() != v) {
            o.displayAutoUpdate(v);
            String k = v ? "enable" : "disable";
            s.sendSuccess(() -> Text.translatable("commands.scoreboard.objectives.modify.displayAutoUpdate." + k, o.name(), o.formattedDisplayName()), true);
        }
        return 0;
    }

    static int setObjectiveFormat(CommandSource s, Objective o, NumberFormat f) {
        o.numberFormat(f);
        if (f != null) s.sendSuccess(() -> Text.translatable("commands.scoreboard.objectives.modify.objectiveFormat.set", o.name()), true);
        else s.sendSuccess(() -> Text.translatable("commands.scoreboard.objectives.modify.objectiveFormat.clear", o.name()), true);
        return 0;
    }

    static int setRenderType(CommandSource s, Objective o, ObjectiveCriteria.RenderType rt) {
        if (o.renderType() != rt) {
            o.renderType(rt);
            s.sendSuccess(() -> Text.translatable("commands.scoreboard.objectives.modify.rendertype", o.formattedDisplayName()), true);
        }
        return 0;
    }

    static int removeObjective(CommandSource s, Objective o) {
        ServerScoreboard b = board(s);
        b.removeObjective(o);
        s.sendSuccess(() -> Text.translatable("commands.scoreboard.objectives.remove.success", o.formattedDisplayName()), true);
        return b.getObjectives().size();
    }

    static int addObjective(CommandSource s, String name, ObjectiveCriteria criteria, Text displayName) throws CommandSyntaxException {
        ServerScoreboard b = board(s);
        if (b.getObjective(name) != null) throw ERROR_OBJECTIVE_ALREADY_EXISTS.create();
        b.addObjective(name, criteria, displayName, criteria.defaultRenderType(), false, null);
        Objective o = b.getObjective(name);
        s.sendSuccess(() -> Text.translatable("commands.scoreboard.objectives.add.success", o.formattedDisplayName()), true);
        return b.getObjectives().size();
    }

    static int listObjectives(CommandSource s) {
        List<Objective> list = board(s).getObjectives();
        if (list.isEmpty()) {
            s.sendSuccess(() -> Text.translatable("commands.scoreboard.objectives.list.empty"), false);
        } else {
            List<Text> names = new ArrayList<>();
            for (Objective o : list) names.add(o.formattedDisplayName());
            s.sendSuccess(() -> Text.translatable("commands.scoreboard.objectives.list.success", list.size(), formatList(names)), false);
        }
        return list.size();
    }
}
