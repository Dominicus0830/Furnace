// S→C 피해 이벤트(0x1A) — 맞은 엔티티·damage_type 순번·원인/직접 엔티티(id+1, 0=없음)·선택적 위치. 클라가 피격 모션·소리를 낸다.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record DamageEvent(int entityId, int sourceTypeId, int sourceCauseId, int sourceDirectId, double[] sourcePosition) implements Packet {

    public static final int ID = 0x1A;

    /** 원인 엔티티가 없는 환경 피해(낙하·공허·굶주림 등). */
    public static DamageEvent environmental(int entityId, int sourceTypeId) {
        return new DamageEvent(entityId, sourceTypeId, -1, -1, null);
    }

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, entityId);
        Types.writeVarInt(buf, sourceTypeId);
        Types.writeVarInt(buf, sourceCauseId + 1);
        Types.writeVarInt(buf, sourceDirectId + 1);
        buf.writeBoolean(sourcePosition != null);
        if (sourcePosition != null) {
            buf.writeDouble(sourcePosition[0]);
            buf.writeDouble(sourcePosition[1]);
            buf.writeDouble(sourcePosition[2]);
        }
    }
}
