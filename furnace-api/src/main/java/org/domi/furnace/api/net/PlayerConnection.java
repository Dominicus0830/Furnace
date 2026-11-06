// 클라이언트 연결 하나 — 핸드셰이크부터 끊길 때까지 같은 객체다. 접속 단계 이벤트(핸드셰이크·사전 로그인·로그인·구성)와
// 접속한 Player 가 같은 연결을 가리키므로 id() 로 단계 사이를 이을 수 있다. 읽기는 어느 스레드에서나 안전하다(최근 값).
// 내부 패킷 타입을 드러내지 않는다 — 마인크래프트 버전이 바뀌어도 이 형태는 그대로다.
package org.domi.furnace.api.net;

import java.net.InetSocketAddress;
import java.util.UUID;
import org.domi.furnace.api.text.Text;

public interface PlayerConnection {

    /** 서버 실행 동안 연결마다 다른 번호(1부터 늘어난다). 재시작하면 다시 1부터. */
    long id();

    /** 지금 단계. */
    ConnectionPhase phase();

    /** 연결이 살아 있는가. */
    boolean isConnected();

    /** 실제 클라이언트 주소(Velocity·BungeeCord 포워딩이면 프록시가 알려 준 주소). 모르면 null. */
    InetSocketAddress address();

    /**
     * 클라이언트가 핸드셰이크에 적은 서버 주소(가상 호스트, 예: "play.example.com"). 끝의 점과 FML 표식은 떼고,
     * BungeeCord 포워딩이면 첫 칸만 남긴다. 핸드셰이크 전이면 null.
     */
    String hostname();

    /** 핸드셰이크에 적은 포트. 핸드셰이크 전이면 0. */
    int hostPort();

    /** 클라이언트 프로토콜 번호(핸드셰이크 값 그대로). 핸드셰이크 전이면 -1. */
    int protocolVersion();

    /** 다른 서버에서 전송(transfer)으로 들어온 연결인가. */
    boolean isTransferred();

    /** 로그인 이름. Login Start 전이면 null. */
    String name();

    /** 인증(또는 포워딩)으로 정해진 UUID. 정해지기 전이면 null. */
    UUID uniqueId();

    /** 클라이언트가 minecraft:brand 로 알린 이름. 아직 모르면 null. */
    String clientBrand();

    /** 클라이언트 언어(client_information, 예: "en_us"). 받기 전이면 "en_us". */
    String locale();

    /**
     * 사유를 보여 주고 끊는다. 어느 스레드에서 불러도 된다. 단계에 맞는 끊기 패킷을 쓴다(상태·핸드셰이크 단계는 사유 없이 닫는다).
     * 플레이 단계면 Player.kick 과 같다(PlayerKickEvent 를 거친다). 이미 끊겼으면 아무 일도 없다.
     */
    void disconnect(Text reason);
}
