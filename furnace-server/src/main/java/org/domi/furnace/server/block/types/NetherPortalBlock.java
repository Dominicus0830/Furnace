// 바닐라 NetherPortalBlock: 틀이 깨지거나 같은 축 이웃이 지옥문이 아니게 되면 무너진다. 무작위 틱으로 좀비 피글린을 낳는다.
// 안에 든 엔티티를 오버월드 ↔ 네더로 보낸다(좌표 비 = 원천/목적 coordinateScale, 목적에서 가까운 지옥문을 찾거나 만든다).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.BlockUtil;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.DimensionTransition;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.spawn.SpawnPlacements;
import org.domi.furnace.server.entity.spawn.SpawnReason;
import org.domi.furnace.server.entity.Portal;
import org.domi.furnace.server.entity.PortalTravel;
import org.domi.furnace.server.entity.Traveler;
import org.domi.furnace.server.world.DimensionType;

public class NetherPortalBlock extends BlockBehavior implements Portal {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(NetherPortalBlock.class);
    private static final Prop AXIS = B.prop("axis");
    public static final String OVERWORLD = "minecraft:overworld", NETHER = "minecraft:the_nether";

    public NetherPortalBlock(int block) { super(block); }

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if ("minecraft:overworld".equals(level.dimension()) && level.gameRule("doMobSpawning")
                && random.nextInt(2000) < level.difficulty()) {
            // 바닐라: 지옥문 맨 아래 칸(지옥문이 아닌 첫 칸)이 스폰 가능한 바닥이면 그 위에 STRUCTURE 로 좀비 피글린을 낳고 포털 쿨다운을 건다.
            Pos p = pos;
            while (B.is(level.getBlockState(p), block)) p = p.below();
            if (level instanceof ChunkLevel cl
                    && SpawnPlacements.isValidSpawn(level.getBlockState(p), cl, p, "minecraft:zombified_piglin")) {
                Mob mob = cl.spawnMob("minecraft:zombified_piglin", p.above(), false, false, SpawnReason.STRUCTURE);
                if (mob != null) mob.setPortalCooldown();
            }
        }
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        int dirAxis = Dir.axis(dir);
        int axis = axis(state);
        boolean otherHorizontal = axis != dirAxis && dirAxis != Dir.AXIS_Y;
        return !otherHorizontal && !B.is(neighborState, block) && !new PortalShape(level, pos, axis).isComplete()
                ? B.defaultState("air") : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    private static int axis(int state) { return "x".equals(AXIS.valueName(state)) ? Dir.AXIS_X : Dir.AXIS_Z; }

    private boolean isPortal(int state) { return B.is(state, block); }

    /** entityInside: 포털을 쓸 수 있으면 안에 있다고 표시(플레이어는 Region 이 따로 본다). */
    @Override
    public void entityInside(int state, BlockLevel level, Pos pos, Entity entity) {
        if (entity.canUsePortal(false)) entity.setAsInsidePortal(this, pos);
    }

    /** getPortalTransitionTime: 플레이어만 기다린다 — 엔티티는 0. */
    @Override
    public int getPortalTransitionTime(ChunkLevel level, Entity entity) { return 0; }

    /** 플레이어: max(1, 무적이면 playersNetherPortalCreativeDelay, 아니면 playersNetherPortalDefaultDelay). */
    @Override
    public int getPlayerTransitionTime(ChunkLevel level, boolean invulnerable) {
        return Math.max(1, level.gameRuleInt(invulnerable ? "playersNetherPortalCreativeDelay" : "playersNetherPortalDefaultDelay"));
    }

    /**
     * getPortalDestination 의 원천 쪽: 목적 차원(네더면 오버월드, 아니면 네더), 좌표 비를 적용해 보더 안으로 자른 출구 좌표, 원천 포털
     * 사각형 안의 상대 위치. 목적 쪽(complete)에서 가까운 지옥문을 찾고, 없으면 원천 축으로 만든다.
     */
    @Override
    public PortalTravel prepareTravel(ChunkLevel level, Traveler t, Pos pos) {
        String key = NETHER.equals(level.dimension()) ? OVERWORLD : NETHER;
        boolean toNether = NETHER.equals(key);
        DimensionType to = DimensionType.byLevelKey(key);
        if (to == null) return null;
        double scale = DimensionType.teleportationScale(level.dimensionType(), to);
        Pos exit = level.clampToBorder(t.x() * scale, t.y(), t.z() * scale);
        // 원천 포털: getDimensionTransitionFromExit 의 entity.level().getBlockState(entryPos)
        int entry = level.getBlockState(pos);
        final int srcAxis;
        final double[] rel;
        if (isPortal(entry)) {
            srcAxis = axis(entry);
            BlockUtil.FoundRectangle r = BlockUtil.getLargestRectangleAround(pos, srcAxis, 21, Dir.AXIS_Y, 21, p -> level.getBlockState(p) == entry);
            rel = PortalShape.getRelativePosition(r, srcAxis, t.x(), t.y(), t.z(), t.width(), t.height());
            // Entity.getRelativePortalPosition 재정의: 산 것·수레·배는 깊이 0
            if (t.resetPortalDepth()) rel[2] = 0.0;
        } else {
            srcAxis = Dir.AXIS_X;
            rel = new double[] {0.5, 0.0, 0.0};
        }
        int createAxis = isPortal(entry) ? axis(entry) : Dir.AXIS_X;
        int search = PortalForcer.searchRadius(toNether) + 21;   // 찾은 지옥문의 사각형(최대 21)까지
        return new PortalTravel(key, exit.x(), exit.z(), search, PortalForcer.createRadius(), target -> {
            Pos found = PortalForcer.findClosestPortalPosition(target, exit, toNether);
            BlockUtil.FoundRectangle rect;
            if (found != null) {
                int st = target.getBlockState(found);
                rect = BlockUtil.getLargestRectangleAround(found, axis(st), 21, Dir.AXIS_Y, 21, p -> target.getBlockState(p) == st);
            } else {
                rect = PortalForcer.createPortal(target, exit, createAxis);
                if (rect == null) {
                    LOG.error("Unable to create a portal, likely target out of worldborder");
                    return null;
                }
            }
            return transition(target, rect, srcAxis, rel, t, found);
        });
    }

    /** createDimensionTransition: 출구 사각형 안의 같은 상대 위치, 축이 다르면 90도 돌리고 속도도 돌린다, 충돌 없는 자리로 민다. */
    private DimensionTransition transition(ChunkLevel target, BlockUtil.FoundRectangle rect, int srcAxis, double[] rel, Traveler t, Pos found) {
        Pos min = rect.minCorner();
        int exitState = target.getBlockState(min);
        int exitAxis = isPortal(exitState) ? axis(exitState) : Dir.AXIS_X;
        double w = rect.axis1Size(), h = rect.axis2Size();
        int rot = srcAxis == exitAxis ? 0 : 90;
        double vx = srcAxis == exitAxis ? t.vx() : t.vz();
        double vz = srcAxis == exitAxis ? t.vz() : -t.vx();
        double d2 = t.width() / 2.0 + (w - t.width()) * rel[0];
        double d3 = (h - t.height()) * rel[1];
        double d4 = 0.5 + rel[2];
        boolean xAxis = exitAxis == Dir.AXIS_X;
        double px = min.x() + (xAxis ? d2 : d4), py = min.y() + d3, pz = min.z() + (xAxis ? d4 : d2);
        double[] p = PortalShape.findCollisionFreePosition(target, px, py, pz, t.width(), t.height());
        // 찾았으면 PLAY_PORTAL_SOUND.then(placePortalTicket(found)), 만들었으면 .then(PLACE_PORTAL_TICKET) — 도착 칸
        Pos ticket = found != null ? found : new Pos(Entity.floor(p[0]), Entity.floor(p[1]), Entity.floor(p[2]));
        return new DimensionTransition(target, p[0], p[1], p[2], vx, t.vy(), vz, t.yRot() + rot, t.xRot(), true, ticket, false);
    }
}
