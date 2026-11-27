package net.minecraft.world.level;

/**
 * 바닐라 게임 규칙 묶음(Mojang 이름 shim, T-203·NMS shim 5c). 값을 베끼지 않고 서버의 규칙({@link org.domi.furnace.api.world.GameRuleRegistry} —
 * /gamerule·Bukkit {@code World#setGameRule} 과 같은 상태)을 본다. 아무 스레드에서 읽고 쓴다.
 * <p>Paper 와 다른 점: Paper 는 월드마다 규칙을 따로 들지만 Furnace 는 서버에 한 벌이다(Bukkit 어댑터와 같다). 바꾸기는 {@code /gamerule} 처럼 즉시
 * 적용·알림이고, 이벤트({@code WorldGameRuleChangeEvent})는 내지 않는다 — Paper 도 이 NMS 길에서는 내지 않는다.
 * 규칙 상수 51개는 바닐라 1.21.1 순서·이름 그대로다(서버 jar 에서 기계로 뽑음).
 */
class GameRules {

    private final org.domi.furnace.api.world.World world;

    /** 호환 모듈 전용 — world 는 규칙을 가진 월드(없으면 null). */
    public GameRules(org.domi.furnace.api.world.World world) { this.world = world; }

    public GameRules() { this(null); }

    /** 이 규칙 묶음의 월드(없으면 null). */
    public org.domi.furnace.api.world.World furnaceWorld() { return world; }

    public static final Key<BooleanValue> RULE_DOFIRETICK = key("doFireTick", true);
    public static final Key<BooleanValue> RULE_MOBGRIEFING = key("mobGriefing", true);
    public static final Key<BooleanValue> RULE_KEEPINVENTORY = key("keepInventory", true);
    public static final Key<BooleanValue> RULE_DOMOBSPAWNING = key("doMobSpawning", true);
    public static final Key<BooleanValue> RULE_DOMOBLOOT = key("doMobLoot", true);
    public static final Key<BooleanValue> RULE_PROJECTILESCANBREAKBLOCKS = key("projectilesCanBreakBlocks", true);
    public static final Key<BooleanValue> RULE_DOBLOCKDROPS = key("doTileDrops", true);
    public static final Key<BooleanValue> RULE_DOENTITYDROPS = key("doEntityDrops", true);
    public static final Key<BooleanValue> RULE_COMMANDBLOCKOUTPUT = key("commandBlockOutput", true);
    public static final Key<BooleanValue> RULE_NATURAL_REGENERATION = key("naturalRegeneration", true);
    public static final Key<BooleanValue> RULE_DAYLIGHT = key("doDaylightCycle", true);
    public static final Key<BooleanValue> RULE_LOGADMINCOMMANDS = key("logAdminCommands", true);
    public static final Key<BooleanValue> RULE_SHOWDEATHMESSAGES = key("showDeathMessages", true);
    public static final Key<IntegerValue> RULE_RANDOMTICKING = key("randomTickSpeed", false);
    public static final Key<BooleanValue> RULE_SENDCOMMANDFEEDBACK = key("sendCommandFeedback", true);
    public static final Key<BooleanValue> RULE_REDUCEDDEBUGINFO = key("reducedDebugInfo", true);
    public static final Key<BooleanValue> RULE_SPECTATORSGENERATECHUNKS = key("spectatorsGenerateChunks", true);
    public static final Key<IntegerValue> RULE_SPAWN_RADIUS = key("spawnRadius", false);
    public static final Key<BooleanValue> RULE_DISABLE_ELYTRA_MOVEMENT_CHECK = key("disableElytraMovementCheck", true);
    public static final Key<IntegerValue> RULE_MAX_ENTITY_CRAMMING = key("maxEntityCramming", false);
    public static final Key<BooleanValue> RULE_WEATHER_CYCLE = key("doWeatherCycle", true);
    public static final Key<BooleanValue> RULE_LIMITED_CRAFTING = key("doLimitedCrafting", true);
    public static final Key<IntegerValue> RULE_MAX_COMMAND_CHAIN_LENGTH = key("maxCommandChainLength", false);
    public static final Key<IntegerValue> RULE_MAX_COMMAND_FORK_COUNT = key("maxCommandForkCount", false);
    public static final Key<IntegerValue> RULE_COMMAND_MODIFICATION_BLOCK_LIMIT = key("commandModificationBlockLimit", false);
    public static final Key<BooleanValue> RULE_ANNOUNCE_ADVANCEMENTS = key("announceAdvancements", true);
    public static final Key<BooleanValue> RULE_DISABLE_RAIDS = key("disableRaids", true);
    public static final Key<BooleanValue> RULE_DOINSOMNIA = key("doInsomnia", true);
    public static final Key<BooleanValue> RULE_DO_IMMEDIATE_RESPAWN = key("doImmediateRespawn", true);
    public static final Key<IntegerValue> RULE_PLAYERS_NETHER_PORTAL_DEFAULT_DELAY = key("playersNetherPortalDefaultDelay", false);
    public static final Key<IntegerValue> RULE_PLAYERS_NETHER_PORTAL_CREATIVE_DELAY = key("playersNetherPortalCreativeDelay", false);
    public static final Key<BooleanValue> RULE_DROWNING_DAMAGE = key("drowningDamage", true);
    public static final Key<BooleanValue> RULE_FALL_DAMAGE = key("fallDamage", true);
    public static final Key<BooleanValue> RULE_FIRE_DAMAGE = key("fireDamage", true);
    public static final Key<BooleanValue> RULE_FREEZE_DAMAGE = key("freezeDamage", true);
    public static final Key<BooleanValue> RULE_DO_PATROL_SPAWNING = key("doPatrolSpawning", true);
    public static final Key<BooleanValue> RULE_DO_TRADER_SPAWNING = key("doTraderSpawning", true);
    public static final Key<BooleanValue> RULE_DO_WARDEN_SPAWNING = key("doWardenSpawning", true);
    public static final Key<BooleanValue> RULE_FORGIVE_DEAD_PLAYERS = key("forgiveDeadPlayers", true);
    public static final Key<BooleanValue> RULE_UNIVERSAL_ANGER = key("universalAnger", true);
    public static final Key<IntegerValue> RULE_PLAYERS_SLEEPING_PERCENTAGE = key("playersSleepingPercentage", false);
    public static final Key<BooleanValue> RULE_BLOCK_EXPLOSION_DROP_DECAY = key("blockExplosionDropDecay", true);
    public static final Key<BooleanValue> RULE_MOB_EXPLOSION_DROP_DECAY = key("mobExplosionDropDecay", true);
    public static final Key<BooleanValue> RULE_TNT_EXPLOSION_DROP_DECAY = key("tntExplosionDropDecay", true);
    public static final Key<IntegerValue> RULE_SNOW_ACCUMULATION_HEIGHT = key("snowAccumulationHeight", false);
    public static final Key<BooleanValue> RULE_WATER_SOURCE_CONVERSION = key("waterSourceConversion", true);
    public static final Key<BooleanValue> RULE_LAVA_SOURCE_CONVERSION = key("lavaSourceConversion", true);
    public static final Key<BooleanValue> RULE_GLOBAL_SOUND_EVENTS = key("globalSoundEvents", true);
    public static final Key<BooleanValue> RULE_DO_VINES_SPREAD = key("doVinesSpread", true);
    public static final Key<BooleanValue> RULE_ENDER_PEARLS_VANISH_ON_DEATH = key("enderPearlsVanishOnDeath", true);
    public static final Key<IntegerValue> RULE_SPAWN_CHUNK_RADIUS = key("spawnChunkRadius", false);

    private static <T extends Value<T>> Key<T> key(String id, boolean bool) { return new Key<>(id, bool); }

    /** 바닐라 getRule(key): 그 규칙의 값 보기. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T extends Value<T>> T getRule(Key<T> key) {
        Key raw = key;
        return (T) (key.bool ? new BooleanValue(raw) : new IntegerValue(raw));
    }

    public boolean getBoolean(Key<BooleanValue> key) { return getRule(key).get(); }

    public int getInt(Key<IntegerValue> key) { return getRule(key).get(); }

    private static org.domi.furnace.api.world.GameRuleRegistry registry() { return org.domi.furnace.api.Furnace.server().gameRules(); }

    /** 바닐라 규칙 열쇠: 이름(예: doDaylightCycle)으로 구별된다. */
    public static final class Key<T extends Value<T>> {
        private final String id;
        final boolean bool;

        private Key(String id, boolean bool) {
            this.id = id;
            this.bool = bool;
        }

        public String getId() { return id; }

        public String getDescriptionId() { return "gamerule." + id; }

        @Override
        public String toString() { return id; }

        @Override
        public boolean equals(Object o) { return this == o || o instanceof Key<?> k && k.id.equals(id); }

        @Override
        public int hashCode() { return id.hashCode(); }

        @SuppressWarnings("unchecked")
        <V> org.domi.furnace.api.world.GameRule<V> furnace() {
            return (org.domi.furnace.api.world.GameRule<V>) (bool ? org.domi.furnace.api.world.GameRule.bool(id) : org.domi.furnace.api.world.GameRule.integer(id));
        }
    }

    /** 바닐라 규칙 값. 서버의 그 규칙을 가리키는 보기다(같은 열쇠의 보기는 모두 같은 값을 본다). */
    public abstract static class Value<T extends Value<T>> {
        final Key<T> key;

        Value(Key<T> key) { this.key = key; }

        /** 바닐라 serialize: 지금 값의 글자. */
        public abstract String serialize();

        @Override
        public String toString() { return serialize(); }
    }

    public static class BooleanValue extends Value<BooleanValue> {
        BooleanValue(Key<BooleanValue> key) { super(key); }

        public boolean get() { return registry().get(key.<Boolean>furnace()); }

        /** Paper set(값, 레벨): 바꾸고 규칙의 효과·알림(/gamerule 과 같다). */
        public void set(boolean value, net.minecraft.server.level.ServerLevel level) { registry().set(key.<Boolean>furnace(), value); }

        @Override
        public String serialize() { return Boolean.toString(get()); }
    }

    public static class IntegerValue extends Value<IntegerValue> {
        IntegerValue(Key<IntegerValue> key) { super(key); }

        public int get() { return registry().get(key.<Integer>furnace()); }

        /** Paper set(값, 레벨): 바꾸고 규칙의 효과·알림(/gamerule 과 같다). */
        public void set(int value, net.minecraft.server.level.ServerLevel level) { registry().set(key.<Integer>furnace(), value); }

        @Override
        public String serialize() { return Integer.toString(get()); }
    }
}
