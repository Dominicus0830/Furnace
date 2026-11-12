package net.minecraft.network;

/**
 * 연결 shim(Mojang 이름, N3 2단계): 바닐라 {@code Connection} 의 "이름·모양"만 있다.
 * 패킷 라이브러리가 연결 객체의 클래스·필드를 리플렉션으로 찾는 데 쓰인다. 실제 Netty 채널을 내보내지 않는다 —
 * 파이프라인에 처리기를 끼우는 코드는 아직 지원하지 않는다(ProtocolLib 다리 3단계).
 */
public class Connection {

    /** 호환 모듈 전용. */
    protected Connection() {}

    /** 바닐라: 이 연결이 받는 방향. */
    public net.minecraft.network.protocol.PacketFlow getReceiving() { return net.minecraft.network.protocol.PacketFlow.SERVERBOUND; }
}
