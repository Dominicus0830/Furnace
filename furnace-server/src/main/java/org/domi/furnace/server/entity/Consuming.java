// 먹기·마시기(바닐라 LivingEntity.startUsingItem/completeUsingItem): 음식(food 컴포넌트)·물약·우유·꿀을 오래 쓰다 끝나면 적용한다.
package org.domi.furnace.server.entity;

import java.util.List;
import java.util.Map;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.effect.Potions;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;

public final class Consuming {
    private Consuming() { }

    private static final int DRINK_TICKS = 32, HONEY_TICKS = 40;

    /** 그 아이템을 지금 쓸 수 있으면 사용 시간(틱), 아니면 -1. */
    public static int duration(Player p, ItemStack s) {
        String n = ItemTools.itemName(s);
        switch (n) {
            case "minecraft:potion": return DRINK_TICKS;
            case "minecraft:milk_bucket": return DRINK_TICKS;
            case "minecraft:honey_bottle": return p.vitals().canEat(false) ? HONEY_TICKS : -1;
            default: break;
        }
        if (!(ItemTools.component(s, "minecraft:food") instanceof Map<?, ?> f)) return -1;
        boolean always = Boolean.TRUE.equals(f.get("can_always_eat"));
        if (!p.vitals().canEat(always)) return -1;
        double secs = f.get("eat_seconds") instanceof Number x ? x.doubleValue() : 1.6;
        return (int) (secs * 20.0F);
    }

    /** 사용 시작: 쓸 수 있으면 true. */
    public static boolean tryStart(Player p, int hand, ItemStack s) {
        int d = duration(p, s);
        if (d < 0) return false;
        p.usingHand = hand;
        p.usingTicks = d;
        return true;
    }

    public static void stop(Player p) { p.usingHand = -1; }

    /** 틱마다: 손의 아이템이 바뀌었으면 취소, 다 되면 끝낸다. */
    public static void tick(Player p, org.domi.furnace.server.world.Region region) {
        if (p.usingHand < 0) return;
        PlayerInventory inv = p.inventory();
        int ws = p.usingHand == 0 ? inv.heldWindowSlot() : 45;
        ItemStack s = inv.get(ws);
        if (s.isEmpty() || duration0(s) < 0) { p.usingHand = -1; return; }
        if (--p.usingTicks > 0) return;
        p.usingHand = -1;
        finish(p, region, inv, ws, s);
    }

    private static int duration0(ItemStack s) {
        String n = ItemTools.itemName(s);
        return n.equals("minecraft:potion") || n.equals("minecraft:milk_bucket") || n.equals("minecraft:honey_bottle")
                || ItemTools.component(s, "minecraft:food") instanceof Map ? 1 : -1;
    }

    private static void finish(Player p, org.domi.furnace.server.world.Region region, PlayerInventory inv, int ws, ItemStack s) {
        PlayerVitals v = p.vitals();
        String n = ItemTools.itemName(s);
        String leftover = null;
        if (ItemTools.component(s, "minecraft:food") instanceof Map<?, ?> f) {
            int nutrition = f.get("nutrition") instanceof Number x ? x.intValue() : 0;
            float sat = f.get("saturation") instanceof Number x ? x.floatValue() : 0;
            v.eatFood(nutrition, sat);
            if (f.get("effects") instanceof List<?> fx) {
                for (Object o : fx) {
                    if (!(o instanceof Map<?, ?> m) || !(m.get("effect") instanceof Map<?, ?> e)) continue;
                    float prob = m.get("probability") instanceof Number x ? x.floatValue() : 1.0F;
                    if (p.random().nextFloat() < prob) v.addEffect(fromMap(e));
                }
            }
            if (f.get("using_converts_to") instanceof Map<?, ?> c && c.get("id") instanceof String id) leftover = id;
        }
        switch (n) {
            case "minecraft:honey_bottle" -> { v.removeEffect(MobEffect.byName("poison")); leftover = "minecraft:glass_bottle"; }
            case "minecraft:milk_bucket" -> { v.removeAllEffects(); leftover = "minecraft:bucket"; }
            case "minecraft:potion" -> { applyPotion(v, s); leftover = "minecraft:glass_bottle"; }
            default -> { }
        }
        boolean creative = v.isCreative();
        if (creative) return;
        ItemStack rest = s.withCount(s.count() - 1);
        inv.set(ws, rest);
        if (leftover != null) {
            ItemStack lo = new ItemStack(ItemContext.id("minecraft:item", leftover.replace("minecraft:", "")), 1);
            if (rest.isEmpty()) inv.set(ws, lo);
            else {
                ItemStack left = inv.add(lo, false, 64, false);
                if (!left.isEmpty()) region.playerDrop(p, left, false, true);
            }
        }
    }

    private static void applyPotion(PlayerVitals v, ItemStack s) {
        if (!(ItemTools.component(s, "minecraft:potion_contents") instanceof Map<?, ?> pc)) return;
        List<MobEffectInstance> list = new java.util.ArrayList<>();
        if (pc.get("potion") instanceof String id) list.addAll(Potions.effectsOf(id));
        if (pc.get("custom_effects") instanceof List<?> ce) for (Object o : ce) if (o instanceof Map<?, ?> m) list.add(fromMap(m));
        for (MobEffectInstance i : list) {
            if (i.effect().isInstantaneous()) v.applyInstantaneousEffect(i.effect(), i.amplifier(), 1.0);
            else v.addEffect(i);
        }
    }

    private static MobEffectInstance fromMap(Map<?, ?> e) {
        MobEffect eff = MobEffect.byName(String.valueOf(e.get("id")));
        int amp = e.get("amplifier") instanceof Number x ? x.intValue() : 0;
        int dur = e.get("duration") instanceof Number x ? x.intValue() : 0;
        boolean amb = Boolean.TRUE.equals(e.get("ambient"));
        boolean particles = !Boolean.FALSE.equals(e.get("show_particles"));
        boolean icon = !Boolean.FALSE.equals(e.get("show_icon"));
        return new MobEffectInstance(eff, dur, amp, amb, particles, icon);
    }
}
