// 바닐라 PrimaryLevelData(level.dat 의 Data) — 서버가 쓰는 필드만 꺼내 두고, 읽은 원본 compound 를 함께 보관해
// 저장 시 모르는 필드(다른 서버·모드·향후 기능이 쓴 값)를 그대로 되돌려 쓴다.
package org.domi.furnace.server.world.storage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.world.Difficulty;
import org.domi.furnace.server.world.GameMode;
import org.domi.furnace.server.world.gen.carver.LegacyRandom;

public final class LevelData {

    public static final int DATA_VERSION = 3955;
    public static final String VERSION_NAME = "1.21.1";
    /** 바닐라 ANVIL_VERSION_ID. */
    public static final int ANVIL_VERSION = 19133;

    /** 바닐라 1.21.1 GameRules 기본값(level.dat 에 문자열로 저장). 2-6 에서 실제 규칙 엔진으로 대체된다. */
    public static final Map<String, String> DEFAULT_GAME_RULES = defaultGameRules();

    private final NbtCompound raw;
    public String levelName;
    public long seed;
    public boolean generateFeatures;
    public boolean bonusChest;
    public int spawnX, spawnY, spawnZ;
    public float spawnAngle;
    public GameMode gameType;
    public Difficulty difficulty;
    public boolean difficultyLocked;
    public boolean hardcore;
    public boolean allowCommands;
    public boolean initialized;
    public long gameTime;
    public long dayTime;
    public final Map<String, String> gameRules = new LinkedHashMap<>();
    public List<String> enabledPacks;
    public List<String> disabledPacks;
    public final List<String> serverBrands = new java.util.ArrayList<>();
    public int clearWeatherTime, rainTime, thunderTime;
    public boolean raining, thundering;
    public boolean wasModded;

    private LevelData(NbtCompound raw) {
        this.raw = raw;
    }

    /** 새 월드(바닐라 Main 의 LevelSettings + WorldOptions). */
    public static LevelData create(String levelName, long seed, boolean generateFeatures, boolean bonusChest, GameMode gameType,
                                   boolean hardcore, Difficulty difficulty, List<String> enabledPacks, List<String> disabledPacks) {
        LevelData d = new LevelData(new NbtCompound());
        d.levelName = levelName;
        d.seed = seed;
        d.generateFeatures = generateFeatures;
        d.bonusChest = bonusChest;
        d.gameType = gameType;
        d.hardcore = hardcore;
        d.difficulty = difficulty;
        d.allowCommands = false;
        d.gameRules.putAll(DEFAULT_GAME_RULES);
        d.enabledPacks = List.copyOf(enabledPacks);
        d.disabledPacks = List.copyOf(disabledPacks);
        d.spawnY = 64;
        return d;
    }

    /** level.dat 의 Data compound 에서 읽는다. */
    public static LevelData fromNbt(NbtCompound data) {
        LevelData d = new LevelData(data.copy());
        d.levelName = str(data, "LevelName", "");
        NbtCompound wgs = data.contains("WorldGenSettings") ? data.getCompound("WorldGenSettings") : new NbtCompound();
        d.seed = wgs.contains("seed") ? ((Number) wgs.tags().get("seed")).longValue() : 0L;
        d.generateFeatures = bool(wgs, "generate_features", true);
        d.bonusChest = bool(wgs, "bonus_chest", false);
        d.spawnX = num(data, "SpawnX", 0).intValue();
        d.spawnY = num(data, "SpawnY", 0).intValue();
        d.spawnZ = num(data, "SpawnZ", 0).intValue();
        d.spawnAngle = num(data, "SpawnAngle", 0f).floatValue();
        d.gameType = GameMode.byId(num(data, "GameType", 0).intValue());
        d.difficulty = data.contains("Difficulty") ? Difficulty.byId(num(data, "Difficulty", 2).intValue()) : Difficulty.NORMAL;
        d.difficultyLocked = bool(data, "DifficultyLocked", false);
        d.hardcore = bool(data, "hardcore", false);
        d.allowCommands = bool(data, "allowCommands", d.gameType == GameMode.CREATIVE);
        d.initialized = bool(data, "initialized", true);
        d.gameTime = num(data, "Time", 0L).longValue();
        d.dayTime = data.contains("DayTime") ? num(data, "DayTime", 0L).longValue() : d.gameTime;
        if (data.contains("GameRules")) {
            for (var e : data.getCompound("GameRules").tags().entrySet()) {
                if (e.getValue() instanceof String s) d.gameRules.put(e.getKey(), s);
            }
        }
        for (var e : DEFAULT_GAME_RULES.entrySet()) d.gameRules.putIfAbsent(e.getKey(), e.getValue());
        NbtCompound packs = data.contains("DataPacks") ? data.getCompound("DataPacks") : null;
        d.enabledPacks = packs != null && packs.contains("Enabled") ? strings(packs.getList("Enabled")) : List.of("vanilla");
        d.disabledPacks = packs != null && packs.contains("Disabled") ? strings(packs.getList("Disabled")) : List.of();
        if (data.contains("ServerBrands")) d.serverBrands.addAll(strings(data.getList("ServerBrands")));
        d.clearWeatherTime = num(data, "clearWeatherTime", 0).intValue();
        d.rainTime = num(data, "rainTime", 0).intValue();
        d.thunderTime = num(data, "thunderTime", 0).intValue();
        d.raining = bool(data, "raining", false);
        d.thundering = bool(data, "thundering", false);
        d.wasModded = bool(data, "WasModded", false);
        return d;
    }

    /** 브랜드 기록(바닐라 setModdedInfo: 처음 보는 브랜드면 추가, 수정 여부는 누적). */
    public void setModdedInfo(String brand, boolean modded) {
        if (!serverBrands.contains(brand)) serverBrands.add(brand);
        wasModded |= modded;
    }

    /** level.dat 의 Data compound. 읽었던 원본 위에 서버가 아는 필드만 덮어써 모르는 필드는 보존된다. */
    public NbtCompound toNbt(long lastPlayed) {
        NbtCompound d = raw.copy();
        NbtCompound wgs = d.contains("WorldGenSettings") ? d.getCompound("WorldGenSettings") : new NbtCompound();
        wgs.put("bonus_chest", b(bonusChest));
        wgs.putLong("seed", seed);
        wgs.put("generate_features", b(generateFeatures));
        if (!wgs.contains("dimensions")) wgs.putCompound("dimensions", defaultDimensions());
        d.putCompound("WorldGenSettings", wgs);
        d.putInt("GameType", gameType.id());
        d.putInt("SpawnX", spawnX);
        d.putInt("SpawnY", spawnY);
        d.putInt("SpawnZ", spawnZ);
        d.put("SpawnAngle", spawnAngle);
        d.putLong("Time", gameTime);
        d.putLong("DayTime", dayTime);
        d.putLong("LastPlayed", lastPlayed);
        d.putString("LevelName", levelName);
        d.putInt("version", ANVIL_VERSION);
        d.put("hardcore", b(hardcore));
        d.put("allowCommands", b(allowCommands));
        d.put("initialized", b(initialized));
        d.put("Difficulty", (byte) difficulty.id());
        d.put("DifficultyLocked", b(difficultyLocked));
        NbtCompound version = new NbtCompound();
        version.putString("Name", VERSION_NAME);
        version.putInt("Id", DATA_VERSION);
        version.put("Snapshot", b(false));
        version.putString("Series", "main");
        d.putCompound("Version", version);
        d.putInt("DataVersion", DATA_VERSION);
        NbtCompound rules = new NbtCompound();
        gameRules.forEach(rules::putString);
        d.putCompound("GameRules", rules);
        NbtCompound packs = new NbtCompound();
        packs.putList("Enabled", stringList(enabledPacks));
        packs.putList("Disabled", stringList(disabledPacks));
        d.putCompound("DataPacks", packs);
        d.putList("ServerBrands", stringList(serverBrands));
        d.put("WasModded", b(wasModded));
        // 아직 런타임이 다루지 않는 상태는 처음 만들 때만 바닐라 기본값으로 채우고, 이후로는 읽은 값을 보존한다.
        d.putInt("clearWeatherTime", clearWeatherTime);
        d.put("raining", b(raining));
        d.putInt("rainTime", rainTime);
        d.put("thundering", b(thundering));
        d.putInt("thunderTime", thunderTime);
        putIfAbsent(d, "BorderCenterX", 0.0);
        putIfAbsent(d, "BorderCenterZ", 0.0);
        putIfAbsent(d, "BorderSize", 5.9999968E7);
        putIfAbsent(d, "BorderSafeZone", 5.0);
        putIfAbsent(d, "BorderWarningBlocks", 5.0);
        putIfAbsent(d, "BorderWarningTime", 15.0);
        putIfAbsent(d, "BorderSizeLerpTarget", 5.9999968E7);
        putIfAbsent(d, "BorderSizeLerpTime", 0L);
        putIfAbsent(d, "BorderDamagePerBlock", 0.2);
        putIfAbsent(d, "WanderingTraderSpawnDelay", 24000);
        putIfAbsent(d, "WanderingTraderSpawnChance", 25);
        putIfAbsent(d, "CustomBossEvents", new NbtCompound());
        putIfAbsent(d, "ScheduledEvents", new NbtList(NbtIo.TAG_END));
        if (!d.contains("DragonFight")) d.putCompound("DragonFight", defaultDragonFight(seed));
        return d;
    }

    /** EndDragonFight 새 상태: 관문 0..19 를 LegacyRandom(seed) 로 Util.shuffle. */
    static NbtCompound defaultDragonFight(long seed) {
        int[] gateways = new int[20];
        for (int i = 0; i < 20; i++) gateways[i] = i;
        LegacyRandom r = new LegacyRandom(seed);
        for (int i = gateways.length; i > 1; i--) {
            int j = r.nextInt(i);
            int t = gateways[i - 1];
            gateways[i - 1] = gateways[j];
            gateways[j] = t;
        }
        NbtCompound c = new NbtCompound();
        c.put("NeedsStateScanning", b(true));
        c.putIntArray("Gateways", gateways);
        c.put("DragonKilled", b(false));
        c.put("PreviouslyKilled", b(false));
        return c;
    }

    private static NbtCompound defaultDimensions() {
        NbtCompound dims = new NbtCompound();
        dims.putCompound("minecraft:overworld", dimension("minecraft:overworld", "minecraft:overworld", multiNoise("minecraft:overworld")));
        dims.putCompound("minecraft:the_nether", dimension("minecraft:the_nether", "minecraft:nether", multiNoise("minecraft:nether")));
        NbtCompound end = new NbtCompound();
        end.putString("type", "minecraft:the_end");
        dims.putCompound("minecraft:the_end", dimension("minecraft:the_end", "minecraft:end", end));
        return dims;
    }

    private static NbtCompound multiNoise(String preset) {
        NbtCompound c = new NbtCompound();
        c.putString("preset", preset);
        c.putString("type", "minecraft:multi_noise");
        return c;
    }

    private static NbtCompound dimension(String type, String settings, NbtCompound biomeSource) {
        NbtCompound gen = new NbtCompound();
        gen.putString("settings", settings);
        gen.putCompound("biome_source", biomeSource);
        gen.putString("type", "minecraft:noise");
        NbtCompound dim = new NbtCompound();
        dim.putCompound("generator", gen);
        dim.putString("type", type);
        return dim;
    }

    private static Map<String, String> defaultGameRules() {
        Map<String, String> m = new LinkedHashMap<>();
        String[] kv = {
                "globalSoundEvents", "true", "tntExplosionDropDecay", "false", "enderPearlsVanishOnDeath", "true",
                "doFireTick", "true", "maxCommandChainLength", "65536", "doVinesSpread", "true",
                "disableElytraMovementCheck", "false", "lavaSourceConversion", "false", "commandBlockOutput", "true",
                "forgiveDeadPlayers", "true", "playersNetherPortalCreativeDelay", "1", "doMobSpawning", "true",
                "maxEntityCramming", "24", "universalAnger", "false", "playersSleepingPercentage", "100",
                "snowAccumulationHeight", "1", "blockExplosionDropDecay", "true", "doImmediateRespawn", "false",
                "naturalRegeneration", "true", "doMobLoot", "true", "fallDamage", "true", "doEntityDrops", "true",
                "randomTickSpeed", "3", "playersNetherPortalDefaultDelay", "80", "spawnRadius", "10",
                "freezeDamage", "true", "sendCommandFeedback", "true", "doWardenSpawning", "true", "fireDamage", "true",
                "reducedDebugInfo", "false", "waterSourceConversion", "true", "projectilesCanBreakBlocks", "true",
                "announceAdvancements", "true", "drowningDamage", "true", "spawnChunkRadius", "2",
                "disableRaids", "false", "doWeatherCycle", "true", "mobExplosionDropDecay", "true",
                "doDaylightCycle", "true", "showDeathMessages", "true", "doTileDrops", "true", "doInsomnia", "true",
                "keepInventory", "false", "doLimitedCrafting", "false", "mobGriefing", "true",
                "commandModificationBlockLimit", "32768", "doTraderSpawning", "true", "logAdminCommands", "true",
                "spectatorsGenerateChunks", "true", "doPatrolSpawning", "true", "maxCommandForkCount", "65536"};
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return java.util.Collections.unmodifiableMap(m);
    }

    private static void putIfAbsent(NbtCompound c, String k, Object v) {
        if (!c.contains(k)) c.put(k, v);
    }

    private static byte b(boolean v) { return (byte) (v ? 1 : 0); }

    private static Number num(NbtCompound c, String k, Number def) {
        return c.tags().get(k) instanceof Number n ? n : def;
    }

    private static boolean bool(NbtCompound c, String k, boolean def) {
        return c.tags().get(k) instanceof Number n ? n.intValue() != 0 : def;
    }

    private static String str(NbtCompound c, String k, String def) {
        return c.tags().get(k) instanceof String s ? s : def;
    }

    private static List<String> strings(NbtList l) {
        List<String> out = new java.util.ArrayList<>();
        for (Object o : l.items()) if (o instanceof String s) out.add(s);
        return out;
    }

    private static NbtList stringList(List<String> values) {
        NbtList l = new NbtList(NbtIo.TAG_STRING);
        values.forEach(l::add);
        return l;
    }
}
