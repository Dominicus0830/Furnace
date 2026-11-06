// 폭발(clientbound explode): 위치·반경·부서진 칸(정수 좌표 기준 상대 바이트)·플레이어 밀림·블록 상호작용·입자 두 종류·소리.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import java.util.List;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record ExplodePacket(double x, double y, double z, float power, List<Pos> toBlow, float kx, float ky, float kz, int blockInteraction,
                            int smallParticle, int largeParticle, int soundId) implements Packet {

    public static final int ID = 0x20;

    @Override public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(power);
        int fx = (int) Math.floor(x), fy = (int) Math.floor(y), fz = (int) Math.floor(z);
        Types.writeVarInt(buf, toBlow.size());
        for (Pos p : toBlow) {
            buf.writeByte(p.x() - fx);
            buf.writeByte(p.y() - fy);
            buf.writeByte(p.z() - fz);
        }
        buf.writeFloat(kx);
        buf.writeFloat(ky);
        buf.writeFloat(kz);
        Types.writeVarInt(buf, blockInteraction);
        Types.writeVarInt(buf, smallParticle);   // 옵션이 없는 입자(explosion·explosion_emitter)
        Types.writeVarInt(buf, largeParticle);
        Types.writeVarInt(buf, soundId + 1);     // 레지스트리 홀더
    }
}
