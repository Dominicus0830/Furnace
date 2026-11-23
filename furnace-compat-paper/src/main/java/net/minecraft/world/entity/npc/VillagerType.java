package net.minecraft.world.entity.npc;

import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import org.domi.furnace.compat.nms.NmsVillagers;

/**
 * 주민 종류(Mojang 이름 shim, 엔티티 데이터 설계 D4). 값은 내장 레지스트리 {@code minecraft:villager_type} 의 것이고(번호 순서는 서버 내장 레지스트리 목록),
 * toString 은 바닐라처럼 이름 경로("plains")다. 바이옴 대응은 서버 주민과 같은 바닐라 표({@link NmsVillagers#typeForBiome}).
 */
public final class VillagerType {

    public static final VillagerType DESERT = register("desert");
    public static final VillagerType JUNGLE = register("jungle");
    public static final VillagerType PLAINS = register("plains");
    public static final VillagerType SAVANNA = register("savanna");
    public static final VillagerType SNOW = register("snow");
    public static final VillagerType SWAMP = register("swamp");
    public static final VillagerType TAIGA = register("taiga");

    private final String name;

    private VillagerType(String name) { this.name = name; }

    /** 바닐라처럼 클래스 초기화 때 내장 레지스트리에 등록한다(번호 순서는 서버 목록과 견준다 — {@link NmsVillagers#typeRegistry}). */
    private static VillagerType register(String name) { return NmsVillagers.registerType(new VillagerType(name)); }

    @Override
    public String toString() { return name; }

    /** 바닐라: 바이옴 키의 종류, 키가 없거나 표에 없으면 PLAINS. */
    public static VillagerType byBiome(Holder<Biome> biome) {
        return biome.unwrapKey().map(ResourceKey::location).map(l -> NmsVillagers.typeForBiome(l.toString())).orElse(PLAINS);
    }
}
