// 바닐라 LocationPredicate 중 position·biomes·dimension·block. 나머지 필드는 거짓으로 본다(경고).
package org.domi.furnace.server.loot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;

public record LocationPredicate(Bounds x, Bounds y, Bounds z, HolderSet biomes, String dimension,
                                HolderSet blocks, Map<String, JsonElement> state, boolean unsupported) {

    static LocationPredicate parse(JsonElement e, LootRegistries regs) {
        JsonObject o = e.getAsJsonObject();
        Bounds x = Bounds.ANY, y = Bounds.ANY, z = Bounds.ANY;
        HolderSet biomes = null, blocks = null;
        String dimension = null;
        Map<String, JsonElement> state = null;
        boolean unsupported = false;
        for (var f : o.entrySet()) {
            switch (f.getKey()) {
                case "position" -> {
                    JsonObject p = f.getValue().getAsJsonObject();
                    x = Bounds.parse(p.get("x")); y = Bounds.parse(p.get("y")); z = Bounds.parse(p.get("z"));
                }
                case "biomes" -> biomes = HolderSet.parse("minecraft:worldgen/biome", f.getValue(), regs);
                case "dimension" -> dimension = LootJson.id(f.getValue().getAsString());
                case "block" -> {
                    JsonObject b = f.getValue().getAsJsonObject();
                    if (b.has("blocks")) blocks = HolderSet.parse("minecraft:block", b.get("blocks"), regs);
                    if (b.has("state")) {
                        state = new LinkedHashMap<>();
                        for (var s : b.getAsJsonObject("state").entrySet()) state.put(s.getKey(), s.getValue());
                    }
                    if (b.has("nbt")) unsupported = true;
                }
                default -> unsupported = true;
            }
        }
        if (unsupported) org.slf4j.LoggerFactory.getLogger(LocationPredicate.class).warn("아직 지원하지 않는 위치 조건 필드: {}", o.keySet());
        return new LocationPredicate(x, y, z, biomes, dimension, blocks, state, unsupported);
    }

    public boolean matches(LootContext ctx, double[] pos) {
        if (pos == null || unsupported) return false;
        if (!x.matches(pos[0]) || !y.matches(pos[1]) || !z.matches(pos[2])) return false;
        if (dimension != null && !dimension.equals(ctx.world().dimension())) return false;
        int bx = (int) Math.floor(pos[0]), by = (int) Math.floor(pos[1]), bz = (int) Math.floor(pos[2]);
        if (biomes != null) {
            String b = ctx.world().biomeAt(bx, by, bz);
            if (b == null || !biomes.contains(b, ctx.registries())) return false;
        }
        if (blocks != null || state != null) {
            LootBlockState s = ctx.world().blockAt(bx, by, bz);
            if (s == null) return false;
            if (blocks != null && !blocks.contains(s.block(), ctx.registries())) return false;
            if (state != null && !StateProperties.matches(state, s)) return false;
        }
        return true;
    }
}
