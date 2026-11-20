package net.minecraft.world.entity;

import org.bukkit.craftbukkit.entity.CraftLivingEntity;

/** 살아 있는 엔티티 shim(Mojang 이름, H6 최소 표면). 체력 읽기·쓰기는 Furnace 엔티티 API 로 간다. */
public class LivingEntity extends Entity {

    /** 호환 모듈 전용(CraftLivingEntity 가 만든다). */
    public LivingEntity(CraftLivingEntity bukkit) { super(bukkit); }

    /** {@link EntityType#create} 전용: 아직 월드에 없는 살아 있는 엔티티. */
    LivingEntity(EntityType<?> type, net.minecraft.world.level.Level level) { super(type, level); }

    @Override
    public CraftLivingEntity getBukkitEntity() { return (CraftLivingEntity) super.getBukkitEntity(); }

    public float getHealth() { return (float) getBukkitEntity().getHealth(); }

    public float getMaxHealth() { return (float) getBukkitEntity().getMaxHealth(); }

    /** 바닐라처럼 0 과 최대 체력 사이로 자른다. */
    public void setHealth(float health) {
        float max = getMaxHealth();
        getBukkitEntity().setHealth(Math.max(0f, Math.min(health, max)));
    }

    /**
     * 바닐라 {@code LivingEntity#getItemInHand}: 그 손의 스택 객체. 월드의 엔티티는 아직 이 길로 읽지 않는다(UnsupportedNmsCall — Bukkit
     * {@code getEquipment()} 를 쓴다). 바닐라 생성자로 만든 월드에 없는 플레이어는 된다({@code ServerPlayer}).
     */
    public net.minecraft.world.item.ItemStack getItemInHand(net.minecraft.world.InteractionHand hand) {
        throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("LivingEntity#getItemInHand", "reading the live hand stack of an entity in a world is not"
                + " available yet - use the Bukkit equipment API");
    }

    /** 바닐라 {@code LivingEntity#setItemInHand}: 그 객체를 손에 둔다. 지원 범위는 {@link #getItemInHand} 와 같다. */
    public void setItemInHand(net.minecraft.world.InteractionHand hand, net.minecraft.world.item.ItemStack stack) {
        throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("LivingEntity#setItemInHand", "changing the live hand stack of an entity in a world is not"
                + " available yet - use the Bukkit equipment API");
    }

    /** 바닐라 LivingEntity: 지워지지 않았고 체력이 0 보다 크다. */
    @Override
    public boolean isAlive() { return !isRemoved() && getHealth() > 0f; }
}
