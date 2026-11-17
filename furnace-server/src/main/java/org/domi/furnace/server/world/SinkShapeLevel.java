// 플러그인 레벨 통로(T-211)의 나무 가장자리 모양 갱신용 레벨: Paper BlockStateListPopulator(+DummyGeneratorAccess)가 블록 동작의 updateShape 에 주는 것과
// 같은 답 — 블록은 이번 배치 사본(월드 + 받아들인 쓰기), 유체는 실제 월드(populator 는 유체를 감싼 레벨에서 읽고 나무 기록은 유체를 모른다),
// 예약 틱은 버린다(DummyGeneratorAccess). updateShape 가 그 밖의 것을 물으면 UnsupportedOperationException(호환 계층이 UnsupportedNmsCall 로).
package org.domi.furnace.server.world;

import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.block.RedstoneTorchToggles;
import org.domi.furnace.server.block.Shape;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;

final class SinkShapeLevel implements BlockLevel {

    /** 실제 월드 블록(사본 전) — 유체 읽기용. */
    interface OriginBlocks { int blockAt(int x, int y, int z); }

    private final FeatureWorldView view;
    private final OriginBlocks origin;

    SinkShapeLevel(FeatureWorldView view, OriginBlocks origin) {
        this.view = view;
        this.origin = origin;
    }

    private static UnsupportedOperationException no(String what) {
        return new UnsupportedOperationException(what + " while updating tree edge shapes through a plugin level");
    }

    @Override public int getBlockState(Pos pos) { return view.getBlock(pos.x(), pos.y(), pos.z()); }

    @Override
    public int getFluidState(Pos pos) {
        if (isOutsideBuildHeight(pos.y())) return org.domi.furnace.server.block.Fluids.EMPTY;
        int s = origin.blockAt(pos.x(), pos.y(), pos.z());
        return BlockBehaviors.of(s).getFluidState(s);
    }

    @Override public int minBuildHeight() { return view.minBuildHeight(); }
    @Override public int maxBuildHeight() { return view.maxBuildHeight(); }

    // DummyGeneratorAccess: 예약 틱은 버린다
    @Override public void scheduleTick(Pos pos, int block, int delay, int priority) {}
    @Override public void scheduleFluidTick(Pos pos, int fluidType, int delay) {}

    @Override public boolean hasChunkAt(Pos pos) { throw no("hasChunkAt"); }
    @Override public boolean isUnobstructed(Shape worldShape) { throw no("isUnobstructed"); }
    @Override public boolean setBlock(Pos pos, int state, int flags, int recursionLeft) { throw no("setBlock"); }
    @Override public boolean destroyBlock(Pos pos, boolean dropBlock, BlockActor breaker, int recursionLeft) { throw no("destroyBlock"); }
    @Override public void updateNeighborsAt(Pos pos, int sourceBlock) { throw no("updateNeighborsAt"); }
    @Override public void updateNeighborsAtExceptFromFacing(Pos pos, int sourceBlock, int skipDir) { throw no("updateNeighborsAtExceptFromFacing"); }
    @Override public void neighborChanged(Pos pos, int sourceBlock, Pos fromPos) { throw no("neighborChanged"); }
    @Override public void neighborChanged(int state, Pos pos, int sourceBlock, Pos fromPos, boolean moved) { throw no("neighborChanged"); }
    @Override public void neighborShapeChanged(int dir, int neighborState, Pos pos, Pos neighborPos, int flags, int recursionLeft) { throw no("neighborShapeChanged"); }
    @Override public void updateNeighbourForOutputSignal(Pos pos, int block) { throw no("updateNeighbourForOutputSignal"); }
    @Override public boolean hasScheduledTick(Pos pos, int block) { throw no("hasScheduledTick"); }
    @Override public boolean willTickThisTick(Pos pos, int block) { throw no("willTickThisTick"); }
    @Override public void playSound(BlockActor except, Pos pos, String sound, String source, float volume, float pitch) { throw no("playSound"); }
    @Override public void playSound(BlockActor except, double x, double y, double z, String sound, String source, float volume, float pitch) { throw no("playSound"); }
    @Override public void levelEvent(BlockActor except, int type, Pos pos, int data) { throw no("levelEvent"); }
    @Override public void blockEvent(Pos pos, int block, int type, int data) { throw no("blockEvent"); }
    @Override public void gameEvent(String event, Pos pos, BlockActor source, int state) { throw no("gameEvent"); }
    @Override public RandomSource random() { throw no("random"); }
    @Override public RedstoneTorchToggles redstoneTorchToggles() { throw no("redstoneTorchToggles"); }
    @Override public long gameTime() { throw no("getGameTime"); }
    @Override public long dayTime() { throw no("getDayTime"); }
    @Override public boolean isRaining() { throw no("isRaining"); }
    @Override public boolean isThundering() { throw no("isThundering"); }
    @Override public boolean gameRule(String name) { throw no("getGameRules"); }
    @Override public int gameRuleInt(String name) { throw no("getGameRules"); }
    @Override public int difficulty() { throw no("getDifficulty"); }
    @Override public String dimension() { throw no("dimension"); }
    @Override public boolean ultraWarm() { throw no("dimensionType"); }
    @Override public boolean withinBorder(Pos pos) { throw no("getWorldBorder"); }
    @Override public Object getBlockEntity(Pos pos) { throw no("getBlockEntity"); }
    @Override public void removeBlockEntity(Pos pos) { throw no("removeBlockEntity"); }
    @Override public void sendActionBar(BlockActor actor, org.domi.furnace.api.text.Text text) { throw no("sendActionBar"); }
    @Override public void popResource(Pos pos, ItemStack stack) { throw no("popResource"); }
    @Override public void popResourceFromFace(Pos pos, int dir, ItemStack stack) { throw no("popResourceFromFace"); }
}
