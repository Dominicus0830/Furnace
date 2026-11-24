package net.minecraft.core.particles;

/** 색이 바뀌는 먼지 입자(Mojang 이름 shim): 시작색·끝색·크기. */
public class DustColorTransitionOptions extends ScalableParticleOptionsBase {

    public static final org.joml.Vector3f SCULK_PARTICLE_COLOR = rgb(3790560);
    public static final DustColorTransitionOptions SCULK_TO_REDSTONE =
            new DustColorTransitionOptions(SCULK_PARTICLE_COLOR, DustParticleOptions.REDSTONE_PARTICLE_COLOR, 1.0F);
    private final org.joml.Vector3f start;
    private final org.joml.Vector3f end;

    public DustColorTransitionOptions(org.joml.Vector3f fromColor, org.joml.Vector3f toColor, float scale) {
        super(scale);
        this.start = fromColor;
        this.end = toColor;
    }

    public org.joml.Vector3f getFromColor() { return start; }

    public org.joml.Vector3f getToColor() { return end; }

    @Override
    public ParticleType<DustColorTransitionOptions> getType() { return ParticleTypes.DUST_COLOR_TRANSITION; }
}
