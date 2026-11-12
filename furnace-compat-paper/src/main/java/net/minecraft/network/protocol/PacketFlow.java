package net.minecraft.network.protocol;

/** 패킷 방향 shim(Mojang 이름, N3 1단계). 상수 순서는 바닐라와 같다(SERVERBOUND 가 먼저). */
public enum PacketFlow {
    SERVERBOUND("serverbound"),
    CLIENTBOUND("clientbound");

    private final String id;

    PacketFlow(String id) { this.id = id; }

    public PacketFlow getOpposite() { return this == CLIENTBOUND ? SERVERBOUND : CLIENTBOUND; }

    public String id() { return id; }
}
