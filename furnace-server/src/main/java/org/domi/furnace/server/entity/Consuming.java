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

    private static final int DRINK_TICKS = 32, HONEY_TICKS = 40, OMINOUS_BOTTLE_TICKS = 120000, SPYGLASS_TICKS = 1200;

    /** SpyglassItem.stopUsing: 쓰기를 멈추는 소리(플레이어 본인에겐 안 보낸다). */
    public static void spyglassStopSound(Player p) {
        var level = p.currentLevel;
        if (level != null) Shields.playSoundExceptSelf(p, level, "minecraft:item.spyglass.stop_using", 1.0F, 1.0F);
    }

    /** 그 아이템을 지금 쓸 수 있으면 사용 시간(틱), 아니면 -1. */
    public static int duration(Player p, ItemStack s) {
        String n = ItemTools.itemName(s);
        switch (n) {
            case "minecraft:potion": return DRINK_TICKS;
            case "minecraft:milk_bucket": return DRINK_TICKS;
            case "minecraft:ominous_bottle": return ItemEffects.OMINOUS_BOTTLE_TICKS;   // ItemUtils.startUsingInstantly — 허기 검사 없음
            case "minecraft:honey_bottle": return HONEY_TICKS;   // HoneyBottleItem.use = ItemUtils.startUsingInstantly — 배가 불러도 마신다(허기 검사 없음)
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
        if (Shields.isShield(s)) return Shields.tryStart(p, hand, s);
        if (n0(s).equals("minecraft:spyglass")) {   // SpyglassItem.use: 소리·사용 통계 → ItemUtils.startUsingInstantly (CONSUME)
            var level = p.currentLevel;
            if (level != null) Shields.playSoundExceptSelf(p, level, "minecraft:item.spyglass.use", 1.0F, 1.0F);
            p.awardStat("minecraft:used:minecraft:spyglass", 1);
            p.startUsingItem(hand, SPYGLASS_TICKS);
            return true;
        }
        int d = duration(p, s);
        if (d < 0) return false;
        p.startUsingItem(hand, d);
        return true;
    }

    public static void stop(Player p) { p.stopUsingItem(); }

    /** 틱마다: 손의 아이템이 바뀌었으면 취소, 다 되면 끝낸다. */
    public static void tick(Player p, org.domi.furnace.server.world.Region region) {
        if (p.usingHand < 0) return;
        PlayerInventory inv = p.inventory();
        int ws = p.usingHand == 0 ? inv.heldWindowSlot() : 45;
        ItemStack s = inv.get(ws);
        var adv = p.advancements();
        if (adv != null && !s.isEmpty() && adv.hasListeners("minecraft:using_item")) {   // ServerPlayer.updateUsingItem: 쓰는 동안 매 틱 using_item
            adv.trigger("minecraft:using_item", org.domi.furnace.server.ServerCore.lootStackOf(s));
        }
        if (n0(s).equals("minecraft:goat_horn")) {   // 염소 뿔: 사용 시간(instrument 의 use_duration)이 지나면 끝
            if (--p.usingTicks <= 0) p.stopUsingItem();
            return;
        }
        if (Brushing.isBrush(s)) {   // 솔: 매 틱 onUseTick(남은 틱은 줄이기 전 값), 시선이 블록을 벗어나면 놓는다
            if (region == null || !region.tickBrush(p)) { p.stopUsingItem(); return; }
            if (--p.usingTicks <= 0) p.stopUsingItem();
            return;
        }
        if (RangedWeapons.usesRelease(s)) {   // 활·쇠뇌·삼지창: 놓을 때 처리
            RangedWeapons.tick(p, region == null ? null : region.level(), s);
            return;
        }
        if (n0(s).equals("minecraft:spyglass")) {   // 망원경: 1200틱이 다 되면 finishUsingItem → stopUsing(소리)
            if (--p.usingTicks <= 0) {
                p.stopUsingItem();
                spyglassStopSound(p);
            }
            return;
        }
        if (n0(s).equals("minecraft:ender_eye")) return;   // 엔더의 눈: startUsingItem 만 하고 사용 시간 0 — 놓거나 아이템이 바뀔 때까지 사용 중 표시가 남는다
        if (Shields.isShield(s)) {   // 방패: 72000틱이 다 되면 그냥 끝(finishUsingItem 은 그대로)
            if (--p.usingTicks <= 0) p.stopUsingItem();
            return;
        }
        if (s.isEmpty() || duration0(s) < 0) { p.stopUsingItem(); return; }
        if (--p.usingTicks > 0) return;
        int hand = p.usingHand;
        p.stopUsingItem();
        // 플러그인 PlayerItemConsumeEvent 취소: 효과·소비 없이, 클라이언트가 먼저 줄인 손 칸을 되돌린다
        if (!p.vitals().env().gameEvents().playerConsume(p, s, hand)) {
            org.domi.furnace.server.plugin.ClientResync.slot(p, ws);
            return;
        }
        finish(p, region, inv, ws, s);
    }

    private static String n0(ItemStack s) { return s.isEmpty() ? "" : ItemTools.itemName(s); }

    private static int duration0(ItemStack s) {
        String n = ItemTools.itemName(s);
        return n.equals("minecraft:potion") || n.equals("minecraft:milk_bucket") || n.equals("minecraft:honey_bottle") || n.equals("minecraft:ominous_bottle")
                || ItemTools.component(s, "minecraft:food") instanceof Map ? 1 : -1;
    }

    private static void gameEventAt(Player p, org.domi.furnace.server.world.gameevent.GameEvent event) {
        var level = p.currentLevel;
        if (level != null) level.gameEvent(event, new Vec3(p.x(), p.y(), p.z()), org.domi.furnace.server.world.gameevent.GameEventContext.of(p));
    }

    private static void finish(Player p, org.domi.furnace.server.world.Region region, PlayerInventory inv, int ws, ItemStack s) {
        PlayerVitals v = p.vitals();
        String n = ItemTools.itemName(s);
        String leftover = null;
        if (n.equals("minecraft:ominous_bottle")) {   // OminousBottleItem.finishUsingItem: super(음식)를 부르지 않는다
            consumeTrigger(p, s);   // 사용 통계(used)도 여기서 오른다
            ItemEffects.ominousBottleDrunk(p, region == null ? null : region.level(), s);
            if (!v.isCreative()) inv.set(ws, s.withCount(s.count() - 1));
            return;
        }
        if (n.equals("minecraft:potion")) {
            finishPotion(p, region == null ? null : region.level(), inv, ws, s);
            return;
        }
        if (n.equals("minecraft:suspicious_stew") && ItemTools.component(s, "minecraft:suspicious_stew_effects") instanceof List<?> stew) {
            for (Object o : stew) {   // SuspiciousStewItem.finishUsingItem: 음식 먹기 전에 효과부터(증폭 0, 아이콘·입자 보임)
                if (!(o instanceof Map<?, ?> m)) continue;
                int dur = m.get("duration") instanceof Number x ? x.intValue() : 160;
                v.addEffect(new MobEffectInstance(MobEffect.byName(String.valueOf(m.get("id"))), dur, 0, false, true, true));
            }
        }
        if (ItemTools.component(s, "minecraft:food") instanceof Map<?, ?> f) {
            int nutrition = f.get("nutrition") instanceof Number x ? x.intValue() : 0;
            float sat = f.get("saturation") instanceof Number x ? x.floatValue() : 0;
            v.eatFood(nutrition, sat);
            consumeTrigger(p, s);   // 바닐라 Player.eat: foodData.eat → consume_item → super.eat(효과)
            if (f.get("effects") instanceof List<?> fx) {
                for (Object o : fx) {
                    if (!(o instanceof Map<?, ?> m) || !(m.get("effect") instanceof Map<?, ?> e)) continue;
                    float prob = m.get("probability") instanceof Number x ? x.floatValue() : 1.0F;
                    if (p.random().nextFloat() < prob) v.addEffect(fromMap(e));
                }
            }
            if (f.get("using_converts_to") instanceof Map<?, ?> c && c.get("id") instanceof String id) leftover = id;
            gameEventAt(p, org.domi.furnace.server.world.gameevent.GameEvent.EAT);   // LivingEntity.eat 끝
        }
        // 바닐라 HoneyBottle·MilkBucketItem.finishUsingItem: 고유 효과 전에 consume_item(꿀은 음식 경로와 합쳐 두 번)
        if (n.equals("minecraft:honey_bottle") || n.equals("minecraft:milk_bucket")) consumeTrigger(p, s);
        switch (n) {
            case "minecraft:honey_bottle" -> { v.removeEffect(MobEffect.byName("poison")); leftover = "minecraft:glass_bottle"; }
            case "minecraft:milk_bucket" -> { v.removeAllEffects(); leftover = "minecraft:bucket"; }
            default -> { }
        }
        boolean creative = v.isCreative();
        if (!creative) {
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
        if (n.equals("minecraft:chorus_fruit") && region != null) ItemEffects.chorusFruitTeleport(p, region.level());   // 먹은 뒤 순간이동(크리에이티브도)
    }

    /**
     * consume_item 트리거와 사용 통계: 바닐라는 같은 자리에서 둘 다 낸다(Player.eat·PotionItem·MilkBucketItem·HoneyBottleItem·OminousBottleItem 의
     * finishUsingItem). 꿀은 음식 경로와 꿀 고유 경로에서 각각 불려 두 번 오른다(바닐라도 둘 다 award).
     */
    private static void consumeTrigger(Player p, ItemStack s) {
        p.awardStat("minecraft:used:" + ItemTools.itemName(s), 1);
        var adv = p.advancements();
        if (adv != null) adv.trigger("minecraft:consume_item", org.domi.furnace.server.ServerCore.lootStackOf(s));
    }

    /** PotionContents.getAllEffects: 기본 물약 효과 + 사용자 지정 효과. */
    public static List<MobEffectInstance> potionEffects(ItemStack s) {
        List<MobEffectInstance> list = new java.util.ArrayList<>();
        if (!(ItemTools.component(s, "minecraft:potion_contents") instanceof Map<?, ?> pc)) return list;
        if (pc.get("potion") instanceof String id) for (var e : Potions.effectsOf(id)) list.add(e.deepCopy());
        if (pc.get("custom_effects") instanceof List<?> ce) for (Object o : ce) if (o instanceof Map<?, ?> m) list.add(fromMap(m));
        return list;
    }

    /** PotionContents.getPotion 이 물인가. */
    public static boolean isWaterPotion(ItemStack s) {
        return ItemTools.component(s, "minecraft:potion_contents") instanceof Map<?, ?> pc && "minecraft:water".equals(pc.get("potion"))
                && !(pc.get("custom_effects") instanceof List<?> l && !l.isEmpty());
    }

    /** PotionContents.getColor: 효과 색의 (단계+1) 가중 평균, 효과가 없으면 물 색(-13083194). */
    public static int potionColor(ItemStack s) {
        List<MobEffectInstance> list = potionEffects(s);
        if (list.isEmpty()) return -13083194;
        float r = 0, g = 0, b = 0;
        int total = 0;
        for (MobEffectInstance i : list) {
            if (!i.visible()) continue;
            int c = i.effect().color();
            int w = i.amplifier() + 1;
            r += w * (c >> 16 & 255) / 255.0F;
            g += w * (c >> 8 & 255) / 255.0F;
            b += w * (c & 255) / 255.0F;
            total += w;
        }
        if (total == 0) return 0;
        return (int) (r / total * 255.0F) << 16 | (int) (g / total * 255.0F) << 8 | (int) (b / total * 255.0F);
    }

    /**
     * Paper 1.21.1 PotionItem.finishUsingItem(플레이어, javap 대조 — user-todo U3c 코디네이터 확정): consume_item·사용 통계 → 지속 효과는 바로 걸고
     * 즉시 효과는 모아 둔다 → 크리에이티브가 아니면 하나 소모 → 모은 즉시 효과(근접도 1.0) → 크리에이티브가 아니면 유리병: 죽었고 keepInventory 가
     * 아니면 발밑에 떨어뜨리고 칸을 비운 채 끝(drink 게임 이벤트 없음), 물약이 다 떨어졌으면 그 칸에, 아니면 인벤토리에 넣는다(가득이면 사라진다 —
     * Inventory.add 의 반환값을 버린다) → drink 게임 이벤트. 바닐라는 즉시 효과를 목록 순서대로 그 자리에서 적용한다.
     */
    static void finishPotion(Player p, org.domi.furnace.server.block.ChunkLevel level, PlayerInventory inv, int ws, ItemStack s) {
        PlayerVitals v = p.vitals();
        consumeTrigger(p, s);
        List<MobEffectInstance> instant = new java.util.ArrayList<>();
        for (MobEffectInstance i : potionEffects(s)) {
            if (i.effect().isInstantaneous()) instant.add(i);
            else v.addEffect(i);
        }
        boolean creative = v.isCreative();
        ItemStack rest = creative ? s : s.withCount(s.count() - 1);
        if (!creative) inv.set(ws, rest);
        for (MobEffectInstance i : instant) v.applyInstantaneousEffect(i.effect(), i.amplifier(), 1.0);
        if (!creative) {
            ItemStack bottle = new ItemStack(ItemContext.id("minecraft:item", "glass_bottle"), 1);
            if (v.health() <= 0.0F && level != null && !level.gameRule("keepInventory")) {
                var ie = new ItemEntity(level, level.nextEntityId(), p.x(), p.y(), p.z(), bottle);   // spawnAtLocation(stack, 0)
                ie.setDefaultPickUpDelay();
                level.addFreshEntity(ie);
                inv.set(ws, ItemStack.EMPTY);
                return;
            }
            if (rest.isEmpty()) inv.set(ws, bottle);
            else inv.add(bottle, false, 64, false);
        }
        gameEventAt(p, org.domi.furnace.server.world.gameevent.GameEvent.DRINK);
    }

    static MobEffectInstance fromMap(Map<?, ?> e) {
        MobEffect eff = MobEffect.byName(String.valueOf(e.get("id")));
        int amp = e.get("amplifier") instanceof Number x ? x.intValue() : 0;
        int dur = e.get("duration") instanceof Number x ? x.intValue() : 0;
        boolean amb = Boolean.TRUE.equals(e.get("ambient"));
        boolean particles = !Boolean.FALSE.equals(e.get("show_particles"));
        boolean icon = !Boolean.FALSE.equals(e.get("show_icon"));
        return new MobEffectInstance(eff, dur, amp, amb, particles, icon);
    }
}
