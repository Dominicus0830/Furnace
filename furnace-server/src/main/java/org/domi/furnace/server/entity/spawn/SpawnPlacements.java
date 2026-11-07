// 바닐라 SpawnPlacements: 종류별 배치 종류(땅·물·용암·제한 없음)와 자연 스폰 검사식(checkSpawnRules). 표는 바닐라 등록 순서 그대로.
// 검사식 이름은 바닐라 정적 메서드(Monster.checkMonsterSpawnRules, Animal.checkAnimalSpawnRules …)를 따른다.
// TODO: 블록별 isValidSpawn 예외는 이름 목록으로 근사한다(유리·트랩도어 불가, 발광 블록 허용 …).
package org.domi.furnace.server.entity.spawn;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockInfo;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.ai.navigation.WalkNodeEvaluator;

public final class SpawnPlacements {
    public enum Type { NO_RESTRICTIONS, IN_WATER, IN_LAVA, ON_GROUND }

    @FunctionalInterface
    public interface Rule {
        boolean test(String type, ChunkLevel level, SpawnReason reason, Pos pos, RandomSource random);
    }

    private record Entry(Type type, Rule rule) {}

    private static final Map<String, Entry> TABLE = new HashMap<>();

    private SpawnPlacements() {}

    // ------------------------------------------------------------------ 배치 종류

    public static Type typeOf(String type) {
        Entry e = TABLE.get(type);
        return e == null ? Type.NO_RESTRICTIONS : e.type;
    }

    public static boolean isSpawnPositionOk(String type, ChunkLevel level, Pos pos) {
        switch (typeOf(type)) {
            case NO_RESTRICTIONS:
                return true;
            case IN_WATER:
                return level.withinBorder(pos) && Fluids.isWater(level.getFluidState(pos)) && !B.behavior(level.getBlockState(pos.above())).isRedstoneConductor(level.getBlockState(pos.above()), level, pos.above());
            case IN_LAVA:
                return level.withinBorder(pos) && Fluids.isLava(level.getFluidState(pos));
            default: {
                if (!level.withinBorder(pos)) return false;
                Pos below = pos.below();
                if (!isValidSpawn(level.getBlockState(below), level, below, type)) return false;
                return isValidEmptySpawnBlock(level, pos, type) && isValidEmptySpawnBlock(level, pos.above(), type);
            }
        }
    }

    public static boolean checkSpawnRules(String type, ChunkLevel level, SpawnReason reason, Pos pos, RandomSource random) {
        Entry e = TABLE.get(type);
        return e == null || e.rule.test(type, level, reason, pos, random);
    }

    // ------------------------------------------------------------------ 바닥·빈 칸 판정

    private static Set<Integer> NEVER, ALWAYS;

    private static void initSets() {
        if (NEVER != null) return;
        Set<Integer> never = new HashSet<>(), always = new HashSet<>();
        Blocks bl = Blocks.get();
        for (int b = 0; b < bl.blockCount(); b++) {
            String n = bl.name(b);
            if (n.equals("minecraft:bedrock") || n.equals("minecraft:barrier") || n.equals("minecraft:glass") || n.equals("minecraft:tinted_glass")
                    || n.endsWith("_stained_glass") || n.endsWith("_trapdoor") || n.equals("minecraft:scaffolding") || n.equals("minecraft:chorus_flower")) never.add(b);
            if (n.equals("minecraft:glowstone") || n.equals("minecraft:sea_lantern") || n.equals("minecraft:jack_o_lantern") || n.equals("minecraft:carved_pumpkin")
                    || n.equals("minecraft:redstone_lamp") || n.equals("minecraft:beacon") || n.equals("minecraft:shroomlight") || n.endsWith("_froglight")) always.add(b);
        }
        NEVER = never;
        ALWAYS = always;
    }

    /** BlockState.isValidSpawn(level, pos, type): 위쪽 면이 단단하고 빛 14 미만(예외 블록은 목록). */
    public static boolean isValidSpawn(int state, ChunkLevel level, Pos pos, String type) {
        initSets();
        int b = Blocks.get().blockOf(state);
        String n = Blocks.get().name(b);
        if (n.endsWith("_leaves")) return type.equals("minecraft:ocelot") || type.equals("minecraft:parrot");
        if (n.equals("minecraft:ice") || n.equals("minecraft:frosted_ice")) return type.equals("minecraft:polar_bear");
        if (n.equals("minecraft:magma_block")) return EntityMeta.of(type).fireImmune();
        if (NEVER.contains(b)) return false;
        if (ALWAYS.contains(b)) return true;
        return B.isFaceSturdy(state, level, pos, Dir.UP) && BlockInfo.get().lightEmission(state) < 14;
    }

    /** EntityType.isBlockDangerous: 불·마그마·켜진 모닥불 또는 시든 장미·달콤한 열매 덤불·선인장·가루눈(종류별 면역 제외). */
    public static boolean isBlockDangerous(String type, int state) {
        String n = Blocks.get().nameOfState(state);
        switch (type) {
            case "minecraft:fox": if (n.equals("minecraft:sweet_berry_bush")) return false; break;
            case "minecraft:polar_bear", "minecraft:snow_golem", "minecraft:stray": if (n.equals("minecraft:powder_snow")) return false; break;
            case "minecraft:wither", "minecraft:wither_skeleton": if (n.equals("minecraft:wither_rose")) return false; break;
            default: break;
        }
        if (!EntityMeta.of(type).fireImmune() && WalkNodeEvaluator.isBurningBlock(state)) return true;
        return n.equals("minecraft:wither_rose") || n.equals("minecraft:sweet_berry_bush") || n.equals("minecraft:cactus") || n.equals("minecraft:powder_snow");
    }

    /** NaturalSpawner.isValidEmptySpawnBlock. */
    public static boolean isValidEmptySpawnBlock(ChunkLevel level, Pos pos, String type) {
        int s = level.getBlockState(pos);
        if (B.isCollisionShapeFullBlock(s)) return false;
        if (B.behavior(s).isSignalSource(s)) return false;
        if (!Fluids.isEmpty(level.getFluidState(pos))) return false;
        if (B.isTag(s, "minecraft:prevent_mob_spawning_inside")) return false;
        return !isBlockDangerous(type, s);
    }

    // ------------------------------------------------------------------ 검사식 묶음(바닐라 정적 메서드)

    private static boolean belowIsTag(ChunkLevel l, Pos p, String tag) { return B.isTag(l.getBlockState(p.below()), tag); }

    /** Animal.isBrightEnoughToSpawn. */
    private static boolean bright(ChunkLevel l, Pos p) { return l.getRawBrightness(p, 0) > 8; }

    /** Mob.checkMobSpawnRules. */
    static boolean mob(String t, ChunkLevel l, SpawnReason r, Pos p, RandomSource rnd) {
        return r == SpawnReason.SPAWNER || isValidSpawn(l.getBlockState(p.below()), l, p.below(), t);
    }

    /** Monster.isDarkEnoughToSpawn. */
    static boolean darkEnough(ChunkLevel l, Pos p, RandomSource rnd) {
        if (l.getSkyLight(p) > rnd.nextInt(32)) return false;
        var type = l.dimensionType();   // 바닐라: monsterSpawnBlockLightLimit · monsterSpawnLightTest(네더 7 고정, 그 밖 0~7 균등)
        int blockLimit = type.monsterSpawnBlockLightLimit();
        if (blockLimit < 15 && l.getBlockLight(p) > blockLimit) return false;
        int bright = l.isThundering() ? l.getRawBrightness(p, 10) : l.getMaxLocalRawBrightness(p);
        int test = type.sampleMonsterSpawnLight(rnd::nextInt);
        return bright <= test;
    }

    static boolean monster(String t, ChunkLevel l, SpawnReason r, Pos p, RandomSource rnd) {
        return l.difficulty() != 0 && (r.ignoresLight() || darkEnough(l, p, rnd)) && mob(t, l, r, p, rnd);
    }

    static boolean anyLightMonster(String t, ChunkLevel l, SpawnReason r, Pos p, RandomSource rnd) { return l.difficulty() != 0 && mob(t, l, r, p, rnd); }

    static boolean animal(String t, ChunkLevel l, SpawnReason r, Pos p, RandomSource rnd) {
        return belowIsTag(l, p, "minecraft:animals_spawnable_on") && (r.ignoresLight() || bright(l, p));
    }

    private static Rule animalOn(String tag) {
        return (t, l, r, p, rnd) -> belowIsTag(l, p, tag) && bright(l, p);
    }

    static boolean surfaceWater(String t, ChunkLevel l, SpawnReason r, Pos p, RandomSource rnd) {
        int sea = l.seaLevel();
        return p.y() >= sea - 13 && p.y() <= sea && Fluids.isWater(l.getFluidState(p.below())) && B.is(l.getBlockState(p.above()), "minecraft:water");
    }

    private static boolean noPlayerNear(ChunkLevel l, Pos p) { return l.getNearestPlayer(p.x() + 0.5, p.y() + 0.5, p.z() + 0.5, 5.0, true) == null; }

    private static Rule fromName(String key) {
        return switch (key) {
            case "monster" -> SpawnPlacements::monster;
            case "anyLight" -> SpawnPlacements::anyLightMonster;
            case "animal" -> SpawnPlacements::animal;
            case "mob" -> SpawnPlacements::mob;
            case "surfaceWater" -> SpawnPlacements::surfaceWater;
            case "axolotl" -> (t, l, r, p, rnd) -> belowIsTag(l, p, "minecraft:axolotls_spawnable_on");
            case "armadillo" -> animalOn("minecraft:armadillo_spawnable_on");
            case "frog" -> animalOn("minecraft:frogs_spawnable_on");
            case "goat" -> animalOn("minecraft:goats_spawnable_on");
            case "mooshroom" -> animalOn("minecraft:mooshrooms_spawnable_on");
            case "parrot" -> animalOn("minecraft:parrots_spawnable_on");
            case "rabbit" -> animalOn("minecraft:rabbits_spawnable_on");
            case "fox" -> animalOn("minecraft:foxes_spawnable_on");
            case "wolf" -> animalOn("minecraft:wolves_spawnable_on");
            case "ocelot" -> (t, l, r, p, rnd) -> rnd.nextInt(3) != 0;
            case "bat" -> (t, l, r, p, rnd) -> {
                if (p.y() >= l.seaLevel()) return false;
                int light = l.getMaxLocalRawBrightness(p);
                int limit = 4;
                LocalDate d = LocalDate.now();
                if (d.getMonthValue() == 10 && d.getDayOfMonth() >= 20 || d.getMonthValue() == 11 && d.getDayOfMonth() <= 3) limit = 7;
                else if (rnd.nextBoolean()) return false;
                return light > rnd.nextInt(limit) ? false : mob(t, l, r, p, rnd);
            };
            case "endermite" -> (t, l, r, p, rnd) -> anyLightMonster(t, l, r, p, rnd) && noPlayerNear(l, p);
            case "ghast" -> (t, l, r, p, rnd) -> l.difficulty() != 0 && rnd.nextInt(20) == 0 && anyLightMonster(t, l, r, p, rnd);
            case "glowSquid" -> (t, l, r, p, rnd) -> p.y() <= l.seaLevel() - 33 && l.getRawBrightness(p, 0) == 0 && B.is(l.getBlockState(p), "minecraft:water");
            case "husk" -> (t, l, r, p, rnd) -> monster(t, l, r, p, rnd) && (r.isSpawner() || l.canSeeSky(p));
            case "magmaCube" -> (t, l, r, p, rnd) -> l.difficulty() != 0;
            case "netherWart" -> (t, l, r, p, rnd) -> !B.is(l.getBlockState(p.below()), "minecraft:nether_wart_block");
            case "zombifiedPiglin" -> (t, l, r, p, rnd) -> l.difficulty() != 0 && !B.is(l.getBlockState(p.below()), "minecraft:nether_wart_block");
            // PatrollingMonster.checkPatrollingMonsterSpawnRules: 블록 빛(LightLayer.BLOCK) 8 초과면 안 됨
            case "pillager" -> (t, l, r, p, rnd) -> l.getBlockLight(p) > 8 ? false : anyLightMonster(t, l, r, p, rnd);
            case "polarBear" -> (t, l, r, p, rnd) -> l.biomeIs(p, "minecraft:polar_bears_spawn_on_alternate_blocks")
                    ? bright(l, p) && belowIsTag(l, p, "minecraft:polar_bears_spawnable_on_alternate") : animal(t, l, r, p, rnd);
            case "silverfish" -> (t, l, r, p, rnd) -> anyLightMonster(t, l, r, p, rnd) && noPlayerNear(l, p);
            case "horseUndead" -> (t, l, r, p, rnd) -> !r.isSpawner() ? animal(t, l, r, p, rnd) : r.ignoresLight() || bright(l, p);
            case "stray" -> (t, l, r, p, rnd) -> {
                Pos q = p;
                do q = q.above(); while (B.is(l.getBlockState(q), "minecraft:powder_snow"));
                return monster(t, l, r, p, rnd) && (r.isSpawner() || l.canSeeSky(q.below()));
            };
            case "strider" -> (t, l, r, p, rnd) -> {
                Pos q = p;
                do q = q.above(); while (Fluids.isLava(l.getFluidState(q)));
                return B.isAir(l.getBlockState(q));
            };
            case "turtle" -> (t, l, r, p, rnd) -> p.y() < l.seaLevel() + 4 && belowIsTag(l, p, "minecraft:sand") && bright(l, p);
            case "guardian" -> (t, l, r, p, rnd) -> (rnd.nextInt(20) == 0 || !l.canSeeSky(p)) && l.difficulty() != 0
                    && (r.isSpawner() || Fluids.isWater(l.getFluidState(p))) && Fluids.isWater(l.getFluidState(p.below()));
            case "drowned" -> (t, l, r, p, rnd) -> {
                if (!Fluids.isWater(l.getFluidState(p.below())) && !r.isSpawner()) return false;
                boolean ok = l.difficulty() != 0 && (r.ignoresLight() || darkEnough(l, p, rnd)) && (r.isSpawner() || Fluids.isWater(l.getFluidState(p)));
                if (ok && r.isSpawner()) return true;
                return l.biomeIs(p, "minecraft:more_frequent_drowned_spawns") ? rnd.nextInt(15) == 0 && ok : rnd.nextInt(40) == 0 && p.y() < l.seaLevel() - 5 && ok;
            };
            case "tropicalFish" -> (t, l, r, p, rnd) -> Fluids.isWater(l.getFluidState(p.below())) && B.is(l.getBlockState(p.above()), "minecraft:water")
                    && (l.biomeIs(p, "minecraft:allows_tropical_fish_spawns_at_any_height") || surfaceWater(t, l, r, p, rnd));
            case "slime" -> SpawnPlacements::slime;
            default -> throw new IllegalArgumentException(key);
        };
    }

    /** Slime.checkSlimeSpawnRules: 늪(달 밝기에 비례), 슬라임 청크(y < 40). */
    private static boolean slime(String t, ChunkLevel l, SpawnReason r, Pos p, RandomSource rnd) {
        if (r.isSpawner()) return mob(t, l, r, p, rnd);
        if (l.difficulty() == 0) return false;
        if (l.biomeIs(p, "minecraft:allows_surface_slime_spawns") && p.y() > 50 && p.y() < 70 && rnd.nextFloat() < 0.5F && rnd.nextFloat() < l.moonBrightness()
                && l.getMaxLocalRawBrightness(p) <= rnd.nextInt(8)) return mob(t, l, r, p, rnd);
        int cx = p.x() >> 4, cz = p.z() >> 4;
        long seed = l.worldSeed() + (long) (cx * cx * 4987142) + (long) (cx * 5947611) + (long) (cz * cz) * 4392871L + (long) (cz * 389711) ^ 987234911L;
        var sr = new org.domi.furnace.server.world.gen.carver.WorldgenRandom();
        sr.setSeed(seed);
        boolean slimeChunk = sr.nextInt(10) == 0;
        return rnd.nextInt(10) == 0 && slimeChunk && p.y() < 40 && mob(t, l, r, p, rnd);
    }

    // ------------------------------------------------------------------ 등록(바닐라 순서)

    private static void reg(String name, Type type, String rule) { TABLE.put("minecraft:" + name, new Entry(type, fromName(rule))); }

    static {
        for (String n : new String[] {"cod", "dolphin", "pufferfish", "salmon", "squid"}) reg(n, Type.IN_WATER, "surfaceWater");
        reg("axolotl", Type.IN_WATER, "axolotl");
        reg("drowned", Type.IN_WATER, "drowned");
        reg("guardian", Type.IN_WATER, "guardian");
        reg("elder_guardian", Type.IN_WATER, "guardian");
        reg("tropical_fish", Type.IN_WATER, "tropicalFish");
        reg("glow_squid", Type.IN_WATER, "glowSquid");
        reg("armadillo", Type.ON_GROUND, "armadillo");
        reg("bat", Type.ON_GROUND, "bat");
        reg("blaze", Type.ON_GROUND, "anyLight");
        for (String n : new String[] {"bogged", "cave_spider", "creeper", "enderman", "giant", "skeleton", "spider", "witch", "wither", "wither_skeleton", "zombie", "zombie_villager", "ravager"}) reg(n, Type.ON_GROUND, "monster");
        for (String n : new String[] {"chicken", "cow", "donkey", "horse", "llama", "mule", "pig", "sheep", "cat"}) reg(n, Type.ON_GROUND, "animal");
        reg("endermite", Type.ON_GROUND, "endermite");
        reg("ender_dragon", Type.ON_GROUND, "mob");
        reg("frog", Type.ON_GROUND, "frog");
        reg("ghast", Type.ON_GROUND, "ghast");
        reg("goat", Type.ON_GROUND, "goat");
        reg("husk", Type.ON_GROUND, "husk");
        reg("iron_golem", Type.ON_GROUND, "mob");
        reg("magma_cube", Type.ON_GROUND, "magmaCube");
        reg("mooshroom", Type.ON_GROUND, "mooshroom");
        reg("ocelot", Type.ON_GROUND, "ocelot");
        reg("parrot", Type.ON_GROUND, "parrot");
        reg("hoglin", Type.ON_GROUND, "netherWart");
        reg("piglin", Type.ON_GROUND, "netherWart");
        reg("pillager", Type.ON_GROUND, "pillager");
        reg("polar_bear", Type.ON_GROUND, "polarBear");
        reg("rabbit", Type.ON_GROUND, "rabbit");
        reg("silverfish", Type.ON_GROUND, "silverfish");
        reg("skeleton_horse", Type.ON_GROUND, "horseUndead");
        reg("zombie_horse", Type.ON_GROUND, "horseUndead");
        reg("slime", Type.ON_GROUND, "slime");
        reg("snow_golem", Type.ON_GROUND, "mob");
        reg("stray", Type.ON_GROUND, "stray");
        reg("strider", Type.IN_LAVA, "strider");
        reg("turtle", Type.ON_GROUND, "turtle");
        reg("villager", Type.ON_GROUND, "mob");
        reg("wolf", Type.ON_GROUND, "wolf");
        reg("zombified_piglin", Type.ON_GROUND, "zombifiedPiglin");
        reg("evoker", Type.NO_RESTRICTIONS, "monster");
        reg("illusioner", Type.NO_RESTRICTIONS, "monster");
        reg("vex", Type.NO_RESTRICTIONS, "monster");
        reg("vindicator", Type.NO_RESTRICTIONS, "monster");
        reg("fox", Type.NO_RESTRICTIONS, "fox");
        reg("panda", Type.NO_RESTRICTIONS, "animal");
        reg("trader_llama", Type.NO_RESTRICTIONS, "animal");
        reg("phantom", Type.NO_RESTRICTIONS, "mob");
        reg("shulker", Type.NO_RESTRICTIONS, "mob");
        reg("warden", Type.NO_RESTRICTIONS, "mob");
        reg("wandering_trader", Type.ON_GROUND, "mob");
    }
}
