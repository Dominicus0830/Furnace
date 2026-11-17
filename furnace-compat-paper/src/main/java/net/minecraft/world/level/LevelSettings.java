package net.minecraft.world.level;

import net.minecraft.world.Difficulty;

/**
 * 바닐라 레벨 설정(Mojang 이름, T-203): 이름·게임 모드·하드코어·난이도·명령 허용·게임 규칙·데이터 설정. 서버 월드의 것({@code ServerLevel#serverLevelData})은
 * 그 월드에서 그때그때 읽는다(이름만 Paper 처럼 {@code PrimaryLevelData#checkName} 으로 바뀐다). 플러그인이 만든 것은 받은 값 그대로다.
 */
public final class LevelSettings {

    /** CraftBukkit 이 바꿀 수 있게 연 이름 칸({@code PrimaryLevelData#checkName}). */
    public String levelName;
    private final GameType gameType;
    private final boolean hardcore;
    private final Difficulty difficulty;
    private final boolean allowCommands;
    private final GameRules gameRules;
    private final WorldDataConfiguration dataConfiguration;
    /** 서버 월드의 설정이면 그 월드(값을 그때그때 읽는다), 아니면 null. */
    private final org.bukkit.World live;

    public LevelSettings(String levelName, GameType gameType, boolean hardcore, Difficulty difficulty, boolean allowCommands, GameRules gameRules,
                         WorldDataConfiguration dataConfiguration) {
        this.levelName = levelName;
        this.gameType = gameType;
        this.hardcore = hardcore;
        this.difficulty = difficulty;
        this.allowCommands = allowCommands;
        this.gameRules = gameRules;
        this.dataConfiguration = dataConfiguration;
        this.live = null;
    }

    private LevelSettings(org.bukkit.craftbukkit.CraftWorld world) {
        this.levelName = world.getName();
        this.gameType = null;
        this.hardcore = false;
        this.difficulty = null;
        this.allowCommands = false;
        this.gameRules = new GameRules(world.furnaceHandle());
        this.dataConfiguration = WorldDataConfiguration.DEFAULT;
        this.live = world;
    }

    /** 호환 모듈 전용: 서버 월드의 설정(값은 그 월드에서 읽는다). */
    public static LevelSettings of(org.bukkit.craftbukkit.CraftWorld world) { return new LevelSettings(world); }

    public String levelName() { return levelName; }

    /** 서버 월드면 서버 기본 게임 모드(server.properties gamemode). */
    public GameType gameType() {
        if (live == null) return gameType;
        return GameType.byName(org.domi.furnace.api.internal.Internals.get().scratchWorlds().defaultGameType());
    }

    public boolean hardcore() { return live == null ? hardcore : live.isHardcore(); }

    public Difficulty difficulty() {
        if (live == null) return difficulty;
        return Difficulty.byName(org.domi.furnace.api.Furnace.server().difficulty().difficulty().name().toLowerCase(java.util.Locale.ROOT));
    }

    /** 데디케이티드 서버의 레벨은 거짓(바닐라 DedicatedServer 가 만드는 LevelSettings). */
    public boolean allowCommands() { return live == null && allowCommands; }

    public GameRules gameRules() { return gameRules; }

    public WorldDataConfiguration getDataConfiguration() { return dataConfiguration; }
}
