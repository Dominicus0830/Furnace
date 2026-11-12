package net.minecraft.network.protocol;

import net.minecraft.network.PacketListener;

/** 묶음 경계 패킷(Mojang 이름). 와이어에서 묶음의 앞뒤에 붙는다 — 플러그인이 직접 보낼 일은 드물다. */
public abstract class BundleDelimiterPacket<T extends PacketListener> implements Packet<T> {

    public BundleDelimiterPacket() {}
}
