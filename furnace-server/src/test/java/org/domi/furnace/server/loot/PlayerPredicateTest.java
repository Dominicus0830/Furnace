// A12-01·A12-11: player 술어의 looking_at·stats 와 모르는 필드 처리(바닐라 코덱은 모르는 키를 무시한다), is_flying 플래그.
package org.domi.furnace.server.loot;

import static org.junit.jupiter.api.Assertions.*;
import static org.domi.furnace.server.loot.PredicateFieldsTest.entity;

import com.google.gson.JsonParser;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlayerPredicateTest {

    @BeforeAll
    static void load() throws Exception { ItemFunctionsTest.load(); }

    private static LootEntity at(double x, String type) {
        return new LootEntity() {
            public String type() { return type; }
            public double[] position() { return new double[] {x, 0, 0}; }
        };
    }

    private static LootEntity player(Map<String, Object> specific) {
        return new LootEntity() {
            public String type() { return "minecraft:player"; }
            public double[] position() { return new double[] {0, 0, 0}; }
            public Object typeSpecific(String key) { return specific.get(key); }
        };
    }

    private static final String PARROT = "{\"type_specific\":{\"type\":\"minecraft:player\",\"looking_at\":{\"type\":\"minecraft:parrot\"}}}";

    @Test
    void lookingAtMatchesTheEntityTheLineEndsOn() {
        assertTrue(entity(PARROT, player(Map.of("level", 0, "looking_at", at(3, "minecraft:parrot")))));
        assertFalse(entity(PARROT, player(Map.of("level", 0, "looking_at", at(3, "minecraft:pig")))));
        assertFalse(entity(PARROT, player(Map.of("level", 0))), "아무것도 안 보면 거짓");
    }

    @Test
    void lookingAtSubPredicateIsMeasuredFromThePlayer() {
        String near = "{\"type_specific\":{\"type\":\"minecraft:player\",\"looking_at\":{\"distance\":{\"horizontal\":{\"max\":5}}}}}";
        assertTrue(entity(near, player(Map.of("level", 0, "looking_at", at(3, "minecraft:cow")))));
        assertFalse(entity(near, player(Map.of("level", 0, "looking_at", at(9, "minecraft:cow")))), "원점은 플레이어 위치");
    }

    @Test
    void statsCompareTheCounterToTheBounds() {
        String json = "{\"type_specific\":{\"type\":\"minecraft:player\",\"stats\":["
                + "{\"type\":\"minecraft:custom\",\"stat\":\"minecraft:jump\",\"value\":{\"min\":2,\"max\":5}},"
                + "{\"type\":\"minecraft:mined\",\"stat\":\"minecraft:stone\",\"value\":1}]}}";
        String jump = "stat:minecraft:custom|minecraft:jump", stone = "stat:minecraft:mined|minecraft:stone";
        assertTrue(entity(json, player(Map.of("level", 0, jump, 3, stone, 1))));
        assertFalse(entity(json, player(Map.of("level", 0, jump, 6, stone, 1))), "상한 초과");
        assertFalse(entity(json, player(Map.of("level", 0, jump, 3, stone, 2))), "정확히 1 이어야");
        assertFalse(entity(json, player(Map.of("level", 0, jump, 3))), "값을 못 읽으면 거짓");
    }

    @Test
    void statValueDefaultsToAny() {
        String json = "{\"type_specific\":{\"type\":\"minecraft:player\",\"stats\":[{\"type\":\"minecraft:custom\",\"stat\":\"jump\"}]}}";
        assertTrue(entity(json, player(Map.of("level", 0, "stat:minecraft:custom|minecraft:jump", 0))));
    }

    @Test
    void unknownStatTypeOrStatIsRejectedLikeTheCodec() {
        for (String s : new String[] {
                "{\"type\":\"minecraft:nothing\",\"stat\":\"minecraft:jump\"}",
                "{\"type\":\"minecraft:custom\",\"stat\":\"minecraft:no_such_stat\"}",
                "{\"type\":\"minecraft:mined\",\"stat\":\"minecraft:no_such_block\"}"}) {
            String json = "{\"type_specific\":{\"type\":\"minecraft:player\",\"stats\":[" + s + "]}}";
            assertThrows(LootParseException.class, () -> EntityPredicate.parse(JsonParser.parseString(json), ItemFunctionsTest.regs), s);
        }
    }

    @Test
    void unknownFieldsAreIgnoredLikeVanillaCodecs() {
        // 바닐라 RecordCodecBuilder 는 모르는 키를 무시한다 — 나머지 조건만으로 판단하고 조건 전체를 거짓으로 만들지 않는다
        assertTrue(entity("{\"type\":\"minecraft:pig\",\"no_such_field\":1}", at(0, "minecraft:pig")));
        assertFalse(entity("{\"type\":\"minecraft:pig\",\"no_such_field\":1}", at(0, "minecraft:cow")));
        assertTrue(entity("{\"flags\":{\"is_on_fire\":false,\"is_unknown\":true}}", at(0, "minecraft:pig")), "모르는 플래그도 무시");
        EntityPredicate loc = EntityPredicate.parse(JsonParser.parseString("{\"location\":{\"no_such_field\":1,\"position\":{\"x\":{\"max\":1}}}}"), ItemFunctionsTest.regs);
        assertTrue(loc.matches(PredicateFieldsTest.ctx(ItemFunctionsTest.WORLD), new double[] {0, 0, 0}, at(0, "minecraft:pig")));
        assertFalse(loc.matches(PredicateFieldsTest.ctx(ItemFunctionsTest.WORLD), new double[] {0, 0, 0}, at(5, "minecraft:pig")));
    }

    @Test
    void isFlyingFlagFollowsTheEntity() {
        LootEntity flying = new LootEntity() {
            public String type() { return "minecraft:player"; }
            public boolean isFlying() { return true; }
        };
        assertTrue(entity("{\"flags\":{\"is_flying\":true}}", flying));
        assertFalse(entity("{\"flags\":{\"is_flying\":false}}", flying));
        assertFalse(entity("{\"flags\":{\"is_flying\":true}}", at(0, "minecraft:player")));
    }
}
