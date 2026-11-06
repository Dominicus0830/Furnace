// 늑대 변종(minecraft:wolf_variant) 정의 — 버전 중립. 텍스처는 리소스 위치(클라 리소스 팩에 있어야 보인다, 예: "minecraft:entity/wolf/wolf_ashen").
// biomes: 이 변종이 자연 스폰되는 바이옴. "#minecraft:is_forest" 같은 태그 하나 또는 바이옴 id 목록.
// 자연 스폰은 레지스트리 순서(id 의 경로 → namespace)로 바이옴이 맞는 첫 변종을 고른다(바닐라와 같다) — 바닐라 변종과 바이옴이 겹치면
// id 순서에 따라 어느 쪽이 될지 정해진다.
package org.domi.furnace.api.registry;

import java.util.List;

public record WolfVariant(String wildTexture, String tameTexture, String angryTexture, List<String> biomes) {

    public WolfVariant {
        java.util.Objects.requireNonNull(wildTexture, "wildTexture");
        java.util.Objects.requireNonNull(tameTexture, "tameTexture");
        java.util.Objects.requireNonNull(angryTexture, "angryTexture");
        biomes = HolderSets.check("biomes", biomes, true);
    }
}
