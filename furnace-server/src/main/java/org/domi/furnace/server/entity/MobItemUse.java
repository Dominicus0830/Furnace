// 플레이어가 아닌 생물의 아이템 사용(바닐라 Item·ItemStack 의 getUseDuration·getUseAnimation·useOnRelease·finishUsingItem). 플레이어는 Consuming·RangedWeapons 가 맡는다.
package org.domi.furnace.server.entity;

import java.util.List;
import java.util.Map;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.world.gameevent.GameEvent;
import org.domi.furnace.server.world.gameevent.GameEventContext;

public final class MobItemUse {
    private MobItemUse() { }

    /** 바닐라 UseAnim 중 몹 사용 효과가 쓰는 것. */
    public enum Anim { NONE, EAT, DRINK, BLOCK, BOW, SPEAR, CROSSBOW, SPYGLASS, TOOT_HORN, BRUSH }

    private static String name(ItemStack s) { return s.isEmpty() ? "minecraft:air" : ItemTools.itemName(s); }

    private static Map<?, ?> food(ItemStack s) { return s.isEmpty() ? null : ItemTools.component(s, "minecraft:food") instanceof Map<?, ?> m ? m : null; }

    /** ItemStack.getUseDuration(entity): 활·방패·삼지창 72000, 쇠뇌 장전+3, 망원경 1200, 솔 200, 엔더의 눈 0, 꿀 40, 물약·우유·불길한 병 32, 뿔은 악기, 그 밖은 음식 eat_seconds×20(없으면 0). */
    public static int duration(ItemStack s) {
        switch (name(s)) {
            case "minecraft:bow", "minecraft:shield", "minecraft:trident": return 72000;
            case "minecraft:crossbow": return RangedWeapons.crossbowUseDuration(s);
            case "minecraft:spyglass": return 1200;
            case "minecraft:brush": return 200;
            case "minecraft:ender_eye": return 0;
            case "minecraft:honey_bottle": return 40;
            case "minecraft:potion", "minecraft:milk_bucket", "minecraft:ominous_bottle": return 32;
            case "minecraft:goat_horn": {
                var inst = ItemEffects.instrumentOf(s);   // InstrumentItem: 악기가 없으면 0
                return inst == null ? 0 : inst.useDuration();
            }
            default: break;
        }
        var f = food(s);
        if (f == null) return 0;
        float secs = f.get("eat_seconds") instanceof Number x ? x.floatValue() : 1.6F;
        return (int) (secs * 20.0F);   // FoodProperties.eatDurationTicks
    }

    /** ItemStack.getUseAnimation. */
    public static Anim anim(ItemStack s) {
        return switch (name(s)) {
            case "minecraft:bow" -> Anim.BOW;
            case "minecraft:crossbow" -> Anim.CROSSBOW;
            case "minecraft:shield" -> Anim.BLOCK;
            case "minecraft:trident" -> Anim.SPEAR;
            case "minecraft:spyglass" -> Anim.SPYGLASS;
            case "minecraft:brush" -> Anim.BRUSH;
            case "minecraft:goat_horn" -> Anim.TOOT_HORN;
            case "minecraft:honey_bottle", "minecraft:potion", "minecraft:milk_bucket", "minecraft:ominous_bottle" -> Anim.DRINK;
            default -> food(s) != null ? Anim.EAT : Anim.NONE;
        };
    }

    /** ItemStack.useOnRelease: 쇠뇌만. */
    public static boolean useOnRelease(ItemStack s) { return name(s).equals("minecraft:crossbow"); }

    /** Item.getDrinkingSound: 꿀은 honey_bottle.drink, 그 밖은 generic.drink. */
    public static String drinkingSound(ItemStack s) {
        return name(s).equals("minecraft:honey_bottle") ? "minecraft:item.honey_bottle.drink" : "minecraft:entity.generic.drink";
    }

    /** Item.getEatingSound. */
    public static String eatingSound(ItemStack s) { return "minecraft:entity.generic.eat"; }

    /**
     * ItemStack.finishUsingItem(level, entity) — 플레이어가 아닌 생물. 돌려준 것이 손에 들 스택이다(서버 스택은 불변이라 바닐라의 제자리 consume 을
     * 줄인 새 스택으로 돌려준다). 같은 객체면 손을 바꾸지 않는다.
     */
    public static ItemStack finish(LivingEntity e, ItemStack s) {
        switch (name(s)) {
            case "minecraft:potion": return potion(e, s);
            case "minecraft:milk_bucket": {   // MilkBucketItem: 효과를 모두 지우고 하나 소모(몹은 양동이를 받지 않는다)
                e.removeAllEffects();
                return s.withCount(s.count() - 1);
            }
            case "minecraft:honey_bottle": {   // HoneyBottleItem: 음식(먹기) 뒤 독 제거, 비면 유리병
                ItemStack r = itemFinish(e, s);
                e.removeEffect(MobEffect.byName("minecraft:poison"));
                return r.isEmpty() ? stack("glass_bottle") : r;
            }
            case "minecraft:ominous_bottle": {   // OminousBottleItem: 소리 → 불길한 징조 → 소모(음식 경로 없음)
                e.level().playSound(null, e.blockPosition(), "minecraft:item.ominous_bottle.dispose", e.soundSource(), 1.0F, 1.0F);
                int amp = ItemTools.component(s, "minecraft:ominous_bottle_amplifier") instanceof Number n ? n.intValue() : 0;
                e.addEffect(new MobEffectInstance(MobEffect.byName("minecraft:bad_omen"), ItemEffects.BAD_OMEN_TICKS, amp, false, false, true));
                return s.withCount(s.count() - 1);
            }
            case "minecraft:suspicious_stew": {   // SuspiciousStewItem: 효과(증폭 0) 먼저, 그다음 먹기
                if (ItemTools.component(s, "minecraft:suspicious_stew_effects") instanceof List<?> stew) {
                    for (Object o : stew) {
                        if (!(o instanceof Map<?, ?> m)) continue;
                        int dur = m.get("duration") instanceof Number x ? x.intValue() : 160;
                        e.addEffect(new MobEffectInstance(MobEffect.byName(String.valueOf(m.get("id"))), dur, 0, false, true, true));
                    }
                }
                return itemFinish(e, s);
            }
            case "minecraft:chorus_fruit": {
                ItemStack r = itemFinish(e, s);
                chorusTeleport(e);
                return r;
            }
            default: return itemFinish(e, s);
        }
    }

    /** Item.finishUsingItem: 음식이면 LivingEntity.eat, 아니면 그대로. */
    private static ItemStack itemFinish(LivingEntity e, ItemStack s) {
        var f = food(s);
        return f == null ? s : eat(e, s, f);
    }

    /**
     * LivingEntity.eat(level, stack, food): 소리(레벨 난수 둘 — 높낮이 1 + (a − b) × 0.4) → 음식 효과(효과마다 엔티티 난수 하나, 확률 미만이면 건다)
     * → 하나 소모 → eat 게임 이벤트.
     */
    static ItemStack eat(LivingEntity e, ItemStack s, Map<?, ?> f) {
        var lr = e.level().random();
        float a = lr.nextFloat(), b = lr.nextFloat();
        e.level().playSound(null, e.x(), e.y(), e.z(), eatingSound(s), "neutral", 1.0F, 1.0F + (a - b) * 0.4F);
        if (f.get("effects") instanceof List<?> fx) {
            for (Object o : fx) {
                if (!(o instanceof Map<?, ?> m) || !(m.get("effect") instanceof Map<?, ?> eff)) continue;
                float prob = m.get("probability") instanceof Number x ? x.floatValue() : 1.0F;
                if (e.random().nextFloat() < prob) e.addEffect(Consuming.fromMap(eff));
            }
        }
        ItemStack r = s.withCount(s.count() - 1);
        e.gameEvent(GameEvent.EAT);
        return r;
    }

    /**
     * PotionItem.finishUsingItem — 플레이어가 아니면 소모하지 않는다. 지속 효과는 바로 걸고 즉시 효과는 모아서 근접도 1.0 으로.
     * Paper: 마신 쪽이 죽었고 keepInventory 가 아니면 유리병을 떨어뜨리고 빈 손(drink 게임 이벤트 없음).
     */
    private static ItemStack potion(LivingEntity e, ItemStack s) {
        List<MobEffectInstance> instant = new java.util.ArrayList<>();
        for (MobEffectInstance i : Consuming.potionEffects(s)) {
            if (i.effect().isInstantaneous()) instant.add(i);
            else e.addEffect(i);
        }
        for (MobEffectInstance i : instant) i.effect().applyInstantenousEffect(e.effectHost(), i.amplifier(), 1.0);
        if (e.health() <= 0.0F && !e.level().gameRule("keepInventory")) {
            e.spawnAtLocation(stack("glass_bottle"), 0.0F);
            return ItemStack.EMPTY;
        }
        if (s.isEmpty()) return stack("glass_bottle");
        e.gameEvent(GameEvent.DRINK);
        return s;
    }

    /**
     * ChorusFruitItem.finishUsingItem 의 순간이동(먹은 뒤): 최대 16번, 엔티티 난수로 x(±8)·y(정수 −8..7, 높이 범위 고정)·z(±8), 탈것에서 내린 뒤
     * randomTeleport(입자 켬). 성공하면 옛 위치에 teleport 게임 이벤트, 새 위치에서 소리(여우는 fox.teleport·neutral, 그 밖은 chorus_fruit.teleport·players),
     * 낙하 거리 초기화.
     */
    private static void chorusTeleport(LivingEntity e) {
        var level = e.level();
        var r = e.random();
        int min = level.minBuildHeight(), max = min + level.dimensionType().logicalHeight() - 1;
        for (int i = 0; i < 16; i++) {
            double d0 = e.x() + (r.nextDouble() - 0.5) * 16.0;
            double d1 = Math.clamp(e.y() + (double) (r.nextInt(16) - 8), (double) min, (double) max);
            double d2 = e.z() + (r.nextDouble() - 0.5) * 16.0;
            if (e.isPassenger()) e.stopRiding();
            Vec3 old = new Vec3(e.x(), e.y(), e.z());
            if (e.randomTeleport(d0, d1, d2, true)) {
                level.gameEvent(GameEvent.TELEPORT, old, GameEventContext.of(e));
                boolean fox = e instanceof Fox;
                level.playSound(null, e.x(), e.y(), e.z(), fox ? "minecraft:entity.fox.teleport" : "minecraft:item.chorus_fruit.teleport",
                        fox ? "neutral" : "players", 1.0F, 1.0F);
                e.resetFallDistance();
                break;
            }
        }
    }

    private static ItemStack stack(String item) { return new ItemStack(ItemContext.id("minecraft:item", item), 1); }
}
