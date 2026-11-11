package net.minecraft.core;

import net.minecraft.resources.ResourceLocation;

/** 모르는 이름·번호에 기본값을 돌려주는 레지스트리(Mojang 이름 shim, N0). 블록은 기본값이 minecraft:air. */
public interface DefaultedRegistry<T> extends Registry<T> {

    ResourceLocation getDefaultKey();
}
