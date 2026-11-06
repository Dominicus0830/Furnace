// 바닐라 DedicatedServerProperties 이식 — server.properties 의 키·기본값·정규화 규칙이 1.21.1 과 같다.
package org.domi.furnace.server.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.domi.furnace.server.world.Difficulty;
import org.domi.furnace.server.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ServerProperties extends VanillaSettings {

    private static final Logger log = LoggerFactory.getLogger(ServerProperties.class);
    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();
    private static final Pattern SHA1 = Pattern.compile("^[a-fA-F0-9]{40}$");

    /** 서버 리소스 팩 설정(resource-pack 이 비어 있으면 없음). prompt 는 텍스트 컴포넌트 JSON 원문. */
    public record ResourcePackInfo(UUID id, String url, String hash, boolean isRequired, String prompt) {}

    public final boolean onlineMode = get("online-mode", true);
    public final boolean preventProxyConnections = get("prevent-proxy-connections", false);
    public final String serverIp = get("server-ip", "");
    public final boolean spawnAnimals = get("spawn-animals", true);
    public final boolean spawnNpcs = get("spawn-npcs", true);
    public final boolean pvp = get("pvp", true);
    public final boolean allowFlight = get("allow-flight", false);
    public final String motd = get("motd", "A Minecraft Server");
    public final String bugReportLink = get("bug-report-link", "");
    public final boolean forceGameMode = get("force-gamemode", false);
    public final boolean enforceWhitelist = get("enforce-whitelist", false);
    public final Difficulty difficulty = get("difficulty",
            dispatchNumberOrString(Difficulty::byId, Difficulty::byName), Difficulty::key, Difficulty.EASY);
    public final GameMode gameMode = get("gamemode",
            dispatchNumberOrString(GameMode::byId, GameMode::byName), GameMode::key, GameMode.SURVIVAL);
    public final String levelName = get("level-name", "world");
    public final int serverPort = get("server-port", 25565);
    public final Boolean announcePlayerAchievements = getLegacyBoolean("announce-player-achievements");
    public final boolean enableQuery = get("enable-query", false);
    public final int queryPort = get("query.port", 25565);
    public final boolean enableRcon = get("enable-rcon", false);
    public final int rconPort = get("rcon.port", 25575);
    public final String rconPassword = get("rcon.password", "");
    public final boolean hardcore = get("hardcore", false);
    public final boolean allowNether = get("allow-nether", true);
    public final boolean spawnMonsters = get("spawn-monsters", true);
    public final boolean useNativeTransport = get("use-native-transport", true);
    public final boolean enableCommandBlock = get("enable-command-block", false);
    public final int spawnProtection = get("spawn-protection", 16);
    public final int opPermissionLevel = get("op-permission-level", 4);
    public final int functionPermissionLevel = get("function-permission-level", 2);
    public final long maxTickTime = get("max-tick-time", TimeUnit.MINUTES.toMillis(1L));
    public final int maxChainedNeighborUpdates = get("max-chained-neighbor-updates", 1000000);
    public final int rateLimitPacketsPerSecond = get("rate-limit", 0);
    public final int viewDistance = get("view-distance", 10);
    public final int simulationDistance = get("simulation-distance", 10);
    public final int maxPlayers = get("max-players", 20);
    public final int networkCompressionThreshold = get("network-compression-threshold", 256);
    public final boolean broadcastRconToOps = get("broadcast-rcon-to-ops", true);
    public final boolean broadcastConsoleToOps = get("broadcast-console-to-ops", true);
    public final int maxWorldSize = get("max-world-size", v -> Math.clamp(v, 1, 29999984), 29999984);
    public final boolean syncChunkWrites = get("sync-chunk-writes", false);
    public final String regionFileCompression = get("region-file-compression", "deflate");
    public final boolean enableJmxMonitoring = get("enable-jmx-monitoring", false);
    public final boolean enableStatus = get("enable-status", true);
    public final boolean hideOnlinePlayers = get("hide-online-players", false);
    public final int entityBroadcastRangePercentage = get("entity-broadcast-range-percentage", v -> Math.clamp(v, 10, 1000), 100);
    public final String textFilteringConfig = get("text-filtering-config", "");
    public final Optional<ResourcePackInfo> serverResourcePackInfo;
    public final List<String> initialEnabledPacks;
    public final List<String> initialDisabledPacks;
    public final MutableValue<Integer> playerIdleTimeout = getMutable("player-idle-timeout",
            wrapNumberDeserializer(Integer::parseInt), Object::toString, 0);
    public final MutableValue<Boolean> whiteList = getMutable("white-list", Boolean::valueOf, Object::toString, false);
    public final boolean enforceSecureProfile = get("enforce-secure-profile", true);
    public final boolean logIps = get("log-ips", true);
    /** level-seed 가 비어 있으면 비어 있다(바닐라는 이때 새 월드에 임의 시드를 쓴다). */
    public final OptionalLong levelSeed;
    public final boolean generateStructures;
    public final JsonObject generatorSettings;
    public final String levelType;
    public boolean acceptsTransfers = get("accepts-transfers", false);

    public ServerProperties(Properties properties) {
        super(properties);
        String seed = get("level-seed", "");
        this.generateStructures = get("generate-structures", true);
        this.levelSeed = parseSeed(seed);
        this.generatorSettings = get("generator-settings", s -> parseJsonObject(!s.isEmpty() ? s : "{}"), new JsonObject());
        this.levelType = get("level-type", s -> s.toLowerCase(Locale.ROOT), "minecraft:normal");
        this.serverResourcePackInfo = getServerPackInfo(
                get("resource-pack-id", ""),
                get("resource-pack", ""),
                get("resource-pack-sha1", ""),
                getLegacyString("resource-pack-hash"),
                get("require-resource-pack", false),
                get("resource-pack-prompt", ""));
        this.initialEnabledPacks = splitPacks(get("initial-enabled-packs", "vanilla"));
        this.initialDisabledPacks = splitPacks(get("initial-disabled-packs", ""));
    }

    public static ServerProperties fromFile(Path path) {
        return new ServerProperties(loadFromFile(path));
    }

    /** WorldOptions.parseSeed: 앞뒤 공백 제거, 비면 없음, 정수가 아니면 String.hashCode. */
    public static OptionalLong parseSeed(String raw) {
        String s = raw.trim();
        if (s.isEmpty()) return OptionalLong.empty();
        try {
            return OptionalLong.of(Long.parseLong(s));
        } catch (NumberFormatException e) {
            return OptionalLong.of(s.hashCode());
        }
    }

    /** GsonHelper.parse — 엄격 모드, 객체가 아니면 예외(바닐라도 기동 실패). */
    private static JsonObject parseJsonObject(String s) {
        try {
            JsonReader reader = new JsonReader(new StringReader(s));
            reader.setStrictness(com.google.gson.Strictness.LEGACY_STRICT);
            JsonObject o = GSON.getAdapter(JsonObject.class).read(reader);
            if (o == null) throw new JsonParseException("JSON data was null or empty");
            return o;
        } catch (java.io.IOException e) {
            throw new JsonParseException(e);
        }
    }

    private static boolean isNullOrEmpty(String s) { return s == null || s.isEmpty(); }

    /** Splitter.on(',').trimResults().splitToList. */
    private static List<String> splitPacks(String s) {
        return Arrays.stream(s.split(",", -1)).map(String::strip).toList();
    }

    private static String validatePrompt(String prompt) {
        if (isNullOrEmpty(prompt)) return null;
        try {
            JsonParser.parseString(prompt);
            return prompt;
        } catch (Exception e) {
            log.warn("Failed to parse resource pack prompt '{}'", prompt, e);
            return null;
        }
    }

    private static Optional<ResourcePackInfo> getServerPackInfo(String id, String url, String sha1, String legacyHash,
                                                                boolean required, String prompt) {
        if (url.isEmpty()) return Optional.empty();
        String hash;
        if (!sha1.isEmpty()) {
            hash = sha1;
            if (!isNullOrEmpty(legacyHash)) {
                log.warn("resource-pack-hash is deprecated and found along side resource-pack-sha1. resource-pack-hash will be ignored.");
            }
        } else if (!isNullOrEmpty(legacyHash)) {
            log.warn("resource-pack-hash is deprecated. Please use resource-pack-sha1 instead.");
            hash = legacyHash;
        } else {
            hash = "";
        }
        if (hash.isEmpty()) {
            log.warn("You specified a resource pack without providing a sha1 hash. Pack will be updated on the client only if you change the name of the pack.");
        } else if (!SHA1.matcher(hash).matches()) {
            log.warn("Invalid sha1 for resource-pack-sha1");
        }
        String validPrompt = validatePrompt(prompt);
        UUID uuid;
        if (id.isEmpty()) {
            uuid = UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8));
            log.warn("resource-pack-id missing, using default of {}", uuid);
        } else {
            try {
                uuid = UUID.fromString(id);
            } catch (IllegalArgumentException e) {
                log.warn("Failed to parse '{}' into UUID", id);
                return Optional.empty();
            }
        }
        return Optional.of(new ResourcePackInfo(uuid, url, hash, required, validPrompt));
    }
}
