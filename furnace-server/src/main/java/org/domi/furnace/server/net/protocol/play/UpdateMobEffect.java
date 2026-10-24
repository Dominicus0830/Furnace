// 효과 걸림/갱신(clientbound update_mob_effect 0x76): 엔티티, 효과 번호, 증폭, 지속시간, 플래그(1 환경 2 입자 4 아이콘 8 blend).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record UpdateMobEffect(int entityId, int effectId, int amplifier, int duration, byte flags) implements Packet {

    public static final int ID = 0x76;

    public static UpdateMobEffect of(int entityId, MobEffectInstance e, boolean blend) {
        int f = (e.ambient() ? 1 : 0) | (e.visible() ? 2 : 0) | (e.showIcon() ? 4 : 0) | (blend ? 8 : 0);
        return new UpdateMobEffect(entityId, e.effect().registryId(), e.amplifier(), e.duration(), (byte) f);
    }

    @Override public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, entityId);
        Types.writeVarInt(buf, effectId);
        Types.writeVarInt(buf, amplifier);
        Types.writeVarInt(buf, duration);
        buf.writeByte(flags);
    }
}
