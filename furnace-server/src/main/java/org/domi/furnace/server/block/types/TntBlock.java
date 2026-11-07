// 바닐라 TntBlock: 신호를 받거나 불·부싯돌로 점화되면 점화된 TNT 엔티티가 된다. unstable 이면 부수기만 해도 점화.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.ItemInteractionResult;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.entity.ItemStack;

public class TntBlock extends BlockBehavior {

    private static final Prop UNSTABLE = B.prop("unstable");

    public TntBlock(int block) { super(block); }

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) {
        if (!B.is(oldState, B.block(state)) && level.hasNeighborSignal(pos)) {
            prime(level, pos, redstone(level, pos, state));
            level.removeBlock(pos, false);
        }
    }

    @Override
    public void neighborChanged(int state, BlockLevel level, Pos pos, int sourceBlock, Pos sourcePos, boolean moved) {
        if (level.hasNeighborSignal(pos)) {
            prime(level, pos, redstone(level, pos, state));
            level.removeBlock(pos, false);
        }
    }

    /** 원인 사슬 [이 TNT 칸 redstone](추적이 꺼져 있으면 null). 신호를 낸 칸은 따라가지 않는다. */
    private static org.domi.furnace.api.event.cause.CauseChain redstone(BlockLevel level, Pos pos, int state) {
        return org.domi.furnace.server.plugin.Causes.tracking(level) ? org.domi.furnace.server.plugin.Causes.block(level.dimension(), pos, state, org.domi.furnace.api.event.cause.CauseEntry.REDSTONE, null) : null;
    }

    @Override
    public int playerWillDestroy(BlockLevel level, Pos pos, int state, BlockActor player) {
        if (!player.isCreative() && UNSTABLE.is(state)) prime(level, pos, org.domi.furnace.server.plugin.Causes.tracking(level) ? org.domi.furnace.server.plugin.Causes.of(player.handle(), org.domi.furnace.api.event.cause.CauseEntry.BREAK) : null);   // 주인은 바닐라처럼 없음
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * TntBlock.wasExploded: 폭발에 휘말리면 짧은 도화선(10~29틱)의 점화된 TNT(생성 난수는 레벨 난수).
     * 주인은 폭발의 간접 원인(Explosion.getIndirectSourceEntity — 앞 TNT 의 주인, 터진 몹, 투사체를 쏜 생물).
     */
    @Override
    public void wasExploded(BlockLevel level, Pos pos, org.domi.furnace.server.block.Explosion explosion) {
        level.spawnPrimedTntOwned(pos, explosion.indirectSourceEntity(), -2, org.domi.furnace.server.plugin.Causes.tracking(level) ? explosion.cause() : null);
    }

    /** 원인 모를 점화(바닐라 TntBlock.prime). */
    public static void prime(BlockLevel level, Pos pos) { explode(level, pos, null, null); }

    /** why: 켠 원인 사슬(추적이 꺼져 있으면 null). */
    public static void prime(BlockLevel level, Pos pos, org.domi.furnace.api.event.cause.CauseChain why) { explode(level, pos, null, why); }

    /** explode: 점화된 TNT 엔티티(생성 때 레벨 난수 1회 — 처음 튀는 방향) + 소리 + PRIME_FUSE. */
    private static void explode(BlockLevel level, Pos pos, BlockActor igniter, org.domi.furnace.api.event.cause.CauseChain why) {
        level.spawnPrimedTnt(pos, igniter, -1, why);
        level.playSound(null, pos.x() + 0.5, pos.y(), pos.z() + 0.5, "minecraft:entity.tnt.primed", "blocks", 1.0F, 1.0F);
        level.gameEvent("minecraft:prime_fuse", pos, igniter, -1);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, int state, BlockLevel level, Pos pos, BlockActor player, int hand, Hit hit) {
        if (!B.holds(stack, "minecraft:flint_and_steel") && !B.holds(stack, "minecraft:fire_charge")) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        explode(level, pos, player, org.domi.furnace.server.plugin.Causes.tracking(level) ? org.domi.furnace.server.plugin.Causes.of(player.handle(), org.domi.furnace.api.event.cause.CauseEntry.IGNITE) : null);
        level.setBlock(pos, B.defaultState("air"), 11);
        // TODO(아이템): 부싯돌 내구도 1(hurtAndBreak) / 화염구 1개 소모(consume), 통계 used
        return ItemInteractionResult.CONSUME;
    }

    /** onProjectileHit: 불붙은 투사체이고 mayInteract 면 쏜 쪽(생물일 때)을 주인으로 점화하고 블록을 없앤다. */
    @Override
    public void onProjectileHit(BlockLevel level, int state, org.domi.furnace.server.entity.Projectile.Hit hit,
                                org.domi.furnace.server.entity.Projectile projectile) {
        Pos pos = hit.blockPos();
        org.domi.furnace.server.entity.Targetable owner = projectile.getOwner();
        if (projectile.isOnFire() && projectile.mayInteract(level, pos)) {
            boolean living = owner instanceof org.domi.furnace.server.entity.LivingEntity || owner instanceof org.domi.furnace.server.entity.Player;
            org.domi.furnace.server.entity.Targetable igniter = living ? owner : null;
            level.spawnPrimedTntOwned(pos, igniter, -1, org.domi.furnace.server.plugin.Causes.tracking(level) ? org.domi.furnace.server.plugin.Causes.of(projectile, org.domi.furnace.api.event.cause.CauseEntry.HIT) : null);
            level.playSound(null, pos.x() + 0.5, pos.y(), pos.z() + 0.5, "minecraft:entity.tnt.primed", "blocks", 1.0F, 1.0F);
            level.gameEventFrom("minecraft:prime_fuse", pos, igniter, -1);
            level.removeBlock(pos, false);
        }
    }
}
