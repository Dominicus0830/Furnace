// LEGACY-ORACLE since 8dea6629: ChunkLevel#tickChunk 의 구역 무작위 틱 루프(T-053 마스크 이전 — 구역 24개를 모두 isRandomlyTicking 으로 확인)
// 옮길 때 바꾼 것은 클래스 밖에서 돌게 하는 부분뿐이다: this → 인자 level, random 필드 → level.random()(같은 객체),
// private blockRandomPos → 패키지 접근(같은 메서드). 구역 순서·난수 소비·호출은 그 커밋 그대로다.
package org.domi.furnace.server.block;

import org.domi.furnace.server.identity.LegacyOracle;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;

@LegacyOracle(since = "8dea6629", copyOf = "ChunkLevel#tickChunk(구역 무작위 틱 루프)")
final class LegacyRandomTickSections {

    private LegacyRandomTickSections() {}

    static void run(ChunkLevel level, ChunkColumn col, int x0, int z0, int randomTickSpeed) {
        ChunkSection[] sections = col.sections();
        for (int si = 0; si < sections.length; si++) {
            ChunkSection sec = sections[si];
            if (sec == null || !sec.isRandomlyTicking()) continue;
            int y0 = col.minY() + (si << 4);
            for (int n = 0; n < randomTickSpeed; n++) {
                Pos p = level.blockRandomPos(x0, y0, z0);
                int st = sections[si].getBlock(p.x() - x0, p.y() - y0, p.z() - z0);
                BlockBehavior b = BlockBehaviors.of(st);
                if (b.isRandomlyTicking(st)) b.randomTick(st, level, p, level.random());
                int f = b.getFluidState(st);
                if (Fluids.isLava(f)) FluidTicker.randomTick(level, p, f, level.random());
            }
        }
    }
}
