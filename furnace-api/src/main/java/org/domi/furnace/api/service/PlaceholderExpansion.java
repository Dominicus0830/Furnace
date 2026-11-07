// 플레이스홀더 한 종류. "%identifier_params%" 에서 identifier 가 이것이다.
package org.domi.furnace.api.service;

import java.util.UUID;

public interface PlaceholderExpansion {

    /** 소문자 영숫자·'-' (밑줄 없음). */
    String identifier();

    /**
     * @param player 값을 묻는 플레이어(없으면 null — 서버 전역 값만 답한다)
     * @param params identifier 뒤 "_" 다음 부분(없으면 빈 문자열)
     * @return 값, 모르면 null(원문이 그대로 남는다)
     */
    String onRequest(UUID player, String params);
}
