// 바닐라 CampfireBlock: 모닥불(lit·signal_fire·waterlogged·facing). 아래가 건초 더미면 신호 연기, 물에 잠기면 꺼진다. 굽기는 블록 엔티티.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.ItemInteractionResult;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.entity.ItemStack;

public class CampfireBlock extends BaseEntityBlock {

    static final Prop LIT = B.prop("lit"), SIGNAL_FIRE = B.prop("signal_fire"), FACING = B.prop("facing");
    private static final int HAY_BLOCK = B.id("hay_block");

    private final boolean spawnParticles;
    private final int fireDamage;

    public CampfireBlock(int block) {
        super(block);
        var p = Blocks.get().params(block);
        this.spawnParticles = p.get("spawn_particles").getAsBoolean();
        this.fireDamage = p.get("fire_damage").getAsInt();
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, int state, BlockLevel level, Pos pos, BlockActor player, int hand, Hit hit) {
        // TODO(블록 엔티티): 모닥불 블록 엔티티가 있고 그 아이템의 모닥불 요리법이 있으면 빈 칸에 올리고(통계 interact_with_campfire) SUCCESS,
        //   올릴 칸이 없으면 CONSUME. 블록 엔티티가 없으면 바닐라도 PASS_TO_DEFAULT.
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    // TODO(엔티티): entityInside — 켜져 있으면 생물에게 불 피해(fire_damage).

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        // TODO(블록 엔티티): 다른 블록이 되면 올린 음식 쏟기(Containers.dropContents).
        if (!B.is(newState, block)) super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        boolean water = B.waterAt(ctx);
        int s = WATERLOGGED.with(defaultState(), water);
        s = SIGNAL_FIRE.with(s, isSmokeSource(ctx.level().getBlockState(ctx.clickedPos().below())));
        s = LIT.with(s, !water);
        return FACING.with(s, Dir.NAME[ctx.horizontalDirection()]);
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        B.tickWaterlogged(state, level, pos);
        return dir == Dir.DOWN ? SIGNAL_FIRE.with(state, isSmokeSource(neighborState)) : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    private static boolean isSmokeSource(int state) { return B.is(state, HAY_BLOCK); }

    /** dowse(entity, level, pos, state): 끄기(블록 엔티티는 올린 음식을 쏟는다) + BLOCK_CHANGE. 켜진 상태 변경은 부르는 쪽이. */
    public static void dowse(BlockActor entity, BlockLevel level, Pos pos, int state) {
        // TODO(블록 엔티티): CampfireBlockEntity.dowse — 올린 음식 쏟기.
        level.gameEvent("minecraft:block_change", pos, entity, -1);
    }

    // TODO(3-4d 유체): placeLiquid — 물이 들어오면 켜져 있으면 extinguish 소리·dowse 후 잠기고 꺼진다.
    // TODO(엔티티): onProjectileHit — 불붙은 투사체가 닿으면 켠다(플래그 11).

    /** isSmokeyPos: 아래 5칸 안에 켜진 모닥불이 있는가(가운데 기둥을 막는 블록이 있으면 그 바로 아래만 본다 — 벌집 연기 판정). */
    public static boolean isSmokeyPos(BlockLevel level, Pos pos) {
        for (int i = 1; i <= 5; i++) {
            Pos p = pos.below(i);
            int s = level.getBlockState(p);
            if (isLitCampfire(s)) return true;
            // 바닐라는 이 칸 모양을 pos(원래 칸) 기준으로 묻는다.
            boolean blocked = B.behavior(s).getCollisionShape(s, level, pos, null).intersects(6 / 16.0, 0, 6 / 16.0, 10 / 16.0, 1, 10 / 16.0);
            if (blocked) return isLitCampfire(level.getBlockState(p.below()));
        }
        return false;
    }

    public static boolean isLitCampfire(int state) { return LIT.has(state) && B.isTag(state, "minecraft:campfires") && LIT.is(state); }

    /** canLight: 잠기지 않은 꺼진 모닥불. */
    public static boolean canLight(int state) {
        return B.isTag(state, "minecraft:campfires") && WATERLOGGED.has(state) && LIT.has(state) && !WATERLOGGED.is(state) && !LIT.is(state);
    }

    public boolean spawnParticles() { return spawnParticles; }

    public int fireDamage() { return fireDamage; }
}
