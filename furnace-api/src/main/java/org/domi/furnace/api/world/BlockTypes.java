// 서버가 아는 블록 종류 표 — Furnace.blockTypes(). 읽기 전용, 아무 스레드.
package org.domi.furnace.api.world;

import java.util.Collection;
import java.util.Optional;

public interface BlockTypes {

    /** 네임스페이스는 생략할 수 있다. 모르는 블록이면 empty. */
    Optional<BlockType> get(String id);

    /** 모든 블록 키(이름순). */
    Collection<String> ids();
}
