package net.minecraft.core.particles;

/**
 * 입자 종류(Mojang 이름 shim). 내장 레지스트리 {@code minecraft:particle_type} 의 값이고 같은 종류는 같은 객체다({@link ParticleTypes}).
 * 코덱은 아직 없다 — {@link #codec()}·{@link #streamCodec()} 은 shim 종류에서 UnsupportedNmsCall.
 */
public abstract class ParticleType<T extends ParticleOptions> {

    private final boolean alwaysShown;

    protected ParticleType(boolean overrideLimiter) { this.alwaysShown = overrideLimiter; }

    public boolean getOverrideLimiter() { return alwaysShown; }

    public abstract com.mojang.serialization.MapCodec<T> codec();

    public abstract net.minecraft.network.codec.StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf, T> streamCodec();

    static org.domi.furnace.compat.nms.UnsupportedNmsCall noCodec(String what) {
        return new org.domi.furnace.compat.nms.UnsupportedNmsCall(what, "particle codecs are not in the shim - convert through the Furnace particle value instead");
    }
}
