// 피해 계산의 한 단계. 바닐라는 받은 피해를 이 순서대로 줄여 가며 마지막에 체력을 깎는다.
// 단계 이름은 마인크래프트 버전과 무관한 "개념"이다(버전마다 세부 식이 달라도 같은 이름으로 다룬다).
package org.domi.furnace.api.event.entity;

public enum DamageStage {

    /** 방어력·방어 강인함(관통 인챈트 포함). bypasses_armor 피해는 건너뛴다. */
    ARMOR,

    /** 저항 효과(단계당 20%). bypasses_effects·bypasses_resistance 피해는 건너뛴다. */
    RESISTANCE,

    /** 방어구 보호 인챈트(보호·화염·폭발·화살·가벼운 착지). bypasses_effects·bypasses_enchantments 피해는 건너뛴다. */
    ENCHANTMENT,

    /** 엔티티 고유 경감(예: 마녀의 마법 피해 85% 감소). 해당하는 엔티티·피해만 적용된다. */
    ENTITY,

    /** 흡수 체력. 이 단계의 줄어든 양이 흡수 체력에서 빠지고, 남은 값이 체력에서 깎인다. */
    ABSORPTION;

    /** 바닐라 계산 순서. */
    public static final java.util.List<DamageStage> ORDER = java.util.List.of(values());
}
