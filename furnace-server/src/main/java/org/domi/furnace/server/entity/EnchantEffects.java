// 바닐라 EnchantmentHelper.doPostAttackEffects 의 서버 로직: 가시(thorns) 반사·돌풍(wind_burst) 폭발.
// 가시: 맞은 쪽 장비 칸(MAINHAND, OFFHAND, FEET, LEGS, CHEST, HEAD, BODY 순 — 슬롯 그룹이 ANY)마다
//   확률 0.15F×레벨(=0.15F + 0.15F×(L-1)) 를 레벨 난수(ServerLevel.random)로 굴리고, 성공하면 때린 쪽 난수로
//   randomBetween(1, 5) 피해(thorns, 원인 = 가시 주인)를 준 뒤 그 장비 내구도 2 를 깎는다.
// 소유권: 때리는 쪽·맞는 쪽을 가진 region 스레드에서만 부른다.
// 차이: 가시 아이템 파괴 통계(item_broken)와 내구도 변경 발전 과제는 아직 없다. 투사체 맞음(화살 등) 경로는 연결하지 않았다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.Explosion;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.damage.DamageSource;
import org.domi.furnace.server.entity.damage.DamageType;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.protocol.play.EntityEvent;

public final class EnchantEffects {
    private EnchantEffects() {}

    /** 장비 칸 순서(EquipmentSlot.values()). */
    private static final EquipmentSlot[] SLOTS = EquipmentSlot.values();

    /** DamageSource.getWeaponItem: 직접 때린 쪽이 생물(플레이어 포함)이면 주 손 아이템. 아니면 null. */
    public static ItemStack weaponOf(Rider direct) {
        if (direct instanceof Player p) return p.equipment(EquipmentSlot.MAINHAND);
        if (direct instanceof LivingEntity le) return le.getItemBySlot(EquipmentSlot.MAINHAND);
        return null;
    }

    static ItemStack stackIn(Targetable e, EquipmentSlot slot) {
        if (e instanceof Player p) return p.equipment(slot);
        if (e instanceof LivingEntity le) return le.getItemBySlot(slot);
        return ItemStack.EMPTY;
    }

    /** 플레이어 창 칸 번호(5 머리 .. 8 발, 45 왼손, 주 손 = 든 칸). 몸 칸은 없다(-1). */
    private static int windowSlot(Player p, EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND -> p.inventory().heldWindowSlot();
            case OFFHAND -> 45;
            case HEAD -> 5;
            case CHEST -> 6;
            case LEGS -> 7;
            case FEET -> 8;
            case BODY -> -1;
        };
    }

    /** LivingEntity.getEquipmentBreakEvent: 주 손 47, 왼손 48, 머리 49, 가슴 50, 다리 51, 발 52, 몸 65(동물 갑옷). */
    private static byte breakEvent(EquipmentSlot slot) {
        return (byte) switch (slot) {
            case MAINHAND -> 47;
            case OFFHAND -> 48;
            case HEAD -> 49;
            case CHEST -> 50;
            case LEGS -> 51;
            case FEET -> 52;
            case BODY -> 65;
        };
    }

    /**
     * ItemStack.hurtAndBreak(amount, level, owner, onBreak): 내구성 확률로 줄이고(레벨 난수) 깎고, 다 닳으면 하나 줄인다.
     * 반환: 새 스택, 부서졌는지는 {@code broke} 로.
     */
    static ItemStack hurtStack(ChunkLevel level, ItemStack stack, int amount, boolean[] broke) {
        if (!ItemTools.isDamageable(stack)) return stack;
        if (amount > 0) {
            int unbreaking = ItemTools.enchantmentLevel(stack, "minecraft:unbreaking");
            if (unbreaking > 0) {
                boolean armor = ItemContext.isInTag("minecraft:item", "minecraft:enchantable/armor", stack.itemId());
                float chance = armor ? (2.0F + 2.0F * (unbreaking - 1)) / (10.0F + 5.0F * (unbreaking - 1))
                        : (float) unbreaking / (2.0F + (unbreaking - 1));
                int removed = 0;
                for (int j = 0; j < amount; j++) if (level.random().nextFloat() < chance) removed++;
                amount -= removed;
            }
            if (amount <= 0) return stack;
        }
        int newDamage = ItemTools.damage(stack) + amount;
        ItemStack damaged = ItemTools.withDamage(stack, newDamage);
        if (newDamage >= ItemTools.maxDamage(stack)) {
            broke[0] = true;
            return damaged.withCount(damaged.count() - 1);
        }
        return damaged;
    }

    /** 장비 칸의 아이템을 깎는다(owner 가 크리에이티브 플레이어면 그대로). 부서지면 칸을 비우고 부서짐 이벤트. */
    static void damageEquipment(ChunkLevel level, Targetable owner, EquipmentSlot slot, ItemStack stack, int amount) {
        if (owner instanceof Player p && p.vitals().instabuild()) return;   // hasInfiniteMaterials
        boolean[] broke = {false};
        ItemStack result = hurtStack(level, stack, amount, broke);
        if (owner instanceof Player p) {
            int ws = windowSlot(p, slot);
            if (ws < 0) return;
            p.inventory().set(ws, result);
            if (broke[0]) {
                var ev = new EntityEvent(p.id(), breakEvent(slot));
                p.send(ev);
                p.vitals().broadcastToTrackers(ev);
            }
        } else if (owner instanceof LivingEntity le) {
            le.setItemSlot(slot, result);
            if (broke[0]) le.broadcastEntityEvent(breakEvent(slot));
        }
    }

    /** 가시: victim 의 장비 칸마다. attacker 는 때린 쪽(null 이면 아무것도 안 한다). */
    public static void thorns(ChunkLevel level, Targetable victim, Targetable attacker) {
        if (level == null || attacker == null || victim == null || !(victim instanceof LivingEntity || victim instanceof Player)) return;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = stackIn(victim, slot);
            int lvl = ItemTools.enchantmentLevel(stack, "minecraft:thorns");
            if (lvl <= 0) continue;
            float chance = 0.15F + 0.15F * (lvl - 1);   // perLevel(0.15F) = linear(base=0.15, per_level_above_first=0.15)
            if (!(level.random().nextFloat() < chance)) continue;
            // DamageEntity(min 1, max 5, thorns): Mth.randomBetween(때린 쪽 난수, 1, 5)
            float amount = randomOf(attacker).nextFloat() * (5.0F - 1.0F) + 1.0F;
            hurtByThorns(attacker, victim, amount);
            damageEquipment(level, victim, slot, stack, 2);   // DamageItem(2)
        }
    }

    private static org.domi.furnace.server.block.RandomSource randomOf(Targetable t) {
        if (t instanceof Entity e) return e.random();
        return ((Player) t).random();
    }

    /** attacker.hurt(DamageSource(thorns, victim), amount). 원인·직접 엔티티가 모두 가시 주인. */
    private static void hurtByThorns(Targetable attacker, Targetable victim, float amount) {
        Rider owner = victim instanceof Rider r ? r : null;
        if (attacker instanceof Player p) {
            p.vitals().hurt(DamageSource.by(DamageType.byKey("thorns"), owner), amount);
        } else if (attacker instanceof LivingEntity le) {
            le.hurtByVia(Entity.Damage.of(DamageType.byKey("thorns")), amount, victim, victim instanceof Entity ve ? ve : null);
        }
    }

    /**
     * 돌풍(wind_burst): 철퇴를 든 공격자가 낙하 1.5 이상이고 비행 중이 아니면 자기 위치에서 반경 3.5 윈드 폭발.
     * 밀림 배수는 레벨 1~3 이 [1.2, 1.75, 2.2], 그 너머는 1.5 + 0.35 × (L-1).
     */
    public static void windBurst(ChunkLevel level, Player attacker, ItemStack weapon) {
        int l = ItemTools.enchantmentLevel(weapon, "minecraft:wind_burst");
        if (l <= 0 || level == null) return;
        var v = attacker.vitals();
        if (!(v.fallDistance() >= 1.5F) || v.isFlying()) return;   // flags.is_flying=false, movement.fall_distance>=1.5
        float mult = l <= 3 ? new float[] {1.2F, 1.75F, 2.2F}[l - 1] : 1.5F + 0.35F * (l - 1);
        level.explode(null, attacker.x(), attacker.y(), attacker.z(), 3.5F, new Explosion.WindCalculator(mult),
                AbstractWindCharge.GUST_EMITTER_SMALL, AbstractWindCharge.GUST_EMITTER_LARGE, AbstractWindCharge.BURST_SOUND);
    }
}
