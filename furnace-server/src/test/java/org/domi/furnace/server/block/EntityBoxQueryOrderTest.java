// X1: 엔티티 상자 조회(forEachIn — 구역 순서 배열을 0 을 가로지를 때만 만든다)와 클래스별 조회가 바닐라 순서(구역 x → z·y 마스크 순 → 넣은 순)로 같은 답을 내는지.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EntityBoxQueryOrderTest {

    @BeforeAll
    static void install() { ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt"))); }

    private record Keyed(Entity e, int sx, int sy, int sz, int seq) {}

    @Test
    void boxQueryKeepsVanillaSectionOrderAcrossZeroCrossings() {
        var level = new BlockPlacementTest.TestLevel();
        Random rnd = new Random(5);
        List<Keyed> all = new ArrayList<>();
        ItemStack stone = B.stack("minecraft:stone", 1);
        for (int i = 0; i < 400; i++) {
            double x = rnd.nextDouble() * 60 - 30, z = rnd.nextDouble() * 60 - 30, y = rnd.nextDouble() * 50 - 62;
            ItemEntity e = new ItemEntity(level, level.nextEntityId(), x, y, z, stone, 0, 0, 0);
            assertTrue(level.addFreshEntity(e));
            all.add(new Keyed(e, Entity.floor(e.x()) >> 4, Entity.floor(e.y()) >> 4, Entity.floor(e.z()) >> 4, i));
        }
        for (int q = 0; q < 300; q++) {
            double cx = rnd.nextDouble() * 60 - 30, cz = rnd.nextDouble() * 60 - 30, cy = rnd.nextDouble() * 50 - 62;
            double hx = 1 + rnd.nextDouble() * 20, hz = 1 + rnd.nextDouble() * 20, hy = 1 + rnd.nextDouble() * 15;
            AABB box = new AABB(cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz);
            int x0 = Entity.floor(box.minX() - 2.0) >> 4, y0 = Entity.floor(box.minY() - 4.0) >> 4, z0 = Entity.floor(box.minZ() - 2.0) >> 4;
            int x1 = Entity.floor(box.maxX() + 2.0) >> 4, y1 = Entity.floor(box.maxY()) >> 4, z1 = Entity.floor(box.maxZ() + 2.0) >> 4;
            List<Entity> expected = all.stream()
                    .filter(k -> k.sx >= x0 && k.sx <= x1 && k.sy >= y0 && k.sy <= y1 && k.sz >= z0 && k.sz <= z1 && k.e.boundingBox().intersects(box))
                    .sorted(Comparator.<Keyed>comparingInt(k -> k.sx).thenComparingInt(k -> k.sz & 4194303).thenComparingInt(k -> k.sy & 1048575).thenComparingInt(k -> k.seq))
                    .map(Keyed::e).toList();
            assertEquals(expected, level.getEntities(null, box, t -> true), "상자 " + q);
            assertEquals(expected, level.getEntitiesOfType(Entity.class, box, t -> true), "종류별 조회 " + q);
            assertEquals(expected, level.getEntitiesOfClass(ItemEntity.class, box, t -> true), "클래스별 조회 " + q);
            assertEquals(List.of(), level.getEntitiesOfType(org.domi.furnace.server.entity.ContainerEntity.class, box, Entity::isAlive), "컨테이너 엔티티 없음");
        }
    }
}
