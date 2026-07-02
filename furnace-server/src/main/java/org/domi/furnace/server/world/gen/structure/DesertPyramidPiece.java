// 사막 피라미드 스텁 조각 — BoundingBox·회전만 바닐라와 일치. 블록배치는 S2b(postProcess 미구현).
package org.domi.furnace.server.world.gen.structure;

import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class DesertPyramidPiece extends StructurePiece {

    public static final int WIDTH = 21;
    public static final int HEIGHT = 15;
    public static final int DEPTH = 21;

    public DesertPyramidPiece(WorldgenRandom random, int minBlockX, int minBlockZ) {
        this(Direction.randomHorizontal(random), minBlockX, minBlockZ);
    }

    private DesertPyramidPiece(Direction orientation, int minBlockX, int minBlockZ) {
        super(makeBoundingBox(minBlockX, 64, minBlockZ, orientation, WIDTH, HEIGHT, DEPTH), orientation);
    }
}
