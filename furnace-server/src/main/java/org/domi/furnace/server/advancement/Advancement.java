// 바닐라 Advancement — 부모, 표시, 보상, 조건(criteria), 요구사항(AND 묶음의 OR 목록), 원격 측정 여부.
package org.domi.furnace.server.advancement;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.loot.Components;
import org.domi.furnace.server.loot.ItemData;
import org.domi.furnace.server.loot.LootRegistries;

public record Advancement(ResourceId id, ResourceId parent, DisplayInfo display, Rewards rewards, Map<String, Criterion> criteria,
                          List<List<String>> requirements, boolean sendsTelemetryEvent) {

    /** 바닐라 AdvancementRewards. */
    public record Rewards(int experience, List<String> loot, List<ResourceId> recipes, String function) {
        static final Rewards EMPTY = new Rewards(0, List.of(), List.of(), null);
    }

    public boolean isRoot() { return parent == null; }

    /** 바닐라 AdvancementRequirements.test — 묶음마다 하나 이상 달성(빈 요구사항이면 거짓). */
    public static boolean test(List<List<String>> reqs, java.util.function.Predicate<String> done) {
        if (reqs.isEmpty()) return false;
        for (List<String> g : reqs) {
            boolean any = false;
            for (String n : g) if (done.test(n)) { any = true; break; }
            if (!any) return false;
        }
        return true;
    }

    static Advancement parse(ResourceId id, JsonElement json, LootRegistries regs) {
        if (!json.isJsonObject()) throw new IllegalArgumentException("Not a JSON object: " + json);
        JsonObject o = json.getAsJsonObject();
        ResourceId parent = o.has("parent") && !o.get("parent").isJsonNull() ? parseId(o.get("parent").getAsString()) : null;
        DisplayInfo display = o.has("display") ? parseDisplay(o.getAsJsonObject("display")) : null;
        Rewards rewards = o.has("rewards") ? parseRewards(o.getAsJsonObject("rewards")) : Rewards.EMPTY;
        if (!o.has("criteria")) throw new IllegalArgumentException("No key criteria in MapLike[{}]");
        Map<String, Criterion> criteria = new LinkedHashMap<>();
        for (var e : o.getAsJsonObject("criteria").entrySet()) criteria.put(e.getKey(), Criterion.parse(e.getValue(), regs));
        if (criteria.isEmpty()) throw new IllegalArgumentException("Advancement criteria cannot be empty");
        List<List<String>> reqs = new ArrayList<>();
        if (o.has("requirements")) {
            for (JsonElement g : (JsonArray) o.get("requirements")) {
                List<String> group = new ArrayList<>();
                for (JsonElement n : g.getAsJsonArray()) group.add(n.getAsString());
                reqs.add(List.copyOf(group));
            }
        } else {
            for (String n : criteria.keySet()) reqs.add(List.of(n));   // 기본: 조건마다 한 묶음(JSON 순서)
        }
        Set<String> union = new LinkedHashSet<>();
        for (List<String> g : reqs) union.addAll(g);
        if (!union.equals(criteria.keySet())) {
            Set<String> missing = new LinkedHashSet<>(criteria.keySet());
            missing.removeAll(union);
            Set<String> unknown = new LinkedHashSet<>(union);
            unknown.removeAll(criteria.keySet());
            throw new IllegalArgumentException("Advancement completion requirements did not exactly match specified criteria. Missing: "
                    + missing + ". Unknown: " + unknown);
        }
        boolean telemetry = o.has("sends_telemetry_event") && o.get("sends_telemetry_event").getAsBoolean();
        return new Advancement(id, parent, display, rewards, criteria, List.copyOf(reqs), telemetry);
    }

    private static ResourceId parseId(String s) {
        ResourceId r = ResourceId.tryParse(s);
        if (r == null) throw new IllegalArgumentException("Not a valid resource location: " + s);
        return r;
    }

    @SuppressWarnings("unchecked")
    private static DisplayInfo parseDisplay(JsonObject d) {
        if (!d.has("icon")) throw new IllegalArgumentException("No key icon in MapLike[" + d + "]");
        JsonElement iconEl = d.get("icon");
        if (!iconEl.isJsonObject()) throw new IllegalArgumentException("Not a JSON object: " + iconEl);
        JsonObject icon = iconEl.getAsJsonObject();
        String item = parseId(icon.get("id").getAsString()).toString();
        if (item.equals("minecraft:air")) throw new IllegalArgumentException("Item must not be minecraft:air");
        if (!ItemData.vanilla().exists(item)) throw new IllegalArgumentException("Unknown item " + item);
        int count = 1;
        if (icon.has("count")) {
            try {
                int c = icon.get("count").getAsInt();
                if (c >= 1 && c <= 99) count = c;   // 범위 밖은 바닐라처럼 1
            } catch (RuntimeException ignored) {
                // 잘못된 개수도 1
            }
        }
        Map<String, Object> comps = icon.has("components") ? (Map<String, Object>) Components.fromJson(icon.get("components")) : Map.of();
        if (!d.has("title")) throw new IllegalArgumentException("No key title in MapLike[" + d + "]");
        if (!d.has("description")) throw new IllegalArgumentException("No key description in MapLike[" + d + "]");
        DisplayInfo.Frame frame = DisplayInfo.Frame.TASK;
        if (d.has("frame")) {
            String f = d.get("frame").getAsString();
            frame = switch (f) {
                case "task" -> DisplayInfo.Frame.TASK;
                case "challenge" -> DisplayInfo.Frame.CHALLENGE;
                case "goal" -> DisplayInfo.Frame.GOAL;
                default -> throw new IllegalArgumentException("Unknown element name:" + f);
            };
        }
        return new DisplayInfo(item, count, comps, d.get("title"), d.get("description"),
                d.has("background") ? parseId(d.get("background").getAsString()).toString() : null, frame,
                !d.has("show_toast") || d.get("show_toast").getAsBoolean(), !d.has("announce_to_chat") || d.get("announce_to_chat").getAsBoolean(),
                d.has("hidden") && d.get("hidden").getAsBoolean());
    }

    private static Rewards parseRewards(JsonObject r) {
        List<String> loot = new ArrayList<>();
        if (r.has("loot")) for (JsonElement e : r.getAsJsonArray("loot")) loot.add(parseId(e.getAsString()).toString());
        List<ResourceId> recipes = new ArrayList<>();
        if (r.has("recipes")) for (JsonElement e : r.getAsJsonArray("recipes")) recipes.add(parseId(e.getAsString()));
        return new Rewards(r.has("experience") ? r.get("experience").getAsInt() : 0, List.copyOf(loot), List.copyOf(recipes),
                r.has("function") ? parseId(r.get("function").getAsString()).toString() : null);
    }
}
