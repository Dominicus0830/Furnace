// 살아 있는 엔티티의 지금 상태 손잡이 — EntityState 에 상태 효과와 밀림(충돌) 여부를 더한다. 같은 소유 스레드 규칙.
package org.domi.furnace.api.entity;

import java.util.List;
import java.util.Optional;
import org.domi.furnace.api.entity.attribute.Attribute;
import org.domi.furnace.api.entity.attribute.AttributeInstance;

public interface LivingEntityState extends EntityState {

    /** 지금 체력. LivingEntity.health() 는 핸들을 만든 시점 값이고, 이것은 소유 스레드에서 읽는 지금 값이다. */
    float health();

    /** 지금 최대 체력(속성 반영). */
    float maxHealth();

    /** 0..최대 체력으로 잘라 곧바로 적용한다. 0 이하면 죽는다(LivingEntity.updateHealth 와 같다). 사라진 엔티티에는 아무 일도 하지 않는다. */
    void setHealth(float health);

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

    // ------------------------------------------------------------------ 속성 (지금 값, 곧바로 쓴다)

    /** 이 속성의 지금 값 손잡이. 엔티티가 그 속성을 가지지 않으면 empty. */
    Optional<AttributeInstance> attribute(Attribute attribute);

    /** 이 엔티티가 가진 속성들(레지스트리 순서). */
    List<Attribute> attributes();

    /** 없는 속성을 단다(기본값은 속성 기본값, 클라이언트로는 보내지 않는다 — Paper registerAttribute). 이미 있으면 false. */
    boolean registerAttribute(Attribute attribute);
}
