package net.minecraft.util.datafix.fixes;

/**
 * 데이터 고치기 종류 이름(Mojang 이름 shim, N2b-6) — 바닐라 1.21.1 상수 그대로. 값은 DataFixerUpper {@code DSL.TypeReference}(이름 + toString "@이름")라
 * 서버 제공 라이브러리 묶음 datafixerupper 가 있어야 초기화된다.
 */
public class References {

    public static final com.mojang.datafixers.DSL.TypeReference LEVEL = reference("level");
    public static final com.mojang.datafixers.DSL.TypeReference PLAYER = reference("player");
    public static final com.mojang.datafixers.DSL.TypeReference CHUNK = reference("chunk");
    public static final com.mojang.datafixers.DSL.TypeReference HOTBAR = reference("hotbar");
    public static final com.mojang.datafixers.DSL.TypeReference OPTIONS = reference("options");
    public static final com.mojang.datafixers.DSL.TypeReference STRUCTURE = reference("structure");
    public static final com.mojang.datafixers.DSL.TypeReference STATS = reference("stats");
    public static final com.mojang.datafixers.DSL.TypeReference SAVED_DATA_COMMAND_STORAGE = reference("saved_data/command_storage");
    public static final com.mojang.datafixers.DSL.TypeReference SAVED_DATA_FORCED_CHUNKS = reference("saved_data/chunks");
    public static final com.mojang.datafixers.DSL.TypeReference SAVED_DATA_MAP_DATA = reference("saved_data/map_data");
    public static final com.mojang.datafixers.DSL.TypeReference SAVED_DATA_MAP_INDEX = reference("saved_data/idcounts");
    public static final com.mojang.datafixers.DSL.TypeReference SAVED_DATA_RAIDS = reference("saved_data/raids");
    public static final com.mojang.datafixers.DSL.TypeReference SAVED_DATA_RANDOM_SEQUENCES = reference("saved_data/random_sequences");
    public static final com.mojang.datafixers.DSL.TypeReference SAVED_DATA_STRUCTURE_FEATURE_INDICES = reference("saved_data/structure_feature_indices");
    public static final com.mojang.datafixers.DSL.TypeReference SAVED_DATA_SCOREBOARD = reference("saved_data/scoreboard");
    public static final com.mojang.datafixers.DSL.TypeReference ADVANCEMENTS = reference("advancements");
    public static final com.mojang.datafixers.DSL.TypeReference POI_CHUNK = reference("poi_chunk");
    public static final com.mojang.datafixers.DSL.TypeReference ENTITY_CHUNK = reference("entity_chunk");
    public static final com.mojang.datafixers.DSL.TypeReference BLOCK_ENTITY = reference("block_entity");
    public static final com.mojang.datafixers.DSL.TypeReference ITEM_STACK = reference("item_stack");
    public static final com.mojang.datafixers.DSL.TypeReference BLOCK_STATE = reference("block_state");
    public static final com.mojang.datafixers.DSL.TypeReference FLAT_BLOCK_STATE = reference("flat_block_state");
    public static final com.mojang.datafixers.DSL.TypeReference DATA_COMPONENTS = reference("data_components");
    public static final com.mojang.datafixers.DSL.TypeReference VILLAGER_TRADE = reference("villager_trade");
    public static final com.mojang.datafixers.DSL.TypeReference PARTICLE = reference("particle");
    public static final com.mojang.datafixers.DSL.TypeReference ENTITY_NAME = reference("entity_name");
    public static final com.mojang.datafixers.DSL.TypeReference ENTITY_TREE = reference("entity_tree");
    public static final com.mojang.datafixers.DSL.TypeReference ENTITY = reference("entity");
    public static final com.mojang.datafixers.DSL.TypeReference BLOCK_NAME = reference("block_name");
    public static final com.mojang.datafixers.DSL.TypeReference ITEM_NAME = reference("item_name");
    public static final com.mojang.datafixers.DSL.TypeReference GAME_EVENT_NAME = reference("game_event_name");
    public static final com.mojang.datafixers.DSL.TypeReference UNTAGGED_SPAWNER = reference("untagged_spawner");
    public static final com.mojang.datafixers.DSL.TypeReference STRUCTURE_FEATURE = reference("structure_feature");
    public static final com.mojang.datafixers.DSL.TypeReference OBJECTIVE = reference("objective");
    public static final com.mojang.datafixers.DSL.TypeReference TEAM = reference("team");
    public static final com.mojang.datafixers.DSL.TypeReference RECIPE = reference("recipe");
    public static final com.mojang.datafixers.DSL.TypeReference BIOME = reference("biome");
    public static final com.mojang.datafixers.DSL.TypeReference MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST = reference("multi_noise_biome_source_parameter_list");
    public static final com.mojang.datafixers.DSL.TypeReference WORLD_GEN_SETTINGS = reference("world_gen_settings");

    public References() {}

    public static com.mojang.datafixers.DSL.TypeReference reference(String name) { return Lib.reference(name); }

    /** DataFixerUpper 를 쓰는 본문(CompatLinkTest 가 라이브러리 없이 링크를 볼 때 이 이름만 건너뛴다). */
    static final class Lib {
        private Lib() {}

        static com.mojang.datafixers.DSL.TypeReference reference(String name) {
            return new com.mojang.datafixers.DSL.TypeReference() {
                @Override
                public String typeName() { return name; }

                @Override
                public String toString() { return "@" + name; }
            };
        }
    }
}
