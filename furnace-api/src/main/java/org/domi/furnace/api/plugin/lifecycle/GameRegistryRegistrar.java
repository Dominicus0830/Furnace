// 게임(동기화) 레지스트리 훅이 받는 등록기 — 몹 변종·그림·마법부여 등 데이터 주도 항목을 서버 시작 때 더한다(불만 표 27·32번).
// 서버는 켜진 데이터 팩을 고른 직후, 동기화 레지스트리를 읽을 때 이 훅을 한 번 부른다. 더한 항목은 데이터 팩 파일과 똑같이
// 다뤄진다: 파일 id 순서로 레지스트리에 들어가고, 클라에는 데이터째 보내진다(클라가 모르는 항목이므로). 같은 id 의 데이터 팩
// 파일이 있으면 데이터 팩이 이긴다. 레지스트리는 서버가 도는 동안 바뀌지 않는다 — 플러그인이 꺼져도 항목은 다음 시작까지 남고,
// addToTag 로 더한 태그 몫은 다음 /reload 부터 빠진다.
// 키·태그 id 는 "namespace:path". 더하는 항목의 namespace 는 자기 플러그인 namespace 여야 한다.
package org.domi.furnace.api.plugin.lifecycle;

import org.domi.furnace.api.registry.EnchantmentDefinition;
import org.domi.furnace.api.registry.PaintingVariant;
import org.domi.furnace.api.registry.WolfVariant;

public interface GameRegistryRegistrar {

    /** 자기 플러그인 namespace(더하는 키는 이것으로 시작해야 한다). */
    String namespace();

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
