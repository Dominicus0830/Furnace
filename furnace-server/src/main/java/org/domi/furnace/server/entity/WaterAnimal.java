// 바닐라 WaterAnimal: 물에서 사는 몹 — 물 길찾기 비용 0, 물 밖에서는 숨이 줄어 질식(-20 에 2 피해), 경험치는 레벨 난수로 1~3.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.navigation.PathType;

public abstract class WaterAnimal extends PathfinderMob {

    protected WaterAnimal(EntityType type, ChunkLevel level, int id) {
        super(type, level, id);
        setPathfindingMalus(PathType.WATER, 0.0F);
    }

    @Override
    public int getAmbientSoundInterval() { return 120; }

    @Override
    protected int getExperienceReward() { return 1 + level.random().nextInt(3); }

    protected void handleAirSupply(int previousAir) {
        if (isAlive() && !isInWaterOrBubble()) {
            setAirSupply(previousAir - 1);
            if (getAirSupply() == -20) {
                setAirSupply(0);
                hurt(new Damage("drown", false, false, false), 2.0F);
            }
        } else {
            setAirSupply(300);
        }
    }

    @Override
    public void baseTick() {
        int air = getAirSupply();
        super.baseTick();
        handleAirSupply(air);
    }

    @Override
    public boolean isPushedByFluid() { return false; }

    @Override
    public boolean checkSpawnObstruction(ChunkLevel level) { return level.isUnobstructed(org.domi.furnace.server.entity.spawn.NaturalSpawner.shapeOf(boundingBox())); }
}
