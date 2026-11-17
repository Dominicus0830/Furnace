package net.minecraft.world.effect;

import net.minecraft.core.Holder;
import org.domi.furnace.compat.nms.NmsMobEffects;

/**
 * 바닐라 상태 효과 상수(Mojang 이름 shim, easy-nms E4c-1b). 39개, 바닐라 1.21.1 선언 순서·타입(Holder&lt;MobEffect&gt;) 그대로다.
 * 값은 {@code BuiltInRegistries.MOB_EFFECT} 의 등록된 손잡이(Holder.Reference)다.
 */
public final class MobEffects {

    private MobEffects() {}

    public static final Holder<MobEffect> MOVEMENT_SPEED = NmsMobEffects.holder("minecraft:speed");
    public static final Holder<MobEffect> MOVEMENT_SLOWDOWN = NmsMobEffects.holder("minecraft:slowness");
    public static final Holder<MobEffect> DIG_SPEED = NmsMobEffects.holder("minecraft:haste");
    public static final Holder<MobEffect> DIG_SLOWDOWN = NmsMobEffects.holder("minecraft:mining_fatigue");
    public static final Holder<MobEffect> DAMAGE_BOOST = NmsMobEffects.holder("minecraft:strength");
    public static final Holder<MobEffect> HEAL = NmsMobEffects.holder("minecraft:instant_health");
    public static final Holder<MobEffect> HARM = NmsMobEffects.holder("minecraft:instant_damage");
    public static final Holder<MobEffect> JUMP = NmsMobEffects.holder("minecraft:jump_boost");
    public static final Holder<MobEffect> CONFUSION = NmsMobEffects.holder("minecraft:nausea");
    public static final Holder<MobEffect> REGENERATION = NmsMobEffects.holder("minecraft:regeneration");
    public static final Holder<MobEffect> DAMAGE_RESISTANCE = NmsMobEffects.holder("minecraft:resistance");
    public static final Holder<MobEffect> FIRE_RESISTANCE = NmsMobEffects.holder("minecraft:fire_resistance");
    public static final Holder<MobEffect> WATER_BREATHING = NmsMobEffects.holder("minecraft:water_breathing");
    public static final Holder<MobEffect> INVISIBILITY = NmsMobEffects.holder("minecraft:invisibility");
    public static final Holder<MobEffect> BLINDNESS = NmsMobEffects.holder("minecraft:blindness");
    public static final Holder<MobEffect> NIGHT_VISION = NmsMobEffects.holder("minecraft:night_vision");
    public static final Holder<MobEffect> HUNGER = NmsMobEffects.holder("minecraft:hunger");
    public static final Holder<MobEffect> WEAKNESS = NmsMobEffects.holder("minecraft:weakness");
    public static final Holder<MobEffect> POISON = NmsMobEffects.holder("minecraft:poison");
    public static final Holder<MobEffect> WITHER = NmsMobEffects.holder("minecraft:wither");
    public static final Holder<MobEffect> HEALTH_BOOST = NmsMobEffects.holder("minecraft:health_boost");
    public static final Holder<MobEffect> ABSORPTION = NmsMobEffects.holder("minecraft:absorption");
    public static final Holder<MobEffect> SATURATION = NmsMobEffects.holder("minecraft:saturation");
    public static final Holder<MobEffect> GLOWING = NmsMobEffects.holder("minecraft:glowing");
    public static final Holder<MobEffect> LEVITATION = NmsMobEffects.holder("minecraft:levitation");
    public static final Holder<MobEffect> LUCK = NmsMobEffects.holder("minecraft:luck");
    public static final Holder<MobEffect> UNLUCK = NmsMobEffects.holder("minecraft:unluck");
    public static final Holder<MobEffect> SLOW_FALLING = NmsMobEffects.holder("minecraft:slow_falling");
    public static final Holder<MobEffect> CONDUIT_POWER = NmsMobEffects.holder("minecraft:conduit_power");
    public static final Holder<MobEffect> DOLPHINS_GRACE = NmsMobEffects.holder("minecraft:dolphins_grace");
    public static final Holder<MobEffect> BAD_OMEN = NmsMobEffects.holder("minecraft:bad_omen");
    public static final Holder<MobEffect> HERO_OF_THE_VILLAGE = NmsMobEffects.holder("minecraft:hero_of_the_village");
    public static final Holder<MobEffect> DARKNESS = NmsMobEffects.holder("minecraft:darkness");
    public static final Holder<MobEffect> TRIAL_OMEN = NmsMobEffects.holder("minecraft:trial_omen");
    public static final Holder<MobEffect> RAID_OMEN = NmsMobEffects.holder("minecraft:raid_omen");
    public static final Holder<MobEffect> WIND_CHARGED = NmsMobEffects.holder("minecraft:wind_charged");
    public static final Holder<MobEffect> WEAVING = NmsMobEffects.holder("minecraft:weaving");
    public static final Holder<MobEffect> OOZING = NmsMobEffects.holder("minecraft:oozing");
    public static final Holder<MobEffect> INFESTED = NmsMobEffects.holder("minecraft:infested");
}
