// 블록 동작이 보는 월드(바닐라 Level/LevelAccessor/LevelReader 중 블록 코드가 쓰는 부분). 서버 쪽만(isClientSide = false).
package org.domi.furnace.server.block;

import org.domi.furnace.server.entity.ItemStack;

public interface BlockLevel {

    // ------------------------------------------------------------------ 읽기

    int getBlockState(Pos pos);

    /** 유체 상태(Fluids). */
    int getFluidState(Pos pos);

    int minBuildHeight();

    /** 상한(제외). */
    int maxBuildHeight();

    default boolean isOutsideBuildHeight(int y) { return y < minBuildHeight() || y >= maxBuildHeight(); }

    /** isInWorldBounds: 높이 안이고 x,z 가 ±30000000 안. */
    default boolean isInWorldBounds(Pos p) {
        return !isOutsideBuildHeight(p.y()) && p.x() >= -30000000 && p.z() >= -30000000 && p.x() < 30000000 && p.z() < 30000000;
    }

    /** 그 청크가 로드돼 있는가(hasChunkAt). */
    boolean hasChunkAt(Pos pos);

    default boolean isEmptyBlock(Pos pos) { return BlockInfo.get().isAir(getBlockState(pos)); }

    default boolean isWaterAt(Pos pos) { return Fluids.isWater(getFluidState(pos)); }

    /** EntityGetter.isUnobstructed(null, shape): 건설을 막는 엔티티(관전자 제외)의 상자와 겹치지 않는가. shape 는 월드 좌표. */
    boolean isUnobstructed(Shape worldShape);

    /** CollisionGetter.isUnobstructed(state, pos, ctx): 충돌 모양이 비었거나 엔티티와 겹치지 않으면. */
    default boolean isUnobstructed(int state, Pos pos, BlockActor collisionEntity) {
        Shape s = BlockBehaviors.of(state).getCollisionShape(state, this, pos, collisionEntity);
        return s.isEmpty() || isUnobstructed(s.move(pos.x(), pos.y(), pos.z()));
    }

    // ------------------------------------------------------------------ 쓰기(바닐라 플래그: Block.UPDATE_*)

    int UPDATE_NEIGHBORS = 1, UPDATE_CLIENTS = 2, UPDATE_INVISIBLE = 4, UPDATE_IMMEDIATE = 8, UPDATE_KNOWN_SHAPE = 16,
            UPDATE_SUPPRESS_DROPS = 32, UPDATE_MOVE_BY_PISTON = 64, UPDATE_NONE = 4, UPDATE_ALL = 3, UPDATE_ALL_IMMEDIATE = 11,
            UPDATE_LIMIT = 512;

    boolean setBlock(Pos pos, int state, int flags, int recursionLeft);

    default boolean setBlock(Pos pos, int state, int flags) { return setBlock(pos, state, flags, UPDATE_LIMIT); }

    default boolean setBlockAndUpdate(Pos pos, int state) { return setBlock(pos, state, UPDATE_ALL); }

    /** removeBlock: 그 자리 유체의 레거시 블록으로(3 | 이동 중이면 64). */
    default boolean removeBlock(Pos pos, boolean moving) {
        return setBlock(pos, Fluids.createLegacyBlock(getFluidState(pos)), UPDATE_ALL | (moving ? UPDATE_MOVE_BY_PISTON : 0));
    }

    boolean destroyBlock(Pos pos, boolean dropBlock, BlockActor breaker, int recursionLeft);

    default boolean destroyBlock(Pos pos, boolean dropBlock) { return destroyBlock(pos, dropBlock, null, UPDATE_LIMIT); }

    // ------------------------------------------------------------------ 이웃·모양 갱신

    void updateNeighborsAt(Pos pos, int sourceBlock);

    void updateNeighborsAtExceptFromFacing(Pos pos, int sourceBlock, int skipDir);

    void neighborChanged(Pos pos, int sourceBlock, Pos fromPos);

    void neighborChanged(int state, Pos pos, int sourceBlock, Pos fromPos, boolean moved);

    void neighborShapeChanged(int dir, int neighborState, Pos pos, Pos neighborPos, int flags, int recursionLeft);

    default void blockUpdated(Pos pos, int block) { updateNeighborsAt(pos, block); }

    void updateNeighbourForOutputSignal(Pos pos, int block);

    // ------------------------------------------------------------------ 예약 틱

    void scheduleTick(Pos pos, int block, int delay, int priority);

    default void scheduleTick(Pos pos, int block, int delay) { scheduleTick(pos, block, delay, 0); }

    void scheduleFluidTick(Pos pos, int fluidType, int delay);

    boolean hasScheduledTick(Pos pos, int block);

    /** 지금 틱에 실행될 예약이 있는가(getBlockTicks().willTickThisTick). */
    boolean willTickThisTick(Pos pos, int block);

    // ------------------------------------------------------------------ 레드스톤(바닐라 SignalGetter)

    /** getSignal(pos, dir): 그 블록이 dir 쪽(이웃 쪽에서 본 방향)으로 내는 신호 — 전도체면 직접 신호 최댓값. */
    int getSignal(Pos pos, int dir);

    int getDirectSignal(Pos pos, int dir);

    int getDirectSignalTo(Pos pos);

    boolean hasSignal(Pos pos, int dir);

    boolean hasNeighborSignal(Pos pos);

    int getBestNeighborSignal(Pos pos);

    // ------------------------------------------------------------------ 효과(소리·레벨 이벤트·블록 이벤트·게임 이벤트)

    /** Level.playSound(except, pos, 소리, 분류, 크기, 높이) — 블록 가운데에서. */
    void playSound(BlockActor except, Pos pos, String sound, String source, float volume, float pitch);

    void playSound(BlockActor except, double x, double y, double z, String sound, String source, float volume, float pitch);

    void levelEvent(BlockActor except, int type, Pos pos, int data);

    /** ServerLevel.globalLevelEvent: globalSoundEvents 게임룰이면 모든 플레이어에게, 아니면 levelEvent. */
    default void globalLevelEvent(int type, Pos pos, int data) { levelEvent(null, type, pos, data); }

    void blockEvent(Pos pos, int block, int type, int data);

    /** 게임 이벤트(스컬크 감지 등) — 진동 체계가 들어오기 전까지는 기록만. */
    void gameEvent(String event, Pos pos, BlockActor source, int state);

    // ------------------------------------------------------------------ 기타

    /**
     * Level.getRawBrightness(pos, skyDarken): max(하늘빛 - skyDarken, 블록빛).
     * TODO(빛): 빛 엔진 전까지 15(하늘이 보이는 바깥과 같은 값) — 어두운 지하에서만 바닐라와 달라진다.
     */
    default int getRawBrightness(Pos pos, int skyDarken) { return 15 - skyDarken; }

    /**
     * Level.skyDarken(updateSkyBrightness): 해 높이·비·천둥으로 정한 하늘빛 감소량(0..11).
     * 바닐라는 틱 머리에 한 번 계산해 두지만 같은 틱 안에서는 값이 같다.
     */
    default int skyDarken() {
        float rain = rainLevel();
        double d0 = 1.0 - rain * 5.0F / 16.0;
        double d1 = 1.0 - thunderLevel() * rain * 5.0F / 16.0;
        double d2 = 0.5 + 2.0 * Math.clamp(Dir.cos(B.timeOfDay(this) * (float) (Math.PI * 2)), -0.25, 0.25);
        return (int) ((1.0 - d2 * d0 * d1) * 11.0);
    }

    /** LevelReader.getMaxLocalRawBrightness(pos) = getRawBrightness(pos, skyDarken). */
    default int getMaxLocalRawBrightness(Pos pos) { return getRawBrightness(pos, skyDarken()); }

    /** Level.getBrightness(LightLayer.SKY, pos): 하늘빛. TODO(빛): 빛 엔진 전까지 15(하늘이 보이는 곳과 같은 값). */
    default int getSkyLight(Pos pos) { return 15; }

    /** Level.getBrightness(LightLayer.BLOCK, pos): 블록빛. TODO(빛): 빛 엔진 전까지 0(주변에 광원이 없는 곳과 같은 값). */
    default int getBlockLight(Pos pos) { return 0; }

    /** level.getBiome(pos).is(#태그). */
    default boolean biomeIs(Pos pos, String tag) { return false; }

    /** Level.isRainingAt(pos): 비가 오고, 하늘이 보이고, 그 바이옴이 비가 오는 곳. TODO(빛·바이옴 강수): 하늘 노출 계산 전까지 거짓. */
    default boolean isRainingAt(Pos pos) { return false; }

    RandomSource random();

    long gameTime();

    long dayTime();

    boolean isRaining();

    boolean isThundering();

    /** Level.getRainLevel(1.0F). */
    default float rainLevel() { return isRaining() ? 1f : 0f; }

    /** 천둥 세기(비 세기를 곱하기 전). */
    default float thunderLevel() { return isThundering() ? 1f : 0f; }

    /** 게임룰 값(불리언). */
    boolean gameRule(String name);

    int gameRuleInt(String name);

    /** 0 평화 .. 3 어려움. */
    int difficulty();

    /** 차원 id(minecraft:overworld …). */
    String dimension();

    boolean ultraWarm();

    /** dimensionType().hasSkyLight(). */
    default boolean hasSkyLight() { return true; }

    /** 월드 보더 안인가(isWithinBounds). */
    boolean withinBorder(Pos pos);

    /** 블록 엔티티(없으면 null — 블록 엔티티 체계가 들어오기 전에는 상자만). */
    Object getBlockEntity(Pos pos);

    /** Level.blockEntityChanged: 그 칸의 청크를 저장 대상으로. */
    default void blockEntityChanged(Pos pos) {}

    /** Level.setBlockEntity: 그 칸 상태가 블록 엔티티를 가질 때만 둔다(틱 받는 종류면 틱 목록에). */
    default void setBlockEntity(org.domi.furnace.server.block.entity.BlockEntity be) {}

    /** ServerLevel.isHandlingTick: 예약 틱(블록·유체)을 돌리는 중인가. */
    default boolean isHandlingTick() { return false; }

    void removeBlockEntity(Pos pos);

    /** 행위자에게 액션바 메시지(예: build.tooHigh). */
    void sendActionBar(BlockActor actor, org.domi.furnace.api.text.Text text);

    /** 블록에서 아이템 떨구기(Block.popResource). 아이템 엔티티는 TODO(엔티티) — 난수는 바닐라와 같은 횟수로 뽑는다. */
    void popResource(Pos pos, ItemStack stack);

    /** Block.popResourceFromFace(level, pos, dir, stack). */
    void popResourceFromFace(Pos pos, int dir, ItemStack stack);

    // ------------------------------------------------------------------ 드롭(바닐라 Block.dropResources·getDrops·popExperience)

    /** 그 블록의 전리품 표(BLOCK 문맥) 결과 — origin 은 칸 가운데. explosionRadius 가 null 이면 폭발 아님. */
    default java.util.List<ItemStack> lootDrops(String lootTable, int state, Pos pos, BlockActor entity, ItemStack tool, Float explosionRadius) {
        return java.util.List.of();
    }

    /** Block.dropResources(state, level, pos, blockEntity, entity, tool): 드롭을 하나씩 popResource 한 뒤 spawnAfterBreak(…, true). */
    default void dropResources(int state, Pos pos, Object blockEntity, BlockActor entity, ItemStack tool) {
        BlockBehavior b = BlockBehaviors.of(state);
        for (ItemStack s : b.getDrops(state, this, pos, blockEntity, entity, tool)) popResource(pos, s);
        b.spawnAfterBreak(state, this, pos, tool, true);
    }

    /** Block.dropResources(state, level, pos). */
    default void dropResources(int state, Pos pos) { dropResources(state, pos, null, null, ItemStack.EMPTY); }

    /** Block.popExperience: doTileDrops 이면 경험치 구슬(ExperienceOrb.award). 구슬은 자기 난수만 쓴다. TODO(엔티티): 구슬 생성. */
    default void popExperience(Pos pos, int amount) {}

    /** ServerLevel.destroyBlockProgress: 부수는 사람을 뺀 32칸 안 플레이어에게 금 가는 단계(0..9, 그 밖이면 지움). */
    default void destroyBlockProgress(int breakerId, Pos pos, int progress) {}
}
