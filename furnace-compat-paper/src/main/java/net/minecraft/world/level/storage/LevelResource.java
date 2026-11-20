package net.minecraft.world.level.storage;

/** 바닐라 월드 폴더 안의 자원 이름(Mojang 이름 shim) — 폴더·파일 이름 상수와 {@link #getId()} 뿐이다. */
public class LevelResource {

    public static final LevelResource PLAYER_ADVANCEMENTS_DIR = new LevelResource("advancements");
    public static final LevelResource PLAYER_STATS_DIR = new LevelResource("stats");
    public static final LevelResource PLAYER_DATA_DIR = new LevelResource("playerdata");
    public static final LevelResource PLAYER_OLD_DATA_DIR = new LevelResource("players");
    public static final LevelResource LEVEL_DATA_FILE = new LevelResource("level.dat");
    public static final LevelResource GENERATED_DIR = new LevelResource("generated");
    public static final LevelResource DATAPACK_DIR = new LevelResource("datapacks");
    public static final LevelResource MAP_RESOURCE_FILE = new LevelResource("resources.zip");
    public static final LevelResource ROOT = new LevelResource(".");

    private final String id;

    private LevelResource(String id) { this.id = id; }

    public String getId() { return id; }

    @Override
    public String toString() { return "/" + id; }
}
