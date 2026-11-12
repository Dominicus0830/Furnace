package net.minecraft.server.network;

import io.netty.channel.ChannelFuture;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

/**
 * 서버 접속 수신 shim(Mojang 이름, N3 2단계): 서버 소켓 채널 목록({@link #channels}).
 * ProtocolLib 같은 패킷 라이브러리는 이 목록을 자기 목록으로 갈아 끼워 새 접속마다 파이프라인 초기화에 끼어든다.
 * <p>
 * 지금은 목록이 비어 있다 — Furnace 의 파이프라인은 바닐라와 처리기 이름·패킷 객체 단이 달라, 실제 소켓 채널을 넘기면 라이브러리가
 * 없는 처리기 이름을 찾다 접속마다 실패한다. 파이프라인 다리(ProtocolLib 다리 3단계)가 생기면 그때 채운다.
 * 목록 필드 이름·모양(제네릭 {@code List<ChannelFuture>}, 바꿔 끼울 수 있는 일반 필드)은 바닐라와 같다.
 */
public class ServerConnectionListener {

    /** 바닐라: 서버 소켓 채널의 bind 결과들. 라이브러리가 {@code ListeningList} 로 갈아 끼운다. */
    private List<ChannelFuture> channels = Collections.synchronizedList(new ArrayList<>());

    /** 바닐라: 지금 열린 연결들(형식 인자는 NMS {@code Connection} — 이 단계에서는 비어 있다). */
    private final List<net.minecraft.network.Connection> connections = Collections.synchronizedList(new ArrayList<>());

    /** 호환 모듈 전용. */
    public ServerConnectionListener() {}

    /** 바닐라: 지금 열린 연결들. */
    public List<net.minecraft.network.Connection> getConnections() { return connections; }
}
