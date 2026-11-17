package net.minecraft.network.protocol;

import net.minecraft.network.PacketListener;

/** 묶음 패킷(Mojang 이름) — 안의 패킷들을 클라이언트가 한 번에 처리한다. */
public abstract class BundlePacket<T extends PacketListener> implements Packet<T> {

    private final Iterable<Packet<? super T>> packets;

    protected BundlePacket(Iterable<Packet<? super T>> packets) { this.packets = packets; }

    public final Iterable<Packet<? super T>> subPackets() { return packets; }
}
