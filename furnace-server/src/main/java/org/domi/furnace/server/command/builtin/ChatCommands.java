// 바닐라 EmoteCommands(me)·MsgCommand(msg/tell/w)·SayCommand — 서명된(또는 위장된) 채팅 메시지로 보낸다.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.Collection;
import org.domi.furnace.server.chat.ChatType;
import org.domi.furnace.server.chat.PlayerChatMessage;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.EntityOps;
import org.domi.furnace.server.command.arguments.EntityArgument;
import org.domi.furnace.server.command.arguments.MessageArgument;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.LiteralCommandNode;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.api.text.Text;

public final class ChatCommands {

    private ChatCommands() {}

    public static void registerMe(CommandDispatcher<CommandSource> d) {
        d.register(literal("me").then(argument("action", MessageArgument.message()).executes(c ->
                EntityOps.result(c.getSource(), MessageArgument.resolveChatMessageAsync(c, "action", 1, m -> broadcast(c.getSource(), m, ChatType.EMOTE_COMMAND))))));
    }

    public static void registerSay(CommandDispatcher<CommandSource> d) {
        d.register(literal("say").requires(s -> s.hasPermission(2))
                .then(argument("message", MessageArgument.message()).executes(c ->
                        EntityOps.result(c.getSource(), MessageArgument.resolveChatMessageAsync(c, "message", 1, m -> broadcast(c.getSource(), m, ChatType.SAY_COMMAND))))));
    }

    /** 바닐라 PlayerList.broadcastChatMessage(message, CommandSourceStack, bound). */
    private static void broadcast(CommandSource source, PlayerChatMessage message, ChatType type) {
        source.getServer().broadcastChatMessage(message, source.getPlayer(), type.bind(source.getDisplayName()));
    }

    public static void registerMsg(CommandDispatcher<CommandSource> d) {
        LiteralCommandNode<CommandSource> node = d.register(literal("msg").then(argument("targets", EntityArgument.players())
                .then(argument("message", MessageArgument.message()).executes(c -> {
                    Collection<Player> targets = EntityArgument.getPlayers(c, "targets");
                    if (targets.isEmpty()) return 0;
                    return EntityOps.result(c.getSource(), MessageArgument.resolveChatMessageAsync(c, "message", targets.size(), m -> sendMessage(c.getSource(), targets, m)));
                }))));
        d.register(literal("tell").redirect(node));
        d.register(literal("w").redirect(node));
    }

    private static void sendMessage(CommandSource source, Collection<Player> targets, PlayerChatMessage message) {
        ChatType.Bound incoming = ChatType.MSG_COMMAND_INCOMING.bind(source.getDisplayName());
        for (Player target : targets) {
            ChatType.Bound outgoing = ChatType.MSG_COMMAND_OUTGOING.bind(source.getDisplayName()).withTargetName(Text.text(target.name()));
            source.sendChatMessage(message, false, outgoing);
            source.getServer().sendChatMessage(target, message, false, incoming);
        }
    }
}
