// BlockState.canSurvive(level, pos) 중 월드젠이 쓰는 블록들의 규칙(바닐라 디컬파일 기준). 구현되지 않은 블록은 로드 시 예외.
package org.domi.furnace.server.world.gen.feature;

import java.util.Set;
import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.BlockRegistry;

public final class BlockSurvival implements BlockPredicate.Survival {

    private final BlockRegistry registry;
    private final FluidKinds fluids;
    private final Set<Integer> dirtTag;      // #minecraft:dirt
    private final Set<Integer> sandTag;      // #minecraft:sand
    private final Set<Integer> farmland;
    private final Set<Integer> clay;
    private final Set<Integer> cactus;
    private final Set<Integer> sugarCane;
    private final Set<Integer> frostedIce;
    private final Set<Integer> mangroveLeaves;
    private final Set<Integer> terracotta;          // #minecraft:terracotta
    private final Set<Integer> nylium;              // #minecraft:nylium
    private final Set<Integer> soulSoil;
    private final Set<Integer> soulFireBase;        // #minecraft:soul_fire_base_blocks
    private final Set<Integer> smallDripleafPlaceable;
    private final Set<Integer> mushroomGrow;        // #minecraft:mushroom_grow_block
    private final Set<Integer> ice;                 // IceBlock 인스턴스(ice, frosted_ice)
    private final Set<Integer> magma;
    private final Set<Integer> clayBlock;
    private final byte[] kinds;                     // state id → Kind ordinal+1 (0=미계산)

    public BlockSurvival(BlockRegistry registry, FluidKinds fluids) {
        this.registry = registry;
        this.fluids = fluids;
        this.dirtTag = tag("minecraft:dirt");
        this.sandTag = tag("minecraft:sand");
        this.farmland = registry.stateIdsForBlock("minecraft:farmland");
        this.clay = registry.stateIdsForBlock("minecraft:clay");
        this.cactus = registry.stateIdsForBlock("minecraft:cactus");
        this.sugarCane = registry.stateIdsForBlock("minecraft:sugar_cane");
        this.frostedIce = registry.stateIdsForBlock("minecraft:frosted_ice");
        this.mangroveLeaves = registry.stateIdsForBlock("minecraft:mangrove_leaves");
        this.terracotta = tag("minecraft:terracotta");
        this.nylium = tag("minecraft:nylium");
        this.soulSoil = registry.stateIdsForBlock("minecraft:soul_soil");
        this.soulFireBase = tag("minecraft:soul_fire_base_blocks");
        this.smallDripleafPlaceable = tag("minecraft:small_dripleaf_placeable");
        this.mushroomGrow = tag("minecraft:mushroom_grow_block");
        java.util.HashSet<Integer> iceIds = new java.util.HashSet<>(registry.stateIdsForBlock("minecraft:ice"));
        iceIds.addAll(frostedIce);
        this.ice = Set.copyOf(iceIds);
        this.magma = registry.stateIdsForBlock("minecraft:magma_block");
        this.clayBlock = clay;
        this.kinds = new byte[registry.totalStateCount()];
    }

    private Set<Integer> tag(String name) {
        java.util.HashSet<Integer> ids = new java.util.HashSet<>();
        for (String b : BlockTags.get().blocksOf(name)) ids.addAll(registry.stateIdsForBlock(b));
        return Set.copyOf(ids);
    }

    private enum Kind {
        BUSH,                // BushBlock 기본: dirt 태그/farmland 위 (꽃, 풀, 고사리, 묘목, 달콤한 열매, 분홍꽃잎, 2칸 식물)
        MANGROVE_PROPAGULE, CACTUS, SUGAR_CANE,
        DEAD_BUSH,           // sand/terracotta/dirt 태그
        AZALEA,              // clay 또는 BushBlock
        LILY_PAD,
        MUSHROOM,
        ROOTS,               // crimson/warped roots: nylium/soul_soil/BushBlock
        SEAGRASS,            // 지면 UP면 단단 && !magma
        MOSS_CARPET,         // 아래가 공기가 아님
        SPORE_BLOSSOM,       // 위 DOWN면 center 지지 && 물 아님
        SMALL_DRIPLEAF,
        FIRE,                // 아래 UP면 단단(정확한 isValidFireLocation 이웃 인화성은 미구현 — 호출부 predicate가 netherrack 위로 한정)
        SOUL_FIRE,
        ALWAYS               // 제약 없음(melon, pumpkin)
    }

    /** 이 이름 블록의 canSurvive가 구현돼 있는가. */
    public boolean supportsBlock(String name) {
        return kindOfName(name) != null;
    }

    private static Kind kindOfName(String name) {
        if (name.endsWith("_sapling")) return Kind.BUSH;
        return switch (name) {
            case "minecraft:mangrove_propagule" -> Kind.MANGROVE_PROPAGULE;
            case "minecraft:cactus" -> Kind.CACTUS;
            case "minecraft:sugar_cane" -> Kind.SUGAR_CANE;
            case "minecraft:dandelion", "minecraft:poppy", "minecraft:blue_orchid", "minecraft:allium",
                 "minecraft:azure_bluet", "minecraft:red_tulip", "minecraft:orange_tulip", "minecraft:white_tulip",
                 "minecraft:pink_tulip", "minecraft:oxeye_daisy", "minecraft:cornflower", "minecraft:lily_of_the_valley",
                 "minecraft:short_grass", "minecraft:fern", "minecraft:tall_grass", "minecraft:large_fern",
                 "minecraft:lilac", "minecraft:rose_bush", "minecraft:peony", "minecraft:sunflower",
                 "minecraft:sweet_berry_bush", "minecraft:pink_petals" -> Kind.BUSH;
            case "minecraft:dead_bush" -> Kind.DEAD_BUSH;
            case "minecraft:azalea", "minecraft:flowering_azalea" -> Kind.AZALEA;
            case "minecraft:lily_pad" -> Kind.LILY_PAD;
            case "minecraft:brown_mushroom", "minecraft:red_mushroom" -> Kind.MUSHROOM;
            case "minecraft:crimson_roots", "minecraft:warped_roots" -> Kind.ROOTS;
            case "minecraft:seagrass" -> Kind.SEAGRASS;
            case "minecraft:moss_carpet" -> Kind.MOSS_CARPET;
            case "minecraft:spore_blossom" -> Kind.SPORE_BLOSSOM;
            case "minecraft:small_dripleaf" -> Kind.SMALL_DRIPLEAF;
            case "minecraft:fire" -> Kind.FIRE;
            case "minecraft:soul_fire" -> Kind.SOUL_FIRE;
            case "minecraft:melon", "minecraft:pumpkin" -> Kind.ALWAYS;
            default -> null;
        };
    }

    private Kind kindOf(int stateId) {
        int k = kinds[stateId];
        if (k == 0) {
            Kind kind = kindOfName(registry.nameOf(stateId));
            k = kind == null ? -1 : kind.ordinal() + 1;
            kinds[stateId] = (byte) k;
        }
        return k < 0 ? null : Kind.values()[k - 1];
    }

    @Override
    public void requireSupported(int stateId) {
        if (kindOf(stateId) == null) {
            throw new IllegalArgumentException("would_survive 미지원 블록: " + registry.nameOf(stateId));
        }
    }

    /** 2칸 식물(DoublePlantBlock 계열)의 아래 칸 state인가. */
    public boolean isLowerDoublePlant(int stateId) {
        Kind kind = kindOf(stateId);
        if (kind != Kind.BUSH && kind != Kind.SMALL_DRIPLEAF) return false;
        String key = registry.canonicalKeyOf(stateId);
        return key.contains("half=lower");
    }

    /** 아래 칸 state의 위 칸 state(half=upper, waterlogged는 호출부가 처리). */
    public int upperOf(int lowerStateId) {
        String key = registry.canonicalKeyOf(lowerStateId);
        return stateFromKey(key.replace("half=lower", "half=upper"));
    }

    public int withWaterlogged(int stateId, boolean waterlogged) {
        String key = registry.canonicalKeyOf(stateId);
        if (!key.contains("waterlogged=")) return stateId;
        return stateFromKey(key.replace("waterlogged=" + !waterlogged, "waterlogged=" + waterlogged));
    }

    private int stateFromKey(String key) {
        int br = key.indexOf('[');
        String name = br < 0 ? key : key.substring(0, br);
        java.util.Map<String, String> props = new java.util.HashMap<>();
        if (br >= 0) {
            for (String kv : key.substring(br + 1, key.length() - 1).split(",")) {
                int eq = kv.indexOf('=');
                props.put(kv.substring(0, eq), kv.substring(eq + 1));
            }
        }
        return registry.stateIdOrAir(name, props);
    }

    public boolean isWaterAt(FeatureWorldView view, int x, int y, int z) {
        return fluids.of(view.getBlock(x, y, z)).isWater();
    }

    private boolean bushMayPlaceOn(int below) {
        return dirtTag.contains(below) || farmland.contains(below);
    }

    @Override
    public boolean canSurvive(FeatureWorldView view, int stateId, int x, int y, int z) {
        Kind kind = kindOf(stateId);
        int below = view.getBlock(x, y - 1, z);
        BlockAttrs attrs = BlockAttrs.get();
        switch (kind) {
            case BUSH:
                return bushMayPlaceOn(below);       // BushBlock.mayPlaceOn
            case MANGROVE_PROPAGULE: {
                // hanging=true면 위가 맹그로브 잎이어야 하고, 아니면 BushBlock + 점토 허용
                if (registry.canonicalKeyOf(stateId).contains("hanging=true")) {
                    return mangroveLeaves.contains(view.getBlock(x, y + 1, z));
                }
                return bushMayPlaceOn(below) || clay.contains(below);
            }
            case CACTUS: {
                int[][] horizontal = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};   // NORTH, EAST, SOUTH, WEST
                for (int[] d : horizontal) {
                    int s = view.getBlock(x + d[0], y, z + d[1]);
                    if (attrs.isSolid(s) || fluids.of(s).isLava()) return false;   // 옆에 고체 또는 용암 금지
                }
                return (cactus.contains(below) || sandTag.contains(below)) && !attrs.isLiquid(view.getBlock(x, y + 1, z));
            }
            case SUGAR_CANE: {
                if (sugarCane.contains(below)) return true;
                if (dirtTag.contains(below) || sandTag.contains(below)) {
                    int[][] horizontal = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
                    for (int[] d : horizontal) {
                        int s = view.getBlock(x + d[0], y - 1, z + d[1]);
                        if (fluids.of(s).isWater() || frostedIce.contains(s)) return true;
                    }
                }
                return false;
            }
            case DEAD_BUSH:
                return sandTag.contains(below) || terracotta.contains(below) || dirtTag.contains(below);
            case AZALEA:
                return clayBlock.contains(below) || bushMayPlaceOn(below);
            case LILY_PAD:
                // LilyPadBlock.mayPlaceOn(floor, pos=below): 아래 칸 유체가 (source) 물이거나 IceBlock, 그리고 이 칸 유체가 비어있음
                return (fluids.of(below) == FluidKind.WATER || ice.contains(below))
                    && fluids.of(view.getBlock(x, y, z)) == FluidKind.EMPTY;
            case MUSHROOM:
                // MushroomBlock.canSurvive: mushroom_grow_block 위면 항상, 아니면 (광량<13 && 아래가 풀 불투명 큐브).
                // 지형 생성 중 광량은 하늘 개방 여부로 근사(기존 측정 동작 유지).
                if (mushroomGrow.contains(below)) return true;
                return !view.isOpenToSky(x, y, z) && attrs.isSolidRender(below);
            case ROOTS:
                return nylium.contains(below) || soulSoil.contains(below) || bushMayPlaceOn(below);
            case SEAGRASS:
                return attrs.isFaceSturdy(below, BlockAttrs.UP) && !magma.contains(below);
            case MOSS_CARPET:
                return !attrs.isAir(below);
            case SPORE_BLOSSOM: {
                int above = view.getBlock(x, y + 1, z);
                return attrs.isFaceSturdy(above, BlockAttrs.DOWN) && !fluids.of(view.getBlock(x, y, z)).isWater();
            }
            case SMALL_DRIPLEAF:
                // lower: mayPlaceOn(below) = small_dripleaf_placeable || (이 칸이 water source && BushBlock.mayPlaceOn)
                return smallDripleafPlaceable.contains(below)
                    || (fluids.of(view.getBlock(x, y, z)) == FluidKind.WATER && bushMayPlaceOn(below));
            case FIRE:
                return attrs.isFaceSturdy(below, BlockAttrs.UP);
            case SOUL_FIRE:
                return soulFireBase.contains(below);
            case ALWAYS:
                return true;
            default:
                throw new IllegalStateException();
        }
    }
}
