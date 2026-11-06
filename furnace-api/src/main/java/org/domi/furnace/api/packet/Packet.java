// 패킷 API 의 공통 형태 — 버전 중립 값 객체(불변 레코드). 숫자 패킷 id·바이트 배치는 드러내지 않는다.
// 서버가 실행 중인 마인크래프트 버전의 변환 계층이 이 값과 실제 와이어 형식을 오간다.
package org.domi.furnace.api.packet;

public interface Packet {

    /** 이 패킷의 종류(방향 + 이름). */
    PacketType<?> type();
}
