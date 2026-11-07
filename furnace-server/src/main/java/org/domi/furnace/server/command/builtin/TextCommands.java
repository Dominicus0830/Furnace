// 바닐라 TellRawCommand·TitleCommand — 선택자를 대상별로 풀어(updateForEntity) 보낸다.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.ComponentUtils;
import org.domi.furnace.server.command.EntityOps;
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
                            // 선택자·점수·NBT 는 대상마다 풀어 보낸다(바닐라 updateForEntity(source, message, player, 0)). 일반 엔티티를 읽으면 region 을 거치므로 future.
                            CommandSource source = c.getSource();
                            List<Player> targets = new ArrayList<>(EntityArgument.getPlayers(c, "targets"));
                            Text message = ComponentArgument.getComponent(c, "message");
                            List<CompletableFuture<Text>> resolved = new ArrayList<>(targets.size());
                            for (Player p : targets) resolved.add(ComponentUtils.resolve(source, message, p));
                            return EntityOps.result(source, EntityOps.awaitAllAsync(source, resolved, texts -> {
                                for (int i = 0; i < targets.size(); i++) targets.get(i).sendSystemMessage(texts.get(i), false);
                                return targets.size();
                            }));
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
        List<Player> list = new ArrayList<>(targets);
        List<CompletableFuture<Text>> resolved = new ArrayList<>(list.size());
        for (Player p : list) resolved.add(ComponentUtils.resolve(s, component, p));
        return EntityOps.result(s, EntityOps.awaitAllAsync(s, resolved, texts -> {
            for (int i = 0; i < list.size(); i++) list.get(i).send(new TextOnlyPacket(packetId, list.get(i).localize(texts.get(i))));
            report(s, "commands.title.show." + name, list);
            return list.size();
        }));
    }

    static int times(CommandSource s, Collection<Player> targets, int fadeIn, int stay, int fadeOut) {
        TitleTimes pkt = new TitleTimes(fadeIn, stay, fadeOut);
        for (Player p : targets) p.send(pkt);
        report(s, "commands.title.times", targets);
        return targets.size();
    }
}
