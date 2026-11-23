package net.minecraft.network.protocol;

/** 패킷 방향 shim(Mojang 이름, N3 1단계). 상수(생성자 인자 포함)와 순서는 명세가 정한다(SERVERBOUND 가 먼저). */
enum PacketFlow {

    private final String id;

    PacketFlow(String id) { this.id = id; }

    public PacketFlow getOpposite() { return this == CLIENTBOUND ? SERVERBOUND : CLIENTBOUND; }

    public String id() { return id; }
}
