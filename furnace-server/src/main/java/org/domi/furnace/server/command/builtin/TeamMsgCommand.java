// 바닐라 TeamMsgCommand: /teammsg <message>(= /tm) — 실행 주체의 팀원(접속한 플레이어)에게 팀 채팅으로 보낸다.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.api.text.ClickEvent;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.chat.ChatType;
import org.domi.furnace.server.chat.PlayerChatMessage;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.arguments.MessageArgument;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.LiteralCommandNode;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Rider;
import org.domi.furnace.server.scores.PlayerTeam;
import org.domi.furnace.server.scores.ScoreHolder;

public final class TeamMsgCommand {

    private TeamMsgCommand() {}

    private static final SimpleCommandExceptionType ERROR_NOT_ON_TEAM = new SimpleCommandExceptionType(Text.translatable("commands.teammsg.failed.noteam"));

    public static void register(CommandDispatcher<CommandSource> d) {
        LiteralCommandNode<CommandSource> node = d.register(literal("teammsg").then(argument("message", MessageArgument.message()).executes(c -> {
            CommandSource source = c.getSource();
            Rider entity = source.getEntityOrException();
            PlayerTeam team = teamOf(source, entity);
            if (team == null) throw ERROR_NOT_ON_TEAM.create();
            List<Player> receivers = new ArrayList<>();
            for (Player p : source.getServer().players()) {
                if (p == entity || teamOf(source, p) == team) receivers.add(p);
            }
            if (!receivers.isEmpty()) MessageArgument.resolveChatMessage(c, "message", m -> sendMessage(source, entity, team, receivers, m));
            return receivers.size();
        })));
        d.register(literal("tm").redirect(node));
    }

    /** Entity.getTeam: 점수판 이름(플레이어는 접속 이름, 그 밖은 UUID)이 든 팀. */
    private static PlayerTeam teamOf(CommandSource source, Rider r) {
        var board = source.getServer().scoreboard();
        return board == null ? null : board.getPlayersTeam(ScoreHolder.nameOf(r));
    }

    private static void sendMessage(CommandSource source, Rider entity, PlayerTeam team, List<Player> receivers, PlayerChatMessage message) {
        // SUGGEST_STYLE: 호버 "chat.type.team.hover", 클릭하면 "/teammsg " 제안
        Text teamName = team.formattedDisplayName().hoverText(Text.translatable("chat.type.team.hover"))
                .clickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/teammsg "));
        ChatType.Bound incoming = ChatType.TEAM_MSG_COMMAND_INCOMING.bind(source.getDisplayName()).withTargetName(teamName);
        ChatType.Bound outgoing = ChatType.TEAM_MSG_COMMAND_OUTGOING.bind(source.getDisplayName()).withTargetName(teamName);
        // 바닐라는 받는 쪽마다 shouldFilterMessageTo(보내는 쪽·받는 쪽 클라이언트가 글자 걸러내기를 켰나)로 message.filter(...)를 부르고, 걸러진 결과가
        // 전부 비면 CHAT_FILTERED_FULL 을 보낸다. 걸러내기 서비스(text-filtering-config)가 없으면 마스크가 PASS_THROUGH 라 어느 쪽이든 원문 그대로
        // 가고 전부 걸러지는 일도 없다. Furnace 는 그 서비스를 지원하지 않으므로(FurnaceMain 경고) 이 분기가 관측 결과를 바꾸지 않는다(/msg 와 같음).
        for (Player p : receivers) source.getServer().sendChatMessage(p, message, false, p == entity ? outgoing : incoming);
    }
}
