// 바닐라 Bucketable: 물 양동이로 잡을 수 있는 몹(물고기 4종·아홀로틀·올챙이). 잡으면 몹 데이터를 양동이의 bucket_entity_data 에 담고,
// 양동이를 쏟으면(MobBucketItem) 그 데이터로 몹을 되살린다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public interface Bucketable {

    String BUCKET_ENTITY_DATA = "minecraft:bucket_entity_data";

    boolean fromBucket();

    void setFromBucket(boolean v);

    /** 빈 몸으로 만든 채운 양동이 스택(getBucketItemStack). */
    ItemStack getBucketItemStack();

    /** 잡을 때 나는 소리(getPickupSound). */
    String getPickupSound();

    /** saveToBucketTag: 양동이에 몹 데이터를 담은 새 스택. 기본은 공통 데이터만. */
    default ItemStack saveToBucketTag(ItemStack bucket) { return saveDefaultDataToBucketTag((Mob) this, bucket); }

    /** loadFromBucketTag: 양동이 데이터를 몹에 입힌다. 기본은 공통 데이터만. */
    default void loadFromBucketTag(NbtCompound tag) { loadDefaultDataFromBucketTag((Mob) this, tag); }

    /** 양동이 스택의 bucket_entity_data 사본(없으면 빈 것). */
    static NbtCompound bucketTag(ItemStack bucket) {
        return ItemTools.component(bucket, BUCKET_ENTITY_DATA) instanceof NbtCompound c ? c.copy() : new NbtCompound();
    }

    /** 양동이 스택의 bucket_entity_data 를 바꾼다(CustomData.update: 비면 지운다). */
    static ItemStack withBucketTag(ItemStack bucket, NbtCompound tag) {
        return ItemTools.with(bucket, BUCKET_ENTITY_DATA, tag.tags().isEmpty() ? null : tag);
    }

    /** Bucketable.saveDefaultDataToBucketTag: 이름(custom_name 컴포넌트)과 NoAI·Silent·NoGravity·Glowing·Invulnerable·Health. */
    static ItemStack saveDefaultDataToBucketTag(Mob mob, ItemStack bucket) {
        ItemStack s = bucket;
        if (mob.hasCustomName()) s = ItemTools.with(s, "minecraft:custom_name", mob.getCustomName().toJson());
        NbtCompound tag = bucketTag(s);
        if (mob.isNoAi()) tag.putBoolean("NoAI", true);
        if (mob.isSilent()) tag.putBoolean("Silent", true);
        if (mob.isNoGravity()) tag.putBoolean("NoGravity", true);
        if (mob.hasGlowingTag()) tag.putBoolean("Glowing", true);
        if (mob.isInvulnerable()) tag.putBoolean("Invulnerable", true);
        tag.putFloat("Health", mob.health());
        return withBucketTag(s, tag);
    }

    /** Bucketable.loadDefaultDataFromBucketTag: 있는 키만 입힌다. Health 는 숫자일 때만. */
    static void loadDefaultDataFromBucketTag(Mob mob, NbtCompound tag) {
        if (tag.contains("NoAI")) mob.setNoAi(tag.getBoolean("NoAI"));
        if (tag.contains("Silent")) mob.setSilent(tag.getBoolean("Silent"));
        if (tag.contains("NoGravity")) mob.setNoGravity(tag.getBoolean("NoGravity"));
        if (tag.contains("Glowing")) mob.setGlowingTag(tag.getBoolean("Glowing"));
        if (tag.contains("Invulnerable")) mob.setInvulnerable(tag.getBoolean("Invulnerable"));
        if (tag.containsNumber("Health")) mob.setHealth(tag.getFloat("Health"));
    }

    /**
     * Bucketable.bucketMobPickup: 물 양동이를 든 손으로 살아 있는 몹을 우클릭하면 소리 → 양동이에 담기 → 손 교체(남으면 인벤토리·바닥) →
     * filled_bucket 발전 과제 → 몹 제거. 처리했으면 true. 소유 region 스레드에서 부른다.
     */
    static boolean bucketMobPickup(Player p, int hand, ItemStack held, Mob mob, org.domi.furnace.server.world.Region region) {
        if (!(mob instanceof Bucketable b) || !ItemTools.itemName(held).equals("minecraft:water_bucket") || !mob.isAlive()) return false;
        mob.playSound(b.getPickupSound(), 1.0F, 1.0F);
        ItemStack bucket = b.saveToBucketTag(b.getBucketItemStack());
        var inv = p.inventory();
        int ws = hand == 0 ? inv.heldWindowSlot() : 45;
        // ItemUtils.createFilledResult(held, player, bucket, preventDuplicates=false): 무한 재료면 그대로 두고 채운 것은 인벤토리(안 되면 바닥)로
        ItemStack rest = p.vitals().instabuild() ? held : held.withCount(held.count() - 1);
        if (rest.isEmpty()) {
            inv.set(ws, bucket);
        } else {
            inv.set(ws, rest);
            ItemStack left = inv.add(bucket, false, 64, false);
            if (!left.isEmpty()) region.playerDrop(p, left, false, false);
        }
        p.runOnOwner(() -> {
            var adv = p.advancements();
            if (adv != null) adv.trigger("minecraft:filled_bucket", org.domi.furnace.server.ServerCore.lootStackOf(bucket));
        });
        mob.discard();
        return true;
    }
}
