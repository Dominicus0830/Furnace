// 살아 있는 엔티티의 지금 상태 손잡이 — EntityState 에 상태 효과와 밀림(충돌) 여부를 더한다. 같은 소유 스레드 규칙.
package org.domi.furnace.api.entity;

import java.util.List;
import java.util.Optional;

public interface LivingEntityState extends EntityState {

    /** 걸린 효과(걸린 순서). */
    List<MobEffect> effects();

    /** 그 종류의 효과(없으면 empty). */
    Optional<MobEffect> effect(String type);

    /**
     * 바닐라 LivingEntity.addEffect: 없으면 걸고, 같은 종류가 있으면 더 센·더 긴 쪽으로 갱신한다(약한 것은 숨은 효과로 남는다).
     * 걸 수 없는 엔티티·종류(언데드의 재생·독 등)거나 바뀐 것이 없으면 false. 투명 효과는 투명 비트를, 속성 효과는 속성을 곧바로 맞춘다.
     * 모르는 종류는 IllegalArgumentException.
     */
    boolean addEffect(MobEffect effect);

    /** 그 종류의 효과를 지운다. 있었으면 true. 모르는 종류는 IllegalArgumentException. */
    boolean removeEffect(String type);

    /** 모든 효과를 지운다. 지운 수. */
    int clearEffects();

    /**
     * 다른 엔티티에 밀리고 미는가(Paper LivingEntity#isCollidable). 기본 true, 저장되지 않는다.
     * false 면 바닐라 isPushable 이 false 가 되어 다른 엔티티가 밀지 않고 이 엔티티도 밀리지 않는다 — 클라이언트의 예측(플레이어 자신이 밀리는 것)은 그대로다.
     */
    boolean isCollidable();

    void setCollidable(boolean collidable);
}
