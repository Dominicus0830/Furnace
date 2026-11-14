package org.bukkit.entity;

/**
 * Paper 1.21.1 Damageable. 체력 메서드는 Paper 처럼 여기 선언한다 — {@code ((Damageable) e).getHealth()} 로 컴파일한 플러그인이
 * Damageable 에서 메서드를 찾는다. 피해 주기·흡수량·최대 체력 쓰기는 아직 없다.
 */
public interface Damageable extends Entity {

    double getHealth();

    void setHealth(double health);

    double getMaxHealth();
}
