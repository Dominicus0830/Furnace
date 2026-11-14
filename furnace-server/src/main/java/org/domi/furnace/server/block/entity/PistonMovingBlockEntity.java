// 바닐라 PistonMovingBlockEntity: 움직이는 피스톤 칸이 옮기는 블록(movedState)·방향·늘이는 중인가·피스톤 머리인가·진행도를 든다.
// 틱마다 진행도 +0.5, 1 이 된 다음 틱에 옮기던 블록을 이웃 모양에 맞춰 놓는다(finalTick 은 그 자리에서 바로).
package org.domi.furnace.server.block.entity;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.Shape;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.types.PistonBaseBlock;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class PistonMovingBlockEntity extends BlockEntity {

    private static final int MOVING_PISTON = B.id("moving_piston"), PISTON_HEAD = B.id("piston_head"), STICKY_PISTON = B.id("sticky_piston");
    private static final int SLIME_BLOCK = B.id("slime_block"), HONEY_BLOCK = B.id("honey_block");
    /** 바닐라 NOCLIP: 이 스레드에서 지금 미는 방향. 그 방향으로 움직이는 칸의 충돌 모양은 받침만 남긴다(region 스레드마다 따로). */
    private static final ThreadLocal<Integer> NOCLIP = new ThreadLocal<>();
    private static final Prop EXTENDED = B.prop("extended"), FACING = B.prop("facing"), TYPE = B.prop("type"), SHORT = B.prop("short"),
            WATERLOGGED = B.prop("waterlogged");

    private int movedState = B.defaultState("minecraft:air");
    private int direction = Dir.NORTH;
    private boolean extending;
    private boolean isSourcePiston;
    private float progress;
    private float progressO;
    private long lastTicked;
    private int deathTicks;

    public PistonMovingBlockEntity(Pos pos, int state) { super("minecraft:piston", pos, state); }

    public PistonMovingBlockEntity(Pos pos, int state, int movedState, int direction, boolean extending, boolean isSourcePiston) {
        this(pos, state);
        this.movedState = movedState;
        this.direction = direction;
        this.extending = extending;
        this.isSourcePiston = isSourcePiston;
    }

    public boolean isExtending() { return extending; }

    public int getDirection() { return direction; }

    public boolean isSourcePiston() { return isSourcePiston; }

    public int getMovedState() { return movedState; }

    public long getLastTicked() { return lastTicked; }

    /** getProgress(partialTick): progressO 와 progress 사이. */
    public float getProgress(float partial) {
        if (partial > 1.0F) partial = 1.0F;
        return progressO + partial * (progress - progressO);
    }

    private float getExtendedProgress(float p) { return extending ? p - 1.0F : 1.0F - p; }

    /** getMovementDirection: 늘이면 방향 그대로, 줄이면 반대. */
    public int getMovementDirection() { return extending ? direction : Dir.opposite(direction); }

    @Override
    public boolean ticking() { return true; }

    @Override
    public boolean isValid(int state) { return B.block(state) == MOVING_PISTON; }

    /** finalTick: 아직 다 움직이지 않았으면 바로 끝낸다 — 옮기던 블록(피스톤 머리면 공기)을 놓고 그 칸에 이웃 변화. */
    public void finalTick(BlockLevel level) {
        if (progressO < 1.0F) {
            progress = 1.0F;
            progressO = progress;
            level.removeBlockEntity(pos);
            setRemoved();
            if (B.block(level.getBlockState(pos)) == MOVING_PISTON) {
                int s = isSourcePiston ? B.defaultState("minecraft:air") : B.updateFromNeighbourShapes(movedState, level, pos);
                level.setBlock(pos, s, 3);
                level.neighborChanged(pos, B.block(s), pos);
            }
        }
    }

    /** tick(level, pos, state, entity). 진행 중이면 새 진행도로 닿은 엔티티를 밀고(moveCollidedEntities) 꿀 블록 위 엔티티를 끈 다음 진행도를 올린다. */
    @Override
    public void tick(BlockLevel level) {
        lastTicked = level.gameTime();
        progressO = progress;
        if (progressO >= 1.0F) {
            level.removeBlockEntity(pos);
            setRemoved();
            if (B.block(level.getBlockState(pos)) == MOVING_PISTON) {
                int s = B.updateFromNeighbourShapes(movedState, level, pos);
                if (B.isAir(s)) {
                    level.setBlock(pos, movedState, 84);
                    B.updateOrDestroy(movedState, s, level, pos, 3, BlockLevel.UPDATE_LIMIT);
                } else {
                    if (WATERLOGGED.has(s) && WATERLOGGED.is(s)) s = WATERLOGGED.with(s, false);
                    level.setBlock(pos, s, 67);
                    level.neighborChanged(pos, B.block(s), pos);
                }
            }
        } else {
            float f = progress + 0.5F;
            moveCollidedEntities(level, pos, f, this);
            moveStuckEntities(level, pos, f, this);
            progress = f;
            if (progress >= 1.0F) progress = 1.0F;
        }
    }

    /**
     * getCollisionShape(level, pos): 줄어드는 피스톤 받침 모양 + 옮기는 블록(또는 머리)의 모양을 진행도만큼 옮긴 것.
     * 이 칸이 지금 미는 방향(NOCLIP)으로 움직이는 중이면 받침만(밀리는 엔티티가 미는 블록에 걸리지 않게).
     */
    public Shape getCollisionShape(BlockLevel level, Pos at) {
        Shape base = !extending && isSourcePiston && B.behavior(movedState) instanceof PistonBaseBlock
                ? collision(EXTENDED.with(movedState, true), level, at) : Shape.EMPTY;
        Integer noclip = NOCLIP.get();
        if (progress < 1.0 && noclip != null && noclip == getMovementDirection()) return base;
        int s;
        if (isSourcePiston) {
            s = SHORT.with(FACING.with(B.defaultState(PISTON_HEAD), Dir.NAME[direction]), extending != 1.0F - progress < 0.25F);
        } else {
            s = movedState;
        }
        float f = getExtendedProgress(progress);
        return Shape.or(base, collision(s, level, at).move(Dir.DX[direction] * f, Dir.DY[direction] * f, Dir.DZ[direction] * f));
    }

    // ------------------------------------------------------------------ 엔티티 밀기(바닐라 moveCollidedEntities·moveStuckEntities)
    // 엔티티는 이 region 레벨(ChunkLevel)에 든 것만 본다 — 블록 엔티티 틱이 도는 region 스레드가 그 엔티티의 주인이다.
    // 플레이어는 레벨 엔티티가 아니고 위치를 클라이언트가 정한다: 클라이언트가 같은 블록 엔티티로 스스로 밀린다(서버는 밀지 않는다).

    /** getCollisionRelatedBlockState: 줄어드는 피스톤 자체면 머리(진행 0.25 넘으면 short), 아니면 옮기는 블록. */
    private int collisionRelatedBlockState() {
        if (!extending && isSourcePiston && B.behavior(movedState) instanceof PistonBaseBlock) {
            int s = B.defaultState(PISTON_HEAD);
            s = SHORT.with(s, progress > 0.25F);
            s = TYPE.with(s, B.is(movedState, STICKY_PISTON) ? "sticky" : "normal");
            return FACING.with(s, FACING.valueName(movedState));
        }
        return movedState;
    }

    private static void moveCollidedEntities(BlockLevel level, Pos pos, float f, PistonMovingBlockEntity be) {
        if (!(level instanceof ChunkLevel cl)) return;
        int dir = be.getMovementDirection();
        double d = f - be.progress;
        Shape shape = collision(be.collisionRelatedBlockState(), level, pos);
        if (shape.isEmpty()) return;
        AABB bounds = moveByPositionAndProgress(pos, toAabb(shape.bounds()), be);
        AABB area = movementArea(bounds, dir, d);
        AABB query = new AABB(Math.min(area.minX(), bounds.minX()), Math.min(area.minY(), bounds.minY()), Math.min(area.minZ(), bounds.minZ()),
                Math.max(area.maxX(), bounds.maxX()), Math.max(area.maxY(), bounds.maxY()), Math.max(area.maxZ(), bounds.maxZ()));
        java.util.List<Entity> list = cl.getEntities(null, query, e -> !e.isSpectator());
        if (list.isEmpty()) return;
        boolean sticky = B.is(be.movedState, SLIME_BLOCK);
        for (Entity e : list) {
            if (e.ignoresPistonPush()) continue;
            if (sticky) {   // 서버 플레이어는 바닐라도 건너뛴다 — 여기 목록엔 애초에 없다
                double vx = e.vx(), vy = e.vy(), vz = e.vz();
                switch (Dir.axis(dir)) {
                    case Dir.AXIS_X -> vx = Dir.DX[dir];
                    case Dir.AXIS_Y -> vy = Dir.DY[dir];
                    default -> vz = Dir.DZ[dir];
                }
                e.setDeltaMovement(vx, vy, vz);
                org.domi.furnace.server.entity.EntityActivation.immunize(e, 10);   // Paper EAR: 점액에 튕긴 엔티티는 10틱 활성(꺼져 있으면 아무것도)
            }
            double d1 = 0.0;
            for (int i = 0, n = shape.boxCount(); i < n; i++) {
                AABB a1 = movementArea(moveByPositionAndProgress(pos, toAabb(shape.box(i)), be), dir, d);
                AABB a2 = e.boundingBox();
                if (a1.intersects(a2)) {
                    d1 = Math.max(d1, getMovement(a1, dir, a2));
                    if (d1 >= d) break;
                }
            }
            if (!(d1 <= 0.0)) {
                d1 = Math.min(d1, d) + 0.01;
                moveEntityByPiston(dir, e, d1, dir);
                if (!be.extending && be.isSourcePiston) fixEntityWithinPistonBase(pos, e, dir, d);
            }
        }
    }

    private static void moveEntityByPiston(int noClipDir, Entity e, double d, int moveDir) {
        NOCLIP.set(noClipDir);
        try {
            e.move(Entity.MOVER_PISTON, d * Dir.DX[moveDir], d * Dir.DY[moveDir], d * Dir.DZ[moveDir]);
        } finally {
            NOCLIP.remove();
        }
    }

    /** moveStuckEntities: 꿀 블록이 옆으로 움직이면 그 위(윗면 ~ +1.5)에 서 있는 엔티티를 같이 옮긴다. */
    private static void moveStuckEntities(BlockLevel level, Pos pos, float f, PistonMovingBlockEntity be) {
        if (!B.is(be.movedState, HONEY_BLOCK) || !(level instanceof ChunkLevel cl)) return;
        int dir = be.getMovementDirection();
        if (Dir.axis(dir) == Dir.AXIS_Y) return;
        double top = collision(be.movedState, level, pos).bounds()[4];   // max(Y)
        AABB box = moveByPositionAndProgress(pos, new AABB(0.0, top, 0.0, 1.0, 1.5000010000000001, 1.0), be);
        double d = f - be.progress;
        for (Entity e : cl.getEntities(null, box, x -> matchesStickyCriteria(box, x, pos))) moveEntityByPiston(dir, e, d, dir);
    }

    private static boolean matchesStickyCriteria(AABB box, Entity e, Pos pos) {
        return !e.ignoresPistonPush() && e.onGround()
                && (e.isSupportedBy(pos) || e.x() >= box.minX() && e.x() <= box.maxX() && e.z() >= box.minZ() && e.z() <= box.maxZ());
    }

    /** fixEntityWithinPistonBase: 줄어드는 피스톤 받침 칸 안에 걸친 엔티티를 받침 밖으로 조금 더 민다. */
    private static void fixEntityWithinPistonBase(Pos pos, Entity e, int dir, double d) {
        AABB a = e.boundingBox();
        AABB b = new AABB(pos.x(), pos.y(), pos.z(), pos.x() + 1.0, pos.y() + 1.0, pos.z() + 1.0);
        if (a.intersects(b)) {
            int opp = Dir.opposite(dir);
            double d0 = getMovement(b, opp, a) + 0.01;
            AABB in = new AABB(Math.max(a.minX(), b.minX()), Math.max(a.minY(), b.minY()), Math.max(a.minZ(), b.minZ()),
                    Math.min(a.maxX(), b.maxX()), Math.min(a.maxY(), b.maxY()), Math.min(a.maxZ(), b.maxZ()));
            double d1 = getMovement(b, opp, in) + 0.01;
            if (Math.abs(d0 - d1) < 0.01) {
                d0 = Math.min(d0, d) + 0.01;
                moveEntityByPiston(dir, e, d0, opp);
            }
        }
    }

    /** getMovement(블록 쪽 상자, 방향, 엔티티 상자): 그 방향으로 겹친 깊이. */
    private static double getMovement(AABB a, int dir, AABB e) {
        return switch (dir) {
            case Dir.EAST -> a.maxX() - e.minX();
            case Dir.WEST -> e.maxX() - a.minX();
            case Dir.DOWN -> e.maxY() - a.minY();
            case Dir.SOUTH -> a.maxZ() - e.minZ();
            case Dir.NORTH -> e.maxZ() - a.minZ();
            default -> a.maxY() - e.minY();   // UP
        };
    }

    /** moveByPositionAndProgress: 칸 좌표 + 남은 진행도만큼 원래 방향(direction)으로 옮긴다. */
    private static AABB moveByPositionAndProgress(Pos pos, AABB box, PistonMovingBlockEntity be) {
        double d = be.getExtendedProgress(be.progress);
        return box.move(pos.x() + d * Dir.DX[be.direction], pos.y() + d * Dir.DY[be.direction], pos.z() + d * Dir.DZ[be.direction]);
    }

    /** PistonMath.getMovementArea: 미는 쪽 면에서 그 방향으로 d 만큼의 얇은 상자. */
    static AABB movementArea(AABB b, int dir, double d) {
        double s = d * (Dir.DX[dir] + Dir.DY[dir] + Dir.DZ[dir]);
        double lo = Math.min(s, 0.0), hi = Math.max(s, 0.0);
        return switch (dir) {
            case Dir.WEST -> AABB.of(b.minX() + lo, b.minY(), b.minZ(), b.minX() + hi, b.maxY(), b.maxZ());
            case Dir.EAST -> AABB.of(b.maxX() + lo, b.minY(), b.minZ(), b.maxX() + hi, b.maxY(), b.maxZ());
            case Dir.DOWN -> AABB.of(b.minX(), b.minY() + lo, b.minZ(), b.maxX(), b.minY() + hi, b.maxZ());
            case Dir.NORTH -> AABB.of(b.minX(), b.minY(), b.minZ() + lo, b.maxX(), b.maxY(), b.minZ() + hi);
            case Dir.SOUTH -> AABB.of(b.minX(), b.minY(), b.maxZ() + lo, b.maxX(), b.maxY(), b.maxZ() + hi);
            default -> AABB.of(b.minX(), b.maxY() + lo, b.minZ(), b.maxX(), b.maxY() + hi, b.maxZ());   // UP
        };
    }

    private static AABB toAabb(double[] b) { return new AABB(b[0], b[1], b[2], b[3], b[4], b[5]); }

    private static Shape collision(int state, BlockLevel level, Pos at) { return B.behavior(state).getCollisionShape(state, level, at, org.domi.furnace.server.block.CollisionContext.EMPTY); }

    @Override
    public void load(NbtCompound tag) {
        movedState = readBlockState(tag.contains("blockState") ? tag.getCompound("blockState") : null);
        direction = Math.floorMod(tag.getIntOr0("facing"), 6);
        progress = tag.tags().get("progress") instanceof Number n ? n.floatValue() : 0.0F;
        progressO = progress;
        extending = tag.tags().get("extending") instanceof Number n && n.byteValue() != 0;
        isSourcePiston = tag.tags().get("source") instanceof Number n && n.byteValue() != 0;
    }

    @Override
    protected void saveAdditional(NbtCompound tag) {
        tag.putCompound("blockState", writeBlockState(movedState));
        tag.putInt("facing", direction);
        tag.putFloat("progress", progressO);
        tag.putBoolean("extending", extending);
        tag.putBoolean("source", isSourcePiston);
    }

    /** getUpdateTag = saveCustomOnly. */
    @Override
    public NbtCompound updateTag() { return saveCustomOnly(); }

    /** NbtUtils.writeBlockState: {Name, Properties?}. */
    public static NbtCompound writeBlockState(int state) {
        Blocks blocks = Blocks.get();
        NbtCompound t = new NbtCompound();
        t.putString("Name", blocks.nameOfState(state));
        Prop[] props = blocks.propsOf(blocks.blockOf(state));
        if (props.length > 0) {
            NbtCompound p = new NbtCompound();
            for (Prop prop : props) p.putString(prop.name(), prop.valueName(state));
            t.putCompound("Properties", p);
        }
        return t;
    }

    /** NbtUtils.readBlockState: 모르는 블록은 공기, 맞지 않는 속성 값은 건너뛴다. */
    public static int readBlockState(NbtCompound t) {
        Blocks blocks = Blocks.get();
        if (t == null || !(t.tags().get("Name") instanceof String name)) return B.defaultState("minecraft:air");
        int b = blocks.block(name);
        if (b < 0) return B.defaultState("minecraft:air");
        int s = blocks.defaultState(b);
        if (t.contains("Properties")) {
            NbtCompound p = t.getCompound("Properties");
            for (Prop prop : blocks.propsOf(b)) {
                if (p.tags().get(prop.name()) instanceof String v && prop.indexOf(s, v) >= 0) s = prop.with(s, v);
            }
        }
        return s;
    }
}
