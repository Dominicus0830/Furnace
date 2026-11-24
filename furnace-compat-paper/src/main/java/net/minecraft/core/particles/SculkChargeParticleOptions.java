package net.minecraft.core.particles;

/** 스컬크 충전 입자(Mojang 이름 shim): 기울기(라디안). */
public record SculkChargeParticleOptions(float roll) implements ParticleOptions {

    @Override
    public ParticleType<SculkChargeParticleOptions> getType() { return ParticleTypes.SCULK_CHARGE; }
}
