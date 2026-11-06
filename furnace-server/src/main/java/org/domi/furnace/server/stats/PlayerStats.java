// 바닐라 ServerStatsCounter — 플레이어 한 명의 통계(world/stats/<uuid>.json). 종류(stat_type)별로 값 이름 → 횟수.
// 값은 int 로 포화 덧셈하고, 바뀐 것만 모아 두었다가 클라이언트가 요청하면(client_command REQUEST_STATS) award_stats 로 보낸다.
package org.domi.furnace.server.stats;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.protocol.play.AwardStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PlayerStats {

    private static final Logger log = LoggerFactory.getLogger("Furnace");

    /** 통계 하나: 종류(minecraft:crafted …)와 그 값(minecraft:stick …). */
    public record Stat(String type, String value) { }

    /** 값을 찾을 레지스트리(바닐라 StatType.getRegistry). */
    private static String registryOf(String type) {
        return switch (type) {
            case "minecraft:mined" -> "minecraft:block";
            case "minecraft:crafted", "minecraft:used", "minecraft:broken", "minecraft:picked_up", "minecraft:dropped" -> "minecraft:item";
            case "minecraft:killed", "minecraft:killed_by" -> "minecraft:entity_type";
            case "minecraft:custom" -> "minecraft:custom_stat";
            default -> null;
        };
    }

    private final Map<Stat, Integer> values = new LinkedHashMap<>();
    private final Set<Stat> dirty = new LinkedHashSet<>();
    private final Path file;

    public PlayerStats(Path worldDir, UUID uuid) {
        this.file = worldDir.resolve("stats").resolve(uuid + ".json");
        load();
    }

    /**
     * 서버 안쪽의 통계 이름을 해석한다: "minecraft:interact_with_loom"(커스텀), "minecraft:crafted:minecraft:stick"(종류:값).
     * 모르는 통계는 null.
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, Stat> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    public static Stat parse(String name) { return CACHE.computeIfAbsent(name, PlayerStats::parse0); }

    private static Stat parse0(String name) {
        if (name.startsWith("minecraft:")) {
            String rest = name.substring(10);
            int c = rest.indexOf(':');
            if (c > 0) {
                String type = "minecraft:" + rest.substring(0, c);
                if (registryOf(type) != null && !type.equals("minecraft:custom")) {
                    String v = rest.substring(c + 1);
                    return new Stat(type, v.indexOf(':') < 0 ? "minecraft:" + v : v);
                }
            }
        }
        return new Stat("minecraft:custom", name);
    }

    /** 그 종류의 레지스트리에 값이 있는 통계인가(점수판 통계 기준 검사). */
    public static boolean isValid(Stat s) { return valid(s); }

    private static boolean valid(Stat s) {
        String reg = registryOf(s.type());
        return reg != null && ItemContext.installed() && ItemContext.id(reg, s.value()) != null;
    }

    public synchronized int get(Stat s) { return values.getOrDefault(s, 0); }

    public synchronized int get(String name) { return get(parse(name)); }

    /** 바닐라 increment: 포화 덧셈(int 최댓값). 모르는 통계는 무시한다. */
    public synchronized void increment(String name, int amount) {
        Stat s = parse(name);
        if (!valid(s)) return;
        values.put(s, (int) Math.min((long) get(s) + amount, Integer.MAX_VALUE));
        dirty.add(s);
    }

    public synchronized void set(String name, int value) {
        Stat s = parse(name);
        if (!valid(s)) return;
        values.put(s, value);
        dirty.add(s);
    }

    /** 바닐라 markAllDirty(접속할 때). */
    public synchronized void markAllDirty() { dirty.addAll(values.keySet()); }

    /** 바닐라 sendStats: 바뀐 것만 모아 award_stats 패킷으로(비웠으면 빈 패킷도 보낸다). */
    public synchronized AwardStats takeDirtyPacket() {
        Map<Stat, Integer> m = new LinkedHashMap<>();
        for (Stat s : dirty) m.put(s, get(s));
        dirty.clear();
        return new AwardStats(m);
    }

    // ------------------------------------------------------------------ 파일

    private void load() {
        if (!Files.isRegularFile(file)) return;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            if (!root.has("stats")) return;
            for (var t : root.getAsJsonObject("stats").entrySet()) {
                if (registryOf(t.getKey()) == null) {
                    log.warn("Invalid statistic type in {}: Don't know what {} is", file, t.getKey());
                    continue;
                }
                for (var v : t.getValue().getAsJsonObject().entrySet()) {
                    Stat s = new Stat(t.getKey(), v.getKey());
                    if (!valid(s)) {
                        log.warn("Invalid statistic in {}: Don't know what {} is", file, v.getKey());
                        continue;
                    }
                    if (v.getValue().isJsonPrimitive() && v.getValue().getAsJsonPrimitive().isNumber()) values.put(s, v.getValue().getAsInt());
                }
            }
        } catch (IOException | RuntimeException e) {
            log.error("Couldn't read statistics file {}", file, e);
        }
    }

    public synchronized void save() {
        Map<String, JsonObject> byType = new LinkedHashMap<>();
        for (var e : values.entrySet()) {
            byType.computeIfAbsent(e.getKey().type(), k -> new JsonObject()).addProperty(e.getKey().value(), e.getValue());
        }
        JsonObject stats = new JsonObject();
        for (var e : byType.entrySet()) stats.add(e.getKey(), e.getValue());
        JsonObject root = new JsonObject();
        root.add("stats", stats);
        root.addProperty("DataVersion", 3955);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, root.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Couldn't save stats", e);
        }
    }

    /** award_stats 패킷의 (종류 번호, 값 번호). 번호를 모르면 null. */
    public static int[] ids(Stat s) {
        Integer type = ItemContext.id("minecraft:stat_type", s.type());
        String reg = registryOf(s.type());
        Integer value = reg == null ? null : ItemContext.id(reg, s.value());
        return type == null || value == null ? null : new int[]{type, value};
    }
}
