// 바닐라 PoolElementStructurePiece — 풀 요소 하나(위치·회전·지면 보정·연결부)를 배치하는 조각.
package org.domi.furnace.server.world.gen.structure;

import java.util.ArrayList;
import java.util.List;

public final class JigsawPiece extends StructurePiece {

    /** 바닐라 JigsawJunction(sourceX, sourceGroundY, sourceZ, deltaY, destProjection) — Beardifier 가 쓴다. */
    public record Junction(int sourceX, int sourceGroundY, int sourceZ, int deltaY, boolean destRigid) {}

    final JigsawPools.Element element;
    int px, py, pz;
    final int groundLevelDelta;
    final int rotation;
    final boolean keepLiquids;
    final List<Junction> junctions = new ArrayList<>();

    JigsawPiece(JigsawPools.Element element, int x, int y, int z, int groundLevelDelta, int rotation, BoundingBox box, boolean keepLiquids) {
        super(box, Direction.NORTH);
        this.element = element;
        this.px = x;
        this.py = y;
        this.pz = z;
        this.groundLevelDelta = groundLevelDelta;
        this.rotation = rotation;
        this.keepLiquids = keepLiquids;
    }

    public JigsawPools.Element element() { return element; }

    public int groundLevelDelta() { return groundLevelDelta; }

    public List<Junction> junctions() { return junctions; }

    public boolean rigid() { return element.rigid(); }

    @Override
    public void move(int dx, int dy, int dz) {
        super.move(dx, dy, dz);
        px += dx;
        py += dy;
        pz += dz;
    }

    @Override
    public void postProcess(PostCtx c) {
        element.place(c, px, py, pz, rotation, c.writableBox, keepLiquids, false);
    }

    @Override
    public String toString() {
        return "<JigsawPiece | " + px + "," + py + "," + pz + " | rot" + rotation + " | " + element + ">";
    }
}
