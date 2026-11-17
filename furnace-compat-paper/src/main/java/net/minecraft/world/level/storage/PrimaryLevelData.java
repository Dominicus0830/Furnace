package net.minecraft.world.level.storage;

import com.mojang.serialization.Lifecycle;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.levelgen.WorldOptions;

/**
 * 바닐라 주 레벨 데이터(level.dat, Mojang 이름, T-203): {@code ServerLevel#serverLevelData}. 서버 월드의 것은 그 월드에서 그때그때 읽고
 * (시드·구조물·평지/디버그), 플러그인이 만든 것(Paper 의 따로 떨어진 레벨에 넘기는 것)은 받은 값 그대로다. 저장 형식(NBT)은 없다.
 */
public class PrimaryLevelData {

    /** 바닐라 특수 월드 종류. */
    public enum SpecialWorldProperty { NONE, FLAT, DEBUG }

    /** CraftBukkit 이 연 칸. */
    public LevelSettings settings;
    private final WorldOptions worldOptions;
    private final SpecialWorldProperty specialWorldProperty;
    private final org.bukkit.craftbukkit.CraftWorld live;

    public PrimaryLevelData(LevelSettings settings, WorldOptions worldOptions, SpecialWorldProperty specialWorldProperty, Lifecycle worldGenSettingsLifecycle) {
        this.settings = settings;
        this.worldOptions = worldOptions;
        this.specialWorldProperty = specialWorldProperty;
        this.live = null;
    }

    private PrimaryLevelData(org.bukkit.craftbukkit.CraftWorld world) {
        this.settings = LevelSettings.of(world);
        this.worldOptions = null;
        this.specialWorldProperty = null;
        this.live = world;
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
}
