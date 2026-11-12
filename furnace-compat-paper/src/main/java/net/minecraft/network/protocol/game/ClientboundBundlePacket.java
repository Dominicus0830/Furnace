package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;

/** clientbound game/bundle(Mojang 이름). 보내면 안의 패킷을 차례로 보낸다(ServerCommonPacketListenerImpl#send). */
public class ClientboundBundlePacket extends BundlePacket<ClientGamePacketListener> {

    public ClientboundBundlePacket(Iterable<Packet<? super ClientGamePacketListener>> packets) { super(packets); }
}
