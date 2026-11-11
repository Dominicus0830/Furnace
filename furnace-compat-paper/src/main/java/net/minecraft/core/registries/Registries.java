package net.minecraft.core.registries;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * 레지스트리 키(Mojang 이름 shim, N0). 키만 있다 — 레지스트리 자체는 {@link BuiltInRegistries} 가 가진 것(블록·아이템)만 꺼낼 수 있다.
 * 값 타입 shim 이 아직 없는 레지스트리는 값 타입을 {@code Object} 로 두었다(바이트코드 설명자는 ResourceKey 하나라 바닐라로 컴파일한 플러그인과 링크된다).
 * 키 이름은 Furnace 내장·동기화 레지스트리 목록(registries/*.txt)에 있는 것과 dimension(바닐라 DIMENSION·LEVEL_STEM, 같은 키)만 두었다.
 */
public final class Registries {

    private Registries() {}

    public static final ResourceKey<Registry<Block>> BLOCK = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("block"));
    public static final ResourceKey<Registry<Item>> ITEM = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("item"));
    public static final ResourceKey<Registry<Object>> ENTITY_TYPE = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("entity_type"));
    public static final ResourceKey<Registry<net.minecraft.world.level.material.Fluid>> FLUID = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("fluid"));
    public static final ResourceKey<Registry<Object>> GAME_EVENT = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("game_event"));
    public static final ResourceKey<Registry<Object>> INSTRUMENT = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("instrument"));
    public static final ResourceKey<Registry<Object>> CAT_VARIANT = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("cat_variant"));
    public static final ResourceKey<Registry<Object>> POINT_OF_INTEREST_TYPE = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("point_of_interest_type"));
    public static final ResourceKey<Registry<Object>> DATA_COMPONENT_TYPE = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("data_component_type"));
    public static final ResourceKey<Registry<Object>> POTION = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("potion"));
    public static final ResourceKey<Registry<Object>> MOB_EFFECT = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("mob_effect"));
    public static final ResourceKey<Registry<Object>> ATTRIBUTE = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("attribute"));
    public static final ResourceKey<Registry<Object>> SOUND_EVENT = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("sound_event"));
    public static final ResourceKey<Registry<Object>> MAP_DECORATION_TYPE = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("map_decoration_type"));
    public static final ResourceKey<Registry<Object>> ARMOR_MATERIAL = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("armor_material"));
    public static final ResourceKey<Registry<Object>> MENU = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("menu"));
    public static final ResourceKey<Registry<Object>> STAT_TYPE = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("stat_type"));
    public static final ResourceKey<Registry<Object>> CUSTOM_STAT = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("custom_stat"));
    public static final ResourceKey<Registry<Object>> BANNER_PATTERN = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("banner_pattern"));
    public static final ResourceKey<Registry<Object>> CHAT_TYPE = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("chat_type"));
    public static final ResourceKey<Registry<Object>> DAMAGE_TYPE = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("damage_type"));
    public static final ResourceKey<Registry<Object>> DIMENSION_TYPE = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("dimension_type"));
    public static final ResourceKey<Registry<Object>> ENCHANTMENT = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("enchantment"));
    public static final ResourceKey<Registry<Object>> JUKEBOX_SONG = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("jukebox_song"));
    public static final ResourceKey<Registry<Object>> PAINTING_VARIANT = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("painting_variant"));
    public static final ResourceKey<Registry<Object>> TRIM_MATERIAL = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("trim_material"));
    public static final ResourceKey<Registry<Object>> TRIM_PATTERN = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("trim_pattern"));
    public static final ResourceKey<Registry<Object>> WOLF_VARIANT = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("wolf_variant"));
    public static final ResourceKey<Registry<Object>> BIOME = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("worldgen/biome"));
    public static final ResourceKey<Registry<Object>> DIMENSION = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("dimension"));
    public static final ResourceKey<Registry<Object>> LEVEL_STEM = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("dimension"));
}
