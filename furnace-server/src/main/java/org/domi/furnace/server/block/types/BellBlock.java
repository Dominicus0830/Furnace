// 바닐라 BellBlock: 종. 바닥·천장·한쪽 벽·양쪽 벽에 붙는다. 알맞은 면을 치거나 신호가 새로 들어오면 울린다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.Explosion;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class BellBlock extends BaseEntityBlock {

    private static final Prop FACING = B.prop("facing"), ATTACHMENT = B.prop("attachment"), POWERED = B.prop("powered");

    public BellBlock(int block) { super(block); }

    private static int facing(int state) { return Dir.byName(FACING.valueName(state)); }

    @Override
    public void neighborChanged(int state, BlockLevel level, Pos pos, int sourceBlock, Pos sourcePos, boolean moved) {
        boolean signal = level.hasNeighborSignal(pos);
        if (signal != POWERED.is(state)) {
            if (signal) attemptToRing(null, level, pos, -1);
            level.setBlock(pos, POWERED.with(state, signal), 3);
        }
    }

    // TODO(엔티티): onProjectileHit — 투사체가 맞힌 면으로 울린다.

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        return onHit(level, state, hit, player, true) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    /** onHit: 알맞은 면이면 울리고 통계 bell_ring. */
    public boolean onHit(BlockLevel level, int state, Hit hit, BlockActor player, boolean checkHit) {
        int dir = hit.face();
        Pos pos = hit.pos();
        if (!checkHit || isProperHit(state, dir, hit.y() - pos.y())) {
            attemptToRing(player, level, pos, dir);
            player.awardStat("minecraft:bell_ring");
            return true;
        }
        return false;
    }

    /** isProperHit: 옆면 아래쪽(0.8124 이하)을, 붙은 방식에 맞는 축으로 쳤는가. */
    private static boolean isProperHit(int state, int dir, double y) {
        if (Dir.axis(dir) == Dir.AXIS_Y || y > 0.8124F) return false;
        int f = facing(state);
        return switch (ATTACHMENT.valueName(state)) {
            case "floor" -> Dir.axis(f) == Dir.axis(dir);
            case "single_wall", "double_wall" -> Dir.axis(f) != Dir.axis(dir);
            case "ceiling" -> true;
            default -> false;
        };
    }

    /** attemptToRing: 블록 엔티티 흔들림(블록 이벤트 1, 방향) + 소리 + BLOCK_CHANGE. dir 이 -1 이면 종이 보는 방향. */
    public boolean attemptToRing(BlockActor source, BlockLevel level, Pos pos, int dir) {
        int state = level.getBlockState(pos);
        if (!B.is(state, block)) return false;   // 바닐라: BellBlockEntity 가 있을 때
        if (dir < 0) dir = facing(state);
        level.blockEvent(pos, block, 1, dir);
        level.playSound(null, pos, "minecraft:block.bell.use", "blocks", 2.0F, 1.0F);
        level.gameEvent("minecraft:block_change", pos, source, -1);
        return true;
    }

    /**
     * BellBlockEntity.triggerEvent(1) → updateEntities: 48칸 상자 안 살아 있는 몹 중 칸 가운데에서 32 안이면 HEARD_BELL_TIME = 지금.
     * 차이: 바닐라는 목록을 60틱 동안 캐시한다(그사이 들어온 몹은 못 듣는다). 여기서는 울릴 때마다 다시 모은다.
     */
    @Override
    public boolean triggerEvent(int state, BlockLevel level, Pos pos, int type, int data) {
        if (type != 1) return false;
        if (level instanceof org.domi.furnace.server.block.ChunkLevel cl) {
            var box = new org.domi.furnace.server.entity.AABB(pos.x(), pos.y(), pos.z(), pos.x() + 1, pos.y() + 1, pos.z() + 1).inflate(48.0, 48.0, 48.0);
            long now = cl.gameTime();
            for (var m : cl.getEntitiesOfClass(org.domi.furnace.server.entity.Mob.class, box, e -> true)) {
                double dx = pos.x() + 0.5 - m.x(), dy = pos.y() + 0.5 - m.y(), dz = pos.z() + 0.5 - m.z();
                if (m.isAlive() && !m.isRemoved() && dx * dx + dy * dy + dz * dz < 32.0 * 32.0 && m.getBrain() != null)
                    m.getBrain().setMemory(org.domi.furnace.server.entity.ai.brain.MemoryModuleType.HEARD_BELL_TIME, now);
            }
        }
        return true;
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int face = ctx.clickedFace();
        Pos pos = ctx.clickedPos();
        BlockLevel level = ctx.level();
        int axis = Dir.axis(face);
        if (axis == Dir.AXIS_Y) {
            int s = ATTACHMENT.with(defaultState(), face == Dir.DOWN ? "ceiling" : "floor");
            s = FACING.with(s, Dir.NAME[ctx.horizontalDirection()]);
            if (B.canSurvive(s, level, pos)) return s;
        } else {
            boolean two = axis == Dir.AXIS_X && sturdy(level, pos.west(), Dir.EAST) && sturdy(level, pos.east(), Dir.WEST)
                    || axis == Dir.AXIS_Z && sturdy(level, pos.north(), Dir.SOUTH) && sturdy(level, pos.south(), Dir.NORTH);
            int s = ATTACHMENT.with(FACING.with(defaultState(), Dir.NAME[Dir.opposite(face)]), two ? "double_wall" : "single_wall");
            if (B.canSurvive(s, level, pos)) return s;
            boolean floor = sturdy(level, pos.below(), Dir.UP);
            s = ATTACHMENT.with(s, floor ? "floor" : "ceiling");
            if (B.canSurvive(s, level, pos)) return s;
        }
        return -1;
    }

    private static boolean sturdy(BlockLevel level, Pos p, int dir) { return B.isFaceSturdy(level.getBlockState(p), level, p, dir); }

    @Override
    public void onExplosionHit(int state, BlockLevel level, Pos pos, Explosion explosion) {
        if (explosion.canTriggerBlocks()) attemptToRing(null, level, pos, -1);
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        String att = ATTACHMENT.valueName(state);
        int connected = Dir.opposite(connectedDirection(state));
        if (connected == dir && !canSurvive(state, level, pos) && !"double_wall".equals(att)) return B.defaultState("air");
        if (Dir.axis(dir) == Dir.axis(facing(state))) {
            if ("double_wall".equals(att) && !B.isFaceSturdy(neighborState, level, neighborPos, dir)) {
                return FACING.with(ATTACHMENT.with(state, "single_wall"), Dir.NAME[Dir.opposite(dir)]);
            }
            if ("single_wall".equals(att) && Dir.opposite(connected) == dir && B.isFaceSturdy(neighborState, level, neighborPos, facing(state))) {
                return ATTACHMENT.with(state, "double_wall");
            }
        }
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        int d = Dir.opposite(connectedDirection(state));
        return d == Dir.UP ? B.canSupportCenter(level, pos.above(), Dir.DOWN) : FaceAttachedHorizontalDirectionalBlock.canAttach(level, pos, d);
    }

    /** getConnectedDirection: 바닥 위, 천장 아래, 벽은 facing 반대. */
    private static int connectedDirection(int state) {
        return switch (ATTACHMENT.valueName(state)) {
            case "floor" -> Dir.UP;
            case "ceiling" -> Dir.DOWN;
            default -> Dir.opposite(facing(state));
        };
    }
}
