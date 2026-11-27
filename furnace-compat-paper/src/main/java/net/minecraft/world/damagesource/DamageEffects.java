package net.minecraft.world.damagesource;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.StringRepresentable;

/** 맞을 때 소리(바닐라 DamageEffects, 데이터 팩 이름·소리 그대로). */
public enum DamageEffects implements StringRepresentable {
    HURT("hurt", SoundEvents.PLAYER_HURT),
    THORNS("thorns", SoundEvents.THORNS_HIT),
    DROWNING("drowning", SoundEvents.PLAYER_HURT_DROWN),
    BURNING("burning", SoundEvents.PLAYER_HURT_ON_FIRE),
    POKING("poking", SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH),
    FREEZING("freezing", SoundEvents.PLAYER_HURT_FREEZE);

    private final String id;
    private final SoundEvent sound;

    DamageEffects(String id, SoundEvent sound) {
        this.id = id;
        this.sound = sound;
    }

    @Override
    public String getSerializedName() { return id; }

    public SoundEvent sound() { return sound; }
}
