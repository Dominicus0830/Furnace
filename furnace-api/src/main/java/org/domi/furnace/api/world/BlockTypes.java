// 서버가 아는 블록 종류 표 — Furnace.blockTypes(). 읽기 전용, 아무 스레드.
package org.domi.furnace.api.world;

import java.util.Collection;
import java.util.Optional;

public interface BlockTypes {

    /** 네임스페이스는 생략할 수 있다. 모르는 블록이면 empty. */
    Optional<BlockType> get(String id);

    /** 모든 블록 키(이름순). */
    Collection<String> ids();

    /**
     * 바닐라 블록 상태 번호(1.21.1 의 {@code Block.getId}, 프로토콜이 쓰는 번호와 같다). 적지 않은 속성은 기본 상태 값을 쓴다.
     * 모르는 블록·속성·값이면 -1. 번호는 마인크래프트 버전마다 바뀌므로 저장하지 말 것(NMS 호환용).
     */
    default int stateId(BlockData data) { throw new UnsupportedOperationException("stateId"); }

    /** 번호의 상태(속성 전부 적힘). 범위 밖이면 empty. */
    default Optional<BlockData> byStateId(int stateId) { throw new UnsupportedOperationException("byStateId"); }

    /** 상태 번호의 개수(1.21.1 = 26684). 번호는 0 부터 이 값 미만. */
    default int stateCount() { throw new UnsupportedOperationException("stateCount"); }
}
