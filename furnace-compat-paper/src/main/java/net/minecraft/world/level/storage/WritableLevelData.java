package net.minecraft.world.level.storage;

import net.minecraft.core.BlockPos;

/** 바닐라 쓸 수 있는 레벨 데이터(Mojang 이름 shim): 스폰을 정한다. */
public interface WritableLevelData extends LevelData {

    void setSpawn(BlockPos pos, float angle);
}
