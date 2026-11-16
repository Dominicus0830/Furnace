package net.minecraft.network;

/** 연결 단계 shim(Mojang 이름, N3 1단계). 상수 순서·이름은 바닐라 1.21.1 과 같다. */
public enum ConnectionProtocol {
    HANDSHAKING("handshake"),
    PLAY("play"),
    STATUS("status"),
    LOGIN("login"),
    CONFIGURATION("configuration");

    private final String id;

    ConnectionProtocol(String id) { this.id = id; }

    public String id() { return id; }
}
