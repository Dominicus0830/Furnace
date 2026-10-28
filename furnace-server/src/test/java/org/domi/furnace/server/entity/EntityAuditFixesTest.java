// 엔티티 감사 소규모 불일치 회귀: 평화로움 제거 규칙(종류별), 속성값, 양 전리품 표, 고양이 눈높이, 스폰 배치 표, 번개 반응, create 누락.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.entity.spawn.SpawnPlacements;
import org.domi.furnace.server.item.ItemContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EntityAuditFixesTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    static final class PeacefulLevel extends BlockPlacementTest.TestLevel {
        @Override public int difficulty() { return 0; }
    }

    /** 바닐라 shouldDespawnInPeaceful: Monster·Ghast·Phantom·Slime(크기>0) 참, Piglin 거짓, EnderDragon.checkDespawn 비어 있음. */
    @Test
    void peacefulDespawnPerType() {
        Map<String, Boolean> expected = new java.util.LinkedHashMap<>();
        expected.put("minecraft:zombie", true);
        expected.put("minecraft:creeper", true);
        expected.put("minecraft:piglin_brute", true);
        expected.put("minecraft:zoglin", true);
        expected.put("minecraft:wither", true);
        expected.put("minecraft:ghast", true);
        expected.put("minecraft:phantom", true);
        expected.put("minecraft:slime", true);
        expected.put("minecraft:magma_cube", true);
        expected.put("minecraft:piglin", false);
        expected.put("minecraft:hoglin", false);
        expected.put("minecraft:shulker", false);
        expected.put("minecraft:ender_dragon", false);
        expected.put("minecraft:cow", false);
        expected.put("minecraft:iron_golem", false);
        for (var en : expected.entrySet()) {
            var level = new PeacefulLevel();
            Mob m = (Mob) Entities.create(level, en.getKey());
            assertNotNull(m, en.getKey());
            m.setPos(0.5, -60, 0.5);
            m.checkDespawn();
            assertEquals(en.getValue(), m.isRemoved(), en.getKey());
        }
    }

    @Test
    void slimesAreEnemies() {
        var level = new BlockPlacementTest.TestLevel();
        assertTrue(Entities.create(level, "minecraft:slime") instanceof Enemy);
        assertTrue(Entities.create(level, "minecraft:magma_cube") instanceof Enemy);
    }

    @Test
    void vindicatorAttributes() {
        var v = (LivingEntity) Entities.create(new BlockPlacementTest.TestLevel(), "minecraft:vindicator");
        assertEquals(24.0, v.attributes().get(Attribute.MAX_HEALTH).baseValue());
        assertEquals(12.0, v.attributes().get(Attribute.FOLLOW_RANGE).baseValue());
    }

    @Test
    void rabbitAttributesAndKillerBunny() {
        var r = (Rabbit) Entities.create(new BlockPlacementTest.TestLevel(), "minecraft:rabbit");
        assertEquals(0.0, r.attributes().get(Attribute.ARMOR).value());
        assertEquals(3.0, r.attributes().get(Attribute.ATTACK_DAMAGE).value());
        r.setVariant(99);
        assertEquals(8.0, r.attributes().get(Attribute.ARMOR).value());
        assertEquals(8.0, r.attributes().get(Attribute.ATTACK_DAMAGE).value());
        r.setVariant(0);
        assertEquals(3.0, r.attributes().get(Attribute.ATTACK_DAMAGE).value());
    }

    @Test
    void shearedSheepUsesDefaultTable() {
        var s = (Sheep) Entities.create(new BlockPlacementTest.TestLevel(), "minecraft:sheep");
        assertEquals("minecraft:entities/sheep/white", s.lootTable());
        s.setSheared(true);
        assertEquals("minecraft:entities/sheep", s.lootTable());
    }

    @Test
    void catEyeHeight() {
        assertEquals(0.35F, EntityType.CAT.eyeHeight());
    }

    @Test
    void beeHasNoSpawnPlacement() {
        assertEquals(SpawnPlacements.Type.NO_RESTRICTIONS, SpawnPlacements.typeOf("minecraft:bee"));
    }

    @Test
    void createsItemAndOrb() {
        var level = new BlockPlacementTest.TestLevel();
        assertTrue(Entities.create(level, "minecraft:item") instanceof ItemEntity);
        assertTrue(Entities.create(level, "minecraft:experience_orb") instanceof ExperienceOrb);
    }

    @Test
    void lightningReactions() {
        var level = new BlockPlacementTest.TestLevel();
        var bolt = new LightningBolt(level, level.nextEntityId());
        var cow = (Mooshroom) Entities.create(level, "minecraft:mooshroom");
        cow.thunderHit(level, bolt);
        assertEquals("brown", cow.mushroomType());
        cow.thunderHit(level, bolt);   // 같은 번개로는 다시 바뀌지 않는다
        assertEquals("brown", cow.mushroomType());
        cow.thunderHit(level, new LightningBolt(level, level.nextEntityId()));
        assertEquals("red", cow.mushroomType());

        var turtle = (Turtle) Entities.create(level, "minecraft:turtle");
        turtle.setPos(0.5, -60, 0.5);
        turtle.thunderHit(level, bolt);
        assertTrue(turtle.isDeadOrDying());

        var stand = (ArmorStand) Entities.create(level, "minecraft:armor_stand");
        stand.setPos(2.5, -60, 0.5);
        stand.thunderHit(level, bolt);
        assertFalse(stand.isRemoved());
        assertFalse(stand.isOnFire());
        assertEquals(stand.maxHealth(), stand.health());
    }
}
