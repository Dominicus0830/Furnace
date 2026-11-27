package net.minecraft.world.level.storage.loot.parameters;

import net.minecraft.resources.ResourceLocation;

/** 바닐라 1.21.1 의 전리품 매개변수 열두 개(값 형식은 필드 제네릭 그대로). */
class LootContextParams {

    public static final LootContextParam<net.minecraft.world.entity.Entity> THIS_ENTITY = create("this_entity");
    public static final LootContextParam<net.minecraft.world.entity.player.Player> LAST_DAMAGE_PLAYER = create("last_damage_player");
    public static final LootContextParam DAMAGE_SOURCE = create("damage_source");
    public static final LootContextParam<net.minecraft.world.entity.Entity> ATTACKING_ENTITY = create("attacking_entity");
    public static final LootContextParam<net.minecraft.world.entity.Entity> DIRECT_ATTACKING_ENTITY = create("direct_attacking_entity");
    public static final LootContextParam<net.minecraft.world.phys.Vec3> ORIGIN = create("origin");
    public static final LootContextParam<net.minecraft.world.level.block.state.BlockState> BLOCK_STATE = create("block_state");
    public static final LootContextParam<net.minecraft.world.level.block.entity.BlockEntity> BLOCK_ENTITY = create("block_entity");
    public static final LootContextParam<net.minecraft.world.item.ItemStack> TOOL = create("tool");
    public static final LootContextParam<Float> EXPLOSION_RADIUS = create("explosion_radius");
    public static final LootContextParam<Integer> ENCHANTMENT_LEVEL = create("enchantment_level");
    public static final LootContextParam<Boolean> ENCHANTMENT_ACTIVE = create("enchantment_active");

    private static <T> LootContextParam<T> create(String name) { return new LootContextParam<>(ResourceLocation.withDefaultNamespace(name)); }
}
