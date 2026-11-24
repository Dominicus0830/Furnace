package net.minecraft.network;

/** 연결 단계 shim(Mojang 이름). 상수(생성자 인자 포함)와 순서는 명세가 정한다(바닐라 1.21.1 과 같다). */
enum ConnectionProtocol {

    private final String id;

    ConnectionProtocol(String id) { this.id = id; }

    public String id() { return id; }
}
