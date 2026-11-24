package net.minecraft.core.particles;

/** 먼지 입자(Mojang 이름 shim): 색(0~1 실수 셋)과 크기. 바닐라처럼 색 벡터를 복사하지 않고 그대로 든다. */
public class DustParticleOptions extends ScalableParticleOptionsBase {

    public static final org.joml.Vector3f REDSTONE_PARTICLE_COLOR = rgb(0xFF0000);
    public static final DustParticleOptions REDSTONE = new DustParticleOptions(REDSTONE_PARTICLE_COLOR, 1.0F);
    private final org.joml.Vector3f rgb;

    public DustParticleOptions(org.joml.Vector3f color, float scale) {
        super(scale);
        this.rgb = color;
    }

    @Override
    public ParticleType<DustParticleOptions> getType() { return ParticleTypes.DUST; }

    public org.joml.Vector3f getColor() { return rgb; }
}
