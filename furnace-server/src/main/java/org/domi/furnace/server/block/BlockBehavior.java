// 블록 동작 기본값(바닐라 BlockBehaviour/Block). 정의 종류(바닐라 블록 클래스)마다 이 클래스를 상속해 바닐라 메서드를 그대로 옮긴다.
// 상태는 int(전역 상태 id), 블록은 블록 번호(Blocks), 위치는 Pos, 유체는 Fluids 인코딩. "null 상태"는 -1.
package org.domi.furnace.server.block;

import org.domi.furnace.server.entity.ItemStack;

public class BlockBehavior {

    protected static final Blocks BLOCKS = Blocks.get();
    protected static final BlockInfo INFO = BlockInfo.get();
    protected static final Prop WATERLOGGED = BLOCKS.prop("waterlogged");

    /** 이 동작의 블록 번호. */
    protected final int block;

    public BlockBehavior(int block) { this.block = block; }

    public final int block() { return block; }

    public final String name() { return BLOCKS.name(block); }

    public final int defaultState() { return BLOCKS.defaultState(block); }

    /** state 가 이 블록의 상태인가(BlockState.is(this)). */
    protected final boolean isThis(int state) { return BLOCKS.blockOf(state) == block; }

    // ------------------------------------------------------------------ 설치

    /** Block.getStateForPlacement — 놓을 수 없으면 -1. */
    public int getStateForPlacement(PlaceContext ctx) { return defaultState(); }

    /** canBeReplaced(ctx): replaceable 이고, 손에 든 것이 이 블록의 아이템이 아니면. */
    public boolean canBeReplaced(int state, PlaceContext ctx) {
        return INFO.replaceable(state) && (ctx.itemInHand().isEmpty() || !ctx.holding(block));
    }

    /** canBeReplaced(fluid): replaceable 이거나 legacySolid 가 아니면. */
    public boolean canBeReplaced(int state, int fluidType) { return INFO.replaceable(state) || !INFO.legacySolid(state); }

    public boolean canSurvive(int state, BlockLevel level, Pos pos) { return true; }

    public void setPlacedBy(BlockLevel level, Pos pos, int state, BlockActor placer, ItemStack stack) {}

    // ------------------------------------------------------------------ 갱신

    /** updateShape: dir 은 이 블록에서 이웃 쪽 방향. 공기를 돌려주면 부서진다. */
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) { return state; }

    public void updateIndirectNeighbourShapes(int state, BlockLevel level, Pos pos, int flags, int recursionLeft) {}

    public void neighborChanged(int state, BlockLevel level, Pos pos, int sourceBlock, Pos sourcePos, boolean moved) {}

    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) {}

    /** onRemove 기본: 블록 엔티티가 있던 블록이 다른 블록이 되면 블록 엔티티를 지운다. */
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        if (INFO.hasBlockEntity(state) && BLOCKS.blockOf(newState) != block) level.removeBlockEntity(pos);
    }

    // ------------------------------------------------------------------ 파괴

    /**
     * Block.playerWillDestroy: 파괴 효과(2001, 부순 사람 제외) → piglin 분노(엔티티 체계 전에는 없음) → 게임 이벤트. 돌려준 상태가 이후 destroy 에 쓰인다.
     */
    public int playerWillDestroy(BlockLevel level, Pos pos, int state, BlockActor player) {
        level.levelEvent(player, 2001, pos, state);
        level.gameEvent("minecraft:block_destroy", pos, player, state);
        return state;
    }

    /**
     * Block.playerDestroy(level, player, pos, state, blockEntity, tool): 플레이어가 부순 뒤(칸은 이미 지워짐) — 통계·허기·드롭.
     * TODO(3-4b): 파괴 경로에서 부르고 기본 동작(통계 mined, 허기 0.005, dropResources)을 채운다.
     */
    public void playerDestroy(BlockLevel level, BlockActor player, Pos pos, int state, ItemStack tool) {}

    /** Block.destroy(level, pos, state): 플레이어 파괴로 칸이 지워진 뒤. */
    public void destroy(BlockLevel level, Pos pos, int state) {}

    // ------------------------------------------------------------------ 틱

    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {}

    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {}

    /** isRandomlyTicking(state) — 기본은 Properties.isRandomlyTicking. */
    public boolean isRandomlyTicking(int state) { return INFO.randomlyTicking(block); }

    // ------------------------------------------------------------------ 상호작용

    public ItemInteractionResult useItemOn(ItemStack stack, int state, BlockLevel level, Pos pos, BlockActor player, int hand, Hit hit) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        return InteractionResult.PASS;
    }

    /** 왼쪽 클릭(attack). */
    public void attack(int state, BlockLevel level, Pos pos, BlockActor player) {}

    /** 블록 이벤트(triggerEvent) — 피스톤·음표 블록·상자 뚜껑 등. */
    public boolean triggerEvent(int state, BlockLevel level, Pos pos, int type, int data) { return false; }

    // ------------------------------------------------------------------ 유체

    /** getFluidState — 기본은 비어 있음, waterlogged=true 면 물 원천. */
    public int getFluidState(int state) {
        return WATERLOGGED.has(state) && WATERLOGGED.is(state) ? Fluids.WATER_SOURCE : Fluids.EMPTY;
    }

    // ------------------------------------------------------------------ 레드스톤

    public boolean isSignalSource(int state) { return false; }

    /** getSignal(state, level, pos, dir): dir 은 신호를 받는 쪽에서 이 블록을 향하는 방향의 반대(바닐라 그대로). */
    public int getSignal(int state, BlockLevel level, Pos pos, int dir) { return 0; }

    public int getDirectSignal(int state, BlockLevel level, Pos pos, int dir) { return 0; }

    public boolean hasAnalogOutputSignal(int state) { return INFO.hasAnalogOutputSignal(state); }

    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) { return 0; }

    /** isRedstoneConductor(state, level, pos). */
    public boolean isRedstoneConductor(int state, BlockLevel level, Pos pos) { return INFO.redstoneConductor(state); }

    // ------------------------------------------------------------------ 모양

    public Shape getShape(int state, BlockLevel level, Pos pos) { return INFO.shape(state); }

    /** getCollisionShape(state, level, pos, 문맥 엔티티 — 없으면 null). */
    public Shape getCollisionShape(int state, BlockLevel level, Pos pos, BlockActor entity) { return INFO.collisionShape(state); }

    public boolean isFaceSturdy(int state, BlockLevel level, Pos pos, int dir, int supportType) {
        return INFO.faceSturdy(state, dir, supportType);
    }

    @Override
    public String toString() { return getClass().getSimpleName() + "(" + name() + ")"; }
}
