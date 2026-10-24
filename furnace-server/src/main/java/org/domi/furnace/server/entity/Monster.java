// 바닐라 Monster: 적대 몹 — 경험치 5, 밝은 곳에서 무행동 시간이 두 배로 쌓여(그 몹은 사라지기 쉬움), 어두운 칸을 선호한다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;

public abstract class Monster extends PathfinderMob {

    protected Monster(EntityType type, ChunkLevel level, int id) {
        super(type, level, id);
        xpReward = 5;
    }

    @Override
    protected void createAttributes(AttributeMap map) {   // Monster.createMonsterAttributes
        super.createAttributes(map);
        map.add(Attribute.ATTACK_DAMAGE, 2.0);
    }

    @Override
    protected void aiStep() {
        updateSwingTime();
        updateNoActionTime();
        super.aiStep();
    }

    /** Monster.updateNoActionTime: 밝기(getLightLevelDependentMagicValue)가 0.5 넘으면 2 더 쌓는다. */
    protected void updateNoActionTime() {
        float f = getLightLevelDependentMagicValue();
        if (f > 0.5F) noActionTime += 2;
    }

    /** Entity.getLightLevelDependentMagicValue: 눈 높이 칸의 밝기(청크가 없으면 0). 오버월드는 ambientLight 0 → 곡선 f/(4-3f). */
    public float getLightLevelDependentMagicValue() {
        Pos pos = new Pos((int) Math.floor(x()), (int) Math.floor(eyeY()), (int) Math.floor(z()));
        int light = level.getMaxLocalRawBrightness(pos);
        float f = light / 15.0F;
        return f / (4.0F - 3.0F * f);
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
}
