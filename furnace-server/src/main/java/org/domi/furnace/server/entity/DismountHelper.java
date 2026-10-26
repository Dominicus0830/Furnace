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
    private static float[][] dismountBounds(Rider r) {
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
}
