package net.minecraft.world.level.storage;

import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;

/**
 * 바닐라 월드 데이터(level.dat 의 윗면, Mojang 이름 shim, 효과·프로필·속성 묶음). {@code MinecraftServer#getWorldData()} 가 주고 구현은 {@link PrimaryLevelData} 다.
 * 끝 용 싸움 데이터({@code endDragonFightData})·충돌 보고서 칸·{@code enabledFeatures}·{@code createTag}·보스바 NBT 는 shim 이 그 형식을 갖지 않거나 저장 형식(NBT)이
 * 없어 두지 않았다(부르면 없는 멤버).
 */
public interface WorldData {

    int ANVIL_VERSION_ID = 19133;
    int MCREGION_VERSION_ID = 19132;

    WorldDataConfiguration getDataConfiguration();

    void setDataConfiguration(WorldDataConfiguration configuration);

    boolean wasModded();

    Set<String> getKnownServerBrands();

    Set<String> getRemovedFeatureFlags();

    void setModdedInfo(String brand, boolean modded);

    ServerLevelData overworldData();

    LevelSettings getLevelSettings();

    CompoundTag getLoadedPlayerTag();

    CompoundTag getCustomBossEvents();

    void setCustomBossEvents(CompoundTag tag);

    String getLevelName();

    void setDifficulty(Difficulty difficulty);

    void setDifficultyLocked(boolean locked);

    Difficulty getDifficulty();

    boolean isDifficultyLocked();

    boolean isHardcore();

    boolean isAllowCommands();

    GameType getGameType();

    void setGameType(GameType type);

    GameRules getGameRules();

    boolean isFlatWorld();

    boolean isDebugWorld();

    int getVersion();

    WorldOptions worldGenOptions();

    com.mojang.serialization.Lifecycle worldGenSettingsLifecycle();
}
