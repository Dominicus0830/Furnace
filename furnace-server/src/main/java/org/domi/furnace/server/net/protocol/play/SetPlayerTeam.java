// S→C 팀(0x60): String 팀 이름, byte 방식(0 추가·1 제거·2 갱신·3 멤버 추가·4 멤버 제거),
// 0/2 면 매개변수(텍스트 NBT 표시 이름, byte 옵션, String 이름표 보임, String 충돌 규칙, VarInt 색 ChatFormatting ordinal, 텍스트 NBT 접두사, 접미사),
// 0/3/4 면 VarInt 개수 + String 멤버들. 바닐라 ClientboundSetPlayerTeamPacket(1.21.1). 사망 메시지 보임은 보내지 않는다.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import java.util.List;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.scores.ChatFormatting;
import org.domi.furnace.server.scores.PlayerTeam;

public record SetPlayerTeam(String name, int method, Parameters parameters, List<String> players) implements Packet {

    public static final int ID = 0x60;
    public static final int METHOD_ADD = 0, METHOD_REMOVE = 1, METHOD_CHANGE = 2, METHOD_JOIN = 3, METHOD_LEAVE = 4;

    public record Parameters(Text displayName, int options, String nametagVisibility, String collisionRule, ChatFormatting color,
                             Text prefix, Text suffix) {
        public static Parameters of(PlayerTeam t) {
            return new Parameters(t.displayName(), t.packOptions(), t.nameTagVisibility().name, t.collisionRule().name, t.color(),
                    t.playerPrefix(), t.playerSuffix());
        }
    }

    /** 바닐라 createAddOrModifyPacket. */
    public static SetPlayerTeam addOrModify(PlayerTeam t, boolean add) {
        return new SetPlayerTeam(t.name(), add ? METHOD_ADD : METHOD_CHANGE, Parameters.of(t), add ? t.playersCopy() : List.of());
    }

    public static SetPlayerTeam remove(PlayerTeam t) { return new SetPlayerTeam(t.name(), METHOD_REMOVE, null, List.of()); }

    /** 바닐라 createPlayerPacket(join = 3, leave = 4). */
    public static SetPlayerTeam player(PlayerTeam t, String player, boolean join) {
        return new SetPlayerTeam(t.name(), join ? METHOD_JOIN : METHOD_LEAVE, null, List.of(player));
    }

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeString(buf, name);
        buf.writeByte(method);
        if (method == METHOD_ADD || method == METHOD_CHANGE) {
            Parameters p = parameters;
            buf.writeBytes(p.displayName().networkNbt());
            buf.writeByte(p.options());
            Types.writeString(buf, p.nametagVisibility());
            Types.writeString(buf, p.collisionRule());
            Types.writeVarInt(buf, p.color().ordinal());
            buf.writeBytes(p.prefix().networkNbt());
            buf.writeBytes(p.suffix().networkNbt());
        }
        if (method == METHOD_ADD || method == METHOD_JOIN || method == METHOD_LEAVE) {
            Types.writeVarInt(buf, players.size());
            for (String s : players) Types.writeString(buf, s);
        }
    }
}
