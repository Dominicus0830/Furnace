// 피해 형식(minecraft:damage_type) 정의. 버전 중립이다. 값은 바닐라 데이터 팩 JSON 과 같은 꼴이다:
// scaling 은 "never"·"when_caused_by_living_non_player"·"always", effects 는 "hurt"·"thorns"·"drowning"·"burning"·"poking"·"freezing",
// deathMessageType 은 "default"·"fall_variants"·"intentional_game_design". msgId 는 죽음 메시지 번역 키 "death.attack.<msgId>" 의 끝.
package org.domi.furnace.api.registry;

public record DamageType(String msgId, String scaling, float exhaustion, String effects, String deathMessageType) {

    public DamageType {
        java.util.Objects.requireNonNull(msgId, "msgId");
        java.util.Objects.requireNonNull(scaling, "scaling");
        java.util.Objects.requireNonNull(effects, "effects");
        java.util.Objects.requireNonNull(deathMessageType, "deathMessageType");
    }
}
