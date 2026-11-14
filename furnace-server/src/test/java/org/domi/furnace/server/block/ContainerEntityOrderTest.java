package org.domi.furnace.server.block;

// X1 검토 보강: 호퍼가 쓰는 getEntitiesOfType(ContainerEntity) 가 옛 방식(전체 조회 + 걸러내기)과 같은 순서·같은 결과인지, 공기 충돌 모양이 비었는지.
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.*;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ContainerEntityOrderTest {
    @BeforeAll
    static void install() { ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt"))); }

    @Test
    void containerTypeQueryEqualsFilteredFullQuery() {
        var level = new BlockPlacementTest.TestLevel();
        Random rnd = new Random(9);
        List<Entity> all = new ArrayList<>();
        ItemStack stone = B.stack("minecraft:stone", 1);
        long nonEmpty = 0;
        for (int i = 0; i < 300; i++) {
            Entity e;
            switch (rnd.nextInt(3)) {
                case 0 -> e = new MinecartChest(level, level.nextEntityId());
                case 1 -> e = new ChestBoat(level, level.nextEntityId());
                default -> e = new ItemEntity(level, level.nextEntityId(), 0, 0, 0, stone, 0, 0, 0);
            }
            e.moveTo(rnd.nextDouble() * 40 - 20, rnd.nextDouble() * 40 - 20, rnd.nextDouble() * 40 - 20, 0, 0);
            assertTrue(level.addFreshEntity(e));
            all.add(e);
            if (i == 10) { // 목록을 일찍 만들어 둔다(웜 경로)
                level.getEntitiesOfType(ContainerEntity.class, new AABB(-30, -30, -30, 30, 30, 30), Entity::isAlive);
            }
        }
        for (int round = 0; round < 40; round++) {
            for (int k = 0; k < 40; k++) {
                Entity e = all.get(rnd.nextInt(all.size()));
                if (e.isRemoved()) continue;
                if (rnd.nextInt(5) == 0) e.discard();
                else e.moveTo(rnd.nextDouble() * 40 - 20, rnd.nextDouble() * 40 - 20, rnd.nextDouble() * 40 - 20, 0, 0);
            }
            for (int q = 0; q < 20; q++) {
                double x = rnd.nextDouble() * 40 - 20, y = rnd.nextDouble() * 40 - 20, z = rnd.nextDouble() * 40 - 20;
                AABB box = new AABB(x - 0.5, y - 0.5, z - 0.5, x + 0.5 + rnd.nextInt(12), y + 0.5 + rnd.nextInt(12), z + 0.5 + rnd.nextInt(12));
                var expected = level.getEntities(null, box, e -> e instanceof ContainerEntity && e.isAlive());
                var got = level.getEntitiesOfType(ContainerEntity.class, box, Entity::isAlive);
                assertEquals(expected, got, "round " + round + " q " + q);
                if (!expected.isEmpty()) nonEmpty++;
            }
        }
        assertTrue(nonEmpty > 100, "nonEmpty=" + nonEmpty);
    }

    @Test
    void airCollisionShapeIsEmpty() {
        var level = new BlockPlacementTest.TestLevel();
        int air = B.defaultState("air");
        assertTrue(BlockBehaviors.of(air).getCollisionShape(air, level, new Pos(0, 0, 0), CollisionContext.EMPTY).isEmpty());
        var cow = new Cow(level, level.nextEntityId());
        assertTrue(BlockBehaviors.of(air).getCollisionShape(air, level, new Pos(0, 0, 0), org.domi.furnace.server.block.CollisionContext.of(cow)).isEmpty());
    }
}
