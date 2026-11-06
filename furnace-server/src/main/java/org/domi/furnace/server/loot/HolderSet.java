// 바닐라 HolderSet — "#태그" | "id" | ["id", ...]. 순서가 후보 순서가 된다.
package org.domi.furnace.server.loot;

import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;

public record HolderSet(String registry, String tag, List<String> direct) {

    public static HolderSet parse(String registry, JsonElement e, LootRegistries regs) {
        if (e.isJsonArray()) {
            List<String> ids = new ArrayList<>();
            for (JsonElement x : e.getAsJsonArray()) ids.add(checked(registry, LootJson.id(x.getAsString()), regs));
            return new HolderSet(registry, null, List.copyOf(ids));
        }
        String s = e.getAsString();
        if (s.startsWith("#")) return new HolderSet(registry, LootJson.id(s.substring(1)), null);
        return new HolderSet(registry, null, List.of(checked(registry, LootJson.id(s), regs)));
    }

    private static String checked(String registry, String id, LootRegistries regs) {
        if (!regs.exists(registry, id)) throw new LootParseException("Unknown registry key in " + registry + ": " + id);
        return id;
    }

    public List<String> resolve(LootRegistries regs) { return tag != null ? regs.tag(registry, tag) : direct; }

    public boolean contains(String id, LootRegistries regs) { return resolve(regs).contains(id); }
}
