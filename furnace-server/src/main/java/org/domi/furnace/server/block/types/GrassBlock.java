// 바닐라 GrassBlock: 잔디 블록. 퍼지는 흙 + 뼛가루(위가 공기면 둘레에 풀·꽃).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.LevelFeature;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class GrassBlock extends SpreadingSnowyDirtBlock implements BonemealableBlock {

    private static final int SHORT_GRASS = B.id("short_grass");

    public GrassBlock(int block) { super(block); }

    @Override
    public boolean isValidBonemealTarget(BlockLevel level, Pos pos, int state) { return B.isAir(level.getBlockState(pos.above())); }

    @Override
    public boolean isBonemealSuccess(BlockLevel level, RandomSource random, Pos pos, int state) { return true; }

    @Override
    public void performBonemeal(BlockLevel level, RandomSource random, Pos pos, int state) {
        Pos above = pos.above();
        int shortGrass = B.defaultState(SHORT_GRASS);
        LevelFeature grassBonemeal = level.placedFeature("minecraft:grass_bonemeal");
        // 미구현이면 통째로 안 한다(가짜로 켜지 않는다)
        if (grassBonemeal != null && !grassBonemeal.supported()) return;
        outer:
        for (int i = 0; i < 128; i++) {
            Pos p = above;
            for (int j = 0; j < i / 16; j++) {
                p = p.offset(random.nextInt(3) - 1, (random.nextInt(3) - 1) * random.nextInt(3) / 2, random.nextInt(3) - 1);
                if (!B.is(level.getBlockState(p.below()), block) || B.isCollisionShapeFullBlock(level.getBlockState(p))) continue outer;
            }
            int s = level.getBlockState(p);
            if (B.is(s, SHORT_GRASS) && random.nextInt(10) == 0) ((BonemealableBlock) B.behavior(shortGrass)).performBonemeal(level, random, p, s);
            if (B.isAir(s)) {
                LevelFeature f;
                if (random.nextInt(8) == 0) {
                    f = level.biomeFlowerFeature(level.biomeAt(p.x(), p.y(), p.z()));
                    if (f == null) continue;
                } else {
                    if (grassBonemeal == null) continue;
                    f = grassBonemeal;
                }
                // 미구현 꽃 feature(가정: 1.21.1 바이옴에는 없음 — 시험으로 확인)는 놓지 않는다
                if (f.supported()) level.placeFeature(f, p, random);
            }
        }
    }
}
