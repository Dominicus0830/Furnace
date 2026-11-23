package net.minecraft.world.entity.animal;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * 개구리 무늬(Mojang 이름 shim, 엔티티 데이터 설계 D4): 바닐라 record 처럼 무늬 그림 위치 하나. 값은 내장 레지스트리 {@code minecraft:frog_variant}
 * ({@link org.domi.furnace.compat.nms.NmsVariants}) 의 것이다. {@code bootstrap}·{@code STREAM_CODEC} 은 두지 않았다(레지스트리는 서버 시작 때 얼어 있다).
 */
record FrogVariant {

    public static final ResourceKey<FrogVariant> TEMPERATE = createKey("temperate");
    public static final ResourceKey<FrogVariant> WARM = createKey("warm");
    public static final ResourceKey<FrogVariant> COLD = createKey("cold");

    private static ResourceKey<FrogVariant> createKey(String name) {
        return ResourceKey.create(Registries.FROG_VARIANT, ResourceLocation.withDefaultNamespace(name));
    }
}
