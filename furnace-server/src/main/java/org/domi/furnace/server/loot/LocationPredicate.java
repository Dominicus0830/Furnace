// 바닐라 LocationPredicate — position·biomes·structures·dimension·smokey·light·block(상태·nbt)·fluid·can_see_sky.
// 월드를 읽는 조건은 그 칸 청크를 가진 스레드에서만 답이 나온다(아니면 거짓 — 바닐라의 isLoaded 검사 자리).
package org.domi.furnace.server.loot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public record LocationPredicate(Bounds x, Bounds y, Bounds z, HolderSet biomes, String dimension,
                                HolderSet blocks, Map<String, JsonElement> state,
                                Predicate<String> structures, Bounds light, Boolean smokey, Boolean canSeeSky,
                                HolderSet fluids, Map<String, JsonElement> fluidState, NbtCompound blockNbt) {

    public static LocationPredicate parse(JsonElement e, LootRegistries regs) {
        JsonObject o = e.getAsJsonObject();
        Bounds x = Bounds.ANY, y = Bounds.ANY, z = Bounds.ANY;
        HolderSet biomes = null, blocks = null, fluids = null;
        String dimension = null;
        Map<String, JsonElement> state = null, fluidState = null;
        Predicate<String> structures = null;
        Bounds light = null;
        Boolean smokey = null, canSeeSky = null;
        NbtCompound blockNbt = null;
        for (var f : o.entrySet()) {
            switch (f.getKey()) {
                case "position" -> {
                    JsonObject p = f.getValue().getAsJsonObject();
                    x = Bounds.parse(p.get("x")); y = Bounds.parse(p.get("y")); z = Bounds.parse(p.get("z"));
                }
                case "biomes" -> biomes = HolderSet.parse("minecraft:worldgen/biome", f.getValue(), regs);
                case "structures" -> structures = structureSet(f.getValue());
                case "dimension" -> dimension = LootJson.id(f.getValue().getAsString());
                case "smokey" -> smokey = f.getValue().getAsBoolean();
                case "can_see_sky" -> canSeeSky = f.getValue().getAsBoolean();
                case "light" -> light = Bounds.parse(f.getValue().getAsJsonObject().get("light"));
                case "block" -> {
                    JsonObject b = f.getValue().getAsJsonObject();
                    if (b.has("blocks")) blocks = HolderSet.parse("minecraft:block", b.get("blocks"), regs);
                    if (b.has("state")) {
                        state = new LinkedHashMap<>();
                        for (var s : b.getAsJsonObject("state").entrySet()) state.put(s.getKey(), s.getValue());
                    }
                    if (b.has("nbt")) {
                        try {
                            blockNbt = org.domi.furnace.server.command.arguments.TagParser.parseTag(b.get("nbt").getAsString());
                        } catch (org.domi.furnace.server.command.brigadier.CommandSyntaxException ex) {
                            throw new LootParseException("위치 조건 block.nbt 를 읽을 수 없다: " + ex.getMessage());
                        }
                    }
                }
                case "fluid" -> {
                    JsonObject b = f.getValue().getAsJsonObject();
                    if (b.has("fluids")) fluids = HolderSet.parse("minecraft:fluid", b.get("fluids"), regs);
                    if (b.has("state")) {
                        fluidState = new LinkedHashMap<>();
                        for (var s : b.getAsJsonObject("state").entrySet()) fluidState.put(s.getKey(), s.getValue());
                    }
                }
                // 바닐라 코덱(RecordCodecBuilder)은 모르는 키를 오류로 보지 않고 무시한다 — 데이터 팩 오타는 조용히 빠진다
                default -> org.slf4j.LoggerFactory.getLogger(LocationPredicate.class).warn("모르는 위치 조건 필드(바닐라처럼 무시한다): {}", f.getKey());
            }
        }
        return new LocationPredicate(x, y, z, biomes, dimension, blocks, state, structures, light, smokey, canSeeSky,
                fluids, fluidState, blockNbt);
    }

    /** 구조물 HolderSet — "id" | ["id", …] | "#태그"(worldgen/structure 태그). */
    private static Predicate<String> structureSet(JsonElement e) {
        if (e.isJsonArray()) {
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (JsonElement x : e.getAsJsonArray()) ids.add(LootJson.id(x.getAsString()));
            return ids::contains;
        }
        String s = e.getAsString();
        if (s.startsWith("#")) {
            List<String> members = org.domi.furnace.server.world.gen.structure.StructureRegistry.structureTag(LootJson.id(s.substring(1)));
            if (members == null) throw new LootParseException("모르는 구조물 태그: " + s);
            return members::contains;
        }
        String id = LootJson.id(s);
        return id::equals;
    }

    public boolean matches(LootContext ctx, double[] pos) {
        if (pos == null) return false;
        if (!x.matches(pos[0]) || !y.matches(pos[1]) || !z.matches(pos[2])) return false;
        if (dimension != null && !dimension.equals(ctx.world().dimension())) return false;
        int bx = (int) Math.floor(pos[0]), by = (int) Math.floor(pos[1]), bz = (int) Math.floor(pos[2]);
        LootWorld w = ctx.world();
        if (biomes != null) {
            String b = w.biomeAt(bx, by, bz);
            if (b == null || !biomes.contains(b, ctx.registries())) return false;
        }
        if (structures != null && !Boolean.TRUE.equals(w.structureWithPieceAt(bx, by, bz, structures))) return false;
        if (smokey != null && !smokey.equals(w.smokeyAt(bx, by, bz))) return false;
        if (light != null) {
            Integer l = w.lightAt(bx, by, bz);
            if (l == null || !light.matches(l)) return false;
        }
        if (blocks != null || state != null || blockNbt != null) {
            LootBlockState s = w.blockAt(bx, by, bz);
            if (s == null) return false;
            if (blocks != null && !blocks.contains(s.block(), ctx.registries())) return false;
            if (state != null && !StateProperties.matches(state, s)) return false;
            if (blockNbt != null) {
                NbtCompound be = w.blockEntityNbtAt(bx, by, bz);
                if (be == null || !org.domi.furnace.server.net.io.nbt.NbtTags.compareNbt(blockNbt, be, true)) return false;
            }
        }
        if (fluids != null || fluidState != null) {
            LootBlockState f = w.fluidAt(bx, by, bz);
            if (f == null) return false;
            if (fluids != null && !fluids.contains(f.block(), ctx.registries())) return false;
            if (fluidState != null && !StateProperties.matches(fluidState, f)) return false;
        }
        if (canSeeSky != null && !canSeeSky.equals(w.canSeeSkyAt(bx, by, bz))) return false;
        return true;
    }
}
