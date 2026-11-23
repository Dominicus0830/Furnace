package org.domi.furnace.compat.nms;

import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;

/**
 * 서버의 레지스트리 묶음 shim({@link RegistryAccess#SERVER}, N2b-6). 키 이름으로 {@link Registry} 를 꺼낸다 —
 * 블록·아이템·유체·소리·상태 효과·지도 표시 종류·주민 종류·직업·고양이/개구리 무늬(내장), 늑대·그림 무늬(데이터 팩, {@link NmsVariants}), 바이옴({@link NmsBiomes}), 설정된/배치된 지형지물·구조물({@link NmsWorldgen}).
 * 그 밖의 키는 비어 있다({@code registryOrThrow} 는 바닐라 문구 {@code Missing registry: …} 로 던진다).
 */
public final class NmsRegistryAccess implements RegistryAccess.Frozen {

    private static final java.util.List<ResourceKey<? extends Registry<?>>> KEYS = java.util.List.of(
            Registries.BLOCK, Registries.ITEM, Registries.ENTITY_TYPE, Registries.FLUID, Registries.SOUND_EVENT, Registries.MOB_EFFECT, Registries.MAP_DECORATION_TYPE,
            Registries.VILLAGER_TYPE, Registries.VILLAGER_PROFESSION, Registries.CAT_VARIANT, Registries.FROG_VARIANT, Registries.WOLF_VARIANT, Registries.PAINTING_VARIANT,
            Registries.BIOME, Registries.CONFIGURED_FEATURE, Registries.PLACED_FEATURE, Registries.STRUCTURE);

    @SuppressWarnings("unchecked")
    @Override
    public <E> Optional<Registry<E>> registry(ResourceKey<? extends Registry<? extends E>> key) {
        if (key == null) return Optional.empty();
        Registry<?> r = switch (key.location().toString()) {
            case "minecraft:block" -> BuiltInRegistries.BLOCK;
            case "minecraft:item" -> BuiltInRegistries.ITEM;
            case "minecraft:entity_type" -> BuiltInRegistries.ENTITY_TYPE;
            case "minecraft:fluid" -> BuiltInRegistries.FLUID;
            case "minecraft:sound_event" -> BuiltInRegistries.SOUND_EVENT;
            case "minecraft:mob_effect" -> BuiltInRegistries.MOB_EFFECT;
            case "minecraft:map_decoration_type" -> BuiltInRegistries.MAP_DECORATION_TYPE;
            case "minecraft:villager_type" -> BuiltInRegistries.VILLAGER_TYPE;
            case "minecraft:villager_profession" -> BuiltInRegistries.VILLAGER_PROFESSION;
            case "minecraft:cat_variant" -> BuiltInRegistries.CAT_VARIANT;
            case "minecraft:frog_variant" -> BuiltInRegistries.FROG_VARIANT;
            case "minecraft:wolf_variant" -> NmsVariants.wolves();
            case "minecraft:painting_variant" -> NmsVariants.paintings();
            case "minecraft:worldgen/biome" -> NmsBiomes.registry();
            case "minecraft:worldgen/configured_feature" -> NmsWorldgen.configuredFeatures();
            case "minecraft:worldgen/placed_feature" -> NmsWorldgen.placedFeatures();
            case "minecraft:worldgen/structure" -> NmsWorldgen.structures();
            default -> null;
        };
        return Optional.ofNullable((Registry<E>) r);
    }

    @Override
    public Stream<RegistryEntry<?>> registries() {
        return KEYS.stream().<RegistryEntry<?>>map(this::entry);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private RegistryEntry<?> entry(ResourceKey<? extends Registry<?>> k) {
        Registry r = (Registry) ((Optional) this.registry((ResourceKey) k)).orElseThrow();
        return new RegistryEntry(k, r);
    }

    @Override
    public String toString() { return "RegistryAccess[Furnace]"; }
}
