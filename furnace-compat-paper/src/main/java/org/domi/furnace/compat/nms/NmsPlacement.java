package org.domi.furnace.compat.nms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtBridge;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.domi.furnace.api.internal.ChunkInternals;
import org.domi.furnace.compat.paper.UnsupportedFeature;

/**
 * 놓기 상태 계산(T-204 3단계 — FAWE PlacementStateProcessor, {@code //fixblocks}): {@code Block#getStateForPlacement}·{@code BlockState#canBeReplaced(ctx)}·
 * {@code canSurvive} 를 서버 블록 동작의 같은 메서드로 돌린다. 레벨·문맥 값은 플러그인이 넘긴 NMS 객체의 가상 메서드로 읽는다 — FAWE 처럼 레벨을 상속해
 * 편집 중인 내용을 답하게 했으면 그 답으로 계산한다(Paper 와 같다). 서버 자신의 월드(재정의 없는 {@code ServerLevel})면 서버가 그 칸의 주인 스레드에서
 * 직접 읽는다. 놓는 플레이어가 있는 문맥은 아직 없다(UnsupportedNmsCall). 읽기 밖의 요청(쓰기·소리·시간 …)이 나오면 UnsupportedNmsCall —
 * 바닐라 놓기 계산은 읽기만 한다(시험이 블록 전수로 확인).
 */
public final class NmsPlacement {

    private NmsPlacement() {}

    /** Block#getStateForPlacement — null 이면 null. */
    public static BlockState stateForPlacement(net.minecraft.world.level.block.Block block, BlockPlaceContext ctx) {
        int s = guard("Block#getStateForPlacement", () -> NmsChunks.bridge().stateForPlacement(block.defaultBlockState().furnaceStateId(), context(ctx)));
        return s < 0 ? null : net.minecraft.world.level.block.Block.stateById(s);
    }

    public static boolean canBeReplaced(BlockState state, BlockPlaceContext ctx) {
        return guard("BlockState#canBeReplaced", () -> NmsChunks.bridge().canBeReplaced(state.furnaceStateId(), context(ctx)) ? 1 : 0) != 0;
    }

    public static boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos) {
        return guard("BlockState#canSurvive", () -> NmsChunks.bridge().canSurvive(state.furnaceStateId(), level(level), pos.getX(), pos.getY(), pos.getZ()) ? 1 : 0) != 0;
    }

    private static int guard(String what, java.util.function.IntSupplier call) {
        try {
            return call.getAsInt();
        } catch (UnsupportedOperationException e) {
            if (e instanceof UnsupportedFeature) throw e;
            throw new UnsupportedNmsCall(what, e.getMessage());
        }
    }

    // ------------------------------------------------------------------ 다리

    static ChunkInternals.PlacementContext context(BlockPlaceContext ctx) {
        if (ctx.getPlayer() != null) {
            throw new UnsupportedNmsCall("BlockPlaceContext with a player", "placement state of a player's placement context is not available yet (only contexts without a player)");
        }
        return new Ctx(ctx);
    }

    static ChunkInternals.PlacementLevel level(BlockGetter level) {
        if (level.getClass() == net.minecraft.server.level.ServerLevel.class) {
            var w = ((net.minecraft.server.level.ServerLevel) level).getWorld();
            if (w != null) return new Lvl(level, w.furnaceHandle());
        }
        return new Lvl(level, null);
    }

    /** 유체 상태 → 서버 꼴(종류 | 양 &lt;&lt; 3 | 떨어짐 &lt;&lt; 7). */
    static int fluid(FluidState f) {
        int type = fluidType(f.getType());
        if (type == 0) return 0;
        return type | f.getAmount() << 3 | (f.hasProperty(FlowingFluid.FALLING) && f.getValue(FlowingFluid.FALLING) ? 1 << 7 : 0);
    }

    /** 유체 종류 번호(레지스트리 순서 — 0 빈 것, 1 흐르는 물, 2 물, 3 흐르는 용암, 4 용암). */
    public static int fluidType(net.minecraft.world.level.material.Fluid t) {
        int type = t == Fluids.EMPTY ? 0 : t == Fluids.FLOWING_WATER ? 1 : t == Fluids.WATER ? 2 : t == Fluids.FLOWING_LAVA ? 3 : t == Fluids.LAVA ? 4 : -1;
        if (type < 0) throw new IllegalStateException("unknown fluid " + t);
        return type;
    }

    private record Lvl(BlockGetter l, org.domi.furnace.api.world.World world) implements ChunkInternals.PlacementLevel {

        @Override public int blockStateAt(int x, int y, int z) { return l.getBlockState(new BlockPos(x, y, z)).furnaceStateId(); }

        @Override public int fluidStateAt(int x, int y, int z) { return fluid(l.getFluidState(new BlockPos(x, y, z))); }

        @Override
        public org.domi.furnace.api.nbt.NbtCompound blockEntityAt(int x, int y, int z) {
            var be = l.getBlockEntity(new BlockPos(x, y, z));
            return be == null ? null : (org.domi.furnace.api.nbt.NbtCompound) NbtBridge.toFurnace(be.saveWithoutMetadata(net.minecraft.core.RegistryAccess.SERVER));
        }

        @Override public int minBuildHeight() { return l.getMinBuildHeight(); }

        @Override public int maxBuildHeight() { return l.getMaxBuildHeight(); }

        @Override
        public boolean withinBorder(int x, int y, int z) {
            if (!(l instanceof net.minecraft.world.level.Level lv)) throw new UnsupportedOperationException("getWorldBorder on a level that is not a Level");
            return lv.getWorldBorder().isWithinBounds(new BlockPos(x, y, z));
        }

        @Override public org.domi.furnace.api.world.World serverWorld() { return world; }
    }

    private record Ctx(BlockPlaceContext c) implements ChunkInternals.PlacementContext {

        @Override public ChunkInternals.PlacementLevel level() { return NmsPlacement.level(c.getLevel()); }

        @Override
        public int[] clickedPos() {
            BlockPos p = c.getClickedPos();
            return new int[] {p.getX(), p.getY(), p.getZ()};
        }

        @Override public int clickedFace() { return c.getClickedFace().get3DDataValue(); }

        @Override
        public double[] clickLocation() {
            var v = c.getClickLocation();
            return new double[] {v.x, v.y, v.z};
        }

        @Override public boolean isInside() { return c.isInside(); }

        @Override public org.domi.furnace.api.item.ItemStack itemInHand() { return c.getItemInHand().furnaceValue(); }

        @Override public int hand() { return c.getHand() == net.minecraft.world.InteractionHand.OFF_HAND ? 1 : 0; }

        @Override public boolean replacingClickedOnBlock() { return c.replacingClickedOnBlock(); }

        @Override public boolean canPlace() { return c.canPlace(); }

        @Override public int nearestLookingDirection() { return c.getNearestLookingDirection().get3DDataValue(); }

        @Override public int nearestLookingVerticalDirection() { return c.getNearestLookingVerticalDirection().get3DDataValue(); }

        @Override
        public int[] nearestLookingDirections() {
            Direction[] d = c.getNearestLookingDirections();
            int[] out = new int[d.length];
            for (int i = 0; i < d.length; i++) out[i] = d[i].get3DDataValue();
            return out;
        }

        @Override public int horizontalDirection() { return c.getHorizontalDirection().get3DDataValue(); }

        @Override public boolean isSecondaryUseActive() { return c.isSecondaryUseActive(); }

        @Override public float rotation() { return c.getRotation(); }
    }
}
