package net.minecraft.world.entity.animal;

import java.util.Objects;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

/**
 * 늑대 무늬(Mojang 이름 shim, 엔티티 데이터 설계 D4-3). 바닐라 1.21.1 처럼 그림 위치 셋(정의에 적힌 꼴)과 자연 스폰 바이옴 묶음을 갖고,
 * 접근자는 {@code textures/<경로>.png} 로 늘인 전체 위치를 돌려준다. 같음·해시는 바닐라처럼 적힌 꼴 셋과 바이옴 묶음으로 본다.
 * 값은 데이터 팩 레지스트리 {@code minecraft:wolf_variant}({@link org.domi.furnace.compat.nms.NmsVariants#wolves()}) 의 것이다.
 * 코덱은 두지 않았다(레지스트리는 서버 시작 때 얼어 있다).
 */
public final class WolfVariant {

    private final ResourceLocation wild;
    private final ResourceLocation tame;
    private final ResourceLocation angry;
    private final ResourceLocation wildFull;
    private final ResourceLocation tameFull;
    private final ResourceLocation angryFull;
    private final HolderSet<Biome> spawnBiomes;

    public WolfVariant(ResourceLocation wildTexture, ResourceLocation tameTexture, ResourceLocation angryTexture, HolderSet<Biome> biomes) {
        this.wild = wildTexture;
        this.wildFull = full(wildTexture);
        this.tame = tameTexture;
        this.tameFull = full(tameTexture);
        this.angry = angryTexture;
        this.angryFull = full(angryTexture);
        this.spawnBiomes = biomes;
    }

    private static ResourceLocation full(ResourceLocation id) { return id.withPath("textures/" + id.getPath() + ".png"); }

    public ResourceLocation wildTexture() { return wildFull; }

    public ResourceLocation tameTexture() { return tameFull; }

    public ResourceLocation angryTexture() { return angryFull; }

    public HolderSet<Biome> biomes() { return spawnBiomes; }

    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof WolfVariant w && Objects.equals(wild, w.wild) && Objects.equals(tame, w.tame)
                && Objects.equals(angry, w.angry) && Objects.equals(spawnBiomes, w.spawnBiomes);
    }

    @Override
    public int hashCode() {
        int h = 1;
        h = 31 * h + wild.hashCode();
        h = 31 * h + tame.hashCode();
        h = 31 * h + angry.hashCode();
        h = 31 * h + spawnBiomes.hashCode();
        return h;
    }
}
