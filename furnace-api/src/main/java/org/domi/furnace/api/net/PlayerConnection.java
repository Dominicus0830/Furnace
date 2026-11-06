// 클라이언트 연결 하나 — 핸드셰이크부터 끊길 때까지 같은 객체다. 접속 단계 이벤트(핸드셰이크·사전 로그인·로그인·구성)와
// 접속한 Player 가 같은 연결을 가리키므로 id() 로 단계 사이를 이을 수 있다. 읽기는 어느 스레드에서나 안전하다(최근 값).
// 내부 패킷 타입을 드러내지 않는다 — 마인크래프트 버전이 바뀌어도 이 형태는 그대로다.
package org.domi.furnace.api.net;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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

    /**
     * 끊긴 이유. 끊기기 전에는 null. 로그인·구성 단계에서 끊겨 PlayerQuitEvent 가 없는 연결도 이 값으로 알 수 있다.
     * (PlayerQuitEvent.reason() 과 같은 값이다.)
     */
    QuitReason quitReason();

    // ---- 구성·플레이 중 클라이언트와 주고받기 ----
    // 아래 메서드는 어느 스레드에서나 부를 수 있다(패킷은 연결의 이벤트 루프로 넘겨 보낸다). Future 는 응답이 오면 완료되고,
    // 연결이 끊기면 ConnectionClosedException 으로 완료된다 — AsyncPlayerConfigureEvent 처리기가 get()/join() 으로 기다려도
    // 연결이 끊기면 풀려난다. 응답을 영원히 안 주는 클라이언트에 대비해 orTimeout 을 거는 것은 부르는 쪽 몫이다.
    // 허용되지 않는 단계에서 부르면 Future 를 돌려주는 메서드는 IllegalStateException 으로 이미 완료된 Future 를, 돌려주지 않는
    // 메서드는 IllegalStateException 을 던진다. 인자가 잘못되면(쿠키 키 꼴·값 크기) 둘 다 IllegalArgumentException 을 던진다.
    // Future 의 후속 처리(thenApply 등)는 연결의 네트워크 스레드가 아니라 플러그인 비동기 실행기에서 돈다.

    /**
     * 리소스 팩을 내려받게 한다(구성·플레이 단계). 돌려준 Future 는 클라이언트의 끝 상태 응답(적용 성공·거절·실패…)이 오면 그 값으로
     * 완료된다. 필수 팩({@link ResourcePack#required()})을 거절하면 서버가 클라이언트를 내보내고(Future 는 DECLINED 로 완료),
     * 그 밖의 실패는 그대로 알려 줄 뿐 연결을 끊지 않는다. 같은 id 를 이미 기다리는 중이면 그 Future 를 돌려준다.
     */
    CompletableFuture<ResourcePackStatus> sendResourcePack(ResourcePack pack);

    /** id 가 같은 리소스 팩을 클라이언트에서 뺀다(구성·플레이 단계). 기다리던 Future 는 DISCARDED 로 완료된다. */
    void removeResourcePack(UUID id);

    /** 서버가 보낸 리소스 팩을 모두 뺀다(구성·플레이 단계). */
    void removeAllResourcePacks();

    /**
     * 클라이언트에 저장된 쿠키를 읽어 오게 한다(구성 단계만). 키는 {@code namespace:path} 꼴이고 없는 쿠키는 null 로 완료된다.
     * 같은 키를 이미 기다리는 중이면 그 Future 를 돌려준다.
     */
    CompletableFuture<byte[]> requestCookie(String key);

    /** 클라이언트에 쿠키를 저장하게 한다(구성 단계만, 바닐라 상한: 값 5120바이트). 전송(transfer)으로 다른 서버에 넘어가도 남는다. */
    void storeCookie(String key, byte[] value);

    /** 클라이언트를 다른 서버로 옮긴다(구성·플레이 단계). 받는 서버가 전송을 받아야 한다. */
    void transfer(String host, int port);
}
