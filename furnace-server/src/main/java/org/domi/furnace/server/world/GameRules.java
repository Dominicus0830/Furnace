// 바닐라 GameRules(1.21.1, 51종) — 타입·기본값·범위·값이 바뀔 때의 동작. 이름 순서(TreeMap)로 나열된다.
package org.domi.furnace.server.world;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GameRules {

    private static final Logger log = LoggerFactory.getLogger(GameRules.class);

    public enum Type { BOOLEAN, INTEGER }

    /** 규칙 정의. min/max 는 정수 규칙의 명령 인자 범위(spawnChunkRadius 만 0..32). */
    public record Rule(String name, Type type, int defaultValue, int min, int max) {
        public boolean isBoolean() { return type == Type.BOOLEAN; }
    }

    public static final Map<String, Rule> RULES;

    static {
        Map<String, Rule> m = new TreeMap<>();
        String[] bools = {"doFireTick:1", "mobGriefing:1", "keepInventory:0", "doMobSpawning:1", "doMobLoot:1",
                "projectilesCanBreakBlocks:1", "doTileDrops:1", "doEntityDrops:1", "commandBlockOutput:1", "naturalRegeneration:1",
                "doDaylightCycle:1", "logAdminCommands:1", "showDeathMessages:1", "sendCommandFeedback:1", "reducedDebugInfo:0",
                "spectatorsGenerateChunks:1", "disableElytraMovementCheck:0", "doWeatherCycle:1", "doLimitedCrafting:0",
                "announceAdvancements:1", "disableRaids:0", "doInsomnia:1", "doImmediateRespawn:0", "drowningDamage:1",
                "fallDamage:1", "fireDamage:1", "freezeDamage:1", "doPatrolSpawning:1", "doTraderSpawning:1", "doWardenSpawning:1",
                "forgiveDeadPlayers:1", "universalAnger:0", "blockExplosionDropDecay:1", "mobExplosionDropDecay:1",
                "tntExplosionDropDecay:0", "waterSourceConversion:1", "lavaSourceConversion:0", "globalSoundEvents:1",
                "doVinesSpread:1", "enderPearlsVanishOnDeath:1"};
        for (String b : bools) {
            String[] kv = b.split(":");
            m.put(kv[0], new Rule(kv[0], Type.BOOLEAN, Integer.parseInt(kv[1]), 0, 1));
        }
        int[][] ints = {};
        Object[][] intRules = {{"randomTickSpeed", 3}, {"spawnRadius", 10}, {"maxEntityCramming", 24},
                {"maxCommandChainLength", 65536}, {"maxCommandForkCount", 65536}, {"commandModificationBlockLimit", 32768},
                {"playersNetherPortalDefaultDelay", 80}, {"playersNetherPortalCreativeDelay", 1}, {"playersSleepingPercentage", 100},
                {"snowAccumulationHeight", 1}};
        for (Object[] r : intRules) m.put((String) r[0], new Rule((String) r[0], Type.INTEGER, (Integer) r[1], Integer.MIN_VALUE, Integer.MAX_VALUE));
        m.put("spawnChunkRadius", new Rule("spawnChunkRadius", Type.INTEGER, 2, 0, 32));
        if (ints.length != 0) throw new IllegalStateException();
        RULES = java.util.Collections.unmodifiableMap(m);
    }

    private final Map<String, Integer> values = new ConcurrentHashMap<>();
    private volatile BiConsumer<Rule, Integer> listener = (r, v) -> {};

    public GameRules() {
        for (Rule r : RULES.values()) values.put(r.name(), r.defaultValue());
    }

    /** level.dat 의 문자열 값에서(바닐라: 불리언 Boolean.parseBoolean, 정수 실패 시 경고 후 0). */
    public static GameRules fromStrings(Map<String, String> saved) {
        GameRules g = new GameRules();
        for (var e : saved.entrySet()) {
            Rule r = RULES.get(e.getKey());
            if (r == null) continue;
            g.values.put(r.name(), r.isBoolean() ? (Boolean.parseBoolean(e.getValue()) ? 1 : 0) : parseInt(e.getValue()));
        }
        return g;
    }

    private static int parseInt(String s) {
        if (!s.isEmpty()) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                log.warn("Failed to parse integer {}", s);
            }
        }
        return 0;
    }

    public Map<String, String> toStrings() {
        Map<String, String> out = new TreeMap<>();
        for (Rule r : RULES.values()) out.put(r.name(), serialize(r, values.get(r.name())));
        return out;
    }

    public static String serialize(Rule r, int v) { return r.isBoolean() ? Boolean.toString(v != 0) : Integer.toString(v); }

    public void onChange(BiConsumer<Rule, Integer> l) { this.listener = l; }

    public boolean getBoolean(String name) { return values.getOrDefault(name, 0) != 0; }

    public int getInt(String name) { return values.getOrDefault(name, 0); }

    public int raw(String name) { return values.getOrDefault(name, 0); }

    public void set(String name, int value) {
        Rule r = RULES.get(name);
        if (r == null) throw new IllegalArgumentException(name);
        values.put(name, value);
        listener.accept(r, value);
    }
}
