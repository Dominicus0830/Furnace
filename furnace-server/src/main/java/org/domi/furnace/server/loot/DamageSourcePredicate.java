// 바닐라 DamageSourcePredicate — tags[{id, expected}], source_entity, direct_entity, is_direct.
package org.domi.furnace.server.loot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

public record DamageSourcePredicate(List<String[]> tags, EntityPredicate source, EntityPredicate direct, Boolean isDirect) {

    public static DamageSourcePredicate parse(JsonElement e, LootRegistries regs) {
        JsonObject o = e.getAsJsonObject();
        List<String[]> tags = new ArrayList<>();
        if (o.has("tags")) {
            for (JsonElement t : o.getAsJsonArray("tags")) {
                JsonObject x = t.getAsJsonObject();
                tags.add(new String[]{LootJson.id(x, "id"), String.valueOf(LootJson.req(x, "expected").getAsBoolean())});
            }
        }
        return new DamageSourcePredicate(tags,
                o.has("source_entity") ? EntityPredicate.parse(o.get("source_entity"), regs) : null,
                o.has("direct_entity") ? EntityPredicate.parse(o.get("direct_entity"), regs) : null,
                o.has("is_direct") ? o.get("is_direct").getAsBoolean() : null);
    }

    public boolean matches(LootContext ctx, double[] origin, LootDamageSource src) {
        for (String[] t : tags) if (src.is(t[0]) != Boolean.parseBoolean(t[1])) return false;
        if (direct != null && !direct.matches(ctx, origin, src.directEntity())) return false;
        if (source != null && !source.matches(ctx, origin, src.entity())) return false;
        return isDirect == null || isDirect == (src.entity() != null && src.entity() == src.directEntity());
    }
}
