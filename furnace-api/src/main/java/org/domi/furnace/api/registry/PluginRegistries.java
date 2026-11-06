// 플러그인끼리 공유하는 정의 레지스트리(예: 한 플러그인이 "능력" 레지스트리를 만들고 다른 플러그인이 능력을 넣는다).
// 이 보기는 한 플러그인에 묶여 있다 — 여기서 만들거나 넣은 것은 그 플러그인 소유로 기록되고, 플러그인이 꺼지면 정리된다:
//  - 그 플러그인이 만든 레지스트리는 항목째 사라진다(다른 플러그인이 쥔 보기는 비어 보이고 쓰기는 IllegalStateException).
//  - 그 플러그인이 남의 레지스트리에 넣은 항목은 빠진다(고정된 레지스트리라도).
// 키는 "namespace:path"(바닐라 리소스 위치 규칙). 만드는 레지스트리와 넣는 항목의 namespace 는 자기 플러그인 namespace 여야 한다.
// 어느 스레드에서 불러도 된다. 읽기는 잠금 없는 스냅숏이다.
package org.domi.furnace.api.registry;

import java.util.Collection;

public interface PluginRegistries {

    /** 이 보기의 플러그인 namespace(PluginDescription.namespace()). */
    String namespace();

    /**
     * 새 레지스트리를 만든다. key 의 namespace 는 자기 namespace 여야 하고, 이미 있으면 IllegalArgumentException.
     * 부트스트랩 단계에서 만들면 다른 플러그인의 부트스트랩·onLoad 에서 바로 쓸 수 있다.
     */
    <T> PluginRegistry<T> create(String key, Class<T> type);

    /** 있는 레지스트리(이 플러그인 이름으로 쓰는 보기). 없으면 null, 값 타입이 맞지 않으면 IllegalArgumentException. */
    <T> PluginRegistry<T> registry(String key, Class<T> type);

    /** 지금 있는 레지스트리 키(만든 순서). */
    Collection<String> keys();
}
