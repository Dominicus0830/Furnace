// 바닐라 Monster: 적대 몹 — 경험치 5, 밝은 곳에서 무행동 시간이 두 배로 쌓여(그 몹은 사라지기 쉬움), 어두운 칸을 선호한다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;

public abstract class Monster extends PathfinderMob implements Enemy {

    protected Monster(EntityType type, ChunkLevel level, int id) {
        super(type, level, id);
        xpReward = 5;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() { return true; }

    /** Monster.isPreventingPlayerRest: 근처에 있으면 그 플레이어가 잘 수 없다(좀비 피글린은 화난 상대에게만). */
    public boolean isPreventingPlayerRest(Player player) { return true; }

    @Override
    protected void createAttributes(AttributeMap map) {   // Monster.createMonsterAttributes
        super.createAttributes(map);
        map.add(Attribute.ATTACK_DAMAGE, 2.0);
    }

    @Override
    protected void aiStep() {
        updateNoActionTime();
        super.aiStep();
    }

    /** Monster.updateNoActionTime: 밝기(getLightLevelDependentMagicValue)가 0.5 넘으면 2 더 쌓는다. */
    protected void updateNoActionTime() {
        float f = getLightLevelDependentMagicValue();
        if (f > 0.5F) noActionTime += 2;
    }

    @Override
    public float getWalkTargetValue(Pos pos, ChunkLevel level) {
        int light = level.getMaxLocalRawBrightness(pos);
        float f = light / 15.0F;
        return -(f / (4.0F - 3.0F * f));
    }

    @Override public String soundSource() { return "hostile"; }

    @Override protected String getHurtSound(Damage source) { return "entity.hostile.hurt"; }

    @Override protected String getDeathSound() { return "entity.hostile.death"; }

    @Override protected boolean shouldDropExperience() { return true; }

    /** Monster.getSwimSound·getSwimSplashSound: hostile 소리. */
    @Override protected String getSwimSound() { return "minecraft:entity.hostile.swim"; }

    @Override protected String swimSplashSound() { return "minecraft:entity.hostile.splash"; }
}
