// 바닐라 EndPortalBlock: 엔드 차원문. 안에 들어온 엔티티를 엔드(또는 엔드에서 오버월드)로 보낸다.
// 엔드로 가면 (100, 50, 0) 아래에 흑요석 단상을 만들고 서쪽을 보게 하며, 오버월드로 가면 월드 스폰 위 첫 빈 칸으로 간다.
// TODO(차원 호스팅): 플레이어 이동(엔드의 크레딧·리스폰 위치 귀환)은 서버가 다른 차원 World 를 띄워야 연결된다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.DimensionTransition;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Portal;
import org.domi.furnace.server.world.gen.feature.EndPlatformFeature;

public class EndPortalBlock extends BaseEntityBlock implements Portal {

    public static final String OVERWORLD = "minecraft:overworld", THE_END = "minecraft:the_end";
    /** ServerLevel.END_SPAWN_POINT. */
    public static final Pos END_SPAWN_POINT = new Pos(100, 50, 0);
    /** Direction.WEST.toYRot(). */
    private static final float WEST_YROT = 90.0F;

    public EndPortalBlock(int block) { super(block); }

    /** 모양 box(0, 6, 0, 16, 12, 16) 과 몸통이 겹치면(Shapes.joinIsNotEmpty) 포털 안이다. */
    public static boolean insideShape(AABB box, Pos pos) {
        double x0 = pos.x(), y0 = pos.y() + 0.375, z0 = pos.z(), x1 = pos.x() + 1.0, y1 = pos.y() + 0.75, z1 = pos.z() + 1.0;
        return box.maxX() - x0 > 1.0E-7 && x1 - box.minX() > 1.0E-7 && box.maxY() - y0 > 1.0E-7 && y1 - box.minY() > 1.0E-7
                && box.maxZ() - z0 > 1.0E-7 && z1 - box.minZ() > 1.0E-7;
    }

    @Override
    public void entityInside(int state, BlockLevel level, Pos pos, Entity entity) {
        if (entity.canUsePortal(false) && insideShape(entity.boundingBox(), pos)) entity.setAsInsidePortal(this, pos);
    }

    /** 이 레벨에서 포털이 보내는 차원(엔드면 오버월드, 그 밖은 엔드). */
    public static String targetDimension(String from) { return THE_END.equals(from) ? OVERWORLD : THE_END; }

    @Override
    public DimensionTransition getPortalDestination(ChunkLevel level, Entity entity, Pos pos) {
        String key = targetDimension(level.dimension());
        ChunkLevel target = level.levelFor(key);
        if (target == null) return null;
        boolean toEnd = THE_END.equals(key);
        float yRot = entity.yRot();
        double x, y, z;
        if (toEnd) {
            x = END_SPAWN_POINT.x() + 0.5;
            y = END_SPAWN_POINT.y();
            z = END_SPAWN_POINT.z() + 0.5;
            EndPlatformFeature.createEndPlatform(target, new Pos(END_SPAWN_POINT.x(), END_SPAWN_POINT.y() - 1, END_SPAWN_POINT.z()), true);
            yRot = WEST_YROT;
        } else {
            // Entity.adjustSpawnLocation: 공유 스폰 칸의 MOTION_BLOCKING_NO_LEAVES 높이 위, 바닥 가운데
            int[] spawn = target.sharedSpawnPos();
            x = spawn[0] + 0.5;
            y = target.getMotionBlockingNoLeavesHeight(spawn[0], spawn[2]);
            z = spawn[2] + 0.5;
        }
        return new DimensionTransition(target, x, y, z, entity.vx(), entity.vy(), entity.vz(), yRot, entity.xRot());
    }

    /** 플레이어가 엔드로 갈 때의 도착 위치: 단상을 만들고 END_SPAWN_POINT 한 칸 아래(바닐라는 플레이어만 1 내린다), 서쪽. */
    public static double[] playerEndArrival(ChunkLevel end) {
        EndPlatformFeature.createEndPlatform(end, new Pos(END_SPAWN_POINT.x(), END_SPAWN_POINT.y() - 1, END_SPAWN_POINT.z()), true);
        return new double[] {END_SPAWN_POINT.x() + 0.5, END_SPAWN_POINT.y() - 1.0, END_SPAWN_POINT.z() + 0.5, WEST_YROT};
    }
}
