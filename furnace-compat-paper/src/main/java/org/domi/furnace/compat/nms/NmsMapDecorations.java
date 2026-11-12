package org.domi.furnace.compat.nms;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import org.domi.furnace.api.Furnace;

/**
 * 지도 표시 종류 레지스트리 shim(easy-nms E4c-2). 번호 순서는 서버 내장 레지스트리 목록({@code builtIn("minecraft:map_decoration_type")}, 35개)이고,
 * 그림 이름·액자 표시·지도 색·탐험 지도 표시·개수 세기는 바닐라 1.21.1 MapDecorationTypes 정의 값이다(바닐라 jar 를 직접 돌려 읽었다,
 * context-notes 2026-11-12-easy-nms-e4c2). 기본값 없는 레지스트리다.
 */
public final class NmsMapDecorations {

    private NmsMapDecorations() {}

    private static final Map<String, MapDecorationType> ROWS = new HashMap<>();

    /** 바닐라 등록 도우미 둘: (이름, 그림, 액자 표시, 개수 세기) = 지도 색 없음·탐험 아님. */
    private static void row(String name, String asset, boolean showOnItemFrame, boolean trackCount) {
        row(name, asset, showOnItemFrame, MapDecorationType.NO_MAP_COLOR, trackCount, false);
    }

    private static void row(String name, String asset, boolean showOnItemFrame, int mapColor, boolean trackCount, boolean explorationMapElement) {
        ROWS.put("minecraft:" + name, new MapDecorationType(ResourceLocation.withDefaultNamespace(asset), showOnItemFrame, mapColor, explorationMapElement, trackCount));
    }

    static {   // 바닐라 1.21.1 MapDecorationTypes 정의
        row("player", "player", false, true);
        row("frame", "frame", true, true);
        row("red_marker", "red_marker", false, true);
        row("blue_marker", "blue_marker", false, true);
        row("target_x", "target_x", true, false);
        row("target_point", "target_point", true, false);
        row("player_off_map", "player_off_map", false, true);
        row("player_off_limits", "player_off_limits", false, true);
        row("mansion", "woodland_mansion", true, 5393476, false, true);
        row("monument", "ocean_monument", true, 3830373, false, true);
        for (String c : new String[] {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple",
                "blue", "brown", "green", "red", "black"}) {
            row("banner_" + c, c + "_banner", true, true);
        }
        row("red_x", "red_x", true, false);
        for (String v : new String[] {"desert", "plains", "savanna", "snowy", "taiga"}) {
            row("village_" + v, v + "_village", true, 10066329, false, true);
        }
        row("jungle_temple", "jungle_temple", true, 10066329, false, true);
        row("swamp_hut", "swamp_hut", true, 10066329, false, true);
        row("trial_chambers", "trial_chambers", true, 12741452, false, true);
    }

    private static volatile NmsMappedRegistry<MapDecorationType> registry;

    public static Registry<MapDecorationType> registry() { return mapped(); }

    private static NmsMappedRegistry<MapDecorationType> mapped() {
        NmsMappedRegistry<MapDecorationType> r = registry;
        if (r == null) {
            synchronized (NmsMapDecorations.class) {
                r = registry;
                if (r == null) {
                    r = new NmsMappedRegistry<>(Registries.MAP_DECORATION_TYPE);
                    var ids = Furnace.server().gameRegistries().builtIn("minecraft:map_decoration_type");
                    if (ids.isEmpty()) throw new IllegalStateException("서버가 지도 표시 종류 레지스트리 목록을 주지 않았다");
                    for (String id : ids) {
                        MapDecorationType row = ROWS.get(id);
                        if (row == null) throw new IllegalStateException("정의가 없는 서버 지도 표시 종류: " + id);
                        r.add(ResourceLocation.parse(id), row);
                    }
                    registry = r;
                }
            }
        }
        return r;
    }

    /** MapDecorationTypes 상수용: 이름의 등록된 손잡이. */
    public static Holder<MapDecorationType> holder(String name) {
        return mapped().getHolder(ResourceLocation.withDefaultNamespace(name))
                .orElseThrow(() -> new IllegalStateException("서버 지도 표시 종류 레지스트리에 없는 이름: " + name));
    }
}
