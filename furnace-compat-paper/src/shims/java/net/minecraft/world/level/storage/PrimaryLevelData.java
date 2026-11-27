package net.minecraft.world.level.storage;

import com.mojang.serialization.Lifecycle;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.WorldDataConfiguration;
import org.domi.furnace.compat.nms.UnsupportedNmsCall;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.levelgen.WorldOptions;

/**
 * 바닐라 주 레벨 데이터(level.dat, Mojang 이름, T-203): {@code ServerLevel#serverLevelData}. 서버 월드의 것은 그 월드에서 그때그때 읽고
 * (시드·구조물·평지/디버그), 플러그인이 만든 것(Paper 의 따로 떨어진 레벨에 넘기는 것)은 받은 값 그대로다. 저장 형식(NBT)은 없다.
 */
class PrimaryLevelData {

    /** 바닐라 특수 월드 종류. */
    public enum SpecialWorldProperty { NONE, FLAT, DEBUG }

    /** CraftBukkit 이 연 칸. */
    public LevelSettings settings;
    private final WorldOptions worldOptions;
    private final SpecialWorldProperty specialWorldProperty;
    private final org.bukkit.craftbukkit.CraftWorld live;
    private final Lifecycle lifecycle;
    /** 플러그인이 만든 레벨 데이터의 시간·날씨·스폰 칸(서버 월드는 그 월드에서 읽는다). */
    private long scratchGameTime, scratchDayTime;
    private boolean scratchRaining, scratchThundering, scratchInitialized;
    private int scratchRainTime, scratchThunderTime, scratchClearTime, scratchTraderDelay, scratchTraderChance;
    private UUID scratchTraderId;
    private BlockPos scratchSpawn = BlockPos.ZERO;
    private float scratchSpawnAngle;
    private GameType scratchGameType;
    private Difficulty scratchDifficulty;
    private boolean scratchDifficultyLocked;

    public PrimaryLevelData(LevelSettings settings, WorldOptions worldOptions, SpecialWorldProperty specialWorldProperty, Lifecycle worldGenSettingsLifecycle) {
        this.settings = settings;
        this.worldOptions = worldOptions;
        this.specialWorldProperty = specialWorldProperty;
        this.live = null;
        this.lifecycle = worldGenSettingsLifecycle;
        this.scratchGameType = settings.gameType();
        this.scratchDifficulty = settings.difficulty();
    }

    private PrimaryLevelData(org.bukkit.craftbukkit.CraftWorld world) {
        this.settings = LevelSettings.of(world);
        this.worldOptions = null;
        this.specialWorldProperty = null;
        this.live = world;
        this.lifecycle = Lifecycle.stable();
    }

    /** 호환 모듈 전용: 서버 월드의 레벨 데이터. */
    public static PrimaryLevelData of(org.bukkit.craftbukkit.CraftWorld world) { return new PrimaryLevelData(world); }

    public WorldOptions worldGenOptions() {
        if (live == null) return worldOptions;
        return new WorldOptions(live.getSeed(), live.canGenerateStructures(), false);
    }

    private SpecialWorldProperty special() {
        if (live == null) return specialWorldProperty;
        return switch (org.domi.furnace.api.Furnace.server().internals().scratchWorlds().generatorKind(live.furnaceHandle())) {
            case "flat" -> SpecialWorldProperty.FLAT;
            case "debug" -> SpecialWorldProperty.DEBUG;
            default -> SpecialWorldProperty.NONE;
        };
    }

    public boolean isFlatWorld() { return special() == SpecialWorldProperty.FLAT; }

    public boolean isDebugWorld() { return special() == SpecialWorldProperty.DEBUG; }

    public String getLevelName() { return settings.levelName(); }

    /** CraftBukkit: 이름이 다르면 바꾼다(Paper 의 월드 이름은 이 값을 따른다). */
    public void checkName(String name) {
        if (!settings.levelName.equals(name)) settings.levelName = name;
    }

    public LevelSettings getLevelSettings() { return settings; }

    // ---- LevelData·ServerLevelData·WorldData: 서버 월드는 그 월드(Bukkit 월드)에서 그때그때 읽고, 플러그인이 만든 것은 받은 값·쓴 값 그대로다.

    @Override
    public BlockPos getSpawnPos() {
        if (live == null) return scratchSpawn;
        var l = live.getSpawnLocation();
        return new BlockPos(l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    @Override
    public float getSpawnAngle() { return live == null ? scratchSpawnAngle : live.getSpawnLocation().getYaw(); }

    @Override
    public void setSpawn(BlockPos pos, float angle) {
        if (live == null) {
            scratchSpawn = pos;
            scratchSpawnAngle = angle;
        } else {
            live.setSpawnLocation(pos.getX(), pos.getY(), pos.getZ(), angle);
        }
    }

    @Override
    public long getGameTime() { return live == null ? scratchGameTime : live.getGameTime(); }

    @Override
    public void setGameTime(long time) {
        if (live != null) throw new UnsupportedNmsCall("PrimaryLevelData#setGameTime", "the game time of a server world is kept by the server clock");
        scratchGameTime = time;
    }

    @Override
    public long getDayTime() { return live == null ? scratchDayTime : live.getFullTime(); }

    @Override
    public void setDayTime(long time) {
        if (live == null) scratchDayTime = time;
        else live.setFullTime(time);
    }

    @Override
    public boolean isThundering() { return live == null ? scratchThundering : live.isThundering(); }

    @Override
    public void setThundering(boolean thundering) {
        if (live == null) scratchThundering = thundering;
        else live.setThundering(thundering);
    }

    @Override
    public boolean isRaining() { return live == null ? scratchRaining : live.hasStorm(); }

    @Override
    public void setRaining(boolean raining) {
        if (live == null) scratchRaining = raining;
        else live.setStorm(raining);
    }

    @Override
    public int getRainTime() { return live == null ? scratchRainTime : live.getWeatherDuration(); }

    @Override
    public void setRainTime(int time) {
        if (live == null) scratchRainTime = time;
        else live.setWeatherDuration(time);
    }

    @Override
    public int getThunderTime() { return live == null ? scratchThunderTime : live.getThunderDuration(); }

    @Override
    public void setThunderTime(int time) {
        if (live == null) scratchThunderTime = time;
        else live.setThunderDuration(time);
    }

    @Override
    public int getClearWeatherTime() { return live == null ? scratchClearTime : live.getClearWeatherDuration(); }

    @Override
    public void setClearWeatherTime(int time) {
        if (live == null) scratchClearTime = time;
        else live.setClearWeatherDuration(time);
    }

    @Override
    public int getWanderingTraderSpawnDelay() { return live == null ? scratchTraderDelay : unsupported("getWanderingTraderSpawnDelay"); }

    @Override
    public void setWanderingTraderSpawnDelay(int delay) {
        if (live != null) unsupported("setWanderingTraderSpawnDelay");
        scratchTraderDelay = delay;
    }

    @Override
    public int getWanderingTraderSpawnChance() { return live == null ? scratchTraderChance : unsupported("getWanderingTraderSpawnChance"); }

    @Override
    public void setWanderingTraderSpawnChance(int chance) {
        if (live != null) unsupported("setWanderingTraderSpawnChance");
        scratchTraderChance = chance;
    }

    @Override
    public UUID getWanderingTraderId() {
        if (live != null) unsupported("getWanderingTraderId");
        return scratchTraderId;
    }

    @Override
    public void setWanderingTraderId(UUID id) {
        if (live != null) unsupported("setWanderingTraderId");
        scratchTraderId = id;
    }

    @Override
    public boolean isInitialized() { return live != null || scratchInitialized; }

    @Override
    public void setInitialized(boolean initialized) {
        if (live == null) scratchInitialized = initialized;
    }

    private static int unsupported(String what) {
        throw new UnsupportedNmsCall("PrimaryLevelData#" + what, "this value of a server world is kept by the server and is not exposed yet");
    }

    @Override
    public boolean isHardcore() { return settings.hardcore(); }

    @Override
    public GameRules getGameRules() { return settings.gameRules(); }

    @Override
    public Difficulty getDifficulty() { return live == null ? scratchDifficulty : settings.difficulty(); }

    @Override
    public void setDifficulty(Difficulty difficulty) {
        if (live != null) throw new UnsupportedNmsCall("PrimaryLevelData#setDifficulty", "change the difficulty through the Bukkit world or the /difficulty command");
        scratchDifficulty = difficulty;
    }

    @Override
    public boolean isDifficultyLocked() { return live == null && scratchDifficultyLocked; }

    @Override
    public void setDifficultyLocked(boolean locked) {
        if (live != null) throw new UnsupportedNmsCall("PrimaryLevelData#setDifficultyLocked", "the difficulty lock of a server world is not exposed yet");
        scratchDifficultyLocked = locked;
    }

    @Override
    public GameType getGameType() { return live == null ? scratchGameType : settings.gameType(); }

    @Override
    public void setGameType(GameType type) {
        if (live != null) throw new UnsupportedNmsCall("PrimaryLevelData#setGameType", "the default game mode of a server world is set in server.properties");
        scratchGameType = type;
    }

    @Override
    public boolean isAllowCommands() { return settings.allowCommands(); }

    @Override
    public ServerLevelData overworldData() { return this; }

    @Override
    public WorldDataConfiguration getDataConfiguration() { return settings.getDataConfiguration(); }

    @Override
    public void setDataConfiguration(WorldDataConfiguration configuration) {
        throw new UnsupportedNmsCall("PrimaryLevelData#setDataConfiguration", "the enabled feature set is fixed by the server");
    }

    /** Furnace 서버는 바닐라 데이터 팩만 쓰는 서버로 알린다(수정된 서버 표시를 저장하지 않는다). */
    @Override
    public boolean wasModded() { return false; }

    @Override
    public Set<String> getKnownServerBrands() { return Set.of(); }

    @Override
    public Set<String> getRemovedFeatureFlags() { return Set.of(); }

    @Override
    public void setModdedInfo(String brand, boolean modded) {
        throw new UnsupportedNmsCall("PrimaryLevelData#setModdedInfo", "Furnace does not save a modded-server marker");
    }

    @Override
    public CompoundTag getLoadedPlayerTag() { return null; }

    @Override
    public CompoundTag getCustomBossEvents() { return null; }

    @Override
    public void setCustomBossEvents(CompoundTag tag) {
        throw new UnsupportedNmsCall("PrimaryLevelData#setCustomBossEvents", "boss events are saved by the server");
    }

    @Override
    public int getVersion() { return ANVIL_VERSION_ID; }

    @Override
    public Lifecycle worldGenSettingsLifecycle() { return lifecycle; }
}
