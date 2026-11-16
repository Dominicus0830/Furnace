// 바닐라 DismountHelper 와 AbstractHorse.getDismountLocationFromVehicle: 탈것 옆 자리에서 발 디딜 바닥을 위로 찾아, 승객 몸(자세별)이 블록에 끼지 않는 곳.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockInfo;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.Shape;

public final class DismountHelper {

    private DismountHelper() {}

    /** isBlockFloorValid: 바닥 높이가 무한이 아니고 1 보다 낮다. */
    public static boolean isBlockFloorValid(double h) { return !Double.isInfinite(h) && h < 1.0; }

    /**
     * canDismountTo(level, passenger, box): 그 상자에 블록 충돌(getBlockCollisions(passenger, …) — 엔티티는 보지 않는다)이 없고 월드 보더
     * 안이다(getWorldBorder().isWithinBounds(box)). 블록 문맥(비계·가루눈 등)은 승객(플레이어 포함)의 것 — 아직 탄 자리의 발 높이.
     */
    public static boolean canDismountTo(ChunkLevel level, Rider passenger, AABB box) {
        return level.noBlockCollision(passenger, box) && withinBorder(level, box);
    }

    /** WorldBorder.isWithinBounds(AABB): 두 모서리(최대는 1e-5 안쪽)가 모두 보더 안(최소 이상, 최대 미만). */
    public static boolean withinBorder(ChunkLevel level, AABB box) {
        double[] w = level.borderBounds();
        return inside(w, box.minX(), box.minZ()) && inside(w, box.maxX() - 1.0E-5F, box.maxZ() - 1.0E-5F);
    }

    private static boolean inside(double[] w, double x, double z) { return x >= w[0] && x < w[2] && z >= w[1] && z < w[3]; }

    /** DismountHelper.nonClimbableShape: 오를 수 있는 블록(사다리 등)·열린 다락문은 빈 모양, 나머지는 충돌 모양. */
    public static Shape nonClimbableShape(ChunkLevel level, Pos p) {
        int s = level.getBlockState(p);
        if (B.isTag(s, "minecraft:climbable")) return Shape.EMPTY;
        Prop open = B.prop("open");
        if (B.isTag(s, "minecraft:trapdoors") && open.has(s) && open.is(s)) return Shape.EMPTY;
        return BlockInfo.get().collisionShape(s);
    }

    /** getBlockFloorHeight(nonClimbableShape(p), nonClimbableShape(p.below())): 탈것 아래 사다리·열린 다락문은 바닥이 아니다. */
    public static double nonClimbableFloorHeight(ChunkLevel level, Pos p) {
        Shape s = nonClimbableShape(level, p);
        if (!s.isEmpty()) return s.bounds()[4];
        Shape below = nonClimbableShape(level, p.below());
        double d = below.isEmpty() ? Double.NEGATIVE_INFINITY : below.bounds()[4];
        return d >= 1.0 ? d - 1.0 : Double.NEGATIVE_INFINITY;
    }

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
                    if (canDismountTo(level, r, box)) return new Vec3(x, py, z);
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
        return bedStandUpPosition(e.level(), new Body(e.type().width(), e.type().height(), e.fireImmune(), false), pos, facing, yRot);
    }

    /** 플레이어(EntityType.PLAYER 0.6×1.8)가 침대에서 일어설 자리. 없으면 null. */
    public static Vec3 playerBedStandUpPosition(ChunkLevel level, Pos pos, int facing, float yRot) {
        return bedStandUpPosition(level, new Body(PLAYER_WIDTH, PLAYER_HEIGHT, false, true), pos, facing, yRot);
    }

    /** EntityType.PLAYER 의 몸 크기. */
    public static final float PLAYER_WIDTH = 0.6F, PLAYER_HEIGHT = 1.8F;

    /** 내려서는 몸의 종류: 크기, 불 면역, 플레이어 여부(EntityType 이 알려 주는 것). */
    private record Body(float w, float h, boolean fireImmune, boolean player) {}

    private static Vec3 bedStandUpPosition(ChunkLevel level, Body body, Pos pos, int facing, float yRot) {
        int cw = org.domi.furnace.server.block.Dir.clockWise(facing);
        int side = isFacingAngle(cw, yRot) ? org.domi.furnace.server.block.Dir.opposite(cw) : cw;
        if (org.domi.furnace.server.block.B.isTag(level.getBlockState(pos.below()), "minecraft:beds")) {
            int[][] around = surround(facing, side);
            Vec3 v = atOffset(level, body, pos, around, true);
            if (v != null) return v;
            v = atOffset(level, body, pos.below(), around, true);
            if (v != null) return v;
            int[][] above = aboveOffsets(facing);
            v = atOffset(level, body, pos, above, true);
            if (v != null) return v;
            v = atOffset(level, body, pos, around, false);
            if (v != null) return v;
            v = atOffset(level, body, pos.below(), around, false);
            return v != null ? v : atOffset(level, body, pos, above, false);
        }
        int[][] a = surround(facing, side), b = aboveOffsets(facing);
        int[][] offs = new int[a.length + b.length][];
        System.arraycopy(a, 0, offs, 0, a.length);
        System.arraycopy(b, 0, offs, a.length, b.length);
        Vec3 v = atOffset(level, body, pos, offs, true);
        return v != null ? v : atOffset(level, body, pos, offs, false);
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

    private static Vec3 atOffset(ChunkLevel level, Body body, Pos pos, int[][] offs, boolean checkDangerous) {
        for (int[] o : offs) {
            Vec3 v = findSafeDismountLocation(level, body.w(), body.h(), body.fireImmune(), body.player(), new Pos(pos.x() + o[0], pos.y(), pos.z() + o[1]), checkDangerous);
            if (v != null) return v;
        }
        return null;
    }

    /** EntityType.isBlockDangerous(state): 불에 면역이 아니면 타는 블록, 그리고 위더 장미·달콤한 열매 덤불·선인장·가루눈. */
    private static boolean isBlockDangerous(boolean fireImmune, int state) {
        return !fireImmune && org.domi.furnace.server.entity.ai.navigation.WalkNodeEvaluator.isBurningBlock(state) || org.domi.furnace.server.block.B.is(state, "minecraft:wither_rose")
                || org.domi.furnace.server.block.B.is(state, "minecraft:sweet_berry_bush") || org.domi.furnace.server.block.B.is(state, "minecraft:cactus")
                || org.domi.furnace.server.block.B.is(state, "minecraft:powder_snow");
    }

    /** DismountHelper.findSafeDismountLocation(type, level, pos, checkDangerous): 그 몹 종류의 몸으로(Furnace 의 Player 는 LivingEntity 가 아니므로 여기는 플레이어가 아니다). */
    public static Vec3 findSafeDismountLocation(LivingEntity e, Pos pos, boolean checkDangerous) {
        return findSafeDismountLocation(e.level(), e.type().width(), e.type().height(), e.fireImmune(), false, pos, checkDangerous);
    }

    /** 몸 크기 w×h 의 플레이어(불에 면역 아님)로 findSafeDismountLocation. */
    public static Vec3 findSafeDismountLocation(ChunkLevel level, float w, float hgt, Pos pos, boolean checkDangerous) {
        return findSafeDismountLocation(level, w, hgt, false, true, pos, checkDangerous);
    }

    /**
     * DismountHelper.findSafeDismountLocation(type, level, pos, checkDangerous): 위험한 블록(그 자리·아래)을 거르고, 사다리·열린 다락문을 뺀
     * 바닥 높이로 서 있을 곳을 정한 뒤, 몸 상자에 블록 충돌이 없고 월드 보더 안(상자 전체)이어야 한다. 플레이어는 invalid_spawn_inside 블록
     * (엔드 포털·관문) 안/바로 위 칸이면 안 된다.
     */
    public static Vec3 findSafeDismountLocation(ChunkLevel level, float w, float hgt, boolean fireImmune, boolean player, Pos pos, boolean checkDangerous) {
        if (checkDangerous && isBlockDangerous(fireImmune, level.getBlockState(pos))) return null;
        double h = nonClimbableFloorHeight(level, pos);
        if (!isBlockFloorValid(h)) return null;
        if (checkDangerous && h <= 0.0 && isBlockDangerous(fireImmune, level.getBlockState(pos.below()))) return null;
        Vec3 v = new Vec3(pos.x() + 0.5, pos.y() + h, pos.z() + 0.5);
        double hw = w / 2.0F;
        AABB box = new AABB(v.x() - hw, v.y(), v.z() - hw, v.x() + hw, v.y() + hgt, v.z() + hw);
        if (!level.noBlockCollision(null, box)) return null;   // getBlockCollisions(null, box): 블록만(배·셜커 몸은 보지 않는다)
        if (player && (B.isTag(level.getBlockState(pos), "minecraft:invalid_spawn_inside") || B.isTag(level.getBlockState(pos.above()), "minecraft:invalid_spawn_inside"))) return null;
        return withinBorder(level, box) ? v : null;
    }
}
