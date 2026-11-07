package net.minecraft.world.level;

import net.minecraft.world.item.Item;

/** 아이템이 될 수 있는 것(Mojang 이름). shim 은 {@link Item} 하나만 구현한다. */
public interface ItemLike {

    Item asItem();
}
