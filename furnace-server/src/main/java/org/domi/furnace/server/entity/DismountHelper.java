// 바닐라 DismountHelper 와 AbstractHorse.getDismountLocationFromVehicle: 탈것 옆 자리에서 발 디딜 바닥을 위로 찾아, 승객 몸(자세별)이 블록에 끼지 않는 곳.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.BlockInfo;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Shape;

public final class DismountHelper {

    private DismountHelper() {}

    /** isBlockFloorValid: 바닥 높이가 무한이 아니고 1 보다 낮다. */
    public static boolean isBlockFloorValid(double h) { return !Double.isInfinite(h) && h < 1.0; }

    /** canDismountTo: 그 상자에 블록 충돌이 없다. TODO(월드 보더): getWorldBorder().isWithinBounds(box). */
    public static boolean canDismountTo(ChunkLevel level, AABB box) { return level.noCollision(box); }

    /** BlockGetter.getBlockFloorHeight(pos): 칸의 충돌 모양 꼭대기, 비었으면 아래 칸 꼭대기 − 1(1 미만이면 −무한). */
    public static double blockFloorHeight(ChunkLevel level, Pos p) {
        Shape s = BlockInfo.get().collisionShape(level.getBlockState(p));
        if (!s.isEmpty()) return s.bounds()[4];
        Shape below = BlockInfo.get().collisionShape(level.getBlockState(p.below()));
        double d = below.isEmpty() ? Double.NEGATIVE_INFINITY : below.bounds()[4];
        return d >= 1.0 ? d - 1.0 : Double.NEGATIVE_INFINITY;
    }

    /** 승객의 내릴 자세들(getDismountPoses)과 자세별 몸(getLocalBoundsForPose): 플레이어는 서기·웅크리기·헤엄, 나머지는 지금 몸. */
    static float[][] dismountBounds(Rider r) {
        if (r instanceof Player) return new float[][] {{0.6F, 1.8F}, {0.6F, 1.5F}, {0.6F, 0.6F}};
        Entity e = (Entity) r;
        return new float[][] {{e.bbWidth(), e.bbHeight()}};
    }

    /** AbstractHorse.getDismountLocationFromVehicle(offset, passenger): 맞는 자리가 없으면 null. TODO(자세): 고른 자세로 승객 자세 바꾸기. */
    public static Vec3 findDismountLocation(Entity vehicle, Vec3 offset, Rider r) {
        ChunkLevel level = vehicle.level();
        double x = vehicle.x() + offset.x(), minY = vehicle.boundingBox().minY(), z = vehicle.z() + offset.z();
        int bx = Entity.floor(x), bz = Entity.floor(z);
        for (float[] dim : dismountBounds(r)) {
            int by = Entity.floor(minY);
            double maxY = vehicle.boundingBox().maxY() + 0.75;
            do {
                double h = blockFloorHeight(level, new Pos(bx, by, bz));
                if (by + h > maxY) break;
                if (isBlockFloorValid(h)) {
                    double py = by + h, hw = dim[0] / 2.0F;
                    AABB box = new AABB(x - hw, py, z - hw, x + hw, py + dim[1], z + hw);
                    if (canDismountTo(level, box)) return new Vec3(x, py, z);
                }
                by++;
            } while (by < maxY);
        }
        return null;
    }

    // ------------------------------------------------------------------ 침대에서 일어서기(바닐라 BedBlock.findStandUpPosition)

    /** Direction.isFacingAngle(yRot). */
    private static boolean isFacingAngle(int dir, float yRot) {
        float f = yRot * (float) (Math.PI / 180.0);
        float fx = -org.domi.furnace.server.entity.ai.control.Mth.sin(f), fz = org.domi.furnace.server.entity.ai.control.Mth.cos(f);
        return org.domi.furnace.server.block.Dir.DX[dir] * fx + org.domi.furnace.server.block.Dir.DZ[dir] * fz > 0.0F;
    }

    /** BedBlock.findStandUpPosition(type, level, pos, facing, yRot). 자리가 없으면 null. */
    public static Vec3 bedStandUpPosition(LivingEntity e, Pos pos, int facing, float yRot) {
        return bedStandUpPosition(e.level(), e.type().width(), e.type().height(), pos, facing, yRot);
    }

    /** 플레이어(EntityType.PLAYER 0.6×1.8)가 침대에서 일어설 자리. 없으면 null. */
    public static Vec3 playerBedStandUpPosition(ChunkLevel level, Pos pos, int facing, float yRot) {
        return bedStandUpPosition(level, PLAYER_WIDTH, PLAYER_HEIGHT, pos, facing, yRot);
    }

    /** EntityType.PLAYER 의 몸 크기. */
    public static final float PLAYER_WIDTH = 0.6F, PLAYER_HEIGHT = 1.8F;

    private static Vec3 bedStandUpPosition(ChunkLevel level, float w, float h, Pos pos, int facing, float yRot) {
        int cw = org.domi.furnace.server.block.Dir.clockWise(facing);
        int side = isFacingAngle(cw, yRot) ? org.domi.furnace.server.block.Dir.opposite(cw) : cw;
        if (org.domi.furnace.server.block.B.isTag(level.getBlockState(pos.below()), "minecraft:beds")) {
            int[][] around = surround(facing, side);
            Vec3 v = atOffset(level, w, h, pos, around, true);
            if (v != null) return v;
            v = atOffset(level, w, h, pos.below(), around, true);
            if (v != null) return v;
            int[][] above = aboveOffsets(facing);
            v = atOffset(level, w, h, pos, above, true);
            if (v != null) return v;
            v = atOffset(level, w, h, pos, around, false);
            if (v != null) return v;
            v = atOffset(level, w, h, pos.below(), around, false);
            return v != null ? v : atOffset(level, w, h, pos, above, false);
        }
        int[][] a = surround(facing, side), b = aboveOffsets(facing);
        int[][] offs = new int[a.length + b.length][];
        System.arraycopy(a, 0, offs, 0, a.length);
        System.arraycopy(b, 0, offs, a.length, b.length);
        Vec3 v = atOffset(level, w, h, pos, offs, true);
        return v != null ? v : atOffset(level, w, h, pos, offs, false);
    }

    private static int[][] surround(int f, int s) {
        int fx = org.domi.furnace.server.block.Dir.DX[f], fz = org.domi.furnace.server.block.Dir.DZ[f];
        int sx = org.domi.furnace.server.block.Dir.DX[s], sz = org.domi.furnace.server.block.Dir.DZ[s];
        return new int[][] {{sx, sz}, {sx - fx, sz - fz}, {sx - fx * 2, sz - fz * 2}, {-fx * 2, -fz * 2}, {-sx - fx * 2, -sz - fz * 2},
                {-sx - fx, -sz - fz}, {-sx, -sz}, {-sx + fx, -sz + fz}, {fx, fz}, {sx + fx, sz + fz}};
    }

    private static int[][] aboveOffsets(int f) {
        return new int[][] {{0, 0}, {-org.domi.furnace.server.block.Dir.DX[f], -org.domi.furnace.server.block.Dir.DZ[f]}};
    }

    private static Vec3 atOffset(ChunkLevel level, float w, float h, Pos pos, int[][] offs, boolean checkDangerous) {
        for (int[] o : offs) {
            Vec3 v = findSafeDismountLocation(level, w, h, new Pos(pos.x() + o[0], pos.y(), pos.z() + o[1]), checkDangerous);
            if (v != null) return v;
        }
        return null;
    }

    /** EntityType.isBlockDangerous(불에 면역이 아닌 몹): 타는 블록·위더 장미·달콤한 열매 덤불·선인장·가루눈. */
    private static boolean isBlockDangerous(int state) {
        return org.domi.furnace.server.entity.ai.navigation.WalkNodeEvaluator.isBurningBlock(state) || org.domi.furnace.server.block.B.is(state, "minecraft:wither_rose")
                || org.domi.furnace.server.block.B.is(state, "minecraft:sweet_berry_bush") || org.domi.furnace.server.block.B.is(state, "minecraft:cactus")
                || org.domi.furnace.server.block.B.is(state, "minecraft:powder_snow");
    }

    /** DismountHelper.findSafeDismountLocation(type, level, pos, checkDangerous). TODO(월드 보더): 상자 전체 대신 칸 하나로 검사한다. */
    public static Vec3 findSafeDismountLocation(LivingEntity e, Pos pos, boolean checkDangerous) {
        return findSafeDismountLocation(e.level(), e.type().width(), e.type().height(), pos, checkDangerous);
    }

    /** 몸 크기 w×h(불 면역 아님)로 findSafeDismountLocation. */
    public static Vec3 findSafeDismountLocation(ChunkLevel level, float w, float hgt, Pos pos, boolean checkDangerous) {
        if (checkDangerous && isBlockDangerous(level.getBlockState(pos))) return null;
        double h = blockFloorHeight(level, pos);
        if (!isBlockFloorValid(h)) return null;
        if (checkDangerous && h <= 0.0 && isBlockDangerous(level.getBlockState(pos.below()))) return null;
        Vec3 v = new Vec3(pos.x() + 0.5, pos.y() + h, pos.z() + 0.5);
        double hw = w / 2.0F;
        AABB box = new AABB(v.x() - hw, v.y(), v.z() - hw, v.x() + hw, v.y() + hgt, v.z() + hw);
        if (!level.noCollision(box)) return null;
        return level.withinBorder(pos) ? v : null;
    }
}
