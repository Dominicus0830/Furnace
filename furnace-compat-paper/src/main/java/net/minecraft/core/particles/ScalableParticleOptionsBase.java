package net.minecraft.core.particles;

/** 크기가 있는 입자 옵션(Mojang 이름 shim): 바닐라처럼 크기를 0.01~4 로 죈다. */
public abstract class ScalableParticleOptionsBase implements ParticleOptions {

    public static final float MIN_SCALE = 0.01F;
    public static final float MAX_SCALE = 4.0F;
    private final float scale;

    public ScalableParticleOptionsBase(float scale) { this.scale = net.minecraft.util.Mth.clamp(scale, MIN_SCALE, MAX_SCALE); }

    public float getScale() { return scale; }

    /** 바닐라 Vec3.fromRGB24(rgb).toVector3f(): 채널마다 double 로 255 로 나눈 뒤 float. */
    static org.joml.Vector3f rgb(int rgb) {
        return new org.joml.Vector3f((float) ((rgb >> 16 & 255) / 255.0), (float) ((rgb >> 8 & 255) / 255.0), (float) ((rgb & 255) / 255.0));
    }
}
