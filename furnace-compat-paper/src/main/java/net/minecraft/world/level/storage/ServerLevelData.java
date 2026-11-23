package net.minecraft.world.level.storage;

import java.util.UUID;
import net.minecraft.world.level.GameType;

/**
 * 바닐라 서버 레벨 데이터(Mojang 이름 shim, {@link PrimaryLevelData} 가 구현). 예약 일정({@code getScheduledEvents})·월드 보더 설정·충돌 보고서 칸은 shim 이
 * 그 형식을 갖지 않아 두지 않았다.
 */
public interface ServerLevelData extends WritableLevelData {

    String getLevelName();

    void setThundering(boolean thundering);

    int getRainTime();

    void setRainTime(int time);

    void setThunderTime(int time);

    int getThunderTime();

    int getClearWeatherTime();

    void setClearWeatherTime(int time);

    int getWanderingTraderSpawnDelay();

    void setWanderingTraderSpawnDelay(int delay);

    int getWanderingTraderSpawnChance();

    void setWanderingTraderSpawnChance(int chance);

    UUID getWanderingTraderId();

    void setWanderingTraderId(UUID id);

    GameType getGameType();

    void setGameType(GameType type);

    boolean isInitialized();

    void setInitialized(boolean initialized);

    boolean isAllowCommands();

    void setGameTime(long time);

    void setDayTime(long time);
}
