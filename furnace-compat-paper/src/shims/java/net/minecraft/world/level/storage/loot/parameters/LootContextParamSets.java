package net.minecraft.world.level.storage.loot.parameters;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** 바닐라 1.21.1 의 전리품 매개변수 묶음 23개 — 필수·허용 키와 등록 이름은 바닐라 클래스를 실행해 읽은 값이다(2026-11-27). */
class LootContextParamSets {

    private static final Map<ResourceLocation, LootContextParamSet> REGISTRY = new LinkedHashMap<>();

    public static final LootContextParamSet EMPTY = register("empty", LootContextParamSet.builder().build());
    public static final LootContextParamSet CHEST = register("chest", LootContextParamSet.builder().required(LootContextParams.ORIGIN).optional(LootContextParams.THIS_ENTITY).build());
    public static final LootContextParamSet COMMAND = register("command", LootContextParamSet.builder().required(LootContextParams.ORIGIN).optional(LootContextParams.THIS_ENTITY).build());
    public static final LootContextParamSet SELECTOR = register("selector", LootContextParamSet.builder().required(LootContextParams.ORIGIN).required(LootContextParams.THIS_ENTITY).build());
    public static final LootContextParamSet FISHING = register("fishing", LootContextParamSet.builder().required(LootContextParams.ORIGIN).required(LootContextParams.TOOL).optional(LootContextParams.THIS_ENTITY).build());
    public static final LootContextParamSet ENTITY = register("entity", LootContextParamSet.builder().required(LootContextParams.THIS_ENTITY).required(LootContextParams.ORIGIN).required(LootContextParams.DAMAGE_SOURCE).optional(LootContextParams.ATTACKING_ENTITY).optional(LootContextParams.DIRECT_ATTACKING_ENTITY).optional(LootContextParams.LAST_DAMAGE_PLAYER).build());
    public static final LootContextParamSet EQUIPMENT = register("equipment", LootContextParamSet.builder().required(LootContextParams.ORIGIN).required(LootContextParams.THIS_ENTITY).build());
    public static final LootContextParamSet ARCHAEOLOGY = register("archaeology", LootContextParamSet.builder().required(LootContextParams.ORIGIN).optional(LootContextParams.THIS_ENTITY).build());
    public static final LootContextParamSet GIFT = register("gift", LootContextParamSet.builder().required(LootContextParams.ORIGIN).required(LootContextParams.THIS_ENTITY).build());
    public static final LootContextParamSet PIGLIN_BARTER = register("barter", LootContextParamSet.builder().required(LootContextParams.THIS_ENTITY).build());
    public static final LootContextParamSet VAULT = register("vault", LootContextParamSet.builder().required(LootContextParams.ORIGIN).optional(LootContextParams.THIS_ENTITY).build());
    public static final LootContextParamSet ADVANCEMENT_REWARD = register("advancement_reward", LootContextParamSet.builder().required(LootContextParams.THIS_ENTITY).required(LootContextParams.ORIGIN).build());
    public static final LootContextParamSet ADVANCEMENT_ENTITY = register("advancement_entity", LootContextParamSet.builder().required(LootContextParams.THIS_ENTITY).required(LootContextParams.ORIGIN).build());
    public static final LootContextParamSet ADVANCEMENT_LOCATION = register("advancement_location", LootContextParamSet.builder().required(LootContextParams.ORIGIN).required(LootContextParams.THIS_ENTITY).required(LootContextParams.TOOL).required(LootContextParams.BLOCK_STATE).build());
    public static final LootContextParamSet BLOCK_USE = register("block_use", LootContextParamSet.builder().required(LootContextParams.ORIGIN).required(LootContextParams.THIS_ENTITY).required(LootContextParams.BLOCK_STATE).build());
    public static final LootContextParamSet ALL_PARAMS = register("generic", LootContextParamSet.builder().required(LootContextParams.THIS_ENTITY).required(LootContextParams.LAST_DAMAGE_PLAYER).required(LootContextParams.DAMAGE_SOURCE).required(LootContextParams.ATTACKING_ENTITY).required(LootContextParams.DIRECT_ATTACKING_ENTITY).required(LootContextParams.ORIGIN).required(LootContextParams.BLOCK_STATE).required(LootContextParams.BLOCK_ENTITY).required(LootContextParams.TOOL).required(LootContextParams.EXPLOSION_RADIUS).build());
    public static final LootContextParamSet BLOCK = register("block", LootContextParamSet.builder().required(LootContextParams.BLOCK_STATE).required(LootContextParams.ORIGIN).required(LootContextParams.TOOL).optional(LootContextParams.THIS_ENTITY).optional(LootContextParams.BLOCK_ENTITY).optional(LootContextParams.EXPLOSION_RADIUS).build());
    public static final LootContextParamSet SHEARING = register("shearing", LootContextParamSet.builder().required(LootContextParams.ORIGIN).optional(LootContextParams.THIS_ENTITY).build());
    public static final LootContextParamSet ENCHANTED_DAMAGE = register("enchanted_damage", LootContextParamSet.builder().required(LootContextParams.THIS_ENTITY).required(LootContextParams.ENCHANTMENT_LEVEL).required(LootContextParams.ORIGIN).required(LootContextParams.DAMAGE_SOURCE).optional(LootContextParams.DIRECT_ATTACKING_ENTITY).optional(LootContextParams.ATTACKING_ENTITY).build());
    public static final LootContextParamSet ENCHANTED_ITEM = register("enchanted_item", LootContextParamSet.builder().required(LootContextParams.TOOL).required(LootContextParams.ENCHANTMENT_LEVEL).build());
    public static final LootContextParamSet ENCHANTED_LOCATION = register("enchanted_location", LootContextParamSet.builder().required(LootContextParams.ENCHANTMENT_LEVEL).required(LootContextParams.ORIGIN).required(LootContextParams.ENCHANTMENT_ACTIVE).required(LootContextParams.THIS_ENTITY).build());
    public static final LootContextParamSet ENCHANTED_ENTITY = register("enchanted_entity", LootContextParamSet.builder().required(LootContextParams.THIS_ENTITY).required(LootContextParams.ENCHANTMENT_LEVEL).required(LootContextParams.ORIGIN).build());
    public static final LootContextParamSet HIT_BLOCK = register("hit_block", LootContextParamSet.builder().required(LootContextParams.THIS_ENTITY).required(LootContextParams.ENCHANTMENT_LEVEL).required(LootContextParams.ORIGIN).required(LootContextParams.BLOCK_STATE).build());

    private static LootContextParamSet register(String name, LootContextParamSet set) {
        ResourceLocation key = ResourceLocation.withDefaultNamespace(name);
        if (REGISTRY.put(key, set) != null) throw new IllegalStateException("Loot table parameter set " + key + " is already registered");
        return set;
    }
}
