// S→C 카메라 대상(0x52): VarInt 엔티티 id. 관전자의 시점을 그 엔티티로 옮긴다(자기 id 면 원래대로).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.PacketIds;

@VanillaPort("net.minecraft.network.protocol.game.ClientboundSetCameraPacket")
public record SetCamera(int entityId) implements Packet {

    public static final int ID = PacketIds.PLAY_CLIENTBOUND_SET_CAMERA;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, entityId);
    }
}
