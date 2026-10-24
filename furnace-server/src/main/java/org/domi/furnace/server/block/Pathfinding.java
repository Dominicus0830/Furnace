// BlockState.isPathfindable(PathComputationType) 표(바닐라에서 뽑은 상태별 1글자: 비트 1 땅·2 물·4 공중).
package org.domi.furnace.server.block;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public final class Pathfinding {
    private Pathfinding() { }

    public static final int LAND = 1, WATER = 2, AIR = 4;

    private static final byte[] BITS = load();

    private static byte[] load() {
        try (var in = Pathfinding.class.getClassLoader().getResourceAsStream("blocks/pathfindable_1.21.1.txt")) {
            if (in == null) throw new IllegalStateException("blocks/pathfindable_1.21.1.txt 없음");
            String line = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).readLine();
            byte[] out = new byte[line.length()];
            for (int i = 0; i < out.length; i++) out[i] = (byte) (line.charAt(i) - '0');
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** state.isPathfindable(type) — type 은 LAND·WATER·AIR. */
    public static boolean isPathfindable(int state, int type) { return state >= 0 && state < BITS.length && (BITS[state] & type) != 0; }
}
