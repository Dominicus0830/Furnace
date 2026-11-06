// 바닐라 FishingRodItem.use: 낚시찌가 있으면 거두고(내구도·소리), 없으면 던진다(소리·낚시찌·통계). 미끼 = 기다림 감소, 바다의 행운 = 행운.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockBreaking;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.item.ItemTools;

public final class FishingRods {
    private FishingRods() {}

    /** EnchantmentHelper.getFishingTimeReduction × 20: 미끼 fishing_time_reduction(5 + 5 × (레벨 − 1) 초). */
    public static int lureSpeed(ItemStack rod) {
        int lvl = ItemTools.enchantmentLevel(rod, "minecraft:lure");
        float seconds = lvl > 0 ? 5.0F + 5.0F * (lvl - 1) : 0.0F;
        return (int) (seconds * 20.0F);
    }

    /** EnchantmentHelper.getFishingLuckBonus: 바다의 행운 fishing_luck_bonus(1 + (레벨 − 1)). */
    public static int luck(ItemStack rod) {
        int lvl = ItemTools.enchantmentLevel(rod, "minecraft:luck_of_the_sea");
        return lvl > 0 ? (int) (1.0F + 1.0F * (lvl - 1)) : 0;
    }

    public static boolean use(ChunkLevel level, BlockActor actor, Player p, int hand) {
        ItemStack stack = actor.itemInHand(hand);
        if (p.fishing != null) {
            int dmg = p.fishing.retrieve(stack);
            BlockBreaking.hurtAndBreak(level, actor, hand, actor.itemInHand(hand), dmg);
            level.playSound(null, p.x(), p.y(), p.z(), "entity.fishing_bobber.retrieve", "neutral", 1.0F,
                    0.4F / (level.random().nextFloat() * 0.4F + 0.8F));
            playerEvent(level, p, org.domi.furnace.server.world.gameevent.GameEvent.ITEM_INTERACT_FINISH);
        } else {
            level.playSound(null, p.x(), p.y(), p.z(), "entity.fishing_bobber.throw", "neutral", 0.5F,
                    0.4F / (level.random().nextFloat() * 0.4F + 0.8F));
            var hook = new FishingHook(p, level, level.nextEntityId(), luck(stack), lureSpeed(stack));
            if (level.gameEvents().playerFish(p, hook, null, null, org.domi.furnace.server.plugin.GameEvents.FISH_FISHING, 0) < 0) {   // 플러그인 PlayerFishEvent(FISHING) 취소
                p.fishing = null;
                return false;
            }
            level.addFreshEntity(hook);
            p.awardStat("minecraft:used:minecraft:fishing_rod", 1);
            playerEvent(level, p, org.domi.furnace.server.world.gameevent.GameEvent.ITEM_INTERACT_START);
        }
        return true;
    }

    /** player.gameEvent(event): 플레이어 자리, 플레이어 출처. */
    private static void playerEvent(ChunkLevel level, Player p, org.domi.furnace.server.world.gameevent.GameEvent event) {
        level.gameEvent(event, new Vec3(p.x(), p.y(), p.z()), org.domi.furnace.server.world.gameevent.GameEventContext.of(p));
    }
}
