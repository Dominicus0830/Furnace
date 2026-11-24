package net.minecraft.core.particles;

/** 입자 한 개(종류 + 종류별 옵션, Mojang 이름 shim). Furnace 값과는 {@link org.domi.furnace.compat.nms.NmsParticles} 가 바꾼다. */
public interface ParticleOptions {

    ParticleType<?> getType();
}
