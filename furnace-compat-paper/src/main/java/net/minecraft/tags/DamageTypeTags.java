package net.minecraft.tags;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

/** 피해 형식 태그 이름(바닐라 DamageTypeTags, 이름·순서 그대로). 내용은 서버에 걸린 태그다. */
public interface DamageTypeTags {

    TagKey<DamageType> DAMAGES_HELMET = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.withDefaultNamespace("damages_helmet"));
    TagKey<DamageType> BYPASSES_ARMOR = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.withDefaultNamespace("bypasses_armor"));
    TagKey<DamageType> BYPASSES_SHIELD = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.withDefaultNamespace("bypasses_shield"));
    TagKey<DamageType> BYPASSES_INVULNERABILITY = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.withDefaultNamespace("bypasses_invulnerability"));
    TagKey<DamageType> BYPASSES_COOLDOWN = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.withDefaultNamespace("bypasses_cooldown"));
    TagKey<DamageType> BYPASSES_EFFECTS = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.withDefaultNamespace("bypasses_effects"));
    TagKey<DamageType> BYPASSES_RESISTANCE = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.withDefaultNamespace("bypasses_resistance"));
    TagKey<DamageType> BYPASSES_ENCHANTMENTS = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.withDefaultNamespace("bypasses_enchantments"));
    TagKey<DamageType> IS_FIRE = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.withDefaultNamespace("is_fire"));
    TagKey<DamageType> IS_PROJECTILE = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.withDefaultNamespace("is_projectile"));
    TagKey<DamageType> WITCH_RESISTANT_TO = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.withDefaultNamespace("witch_resistant_to"));
    TagKey<DamageType> IS_EXPLOSION = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.withDefaultNamespace("is_explosion"));
    TagKey<DamageType> IS_FALL = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.withDefaultNamespace("is_fall"));
    TagKey<DamageType> IS_DROWNING = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.withDefaultNamespace("is_drowning"));
    TagKey<DamageType> IS_FREEZING = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.withDefaultNamespace("is_freezing"));
    TagKey<DamageType> IS_LIGHTNING = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.withDefaultNamespace("is_lightning"));
    TagKey<DamageType> NO_ANGER = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.withDefaultNamespace("no_anger"));
    TagKey<DamageType> NO_IMPACT = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.withDefaultNamespace("no_impact"));
    TagKey<DamageType> ALWAYS_MOST_SIGNIFICANT_FALL = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.withDefaultNamespace("always_most_significant_fall"));
    TagKey<DamageType> WITHER_IMMUNE_TO = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.withDefaultNamespace("wither_immune_to"));
    TagKey<DamageType> IGNITES_ARMOR_STANDS = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.withDefaultNamespace("ignites_armor_stands"));
    TagKey<DamageType> BURNS_ARMOR_STANDS = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.withDefaultNamespace("burns_armor_stands"));
    TagKey<DamageType> AVOIDS_GUARDIAN_THORNS = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.withDefaultNamespace("avoids_guardian_thorns"));
    TagKey<DamageType> ALWAYS_TRIGGERS_SILVERFISH = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.withDefaultNamespace("always_triggers_silverfish"));
    TagKey<DamageType> ALWAYS_HURTS_ENDER_DRAGONS = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.withDefaultNamespace("always_hurts_ender_dragons"));
    TagKey<DamageType> NO_KNOCKBACK = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.withDefaultNamespace("no_knockback"));
    TagKey<DamageType> ALWAYS_KILLS_ARMOR_STANDS = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.withDefaultNamespace("always_kills_armor_stands"));
    TagKey<DamageType> CAN_BREAK_ARMOR_STAND = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.withDefaultNamespace("can_break_armor_stand"));
    TagKey<DamageType> BYPASSES_WOLF_ARMOR = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.withDefaultNamespace("bypasses_wolf_armor"));
    TagKey<DamageType> IS_PLAYER_ATTACK = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.withDefaultNamespace("is_player_attack"));
    TagKey<DamageType> BURN_FROM_STEPPING = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.withDefaultNamespace("burn_from_stepping"));
    TagKey<DamageType> PANIC_CAUSES = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.withDefaultNamespace("panic_causes"));
    TagKey<DamageType> PANIC_ENVIRONMENTAL_CAUSES = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.withDefaultNamespace("panic_environmental_causes"));
}
