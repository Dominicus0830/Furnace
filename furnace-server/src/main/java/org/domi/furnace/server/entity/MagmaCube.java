// 바닐라 MagmaCube: 불에 면역인 슬라임. 크기 × 3 방어도, 더 오래 쉬며(점프 지연 ×4) 높게 뛴다. 닿으면 공격력 +2.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;

public class MagmaCube extends Slime {

    public MagmaCube(ChunkLevel level, int id) { super(EntityType.MAGMA_CUBE, level, id); }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MOVEMENT_SPEED, 0.2F);
    }

    @Override
    protected void onSizeSet(int size) { attributes().get(Attribute.ARMOR).setBaseValue(size * 3); }

    @Override public float getLightLevelDependentMagicValue() { return 1.0F; }

    @Override public boolean isOnFire() { return false; }

    @Override protected int getJumpDelay() { return super.getJumpDelay() * 4; }

    @Override
    public void jumpFromGround() {
        setDeltaMovement(vx(), getJumpPower() + getSize() * 0.1F, vz());
        hasImpulse = true;
    }

    @Override
    protected void jumpInLiquid(boolean lava) {
        if (lava) {
            setDeltaMovement(vx(), 0.22F + getSize() * 0.05F, vz());
            hasImpulse = true;
        } else {
            super.jumpInLiquid(false);
        }
    }

    @Override protected boolean isDealsDamage() { return isEffectiveAi(); }

    @Override protected float getAttackDamage() { return super.getAttackDamage() + 2.0F; }

    @Override protected String getHurtSound(Damage source) { return isTiny() ? "entity.magma_cube.hurt_small" : "entity.magma_cube.hurt"; }

    @Override protected String getDeathSound() { return isTiny() ? "entity.magma_cube.death_small" : "entity.magma_cube.death"; }

    @Override protected String getSquishSound() { return isTiny() ? "entity.magma_cube.squish_small" : "entity.magma_cube.squish"; }

    @Override protected String getJumpSound() { return "entity.magma_cube.jump"; }
}
