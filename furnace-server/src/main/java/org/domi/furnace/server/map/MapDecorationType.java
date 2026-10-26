// 바닐라 MapDecorationTypes — map_decoration_type 레지스트리(번호 = 선언 순서 = 네트워크 id).
// 필드: 이름, 액자 안 지도에도 보이는가(showOnItemFrame), 탐험 지도 표식인가(explorationMapElement), 센 수(trackCount — 256개 제한),
// 지도 아이템 색(mapColor, 없으면 -1 — 목표 표식을 달 때 map_color 컴포넌트가 된다).
package org.domi.furnace.server.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record MapDecorationType(int id, String name, boolean showOnItemFrame, boolean explorationMapElement, boolean trackCount, int mapColor) {

    private static final List<MapDecorationType> ALL = new ArrayList<>();
    private static final Map<String, MapDecorationType> BY_NAME = new HashMap<>();

    private static MapDecorationType reg(String name, boolean showOnItemFrame, boolean trackCount, boolean exploration) {
        return reg(name, showOnItemFrame, trackCount, exploration, -1);
    }

    private static MapDecorationType reg(String name, boolean showOnItemFrame, boolean trackCount, boolean exploration, int mapColor) {
        MapDecorationType t = new MapDecorationType(ALL.size(), "minecraft:" + name, showOnItemFrame, exploration, trackCount, mapColor);
        ALL.add(t);
        BY_NAME.put(t.name, t);
        return t;
    }

    public static final MapDecorationType PLAYER = reg("player", false, true, false),
            FRAME = reg("frame", true, true, false),
            RED_MARKER = reg("red_marker", false, true, false),
            BLUE_MARKER = reg("blue_marker", false, true, false),
            TARGET_X = reg("target_x", true, false, false),
            TARGET_POINT = reg("target_point", true, false, false),
            PLAYER_OFF_MAP = reg("player_off_map", false, true, false),
            PLAYER_OFF_LIMITS = reg("player_off_limits", false, true, false),
            MANSION = reg("mansion", true, false, true, 5393476),
            MONUMENT = reg("monument", true, false, true, 3830373);

    /** 깃발 16색(banner_<색> — 흰색부터 DyeColor 순서). */
    public static final String[] DYES = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
            "purple", "blue", "brown", "green", "red", "black"};

    static {
        for (String dye : DYES) reg("banner_" + dye, true, true, false);
    }

    public static final MapDecorationType RED_X = reg("red_x", true, false, false),
            VILLAGE_DESERT = reg("village_desert", true, false, true, 10066329),
            VILLAGE_PLAINS = reg("village_plains", true, false, true, 10066329),
            VILLAGE_SAVANNA = reg("village_savanna", true, false, true, 10066329),
            VILLAGE_SNOWY = reg("village_snowy", true, false, true, 10066329),
            VILLAGE_TAIGA = reg("village_taiga", true, false, true, 10066329),
            JUNGLE_TEMPLE = reg("jungle_temple", true, false, true, 10066329),
            SWAMP_HUT = reg("swamp_hut", true, false, true, 10066329),
            TRIAL_CHAMBERS = reg("trial_chambers", true, false, true, 12741452);

    /** 이름("minecraft:monument" 또는 "monument") → 종류. 모르면 null. */
    public static MapDecorationType byName(String name) {
        return BY_NAME.get(name.indexOf(':') < 0 ? "minecraft:" + name : name);
    }

    public static MapDecorationType byId(int id) { return ALL.get(id); }

    /** 깃발 색(DyeColor 순서 0..15) → 깃발 표식. */
    public static MapDecorationType banner(int dyeId) { return ALL.get(PLAYER_OFF_LIMITS.id + 3 + dyeId); }

    public static int count() { return ALL.size(); }
}
