// 몹 사망: 플레이어가 때려 죽이면 경험치 구슬과 전리품 표 결과를 떨구고, 환경 피해로 죽으면 떨구지 않는다. 구슬 값은 xpReward.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MobDeathLootTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    static final class Pig extends Mob {
        Pig(ChunkLevel level, int id) {
            super(EntityType.VILLAGER, level, id);
            xpReward = 5;
        }
        @Override protected void addAdditionalSaveData(NbtCompound tag) { super.addAdditionalSaveData(tag); }
    }

    static final class DropLevel extends BlockPlacementTest.TestLevel {
        @Override
        public List<ItemStack> entityDrops(String table, org.domi.furnace.server.loot.LootEntity self, double x, double y, double z, String damageType,
                Player attacker, Player lastPlayer, long seed) {
            return List.of(new ItemStack(ItemContext.id("minecraft:item", "bone"), 2));
        }
    }

    private static final Entity.Damage FALL = new Entity.Damage("fall", false, false, false);
    private static final Entity.Damage HIT = new Entity.Damage("player_attack", false, false, false);

    private static int orbTotal(ChunkLevel level) {
        return level.getEntitiesOfClass(ExperienceOrb.class, new AABB(-50, -80, -50, 50, 80, 50), o -> true).stream().mapToInt(o -> o.value() * o.count()).sum();
    }

    private static List<ItemEntity> items(ChunkLevel level) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(-50, -80, -50, 50, 80, 50), o -> true);
    }

    @Test
    void killedByPlayerDropsLootAndExperience() {
        var level = new DropLevel();
        var pig = new Pig(level, level.nextEntityId());
        pig.setPos(0.5, -60, 0.5);
        var player = new Player(7, UUID.randomUUID(), "Steve", new EmbeddedChannel());
        assertTrue(pig.hurtBy(HIT, 100f, player));
        assertEquals(5, orbTotal(level));
        assertEquals(1, items(level).size());
        assertEquals(2, items(level).get(0).getItem().count());
    }

    @Test
    void environmentalDeathGivesNoExperience() {
        var level = new DropLevel();
        var pig = new Pig(level, level.nextEntityId());
        pig.setPos(0.5, -60, 0.5);
        pig.hurt(FALL, 100f);
        assertEquals(0, orbTotal(level));
        assertEquals(1, items(level).size(), "전리품 표는 플레이어 없이도 나온다");
    }

    @Test
    void recentPlayerHitCountsForExperienceAfterLaterEnvironmentalKill() {
        var level = new DropLevel();
        var pig = new Pig(level, level.nextEntityId());
        pig.setPos(0.5, -60, 0.5);
        var player = new Player(7, UUID.randomUUID(), "Steve", new EmbeddedChannel());
        pig.hurtBy(HIT, 3f, player);
        pig.invulnerableTime = 0;
        pig.hurt(FALL, 100f);
        assertEquals(5, orbTotal(level), "100틱 안에 플레이어가 때렸다");
    }
}
