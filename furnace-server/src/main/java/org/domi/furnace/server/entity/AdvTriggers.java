// 발전 과제 트리거를 게임 사건 쪽에서 쉽게 부르기 위한 정적 도우미(상대 엔티티 값은 부르는 스레드에서 떠 둔다).
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.loot.LootEntity;

public final class AdvTriggers {

    private AdvTriggers() {}

    /** 바닐라 SUMMONED_ENTITY: entity 의 상자를 inflate 한 안의 플레이어에게(소환한 몹은 기준을 채운다). */
    public static void summonedNear(BlockLevel level, Entity entity, double inflate) {
        LootEntity snap = KillCredit.lootOf(entity);
        level.forEachPlayerIn(entity.boundingBox().inflate(inflate), p -> p.triggerAdvancement(a -> a.triggerSummoned(snap)));
    }

    /** 바닐라 ITEM_DURABILITY_CHANGED.trigger(player, stack, 새 피해 값) — 깎기 전 스택으로 부른다. */
    public static void durability(Object owner, ItemStack before, int newDamage) {
        if (!(owner instanceof Player p)) return;
        var snap = org.domi.furnace.server.ServerCore.lootStackOf(before);
        p.triggerAdvancement(a -> a.triggerDurability(snap, newDamage));
    }

    /** 아이템 엔티티를 던진 플레이어(UUID 로 서버 전체에서 찾는다 — 같은 region 이 아닐 수 있다). 없으면 null. */
    public static Player throwerPlayer(ItemEntity ie) {
        java.util.UUID u = ie.thrower();
        if (u == null || ie.level() == null) return null;
        Player[] found = {null};
        ie.level().forEachPlayer(p -> { if (p.uuid().equals(u)) found[0] = p; });
        return found[0];
    }

    /** LivingEntity.onItemPickup: 던진 쪽이 플레이어면 그에게 thrown_item_picked_up_by_entity(스택, 주운 생물). */
    public static void pickedUpByEntity(ItemEntity ie, ItemStack stack, Rider picker) {
        Player thrower = throwerPlayer(ie);
        if (thrower == null) return;
        var item = org.domi.furnace.server.ServerCore.lootStackOf(stack);
        var ent = KillCredit.lootOf(picker);
        thrower.triggerAdvancement(a -> a.triggerItemEntity("minecraft:thrown_item_picked_up_by_entity", item, ent));
    }

    /** ServerPlayer.onItemPickup: super(위) 다음, 던진 쪽(엔티티)이 있으면 줍은 플레이어에게 thrown_item_picked_up_by_player. */
    public static void pickedUpByPlayer(ItemEntity ie, ItemStack stack, Player picker) {
        pickedUpByEntity(ie, stack, picker);
        Player thrower = throwerPlayer(ie);
        Rider owner = thrower != null ? thrower : ie.getOwnerEntity() instanceof Rider r ? r : null;
        if (owner == null) return;
        var item = org.domi.furnace.server.ServerCore.lootStackOf(stack);
        var ent = KillCredit.lootOf(owner);
        picker.triggerAdvancement(a -> a.triggerItemEntity("minecraft:thrown_item_picked_up_by_player", item, ent));
    }

    /** 엔티티 여럿의 값을 한꺼번에 뜬다. */
    public static List<LootEntity> lootOfAll(java.util.Collection<? extends Rider> riders) {
        List<LootEntity> l = new ArrayList<>();
        for (Rider r : riders) l.add(KillCredit.lootOf(r));
        return l;
    }
}
