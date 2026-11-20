package net.minecraft.world.entity.ai.attributes;

import net.minecraft.core.Holder;
import org.domi.furnace.compat.nms.NmsAttributes;

/**
 * 바닐라 속성 상수(Mojang 이름 shim, NMS shim 3번 묶음). 31개, 바닐라 1.21.1 선언 순서·타입(Holder&lt;Attribute&gt;) 그대로다.
 * 값은 {@code BuiltInRegistries.ATTRIBUTE} 의 등록된 손잡이(Holder.Reference)다.
 */
public class Attributes {

    public static final Holder<Attribute> ARMOR = NmsAttributes.holder("minecraft:generic.armor");
    public static final Holder<Attribute> ARMOR_TOUGHNESS = NmsAttributes.holder("minecraft:generic.armor_toughness");
    public static final Holder<Attribute> ATTACK_DAMAGE = NmsAttributes.holder("minecraft:generic.attack_damage");
    public static final Holder<Attribute> ATTACK_KNOCKBACK = NmsAttributes.holder("minecraft:generic.attack_knockback");
    public static final Holder<Attribute> ATTACK_SPEED = NmsAttributes.holder("minecraft:generic.attack_speed");
    public static final Holder<Attribute> BLOCK_BREAK_SPEED = NmsAttributes.holder("minecraft:player.block_break_speed");
    public static final Holder<Attribute> BLOCK_INTERACTION_RANGE = NmsAttributes.holder("minecraft:player.block_interaction_range");
    public static final Holder<Attribute> BURNING_TIME = NmsAttributes.holder("minecraft:generic.burning_time");
    public static final Holder<Attribute> EXPLOSION_KNOCKBACK_RESISTANCE = NmsAttributes.holder("minecraft:generic.explosion_knockback_resistance");
    public static final Holder<Attribute> ENTITY_INTERACTION_RANGE = NmsAttributes.holder("minecraft:player.entity_interaction_range");
    public static final Holder<Attribute> FALL_DAMAGE_MULTIPLIER = NmsAttributes.holder("minecraft:generic.fall_damage_multiplier");
    public static final Holder<Attribute> FLYING_SPEED = NmsAttributes.holder("minecraft:generic.flying_speed");
    public static final Holder<Attribute> FOLLOW_RANGE = NmsAttributes.holder("minecraft:generic.follow_range");
    public static final Holder<Attribute> GRAVITY = NmsAttributes.holder("minecraft:generic.gravity");
    public static final Holder<Attribute> JUMP_STRENGTH = NmsAttributes.holder("minecraft:generic.jump_strength");
    public static final Holder<Attribute> KNOCKBACK_RESISTANCE = NmsAttributes.holder("minecraft:generic.knockback_resistance");
    public static final Holder<Attribute> LUCK = NmsAttributes.holder("minecraft:generic.luck");
    public static final Holder<Attribute> MAX_ABSORPTION = NmsAttributes.holder("minecraft:generic.max_absorption");
    public static final Holder<Attribute> MAX_HEALTH = NmsAttributes.holder("minecraft:generic.max_health");
    public static final Holder<Attribute> MINING_EFFICIENCY = NmsAttributes.holder("minecraft:player.mining_efficiency");
    public static final Holder<Attribute> MOVEMENT_EFFICIENCY = NmsAttributes.holder("minecraft:generic.movement_efficiency");
    public static final Holder<Attribute> MOVEMENT_SPEED = NmsAttributes.holder("minecraft:generic.movement_speed");
    public static final Holder<Attribute> OXYGEN_BONUS = NmsAttributes.holder("minecraft:generic.oxygen_bonus");
    public static final Holder<Attribute> SAFE_FALL_DISTANCE = NmsAttributes.holder("minecraft:generic.safe_fall_distance");
    public static final Holder<Attribute> SCALE = NmsAttributes.holder("minecraft:generic.scale");
    public static final Holder<Attribute> SNEAKING_SPEED = NmsAttributes.holder("minecraft:player.sneaking_speed");
    public static final Holder<Attribute> SPAWN_REINFORCEMENTS_CHANCE = NmsAttributes.holder("minecraft:zombie.spawn_reinforcements");
    public static final Holder<Attribute> STEP_HEIGHT = NmsAttributes.holder("minecraft:generic.step_height");
    public static final Holder<Attribute> SUBMERGED_MINING_SPEED = NmsAttributes.holder("minecraft:player.submerged_mining_speed");
    public static final Holder<Attribute> SWEEPING_DAMAGE_RATIO = NmsAttributes.holder("minecraft:player.sweeping_damage_ratio");
    public static final Holder<Attribute> WATER_MOVEMENT_EFFICIENCY = NmsAttributes.holder("minecraft:generic.water_movement_efficiency");

    public Attributes() {}
}
