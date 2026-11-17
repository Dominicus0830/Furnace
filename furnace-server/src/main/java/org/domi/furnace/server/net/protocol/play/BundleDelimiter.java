// S→C 묶음 구분자(bundle_delimiter 0x00): 두 구분자 사이의 패킷을 클라이언트가 한 틱에 함께 처리한다(엔티티 스폰 묶음).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.PacketIds;

@VanillaPort("net.minecraft.network.protocol.game.ClientboundBundleDelimiterPacket")
public record BundleDelimiter() implements Packet {

    public static final int ID = PacketIds.PLAY_CLIENTBOUND_BUNDLE_DELIMITER;
    public static final BundleDelimiter INSTANCE = new BundleDelimiter();

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {}
}
