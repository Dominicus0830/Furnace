package net.minecraft.core.particles;

import net.minecraft.world.item.ItemStack;

/** 아이템 입자(Mojang 이름 shim). 바닐라처럼 스택을 복사하지 않는다. */
public class ItemParticleOption implements ParticleOptions {

    private final ParticleType<ItemParticleOption> kind;
    private final ItemStack stack;

    public ItemParticleOption(ParticleType<ItemParticleOption> type, ItemStack itemStack) {
        if (itemStack.isEmpty()) throw new IllegalArgumentException("Empty stacks are not allowed");
        this.kind = type;
        this.stack = itemStack;
    }

    @Override
    public ParticleType<ItemParticleOption> getType() { return kind; }

    public ItemStack getItem() { return stack; }
}
