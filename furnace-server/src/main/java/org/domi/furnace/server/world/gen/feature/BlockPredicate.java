// vanilla BlockPredicate(placement filter / block_column allowed_placement 등) 전체 구현. 지원하지 않는 타입은 조용히 건너뛰지 않고 예외.
package org.domi.furnace.server.world.gen.feature;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;

public interface BlockPredicate {

    boolean test(FeatureWorldView view, int x, int y, int z);

    // ---- 구현 ----

    record True() implements BlockPredicate {
        public boolean test(FeatureWorldView v, int x, int y, int z) { return true; }
    }

    record Not(BlockPredicate inner) implements BlockPredicate {
        public boolean test(FeatureWorldView v, int x, int y, int z) { return !inner.test(v, x, y, z); }
    }

    record AllOf(List<BlockPredicate> preds) implements BlockPredicate {
        public boolean test(FeatureWorldView v, int x, int y, int z) {
            for (BlockPredicate p : preds) if (!p.test(v, x, y, z)) return false;
            return true;
        }
    }

    record AnyOf(List<BlockPredicate> preds) implements BlockPredicate {
        public boolean test(FeatureWorldView v, int x, int y, int z) {
            for (BlockPredicate p : preds) if (p.test(v, x, y, z)) return true;
            return false;
        }
    }

    /** matching_blocks / matching_block_tag: 오프셋 위치 state의 블록이 집합(모든 state)에 속하는가. */
    record MatchingStates(Set<Integer> states, int ox, int oy, int oz) implements BlockPredicate {
        public boolean test(FeatureWorldView v, int x, int y, int z) {
            return states.contains(v.getBlock(x + ox, y + oy, z + oz));
        }
    }

    /** matching_fluids: 오프셋 위치의 유체 종류가 목록에 있는가(FluidKinds 규칙). */
    record MatchingFluids(Set<FluidKind> fluids, FluidKinds kinds, int ox, int oy, int oz) implements BlockPredicate {
        public boolean test(FeatureWorldView v, int x, int y, int z) {
            return fluids.contains(kinds.of(v.getBlock(x + ox, y + oy, z + oz)));
        }
    }

    record Solid(int ox, int oy, int oz) implements BlockPredicate {
        public boolean test(FeatureWorldView v, int x, int y, int z) {
            return BlockAttrs.get().isSolid(v.getBlock(x + ox, y + oy, z + oz));
        }
    }

    record Replaceable(int ox, int oy, int oz) implements BlockPredicate {
        public boolean test(FeatureWorldView v, int x, int y, int z) {
            return BlockAttrs.get().isReplaceable(v.getBlock(x + ox, y + oy, z + oz));
        }
    }

    record HasSturdyFace(int ox, int oy, int oz, int direction) implements BlockPredicate {
        public boolean test(FeatureWorldView v, int x, int y, int z) {
            return BlockAttrs.get().isFaceSturdy(v.getBlock(x + ox, y + oy, z + oz), direction);
        }
    }

    record InsideWorldBounds(int ox, int oy, int oz) implements BlockPredicate {
        public boolean test(FeatureWorldView v, int x, int y, int z) {
            int py = y + oy;
            return py >= ChunkColumn.MIN_Y && py <= ChunkColumn.MAX_Y;
        }
    }

    record WouldSurvive(int stateId, int ox, int oy, int oz, Survival survival) implements BlockPredicate {
        public boolean test(FeatureWorldView v, int x, int y, int z) {
            return survival.canSurvive(v, stateId, x + ox, y + oy, z + oz);
        }
    }

    /** BlockState.canSurvive(level, pos) — 블록 클래스별 규칙(필요한 블록만 구현, 그 외는 로드 시 예외). */
    interface Survival {
        boolean canSurvive(FeatureWorldView view, int stateId, int x, int y, int z);

        /** 로드 시 호출: 이 state의 canSurvive 규칙이 구현돼 있지 않으면 예외. */
        default void requireSupported(int stateId) {}
    }

    // ---- 파서 ----

    final class Parser {
        private final BlockRegistry registry;
        private final java.util.function.Function<String, Set<Integer>> tagStates;
        private final Survival survival;
        private final FluidKinds fluidKinds;

        public Parser(BlockRegistry registry, java.util.function.Function<String, Set<Integer>> tagStates,
                      Survival survival, FluidKinds fluidKinds) {
            this.registry = registry;
            this.tagStates = tagStates;
            this.survival = survival;
            this.fluidKinds = fluidKinds;
        }

        private int[] offset(JsonObject o) {
            if (!o.has("offset")) return new int[]{0, 0, 0};
            JsonArray a = o.getAsJsonArray("offset");
            return new int[]{a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()};
        }

        private Set<Integer> blocks(JsonElement el) {
            Set<Integer> ids = new HashSet<>();
            if (el.isJsonArray()) {
                for (JsonElement e : el.getAsJsonArray()) ids.addAll(registry.stateIdsForBlock(e.getAsString()));
            } else {
                String s = el.getAsString();
                if (s.startsWith("#")) ids.addAll(tagStates.apply(s.substring(1)));
                else ids.addAll(registry.stateIdsForBlock(s));
            }
            return ids;
        }

        public BlockPredicate parse(JsonObject o) {
            String type = o.get("type").getAsString();
            int[] off = offset(o);
            switch (type) {
                case "minecraft:true": return new True();
                case "minecraft:not": return new Not(parse(o.getAsJsonObject("predicate")));
                case "minecraft:all_of": case "minecraft:any_of": {
                    List<BlockPredicate> list = new ArrayList<>();
                    for (JsonElement e : o.getAsJsonArray("predicates")) list.add(parse(e.getAsJsonObject()));
                    return type.equals("minecraft:all_of") ? new AllOf(List.copyOf(list)) : new AnyOf(List.copyOf(list));
                }
                case "minecraft:matching_blocks":
                    return new MatchingStates(Set.copyOf(blocks(o.get("blocks"))), off[0], off[1], off[2]);
                case "minecraft:matching_block_tag":
                    return new MatchingStates(Set.copyOf(tagStates.apply(o.get("tag").getAsString())), off[0], off[1], off[2]);
                case "minecraft:matching_fluids": {
                    Set<FluidKind> kinds = new HashSet<>();
                    JsonElement f = o.get("fluids");
                    List<String> names = new ArrayList<>();
                    if (f.isJsonArray()) f.getAsJsonArray().forEach(e -> names.add(e.getAsString()));
                    else names.add(f.getAsString());
                    for (String n : names) kinds.add(FluidKind.byName(n));
                    return new MatchingFluids(kinds, fluidKinds, off[0], off[1], off[2]);
                }
                case "minecraft:solid": return new Solid(off[0], off[1], off[2]);
                case "minecraft:replaceable": return new Replaceable(off[0], off[1], off[2]);
                case "minecraft:has_sturdy_face": {
                    int dir = switch (o.get("direction").getAsString()) {
                        case "down" -> BlockAttrs.DOWN; case "up" -> BlockAttrs.UP; case "north" -> BlockAttrs.NORTH;
                        case "south" -> BlockAttrs.SOUTH; case "west" -> BlockAttrs.WEST; case "east" -> BlockAttrs.EAST;
                        default -> throw new IllegalArgumentException("방향: " + o);
                    };
                    return new HasSturdyFace(off[0], off[1], off[2], dir);
                }
                case "minecraft:inside_world_bounds": return new InsideWorldBounds(off[0], off[1], off[2]);
                case "minecraft:would_survive": {
                    JsonObject st = o.getAsJsonObject("state");
                    String name = st.get("Name").getAsString();
                    java.util.TreeMap<String, String> props = new java.util.TreeMap<>();
                    if (st.has("Properties")) st.getAsJsonObject("Properties").entrySet()
                        .forEach(e -> props.put(e.getKey(), e.getValue().getAsString()));
                    int id = registry.stateIdOrAir(name, props);
                    survival.requireSupported(id);
                    return new WouldSurvive(id, off[0], off[1], off[2], survival);
                }
                default: throw new IllegalArgumentException("지원하지 않는 block predicate: " + type);
            }
        }
    }
}
