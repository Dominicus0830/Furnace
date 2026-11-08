// 상태 효과 하나(바닐라 MobEffectInstance 의 값) — 종류 키, 지속 틱, 증폭, 환경(비콘)·입자·아이콘 표시. 불변 값이라 어느 스레드에서나 쓴다.
package org.domi.furnace.api.entity;

import java.util.Objects;

/**
 * @param type      효과 키(minecraft:invisibility)
 * @param duration  남은 틱. {@link #INFINITE} 이면 끝나지 않는다
 * @param amplifier 증폭(0 = I 단계). 서버가 0..255 로 자른다(바닐라와 같다)
 * @param ambient   비콘·전달체에서 온 효과처럼 입자를 옅게
 * @param particles 몸에서 입자가 나는가
 * @param icon      화면 오른쪽 위에 아이콘을 보이는가
 */
public record MobEffect(String type, int duration, int amplifier, boolean ambient, boolean particles, boolean icon) {

    /** 끝나지 않는 지속시간(바닐라 MobEffectInstance.INFINITE_DURATION). */
    public static final int INFINITE = -1;

    public MobEffect {
        Objects.requireNonNull(type, "type");
        if (type.indexOf(':') < 0) type = "minecraft:" + type;
    }

    /** 입자·아이콘을 보이는 평범한 효과. */
    public static MobEffect of(String type, int duration, int amplifier) { return new MobEffect(type, duration, amplifier, false, true, true); }
}
