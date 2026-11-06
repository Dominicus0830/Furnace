// 바닐라 JigsawPlacement.generateJigsaw(level, pool, target, maxDepth, pos, keepJigsaws): 직소 블록의 "생성" 버튼과 /place jigsaw 가 쓴다.
// 조각이 닿는 청크는 모두 이 region 에 로드돼 있어야 한다(바닐라는 그 자리에서 불러온다) — 아니면 일부만 놓지 않고 GridException.
package org.domi.furnace.server.world.gen.structure;

import java.util.List;
import org.domi.furnace.server.world.LiveGrid;
import org.domi.furnace.server.world.RegionLevel;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class JigsawGenerate {

    private JigsawGenerate() {}

    /** 조각 생성에 실패하면 false(풀 없음·붙을 곳 없음 등). 청크가 로드돼 있지 않거나 너무 크면 LiveGrid.GridException. */
    public static boolean generate(RegionLevel level, NoiseWorldSource gen, String pool, String target, int maxDepth, int x, int y, int z, boolean keepJigsaws) {
        StructureEngine engine = gen.structureEngine();
        int cx = x >> 4, cz = z >> 4;
        requireLoaded(level, cx, cz, cx, cz);
        List<JigsawPiece> pieces = JigsawStructure.generateForCommand(engine, gen, pool, target, maxDepth, x, y, z);
        if (pieces == null) return false;
        int minCx = cx, minCz = cz, maxCx = cx, maxCz = cz;
        for (JigsawPiece piece : pieces) {
            BoundingBox b = piece.boundingBox();
            minCx = Math.min(minCx, b.minX() >> 4); minCz = Math.min(minCz, b.minZ() >> 4);
            maxCx = Math.max(maxCx, b.maxX() >> 4); maxCz = Math.max(maxCz, b.maxZ() >> 4);
        }
        requireLoaded(level, minCx, minCz, maxCx, maxCz);
        LiveGrid grid = LiveGrid.covering(level, minCx, minCz, maxCx, maxCz, gen.seed());
        WorldgenRandom random = new WorldgenRandom();
        random.setSeed(level.random().nextLong());
        BoundingBox infinite = new BoundingBox(Integer.MIN_VALUE / 2, Integer.MIN_VALUE / 2, Integer.MIN_VALUE / 2,
                Integer.MAX_VALUE / 2, Integer.MAX_VALUE / 2, Integer.MAX_VALUE / 2);
        PostCtx ctx = new PostCtx(engine, grid.view, random, random, infinite, cx, cz, x, y, z);
        for (JigsawPiece piece : pieces) piece.place(ctx, keepJigsaws);
        grid.checkComplete();
        grid.commit(2);   // 템플릿·구조물 배치 flag(2) — 모양 갱신은 사본 안에서 이미 했다
        return true;
    }

    private static void requireLoaded(RegionLevel level, int minCx, int minCz, int maxCx, int maxCz) {
        for (int cx = minCx; cx <= maxCx; cx++)
            for (int cz = minCz; cz <= maxCz; cz++)
                if (level.listenerChunk(cx, cz) == null) throw new LiveGrid.GridException(false);
    }
}
