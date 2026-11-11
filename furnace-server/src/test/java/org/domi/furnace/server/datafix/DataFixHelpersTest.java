// 데이터 픽서 도움 클래스의 결정적 단위 시험: FixUtil(이름공간·UUID·글 컴포넌트)·BlockStateFixes(바닐라 속성 고침 4개)·
// MobEffectFixes(3568 숫자 효과 id)·AttributeIds(3945 표). 기대값은 바닐라 1.21.1 DataFix 클래스의 규칙을 손으로 계산한 것이다.
package org.domi.furnace.server.datafix;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.domi.furnace.server.item.Snbt;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.junit.jupiter.api.Test;

class DataFixHelpersTest {

    private static NbtCompound nbt(String snbt) { return (NbtCompound) Snbt.parse(snbt); }

    // ------------------------------------------------------------------ FixUtil

    @Test
    void nsAddsMinecraftNamespaceOnlyToValidIds() {
        assertEquals("minecraft:stone", FixUtil.ns("stone"));
        assertEquals("minecraft:stone", FixUtil.ns("minecraft:stone"));
        assertEquals("mod:thing", FixUtil.ns("mod:thing"));
        assertEquals("Not Valid!", FixUtil.ns("Not Valid!"), "올바른 위치 문자열이 아니면 그대로");
    }

    @Test
    void renamerMatchesNamespacedKeyAndLeavesOthers() {
        var r = FixUtil.renamer("minecraft:a", "minecraft:b");
        assertEquals("minecraft:b", r.apply("a"));
        assertEquals("minecraft:b", r.apply("minecraft:a"));
        assertEquals("minecraft:c", r.apply("minecraft:c"));
        var m = FixUtil.renamer(java.util.Map.of("minecraft:x", "minecraft:y"));
        assertEquals("minecraft:y", m.apply("x"));
        assertEquals("other", m.apply("other"));
    }

    @Test
    void renameMovesFieldToEndAndOverwritesTarget() {
        NbtCompound c = nbt("{a:1,b:2,c:3}");
        FixUtil.rename(c, "a", "c");
        assertEquals("{b:2,c:1}", Snbt.write(c));
        FixUtil.rename(c, "missing", "b");
        assertEquals(2, c.getInt("b"), "없는 키는 아무것도 하지 않는다");
    }

    @Test
    void updateOnlyTouchesExistingKeyInPlace() {
        NbtCompound c = nbt("{a:1,b:2}");
        FixUtil.update(c, "a", v -> ((Number) v).intValue() + 10);
        FixUtil.update(c, "z", v -> 99);
        assertEquals(11, c.getInt("a"));
        assertFalse(c.contains("z"));
        assertEquals("a", c.tags().keySet().iterator().next(), "자리는 그대로");
    }

    @Test
    void stableJsonSortsKeysAndSkipsHtmlEscaping() {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("z", "<&>");
        o.addProperty("a", 1);
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        arr.add(true);
        arr.add(com.google.gson.JsonNull.INSTANCE);
        o.add("m", arr);
        assertEquals("{\"a\":1,\"m\":[true,null],\"z\":\"<&>\"}", FixUtil.stableJson(o));
    }

    @Test
    void textComponentHelpers() {
        assertEquals("{\"text\":\"hi\"}", FixUtil.plainText("hi"));
        assertEquals("{\"translate\":\"k.v\"}", FixUtil.translatable("k.v"));
        assertEquals("k.v", FixUtil.translationKey("{\"translate\":\"k.v\"}").orElseThrow());
        assertTrue(FixUtil.translationKey("{\"text\":\"x\"}").isEmpty());
        assertTrue(FixUtil.translationKey("not json{").isEmpty(), "깨진 JSON 은 바닐라도 무시");
        assertEquals("{\"text\":\"s\"}", FixUtil.wrapLiteral("s"));
        NbtCompound other = new NbtCompound();
        assertSame(other, FixUtil.wrapLiteral(other));
    }

    @Test
    void uuidMostLeastBecomesIntArray() {
        NbtCompound c = nbt("{OwnerMost:4294967296L,OwnerLeast:8589934593L}");
        FixUtil.uuidFromMostLeast(c, "Owner", "Owner");
        assertArrayEquals(new int[] {1, 0, 2, 1}, (int[]) c.tags().get("Owner"));
        assertFalse(c.contains("OwnerMost"));
        assertFalse(c.contains("OwnerLeast"));
        // 둘 중 하나가 0 이면 바닐라는 건드리지 않는다
        NbtCompound zero = nbt("{OwnerMost:5L,OwnerLeast:0L}");
        FixUtil.uuidFromMostLeast(zero, "Owner", "Owner");
        assertTrue(zero.contains("OwnerMost") && zero.contains("OwnerLeast"));
        assertFalse(zero.contains("Owner"));
    }

    @Test
    void uuidStringBecomesIntArrayAndBadStringStays() {
        UUID u = UUID.fromString("00000001-0000-0002-0000-000000000003");
        NbtCompound c = nbt("{o:\"" + u + "\"}");
        FixUtil.uuidFromString(c, "o", "owner");
        assertArrayEquals(new int[] {1, 2, 0, 3}, (int[]) c.tags().get("owner"));
        assertFalse(c.contains("o"));
        NbtCompound bad = nbt("{o:\"zzz\"}");
        FixUtil.uuidFromString(bad, "o", "owner");
        assertEquals("zzz", bad.getString("o"));
        assertFalse(bad.contains("owner"));
    }

    @Test
    void blockPosCompoundBecomesIntArrayOnlyWhenAllNumbers() {
        assertArrayEquals(new int[] {1, 2, 3}, (int[]) FixUtil.blockPos(nbt("{X:1,Y:2,Z:3}")));
        NbtCompound partial = nbt("{X:1,Y:2}");
        assertSame(partial, FixUtil.blockPos(partial));
        NbtCompound text = nbt("{X:1,Y:2,Z:\"3\"}");
        assertSame(text, FixUtil.blockPos(text));
    }

    @Test
    void mapCompoundsKeepsNonCompoundElements() {
        NbtList l = (NbtList) nbt("{l:[{a:1},{a:2}]}").tags().get("l");
        NbtList out = FixUtil.mapCompounds(l, c -> { c.putInt("a", c.getInt("a") * 2); return c; });
        assertEquals(2, out.size());
        assertEquals(4, ((NbtCompound) out.get(1)).getInt("a"));
        assertEquals(2, FixUtil.stringList("x", "y").size());
    }

    // ------------------------------------------------------------------ BlockStateFixes

    @Test
    void wallPropertyMapsBooleanSidesToHeights() {
        NbtCompound st = nbt("{Name:\"minecraft:cobblestone_wall\",Properties:{east:\"true\",west:\"false\",north:\"true\",south:\"false\",up:\"true\"}}");
        BlockStateFixes.wallProperty(st);
        NbtCompound p = st.getCompound("Properties");
        assertEquals("low", p.getString("east"));
        assertEquals("none", p.getString("west"));
        assertEquals("low", p.getString("north"));
        assertEquals("none", p.getString("south"));
        assertEquals("true", p.getString("up"), "up 은 그대로");
        NbtCompound notWall = nbt("{Name:\"minecraft:stone\",Properties:{east:\"true\"}}");
        BlockStateFixes.wallProperty(notWall);
        assertEquals("true", notWall.getCompound("Properties").getString("east"));
    }

    @Test
    void jigsawFacingBecomesOrientationAndDefaultsToNorth() {
        NbtCompound down = nbt("{Name:\"minecraft:jigsaw\",Properties:{facing:\"down\"}}");
        BlockStateFixes.jigsawRotation(down);
        assertEquals("down_south", down.getCompound("Properties").getString("orientation"));
        assertFalse(down.getCompound("Properties").contains("facing"));
        NbtCompound east = nbt("{Name:\"minecraft:jigsaw\",Properties:{facing:\"east\"}}");
        BlockStateFixes.jigsawRotation(east);
        assertEquals("east_up", east.getCompound("Properties").getString("orientation"));
        NbtCompound none = nbt("{Name:\"minecraft:jigsaw\",Properties:{}}");
        BlockStateFixes.jigsawRotation(none);
        assertEquals("north_up", none.getCompound("Properties").getString("orientation"));
    }

    @Test
    void redstoneLonelySidesBecomeSideButConnectedAxisKeepsNone() {
        // 동쪽만 이어짐: x 축이 켜져 있어 남북은 none 그대로, z 축은 꺼져 있어 이어지지 않은 서쪽은 side(바닐라 RedstoneWireConnectionsFix)
        NbtCompound st = nbt("{Name:\"minecraft:redstone_wire\",Properties:{east:\"side\",west:\"none\",north:\"none\",south:\"none\"}}");
        BlockStateFixes.redstoneConnections(st);
        NbtCompound p = st.getCompound("Properties");
        assertEquals("side", p.getString("east"));
        assertEquals("side", p.getString("west"));
        assertEquals("none", p.getString("north"));
        assertEquals("none", p.getString("south"));
    }

    @Test
    void cauldronRenamesByLevel() {
        NbtCompound empty = nbt("{Name:\"minecraft:cauldron\",Properties:{level:\"0\"}}");
        BlockStateFixes.cauldron(empty);
        assertEquals("minecraft:cauldron", empty.getString("Name"));
        assertFalse(empty.contains("Properties"));
        NbtCompound full = nbt("{Name:\"minecraft:cauldron\",Properties:{level:\"3\"}}");
        BlockStateFixes.cauldron(full);
        assertEquals("minecraft:water_cauldron", full.getString("Name"));
        NbtCompound noProps = nbt("{Name:\"minecraft:cauldron\"}");
        BlockStateFixes.cauldron(noProps);
        assertEquals("minecraft:cauldron", noProps.getString("Name"));
    }

    @Test
    void blockRenameOnlyChangesNameWhenMapped() {
        var f = BlockStateFixes.rename(FixUtil.renamer("minecraft:old", "minecraft:new"));
        assertEquals("minecraft:new", f.apply(nbt("{Name:\"minecraft:old\"}")).getString("Name"));
        assertEquals("minecraft:keep", f.apply(nbt("{Name:\"minecraft:keep\"}")).getString("Name"));
        assertDoesNotThrow(() -> f.apply(nbt("{}")));
    }

    // ------------------------------------------------------------------ MobEffectFixes

    @Test
    void beaconEffectNumbersBecomeNames() {
        NbtCompound be = nbt("{Primary:1,Secondary:10,Levels:2}");
        MobEffectFixes.beacon(be);
        assertEquals("minecraft:speed", be.getString("primary_effect"));
        assertEquals("minecraft:regeneration", be.getString("secondary_effect"));
        assertFalse(be.contains("Primary"));
        assertFalse(be.contains("Secondary"));
        NbtCompound unknown = nbt("{Primary:99,Secondary:0}");
        MobEffectFixes.beacon(unknown);
        assertFalse(unknown.contains("primary_effect"), "모르는 숫자는 필드만 지운다");
        assertFalse(unknown.contains("secondary_effect"));
    }

    @Test
    void effectInstanceRenamesFieldsAndNestsHiddenEffect() {
        NbtCompound e = nbt("{Id:33,Amplifier:1b,Duration:100,Ambient:0b,ShowParticles:1b,ShowIcon:1b,HiddenEffect:{Id:1,Duration:5}}");
        MobEffectFixes.effectInstance(e);
        assertEquals("minecraft:darkness", e.getString("id"));
        assertEquals(100, e.getInt("duration"));
        assertTrue(e.contains("amplifier") && e.contains("ambient") && e.contains("show_particles") && e.contains("show_icon"));
        NbtCompound hidden = e.getCompound("hidden_effect");
        assertEquals("minecraft:speed", hidden.getString("id"));
        assertEquals(5, hidden.getInt("duration"));
    }

    @Test
    void mooshroomStewEffectMovesIntoList() {
        NbtCompound e = nbt("{id:\"minecraft:mooshroom\",EffectId:16b,EffectDuration:200}");
        MobEffectFixes.entity(e);
        NbtCompound stew = (NbtCompound) e.getList("stew_effects").get(0);
        assertEquals("minecraft:night_vision", stew.getString("id"));
        assertEquals(200, stew.getInt("duration"));
        assertFalse(e.contains("EffectId"));
        assertFalse(e.contains("EffectDuration"));
    }

    @Test
    void activeEffectsAreRenamedOnAnyEntity() {
        NbtCompound e = nbt("{id:\"minecraft:zombie\",ActiveEffects:[{Id:2,Duration:10}]}");
        MobEffectFixes.entity(e);
        assertFalse(e.contains("ActiveEffects"));
        NbtCompound fx = (NbtCompound) e.getList("active_effects").get(0);
        assertEquals("minecraft:slowness", fx.getString("id"));
    }

    @Test
    void suspiciousStewAndPotionItems() {
        NbtCompound stew = nbt("{id:\"minecraft:suspicious_stew\",tag:{Effects:[{EffectId:8b,EffectDuration:60}]}}");
        MobEffectFixes.item(stew);
        NbtCompound fx = (NbtCompound) stew.getCompound("tag").getList("effects").get(0);
        assertEquals("minecraft:jump_boost", fx.getString("id"));
        assertEquals(60, fx.getInt("duration"));
        NbtCompound potion = nbt("{id:\"minecraft:potion\",tag:{CustomPotionEffects:[{Id:5,Duration:1}]}}");
        MobEffectFixes.item(potion);
        assertEquals("minecraft:strength", ((NbtCompound) potion.getCompound("tag").getList("custom_potion_effects").get(0)).getString("id"));
        NbtCompound plain = nbt("{id:\"minecraft:stick\",tag:{CustomPotionEffects:[{Id:5}]}}");
        MobEffectFixes.item(plain);
        assertTrue(plain.getCompound("tag").contains("CustomPotionEffects"), "물약이 아닌 아이템은 그대로");
    }

    // ------------------------------------------------------------------ AttributeIds

    @Test
    void attributeIdTablesResolveKnownKeys() {
        assertEquals("minecraft:sprinting", AttributeIds.BY_UUID.get(UUID.fromString("662a6b8d-da3e-4c1c-8813-96ea6097278d")));
        assertEquals("minecraft:armor.helmet", AttributeIds.BY_UUID.get(UUID.fromString("2AD3F246-FEE1-4E67-B886-69FD380BB150")));
        assertEquals("minecraft:random_spawn_bonus", AttributeIds.BY_NAME.get("Random spawn bonus"));
        assertEquals(5, AttributeIds.BY_NAME.size());
        assertEquals(40, AttributeIds.BY_UUID.size(), "표 크기가 바뀌면 바닐라 대조를 다시 한다");
    }
}
