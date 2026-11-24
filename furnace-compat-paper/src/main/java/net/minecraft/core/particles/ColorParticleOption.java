package net.minecraft.core.particles;

/** 색 하나(0xAARRGGBB)를 갖는 입자(Mojang 이름 shim, 1.21.1 은 entity_effect). */
public class ColorParticleOption implements ParticleOptions {

    private final ParticleType<ColorParticleOption> type;
    private final int color;

    private ColorParticleOption(ParticleType<ColorParticleOption> type, int color) {
        this.type = type;
        this.color = color;
    }

    @Override
    public ParticleType<ColorParticleOption> getType() { return type; }

    public float getRed() { return (color >> 16 & 255) / 255.0F; }

    public float getGreen() { return (color >> 8 & 255) / 255.0F; }

    public float getBlue() { return (color & 255) / 255.0F; }

    public float getAlpha() { return (color >>> 24) / 255.0F; }

    public static ColorParticleOption create(ParticleType<ColorParticleOption> type, int color) { return new ColorParticleOption(type, color); }

    /** 바닐라 FastColor.ARGB32.colorFromFloat(1, r, g, b): 채널마다 floor(x * 255). */
    public static ColorParticleOption create(ParticleType<ColorParticleOption> type, float red, float green, float blue) {
        return create(type, 255 << 24 | channel(red) << 16 | channel(green) << 8 | channel(blue));
    }

    private static int channel(float f) { return net.minecraft.util.Mth.floor(f * 255.0F); }

    /** 호환 모듈 전용: 0xAARRGGBB. */
    public int furnaceColor() { return color; }
}
