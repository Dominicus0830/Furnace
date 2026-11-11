package net.minecraft.core.registries;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * 레지스트리 키(Mojang 이름 shim, N0). 키만 있다 — 레지스트리 자체는 {@link BuiltInRegistries} 가 가진 것(지금은 블록)만 꺼낼 수 있다.
 * 값 타입 shim 이 아직 없는 레지스트리의 키는 두지 않았다(없는 필드 — 호출 위치에서 실패).
 */
public final class Registries {

    private Registries() {}

    public static final ResourceKey<Registry<Block>> BLOCK = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("block"));
    public static final ResourceKey<Registry<Item>> ITEM = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("item"));
}
