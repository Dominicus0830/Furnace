// 동물: 둘 다 사랑 모드면 붙어서 새끼를 낳고(부모 나이 6000), 새끼는 아기이며 시간이 가면 자란다. 아기 나이는 저장된다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

class AnimalBreedingTest {

    @org.junit.jupiter.api.BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static void run(BlockPlacementTest.TestLevel level, Cow... cows) {
        for (Cow c : cows) {
            c.setOldPosAndRot();
            c.tickCount++;
            c.tick();
        }
    }

    @Test
    void twoLovingCowsMakeABaby() {
        var level = new BlockPlacementTest.TestLevel();
        var a = new Cow(level, level.nextEntityId());
        var b = new Cow(level, level.nextEntityId());
        a.moveTo(0.5, -60, 0.5, 0, 0);
        b.moveTo(2.5, -60, 0.5, 0, 0);
        a.setOnGround(true);
        b.setOnGround(true);
        level.addFreshEntity(a);
        level.addFreshEntity(b);
        a.setInLove(null);
        b.setInLove(null);
        for (int i = 0; i < 400 && a.getAge() == 0; i++) run(level, a, b);
        assertTrue(a.getAge() > 5990 && a.getAge() <= 6000, "부모는 쿨다운: " + a.getAge());
        assertTrue(b.getAge() > 5990);
        assertFalse(a.isInLove());
        long babies = level.getEntitiesOfClass(Cow.class, a.boundingBox().inflate(32), e -> e.isBaby()).size();
        assertEquals(1, babies);
    }

    @Test
    void babyAgeIsSavedAndGrowsUp() {
        var level = new BlockPlacementTest.TestLevel();
        var c = new Cow(level, level.nextEntityId());
        c.setBaby(true);
        assertTrue(c.isBaby());
        NbtCompound tag = c.save();
        assertEquals(-24000, tag.getInt("Age"));
        c.setAge(-2);
        run(level, c);
        run(level, c);
        assertFalse(c.isBaby());
    }

    @Test
    void sheepOffspringMixesDyes() {
        var level = new BlockPlacementTest.TestLevel();
        var a = new Sheep(level, level.nextEntityId());
        var b = new Sheep(level, level.nextEntityId());
        a.setColor(DyeColor.BLUE);
        b.setColor(DyeColor.RED);
        assertEquals(DyeColor.PURPLE, ((Sheep) a.getBreedOffspring(level, b)).getColor());
        b.setColor(DyeColor.BLUE);
        assertEquals(DyeColor.BLUE, ((Sheep) a.getBreedOffspring(level, b)).getColor());
    }

    @Test
    void shearingDropsWoolOnce() {
        var level = new BlockPlacementTest.TestLevel();
        var s = new Sheep(level, level.nextEntityId());
        s.moveTo(0.5, -60, 0.5, 0, 0);
        s.setColor(DyeColor.GREEN);
        assertTrue(s.readyForShearing());
        s.shear("players");
        assertTrue(s.isSheared());
        assertFalse(s.readyForShearing());
    }

    @Test
    void allAnimalsSaveAndLoad() {
        var level = new BlockPlacementTest.TestLevel();
        for (String id : new String[] {"minecraft:chicken", "minecraft:cow", "minecraft:pig", "minecraft:sheep"}) {
            Entity e = Entities.create(level, id);
            assertNotNull(e, id);
            e.moveTo(0.5, -60, 0.5, 0, 0);
            NbtCompound tag = e.save();
            Entity back = Entities.load(level, tag);
            assertNotNull(back, id);
            assertEquals(id, back.type().name());
        }
    }
}
