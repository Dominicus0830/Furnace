// 게임 레지스트리 훅이 받는 등록기 — 클라이언트와 동기되는 레지스트리(바이옴·차원 타입)에 플러그인 항목을 더한다.
// 이 훅은 서버가 레지스트리를 고정하기 전 시작 때 한 번만 불린다. 고정된 뒤에는 항목을 더할 수 없다(클라이언트가 접속 때 레지스트리를 한 번 받기 때문).
package org.domi.furnace.api.plugin.lifecycle;

import org.domi.furnace.api.worldgen.BiomeDefinition;
import org.domi.furnace.api.worldgen.DimensionTypeDefinition;

public interface GameRegistrar {

    /**
     * 바이옴을 등록한다. 키는 "namespace:path" 이고 namespace 는 이 플러그인의 namespace 여야 한다(없으면 자동으로 붙는다).
     * 바닐라 바이옴 뒤에 등록 순서대로 붙는다. 청크에는 이름으로 저장되므로 플러그인이 빠져도 월드는 열리고(그 바이옴은 평원으로 읽힌다),
     * 다시 넣으면 그대로 돌아온다.
     *
     * @return 전체 키
     * @throws IllegalArgumentException 키 형식·namespace 가 틀렸거나 이미 있는 키일 때
     */
    String registerBiome(String key, BiomeDefinition definition);

    /**
     * 차원 타입을 등록한다. 키 규칙은 바이옴과 같다. 이 타입을 쓰는 차원(월드)은 다중 월드 API 가 만든다.
     *
     * @return 전체 키
     */
    String registerDimensionType(String key, DimensionTypeDefinition definition);

    /** 지금까지 등록된 바이옴 키(바닐라와 앞선 플러그인 것 포함)인가. */
    boolean hasBiome(String key);

    /** 지금까지 등록된 차원 타입 키인가. */
    boolean hasDimensionType(String key);
}
