package org.bukkit.entity;

/** Paper 1.21.1 ComplexEntityPart: 여러 몸통으로 된 엔티티의 한 부분. Furnace 는 아직 부분 엔티티를 Bukkit 에 드러내지 않는다(형만 있다). */
public interface ComplexEntityPart extends Entity {

    ComplexLivingEntity getParent();
}
