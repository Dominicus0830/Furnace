package net.minecraft.core.particles;

/** 옵션 없는 입자 종류(Mojang 이름 shim): 종류 자신이 입자 값이다. */
public class SimpleParticleType extends ParticleType<SimpleParticleType> implements ParticleOptions {

    protected SimpleParticleType(boolean overrideLimiter) { super(overrideLimiter); }

    @Override
    public SimpleParticleType getType() { return this; }

    @Override
    public com.mojang.serialization.MapCodec<SimpleParticleType> codec() { throw noCodec("SimpleParticleType#codec"); }

    @Override
    public net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, SimpleParticleType> streamCodec() { throw noCodec("SimpleParticleType#streamCodec"); }
}
