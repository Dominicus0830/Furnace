package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.Entities;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.LivingEntity;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * A08-03 회귀. 바닐라 PowderSnowBlock.fallOn 은 4칸 이상 떨어졌을 때 소리만 내고 super(낙하 피해)를 부르지 않는다.
 * 플레이어 아닌 생물(Entity.checkFallDamage)도 가루눈 위에서는 낙하 피해가 없다.
 */
class A08PowderSnowFallTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    @Test
    void mobTakesNoFallDamageOnPowderSnow() {
        var level = new BlockPlacementTest.TestLevel();
        Pos snow = new Pos(5, -60, 5);
        int ps = Blocks.get().defaultState("minecraft:powder_snow");
        level.setBlock(snow, ps, 3);
        Entity cow = Entities.create(level, "minecraft:cow");
        cow.moveTo(5.5, -59.1, 5.5, 0, 0);
        level.addFreshEntity(cow);
        float before = ((LivingEntity) cow).health();
        BlockBehaviors.of(ps).fallOn(level, ps, snow, cow, 10.0F);   // Entity.checkFallDamage 가 착지 칸의 블록에 부르는 것
        assertEquals(before, ((LivingEntity) cow).health(), 0.0F, "바닐라: 가루눈에 떨어져도 낙하 피해 없음(소리만)");
    }

    @Test
    void controlDirtStillHurts() {
        var level = new BlockPlacementTest.TestLevel();
        Pos p = new Pos(5, -61, 5);
        int dirt = Blocks.get().defaultState("minecraft:dirt");
        Entity cow = Entities.create(level, "minecraft:cow");
        cow.moveTo(5.5, -60.0, 5.5, 0, 0);
        level.addFreshEntity(cow);
        float before = ((LivingEntity) cow).health();
        BlockBehaviors.of(dirt).fallOn(level, dirt, p, cow, 10.0F);
        assertTrue(((LivingEntity) cow).health() < before, "대조군: 흙 위 낙하는 피해");
    }
}
