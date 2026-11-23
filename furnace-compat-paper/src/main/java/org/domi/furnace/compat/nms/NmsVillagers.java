package org.domi.furnace.compat.nms;

import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.domi.furnace.api.Furnace;

/**
 * 주민 종류·직업 레지스트리 shim(엔티티 데이터 설계 D4). 바닐라처럼 두 클래스의 초기화가 상수를 차례로 등록하고, 그 번호 순서가 서버 내장 레지스트리 목록
 * ({@code builtIn("minecraft:villager_type")} 7개, {@code builtIn("minecraft:villager_profession")} 15개 — 바닐라 reports/registries.json)과 같은지
 * 처음 꺼낼 때 본다. 기본값은 바닐라 DefaultedMappedRegistry 처럼 plains·none.
 * 직업 성분은 바닐라 1.21.1 VillagerProfession 등록과 같다(서버 주민 정의와 NmsVillagersTest 가 견준다).
 */
public final class NmsVillagers {

    private NmsVillagers() {}

    /** 바닐라 VillagerProfession.ALL_ACQUIRABLE_JOBS. */
    public static final Predicate<Holder<PoiType>> ALL_ACQUIRABLE_JOBS = holder -> holder.is(PoiTypeTags.ACQUIRABLE_JOB_SITE);

    private static final NmsDefaultedRegistry<VillagerType> TYPES =
            new NmsDefaultedRegistry<>(Registries.VILLAGER_TYPE, ResourceLocation.withDefaultNamespace("plains"));
    private static final NmsDefaultedRegistry<VillagerProfession> PROFESSIONS =
            new NmsDefaultedRegistry<>(Registries.VILLAGER_PROFESSION, ResourceLocation.withDefaultNamespace("none"));
    private static volatile boolean checked;

    /** VillagerType 클래스 초기화가 상수마다 부른다(바닐라 Registry.register). */
    public static VillagerType registerType(VillagerType t) {
        TYPES.add(ResourceLocation.withDefaultNamespace(t.toString()), t);
        return t;
    }

    /** VillagerProfession 클래스 초기화가 상수마다 부른다: 바닐라 등록 성분으로 만들어 레지스트리에 넣는다. */
    public static VillagerProfession registerProfession(String name) {
        VillagerProfession p = create(name);
        PROFESSIONS.add(ResourceLocation.withDefaultNamespace(name), p);
        return p;
    }

    public static DefaultedRegistry<VillagerType> typeRegistry() {
        check();
        return TYPES;
    }

    public static DefaultedRegistry<VillagerProfession> professionRegistry() {
        check();
        return PROFESSIONS;
    }

    /** 이름(경로)의 등록된 종류. */
    public static VillagerType type(String path) {
        return typeRegistry().getOptional(ResourceLocation.withDefaultNamespace(path))
                .orElseThrow(() -> new IllegalStateException("name missing from the server villager type registry: " + path));
    }

    /**
     * 두 클래스의 상수를 다 등록시키고(클래스 초기화를 일으킨다), 번호 순서가 서버 내장 레지스트리 목록과 같은지 한 번 본다 — 다르면 조용히 틀린 번호를
     * 주지 않는다. 클래스 초기화 안에서 다시 불리면(같은 스레드) 아직 다 등록되지 않았으므로 보지 않고 지나간다.
     */
    private static void check() {
        if (checked) return;
        VillagerType plains = VillagerType.PLAINS;
        VillagerProfession none = VillagerProfession.NONE;
        if (plains == null || none == null) return;   // 클래스 초기화 도중
        synchronized (NmsVillagers.class) {
            if (checked) return;
            same("minecraft:villager_type", TYPES);
            same("minecraft:villager_profession", PROFESSIONS);
            checked = true;
        }
    }

    private static void same(String registry, NmsDefaultedRegistry<?> r) {
        List<String> have = new java.util.ArrayList<>();
        r.holders().forEach(h -> have.add(h.key().location().toString()));
        List<String> want = Furnace.server().gameRegistries().builtIn(registry);
        if (!have.equals(want)) throw new IllegalStateException(registry + " differs from the server list: " + have + " vs " + want);
    }

    /** 바닐라 1.21.1 VillagerProfession 등록(이름 → 직업 블록 키·요청 아이템·보조 지점·일 소리). 무직·바보는 직업 블록 없음, 무직만 직업 블록 태그를 얻을 수 있다. */
    private static VillagerProfession create(String name) {
        return switch (name) {
            case "none" -> new VillagerProfession(name, PoiType.NONE, ALL_ACQUIRABLE_JOBS, ImmutableSet.of(), ImmutableSet.of(), null);
            case "nitwit" -> new VillagerProfession(name, PoiType.NONE, PoiType.NONE, ImmutableSet.of(), ImmutableSet.of(), null);
            case "farmer" -> job(name, PoiTypes.FARMER, ImmutableSet.of(Items.WHEAT, Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS, Items.BONE_MEAL),
                    ImmutableSet.of(Blocks.FARMLAND), SoundEvents.VILLAGER_WORK_FARMER);
            case "armorer" -> job(name, PoiTypes.ARMORER, SoundEvents.VILLAGER_WORK_ARMORER);
            case "butcher" -> job(name, PoiTypes.BUTCHER, SoundEvents.VILLAGER_WORK_BUTCHER);
            case "cartographer" -> job(name, PoiTypes.CARTOGRAPHER, SoundEvents.VILLAGER_WORK_CARTOGRAPHER);
            case "cleric" -> job(name, PoiTypes.CLERIC, SoundEvents.VILLAGER_WORK_CLERIC);
            case "fisherman" -> job(name, PoiTypes.FISHERMAN, SoundEvents.VILLAGER_WORK_FISHERMAN);
            case "fletcher" -> job(name, PoiTypes.FLETCHER, SoundEvents.VILLAGER_WORK_FLETCHER);
            case "leatherworker" -> job(name, PoiTypes.LEATHERWORKER, SoundEvents.VILLAGER_WORK_LEATHERWORKER);
            case "librarian" -> job(name, PoiTypes.LIBRARIAN, SoundEvents.VILLAGER_WORK_LIBRARIAN);
            case "mason" -> job(name, PoiTypes.MASON, SoundEvents.VILLAGER_WORK_MASON);
            case "shepherd" -> job(name, PoiTypes.SHEPHERD, SoundEvents.VILLAGER_WORK_SHEPHERD);
            case "toolsmith" -> job(name, PoiTypes.TOOLSMITH, SoundEvents.VILLAGER_WORK_TOOLSMITH);
            case "weaponsmith" -> job(name, PoiTypes.WEAPONSMITH, SoundEvents.VILLAGER_WORK_WEAPONSMITH);
            default -> throw new IllegalStateException("villager profession without a definition: " + name);
        };
    }

    private static VillagerProfession job(String name, ResourceKey<PoiType> site, SoundEvent sound) {
        return job(name, site, ImmutableSet.of(), ImmutableSet.of(), sound);
    }

    /** 바닐라처럼 들고 있는·얻을 직업 블록 조건은 따로 만든 두 람다다(record equals 가 다르게 본다). */
    private static VillagerProfession job(String name, ResourceKey<PoiType> site, ImmutableSet<net.minecraft.world.item.Item> requested,
                                          ImmutableSet<net.minecraft.world.level.block.Block> secondary, SoundEvent sound) {
        Predicate<Holder<PoiType>> held = h -> h.is(site);
        Predicate<Holder<PoiType>> acquirable = h -> h.is(site);
        return new VillagerProfession(name, held, acquirable, requested, secondary, sound);
    }

    /** 바닐라 VillagerType BY_BIOME(바이옴 이름 → 종류), 표에 없으면 PLAINS. */
    public static VillagerType typeForBiome(String biome) {
        String t = switch (biome.startsWith("minecraft:") ? biome.substring(10) : "-") {
            case "badlands", "desert", "eroded_badlands", "wooded_badlands" -> "desert";
            case "bamboo_jungle", "jungle", "sparse_jungle" -> "jungle";
            case "savanna_plateau", "savanna", "windswept_savanna" -> "savanna";
            case "deep_frozen_ocean", "frozen_ocean", "frozen_river", "ice_spikes", "snowy_beach", "snowy_taiga", "snowy_plains", "grove", "snowy_slopes",
                    "frozen_peaks", "jagged_peaks" -> "snow";
            case "swamp", "mangrove_swamp" -> "swamp";
            case "old_growth_spruce_taiga", "old_growth_pine_taiga", "windswept_hills", "windswept_gravelly_hills", "windswept_forest", "taiga" -> "taiga";
            default -> "plains";
        };
        return type(t);
    }

}
