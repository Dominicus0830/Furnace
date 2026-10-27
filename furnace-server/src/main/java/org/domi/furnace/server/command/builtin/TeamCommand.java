// 바닐라 TeamCommand — /team list|add|remove|empty|join|leave|modify.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.ComponentUtils;
import org.domi.furnace.server.command.arguments.ComponentArgument;
import org.domi.furnace.server.command.arguments.ScoreHolderArgument;
import org.domi.furnace.server.command.arguments.ScoreboardArguments;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.LiteralArgumentBuilder;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.scores.ChatFormatting;
import org.domi.furnace.server.scores.PlayerTeam;
import org.domi.furnace.server.scores.ScoreHolder;
import org.domi.furnace.server.scores.ServerScoreboard;

public final class TeamCommand {

    private TeamCommand() {}

    private static final SimpleCommandExceptionType ERROR_TEAM_ALREADY_EXISTS = err("commands.team.add.duplicate");
    private static final SimpleCommandExceptionType ERROR_TEAM_ALREADY_EMPTY = err("commands.team.empty.unchanged");
    private static final SimpleCommandExceptionType ERROR_TEAM_ALREADY_NAME = err("commands.team.option.name.unchanged");
    private static final SimpleCommandExceptionType ERROR_TEAM_ALREADY_COLOR = err("commands.team.option.color.unchanged");
    private static final SimpleCommandExceptionType ERROR_TEAM_ALREADY_FRIENDLYFIRE_ENABLED = err("commands.team.option.friendlyfire.alreadyEnabled");
    private static final SimpleCommandExceptionType ERROR_TEAM_ALREADY_FRIENDLYFIRE_DISABLED = err("commands.team.option.friendlyfire.alreadyDisabled");
    private static final SimpleCommandExceptionType ERROR_TEAM_ALREADY_FRIENDLYINVISIBLES_ENABLED = err("commands.team.option.seeFriendlyInvisibles.alreadyEnabled");
    private static final SimpleCommandExceptionType ERROR_TEAM_ALREADY_FRIENDLYINVISIBLES_DISABLED = err("commands.team.option.seeFriendlyInvisibles.alreadyDisabled");
    private static final SimpleCommandExceptionType ERROR_TEAM_NAMETAG_VISIBLITY_UNCHANGED = err("commands.team.option.nametagVisibility.unchanged");
    private static final SimpleCommandExceptionType ERROR_TEAM_DEATH_MESSAGE_VISIBLITY_UNCHANGED = err("commands.team.option.deathMessageVisibility.unchanged");
    private static final SimpleCommandExceptionType ERROR_TEAM_COLLISION_UNCHANGED = err("commands.team.option.collisionRule.unchanged");

    private static SimpleCommandExceptionType err(String key) { return new SimpleCommandExceptionType(Text.translatable(key)); }

    private static ServerScoreboard board(CommandSource s) { return s.getServer().scoreboard(); }

    @FunctionalInterface
    private interface TeamOp {
        int run(CommandSource s, PlayerTeam t) throws CommandSyntaxException;
    }

    private static LiteralArgumentBuilder<CommandSource> visibility(String name, boolean nametag) {
        LiteralArgumentBuilder<CommandSource> b = literal(name);
        // 바닐라 등록 순서: never, hideForOtherTeams, hideForOwnTeam, always
        PlayerTeam.Visibility[] order = {PlayerTeam.Visibility.NEVER, PlayerTeam.Visibility.HIDE_FOR_OTHER_TEAMS,
                PlayerTeam.Visibility.HIDE_FOR_OWN_TEAM, PlayerTeam.Visibility.ALWAYS};
        for (PlayerTeam.Visibility v : order) {
            b.then(literal(v.name).executes(c -> nametag
                    ? setNametagVisibility(c.getSource(), ScoreboardArguments.getTeam(c, "team"), v)
                    : setDeathMessageVisibility(c.getSource(), ScoreboardArguments.getTeam(c, "team"), v)));
        }
        return b;
    }

    public static void register(CommandDispatcher<CommandSource> d) {
        LiteralArgumentBuilder<CommandSource> collision = literal("collisionRule");
        PlayerTeam.CollisionRule[] order = {PlayerTeam.CollisionRule.NEVER, PlayerTeam.CollisionRule.PUSH_OWN_TEAM,
                PlayerTeam.CollisionRule.PUSH_OTHER_TEAMS, PlayerTeam.CollisionRule.ALWAYS};
        for (PlayerTeam.CollisionRule r : order) {
            collision.then(literal(r.name).executes(c -> setCollision(c.getSource(), ScoreboardArguments.getTeam(c, "team"), r)));
        }
        d.register(literal("team").requires(s -> s.hasPermission(2))
                .then(literal("list").executes(c -> listTeams(c.getSource()))
                        .then(argument("team", ScoreboardArguments.team()).executes(c -> listMembers(c.getSource(), ScoreboardArguments.getTeam(c, "team")))))
                .then(literal("add").then(argument("team", Arguments.word())
                        .executes(c -> createTeam(c.getSource(), c.getArgument("team", String.class), Text.text(c.getArgument("team", String.class))))
                        .then(argument("displayName", ComponentArgument.textComponent())
                                .executes(c -> createTeam(c.getSource(), c.getArgument("team", String.class), ComponentArgument.getComponent(c, "displayName"))))))
                .then(literal("remove").then(argument("team", ScoreboardArguments.team()).executes(c -> deleteTeam(c.getSource(), ScoreboardArguments.getTeam(c, "team")))))
                .then(literal("empty").then(argument("team", ScoreboardArguments.team()).executes(c -> emptyTeam(c.getSource(), ScoreboardArguments.getTeam(c, "team")))))
                .then(literal("join").then(argument("team", ScoreboardArguments.team())
                        .executes(c -> {
                            PlayerTeam t = ScoreboardArguments.getTeam(c, "team");
                            return joinTeam(c.getSource(), t, List.of(ScoreHolder.of(c.getSource().getEntityOrException())));
                        })
                        .then(argument("members", ScoreHolderArgument.scoreHolders()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                .executes(c -> {
                                    PlayerTeam t = ScoreboardArguments.getTeam(c, "team");
                                    CommandSource s = c.getSource();
                                    return ScoreHolderArgument.await(s, ScoreHolderArgument.getNamesWithDefaultWildcard(c, "members"), h -> joinTeam(s, t, h));
                                }))))
                .then(literal("leave").then(argument("members", ScoreHolderArgument.scoreHolders()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                        .executes(c -> {
                            CommandSource s = c.getSource();
                            return ScoreHolderArgument.await(s, ScoreHolderArgument.getNamesWithDefaultWildcard(c, "members"), h -> leaveTeam(s, h));
                        })))
                .then(literal("modify").then(argument("team", ScoreboardArguments.team())
                        .then(literal("displayName").then(argument("displayName", ComponentArgument.textComponent())
                                .executes(c -> setDisplayName(c.getSource(), ScoreboardArguments.getTeam(c, "team"), ComponentArgument.getComponent(c, "displayName")))))
                        .then(literal("color").then(argument("value", ScoreboardArguments.color())
                                .executes(c -> setColor(c.getSource(), ScoreboardArguments.getTeam(c, "team"), c.getArgument("value", ChatFormatting.class)))))
                        .then(literal("friendlyFire").then(argument("allowed", Arguments.bool())
                                .executes(c -> setFriendlyFire(c.getSource(), ScoreboardArguments.getTeam(c, "team"), c.getArgument("allowed", Boolean.class)))))
                        .then(literal("seeFriendlyInvisibles").then(argument("allowed", Arguments.bool())
                                .executes(c -> setFriendlySight(c.getSource(), ScoreboardArguments.getTeam(c, "team"), c.getArgument("allowed", Boolean.class)))))
                        .then(visibility("nametagVisibility", true))
                        .then(visibility("deathMessageVisibility", false))
                        .then(collision)
                        .then(literal("prefix").then(argument("prefix", ComponentArgument.textComponent())
                                .executes(c -> setPrefix(c.getSource(), ScoreboardArguments.getTeam(c, "team"), ComponentArgument.getComponent(c, "prefix")))))
                        .then(literal("suffix").then(argument("suffix", ComponentArgument.textComponent())
                                .executes(c -> setSuffix(c.getSource(), ScoreboardArguments.getTeam(c, "team"), ComponentArgument.getComponent(c, "suffix"))))))));
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

    static int leaveTeam(CommandSource s, Collection<ScoreHolder> members) {
        ServerScoreboard b = board(s);
        for (ScoreHolder h : members) b.removePlayerFromTeam(h.scoreboardName());
        if (members.size() == 1) s.sendSuccess(() -> Text.translatable("commands.team.leave.success.single", firstName(members)), true);
        else s.sendSuccess(() -> Text.translatable("commands.team.leave.success.multiple", members.size()), true);
        return members.size();
    }

    static int joinTeam(CommandSource s, PlayerTeam team, Collection<ScoreHolder> members) {
        ServerScoreboard b = board(s);
        for (ScoreHolder h : members) b.addPlayerToTeam(h.scoreboardName(), team);
        if (members.size() == 1) s.sendSuccess(() -> Text.translatable("commands.team.join.success.single", firstName(members), team.formattedDisplayName()), true);
        else s.sendSuccess(() -> Text.translatable("commands.team.join.success.multiple", members.size(), team.formattedDisplayName()), true);
        return members.size();
    }

    static int setNametagVisibility(CommandSource s, PlayerTeam t, PlayerTeam.Visibility v) throws CommandSyntaxException {
        if (t.nameTagVisibility() == v) throw ERROR_TEAM_NAMETAG_VISIBLITY_UNCHANGED.create();
        t.nameTagVisibility(v);
        s.sendSuccess(() -> Text.translatable("commands.team.option.nametagVisibility.success", t.formattedDisplayName(), v.displayName()), true);
        return 0;
    }

    static int setDeathMessageVisibility(CommandSource s, PlayerTeam t, PlayerTeam.Visibility v) throws CommandSyntaxException {
        if (t.deathMessageVisibility() == v) throw ERROR_TEAM_DEATH_MESSAGE_VISIBLITY_UNCHANGED.create();
        t.deathMessageVisibility(v);
        s.sendSuccess(() -> Text.translatable("commands.team.option.deathMessageVisibility.success", t.formattedDisplayName(), v.displayName()), true);
        return 0;
    }

    static int setCollision(CommandSource s, PlayerTeam t, PlayerTeam.CollisionRule r) throws CommandSyntaxException {
        if (t.collisionRule() == r) throw ERROR_TEAM_COLLISION_UNCHANGED.create();
        t.collisionRule(r);
        s.sendSuccess(() -> Text.translatable("commands.team.option.collisionRule.success", t.formattedDisplayName(), r.displayName()), true);
        return 0;
    }

    static int setFriendlySight(CommandSource s, PlayerTeam t, boolean v) throws CommandSyntaxException {
        if (t.canSeeFriendlyInvisibles() == v) throw (v ? ERROR_TEAM_ALREADY_FRIENDLYINVISIBLES_ENABLED : ERROR_TEAM_ALREADY_FRIENDLYINVISIBLES_DISABLED).create();
        t.seeFriendlyInvisibles(v);
        s.sendSuccess(() -> Text.translatable("commands.team.option.seeFriendlyInvisibles." + (v ? "enabled" : "disabled"), t.formattedDisplayName()), true);
        return 0;
    }

    static int setFriendlyFire(CommandSource s, PlayerTeam t, boolean v) throws CommandSyntaxException {
        if (t.isAllowFriendlyFire() == v) throw (v ? ERROR_TEAM_ALREADY_FRIENDLYFIRE_ENABLED : ERROR_TEAM_ALREADY_FRIENDLYFIRE_DISABLED).create();
        t.allowFriendlyFire(v);
        s.sendSuccess(() -> Text.translatable("commands.team.option.friendlyfire." + (v ? "enabled" : "disabled"), t.formattedDisplayName()), true);
        return 0;
    }

    static int setDisplayName(CommandSource s, PlayerTeam t, Text name) throws CommandSyntaxException {
        if (t.displayName().equals(name)) throw ERROR_TEAM_ALREADY_NAME.create();
        t.displayName(name);
        s.sendSuccess(() -> Text.translatable("commands.team.option.name.success", t.formattedDisplayName()), true);
        return 0;
    }

    static int setColor(CommandSource s, PlayerTeam t, ChatFormatting color) throws CommandSyntaxException {
        if (t.color() == color) throw ERROR_TEAM_ALREADY_COLOR.create();
        t.color(color);
        s.sendSuccess(() -> Text.translatable("commands.team.option.color.success", t.formattedDisplayName(), color.key()), true);
        return 0;
    }

    static int emptyTeam(CommandSource s, PlayerTeam t) throws CommandSyntaxException {
        ServerScoreboard b = board(s);
        List<String> members = t.playersCopy();
        if (members.isEmpty()) throw ERROR_TEAM_ALREADY_EMPTY.create();
        for (String m : members) b.removePlayerFromTeam(m, t);
        s.sendSuccess(() -> Text.translatable("commands.team.empty.success", members.size(), t.formattedDisplayName()), true);
        return members.size();
    }

    static int deleteTeam(CommandSource s, PlayerTeam t) {
        ServerScoreboard b = board(s);
        b.removePlayerTeam(t);
        s.sendSuccess(() -> Text.translatable("commands.team.remove.success", t.formattedDisplayName()), true);
        return b.getPlayerTeams().size();
    }

    static int createTeam(CommandSource s, String name, Text displayName) throws CommandSyntaxException {
        ServerScoreboard b = board(s);
        if (b.getPlayerTeam(name) != null) throw ERROR_TEAM_ALREADY_EXISTS.create();
        PlayerTeam t = b.addPlayerTeam(name);
        t.displayName(displayName);
        s.sendSuccess(() -> Text.translatable("commands.team.add.success", t.formattedDisplayName()), true);
        return b.getPlayerTeams().size();
    }

    static int listMembers(CommandSource s, PlayerTeam t) {
        List<String> members = t.playersCopy();
        if (members.isEmpty()) {
            s.sendSuccess(() -> Text.translatable("commands.team.list.members.empty", t.formattedDisplayName()), false);
        } else {
            List<Text> names = new ArrayList<>();
            for (String m : members) names.add(Text.text(m));
            s.sendSuccess(() -> Text.translatable("commands.team.list.members.success", t.formattedDisplayName(), members.size(), formatList(names)), false);
        }
        return members.size();
    }

    static int listTeams(CommandSource s) {
        List<PlayerTeam> teams = board(s).getPlayerTeams();
        if (teams.isEmpty()) {
            s.sendSuccess(() -> Text.translatable("commands.team.list.teams.empty"), false);
        } else {
            List<Text> names = new ArrayList<>();
            for (PlayerTeam t : teams) names.add(t.formattedDisplayName());
            s.sendSuccess(() -> Text.translatable("commands.team.list.teams.success", teams.size(), formatList(names)), false);
        }
        return teams.size();
    }

    static int setPrefix(CommandSource s, PlayerTeam t, Text prefix) {
        t.playerPrefix(prefix);
        s.sendSuccess(() -> Text.translatable("commands.team.option.prefix.success", prefix), false);
        return 1;
    }

    static int setSuffix(CommandSource s, PlayerTeam t, Text suffix) {
        t.playerSuffix(suffix);
        s.sendSuccess(() -> Text.translatable("commands.team.option.suffix.success", suffix), false);
        return 1;
    }
}
