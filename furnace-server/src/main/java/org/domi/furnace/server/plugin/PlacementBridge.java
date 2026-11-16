// 놓기 상태 계산 다리(T-204 3단계): 플러그인의 NMS BlockPlaceContext·레벨 객체로 서버 블록 동작의 getStateForPlacement·canBeReplaced·canSurvive 를 돌린다.
// FAWE PlacementStateProcessor 는 편집 중인 내용을 답하는 레벨(PaperweightLevelProxy)과 값을 바꿔 끼우는 문맥으로 부른다 — 그 객체의 가상 메서드로 읽어야
// Paper 와 같은 답이 나온다. 서버 자신의 월드면(재정의 없음) 그 칸의 주인 스레드에서 서버 레벨을 직접 읽는다.
package org.domi.furnace.server.plugin;

import org.domi.furnace.api.internal.ChunkInternals.PlacementContext;
import org.domi.furnace.api.internal.ChunkInternals.PlacementLevel;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.block.RedstoneTorchToggles;
import org.domi.furnace.server.block.Shape;
import org.domi.furnace.server.entity.ItemStack;

final class PlacementBridge {

    private PlacementBridge() {}

    /** 서버 자신의 월드이고 이 스레드가 그 칸의 주인이면 서버 레벨, 아니면 그 객체의 가상 메서드로 읽는 레벨. */
    static BlockLevel level(PlacementLevel pl, int x, int z) {
        var w = pl.serverWorld();
        if (w != null) {
            var h = (WorldHandle) w;
            if (h.handle().isChunkLoaded(x >> 4, z >> 4) && org.domi.furnace.server.world.RegionBorrow.claimChunk(h.handle(), x >> 4, z >> 4)) {
                var r = h.ownedRegion(new org.domi.furnace.api.world.BlockPos(x, 0, z), "Block#getStateForPlacement");
                if (r != null && r.chunkLoaded(new org.domi.furnace.server.world.ChunkPos(x >> 4, z >> 4))) return r.level();
            }
        }
        return new ReadLevel(pl);
    }

    static int stateForPlacement(int block, PlacementContext ctx) { return BlockBehaviors.of(block).getStateForPlacement(new Context(ctx)); }

    static boolean canBeReplaced(int state, PlacementContext ctx) { return BlockBehaviors.of(state).canBeReplaced(state, new Context(ctx)); }

    static boolean canSurvive(int state, PlacementLevel pl, int x, int y, int z) {
        return BlockBehaviors.of(state).canSurvive(state, level(pl, x, z), new Pos(x, y, z));
    }

    /**
     * NMS BlockPlaceContext 의 가상 메서드로 답하는 서버 문맥. 놓는 플레이어는 없다(호환 계층이 막는다). 값은 쓰일 때 묻는다 — 바닐라 블록 코드가 묻는 것만
     * 문맥에 닿아야 한다(FAWE 문맥은 생성자 안에서 canBeReplaced 가 불릴 때 클릭 칸 등이 아직 비어 있다). 레벨도 처음 쓰일 때 고른다.
     */
    static final class Context extends PlaceContext {

        private final PlacementContext c;
        private BlockLevel lv;

        Context(PlacementContext c) {
            super(null, 0, new Hit(new Pos(0, 0, 0), 0, 0, 0, 0, false), false);
            this.c = c;
        }

        @Override
        public BlockLevel level() {
            BlockLevel l = lv;
            if (l == null) {
                var pl = c.level();
                if (pl.serverWorld() == null) {
                    l = new ReadLevel(pl);
                } else {
                    Pos p = clickedPos();
                    l = PlacementBridge.level(pl, p.x(), p.z());
                }
                lv = l;
            }
            return l;
        }

        @Override public int hand() { return c.hand(); }
        @Override public BlockActor player() { return null; }
        @Override public ItemStack itemInHand() { return ApiConvert.item(c.itemInHand()); }
        @Override public Hit hit() { throw new UnsupportedOperationException("the clicked block of a plugin placement context"); }
        @Override public int clickedFace() { return c.clickedFace(); }
        @Override public boolean isInside() { return c.isInside(); }
        @Override public double clickX() { return c.clickLocation()[0]; }
        @Override public double clickY() { return c.clickLocation()[1]; }
        @Override public double clickZ() { return c.clickLocation()[2]; }
        @Override public int horizontalDirection() { return c.horizontalDirection(); }
        @Override public boolean isSecondaryUseActive() { return c.isSecondaryUseActive(); }
        @Override public float rotation() { return c.rotation(); }

        @Override
        public Pos clickedPos() {
            int[] p = c.clickedPos();
            return new Pos(p[0], p[1], p[2]);
        }

        @Override public boolean canPlace() { return c.canPlace(); }
        @Override public boolean replacingClickedOnBlock() { return c.replacingClickedOnBlock(); }
        @Override public int nearestLookingDirection() { return c.nearestLookingDirection(); }
        @Override public int nearestLookingVerticalDirection() { return c.nearestLookingVerticalDirection(); }
        @Override public int[] nearestLookingDirections() { return c.nearestLookingDirections(); }
    }

    /**
     * 읽기만 하는 레벨 — 블록·유체·블록 엔티티·높이·월드 경계는 그 객체의 가상 메서드, 신호는 그 읽기로 서버의 같은 계산(BlockLevel 기본 메서드).
     * 그 밖의 요청은 UnsupportedOperationException — 호환 계층이 UnsupportedNmsCall 로 바꾼다. 바닐라 놓기 계산에서 읽기 밖으로 나가는 것은 둘뿐이다(시험이 블록
     * 전수로 확인): 덩굴(weeping·twisting·cave vines)의 나이 난수(level.random)와 산호 블록의 예약 틱(물이 없으면 scheduleTick + getRandom). Paper 에서
     * FAWE 프록시는 생성자 없이 만든 ServerLevel 이라 그 필드가 null 이어서 NullPointerException 이 나고, 여기서는 UnsupportedNmsCall 이다(가정: 둘 다 예외라
     * FAWE 쪽 처리가 같다 — 4단계 실측에서 확인).
     */
    static final class ReadLevel implements BlockLevel {

        private final PlacementLevel l;

        ReadLevel(PlacementLevel l) { this.l = l; }

        private static UnsupportedOperationException no(String what) {
            return new UnsupportedOperationException(what + " on a plugin level during placement");
        }

        @Override public int getBlockState(Pos pos) { return l.blockStateAt(pos.x(), pos.y(), pos.z()); }
        @Override public int getFluidState(Pos pos) { return l.fluidStateAt(pos.x(), pos.y(), pos.z()); }
        @Override public int minBuildHeight() { return l.minBuildHeight(); }
        @Override public int maxBuildHeight() { return l.maxBuildHeight(); }

        /**
         * 그 객체의 getBlockEntity 저장 꼴로 그 칸 상태의 떨어진 서버 블록 엔티티를 만든다(FAWE 프록시도 그 칸 상태로 newBlockEntity + loadWithComponents).
         * 저장 꼴에 없는 실행 중 값(상자를 연 수 등)은 비어 있다 — 서버 자신의 월드는 이 길을 타지 않는다.
         */
        @Override
        public Object getBlockEntity(Pos pos) {
            var nbt = l.blockEntityAt(pos.x(), pos.y(), pos.z());
            return nbt == null ? null : ChunkInternalsImpl.detachedBlockEntity(getBlockState(pos), pos, nbt);
        }

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
        @Override public void scheduleTick(Pos pos, int block, int delay, int priority) { throw no("scheduleTick"); }
        @Override public void scheduleFluidTick(Pos pos, int fluidType, int delay) { throw no("scheduleTick"); }
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
        @Override public boolean withinBorder(Pos pos) { return l.withinBorder(pos.x(), pos.y(), pos.z()); }
        @Override public void removeBlockEntity(Pos pos) { throw no("removeBlockEntity"); }
        @Override public void sendActionBar(BlockActor actor, org.domi.furnace.api.text.Text text) { throw no("sendActionBar"); }
        @Override public void popResource(Pos pos, ItemStack stack) { throw no("popResource"); }
        @Override public void popResourceFromFace(Pos pos, int dir, ItemStack stack) { throw no("popResourceFromFace"); }
    }

}
