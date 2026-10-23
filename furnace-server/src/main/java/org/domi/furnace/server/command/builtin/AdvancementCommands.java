// /advancement grant|revoke <targets> only|from|until|through <advancement> [criterion] | everything (바닐라 AdvancementCommands).
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.advancement.Advancement;
import org.domi.furnace.server.advancement.AdvancementManager;
import org.domi.furnace.server.advancement.AdvancementNode;
import org.domi.furnace.server.advancement.AdvancementProgress;
import org.domi.furnace.server.advancement.PlayerAdvancements;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.SharedSuggestions;
import org.domi.furnace.server.command.arguments.EntityArgument;
import org.domi.furnace.server.command.arguments.ResourceLocationArgument;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.Dynamic2CommandExceptionType;
import org.domi.furnace.server.command.brigadier.DynamicCommandExceptionType;
import org.domi.furnace.server.command.brigadier.LiteralArgumentBuilder;
import org.domi.furnace.server.command.brigadier.SuggestionProvider;
import org.domi.furnace.server.entity.Player;

public final class AdvancementCommands {

    private static final DynamicCommandExceptionType ERROR_UNKNOWN = new DynamicCommandExceptionType(id -> Text.translatable("advancement.advancementNotFound", id));
    private static final Dynamic2CommandExceptionType ERROR_NO_CRITERION =
            new Dynamic2CommandExceptionType((adv, c) -> Text.translatable("commands.advancement.criterionNotFound", adv, c));

    private static final SuggestionProvider<CommandSource> SUGGEST_ADVANCEMENTS = (c, b) -> {
        AdvancementManager m = manager(c.getSource());
        return SharedSuggestions.suggestResource(m == null ? List.of() : m.all().stream().map(Advancement::id).toList(), b);
    };

    private enum Action { GRANT, REVOKE }

    /** 바닐라 Mode(부모, 자식 포함 여부). */
    private enum Mode {
        ONLY(false, false), THROUGH(true, true), FROM(false, true), UNTIL(true, false), EVERYTHING(true, true);

        final boolean parents, children;

        Mode(boolean parents, boolean children) {
            this.parents = parents;
            this.children = children;
        }
    }

    private AdvancementCommands() {}

    private static AdvancementManager manager(CommandSource s) {
        var dp = s.getServer().dataPacks();
        return dp == null ? null : dp.advancements();
    }

    public static void register(CommandDispatcher<CommandSource> d) {
        d.register(literal("advancement").requires(s -> s.hasPermission(2))
                .then(branch(Action.GRANT))
                .then(branch(Action.REVOKE)));
    }

    private static LiteralArgumentBuilder<CommandSource> branch(Action action) {
        return literal(action == Action.GRANT ? "grant" : "revoke").then(argument("targets", EntityArgument.players())
                .then(literal("only").then(argument("advancement", ResourceLocationArgument.id()).suggests(SUGGEST_ADVANCEMENTS)
                        .executes(c -> perform(c, action, Mode.ONLY))
                        .then(argument("criterion", Arguments.greedyString())
                                .suggests((c, b) -> SharedSuggestions.suggest(advancement(c).criteria().keySet(), b))
                                .executes(c -> performCriterion(c, action, c.getArgument("criterion", String.class))))))
                .then(literal("from").then(argument("advancement", ResourceLocationArgument.id()).suggests(SUGGEST_ADVANCEMENTS)
                        .executes(c -> perform(c, action, Mode.FROM))))
                .then(literal("until").then(argument("advancement", ResourceLocationArgument.id()).suggests(SUGGEST_ADVANCEMENTS)
                        .executes(c -> perform(c, action, Mode.UNTIL))))
                .then(literal("through").then(argument("advancement", ResourceLocationArgument.id()).suggests(SUGGEST_ADVANCEMENTS)
                        .executes(c -> perform(c, action, Mode.THROUGH))))
                .then(literal("everything").executes(c -> performAll(c, action))));
    }

    private static Advancement advancement(CommandContext<CommandSource> c) throws CommandSyntaxException {
        var id = ResourceLocationArgument.getId(c, "advancement");
        AdvancementManager m = manager(c.getSource());
        Advancement a = m == null ? null : m.get(id);
        if (a == null) throw ERROR_UNKNOWN.create(id.toString());
        return a;
    }

    /** 바닐라 getAdvancements — 부모(가까운 것부터 뿌리까지) → 자신 → 자손(전위 순회). */
    private static List<Advancement> select(AdvancementManager m, Advancement a, Mode mode) {
        AdvancementNode node = m.node(a.id());
        if (node == null) return List.of(a);
        List<Advancement> list = new ArrayList<>();
        if (mode.parents) for (AdvancementNode p = node.parent(); p != null; p = p.parent()) list.add(p.advancement());
        list.add(a);
        if (mode.children) addChildren(node, list);
        return list;
    }

    private static void addChildren(AdvancementNode n, List<Advancement> out) {
        for (AdvancementNode c : n.children()) {
            out.add(c.advancement());
            addChildren(c, out);
        }
    }

    private static int perform(CommandContext<CommandSource> c, Action action, Mode mode) throws CommandSyntaxException {
        Advancement a = advancement(c);
        return performMany(c.getSource(), EntityArgument.getPlayers(c, "targets"), action, select(manager(c.getSource()), a, mode));
    }

    private static int performAll(CommandContext<CommandSource> c, Action action) throws CommandSyntaxException {
        AdvancementManager m = manager(c.getSource());
        return performMany(c.getSource(), EntityArgument.getPlayers(c, "targets"), action, m == null ? List.of() : new ArrayList<>(m.all()));
    }

    /** 바닐라 grant: 이미 달성이면 거짓, 아니면 남은 조건을 모두. revoke: 진행이 없으면 거짓, 아니면 달성한 조건을 모두. */
    private static boolean apply(PlayerAdvancements adv, Action action, Advancement a) {
        AdvancementProgress p = adv.getOrStartProgress(a);
        if (action == Action.GRANT) {
            if (p.isDone()) return false;
            for (String crit : p.remainingCriteria()) adv.award(a, crit);
        } else {
            if (!p.hasProgress()) return false;
            for (String crit : p.completedCriteria()) adv.revoke(a, crit);
        }
        return true;
    }

    private static int performMany(CommandSource source, Collection<Player> targets, Action action, List<Advancement> list) throws CommandSyntaxException {
        String key = "commands.advancement." + (action == Action.GRANT ? "grant" : "revoke");
        int count = 0;
        for (Player p : targets) {
            PlayerAdvancements adv = p.advancements();
            if (adv == null) continue;
            for (Advancement a : list) if (apply(adv, action, a)) count++;
        }
        Object targetArg = targets.size() == 1 ? targets.iterator().next().displayName() : targets.size();
        String who = targets.size() == 1 ? ".to.one" : ".to.many";
        if (count == 0) {
            if (list.size() == 1) throw new CommandSyntaxException(null, Text.translatable(key + ".one" + who + ".failure", PlayerAdvancements.name(list.get(0)), targetArg));
            throw new CommandSyntaxException(null, Text.translatable(key + ".many" + who + ".failure", list.size(), targetArg));
        }
        Text msg = list.size() == 1 ? Text.translatable(key + ".one" + who + ".success", PlayerAdvancements.name(list.get(0)), targetArg)
                : Text.translatable(key + ".many" + who + ".success", list.size(), targetArg);
        source.sendSuccess(() -> msg, true);
        return count;
    }

    private static int performCriterion(CommandContext<CommandSource> c, Action action, String criterion) throws CommandSyntaxException {
        Advancement a = advancement(c);
        if (!a.criteria().containsKey(criterion)) throw ERROR_NO_CRITERION.create(PlayerAdvancements.name(a), criterion);
        String key = "commands.advancement." + (action == Action.GRANT ? "grant" : "revoke");
        Collection<Player> targets = EntityArgument.getPlayers(c, "targets");
        int count = 0;
        for (Player p : targets) {
            PlayerAdvancements adv = p.advancements();
            if (adv == null) continue;
            if (action == Action.GRANT ? adv.award(a, criterion) : adv.revoke(a, criterion)) count++;
        }
        Object targetArg = targets.size() == 1 ? targets.iterator().next().displayName() : targets.size();
        String who = targets.size() == 1 ? ".criterion.to.one" : ".criterion.to.many";
        if (count == 0) throw new CommandSyntaxException(null, Text.translatable(key + who + ".failure", criterion, PlayerAdvancements.name(a), targetArg));
        c.getSource().sendSuccess(() -> Text.translatable(key + who + ".success", criterion, PlayerAdvancements.name(a), targetArg), true);
        return count;
    }
}
