// 바닐라 StructureProcessor 구현 모음 (block_ignore, block_rot, gravity, jigsaw_replacement, rule, nop, block_age,
// blackstone_replace, lava_submerged_block, protected_blocks, capped) + rule 테스트·위치 테스트·블록 엔티티 수정자.
package org.domi.furnace.server.world.gen.structure.template;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.gen.carver.LegacyRandom;
import org.domi.furnace.server.world.gen.feature.BlockTags;
import org.domi.furnace.server.world.gen.structure.template.StructureTemplate.BlockInfo;

public final class Processors {

    private Processors() {}

    static boolean isAirName(String n) {
        return n.equals("minecraft:air") || n.equals("minecraft:cave_air") || n.equals("minecraft:void_air");
    }

    // ------------------------------------------------------------------ block_ignore

    public static final class BlockIgnore extends Processor {
        private final Set<String> blocks;
        public BlockIgnore(Set<String> blocks) { this.blocks = blocks; }
        public static BlockIgnore structureBlock() { return new BlockIgnore(Set.of("minecraft:structure_block")); }
        public static BlockIgnore structureAndAir() { return new BlockIgnore(Set.of("minecraft:air", "minecraft:structure_block")); }
        public static BlockIgnore airOnly() { return new BlockIgnore(Set.of("minecraft:air")); }
        @Override
        public BlockInfo processBlock(Ctx ctx, int ox, int oy, int oz, int px, int py, int pz, BlockInfo original, BlockInfo current, PlaceSettings s) {
            return blocks.contains(ctx.table.name(current.state())) ? null : current;
        }
    }

    // ------------------------------------------------------------------ block_rot

    public static final class BlockRot extends Processor {
        private final Set<String> rottable; // null = 전부
        private final float integrity;
        public BlockRot(Set<String> rottable, float integrity) { this.rottable = rottable; this.integrity = integrity; }
        @Override
        public BlockInfo processBlock(Ctx ctx, int ox, int oy, int oz, int px, int py, int pz, BlockInfo original, BlockInfo current, PlaceSettings s) {
            LegacyRandom r = s.getRandom(current.x(), current.y(), current.z());
            boolean rot = rottable == null || rottable.contains(ctx.table.name(original.state()));
            return rot && !(r.nextFloat() <= integrity) ? null : current;
        }
    }

    // ------------------------------------------------------------------ gravity

    public static final class Gravity extends Processor {
        private final String heightmap;
        private final int offset;
        public Gravity(String heightmap, int offset) { this.heightmap = heightmap; this.offset = offset; }
        @Override
        public BlockInfo processBlock(Ctx ctx, int ox, int oy, int oz, int px, int py, int pz, BlockInfo original, BlockInfo current, PlaceSettings s) {
            // ServerLevel(실제 월드젠 영역)이면 WG 높이맵 대신 최종 높이맵을 쓰는 차이(world_surface_wg→world_surface …)는 구조물이 결정 시점에 이미 WG 기준으로 쓴다.
            int h = ctx.heightFirstFree(heightmap, current.x(), current.z()) + offset;
            return new BlockInfo(current.x(), h + original.y(), current.z(), current.state(), current.nbt());
        }
    }

    // ------------------------------------------------------------------ jigsaw_replacement

    public static final class JigsawReplacement extends Processor {
        @Override
        public BlockInfo processBlock(Ctx ctx, int ox, int oy, int oz, int px, int py, int pz, BlockInfo original, BlockInfo current, PlaceSettings s) {
            if (!ctx.table.name(current.state()).equals("minecraft:jigsaw")) return current;
            if (current.nbt() == null) return current;
            String finalState = current.nbt().getString("final_state");
            int state = ctx.parseBlockState(finalState);
            if (state < 0) return null;
            return ctx.table.name(state).equals("minecraft:structure_void") ? null : new BlockInfo(current.x(), current.y(), current.z(), state, null);
        }
    }

    // ------------------------------------------------------------------ nop / lava_submerged_block / protected_blocks

    public static final class Nop extends Processor {}

    public static final class LavaSubmerged extends Processor {
        @Override
        public BlockInfo processBlock(Ctx ctx, int ox, int oy, int oz, int px, int py, int pz, BlockInfo original, BlockInfo current, PlaceSettings s) {
            boolean lava = ctx.table.name(ctx.view.getBlock(current.x(), current.y(), current.z())).equals("minecraft:lava");
            // !Block.isShapeFullBlock(state.getCollisionShape(level, pos)) — 충돌 모양이 꽉 찬 블록이 아니면 용암으로 교체
            return lava && !ctx.isFullCollisionBlock(current.state())
                ? new BlockInfo(current.x(), current.y(), current.z(), ctx.table.registry().defaultStateId("minecraft:lava"), current.nbt())
                : current;
        }
    }

    public static final class ProtectedBlocks extends Processor {
        private final Set<String> cannotReplace;
        public ProtectedBlocks(Set<String> cannotReplace) { this.cannotReplace = cannotReplace; }
        @Override
        public BlockInfo processBlock(Ctx ctx, int ox, int oy, int oz, int px, int py, int pz, BlockInfo original, BlockInfo current, PlaceSettings s) {
            return cannotReplace.contains(ctx.table.name(ctx.view.getBlock(current.x(), current.y(), current.z()))) ? null : current;
        }
    }

    // ------------------------------------------------------------------ blackstone_replace

    public static final class BlackstoneReplace extends Processor {
        private static final Map<String, String> MAP = Map.ofEntries(
            Map.entry("minecraft:cobblestone", "minecraft:blackstone"),
            Map.entry("minecraft:mossy_cobblestone", "minecraft:blackstone"),
            Map.entry("minecraft:stone", "minecraft:polished_blackstone"),
            Map.entry("minecraft:stone_bricks", "minecraft:polished_blackstone_bricks"),
            Map.entry("minecraft:mossy_stone_bricks", "minecraft:polished_blackstone_bricks"),
            Map.entry("minecraft:cobblestone_stairs", "minecraft:blackstone_stairs"),
            Map.entry("minecraft:mossy_cobblestone_stairs", "minecraft:blackstone_stairs"),
            Map.entry("minecraft:stone_stairs", "minecraft:polished_blackstone_stairs"),
            Map.entry("minecraft:stone_brick_stairs", "minecraft:polished_blackstone_brick_stairs"),
            Map.entry("minecraft:mossy_stone_brick_stairs", "minecraft:polished_blackstone_brick_stairs"),
            Map.entry("minecraft:cobblestone_slab", "minecraft:blackstone_slab"),
            Map.entry("minecraft:mossy_cobblestone_slab", "minecraft:blackstone_slab"),
            Map.entry("minecraft:smooth_stone_slab", "minecraft:polished_blackstone_slab"),
            Map.entry("minecraft:stone_slab", "minecraft:polished_blackstone_slab"),
            Map.entry("minecraft:stone_brick_slab", "minecraft:polished_blackstone_brick_slab"),
            Map.entry("minecraft:mossy_stone_brick_slab", "minecraft:polished_blackstone_brick_slab"),
            Map.entry("minecraft:stone_brick_wall", "minecraft:polished_blackstone_brick_wall"),
            Map.entry("minecraft:mossy_stone_brick_wall", "minecraft:polished_blackstone_brick_wall"),
            Map.entry("minecraft:cobblestone_wall", "minecraft:blackstone_wall"),
            Map.entry("minecraft:mossy_cobblestone_wall", "minecraft:blackstone_wall"),
            Map.entry("minecraft:chiseled_stone_bricks", "minecraft:chiseled_polished_blackstone"),
            Map.entry("minecraft:cracked_stone_bricks", "minecraft:cracked_polished_blackstone_bricks"),
            Map.entry("minecraft:iron_bars", "minecraft:iron_bars"));
        @Override
        public BlockInfo processBlock(Ctx ctx, int ox, int oy, int oz, int px, int py, int pz, BlockInfo original, BlockInfo current, PlaceSettings s) {
            String to = MAP.get(ctx.table.name(current.state()));
            if (to == null) return current;
            int st = ctx.table.registry().defaultStateId(to);
            BlockStateTable t = ctx.table;
            for (String p : new String[]{"facing", "half", "shape"}) {
                String v = t.prop(current.state(), p);
                if (v != null && t.prop(st, p) != null) st = t.with(st, p, v);
            }
            String type = t.prop(current.state(), "type");
            if (type != null && t.prop(st, "type") != null) st = t.with(st, "type", type);
            return new BlockInfo(current.x(), current.y(), current.z(), st, current.nbt());
        }
    }

    // ------------------------------------------------------------------ block_age

    public static final class BlockAge extends Processor {
        private final float mossiness;
        public BlockAge(float mossiness) { this.mossiness = mossiness; }
        private static final String[] HORIZONTAL = {"north", "east", "south", "west"};   // Direction.Plane.HORIZONTAL
        private static final String[] HALVES = {"top", "bottom"};                          // Half.values()
        @Override
        public BlockInfo processBlock(Ctx ctx, int ox, int oy, int oz, int px, int py, int pz, BlockInfo original, BlockInfo current, PlaceSettings s) {
            LegacyRandom r = s.getRandom(current.x(), current.y(), current.z());
            BlockStateTable t = ctx.table;
            int st = current.state();
            String name = t.name(st);
            int out = -1;
            if (name.equals("minecraft:stone_bricks") || name.equals("minecraft:stone") || name.equals("minecraft:chiseled_stone_bricks")) {
                out = fullStone(ctx, r);
            } else if (BlockTags.get().blocksOf("minecraft:stairs").contains(name)) {
                out = stairs(ctx, r, st);
            } else if (BlockTags.get().blocksOf("minecraft:slabs").contains(name)) {
                out = r.nextFloat() < mossiness ? t.registry().defaultStateId("minecraft:mossy_stone_brick_slab") : -1;
            } else if (BlockTags.get().blocksOf("minecraft:walls").contains(name)) {
                out = r.nextFloat() < mossiness ? t.registry().defaultStateId("minecraft:mossy_stone_brick_wall") : -1;
            } else if (name.equals("minecraft:obsidian")) {
                out = r.nextFloat() < 0.15F ? t.registry().defaultStateId("minecraft:crying_obsidian") : -1;
            }
            return out >= 0 ? new BlockInfo(current.x(), current.y(), current.z(), out, current.nbt()) : current;
        }
        private int fullStone(Ctx ctx, LegacyRandom r) {
            if (r.nextFloat() >= 0.5F) return -1;
            BlockStateTable t = ctx.table;
            int[] normal = {t.registry().defaultStateId("minecraft:cracked_stone_bricks"), randomFacingStairs(ctx, r, "minecraft:stone_brick_stairs")};
            int[] mossy = {t.registry().defaultStateId("minecraft:mossy_stone_bricks"), randomFacingStairs(ctx, r, "minecraft:mossy_stone_brick_stairs")};
            return randomBlock(r, normal, mossy);
        }
        private int randomFacingStairs(Ctx ctx, LegacyRandom r, String block) {
            BlockStateTable t = ctx.table;
            int st = t.registry().defaultStateId(block);
            st = t.with(st, "facing", HORIZONTAL[r.nextInt(4)]);
            return t.with(st, "half", HALVES[r.nextInt(2)]);
        }
        private int stairs(Ctx ctx, LegacyRandom r, int state) {
            BlockStateTable t = ctx.table;
            String facing = t.prop(state, "facing"), half = t.prop(state, "half");
            if (r.nextFloat() >= 0.5F) return -1;
            // 바닐라: 일반 배열은 정적 {stone_slab, stone_brick_slab}, 이끼 배열은 {mossy_stone_brick_stairs(facing,half), mossy_stone_brick_slab}
            int[] normal = {t.registry().defaultStateId("minecraft:stone_slab"), t.registry().defaultStateId("minecraft:stone_brick_slab")};
            int mossyStairs = t.with(t.with(t.registry().defaultStateId("minecraft:mossy_stone_brick_stairs"), "facing", facing), "half", half);
            int[] mossy = {mossyStairs, t.registry().defaultStateId("minecraft:mossy_stone_brick_slab")};
            return randomBlock(r, normal, mossy);
        }
        private int randomBlock(LegacyRandom r, int[] normal, int[] mossy) {
            int[] pick = r.nextFloat() < mossiness ? mossy : normal;
            return pick[r.nextInt(pick.length)];
        }
    }

    // ------------------------------------------------------------------ rule

    public interface RuleTest { boolean test(Processor.Ctx ctx, int state, LegacyRandom r); }

    public interface PosRuleTest { boolean test(int inX, int inY, int inZ, int curX, int curY, int curZ, int pivX, int pivY, int pivZ, LegacyRandom r); }

    public interface BlockEntityModifier { NbtCompound apply(LegacyRandom r, NbtCompound tag); }

    public record Rule(RuleTest input, RuleTest location, PosRuleTest position, int outputState, BlockEntityModifier modifier) {}

    public static final class RuleProcessor extends Processor {
        private final List<Rule> rules;
        public RuleProcessor(List<Rule> rules) { this.rules = rules; }
        @Override
        public BlockInfo processBlock(Ctx ctx, int ox, int oy, int oz, int px, int py, int pz, BlockInfo original, BlockInfo current, PlaceSettings s) {
            LegacyRandom r = new LegacyRandom(PlaceSettings.getSeed(current.x(), current.y(), current.z()));
            int locState = ctx.view.getBlock(current.x(), current.y(), current.z());
            for (Rule rule : rules) {
                if (rule.input.test(ctx, current.state(), r) && rule.location.test(ctx, locState, r)
                    && rule.position.test(original.x(), original.y(), original.z(), current.x(), current.y(), current.z(), px, py, pz, r)) {
                    return new BlockInfo(current.x(), current.y(), current.z(), rule.outputState, rule.modifier.apply(r, current.nbt()));
                }
            }
            return current;
        }
    }

    // ------------------------------------------------------------------ capped

    public static final class Capped extends Processor {
        private final Processor delegate;
        private final int limitMin, limitMax;   // IntProvider (constant 또는 uniform)
        public Capped(Processor delegate, int limitMin, int limitMax) { this.delegate = delegate; this.limitMin = limitMin; this.limitMax = limitMax; }
        @Override
        public List<BlockInfo> finalizeProcessing(Ctx ctx, int ox, int oy, int oz, int px, int py, int pz,
                                                  List<BlockInfo> originals, List<BlockInfo> processed, PlaceSettings s) {
            if (limitMax == 0 || processed.isEmpty()) return processed;
            if (originals.size() != processed.size()) return processed;
            // RandomSource.create(worldSeed).forkPositional().at(offset)
            LegacyRandom base = new LegacyRandom(ctx.worldSeed());
            long forkSeed = base.nextLong();
            LegacyRandom r = new LegacyRandom(PlaceSettings.getSeed(ox, oy, oz) ^ forkSeed);
            int sample = limitMin == limitMax ? limitMin : limitMin + r.nextInt(limitMax - limitMin + 1);
            int limit = Math.min(sample, processed.size());
            if (limit < 1) return processed;
            // Util.toShuffledList(IntStream.range(0, n), r)
            int n = processed.size();
            int[] idx = new int[n];
            for (int i = 0; i < n; i++) idx[i] = i;
            for (int i = n; i > 1; i--) {
                int j = r.nextInt(i);
                int t = idx[i - 1]; idx[i - 1] = idx[j]; idx[j] = t;
            }
            int count = 0;
            for (int k = 0; k < n && count < limit; k++) {
                int i = idx[k];
                BlockInfo a = originals.get(i), b = processed.get(i);
                BlockInfo c = delegate.processBlock(ctx, ox, oy, oz, px, py, pz, a, b, s);
                if (c != null && !b.equals(c)) {
                    count++;
                    processed.set(i, c);
                }
            }
            return processed;
        }
    }

    // ------------------------------------------------------------------ 보조: 규칙 테스트 구현

    public static RuleTest alwaysTrue() { return (ctx, st, r) -> true; }

    public static RuleTest blockMatch(String block) { return (ctx, st, r) -> ctx.table.name(st).equals(block); }

    public static RuleTest blockStateMatch(int state) { return (ctx, st, r) -> st == state; }

    public static RuleTest tagMatch(Set<String> blocks) { return (ctx, st, r) -> blocks.contains(ctx.table.name(st)); }

    public static RuleTest randomBlockMatch(String block, float p) { return (ctx, st, r) -> ctx.table.name(st).equals(block) && r.nextFloat() < p; }

    public static RuleTest randomBlockStateMatch(int state, float p) { return (ctx, st, r) -> st == state && r.nextFloat() < p; }

    public static PosRuleTest alwaysTruePos() { return (a, b, c, d, e, f, g, h, i, r) -> true; }

    public static PosRuleTest linearPos(float minChance, float maxChance, int minDist, int maxDist) {
        return (ix, iy, iz, cx, cy, cz, px, py, pz, r) -> {
            int dist = (int) ((float) Math.abs(cx - px) + (float) Math.abs(cy - py) + (float) Math.abs(cz - pz));
            float f = r.nextFloat();
            return f <= clampedLerp(minChance, maxChance, inverseLerp((float) dist, (float) minDist, (float) maxDist));
        };
    }

    public static PosRuleTest axisAlignedLinearPos(float minChance, float maxChance, int minDist, int maxDist, String axis) {
        return (ix, iy, iz, cx, cy, cz, px, py, pz, r) -> {
            // Direction.get(POSITIVE, axis) 의 step 성분
            int sx = axis.equals("x") ? 1 : 0, sy = axis.equals("y") ? 1 : 0, sz = axis.equals("z") ? 1 : 0;
            float a = (float) Math.abs((cx - px) * sx);
            float b = (float) Math.abs((cy - py) * sy);
            float c = (float) Math.abs((cz - pz) * sz);
            int dist = (int) (a + b + c);
            float f = r.nextFloat();
            return f <= clampedLerp(minChance, maxChance, inverseLerp((float) dist, (float) minDist, (float) maxDist));
        };
    }

    static float inverseLerp(float v, float min, float max) { return (v - min) / (max - min); }

    static float clampedLerp(float a, float b, float t) {
        if (t < 0.0F) return a;
        return t > 1.0F ? b : a + t * (b - a);
    }

    public static BlockEntityModifier passthrough() { return (r, tag) -> tag; }

    public static BlockEntityModifier clear() { return (r, tag) -> new NbtCompound(); }

    public static BlockEntityModifier appendStatic(NbtCompound data) {
        return (r, tag) -> {
            if (tag == null) return data.copy();
            NbtCompound c = tag.copy();
            for (Map.Entry<String, Object> e : data.tags().entrySet()) c.put(e.getKey(), e.getValue());
            return c;
        };
    }

    public static BlockEntityModifier appendLoot(String lootTable) {
        return (r, tag) -> {
            NbtCompound c = tag == null ? new NbtCompound() : tag.copy();
            c.putString("LootTable", lootTable);
            c.putLong("LootTableSeed", r.nextLong());
            return c;
        };
    }

    static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> r = new HashSet<>(a);
        r.addAll(b);
        return r;
    }

    static List<Processor> list(Processor... p) {
        List<Processor> l = new ArrayList<>();
        for (Processor x : p) l.add(x);
        return l;
    }
}
