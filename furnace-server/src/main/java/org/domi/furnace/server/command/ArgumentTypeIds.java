// command_argument_type 레지스트리 프로토콜 id(1.21.1, 데이터 생성기 registries.json 순서).
package org.domi.furnace.server.command;

import java.util.HashMap;
import java.util.Map;

public final class ArgumentTypeIds {

    private static final String[] ORDER = {
            "brigadier:bool", "brigadier:float", "brigadier:double", "brigadier:integer", "brigadier:long", "brigadier:string",
            "minecraft:entity", "minecraft:game_profile", "minecraft:block_pos", "minecraft:column_pos", "minecraft:vec3",
            "minecraft:vec2", "minecraft:block_state", "minecraft:block_predicate", "minecraft:item_stack",
            "minecraft:item_predicate", "minecraft:color", "minecraft:component", "minecraft:style", "minecraft:message",
            "minecraft:nbt_compound_tag", "minecraft:nbt_tag", "minecraft:nbt_path", "minecraft:objective",
            "minecraft:objective_criteria", "minecraft:operation", "minecraft:particle", "minecraft:angle",
            "minecraft:rotation", "minecraft:scoreboard_slot", "minecraft:score_holder", "minecraft:swizzle",
            "minecraft:team", "minecraft:item_slot", "minecraft:item_slots", "minecraft:resource_location",
            "minecraft:function", "minecraft:entity_anchor", "minecraft:int_range", "minecraft:float_range",
            "minecraft:dimension", "minecraft:gamemode", "minecraft:time", "minecraft:resource_or_tag",
            "minecraft:resource_or_tag_key", "minecraft:resource", "minecraft:resource_key", "minecraft:template_mirror",
            "minecraft:template_rotation", "minecraft:heightmap", "minecraft:loot_table", "minecraft:loot_predicate",
            "minecraft:loot_modifier", "minecraft:uuid"};
    private static final Map<String, Integer> IDS = new HashMap<>();

    static {
        for (int i = 0; i < ORDER.length; i++) IDS.put(ORDER[i], i);
    }

    private ArgumentTypeIds() {}

    public static String nameOf(int id) { return id >= 0 && id < ORDER.length ? ORDER[id] : "id:" + id; }

    public static int idOf(String name) {
        Integer id = IDS.get(name);
        if (id == null) throw new IllegalArgumentException("알 수 없는 인자 타입: " + name);
        return id;
    }
}
