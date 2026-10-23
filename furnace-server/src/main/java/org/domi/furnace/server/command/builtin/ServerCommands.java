// 바닐라 KickCommand·ListPlayersCommand·SayCommand·HelpCommand·StopCommand·SeedCommand·SaveAll/Off/OnCommand·SetPlayerIdleTimeoutCommand.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.arguments.EntityArgument;
import org.domi.furnace.server.command.arguments.MessageArgument;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandNode;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.ParseResults;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.net.protocol.play.Disconnect;

public final class ServerCommands {

    private static final SimpleCommandExceptionType ERROR_HELP_FAILED = new SimpleCommandExceptionType(Text.translatable("commands.help.failed"));
    private static final SimpleCommandExceptionType ERROR_KICKING_OWNER = new SimpleCommandExceptionType(Text.translatable("commands.kick.owner.failed"));
    private static final SimpleCommandExceptionType ERROR_SAVE_FAILED = new SimpleCommandExceptionType(Text.translatable("commands.save.failed"));
    private static final SimpleCommandExceptionType ERROR_SAVE_ALREADY_OFF = new SimpleCommandExceptionType(Text.translatable("commands.save.alreadyOff"));
    private static final SimpleCommandExceptionType ERROR_SAVE_ALREADY_ON = new SimpleCommandExceptionType(Text.translatable("commands.save.alreadyOn"));

    private ServerCommands() {}

    public static void registerHelp(CommandDispatcher<CommandSource> d) {
        d.register(literal("help")
                .executes(c -> {
                    Map<CommandNode<CommandSource>, String> map = d.getSmartUsage(d.getRoot(), c.getSource());
                    for (String s : map.values()) c.getSource().sendSuccess(() -> Text.text("/" + s), false);
                    return map.size();
                })
                .then(argument("command", Arguments.greedyString()).executes(c -> {
                    ParseResults<CommandSource> parse = d.parse(c.getArgument("command", String.class), c.getSource());
                    if (parse.getContext().getNodes().isEmpty()) throw ERROR_HELP_FAILED.create();
                    var nodes = parse.getContext().getNodes();
                    Map<CommandNode<CommandSource>, String> map = d.getSmartUsage(nodes.get(nodes.size() - 1).getNode(), c.getSource());
                    for (String s : map.values()) {
                        c.getSource().sendSuccess(() -> Text.text("/" + parse.getReader().getString() + " " + s), false);
                    }
                    return map.size();
                })));
    }

    public static void registerKick(CommandDispatcher<CommandSource> d) {
        d.register(literal("kick").requires(s -> s.hasPermission(2))
                .then(argument("targets", EntityArgument.players())
                        .executes(c -> kickPlayers(c.getSource(), EntityArgument.getPlayers(c, "targets"),
                                Text.translatable("multiplayer.disconnect.kicked")))
                        .then(argument("reason", MessageArgument.message())
                                .executes(c -> kickPlayers(c.getSource(), EntityArgument.getPlayers(c, "targets"),
                                        MessageArgument.getMessage(c, "reason"))))));
    }

    private static int kickPlayers(CommandSource source, Collection<Player> players, Text reason) throws CommandSyntaxException {
        int i = 0;
        for (Player p : players) {
            p.kick(new Disconnect(reason));
            source.sendSuccess(() -> Text.translatable("commands.kick.success", Text.text(p.name()), reason), true);
            i++;
        }
        if (i == 0) throw ERROR_KICKING_OWNER.create();
        return i;
    }

    public static void registerList(CommandDispatcher<CommandSource> d) {
        d.register(literal("list")
                .executes(c -> listPlayers(c.getSource(), p -> Text.text(p.name())))
                .then(literal("uuids").executes(c -> listPlayers(c.getSource(),
                        p -> Text.translatable("commands.list.nameAndId", p.name(), p.uuid().toString())))));
    }

    private static int listPlayers(CommandSource source, Function<Player, Text> name) {
        List<Player> players = source.getServer().players();
        Text list = Text.empty();
        for (int i = 0; i < players.size(); i++) {
            if (i > 0) list = list.append(Text.text(", ").color(org.domi.furnace.api.text.NamedTextColor.GRAY));
            list = list.append(name.apply(players.get(i)));
        }
        int max = source.getServer().settings().get().maxPlayers;
        Text names = list;
        source.sendSuccess(() -> Text.translatable("commands.list.players", players.size(), max, names), false);
        return players.size();
    }

    public static void registerSeed(CommandDispatcher<CommandSource> d) {
        d.register(literal("seed").requires(s -> s.hasPermission(2)).executes(c -> {
            long seed = c.getSource().getServer().levelData().seed;
            String s = String.valueOf(seed);
            Text value = Text.translatable("chat.square_brackets", Text.text(s).color(org.domi.furnace.api.text.NamedTextColor.GREEN)
                    .clickEvent(new org.domi.furnace.api.text.ClickEvent(org.domi.furnace.api.text.ClickEvent.Action.COPY_TO_CLIPBOARD, s)).hoverText(Text.translatable("chat.copy.click")).insertion(s));
            c.getSource().sendSuccess(() -> Text.translatable("commands.seed.success", value), false);
            return (int) seed;
        }));
    }

    public static void registerSaveAll(CommandDispatcher<CommandSource> d) {
        d.register(literal("save-all").requires(s -> s.hasPermission(4))
                .executes(c -> saveAll(c.getSource(), false))
                .then(literal("flush").executes(c -> saveAll(c.getSource(), true))));
    }

    private static int saveAll(CommandSource source, boolean flush) throws CommandSyntaxException {
        source.sendSuccess(() -> Text.translatable("commands.save.saving"), false);
        try {
            source.getServer().saveAll();
        } catch (RuntimeException e) {
            throw ERROR_SAVE_FAILED.create();
        }
        source.sendSuccess(() -> Text.translatable("commands.save.success"), true);
        return 1;
    }

    public static void registerSaveOff(CommandDispatcher<CommandSource> d) {
        d.register(literal("save-off").requires(s -> s.hasPermission(4)).executes(c -> {
            if (!c.getSource().getServer().autosaveEnabled()) throw ERROR_SAVE_ALREADY_OFF.create();
            c.getSource().getServer().autosaveEnabled(false);
            c.getSource().sendSuccess(() -> Text.translatable("commands.save.disabled"), true);
            return 1;
        }));
    }

    public static void registerSaveOn(CommandDispatcher<CommandSource> d) {
        d.register(literal("save-on").requires(s -> s.hasPermission(4)).executes(c -> {
            if (c.getSource().getServer().autosaveEnabled()) throw ERROR_SAVE_ALREADY_ON.create();
            c.getSource().getServer().autosaveEnabled(true);
            c.getSource().sendSuccess(() -> Text.translatable("commands.save.enabled"), true);
            return 1;
        }));
    }

    public static void registerSetIdleTimeout(CommandDispatcher<CommandSource> d) {
        d.register(literal("setidletimeout").requires(s -> s.hasPermission(3))
                .then(argument("minutes", Arguments.integer(0)).executes(c -> {
                    int minutes = c.getArgument("minutes", Integer.class);
                    c.getSource().getServer().settings().update(p -> p.playerIdleTimeout.with(minutes));
                    c.getSource().sendSuccess(() -> Text.translatable("commands.setidletimeout.success", minutes), true);
                    return minutes;
                })));
    }

    public static void registerStop(CommandDispatcher<CommandSource> d) {
        d.register(literal("stop").requires(s -> s.hasPermission(4)).executes(c -> {
            c.getSource().sendSuccess(() -> Text.translatable("commands.stop.stopping"), true);
            c.getSource().getServer().halt();
            return 1;
        }));
    }
}
