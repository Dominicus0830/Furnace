package net.minecraft.world.entity;

import org.bukkit.craftbukkit.entity.CraftLivingEntity;

/** 살아 있는 엔티티 shim(Mojang 이름, H6 최소 표면). 체력 읽기·쓰기는 Furnace 엔티티 API 로 간다. */
public class LivingEntity extends Entity {

    /** 호환 모듈 전용(CraftLivingEntity 가 만든다). */
    public LivingEntity(CraftLivingEntity bukkit) { super(bukkit); }

    @Override
    public CraftLivingEntity getBukkitEntity() { return (CraftLivingEntity) super.getBukkitEntity(); }

    public float getHealth() { return (float) getBukkitEntity().getHealth(); }

    public float getMaxHealth() { return (float) getBukkitEntity().getMaxHealth(); }

    /** 바닐라처럼 0 과 최대 체력 사이로 자른다. */
    public void setHealth(float health) {
        float max = getMaxHealth();
        getBukkitEntity().setHealth(Math.max(0f, Math.min(health, max)));
    }

    /** 바닐라 LivingEntity: 지워지지 않았고 체력이 0 보다 크다. */
    @Override
    public boolean isAlive() { return !isRemoved() && getHealth() > 0f; }
}
