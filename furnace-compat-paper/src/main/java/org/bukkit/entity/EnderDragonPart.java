package org.bukkit.entity;

/** Paper 1.21.1 EnderDragonPart: 엔더 드래곤의 몸통 한 부분. 형만 있다(ComplexEntityPart 참고). */
public interface EnderDragonPart extends ComplexEntityPart, Damageable {

    @Override
    EnderDragon getParent();
}
