package net.minecraft.world.level.biome;

import net.minecraft.resources.ResourceKey;

/**
 * 바이옴 shim(Mojang 이름, N2a): 청크 섹션의 바이옴 칸({@code Holder<Biome>})이 가리키는 값. 지금은 키로 서로 구별만 된다 —
 * 기후·색·생성 설정(getBaseTemperature·getSpecialEffects 등)은 없다(그 호출은 UnsupportedNmsCall). 같은 바이옴은 같은 객체다.
 */
public final class Biome {

    private final ResourceKey<Biome> key;

    /** 호환 모듈 전용 — 바이옴 표(NmsChunks)가 만든다. */
    public Biome(ResourceKey<Biome> key) { this.key = java.util.Objects.requireNonNull(key, "key"); }

    /** Furnace: 이 바이옴의 레지스트리 키. */
    public ResourceKey<Biome> furnaceKey() { return key; }

    @Override
    public String toString() { return "Biome[" + key.location() + "]"; }
}
