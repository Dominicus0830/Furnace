// Bukkit 게임 규칙 상수(Paper 1.21.1 과 같은 51개·같은 선언 순서). 값 읽기·쓰기는 World#getGameRuleValue·setGameRule.
// Paper 는 Adventure Translatable 을 구현한다 — 호환 계층은 Adventure 를 싣지 않아 translationKey() 메서드만 둔다.
// equals 는 이름·형으로 비교하고 hashCode 는 Object 그대로다(Paper 와 같다). values() 순서는 이름 HashMap 순서(Paper 와 같은 표).
package org.bukkit;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class GameRule<T> {

    private static final Map<String, GameRule<?>> gameRules = new HashMap<>();

    // 불리언 규칙
    public static final GameRule<Boolean> ANNOUNCE_ADVANCEMENTS = new GameRule<>("announceAdvancements", Boolean.class);
    public static final GameRule<Boolean> COMMAND_BLOCK_OUTPUT = new GameRule<>("commandBlockOutput", Boolean.class);
    public static final GameRule<Boolean> DISABLE_ELYTRA_MOVEMENT_CHECK = new GameRule<>("disableElytraMovementCheck", Boolean.class);
    public static final GameRule<Boolean> DO_DAYLIGHT_CYCLE = new GameRule<>("doDaylightCycle", Boolean.class);
    public static final GameRule<Boolean> DO_ENTITY_DROPS = new GameRule<>("doEntityDrops", Boolean.class);
    public static final GameRule<Boolean> DO_FIRE_TICK = new GameRule<>("doFireTick", Boolean.class);
    public static final GameRule<Boolean> DO_LIMITED_CRAFTING = new GameRule<>("doLimitedCrafting", Boolean.class);
    public static final GameRule<Boolean> DO_MOB_LOOT = new GameRule<>("doMobLoot", Boolean.class);
    public static final GameRule<Boolean> PROJECTILES_CAN_BREAK_BLOCKS = new GameRule<>("projectilesCanBreakBlocks", Boolean.class);
    public static final GameRule<Boolean> DO_MOB_SPAWNING = new GameRule<>("doMobSpawning", Boolean.class);
    public static final GameRule<Boolean> DO_TILE_DROPS = new GameRule<>("doTileDrops", Boolean.class);
    public static final GameRule<Boolean> DO_WEATHER_CYCLE = new GameRule<>("doWeatherCycle", Boolean.class);
    public static final GameRule<Boolean> KEEP_INVENTORY = new GameRule<>("keepInventory", Boolean.class);
    public static final GameRule<Boolean> LOG_ADMIN_COMMANDS = new GameRule<>("logAdminCommands", Boolean.class);
    public static final GameRule<Boolean> MOB_GRIEFING = new GameRule<>("mobGriefing", Boolean.class);
    public static final GameRule<Boolean> NATURAL_REGENERATION = new GameRule<>("naturalRegeneration", Boolean.class);
    public static final GameRule<Boolean> REDUCED_DEBUG_INFO = new GameRule<>("reducedDebugInfo", Boolean.class);
    public static final GameRule<Boolean> SEND_COMMAND_FEEDBACK = new GameRule<>("sendCommandFeedback", Boolean.class);
    public static final GameRule<Boolean> SHOW_DEATH_MESSAGES = new GameRule<>("showDeathMessages", Boolean.class);
    public static final GameRule<Boolean> SPECTATORS_GENERATE_CHUNKS = new GameRule<>("spectatorsGenerateChunks", Boolean.class);
    public static final GameRule<Boolean> DISABLE_RAIDS = new GameRule<>("disableRaids", Boolean.class);
    public static final GameRule<Boolean> DO_INSOMNIA = new GameRule<>("doInsomnia", Boolean.class);
    public static final GameRule<Boolean> DO_IMMEDIATE_RESPAWN = new GameRule<>("doImmediateRespawn", Boolean.class);
    public static final GameRule<Boolean> DROWNING_DAMAGE = new GameRule<>("drowningDamage", Boolean.class);
    public static final GameRule<Boolean> FALL_DAMAGE = new GameRule<>("fallDamage", Boolean.class);
    public static final GameRule<Boolean> FIRE_DAMAGE = new GameRule<>("fireDamage", Boolean.class);
    public static final GameRule<Boolean> FREEZE_DAMAGE = new GameRule<>("freezeDamage", Boolean.class);
    public static final GameRule<Boolean> DO_PATROL_SPAWNING = new GameRule<>("doPatrolSpawning", Boolean.class);
    public static final GameRule<Boolean> DO_TRADER_SPAWNING = new GameRule<>("doTraderSpawning", Boolean.class);
    public static final GameRule<Boolean> DO_WARDEN_SPAWNING = new GameRule<>("doWardenSpawning", Boolean.class);
    public static final GameRule<Boolean> FORGIVE_DEAD_PLAYERS = new GameRule<>("forgiveDeadPlayers", Boolean.class);
    public static final GameRule<Boolean> UNIVERSAL_ANGER = new GameRule<>("universalAnger", Boolean.class);
    public static final GameRule<Boolean> BLOCK_EXPLOSION_DROP_DECAY = new GameRule<>("blockExplosionDropDecay", Boolean.class);
    public static final GameRule<Boolean> MOB_EXPLOSION_DROP_DECAY = new GameRule<>("mobExplosionDropDecay", Boolean.class);
    public static final GameRule<Boolean> TNT_EXPLOSION_DROP_DECAY = new GameRule<>("tntExplosionDropDecay", Boolean.class);
    public static final GameRule<Boolean> WATER_SOURCE_CONVERSION = new GameRule<>("waterSourceConversion", Boolean.class);
    public static final GameRule<Boolean> LAVA_SOURCE_CONVERSION = new GameRule<>("lavaSourceConversion", Boolean.class);
    public static final GameRule<Boolean> GLOBAL_SOUND_EVENTS = new GameRule<>("globalSoundEvents", Boolean.class);
    public static final GameRule<Boolean> DO_VINES_SPREAD = new GameRule<>("doVinesSpread", Boolean.class);
    public static final GameRule<Boolean> ENDER_PEARLS_VANISH_ON_DEATH = new GameRule<>("enderPearlsVanishOnDeath", Boolean.class);

    // 정수 규칙
    public static final GameRule<Integer> RANDOM_TICK_SPEED = new GameRule<>("randomTickSpeed", Integer.class);
    public static final GameRule<Integer> SPAWN_RADIUS = new GameRule<>("spawnRadius", Integer.class);
    public static final GameRule<Integer> MAX_ENTITY_CRAMMING = new GameRule<>("maxEntityCramming", Integer.class);
    public static final GameRule<Integer> MAX_COMMAND_CHAIN_LENGTH = new GameRule<>("maxCommandChainLength", Integer.class);
    public static final GameRule<Integer> MAX_COMMAND_FORK_COUNT = new GameRule<>("maxCommandForkCount", Integer.class);
    public static final GameRule<Integer> COMMAND_MODIFICATION_BLOCK_LIMIT = new GameRule<>("commandModificationBlockLimit", Integer.class);
    public static final GameRule<Integer> PLAYERS_SLEEPING_PERCENTAGE = new GameRule<>("playersSleepingPercentage", Integer.class);
    public static final GameRule<Integer> SNOW_ACCUMULATION_HEIGHT = new GameRule<>("snowAccumulationHeight", Integer.class);
    public static final GameRule<Integer> PLAYERS_NETHER_PORTAL_DEFAULT_DELAY = new GameRule<>("playersNetherPortalDefaultDelay", Integer.class);
    public static final GameRule<Integer> PLAYERS_NETHER_PORTAL_CREATIVE_DELAY = new GameRule<>("playersNetherPortalCreativeDelay", Integer.class);
    public static final GameRule<Integer> SPAWN_CHUNK_RADIUS = new GameRule<>("spawnChunkRadius", Integer.class);

    private final String name;
    private final Class<T> type;

    private GameRule(String name, Class<T> type) {
        Objects.requireNonNull(name, "GameRule name cannot be null");
        Objects.requireNonNull(type, "GameRule type cannot be null");
        if (type != Boolean.class && type != Integer.class) throw new IllegalArgumentException("Must be of type Boolean or Integer. Found " + type.getName() + " ");
        this.name = name;
        this.type = type;
        gameRules.put(name, this);
    }

    public String getName() { return name; }

    public Class<T> getType() { return type; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        return obj instanceof GameRule<?> other && name.equals(other.name) && type == other.type;
    }

    @Override
    public String toString() { return "GameRule{key=" + name + ", type=" + type + '}'; }

    /** 이름으로 찾는다(대소문자 구분), 없으면 null. */
    public static GameRule<?> getByName(String rule) {
        Objects.requireNonNull(rule, "Rule cannot be null");
        return gameRules.get(rule);
    }

    public static GameRule<?>[] values() { return gameRules.values().toArray(new GameRule<?>[0]); }

    /** Paper Translatable — 클라 번역 키. */
    public String translationKey() { return "gamerule." + name; }
}
