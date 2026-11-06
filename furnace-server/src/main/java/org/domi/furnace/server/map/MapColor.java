// 바닐라 MapColor 의 색 번호(0..63)와 밝기(Brightness). 지도 색 바이트 = 번호 << 2 | 밝기 번호.
// 블록 상태별 색은 blocks/stateflags 표(BlockInfo.mapColor)에 RGB 로 있다 — 번호 표(blocks/mapcolors, tools/vanilla-data/ExportMapColors)로 번호를 되찾는다.
package org.domi.furnace.server.map;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.world.BlockAttrs;

public final class MapColor {
    private MapColor() { }

    public static final int NONE = 0, DIRT = 10, STONE = 11, WATER = 12, COLOR_ORANGE = 15, COLOR_BROWN = 26;

    /** MapColor.Brightness: LOW(180) NORMAL(220) HIGH(255) LOWEST(135) — 번호는 선언 순서(0..3). */
    public static final int LOW = 0, NORMAL = 1, HIGH = 2, LOWEST = 3;

    private static volatile byte[] stateIds;

    /** BlockState.getMapColor(level, pos) 의 번호(0..63) — 상태 id 하나로 정해진다. */
    public static int ofState(int state) {
        byte[] t = stateIds;
        if (t == null) stateIds = t = buildStateIds();
        return state >= 0 && state < t.length ? t[state] : NONE;
    }

    private static byte[] buildStateIds() {
        Map<Integer, Integer> idByRgb = new HashMap<>();
        try (InputStream in = MapColor.class.getClassLoader().getResourceAsStream("blocks/mapcolors_1.21.1.txt")) {
            if (in == null) throw new IllegalStateException("지도 색 표 리소스 없음");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("#") || line.isBlank()) continue;
                String[] p = line.split(" ");
                idByRgb.put(Integer.parseInt(p[1]), Integer.parseInt(p[0]));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        byte[] t = new byte[BlockAttrs.get().stateCount()];
        for (int s = 0; s < t.length; s++) {
            Integer id = idByRgb.get(B.INFO.mapColor(s));
            if (id == null) throw new IllegalStateException("모르는 지도 색 RGB: 상태 " + s);
            t[s] = (byte) (int) id;
        }
        return t;
    }

    /** MapColor.getPackedId(brightness) — NONE(0)도 그대로 번호 << 2 | 밝기다. */
    public static byte packed(int color, int brightness) { return (byte) (color << 2 | brightness & 3); }
}
