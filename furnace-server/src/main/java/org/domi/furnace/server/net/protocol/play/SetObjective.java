// S→C 점수판 목표(0x5E): String 이름, byte 방식(0 추가·1 제거·2 갱신), 0/2 면 텍스트 NBT 표시 이름 + VarInt 표시 방식 + Optional<숫자 형식>.
// 바닐라 ClientboundSetObjectivePacket(1.21.1) 의 필드 순서.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.scores.NumberFormat;
import org.domi.furnace.server.scores.Objective;
import org.domi.furnace.server.scores.ObjectiveCriteria;

public record SetObjective(String name, int method, Text displayName, ObjectiveCriteria.RenderType renderType, NumberFormat numberFormat)
        implements Packet {

    public static final int ID = 0x5E;
    public static final int METHOD_ADD = 0, METHOD_REMOVE = 1, METHOD_CHANGE = 2;

    public static SetObjective of(Objective o, int method) {
        return new SetObjective(o.name(), method, o.displayName(), o.renderType(), o.numberFormat());
    }

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeString(buf, name);
        buf.writeByte(method);
        if (method == METHOD_ADD || method == METHOD_CHANGE) {
            buf.writeBytes(displayName.networkNbt());
            Types.writeVarInt(buf, renderType.ordinal());
            NumberFormat.writeOptional(buf, numberFormat);
        }
    }
}
