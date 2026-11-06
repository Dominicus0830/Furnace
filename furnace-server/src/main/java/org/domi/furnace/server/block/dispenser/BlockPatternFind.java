// 바닐라 BlockPattern.find(level, pos): pos..pos+(n-1) 상자의 각 칸을 기준으로 24 방향(손가락·엄지)마다 모양을 맞춰 본다.
// 발사기의 깎은 호박(골렘 바탕)·위더 해골(위더 바탕) 판정에만 쓴다. 바탕 모양은 비어 있는 ' ' 칸이 무엇이든 맞는다.
package org.domi.furnace.server.block.dispenser;

import java.util.function.IntPredicate;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;

final class BlockPatternFind {

    private BlockPatternFind() {}

    /** 한 aisle(깊이 1)짜리 모양: rows[j] 의 i 번째 글자. '#' 는 base, '~' 는 공기, ' ' 는 아무것. */
    static boolean find(BlockLevel level, Pos pos, String[] rows, IntPredicate base) {
        int height = rows.length, width = rows[0].length(), depth = 1;
        int n = Math.max(Math.max(width, height), depth);
        for (int x = 0; x < n; x++) {
            for (int y = 0; y < n; y++) {
                for (int z = 0; z < n; z++) {
                    Pos p = pos.offset(x, y, z);
                    for (int finger : Dir.ALL) {
                        for (int thumb : Dir.ALL) {
                            if (thumb == finger || thumb == Dir.opposite(finger)) continue;
                            if (matches(level, p, finger, thumb, rows, base)) return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    // BlockPos.betweenClosed 는 x 가 가장 빨리 돈다 — 존재 여부만 보므로 순서는 결과에 영향 없다.

    private static boolean matches(BlockLevel level, Pos pos, int finger, int thumb, String[] rows, IntPredicate base) {
        int fx = Dir.DX[finger], fy = Dir.DY[finger], fz = Dir.DZ[finger];
        int tx = Dir.DX[thumb], ty = Dir.DY[thumb], tz = Dir.DZ[thumb];
        int cx = fy * tz - fz * ty, cy = fz * tx - fx * tz, cz = fx * ty - fy * tx;   // finger × thumb
        for (int i = 0; i < rows[0].length(); i++) {
            for (int j = 0; j < rows.length; j++) {
                char c = rows[j].charAt(i);
                if (c == ' ') continue;
                Pos q = pos.offset(-tx * j + cx * i, -ty * j + cy * i, -tz * j + cz * i);   // k(깊이) = 0
                int s = level.getBlockState(q);
                if (c == '~' ? !B.isAir(s) : !base.test(s)) return false;
            }
        }
        return true;
    }
}
