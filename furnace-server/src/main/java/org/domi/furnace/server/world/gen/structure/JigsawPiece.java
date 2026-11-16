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
    public String pieceType() { return "jigsaw"; }

    /** 바닐라 PoolElementStructurePiece 는 방향을 정하지 않는다(O = -1). */
    @Override
    protected Direction savedOrientation() { return null; }

    /** 바닐라 PoolElementStructurePiece.addAdditionalSaveData. */
    @Override
    protected void saveAdditional(org.domi.furnace.server.net.io.nbt.NbtCompound t) {
        t.putInt("PosX", px);
        t.putInt("PosY", py);
        t.putInt("PosZ", pz);
        t.putInt("ground_level_delta", groundLevelDelta);
        t.put("pool_element", element.encode(element.projection()));
        t.putString("rotation", TemplatePiece.rotationName(rotation));
        var list = new org.domi.furnace.server.net.io.nbt.NbtList(org.domi.furnace.server.net.io.nbt.NbtIo.TAG_COMPOUND);
        for (Junction j : junctions) {
            org.domi.furnace.server.net.io.nbt.NbtCompound c = new org.domi.furnace.server.net.io.nbt.NbtCompound();
            c.putInt("source_x", j.sourceX());
            c.putInt("source_ground_y", j.sourceGroundY());
            c.putInt("source_z", j.sourceZ());
            c.putInt("delta_y", j.deltaY());
            c.putString("dest_proj", j.destRigid() ? "rigid" : "terrain_matching");
            list.add(c);
        }
        t.putList("junctions", list);
        // 기본값(apply_waterlogging)이면 쓰지 않는다
        if (!keepLiquids) t.putString("liquid_settings", "ignore_waterlogging");
    }

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

    /** 바닐라 PoolElementStructurePiece.place(level, …, random, box, pivot, keepJigsaws) — 피벗·상자는 c 의 것. */
    public void place(PostCtx c, boolean keepJigsaws) {
        element.place(c, px, py, pz, rotation, c.writableBox, keepLiquids, keepJigsaws);
    }

    @Override
    public String toString() {
        return "<JigsawPiece | " + px + "," + py + "," + pz + " | rot" + rotation + " | " + element + ">";
    }
}
