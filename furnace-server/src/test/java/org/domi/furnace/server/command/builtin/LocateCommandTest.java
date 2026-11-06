// /locate structure: 이름 해석(id·태그·없는 것)과 거리 계산(바닐라 int 곱·float 제곱근·내림).
package org.domi.furnace.server.command.builtin;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.domi.furnace.server.command.arguments.ResourceOrTagKeyArgument;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.data.ResourceId;
import org.junit.jupiter.api.Test;

class LocateCommandTest {

    @Test
    void resolve() throws Exception {
        var arg = ResourceOrTagKeyArgument.resourceOrTagKey("minecraft:worldgen/structure");
        var id = arg.parse(new StringReader("swamp_hut"));
        assertFalse(id.tag());
        assertEquals(List.of("minecraft:swamp_hut"), LocateCommand.resolve(id));
        var tag = arg.parse(new StringReader("#minecraft:mineshaft"));
        assertTrue(tag.tag());
        assertEquals("#minecraft:mineshaft", tag.printable());
        assertEquals(List.of("minecraft:mineshaft", "minecraft:mineshaft_mesa"), LocateCommand.resolve(tag));
        assertNull(LocateCommand.resolve(new ResourceOrTagKeyArgument.Result(ResourceId.parse("minecraft:nope"), false)));
        assertNull(LocateCommand.resolve(new ResourceOrTagKeyArgument.Result(ResourceId.parse("minecraft:nope"), true)));
    }

    @Test
    void distance() {
        assertEquals(5, LocateCommand.dist(0, 0, 3, 4));
        assertEquals(1414, LocateCommand.dist(0, 0, 1000, -1000));
        assertEquals(0, LocateCommand.dist(7, 7, 7, 7));
    }

    @Test
    void biomeAndPoiNames() {
        var id = new ResourceOrTagKeyArgument.Result(ResourceId.parse("minecraft:plains"), false);
        var tag = new ResourceOrTagKeyArgument.Result(ResourceId.parse("minecraft:village"), true);
        assertEquals("minecraft:plains", LocateCommand.displayName(id, "minecraft:plains"));
        assertEquals("#minecraft:village (minecraft:home)", LocateCommand.displayName(tag, "minecraft:home"));
        assertTrue(LocateCommand.biomePredicate(id, null).test("minecraft:plains"));
        assertFalse(LocateCommand.biomePredicate(id, null).test("minecraft:desert"));
        var p = LocateCommand.poiPredicate(tag, null);
        assertTrue(p.test(org.domi.furnace.server.world.poi.PoiTypes.HOME));
        assertFalse(p.test(org.domi.furnace.server.world.poi.PoiTypes.LODESTONE));
        assertTrue(LocateCommand.poiPredicate(tag, List.of("minecraft:lodestone")).test(org.domi.furnace.server.world.poi.PoiTypes.LODESTONE));
    }

    @Test
    void distance3d() {
        assertEquals(13, LocateCommand.dist3(0, 0, 0, 3, 4, 12));
        assertEquals(0, LocateCommand.dist3(1, 2, 3, 1, 2, 3));
        // float 반올림 경계: distSqr = 90001²-1 은 float 로 90001² 가 되어 바닐라는 90001(정확한 값은 90000.99…)
        assertEquals(90001, LocateCommand.dist3(0, 0, 0, 90000, 300, 300));
    }

    @Test
    void registriesKnowBiomesAndPoi() {
        var set = org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt");
        var tags = new org.domi.furnace.server.data.TagRegistries(set);
        // 파싱 검증(ResourceOrTagKeyArgument)이 POI id 를 레지스트리로 거를 수 있다
        var poi = tags.names(LocateCommand.POI_REGISTRY).stream().map(Object::toString).toList();
        for (var t : org.domi.furnace.server.world.poi.PoiTypes.all()) assertTrue(poi.contains(t.name()), t.name());
        assertFalse(poi.contains("minecraft:nope"));
        // 평지 biomeId(FurnaceMain: RegistrySet.indexOf)와 명령이 쓰는 이름 목록(TagRegistries.names) 순서가 같다
        var biomes = tags.names(LocateCommand.BIOME_REGISTRY).stream().map(Object::toString).toList();
        assertFalse(biomes.isEmpty());
        for (int i = 0; i < biomes.size(); i++) assertEquals(i, set.indexOf(LocateCommand.BIOME_REGISTRY, biomes.get(i)), biomes.get(i));
    }
}
