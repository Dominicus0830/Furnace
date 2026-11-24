package net.minecraft.core.particles;

import net.minecraft.world.item.ItemStack;

/** 아이템 입자(Mojang 이름 shim). 바닐라처럼 스택을 복사하지 않는다. */
public class ItemParticleOption implements ParticleOptions {

    private final ParticleType<ItemParticleOption> type;
    private final ItemStack itemStack;

    public ItemParticleOption(ParticleType<ItemParticleOption> type, ItemStack itemStack) {
        if (itemStack.isEmpty()) throw new IllegalArgumentException("Empty stacks are not allowed");
        this.type = type;
        this.itemStack = itemStack;
    }

    @Override
    public ParticleType<ItemParticleOption> getType() { return type; }

    public ItemStack getItem() { return itemStack; }
}
