// 발사기 동작 중 엔티티를 건드리는 부분: 갑옷 입히기(ArmorItem.dispenseArmor), 안장, 말 갑옷, 상자 싣기, 털 깎기, 아르마딜로 솔질.
// 앞 칸 상자(new AABB(pos)) 안 엔티티를 바닐라 getEntitiesOfClass 순서로 훑는다 — 플레이어는 엔티티 저장소 밖이라 맨 뒤에.
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.item.ItemTools;

public final class DispenserEquip {

    private DispenserEquip() {}

    /** 앞 칸 상자 안의 살아 있는 생물(관전자 제외는 호출 쪽 조건으로). */
    static List<LivingEntity> livingIn(ChunkLevel level, Pos pos) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : level.getEntities(null, box(pos), e -> e instanceof LivingEntity)) out.add((LivingEntity) e);
        return out;
    }

    private static AABB box(Pos pos) { return new AABB(pos.x(), pos.y(), pos.z(), pos.x() + 1.0, pos.y() + 1.0, pos.z() + 1.0); }

    /** 갑옷 대상: 생물 + 플레이어(플레이어는 LivingEntity 가 아니다). */
    static List<Object> wearersIn(ChunkLevel level, Pos pos) {
        List<Object> out = new ArrayList<>(livingIn(level, pos));
        level.forEachPlayerIn(box(pos), out::add);
        return out;
    }

    // ------------------------------------------------------------------ 갑옷(ArmorItem.dispenseArmor)

    /** Equipable.getEquipmentSlot — 장착 아이템이 아니면 null. */
    public static EquipmentSlot equipableSlot(ItemStack stack) {
        String n = ItemTools.itemName(stack);
        if (n.endsWith("_helmet") || n.equals("minecraft:carved_pumpkin") || n.endsWith("_head") || n.endsWith("_skull")) return EquipmentSlot.HEAD;
        if (n.endsWith("_chestplate") || n.equals("minecraft:elytra")) return EquipmentSlot.CHEST;
        if (n.endsWith("_leggings")) return EquipmentSlot.LEGS;
        if (n.endsWith("_boots")) return EquipmentSlot.FEET;
        if (n.equals("minecraft:shield")) return EquipmentSlot.OFFHAND;
        if (n.equals("minecraft:wolf_armor") || n.endsWith("_horse_armor")) return EquipmentSlot.BODY;
        if (n.endsWith("_carpet") && org.domi.furnace.server.item.ItemTags.is(stack, "minecraft:wool_carpets")) return EquipmentSlot.BODY;   // WoolCarpetBlock
        return null;
    }

    /** canUseSlot: 생물 기본 false, 몹·거치대·플레이어는 몸통 칸만 빼고, 늑대·말·라마는 모두. */
    static boolean canUseSlot(Object e, EquipmentSlot slot) {
        if (e instanceof Wolf || e instanceof Horse || e instanceof Llama) return true;
        if (e instanceof Mob || e instanceof ArmorStand || e instanceof Player) return slot != EquipmentSlot.BODY;
        return false;
    }

    /** getEquipmentSlotForItem: 장착 칸을 쓸 수 있으면 그 칸, 아니면 주 손. */
    static EquipmentSlot slotFor(Object e, ItemStack stack) {
        EquipmentSlot s = equipableSlot(stack);
        return s != null && canUseSlot(e, s) ? s : EquipmentSlot.MAINHAND;
    }

    static ItemStack itemBySlot(Object e, EquipmentSlot slot) {
        return e instanceof Player p ? p.equipment(slot) : ((LivingEntity) e).getItemBySlot(slot);
    }

    /** canTakeItem: 알레이는 거절, 돌고래·여우·판다는 주 손만, 몹은 칸이 비고 줍기 가능, 거치대는 칸이 비고 막히지 않음, 플레이어는 칸이 비면. */
    static boolean canTakeItem(Object e, ItemStack stack) {
        EquipmentSlot slot = slotFor(e, stack);
        if (e instanceof Allay) return false;
        if (e instanceof Dolphin || e instanceof Fox || e instanceof Panda) {
            return itemBySlot(e, slot).isEmpty() && slot == EquipmentSlot.MAINHAND && ((Mob) e).canPickUpLoot();
        }
        if (e instanceof Mob m) return itemBySlot(e, slot).isEmpty() && m.canPickUpLoot();
        if (e instanceof ArmorStand a) return itemBySlot(e, slot).isEmpty() && !a.isSlotDisabled(slot);
        if (e instanceof Player) return itemBySlot(e, slot).isEmpty();
        return false;
    }

    /** setItemSlot + onEquipItem. 플레이어는 인벤토리 칸이라 여기서 직접(빈 칸에 새 장비: 장착 칸이 맞으면 장착 소리, 씨앗은 엔티티 난수). */
    private static void setItemSlot(ChunkLevel level, Object o, EquipmentSlot slot, ItemStack stack) {
        if (o instanceof Player p) {
            var inv = p.inventory();
            int ws = switch (slot) {
                case MAINHAND -> inv.heldWindowSlot();
                case OFFHAND -> 45;
                case HEAD -> 5;
                case CHEST -> 6;
                case LEGS -> 7;
                case FEET -> 8;
                case BODY -> -1;
            };
            if (ws >= 0) inv.set(ws, stack);
            if (p.isSpectator()) return;
            if (equipableSlot(stack) == slot) {
                level.playSound(null, p.x(), p.y(), p.z(), org.domi.furnace.server.block.ItemUse.equipSound(ItemTools.itemName(stack)), "players", 1.0F, 1.0F);
                p.random().nextLong();
            }
            // Player.doesEmitEquipEvent: 방어구 칸만
            if (slot.type() == EquipmentSlot.Type.HUMANOID_ARMOR) equipEvent(level, p.x(), p.y(), p.z(), p, stack);
            return;
        }
        ((LivingEntity) o).setItemSlot(slot, stack);   // 몹·거치대: setItemSlot 안의 onEquipItem 이 소리·게임 이벤트를 낸다
    }

    /** onEquipItem 의 게임 이벤트: 새 것이 장착 아이템이면 EQUIP, 아니면 UNEQUIP(엔티티 위치, 엔티티 출처). */
    public static void equipEvent(ChunkLevel level, double x, double y, double z, Object source, ItemStack now) {
        var ev = equipableSlot(now) != null ? org.domi.furnace.server.world.gameevent.GameEvent.EQUIP : org.domi.furnace.server.world.gameevent.GameEvent.UNEQUIP;
        level.gameEvent(ev, new Vec3(x, y, z), org.domi.furnace.server.world.gameevent.GameEventContext.of(source));
    }

    /**
     * dispenseArmor: 앞 칸의 살아 있고 관전자가 아니며 받을 수 있는 첫 생물에게 하나를 입힌다. 몹이면 떨굼 확률 2.0·사라지지 않게.
     * 입혔으면 남은 스택, 아니면 null.
     */
    public static ItemStack dispenseArmor(ChunkLevel level, Pos pos, ItemStack stack) {
        for (Object e : wearersIn(level, pos)) {
            boolean alive = e instanceof Player p ? p.isAlive() && !p.isSpectator() : ((LivingEntity) e).isAlive() && !((LivingEntity) e).isSpectator();
            if (!alive || !canTakeItem(e, stack)) continue;
            EquipmentSlot slot = slotFor(e, stack);
            setItemSlot(level, e, slot, stack.withCount(1));
            if (e instanceof Mob m) {
                m.setDropChance(slot, 2.0F);
                m.persistenceRequired = true;
            }
            return stack.withCount(stack.count() - 1);
        }
        return null;
    }

    // ------------------------------------------------------------------ 안장·말 갑옷·상자

    /** 안장: 살아 있는 Saddleable(말 무리·돼지·스트라이더) 중 안장이 없고 얹을 수 있는 첫째에 하나(소리 분류 blocks). 못 했으면 null. */
    public static ItemStack saddle(ChunkLevel level, Pos pos, ItemStack stack) {
        for (LivingEntity e : livingIn(level, pos)) {
            if (e instanceof AbstractHorse h && !h.isSaddled() && h.isSaddleable()) {
                h.equipSaddle(stack.withCount(1));
                return stack.withCount(stack.count() - 1);
            }
            if (e instanceof Pig p && !p.isSaddled() && p.isAlive() && !p.isBaby()) {
                p.setSaddled(true);
                level.playSound(null, p.x(), p.y(), p.z(), "minecraft:entity.pig.saddle", "blocks", 0.5F, 1.0F);
                return stack.withCount(stack.count() - 1);
            }
            if (e instanceof Strider s && !s.isSaddled() && s.isAlive() && !s.isBaby()) {
                s.entityData.set(Strider.DATA_SADDLE_ID, true);
                level.playSound(null, s.x(), s.y(), s.z(), "minecraft:entity.strider.saddle", "blocks", 0.5F, 1.0F);
                return stack.withCount(stack.count() - 1);
            }
        }
        return null;
    }

    /** 말 갑옷: 살아 있고 몸통 칸을 쓰는 말 무리 중 그 갑옷을 입고, 아직 안 입었고, 길들여진 첫째에 하나. 못 했으면 null. */
    public static ItemStack horseArmor(ChunkLevel level, Pos pos, ItemStack stack) {
        for (LivingEntity e : livingIn(level, pos)) {
            if (!(e instanceof AbstractHorse h) || !h.isAlive() || !h.canUseBodySlot()) continue;
            if (h.isBodyArmorItem(stack) && !h.isWearingBodyArmor() && h.isTamed()) {
                h.setBodyArmorItem(stack.withCount(1));
                return stack.withCount(stack.count() - 1);
            }
        }
        return null;
    }

    /** 상자: 살아 있고 상자가 없는 상자 말 무리 중 길들여진 첫째에 싣는다(getSlot(499).set — 소리 없음). 못 했으면 null. */
    public static ItemStack chest(ChunkLevel level, Pos pos, ItemStack stack) {
        for (LivingEntity e : livingIn(level, pos)) {
            if (!(e instanceof AbstractChestedHorse h) || !h.isAlive() || h.hasChest()) continue;
            if (h.isTamed()) {
                h.setChest(true);
                h.createInventory();
                return stack.withCount(stack.count() - 1);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 가위·솔

    /** 가위: 관전자가 아닌 생물 중 깎을 수 있는 첫째(양·버섯소·눈 골렘·보그드)를 깎는다(소리 분류 blocks). */
    public static boolean shear(ChunkLevel level, Pos pos) {
        for (LivingEntity e : livingIn(level, pos)) {
            if (e.isSpectator()) continue;
            if (e instanceof Sheep s && s.readyForShearing()) s.shear("blocks");
            else if (e instanceof Mooshroom m && m.readyForShearing()) m.shear("blocks");
            else if (e instanceof SnowGolem g && g.readyForShearing()) g.shear("blocks");
            else if (e instanceof Bogged b && b.readyForShearing()) b.shear("blocks");
            else continue;
            level.gameEvent("minecraft:shear", pos, null, -1);
            return true;
        }
        return false;
    }

    /** 솔: 관전자가 아닌 아르마딜로가 있으면 첫째부터 비늘을 턴다. 하나라도 턴 게 있으면 true. 없으면 false, 아르마딜로가 하나도 없으면 null. */
    public static Boolean brush(ChunkLevel level, Pos pos) {
        boolean any = false;
        for (LivingEntity e : livingIn(level, pos)) {
            if (!(e instanceof Armadillo a) || e.isSpectator()) continue;
            any = true;
            if (a.brushOffScute()) return true;
        }
        return any ? Boolean.FALSE : null;
    }
}
