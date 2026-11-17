// S→C 피격 모션(0x24): VarInt 엔티티 번호, float 피격 방향(도). 바닐라 ServerPlayer.indicateDamage — 화면 기울임.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.PacketIds;

@VanillaPort("net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket")
public record HurtAnimation(int entityId, float yaw) implements Packet {

    public static final int ID = PacketIds.PLAY_CLIENTBOUND_HURT_ANIMATION;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, entityId);
        buf.writeFloat(yaw);
    }
}
