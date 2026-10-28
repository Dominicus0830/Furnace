// 바닐라 ObjectiveCriteria — 목표의 기준. 이름 있는 기준(dummy·trigger·health …)과 통계 기준("minecraft.custom:minecraft.jump" 꼴).
// readOnly 기준(health 등)은 명령으로 값을 바꿀 수 없고, 서버가 자동으로 갱신한다.
package org.domi.furnace.server.scores;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.domi.furnace.server.stats.PlayerStats;

public final class ObjectiveCriteria {

    /** 바닐라 RenderType. */
    public enum RenderType {
        INTEGER("integer"),
        HEARTS("hearts");

        private final String id;

        RenderType(String id) { this.id = id; }

        public String id() { return id; }

        /** 바닐라 byId(모르면 integer). */
        public static RenderType byId(String s) {
            for (RenderType r : values()) if (r.id.equals(s)) return r;
            return INTEGER;
        }
    }

    // 바닐라: CUSTOM_CRITERIA(이름 있는 것) 와 CRITERIA_CACHE(통계 기준 포함 전부)
    private static final Map<String, ObjectiveCriteria> CUSTOM = new HashMap<>();
    private static final Map<String, ObjectiveCriteria> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    public static final ObjectiveCriteria DUMMY = register("dummy");
    public static final ObjectiveCriteria TRIGGER = register("trigger");
    public static final ObjectiveCriteria DEATH_COUNT = register("deathCount");
    public static final ObjectiveCriteria KILL_COUNT_PLAYERS = register("playerKillCount");
    public static final ObjectiveCriteria KILL_COUNT_ALL = register("totalKillCount");
    public static final ObjectiveCriteria HEALTH = register("health", true, RenderType.HEARTS);
    public static final ObjectiveCriteria FOOD = register("food", true, RenderType.INTEGER);
    public static final ObjectiveCriteria AIR = register("air", true, RenderType.INTEGER);
    public static final ObjectiveCriteria ARMOR = register("armor", true, RenderType.INTEGER);
    public static final ObjectiveCriteria EXPERIENCE = register("xp", true, RenderType.INTEGER);
    public static final ObjectiveCriteria LEVEL = register("level", true, RenderType.INTEGER);
    public static final ObjectiveCriteria[] TEAM_KILL = new ObjectiveCriteria[16];
    public static final ObjectiveCriteria[] KILLED_BY_TEAM = new ObjectiveCriteria[16];

    static {
        for (int i = 0; i < 16; i++) TEAM_KILL[i] = register("teamkill." + ChatFormatting.byColorId(i).key());
        for (int i = 0; i < 16; i++) KILLED_BY_TEAM[i] = register("killedByTeam." + ChatFormatting.byColorId(i).key());
    }

    private final String name;
    private final boolean readOnly;
    private final RenderType renderType;

    private ObjectiveCriteria(String name, boolean readOnly, RenderType renderType) {
        this.name = name;
        this.readOnly = readOnly;
        this.renderType = renderType;
        CACHE.put(name, this);
    }

    private static ObjectiveCriteria register(String name, boolean readOnly, RenderType type) {
        ObjectiveCriteria c = new ObjectiveCriteria(name, readOnly, type);
        CUSTOM.put(name, c);
        return c;
    }

    private static ObjectiveCriteria register(String name) { return register(name, false, RenderType.INTEGER); }

    /** 바닐라 getCustomCriteriaNames(HashMap 키 순서). */
    public static Set<String> customCriteriaNames() { return Set.copyOf(CUSTOM.keySet()); }

    /** 바닐라 byName: 이름 있는 기준 또는 "종류:값"(점을 콜론 대신 쓴 리소스 이름) 통계 기준. 모르면 빈 값. */
    public static Optional<ObjectiveCriteria> byName(String name) {
        ObjectiveCriteria c = CACHE.get(name);
        if (c != null) return Optional.of(c);
        int colon = name.indexOf(':');
        if (colon < 0) return Optional.empty();
        String type = dotted(name.substring(0, colon));
        String value = dotted(name.substring(colon + 1));
        if (type == null || value == null) return Optional.empty();
        String serverName = type.equals("minecraft:custom") ? value : type + ":" + value;
        PlayerStats.Stat stat = PlayerStats.parse(serverName);
        if (!stat.type().equals(type) || !PlayerStats.isValid(stat)) return Optional.empty();
        String canonical = statCriteriaName(stat);
        // 생성자가 CACHE 에 넣으므로 computeIfAbsent 안에서 만들면 Recursive update — 잠그고 확인 후 만든다
        synchronized (CACHE) {
            ObjectiveCriteria ex = CACHE.get(canonical);
            return Optional.of(ex != null ? ex : new ObjectiveCriteria(canonical, false, RenderType.INTEGER));
        }
    }

    /** 통계의 기준 이름(바닐라 Stat.buildName: 종류와 값의 ':' 를 '.' 로 바꿔 ':' 로 잇는다). */
    public static String statCriteriaName(PlayerStats.Stat stat) {
        return stat.type().replace(':', '.') + ":" + stat.value().replace(':', '.');
    }

    /** 통계 이름(서버 안쪽 "minecraft:jump" / "minecraft:mined:minecraft:stone")의 기준(통계가 이상하면 null). */
    public static ObjectiveCriteria forStat(String statName) {
        PlayerStats.Stat stat = PlayerStats.parse(statName);
        ObjectiveCriteria c = CACHE.get(statCriteriaName(stat));
        return c;
    }

    /** 바닐라 ResourceLocation.of(s, '.') — 점 앞이 이름공간(없으면 minecraft). 형식이 틀리면 null. */
    private static String dotted(String s) {
        int dot = s.indexOf('.');
        String ns = dot >= 0 ? s.substring(0, dot) : "minecraft";
        String path = dot >= 0 ? s.substring(dot + 1) : s;
        if (ns.isEmpty()) ns = "minecraft";
        for (int i = 0; i < ns.length(); i++) if (!validNs(ns.charAt(i))) return null;
        for (int i = 0; i < path.length(); i++) if (!validNs(path.charAt(i)) && path.charAt(i) != '/') return null;
        return ns + ":" + path;
    }

    private static boolean validNs(char c) {
        return c == '_' || c == '-' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '.';
    }

    public String name() { return name; }

    public boolean isReadOnly() { return readOnly; }

    public RenderType defaultRenderType() { return renderType; }

    @Override
    public String toString() { return name; }
}
