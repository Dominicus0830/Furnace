// 바닐라 RespawnAnchorBlock: 리스폰 정박기. 발광석으로 충전(0~4). 네더에서만 리스폰 지점, 다른 차원에서 쓰면 터진다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.ItemInteractionResult;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.entity.ItemStack;

public class RespawnAnchorBlock extends org.domi.furnace.server.block.BlockBehavior {

    static final Prop CHARGE = B.prop("charges");

    public RespawnAnchorBlock(int block) { super(block); }

    private static boolean isChargeFuel(ItemStack s) { return B.holds(s, "minecraft:glowstone"); }

    private static boolean canBeCharged(int state) { return CHARGE.intValue(state) < 4; }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, int state, BlockLevel level, Pos pos, BlockActor player, int hand, Hit hit) {
        if (isChargeFuel(stack) && canBeCharged(state)) {
            charge(player, level, pos, state);
            if (!player.instabuild()) player.setItemInHand(hand, stack.withCount(stack.count() - 1));
            return ItemInteractionResult.CONSUME;
        }
        return hand == BlockActor.MAIN_HAND && isChargeFuel(player.itemInHand(BlockActor.OFF_HAND)) && canBeCharged(state)
                ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        if (CHARGE.intValue(state) == 0) return InteractionResult.PASS;
        if (!canSetSpawn(level)) {
            explode(level, pos);
            return InteractionResult.CONSUME;
        }
        // 리스폰 지점이 이 칸이 아니면 정하고(메시지) set_spawn 소리
        if (!player.respawnPointIs(level.dimension(), pos)) {
            player.setRespawnPosition(level.dimension(), pos, 0.0F, false, true);
            level.playSound(null, pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5, "minecraft:block.respawn_anchor.set_spawn", "blocks", 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }

    /** isWaterThatWouldFlow: 물이고, 원천이거나 양 2 이상이면서 아래 칸이 물이 아니다. */
    public static boolean isWaterThatWouldFlow(Pos pos, BlockLevel level) {
        int f = level.getFluidState(pos);
        if (!Fluids.isWater(f)) return false;
        if (Fluids.isSource(f)) return true;
        if (Fluids.amount(f) < 2.0F) return false;
        return !Fluids.isWater(level.getFluidState(pos.below()));
    }

    /**
     * explode: 블록을 지우고, 옆(북·동·남·서)에 흐를 물이 있거나 위가 물이면 이 칸의 폭발 저항을 물(100)로 해서
     * 가운데에서 BAD_RESPAWN_POINT 폭발(5.0, 불, BLOCK).
     */
    private static void explode(BlockLevel level, Pos pos) {
        int exploded = level.getBlockState(pos);
        level.removeBlock(pos, false);
        boolean water = false;
        for (int d : new int[] {Dir.NORTH, Dir.EAST, Dir.SOUTH, Dir.WEST}) {
            if (isWaterThatWouldFlow(pos.relative(d), level)) { water = true; break; }
        }
        boolean inWater = water || Fluids.isWater(level.getFluidState(pos.above()));
        if (level instanceof org.domi.furnace.server.block.ChunkLevel cl) {
            cl.explodeBadRespawnPoint(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5, inWater ? pos : null,
                    org.domi.furnace.server.block.BlockInfo.get().explosionResistance(org.domi.furnace.server.block.B.id("water")), exploded);
        }
    }

    /** canSetSpawn: 차원 타입의 respawnAnchorWorks(바닐라 타입 중 네더만). */
    public static boolean canSetSpawn(BlockLevel level) { return level.dimensionType().respawnAnchorWorks(); }

    /** charge: 충전 +1, BLOCK_CHANGE, 충전 소리. */
    public static void charge(BlockActor entity, BlockLevel level, Pos pos, int state) {
        int s = CHARGE.withInt(state, CHARGE.intValue(state) + 1);
        level.setBlock(pos, s, 3);
        level.gameEvent("minecraft:block_change", pos, entity, s);
        level.playSound(null, pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5, "minecraft:block.respawn_anchor.charge", "blocks", 1.0F, 1.0F);
    }

    @Override
    public boolean hasAnalogOutputSignal(int state) { return true; }

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) { return scaledCharge(state, 15); }

    /** 충전(0~4). */
    public static int charge(int state) { return CHARGE.intValue(state); }

    public static int withCharge(int state, int charge) { return CHARGE.withInt(state, charge); }

    /** RESPAWN_HORIZONTAL_OFFSETS: 북·서·남·동, 그다음 네 모서리. */
    private static final int[][] HORIZONTAL = {{0, 0, -1}, {-1, 0, 0}, {0, 0, 1}, {1, 0, 0}, {-1, 0, -1}, {1, 0, -1}, {-1, 0, 1}, {1, 0, 1}};

    /** RESPAWN_OFFSETS: 수평 8칸, 그 아래 8칸, 그 위 8칸, 바로 위. */
    private static final int[][] OFFSETS;

    static {
        OFFSETS = new int[25][];
        for (int i = 0; i < 8; i++) {
            OFFSETS[i] = HORIZONTAL[i];
            OFFSETS[8 + i] = new int[] {HORIZONTAL[i][0], HORIZONTAL[i][1] - 1, HORIZONTAL[i][2]};
            OFFSETS[16 + i] = new int[] {HORIZONTAL[i][0], HORIZONTAL[i][1] + 1, HORIZONTAL[i][2]};
        }
        OFFSETS[24] = new int[] {0, 1, 0};
    }

    /** findStandUpPosition(PLAYER, level, pos): 위험 블록을 피하는 자리부터, 없으면 위험을 따지지 않고. 없으면 null. */
    public static org.domi.furnace.server.entity.Vec3 findStandUpPosition(org.domi.furnace.server.block.ChunkLevel level, Pos pos) {
        var v = findStandUpPosition(level, pos, true);
        return v != null ? v : findStandUpPosition(level, pos, false);
    }

    private static org.domi.furnace.server.entity.Vec3 findStandUpPosition(org.domi.furnace.server.block.ChunkLevel level, Pos pos, boolean checkDangerous) {
        for (int[] o : OFFSETS) {
            var v = org.domi.furnace.server.entity.DismountHelper.findSafeDismountLocation(level, org.domi.furnace.server.entity.DismountHelper.PLAYER_WIDTH,
                    org.domi.furnace.server.entity.DismountHelper.PLAYER_HEIGHT, pos.offset(o[0], o[1], o[2]), checkDangerous);
            if (v != null) return v;
        }
        return null;
    }

    /** getScaledChargeLevel: floor(charge / 4 * max). */
    public static int scaledCharge(int state, int max) { return (int) Math.floor(CHARGE.intValue(state) / 4.0F * max); }
}
