package net.minecraft.world.level.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;

/**
 * 바닐라 레벨 데이터 읽기 면(Mojang 이름 shim, 효과·프로필·속성 묶음). 구현은 {@link PrimaryLevelData} 하나다. 충돌 보고서 칸({@code fillCrashReportCategory})은
 * shim 이 {@code CrashReportCategory} 를 갖지 않아 두지 않았다.
 */
public interface LevelData {

    BlockPos getSpawnPos();

    float getSpawnAngle();

    long getGameTime();

    long getDayTime();

    boolean isThundering();

    boolean isRaining();

    void setRaining(boolean raining);

    boolean isHardcore();

    GameRules getGameRules();

    Difficulty getDifficulty();

    boolean isDifficultyLocked();
}
