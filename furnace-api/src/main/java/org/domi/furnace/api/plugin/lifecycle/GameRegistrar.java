// 게임 레지스트리 훅이 받는 등록기 — 클라이언트와 동기되는 레지스트리에 플러그인 항목을 더한다.
//  - 바이옴·차원 타입(불만 표 39번): 바닐라 항목 뒤에 등록 순서로 붙는다.
//  - 몹 변종·그림·마법부여 등 데이터 주도 항목(불만 표 27·32번): 데이터 팩 파일과 똑같이 다뤄진다 — 파일 id 순서로 레지스트리에
//    들어가고 클라에는 데이터째 보내진다. 같은 id 의 데이터 팩 파일이 있으면 데이터 팩이 이긴다. 플러그인이 꺼져도 항목은 다음
//    시작까지 남고, addToTag 로 더한 태그 몫은 다음 /reload 부터 빠진다.
// 이 훅은 서버가 레지스트리를 고정하기 전 시작 때 한 번만 불린다. 고정된 뒤에는 항목을 더할 수 없다(클라이언트가 접속 때 레지스트리를 한 번 받기 때문).
// 키·태그 id 는 "namespace:path". 더하는 항목의 namespace 는 자기 플러그인 namespace 여야 한다(없으면 자동으로 붙는다).
package org.domi.furnace.api.plugin.lifecycle;

import org.domi.furnace.api.registry.EnchantmentDefinition;
import org.domi.furnace.api.registry.PaintingVariant;
import org.domi.furnace.api.registry.WolfVariant;
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

    /** 늑대 변종을 더한다(minecraft:wolf_variant). */
    void wolfVariant(String key, WolfVariant variant);

    /** 그림 변종을 더한다(minecraft:painting_variant). */
    void paintingVariant(String key, PaintingVariant variant);

    /** 마법부여를 더한다(minecraft:enchantment). */
    void enchantment(String key, EnchantmentDefinition definition);

    /**
     * 데이터 팩 JSON 그대로 더한다 — 위 타입이 없는 레지스트리(minecraft:banner_pattern, trim_material, trim_pattern,
     * jukebox_song)나 효과(effects)가 있는 마법부여용. JSON 꼴은 마인크래프트 버전에 묶인다(데이터 팩 형식과 같다).
     * 읽을 수 없는 JSON 이면 IllegalArgumentException.
     */
    void json(String registry, String key, String json);

    /**
     * 태그에 항목을 더한다(데이터 팩 태그 파일의 replace=false 와 같다 — 태그 끝에 붙는다). 리로드마다 다시 적용되고,
     * 플러그인이 꺼지면 다음 /reload 부터 빠진다. 바닐라 태그(예: minecraft:in_enchanting_table)에도 넣을 수 있다.
     * 태그를 클라에 보내는 레지스트리만 된다(마법부여·그림 변종·현수막 무늬·아이템·블록 등).
     */
    void addToTag(String registry, String tag, String... entries);
}
