// 한 엔티티의 한 속성을 지금 값으로 다루는 손잡이(Bukkit AttributeInstance 와 같은 뜻) — 그 엔티티를 가진 region 스레드에서만 쓴다.
// 얻는 곳: {@code LivingEntityState#attribute}(editState/stateNow 안), {@link AttributeHandle#edit} 의 콜백. 콜백 밖으로 들고 나가지 않는다 —
// 호출마다 소유 스레드를 확인하고 아니면 ThreadOwnershipException 을 던진다.
package org.domi.furnace.api.entity.attribute;

import java.util.List;
import java.util.Optional;

public interface AttributeInstance {

    Attribute attribute();

    /** 이 속성의 기본값({@link Attribute#defaultValue} — Bukkit getDefaultValue 와 같다. 엔티티 종류가 정한 시작 값은 {@link #baseValue()}). */
    double defaultValue();

    /** 수정자를 빼고 정한 기본값(바닐라 getBaseValue). 범위로 자르지 않은 넣은 값 그대로다. */
    double baseValue();

    /** 기본값을 정한다(바닐라 setBaseValue). 범위 밖이어도 그대로 두고, {@link #value()} 가 범위로 자른다 — 최대 체력을 줄이면 체력도 따라 잘린다. */
    void setBaseValue(double value);

    /** 수정자까지 합친 지금 값(속성 범위로 자른다). */
    double value();

    /** 달려 있는 수정자 전부(단 순서). 장비·상태 효과·달리기가 단 것도 들어 있다(Bukkit 과 같다). */
    List<AttributeModifier> modifiers();

    Optional<AttributeModifier> modifier(String id);

    default boolean hasModifier(String id) { return modifier(id).isPresent(); }

    /**
     * 수정자를 단다 — 엔티티와 함께 **저장**된다(바닐라 addPermanentModifier, Bukkit addModifier). 같은 id 가 이미 있으면
     * IllegalArgumentException("Modifier is already applied on this attribute!").
     */
    void addModifier(AttributeModifier modifier);

    /** {@link #addModifier} 인데 저장하지 않는다(Paper addTransientModifier — 서버를 다시 켜면 사라진다). */
    void addTransientModifier(AttributeModifier modifier);

    /** 같은 id 가 있으면 바꾸고 없으면 단다(저장되는 수정자). Bukkit 에는 없는 편의 — remove 뒤 add 를 한 번에. */
    void setModifier(AttributeModifier modifier);

    /** id 의 수정자를 뗀다. 있었으면 true. */
    boolean removeModifier(String id);

    /** 이 속성의 모든 수정자를 뗀다(장비·효과 몫은 다음 동기에 다시 붙는다). */
    default void clearModifiers() { for (AttributeModifier m : modifiers()) removeModifier(m.id()); }
}
