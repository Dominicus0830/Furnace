// 월드젠 단계 훅 등록기(LifecycleEvents.WORLD_GENERATION). 키로 등록해 두면 월드가 키로 고른다.
package org.domi.furnace.api.plugin.lifecycle;

import org.domi.furnace.api.worldgen.ChunkGenerationHooks;

public interface WorldGenerationRegistrar {

    /**
     * 훅을 등록한다. 키는 "namespace:path" 이고 namespace 는 이 플러그인의 namespace 여야 한다(없으면 자동으로 붙는다).
     * 핫 리로드로 다시 불리면 같은 키의 훅을 새 것으로 바꾼다.
     *
     * @return 전체 키
     * @throws IllegalArgumentException 키 형식·namespace 가 틀렸거나 다른 플러그인이 이미 쓰는 키일 때
     */
    String register(String key, ChunkGenerationHooks hooks);
}
