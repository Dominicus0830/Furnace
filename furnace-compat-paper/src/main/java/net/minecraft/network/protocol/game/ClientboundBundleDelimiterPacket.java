package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.BundleDelimiterPacket;

/** clientbound game/bundle_delimiter(Mojang 이름). */
public class ClientboundBundleDelimiterPacket extends BundleDelimiterPacket<ClientGamePacketListener> {

    public ClientboundBundleDelimiterPacket() {}
}
