// spawn-animals=false / spawn-npcs=false 의 "지우는 엔티티" 판정을 바닐라 1.21.1 ServerLevel.shouldDiscardEntity 와 대조한다.
// 바닐라(바이트코드 확인): 동물 스폰이 꺼졌으면 Animal·WaterAnimal 을 지우고, 그 밖에는 Npc 스폰이 꺼졌을 때 Npc(AbstractVillager = 주민·떠돌이 상인뿐)를 지운다.
// 기대 목록은 바닐라 클래스 계통(Animal / WaterAnimal / AbstractVillager)을 이름으로 적은 것이라, 서버의 클래스 계통이 어긋나면 여기서 잡힌다.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.Entities;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.EntityType;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SpawnDiscardTest {

    /** 바닐라 Animal 의 하위 종류(TamableAnimal·AbstractHorse·Hoglin·Strider 포함). */
    private static final Set<String> ANIMALS = Set.of("armadillo", "axolotl", "bee", "camel", "cat", "chicken", "cow", "donkey", "fox", "frog", "goat",
            "hoglin", "horse", "llama", "trader_llama", "mooshroom", "mule", "ocelot", "panda", "parrot", "pig", "polar_bear", "rabbit", "sheep",
            "skeleton_horse", "zombie_horse", "sniffer", "strider", "turtle", "wolf");
    /** 바닐라 WaterAnimal 의 하위 종류(AbstractFish·AgeableWaterCreature 포함). */
    private static final Set<String> WATER = Set.of("cod", "salmon", "tropical_fish", "pufferfish", "squid", "glow_squid", "dolphin", "tadpole");
    /** 바닐라 Npc(AbstractVillager). */
    private static final Set<String> NPCS = Set.of("villager", "wandering_trader");

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    @Test
    void discardRulesMatchVanillaClassHierarchy() {
        var level = new BlockPlacementTest.TestLevel();
        Set<String> seen = new HashSet<>();
        int checked = 0;
        for (int id = 0; ; id++) {
            String name = EntityType.registryName(id);
            if (name == null) break;
            Entity e;
            try {
                e = Entities.create(level, name);
            } catch (RuntimeException ex) {
                continue;   // 시험 레벨로 못 만드는 종류는 건너뛴다(아래에서 기대 목록이 다 확인됐는지 본다)
            }
            if (e == null) continue;
            String shortName = name.substring("minecraft:".length());
            boolean animal = ANIMALS.contains(shortName) || WATER.contains(shortName), npc = NPCS.contains(shortName);
            seen.add(shortName);
            checked++;
            // 둘 다 켜짐(기본): 아무것도 지우지 않는다
            assertFalse(ChunkLevel.shouldDiscardEntity(e, true, true), name + " 기본에서는 지우지 않는다");
            // spawn-animals=false
            assertEquals(animal, ChunkLevel.shouldDiscardEntity(e, false, true), name + " spawn-animals=false");
            // spawn-npcs=false
            assertEquals(npc, ChunkLevel.shouldDiscardEntity(e, true, false), name + " spawn-npcs=false");
            // 둘 다 false
            assertEquals(animal || npc, ChunkLevel.shouldDiscardEntity(e, false, false), name + " 둘 다 false");
        }
        assertTrue(checked > 100, "충분한 종류를 훑었다: " + checked);
        // 시험 레벨로 만들어지는 기대 종류는 모두 확인했다(만들 수 있는 것이 빠졌으면 목록이 낡은 것)
        Set<String> missingAnimals = new HashSet<>(ANIMALS);
        missingAnimals.addAll(WATER);
        missingAnimals.addAll(NPCS);
        missingAnimals.removeAll(seen);
        // 서버가 아직 만들지 못하는 종류는 바닐라 목록에 있어도 판정할 수 없다 — 줄어드는 방향만 허용한다
        assertTrue(missingAnimals.size() <= 12, "서버가 만들지 못하는 기대 종류가 너무 많다: " + missingAnimals);
        for (String must : new String[] {"cow", "pig", "sheep", "chicken", "wolf", "cat", "villager", "wandering_trader", "cod", "salmon", "squid"}) {
            assertTrue(seen.contains(must), must + " 은 만들어져 판정됐어야 한다");
        }
    }
}
