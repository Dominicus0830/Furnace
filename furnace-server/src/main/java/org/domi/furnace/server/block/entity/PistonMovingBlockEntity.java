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
import org.domi.furnace.server.block.types.PistonBaseBlock;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class PistonMovingBlockEntity extends BlockEntity {

    private static final int MOVING_PISTON = B.id("moving_piston"), PISTON_HEAD = B.id("piston_head"), STICKY_PISTON = B.id("sticky_piston");
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

    /** tick(level, pos, state, entity). TODO(엔티티): 진행 중에 닿은 엔티티 밀기(moveCollidedEntities)·꿀 블록에 붙은 엔티티 끌기(moveStuckEntities). */
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
            progress = f;
            if (progress >= 1.0F) progress = 1.0F;
        }
    }

    /** getCollisionShape(level, pos): 줄어드는 피스톤 받침 모양 + 옮기는 블록(또는 머리)의 모양을 진행도만큼 옮긴 것. */
    public Shape getCollisionShape(BlockLevel level, Pos at) {
        Shape base = !extending && isSourcePiston && B.behavior(movedState) instanceof PistonBaseBlock
                ? collision(EXTENDED.with(movedState, true), level, at) : Shape.EMPTY;
        int s;
        if (isSourcePiston) {
            s = SHORT.with(FACING.with(B.defaultState(PISTON_HEAD), Dir.NAME[direction]), extending != 1.0F - progress < 0.25F);
        } else {
            s = movedState;
        }
        float f = getExtendedProgress(progress);
        return Shape.or(base, collision(s, level, at).move(Dir.DX[direction] * f, Dir.DY[direction] * f, Dir.DZ[direction] * f));
    }

    private static Shape collision(int state, BlockLevel level, Pos at) { return B.behavior(state).getCollisionShape(state, level, at, null); }

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
