// 바닐라 TridentItem: 오래 쓰기(72000틱) 시작, 놓을 때 10틱 이상 당겼으면 던진다(속도 2.5, 흩어짐 1, 내구도 1).
// TODO(급류): riptide 는 플레이어 물·비 판정과 서버 쪽 플레이어 이동(회전 공격)이 없어 쓰기 시작 자체를 막는다(바닐라도 물·비 밖에선 실패).
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockBreaking;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.item.ItemTools;

public final class Tridents {
    private Tridents() {}

    public static final int USE_TICKS = 72000, THROW_THRESHOLD = 10;
    public static final float SHOOT_POWER = 2.5F;

    public static boolean isTrident(ItemStack s) { return ItemTools.itemName(s).equals("minecraft:trident"); }

    /** isTooDamagedToUse: 피해 ≥ 최대 − 1. */
    static boolean tooDamaged(ItemStack s) { return ItemTools.damage(s) >= ItemTools.maxDamage(s) - 1; }

    /** getTridentSpinAttackStrength: 급류 1.5 + 0.75 × (레벨 − 1). */
    static float spinStrength(ItemStack s) {
        int lvl = ItemTools.enchantmentLevel(s, "minecraft:riptide");
        return lvl > 0 ? 1.5F + 0.75F * (lvl - 1) : 0.0F;
    }

    /** use: 너무 닳았거나 급류인데 물·비 밖이면 실패, 아니면 쓰기 시작. */
    static boolean use(BlockActor actor, Player p, int hand) {
        ItemStack s = actor.itemInHand(hand);
        if (tooDamaged(s)) return true;
        if (spinStrength(s) > 0.0F) return true;   // TODO(급류): isInWaterOrRain
        RangedWeapons.start(p, hand, USE_TICKS);
        return true;
    }

    /** releaseUsing: 10틱 이상이면 내구도 1, ThrownTrident 를 shootFromRotation(0, 2.5, 1), 창조면 창조 전용 줍기, 소리, 손에서 뺀다, 통계. */
    static void release(ChunkLevel level, BlockActor actor, Player p, int hand, int remaining) {
        int ticks = USE_TICKS - remaining;
        if (ticks < THROW_THRESHOLD) return;
        ItemStack s = actor.itemInHand(hand);
        float spin = spinStrength(s);
        if (spin > 0.0F) return;   // TODO(급류)
        if (tooDamaged(s)) return;
        BlockBreaking.hurtAndBreak(level, actor, hand, s, 1);
        ItemStack cur = actor.itemInHand(hand);
        var t = new ThrownTrident(level, level.nextEntityId(), p, cur);
        t.shootFromRotation(p.pitch(), p.yaw(), 0.0F, SHOOT_POWER, 1.0F, p.knownMovement(), p.onGround());
        if (p.hasInfiniteMaterials()) t.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        level.addFreshEntity(t);
        level.playSound(null, t.x(), t.y(), t.z(), "item.trident.throw", "players", 1.0F, 1.0F);
        if (!p.hasInfiniteMaterials()) actor.setItemInHand(hand, ItemStack.EMPTY);
        p.awardStat("minecraft:used:minecraft:trident", 1);
    }
}
