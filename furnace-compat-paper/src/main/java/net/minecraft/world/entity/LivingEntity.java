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
     * 바닐라 {@code LivingEntity#getAttribute(Holder)}: 이 엔티티의 그 속성(가지지 않으면 null). 서버의 그 엔티티 속성을 보는 shim 이다 —
     * 주인 스레드면 곧바로 읽고 쓰고, 다른 스레드의 쓰기는 주인 스레드로 넘긴다(NMS shim 3번 묶음, {@link net.minecraft.world.entity.ai.attributes.AttributeInstance}).
     */
    public net.minecraft.world.entity.ai.attributes.AttributeInstance getAttribute(
            net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        return net.minecraft.world.entity.ai.attributes.AttributeInstance.furnaceOf(
                (org.domi.furnace.api.entity.LivingEntity) getBukkitEntity().furnaceHandle(), attribute);
    }

    /** 바닐라: 그 속성의 값. 가지지 않으면 IllegalArgumentException("Can't find attribute " + 이름) — 바닐라 AttributeSupplier 와 같다. */
    public double getAttributeValue(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        return need(attribute).getValue();
    }

    /** 바닐라: 그 속성의 기본값(수정자를 뺀 값). 가지지 않으면 {@link #getAttributeValue} 와 같은 예외. */
    public double getAttributeBaseValue(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        return need(attribute).getBaseValue();
    }

    private net.minecraft.world.entity.ai.attributes.AttributeInstance need(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        var i = getAttribute(attribute);
        if (i == null) throw new IllegalArgumentException("Can't find attribute " + attribute.getRegisteredName());
        return i;
    }

    /**
     * 바닐라 {@code LivingEntity#getItemInHand}: 그 손의 스택 객체. 월드의 엔티티는 아직 이 길로 읽지 않는다(UnsupportedNmsCall — Bukkit
     * {@code getEquipment()} 를 쓴다). 바닐라 생성자로 만든 월드에 없는 플레이어는 된다({@code ServerPlayer}).
     */
    public net.minecraft.world.item.ItemStack getItemInHand(net.minecraft.world.InteractionHand hand) {
        throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("LivingEntity#getItemInHand", "reading the live hand stack of an entity in a world is not"
                + " available yet - use the Bukkit equipment API");
    }

    /** 바닐라 {@code LivingEntity#getMainHandItem}: 주 손의 스택({@link #getItemInHand} 와 같은 범위 — 접속한 플레이어는 손 칸의 살아 있는 보기). */
    public net.minecraft.world.item.ItemStack getMainHandItem() { return getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND); }

    /** 바닐라 {@code LivingEntity#setItemInHand}: 그 객체를 손에 둔다. 지원 범위는 {@link #getItemInHand} 와 같다. */
    public void setItemInHand(net.minecraft.world.InteractionHand hand, net.minecraft.world.item.ItemStack stack) {
        throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("LivingEntity#setItemInHand", "changing the live hand stack of an entity in a world is not"
                + " available yet - use the Bukkit equipment API");
    }

    /** 바닐라 LivingEntity: 지워지지 않았고 체력이 0 보다 크다. */
    @Override
    public boolean isAlive() { return !isRemoved() && getHealth() > 0f; }

    /** 바닐라 isFallFlying: 공유 플래그 7(겉날개로 나는 중). */
    public boolean isFallFlying() { return (furnaceLook("LivingEntity#isFallFlying").sharedFlags() & 1 << 7) != 0; }
}
