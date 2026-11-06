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
import org.domi.furnace.server.block.entity.CampfireBlockEntity;
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
    public org.domi.furnace.server.block.entity.BlockEntity newBlockEntity(Pos pos, int state) { return new CampfireBlockEntity(pos, state); }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, int state, BlockLevel level, Pos pos, BlockActor player, int hand, Hit hit) {
        if (level.getBlockEntity(pos) instanceof CampfireBlockEntity be) {
            var recipe = be.getCookableRecipe(stack, level);
            if (recipe != null) {
                if (be.placeFood(player, hand, stack, recipe.recipe().cookingTime(), level)) {
                    player.awardStat("minecraft:interact_with_campfire");
                    return ItemInteractionResult.SUCCESS;
                }
                return ItemInteractionResult.CONSUME;
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** entityInside: 켜져 있으면 생물에게 불 피해(fire_damage). 플레이어는 Region 이 이동할 때 {@link #entityInsidePlayer} 로. */
    @Override
    public void entityInside(int state, BlockLevel level, Pos pos, org.domi.furnace.server.entity.Entity entity) {
        if (LIT.is(state) && entity instanceof org.domi.furnace.server.entity.LivingEntity) {
            entity.hurt(org.domi.furnace.server.entity.Entity.Damage.IN_FIRE, (float) fireDamage);
        }
    }

    /** entityInside 의 플레이어 쪽(플레이어는 Entity 가 아니다): 켜져 있으면 campfire 피해 fire_damage. */
    public void entityInsidePlayer(int state, org.domi.furnace.server.entity.PlayerVitals vitals) {
        if (LIT.is(state)) vitals.hurt(org.domi.furnace.server.entity.damage.DamageType.CAMPFIRE, (float) fireDamage);
    }

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        if (!B.is(newState, block)) {
            if (level.getBlockEntity(pos) instanceof CampfireBlockEntity be) level.dropContents(pos, be.items);   // Containers.dropContents
            super.onRemove(state, level, pos, newState, moved);
        }
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

    /** dowse(entity, level, pos, state): 끄기(블록 엔티티는 갱신만) + BLOCK_CHANGE. 켜진 상태 변경은 부르는 쪽이. */
    public static void dowse(BlockActor entity, BlockLevel level, Pos pos, int state) {
        // 바닐라 CampfireBlockEntity.dowse 는 클라이언트 갱신만 한다(음식은 그대로 — 쏟는 것은 블록이 바뀔 때).
        if (level.getBlockEntity(pos) instanceof CampfireBlockEntity be) be.setChanged(level);
        level.gameEvent("minecraft:block_change", pos, entity, -1);
    }

    /** placeLiquid: 물 원천이 들어오면 켜져 있던 불은 끄는 소리·dowse 후, 잠기고 꺼진 상태로. */
    @Override
    public boolean placeLiquid(BlockLevel level, Pos pos, int state, int fluidState) {
        if (WATERLOGGED.is(state) || org.domi.furnace.server.block.Fluids.type(fluidState) != org.domi.furnace.server.block.Fluids.T_WATER) return false;
        if (LIT.is(state)) {
            level.playSound(null, pos, "minecraft:entity.generic.extinguish_fire", "blocks", 1.0F, 1.0F);
            dowse(null, level, pos, state);
        }
        level.setBlock(pos, LIT.with(WATERLOGGED.with(state, true), false), 3);
        level.scheduleFluidTick(pos, org.domi.furnace.server.block.Fluids.T_WATER, org.domi.furnace.server.block.Fluids.tickDelay(fluidState, level.ultraWarm()));
        return true;
    }

    /** onProjectileHit: 불붙은 투사체이고 mayInteract 이며 꺼져 있고 물에 잠기지 않았으면 켠다(플래그 11). */
    @Override
    public void onProjectileHit(BlockLevel level, int state, org.domi.furnace.server.entity.Projectile.Hit hit,
                                org.domi.furnace.server.entity.Projectile projectile) {
        Pos pos = hit.blockPos();
        if (projectile.isOnFire() && projectile.mayInteract(level, pos) && !LIT.is(state) && !WATERLOGGED.is(state)) {
            level.setBlock(pos, LIT.with(state, true), 11);
        }
    }

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
