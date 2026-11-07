// EntityPredicate.type_specific(lightning·player·raider·변종)와 LocationPredicate(structures·light·smokey·block.nbt·fluid·can_see_sky) 시험.
package org.domi.furnace.server.loot;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.gen.carver.LegacyRandom;
import org.domi.furnace.server.world.gen.noise.NoiseRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PredicateFieldsTest {

    @BeforeAll
    static void load() throws Exception { ItemFunctionsTest.load(); }

    static LootContext ctx(LootWorld w) {
        return new LootContext(ItemFunctionsTest.data, w, new LootParams(), new LegacyRandom(1L));
    }

    static boolean entity(String json, LootEntity e) {
        EntityPredicate p = EntityPredicate.parse(JsonParser.parseString(json), ItemFunctionsTest.regs);
        return p.matches(ctx(ItemFunctionsTest.WORLD), new double[] {0, 0, 0}, e);
    }

    static LootEntity typed(String type, Map<String, Object> specific) {
        return new LootEntity() {
            public String type() { return type; }
            public double[] position() { return new double[] {0, 0, 0}; }
            public Object typeSpecific(String key) { return specific.get(key); }
        };
    }

    @Test
    void raiderDefaultsToNoRaidAndNotCaptain() {
        LootEntity captain = typed("minecraft:pillager", Map.of("is_captain", true, "has_raid", false));
        LootEntity grunt = typed("minecraft:pillager", Map.of("is_captain", false, "has_raid", false));
        LootEntity inRaid = typed("minecraft:pillager", Map.of("is_captain", false, "has_raid", true));
        assertTrue(entity("{\"type_specific\":{\"type\":\"minecraft:raider\",\"is_captain\":true}}", captain));
        assertFalse(entity("{\"type_specific\":{\"type\":\"minecraft:raider\",\"is_captain\":true}}", grunt));
        // 바닐라 RaiderPredicate: 안 쓴 필드는 false 와 같아야 한다
        assertTrue(entity("{\"type_specific\":{\"type\":\"minecraft:raider\"}}", grunt));
        assertFalse(entity("{\"type_specific\":{\"type\":\"minecraft:raider\"}}", captain));
        assertTrue(entity("{\"type_specific\":{\"type\":\"minecraft:raider\",\"has_raid\":true}}", inRaid));
        assertFalse(entity("{\"type_specific\":{\"type\":\"minecraft:raider\",\"has_raid\":true}}", grunt));
    }

    @Test
    void lightningChecksFireCountAndStruckEntities() {
        LootEntity victim = typed("minecraft:cow", Map.of());
        LootEntity bolt = typed("minecraft:lightning_bolt", Map.of("blocks_set_on_fire", 3, "entity_struck", List.of(victim)));
        assertTrue(entity("{\"type_specific\":{\"type\":\"minecraft:lightning\",\"blocks_set_on_fire\":{\"min\":2,\"max\":4}}}", bolt));
        assertFalse(entity("{\"type_specific\":{\"type\":\"minecraft:lightning\",\"blocks_set_on_fire\":{\"min\":4}}}", bolt));
        assertTrue(entity("{\"type_specific\":{\"type\":\"minecraft:lightning\",\"entity_struck\":{\"type\":\"minecraft:cow\"}}}", bolt));
        assertFalse(entity("{\"type_specific\":{\"type\":\"minecraft:lightning\",\"entity_struck\":{\"type\":\"minecraft:pig\"}}}", bolt));
    }

    @Test
    void playerLevelGamemodeRecipesAndAdvancements() {
        Map<String, Object> m = Map.of("level", 12, "gamemode", "survival", "recipe:minecraft:stick", true,
                "recipe:minecraft:bread", false, "advancement:minecraft:story/root", true, "advancement:minecraft:story/root|crit", false);
        LootEntity p = typed("minecraft:player", m);
        String t = "{\"type_specific\":{\"type\":\"minecraft:player\",";
        assertTrue(entity(t + "\"level\":{\"min\":10}}}", p));
        assertFalse(entity(t + "\"level\":{\"min\":13}}}", p));
        assertTrue(entity(t + "\"gamemode\":[\"survival\",\"creative\"]}}", p));
        assertFalse(entity(t + "\"gamemode\":[\"adventure\"]}}", p));
        assertTrue(entity(t + "\"recipes\":{\"minecraft:stick\":true,\"minecraft:bread\":false}}}", p));
        assertFalse(entity(t + "\"recipes\":{\"minecraft:bread\":true}}}", p));
        assertTrue(entity(t + "\"advancements\":{\"minecraft:story/root\":true}}}", p));
        assertTrue(entity(t + "\"advancements\":{\"minecraft:story/root\":{\"crit\":false}}}}", p));
        assertFalse(entity(t + "\"advancements\":{\"minecraft:story/root\":{\"crit\":true}}}}", p));
        // 모르는 발전 과제(null)는 거짓
        assertFalse(entity(t + "\"advancements\":{\"minecraft:nope\":true}}}", p));
        // 아직 못 하는 필드는 거짓
        assertFalse(entity(t + "\"looking_at\":{\"type\":\"minecraft:cow\"}}}", p));
    }

    @Test
    void enumVariantsCompareByNameAndRegistryVariantsById() {
        LootEntity axolotl = typed("minecraft:axolotl", Map.of("variant", "gold"));
        assertTrue(entity("{\"type_specific\":{\"type\":\"minecraft:axolotl\",\"variant\":\"gold\"}}", axolotl));
        assertFalse(entity("{\"type_specific\":{\"type\":\"minecraft:axolotl\",\"variant\":\"blue\"}}", axolotl));
        LootEntity cat = typed("minecraft:cat", Map.of("variant", "minecraft:tabby"));
        assertTrue(entity("{\"type_specific\":{\"type\":\"minecraft:cat\",\"variant\":\"tabby\"}}", cat));
        assertTrue(entity("{\"type_specific\":{\"type\":\"minecraft:cat\",\"variant\":[\"minecraft:black\",\"minecraft:tabby\"]}}", cat));
        assertFalse(entity("{\"type_specific\":{\"type\":\"minecraft:cat\",\"variant\":\"minecraft:black\"}}", cat));
        LootEntity wolf = typed("minecraft:wolf", Map.of("variant", "minecraft:pale"));
        assertTrue(entity("{\"type_specific\":{\"type\":\"minecraft:wolf\",\"variant\":\"minecraft:pale\"}}", wolf));
        LootEntity fish = typed("minecraft:tropical_fish", Map.of("variant", "kob"));
        assertTrue(entity("{\"type_specific\":{\"type\":\"minecraft:tropical_fish\",\"variant\":\"kob\"}}", fish));
    }

    // ------------------------------------------------------------------ 위치

    static final NbtCompound CHEST_NBT = ItemFunctionsTest.snbt("{id:\"minecraft:chest\",x:1,y:64,z:2,Items:[],CustomName:'\"Loot\"'}");

    static LootWorld loaded() {
        return new LootWorld() {
            public NoiseRandom random() { return new LegacyRandom(1L); }
            public NoiseRandom randomSequence(ResourceId id) { return new LegacyRandom(1L); }
            public Boolean structureWithPieceAt(int x, int y, int z, Predicate<String> ids) { return ids.test("minecraft:village_plains"); }
            public Boolean smokeyAt(int x, int y, int z) { return true; }
            public Integer lightAt(int x, int y, int z) { return 9; }
            public Boolean canSeeSkyAt(int x, int y, int z) { return false; }
            public LootBlockState fluidAt(int x, int y, int z) { return new LootBlockState("minecraft:water", Map.of("level", "3", "falling", "false")); }
            public LootBlockState blockAt(int x, int y, int z) { return new LootBlockState("minecraft:chest", Map.of("facing", "north")); }
            public NbtCompound blockEntityNbtAt(int x, int y, int z) { return CHEST_NBT; }
        };
    }

    static boolean location(String json, LootWorld w) {
        LocationPredicate p = LocationPredicate.parse(JsonParser.parseString(json), ItemFunctionsTest.regs);
        return p.matches(ctx(w), new double[] {1.5, 64.0, 2.5});
    }

    @Test
    void locationReadsWorldFields() {
        LootWorld w = loaded();
        assertTrue(location("{\"structures\":\"minecraft:village_plains\"}", w));
        assertTrue(location("{\"structures\":[\"minecraft:mansion\",\"minecraft:village_plains\"]}", w));
        assertFalse(location("{\"structures\":\"minecraft:mansion\"}", w));
        assertTrue(location("{\"smokey\":true}", w));
        assertFalse(location("{\"smokey\":false}", w));
        assertTrue(location("{\"light\":{\"light\":{\"min\":8,\"max\":10}}}", w));
        assertFalse(location("{\"light\":{\"light\":{\"min\":10}}}", w));
        assertTrue(location("{\"can_see_sky\":false}", w));
        assertFalse(location("{\"can_see_sky\":true}", w));
        assertTrue(location("{\"fluid\":{\"fluids\":\"minecraft:water\",\"state\":{\"level\":\"3\"}}}", w));
        assertFalse(location("{\"fluid\":{\"fluids\":\"minecraft:lava\"}}", w));
        assertFalse(location("{\"fluid\":{\"state\":{\"level\":\"4\"}}}", w));
        assertTrue(location("{\"block\":{\"blocks\":\"minecraft:chest\",\"nbt\":\"{CustomName:'\\\"Loot\\\"'}\"}}", w));
        assertFalse(location("{\"block\":{\"nbt\":\"{CustomName:'\\\"Other\\\"'}\"}}", w));
    }

    @Test
    void locationFieldsAreFalseWhenWorldCannotBeReadOnThisThread() {
        LootWorld unknown = ItemFunctionsTest.WORLD;   // 모든 읽기가 null — 이 스레드가 칸을 갖지 않는 경우
        for (String j : new String[] {"{\"structures\":\"minecraft:mansion\"}", "{\"smokey\":false}", "{\"light\":{\"light\":{\"min\":0}}}",
                "{\"can_see_sky\":false}", "{\"fluid\":{\"fluids\":\"minecraft:water\"}}", "{\"block\":{\"nbt\":\"{}\"}}"}) {
            assertFalse(location(j, unknown), j);
        }
    }

    @Test
    void structureTagIsExpanded() {
        assertTrue(location("{\"structures\":\"#minecraft:village\"}", loaded()));
        assertThrows(LootParseException.class, () -> location("{\"structures\":\"#minecraft:no_such_tag\"}", loaded()));
    }
}
