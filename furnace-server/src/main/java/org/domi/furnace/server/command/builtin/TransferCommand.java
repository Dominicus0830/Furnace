// 바닐라 TransferCommand — /transfer <호스트> [포트 [대상]]: 클라이언트를 다른 서버로 보낸다(권한 3).
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.Collection;
import java.util.List;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.arguments.EntityArgument;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.protocol.play.Transfer;

public final class TransferCommand {

    private TransferCommand() {}

    public static void register(CommandDispatcher<CommandSource> d) {
        d.register(literal("transfer").requires(s -> s.hasPermission(3))
                .then(argument("hostname", Arguments.string())
                        .executes(c -> transfer(c.getSource(), c.getArgument("hostname", String.class), 25565, List.of(c.getSource().getPlayerOrException())))
                        .then(argument("port", Arguments.integer(1, 65535))
                                .executes(c -> transfer(c.getSource(), c.getArgument("hostname", String.class), c.getArgument("port", Integer.class),
                                        List.of(c.getSource().getPlayerOrException())))
                                .then(argument("players", EntityArgument.players())
                                        .executes(c -> transfer(c.getSource(), c.getArgument("hostname", String.class), c.getArgument("port", Integer.class),
                                                EntityArgument.getPlayers(c, "players")))))));
    }

    private static final SimpleCommandExceptionType ERROR_NO_PLAYERS = new SimpleCommandExceptionType(Text.translatable("commands.transfer.error.no_players"));

    static int transfer(CommandSource s, String host, int port, Collection<Player> players) throws CommandSyntaxException {
        if (players.isEmpty()) throw ERROR_NO_PLAYERS.create();
        Transfer pkt = new Transfer(host, port);
        for (Player p : players) p.send(pkt);
        if (players.size() == 1) {
            Player only = players.iterator().next();
            s.sendSuccess(() -> Text.translatable("commands.transfer.success.single", only.displayName(), host, port), true);
        } else {
            s.sendSuccess(() -> Text.translatable("commands.transfer.success.multiple", players.size(), host, port), true);
        }
        return players.size();
    }
}
