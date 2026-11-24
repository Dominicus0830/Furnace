package net.minecraft.core.particles;

/** 비명 입자(Mojang 이름 shim): 나타나기까지 지연(틱). */
public class ShriekParticleOption implements ParticleOptions {

    private final int delay;

    public ShriekParticleOption(int delay) { this.delay = delay; }

    @Override
    public ParticleType<ShriekParticleOption> getType() { return ParticleTypes.SHRIEK; }

    public int getDelay() { return delay; }
}
