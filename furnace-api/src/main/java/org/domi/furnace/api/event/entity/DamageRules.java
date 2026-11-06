// 서버가 이벤트에 넘기는 "바닐라 기본 계산". 플러그인 구현용이 아니다 — 서버가 엔티티의 현재 상태(속성·효과·장비)로 만든다.
// ABSORPTION 단계는 이벤트가 직접 계산하므로 여기에 없다.
package org.domi.furnace.api.event.entity;

public interface DamageRules {

    /** 이 피해에 그 단계가 바닐라에서 적용되는가(피해 유형 태그·효과·장비 조건). */
    boolean applies(DamageStage stage);

    /** 바닐라 식: input 을 넣었을 때 그 단계 뒤의 값. 적용 여부와 상관없이 식 자체를 계산한다(적용 안 되는 조건이면 input 그대로). */
    float apply(DamageStage stage, float input);
}
