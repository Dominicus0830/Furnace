// 바닐라 TellRawCommand·TitleCommand — 선택자를 대상별로 풀어(updateForEntity) 보낸다.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.Collection;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.ComponentUtils;
import org.domi.furnace.server.command.arguments.ComponentArgument;
import org.domi.furnace.server.command.arguments.EntityArgument;
import org.domi.furnace.server.command.arguments.TimeArgument;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.protocol.play.ClearTitles;
import org.domi.furnace.server.net.protocol.play.TextOnlyPacket;
import org.domi.furnace.server.net.protocol.play.TitleTimes;

public final class TextCommands {

    private TextCommands() {}

    public static void registerTellRaw(CommandDispatcher<CommandSource> d) {
        d.register(literal("tellraw").requires(s -> s.hasPermission(2))
                .then(argument("targets", EntityArgument.players())
                        .then(argument("message", ComponentArgument.textComponent()).executes(c -> {
                            int n = 0;
                            for (Player p : EntityArgument.getPlayers(c, "targets")) {
                                p.sendSystemMessage(ComponentUtils.updateForEntity(c.getSource(), ComponentArgument.getComponent(c, "message"), p, 0), false);
                                n++;
                            }
                            return n;
                        }))));
    }

    public static void registerTitle(CommandDispatcher<CommandSource> d) {
        d.register(literal("title").requires(s -> s.hasPermission(2))
                .then(argument("targets", EntityArgument.players())
                        .then(literal("clear").executes(c -> clear(c.getSource(), EntityArgument.getPlayers(c, "targets"))))
                        .then(literal("reset").executes(c -> reset(c.getSource(), EntityArgument.getPlayers(c, "targets"))))
                        .then(literal("title").then(argument("title", ComponentArgument.textComponent())
                                .executes(c -> show(c.getSource(), EntityArgument.getPlayers(c, "targets"), ComponentArgument.getComponent(c, "title"), "title", TextOnlyPacket.TITLE))))
                        .then(literal("subtitle").then(argument("title", ComponentArgument.textComponent())
                                .executes(c -> show(c.getSource(), EntityArgument.getPlayers(c, "targets"), ComponentArgument.getComponent(c, "title"), "subtitle", TextOnlyPacket.SUBTITLE))))
                        .then(literal("actionbar").then(argument("title", ComponentArgument.textComponent())
                                .executes(c -> show(c.getSource(), EntityArgument.getPlayers(c, "targets"), ComponentArgument.getComponent(c, "title"), "actionbar", TextOnlyPacket.ACTION_BAR))))
                        .then(literal("times").then(argument("fadeIn", TimeArgument.time(0))
                                .then(argument("stay", TimeArgument.time(0))
                                        .then(argument("fadeOut", TimeArgument.time(0)).executes(c -> times(c.getSource(), EntityArgument.getPlayers(c, "targets"),
                                                c.getArgument("fadeIn", Integer.class), c.getArgument("stay", Integer.class), c.getArgument("fadeOut", Integer.class)))))))));
    }

    private static void report(CommandSource s, String key, Collection<Player> targets, Object... extra) {
        boolean single = targets.size() == 1;
        Object[] args;
        if (single) {
            args = new Object[] {targets.iterator().next().displayName()};
        } else {
            args = new Object[] {targets.size()};
        }
        s.sendSuccess(() -> Text.translatable(key + (single ? ".single" : ".multiple"), args), true);
    }

    static int clear(CommandSource s, Collection<Player> targets) {
        ClearTitles pkt = new ClearTitles(false);
        for (Player p : targets) p.send(pkt);
        report(s, "commands.title.cleared", targets);
        return targets.size();
    }

    static int reset(CommandSource s, Collection<Player> targets) {
        ClearTitles pkt = new ClearTitles(true);
        for (Player p : targets) p.send(pkt);
        report(s, "commands.title.reset", targets);
        return targets.size();
    }

    static int show(CommandSource s, Collection<Player> targets, Text component, String name, int packetId) throws CommandSyntaxException {
        for (Player p : targets) p.send(new TextOnlyPacket(packetId, p.localize(ComponentUtils.updateForEntity(s, component, p, 0))));
        report(s, "commands.title.show." + name, targets);
        return targets.size();
    }

    static int times(CommandSource s, Collection<Player> targets, int fadeIn, int stay, int fadeOut) {
        TitleTimes pkt = new TitleTimes(fadeIn, stay, fadeOut);
        for (Player p : targets) p.send(pkt);
        report(s, "commands.title.times", targets);
        return targets.size();
    }
}
