// 바닐라 ChestBlock: 상자. 이웃 상자와 이어 붙고(type single/left/right), 위가 막히면 열리지 않는다. 내용물은 블록 엔티티.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.world.ChestBlockEntity;

public class ChestBlock extends AbstractChestBlock {

    static final Prop FACING = B.prop("facing"), TYPE = B.prop("type");
    static final String SINGLE = "single", LEFT = "left", RIGHT = "right";

    public ChestBlock(int block) { super(block); }

    static int facing(int state) { return Dir.byName(FACING.valueName(state)); }

    private static String type(int state) { return TYPE.valueName(state); }

    /** 홑상자인가(type=single). */
    public boolean isSingle(int state) { return SINGLE.equals(type(state)); }

    /** 오른쪽 상자인가(더블 상자의 첫째 용기 — DoubleBlockCombiner.BlockType.FIRST). */
    public boolean isRightChest(int state) { return RIGHT.equals(type(state)); }

    /**
     * 짝이 되는 상자 위치(없으면 null): 홑상자가 아니고, 이어진 쪽 칸이 같은 블록·같은 방향·반대 type 일 때.
     * (DoubleBlockCombiner.combineWithNeigbour 의 이웃 검사. 막힘 검사는 호출 쪽이 한다.)
     */
    public Pos doubleChestPartner(BlockLevel level, Pos pos, int state) {
        if (SINGLE.equals(type(state))) return null;
        Pos np = pos.relative(getConnectedDirection(state));
        int ns = level.getBlockState(np);
        if (B.is(ns, block) && !SINGLE.equals(type(ns)) && !type(ns).equals(type(state)) && facing(ns) == facing(state)) return np;
        return null;
    }

    private static String opposite(String type) { return LEFT.equals(type) ? RIGHT : RIGHT.equals(type) ? LEFT : SINGLE; }

    /** getConnectedDirection: 짝이 있는 쪽(왼쪽이면 시계 방향, 아니면 반시계). */
    public static int getConnectedDirection(int state) {
        int f = facing(state);
        return LEFT.equals(type(state)) ? Dir.clockWise(f) : Dir.counterClockWise(f);
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        B.tickWaterlogged(state, level, pos);
        if (B.is(neighborState, block) && Dir.horizontal(dir)) {
            String nt = type(neighborState);
            if (SINGLE.equals(type(state)) && !SINGLE.equals(nt) && facing(state) == facing(neighborState)
                    && getConnectedDirection(neighborState) == Dir.opposite(dir)) {
                return TYPE.with(state, opposite(nt));
            }
        } else if (getConnectedDirection(state) == dir) {
            return TYPE.with(state, SINGLE);
        }
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        String type = SINGLE;
        int facing = Dir.opposite(ctx.horizontalDirection());
        boolean secondary = ctx.isSecondaryUseActive();
        int face = ctx.clickedFace();
        if (Dir.horizontal(face) && secondary) {
            int partner = candidatePartnerFacing(ctx, Dir.opposite(face));
            if (partner >= 0 && Dir.axis(partner) != Dir.axis(face)) {
                facing = partner;
                type = Dir.counterClockWise(partner) == Dir.opposite(face) ? RIGHT : LEFT;
            }
        }
        if (SINGLE.equals(type) && !secondary) {
            if (facing == candidatePartnerFacing(ctx, Dir.clockWise(facing))) type = LEFT;
            else if (facing == candidatePartnerFacing(ctx, Dir.counterClockWise(facing))) type = RIGHT;
        }
        int s = TYPE.with(FACING.with(defaultState(), Dir.NAME[facing]), type);
        return WATERLOGGED.with(s, B.waterAt(ctx));
    }

    /** candidatePartnerFacing: 그 쪽 칸이 홑상자면 그 방향, 아니면 -1. */
    private int candidatePartnerFacing(PlaceContext ctx, int dir) {
        int s = ctx.level().getBlockState(ctx.clickedPos().relative(dir));
        return B.is(s, block) && SINGLE.equals(type(s)) ? facing(s) : -1;
    }

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        // Containers.dropContentsOnDestroy: 다른 블록이 되면 내용물을 쏟고(열지 않은 전리품 상자는 먼저 채운다) 비교기에 알린다
        if (!B.is(newState, block) && level.getBlockEntity(pos) instanceof org.domi.furnace.server.world.ChestBlockEntity chest) {
            level.unpackContainerLoot(chest);
            level.dropContents(pos, chest.items);
            level.updateNeighbourForOutputSignal(pos, block);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    /** useWithoutItem: (짝까지) 막히지 않았으면 화면을 열고 통계. 결과는 언제나 CONSUME. */
    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        boolean blocked = isChestBlockedAt(level, pos);
        if (!blocked && !SINGLE.equals(type(state))) {
            Pos other = pos.relative(getConnectedDirection(state));
            int os = level.getBlockState(other);
            if (B.is(os, block) && !SINGLE.equals(type(os)) && isChestBlockedAt(level, other)) blocked = true;
        }
        if (!blocked) {
            player.openContainer(pos);
            player.awardStat(openChestStat());
            level.angerNearbyPiglins(player, true);
        }
        return InteractionResult.CONSUME;
    }

    protected String openChestStat() { return "minecraft:open_chest"; }

    /** isChestBlockedAt: 위 칸이 레드스톤 전도체이거나 위에 앉은 고양이가 있으면 막힘. */
    public static boolean isChestBlockedAt(BlockLevel level, Pos pos) { return isBlockedChestByBlock(level, pos) || level.isCatSittingAbove(pos); }

    /** isBlockedChestByBlock: 위 칸이 레드스톤 전도체면 막힘. */
    public static boolean isBlockedChestByBlock(BlockLevel level, Pos pos) {
        Pos above = pos.above();
        int s = level.getBlockState(above);
        return B.behavior(s).isRedstoneConductor(s, level, above);
    }

    /**
     * getAnalogOutputSignal: 합친 용기(getContainer, 막힘 검사 포함)의 찬 정도 — 막혔거나 짝이 막혔으면 0.
     * AbstractContainerMenu.getRedstoneSignalFromContainer.
     */
    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) {
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity a) || isChestBlockedAt(level, pos)) return 0;
        java.util.List<ItemStack[]> parts = new java.util.ArrayList<>();
        parts.add(a.items);
        if (!SINGLE.equals(type(state))) {
            Pos np = pos.relative(getConnectedDirection(state));
            int ns = level.getBlockState(np);
            if (B.is(ns, block) && !SINGLE.equals(type(ns)) && !type(ns).equals(type(state)) && facing(ns) == facing(state)) {
                if (isChestBlockedAt(level, np)) return 0;
                if (level.getBlockEntity(np) instanceof ChestBlockEntity b) parts.add(b.items);
            }
        }
        return redstoneSignalFromContainer(parts);
    }

    /** getRedstoneSignalFromContainer: 칸마다 개수 / min(99, 최대 묶음) 의 평균을 Mth.lerpDiscrete(f, 0, 15). */
    public static int redstoneSignalFromContainer(java.util.List<ItemStack[]> parts) {
        float f = 0.0F;
        int size = 0;
        for (ItemStack[] items : parts) {
            size += items.length;
            for (ItemStack s : items) {
                if (s != null && !s.isEmpty()) f += (float) s.count() / (float) Math.min(99, org.domi.furnace.server.item.ItemStackCodecs.maxStackSize(s));
            }
        }
        f /= (float) size;
        return (int) Math.floor(f * 14.0F) + (f > 0.0F ? 1 : 0);
    }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        // ChestBlock.tick: 여는 사람 수를 다시 센다(접속이 끊긴 사람 등)
        if (level.getBlockEntity(pos) instanceof org.domi.furnace.server.block.entity.ContainerBlockEntity c) c.recheckOpen(level);
    }
}
