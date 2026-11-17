package org.bukkit;

/**
 * Paper 1.21.1 Nameable: 이름을 붙일 수 있는 것(엔티티, 이름 붙인 용기·신호기·현수막·마법 부여대 상태). 레거시 § 문자열로 읽고 쓴다.
 * Adventure 판(customName(Component))은 호환 계층에 Adventure 가 없어 빠졌다.
 */
public interface Nameable {

    /** 붙인 이름(§ 서식 코드 포함), 없으면 null. */
    String getCustomName();

    /** 이름을 붙인다. null·빈 문자열이면 뗀다. */
    void setCustomName(String name);
}
