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

    /** Block.onProjectileHit: 투사체가 이 블록에 맞았다(Projectile.onHitBlock 이 맞은 블록에 부른다). */
    public void onProjectileHit(BlockLevel level, int state, org.domi.furnace.server.entity.Projectile.Hit hit,
                                org.domi.furnace.server.entity.Projectile projectile) {}

    /** onRemove 기본: 블록 엔티티가 있던 블록이 다른 블록이 되면 블록 엔티티를 지운다. */
    /** EntityBlock.newBlockEntity — 블록 엔티티를 가진 블록이 만든다(아직 이식하지 않은 종류는 null). */
    public org.domi.furnace.server.block.entity.BlockEntity newBlockEntity(Pos pos, int state) { return null; }

    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        if (INFO.hasBlockEntity(state) && BLOCKS.blockOf(newState) != block) level.removeBlockEntity(pos);
    }

    // ------------------------------------------------------------------ 파괴

    /**
     * Block.playerWillDestroy: 파괴 효과(2001, 부순 사람 제외) → piglin 분노(#guarded_by_piglins) → 게임 이벤트. 돌려준 상태가 이후 destroy 에 쓰인다.
     */
    public int playerWillDestroy(BlockLevel level, Pos pos, int state, BlockActor player) {
        level.levelEvent(player, 2001, pos, state);
        // 금 블록·상자류를 캐면 주변 피글린이 화낸다(캐기는 시야를 보지 않는다).
        if (B.isTag(state, "minecraft:guarded_by_piglins")) level.angerNearbyPiglins(player, false);
        level.gameEvent("minecraft:block_destroy", pos, player, state);
        return state;
    }

    /**
     * Block.playerDestroy(level, player, pos, state, blockEntity, tool): 플레이어가 부순 뒤(칸은 이미 지워짐) — 통계 mined·허기 0.005·드롭.
     */
    public void playerDestroy(BlockLevel level, BlockActor player, Pos pos, int state, Object blockEntity, ItemStack tool) {
        player.awardStat("minecraft:mined:" + name());
        player.causeFoodExhaustion(0.005F);
        level.dropResources(state, pos, blockEntity, player, tool);
    }

    /** Block.dropFromExplosion: 폭발이 부술 때 전리품을 떨구는가. */
    public boolean dropFromExplosion() { return true; }

    /** Fallable.getFallDamageSource: 떨어지는 블록이 맞힐 때의 피해 종류. */
    public String fallDamageType() { return "falling_block"; }

    /** Fallable.onLand: 떨어지던 블록이 내려앉았다(replaced 는 원래 그 자리 상태). */
    public void onLand(BlockLevel level, Pos pos, int state, int replaced, boolean silent) {}

    /** Fallable.onBrokenAfterFall: 내려앉지 못하고 부서졌다. */
    public void onBrokenAfterFall(BlockLevel level, Pos pos, boolean silent) {}

    /** Fallable.falling(entity): 떨어지는 엔티티가 만들어질 때 손볼 것(모루 피해 설정). 엔티티는 FallingBlockEntity. */
    public void falling(Object fallingBlockEntity) {}

    /** Block.onExplosionHit: 블록을 터뜨리는 폭발(윈드 차지)이 맞았을 때. */
    public void onExplosionHit(int state, BlockLevel level, Pos pos, Explosion explosion) {}

    /** Block.wasExploded: 폭발에 부서진 뒤(TNT 는 점화). */
    public void wasExploded(BlockLevel level, Pos pos, Explosion explosion) {}

    /** BlockBehaviour.getDrops: 전리품 표가 비었으면 없음, 아니면 BLOCK 문맥(block_state = 이 상태, block_entity = 파괴 직전 블록 엔티티)으로 평가. */
    public java.util.List<ItemStack> getDrops(int state, BlockLevel level, Pos pos, Object blockEntity, BlockActor entity, ItemStack tool) {
        return getDrops(state, level, pos, blockEntity, entity, tool, null);
    }

    /** getDrops — 폭발 반경(없으면 null)까지. */
    public java.util.List<ItemStack> getDrops(int state, BlockLevel level, Pos pos, Object blockEntity, BlockActor entity, ItemStack tool,
                                              Float explosionRadius) {
        String table = INFO.lootTable(block);
        if (table == null || table.equals("minecraft:empty")) return java.util.List.of();
        // block_entity: 부르는 쪽(이 칸의 region 스레드)에서 컴포넌트를 모아 넘긴다
        java.util.Map<String, Object> be = blockEntity instanceof org.domi.furnace.server.block.entity.BlockEntity b ? b.collectComponents() : null;
        return level.lootDrops(table, state, pos, entity, tool, explosionRadius, be);
    }

    /** Block.handlePrecipitation(state, level, pos, "rain"|"snow"): 비·눈이 내린 칸(가마솥만 반응). */
    public void handlePrecipitation(int state, BlockLevel level, Pos pos, String precipitation) {}

    /** BlockBehaviour.spawnAfterBreak(state, level, pos, tool, dropExperience): 경험치·좀벌레 등. */
    public void spawnAfterBreak(int state, BlockLevel level, Pos pos, ItemStack tool, boolean dropExperience) {}

    /**
     * Block.tryDropExperience(level, pos, tool, IntProvider[min..max]): 표본(uniform 이면 레벨 난수 nextInt) → 섬세한 손길이면 0
     * (EnchantmentHelper.processBlockExperience — block_experience 효과) → 0 보다 크면 popExperience.
     */
    protected static void tryDropExperience(BlockLevel level, Pos pos, ItemStack tool, int min, int max) {
        int sample = min == max ? min : level.random().nextInt(max - min + 1) + min;
        int xp = org.domi.furnace.server.item.ItemTools.enchantmentLevel(tool, "minecraft:silk_touch") > 0 ? 0 : sample;
        if (xp > 0) level.popExperience(pos, xp);
    }

    /**
     * BlockBehaviour.getDestroyProgress(state, player, level, pos): 경도가 -1 이면 0, 아니면 플레이어 파괴 속도 / 경도 / (맞는 도구 30, 아니면 100).
     */
    public float getDestroyProgress(int state, BlockActor player, BlockLevel level, Pos pos) {
        float hardness = INFO.destroyTime(block);
        if (hardness == -1.0F) return 0.0F;
        int div = player.hasCorrectToolForDrops(state) ? 30 : 100;
        return player.destroySpeed(state) / hardness / (float) div;
    }

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

    /** LiquidBlockContainer 인가 — 기본은 SimpleWaterloggedBlock(waterlogged 속성이 있는 블록). 켈프·해초도(언제나 거절). */
    public boolean isLiquidContainer(int state) { return WATERLOGGED.has(state); }

    /** LiquidBlockContainer.canPlaceLiquid(player, level, pos, state, fluid) — SimpleWaterloggedBlock: 물 원천만. */
    public boolean canPlaceLiquid(BlockActor player, BlockLevel level, Pos pos, int state, int fluidType) {
        return WATERLOGGED.has(state) && fluidType == Fluids.T_WATER;
    }

    /** LiquidBlockContainer.placeLiquid(level, pos, state, fluidState) — SimpleWaterloggedBlock: 잠기지 않았고 물 원천이면 잠그고 물 틱. */
    public boolean placeLiquid(BlockLevel level, Pos pos, int state, int fluidState) {
        if (WATERLOGGED.has(state) && !WATERLOGGED.is(state) && Fluids.type(fluidState) == Fluids.T_WATER) {
            level.setBlock(pos, WATERLOGGED.with(state, true), 3);
            level.scheduleFluidTick(pos, Fluids.T_WATER, Fluids.tickDelay(fluidState, level.ultraWarm()));
            return true;
        }
        return false;
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

    /** getVisualShape(state, level, pos): 시선 검사(ClipContext.Block.VISUAL)가 보는 모양 — 기본은 충돌 모양. */
    public Shape getVisualShape(int state, BlockLevel level, Pos pos, BlockActor entity) { return getCollisionShape(state, level, pos, entity); }

    // ------------------------------------------------------------------ 엔티티(바닐라 BlockBehaviour·Block 의 엔티티 훅)

    /** entityInside(state, level, pos, entity): 엔티티 몸통이 이 칸에 걸쳐 있을 때 매 이동. */
    public void entityInside(int state, BlockLevel level, Pos pos, org.domi.furnace.server.entity.Entity entity) {}

    /** stepOn(level, pos, state, entity): 땅에 선 엔티티가 디딘 블록. */
    public void stepOn(BlockLevel level, Pos pos, int state, org.domi.furnace.server.entity.Entity entity) {}

    /** fallOn(level, state, pos, entity, fallDistance): 떨어져 내려앉았다 — 기본은 낙하 피해(배수 1). */
    public void fallOn(BlockLevel level, int state, Pos pos, org.domi.furnace.server.entity.Entity entity, float fallDistance) {
        entity.causeFallDamage(fallDistance, 1.0F);
    }

    /** updateEntityAfterFallOn(level, entity): 세로로 부딪힌 뒤 — 기본은 세로 속도를 0 으로(곱하기 0). */
    public void updateEntityAfterFallOn(BlockLevel level, org.domi.furnace.server.entity.Entity entity) {
        entity.setDeltaMovement(entity.vx() * 1.0, entity.vy() * 0.0, entity.vz() * 1.0);
    }

    /** getCollisionShape(state, level, pos, 문맥 엔티티 — 없으면 null). */
    public Shape getCollisionShape(int state, BlockLevel level, Pos pos, BlockActor entity) { return INFO.collisionShape(state); }

    public boolean isFaceSturdy(int state, BlockLevel level, Pos pos, int dir, int supportType) {
        return INFO.faceSturdy(state, dir, supportType);
    }

    @Override
    public String toString() { return getClass().getSimpleName() + "(" + name() + ")"; }
}
