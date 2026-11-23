package net.minecraft.world.entity.item;

import net.minecraft.world.entity.Entity;
import org.bukkit.craftbukkit.entity.CraftEntity;

/**
 * 떨어진 아이템 엔티티(Mojang 이름 shim, NMS shim 5번 묶음). {@code CraftItem#getHandle()} 이 이것을 준다 — 플러그인이 {@code instanceof ItemEntity} 로
 * 종류를 가리고 {@link Entity} 의 자리 읽기·{@code moveTo} 를 쓴다. 아이템은 Furnace 핸들이 준 지금 값의 복사본이다.
 */
class ItemEntity {

    /** 바닐라: 떨어진 아이템 스택(Furnace 핸들이 준 값의 복사본 — 고쳐도 엔티티는 안 바뀐다). 아이템 정보가 없으면 빈 스택. */
    public net.minecraft.world.item.ItemStack getItem() {
        var stack = getBukkitEntity().furnaceHandle().droppedItem();
        return stack == null || stack.isEmpty() ? net.minecraft.world.item.ItemStack.EMPTY : net.minecraft.world.item.ItemStack.fromFurnace(stack, stack.amount());
    }
}
