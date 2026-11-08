// 작은 바닐라 차이 정리: 몹 장비 인챈트 속성(화염 보호), 스폰 알로 새끼 낳기(SpawnEggItem.spawnOffspringFromSpawnEgg), 변환 때 아기 유지.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.UUID;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SmallParityGapsTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static ItemStack item(String name) { return new ItemStack(ItemContext.id("minecraft:item", name), 1); }

    private static Player player(int id, GameMode mode) {
        var p = new Player(id, UUID.randomUUID(), "parity-" + id, new ConcurrentEmbeddedChannel());
        p.teleporter(t -> {});
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, mode, false);
        return p;
    }

    private static long babies(BlockPlacementTest.TestLevel level, Class<? extends Mob> type) {
        return level.entities().tickSnapshot().stream().filter(e -> type.isInstance(e) && ((Mob) e).isBaby()).count();
    }

    // ------------------------------------------------------------------ 몹 장비 인챈트 속성

    @Test
    void mobArmorFireProtectionShortensBurning() {
        var level = new BlockPlacementTest.TestLevel();
        var z = new Zombie(level, level.nextEntityId());
        var helmet = ItemTools.with(item("minecraft:iron_helmet"), "minecraft:enchantments", Map.of("levels", Map.of("minecraft:fire_protection", 4)));
        z.setItemSlot(EquipmentSlot.HEAD, helmet);
        z.detectEquipmentUpdates();
        // 화염 보호 IV: add_multiplied_base −0.15 − 0.15 × 3 = −0.6 → 0.4 배
        assertEquals(0.4, z.attributeValue(Attribute.BURNING_TIME), 1e-6);
        z.igniteForSeconds(10.0F);
        assertEquals(80, z.remainingFireTicks(), "ceil(200 × 0.4)");
        z.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        z.detectEquipmentUpdates();
        assertEquals(1.0, z.attributeValue(Attribute.BURNING_TIME), 1e-6, "벗으면 수정자가 떨어진다");
    }

    @Test
    void blastProtectionAddsExplosionKnockbackResistanceToMobArmor() {
        var level = new BlockPlacementTest.TestLevel();
        var z = new Zombie(level, level.nextEntityId());
        var chest = ItemTools.with(item("minecraft:iron_chestplate"), "minecraft:enchantments", Map.of("levels", Map.of("minecraft:blast_protection", 2)));
        z.setItemSlot(EquipmentSlot.CHEST, chest);
        z.detectEquipmentUpdates();
        assertEquals(0.3, z.attributeValue(Attribute.EXPLOSION_KNOCKBACK_RESISTANCE), 1e-6, "linear(0.15, +0.15) 레벨 2 = 0.30");
    }

    // ------------------------------------------------------------------ 스폰 알 새끼

    private static Mob put(BlockPlacementTest.TestLevel level, Mob m, double x) {
        m.moveTo(x, -60, 0.5, 0, 0);
        level.addFreshEntity(m);
        return m;
    }

    @Test
    void spawnEggOnSameKindMakesBabyAndConsumesEgg() {
        var level = new BlockPlacementTest.TestLevel();
        var cow = (Cow) put(level, new Cow(level, level.nextEntityId()), 3.5);
        var p = player(1, GameMode.SURVIVAL);
        var egg = new ItemStack(ItemContext.id("minecraft:item", "minecraft:cow_spawn_egg"), 2);
        p.inventory().set(p.inventory().heldWindowSlot(), egg);
        assertTrue(ItemEffects.spawnEggOffspring(p, 0, egg, cow));
        assertEquals(1, babies(level, Cow.class), "같은 종류의 새끼가 하나 생겼다");
        Cow baby = (Cow) level.entities().tickSnapshot().stream().filter(e -> e instanceof Cow c && c.isBaby()).findFirst().orElseThrow();
        assertEquals(3.5, baby.x(), 1e-9);
        assertEquals(-60.0, baby.y(), 1e-9);
        assertEquals(1, p.inventory().get(p.inventory().heldWindowSlot()).count(), "알 하나 소비");
        assertFalse(cow.isBaby(), "어미는 그대로");
    }

    @Test
    void spawnEggCreativeKeepsEgg() {
        var level = new BlockPlacementTest.TestLevel();
        var pig = (Pig) put(level, new Pig(level, level.nextEntityId()), 3.5);
        var p = player(2, GameMode.CREATIVE);
        var egg = item("minecraft:pig_spawn_egg");
        p.inventory().set(p.inventory().heldWindowSlot(), egg);
        assertTrue(ItemEffects.spawnEggOffspring(p, 0, egg, pig));
        assertEquals(1, babies(level, Pig.class));
        assertEquals(1, p.inventory().get(p.inventory().heldWindowSlot()).count(), "크리에이티브는 줄지 않는다");
    }

    @Test
    void spawnEggOfOtherKindOrNonBabyKindDoesNothing() {
        var level = new BlockPlacementTest.TestLevel();
        var pig = (Pig) put(level, new Pig(level, level.nextEntityId()), 3.5);
        var creeper = (Creeper) put(level, new Creeper(level, level.nextEntityId()), 6.5);
        var p = player(3, GameMode.SURVIVAL);
        var cowEgg = item("minecraft:cow_spawn_egg");
        p.inventory().set(p.inventory().heldWindowSlot(), cowEgg);
        assertFalse(ItemEffects.spawnEggOffspring(p, 0, cowEgg, pig), "종류가 다르면 넘긴다");
        var creeperEgg = item("minecraft:creeper_spawn_egg");
        p.inventory().set(p.inventory().heldWindowSlot(), creeperEgg);
        assertFalse(ItemEffects.spawnEggOffspring(p, 0, creeperEgg, creeper), "아기가 될 수 없는 종류는 아무것도 안 한다");
        assertFalse(ItemEffects.spawnEggOffspring(p, 0, item("minecraft:apple"), pig));
        assertEquals(0, babies(level, Mob.class));
        assertEquals(1, p.inventory().get(p.inventory().heldWindowSlot()).count(), "알은 그대로");
        assertEquals(2, level.entities().tickSnapshot().size(), "몹은 늘지 않았다");
    }

    @Test
    void spawnEggOnZombieMakesBabyZombie() {
        var level = new BlockPlacementTest.TestLevel();
        var zombie = (Zombie) put(level, new Zombie(level, level.nextEntityId()), 3.5);
        var p = player(4, GameMode.SURVIVAL);
        var egg = item("minecraft:zombie_spawn_egg");
        p.inventory().set(p.inventory().heldWindowSlot(), egg);
        assertTrue(ItemEffects.spawnEggOffspring(p, 0, egg, zombie));
        assertEquals(1, babies(level, Zombie.class), "AgeableMob 이 아니어도 setBaby 가 되는 종류는 새끼");
    }

    @Test
    void spawnEggEntityDataIdOverridesKindAndNameCarries() {
        var level = new BlockPlacementTest.TestLevel();
        var cow = (Cow) put(level, new Cow(level, level.nextEntityId()), 3.5);
        var p = player(5, GameMode.SURVIVAL);
        var data = new NbtCompound();
        data.putString("id", "minecraft:cow");
        var egg = ItemTools.with(item("minecraft:pig_spawn_egg"), "minecraft:entity_data", data);
        egg = ItemTools.with(egg, "minecraft:custom_name", "{\"text\":\"Bessie\"}");
        p.inventory().set(p.inventory().heldWindowSlot(), egg);
        assertTrue(ItemEffects.spawnEggOffspring(p, 0, egg, cow), "entity_data 의 id 가 알의 종류");
        Cow baby = (Cow) level.entities().tickSnapshot().stream().filter(e -> e instanceof Cow c && c.isBaby()).findFirst().orElseThrow();
        assertTrue(baby.hasCustomName(), "알의 이름이 새끼 이름");
    }

    @Test
    void spawnEggOnFoxBabyTrustsPlayer() {
        var level = new BlockPlacementTest.TestLevel();
        var fox = (Fox) put(level, new Fox(level, level.nextEntityId()), 3.5);
        var p = player(6, GameMode.SURVIVAL);
        var egg = item("minecraft:fox_spawn_egg");
        p.inventory().set(p.inventory().heldWindowSlot(), egg);
        assertTrue(ItemEffects.spawnEggOffspring(p, 0, egg, fox));
        Fox baby = (Fox) level.entities().tickSnapshot().stream().filter(e -> e instanceof Fox f && f.isBaby()).findFirst().orElseThrow();
        assertTrue(baby.trusts(p.uuid()));
    }

    // ------------------------------------------------------------------ 변환 때 아기 유지(Mob.convertTo → target.setBaby(isBaby))

    @Test
    void babyHoglinConvertsToBabyZoglin() {
        var level = new BlockPlacementTest.TestLevel();
        var hoglin = (Hoglin) put(level, new Hoglin(level, level.nextEntityId()), 3.5);
        hoglin.setBaby(true);
        var zoglin = hoglin.convertTo(new Zoglin(level, level.nextEntityId()), true, "PIGLIN_ZOMBIFIED");
        assertTrue(zoglin.isBaby());
        assertEquals(zoglin.type().height() * 0.5F, zoglin.bbHeight(), 1e-4, "아기 조글린 상자는 0.5 배");
    }

    @Test
    void babyZombieVillagerCuresToBabyVillager() {
        var level = new BlockPlacementTest.TestLevel();
        var zv = (ZombieVillager) put(level, new ZombieVillager(level, level.nextEntityId()), 3.5);
        zv.setBaby(true);
        var villager = zv.convertTo(new Villager(level, level.nextEntityId()), false, "CURED");
        assertTrue(villager.isBaby());
    }
}
