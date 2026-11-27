package net.minecraft.world.damagesource;

/** 피해 형식(바닐라 DamageType 레코드). 서버 레지스트리 값은 {@link org.domi.furnace.compat.nms.NmsDamageTypes} 가 서버 정의에서 만든다. */
public record DamageType(String msgId, DamageScaling scaling, float exhaustion, DamageEffects effects, DeathMessageType deathMessageType) {

    public DamageType(String msgId, DamageScaling scaling, float exhaustion) {
        this(msgId, scaling, exhaustion, DamageEffects.HURT, DeathMessageType.DEFAULT);
    }

    public DamageType(String msgId, DamageScaling scaling, float exhaustion, DamageEffects effects) {
        this(msgId, scaling, exhaustion, effects, DeathMessageType.DEFAULT);
    }

    public DamageType(String msgId, float exhaustion, DamageEffects effects) {
        this(msgId, DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, exhaustion, effects);
    }

    public DamageType(String msgId, float exhaustion) {
        this(msgId, DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, exhaustion);
    }
}
