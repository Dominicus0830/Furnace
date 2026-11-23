package net.minecraft.world.item.crafting;

import net.minecraft.resources.ResourceLocation;

/** 레시피와 그 이름표(Mojang 이름 shim, 바닐라 레코드와 같은 모양). 레시피 자체는 생성기가 선언만 만든다. */
public record RecipeHolder<T extends Recipe<?>>(ResourceLocation id, T value) {
}
