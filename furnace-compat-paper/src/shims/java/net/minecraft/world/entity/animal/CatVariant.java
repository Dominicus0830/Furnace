package net.minecraft.world.entity.animal;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * 고양이 무늬(Mojang 이름 shim, 엔티티 데이터 설계 D4): 바닐라 record 처럼 무늬 그림 위치 하나. 값은 내장 레지스트리 {@code minecraft:cat_variant}
 * ({@link org.domi.furnace.compat.nms.NmsVariants}) 의 것이다. {@code bootstrap}·{@code STREAM_CODEC} 은 두지 않았다(레지스트리는 서버 시작 때 얼어 있다).
 */
record CatVariant {

    public static final ResourceKey<CatVariant> TABBY = createKey("tabby");
    public static final ResourceKey<CatVariant> BLACK = createKey("black");
    public static final ResourceKey<CatVariant> RED = createKey("red");
    public static final ResourceKey<CatVariant> SIAMESE = createKey("siamese");
    public static final ResourceKey<CatVariant> BRITISH_SHORTHAIR = createKey("british_shorthair");
    public static final ResourceKey<CatVariant> CALICO = createKey("calico");
    public static final ResourceKey<CatVariant> PERSIAN = createKey("persian");
    public static final ResourceKey<CatVariant> RAGDOLL = createKey("ragdoll");
    public static final ResourceKey<CatVariant> WHITE = createKey("white");
    public static final ResourceKey<CatVariant> JELLIE = createKey("jellie");
    public static final ResourceKey<CatVariant> ALL_BLACK = createKey("all_black");

    private static ResourceKey<CatVariant> createKey(String name) {
        return ResourceKey.create(Registries.CAT_VARIANT, ResourceLocation.withDefaultNamespace(name));
    }
}
