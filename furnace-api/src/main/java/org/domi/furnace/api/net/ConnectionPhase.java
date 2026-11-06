// 연결이 지금 어느 단계인가. 마인크래프트 프로토콜의 단계 이름을 따르지만 버전에 매이지 않는다(새 버전이 단계를 늘리면 여기에 더한다).
package org.domi.furnace.api.net;

public enum ConnectionPhase {
    /** 첫 패킷(핸드셰이크)을 기다린다. */
    HANDSHAKE,
    /** 서버 목록 조회(상태·핑). 이 연결로는 들어오지 않는다. */
    STATUS,
    /** 로그인(인증·접속 검사). */
    LOGIN,
    /** 구성(레지스트리·태그·리소스 팩). 플레이 중 재구성도 이 단계다. */
    CONFIGURATION,
    /** 월드에 들어와 플레이 중. */
    PLAY,
    /** 끊김. */
    CLOSED
}
