// 표준 서비스: 플레이스홀더 제공자(PlaceholderAPI 식). 확장(PlaceholderExpansion)은 이 서비스에 올리고, 소비자는 resolve 로 문자열을 푼다.
package org.domi.furnace.api.service;

import java.util.Set;
import java.util.UUID;
import org.domi.furnace.api.plugin.FurnacePlugin;

public interface PlaceholderService {

    String name();

    /** 확장을 올린다. 같은 identifier 가 있으면 IllegalArgumentException. owner 가 꺼지면 제공자가 풀어 준다. */
    void register(FurnacePlugin owner, PlaceholderExpansion expansion);

    boolean unregister(String identifier);

    Set<String> identifiers();

    /** "%identifier_params%" 하나의 값(없으면 null). 입력은 퍼센트 없이 "identifier_params". */
    String resolveOne(UUID player, String placeholder);

    /** 문자열 안의 "%...%" 를 풀어 돌려준다. 풀지 못한 것은 원문 그대로 둔다. */
    String resolve(UUID player, String text);
}
