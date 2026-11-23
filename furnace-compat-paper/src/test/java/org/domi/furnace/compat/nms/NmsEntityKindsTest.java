package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.vehicle.ChestBoat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.compat.CompatTestPlugin;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * T-232: 엔티티 객체가 그 종류의 바닐라 클래스인가. 정답은 바닐라에서 뽑은 종류 표({@code src/shimspec/kind-classes-1.21.1.txt} 의 E 줄 — {@code EntityType}
 * 상수의 형식 인자, 레벨 없이 만들어지는 46종은 만들어 본 클래스로 대조)이고, 상위 사슬은 잠금 파일이 정한다 — 객체 클래스가 같으면 {@code instanceof} 가 바닐라와 같다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsEntityKindsTest {

    FurnaceTestServer server;
    TestPlayer tp;
    ServerLevel level;
    CraftWorld world;
    BlockPos at;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).exposeCompatToNative(true).plugin(CompatTestPlugin.class).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Kinds");
        server.tick(3);
        var loc = tp.player().location();
        at = new BlockPos((int) Math.floor(loc.x()) + 3, (int) Math.floor(loc.y()) + 1, (int) Math.floor(loc.z()) + 3);
        world = (CraftWorld) Compat.player(tp.player()).getWorld();
        level = world.getHandle();
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T on(java.util.function.Supplier<T> task) { return server.onRegion(at, task); }

    private static List<String[]> entityKinds() throws Exception {
        List<String[]> out = new ArrayList<>();
        for (String l : Files.readAllLines(Path.of("src/shimspec/kind-classes-1.21.1.txt"), StandardCharsets.UTF_8)) {
            if (l.startsWith("E ")) out.add(l.split(" "));
        }
        return out;
    }

    @Test
    void everyEntityTypeCreatesItsVanillaClass() throws Exception {
        List<String[]> kinds = entityKinds();
        assertEquals(129, kinds.size(), "1.21.1 엔티티 종류 130개 중 플레이어만 빠진다");
        List<String> wrong = new ArrayList<>();
        for (String[] k : kinds) {
            Entity e = EntityType.byString(k[1]).orElseThrow().create(level);
            if (k[1].equals("minecraft:fishing_bobber")) {   // 바닐라 공장 createNothing: create(level) 은 null(낚시찌는 낚싯대가 만든다)
                if (e != null) wrong.add(k[1] + " -> " + e.getClass().getName() + " (vanilla create gives null)");
                continue;
            }
            if (e == null || !e.getClass().getName().equals(k[2])) wrong.add(k[1] + " -> " + (e == null ? "null" : e.getClass().getName()) + " (vanilla " + k[2] + ")");
        }
        assertEquals(List.of(), wrong);
        assertNull(EntityType.PLAYER.create(level), "바닐라도 플레이어 종류의 공장은 null");
    }

    @Test
    void chainClassesAreAbstractLikeVanilla() {
        for (Class<?> c : List.of(Entity.class, LivingEntity.class, Mob.class, Monster.class, HangingEntity.class)) {
            assertTrue(Modifier.isAbstract(c.getModifiers()), c + " 는 바닐라에서 추상");
        }
        assertFalse(Modifier.isAbstract(Zombie.class.getModifiers()));
    }

    @Test
    void worldEntitiesHaveTheirVanillaClass() {
        // Axiom 이 가르는 세 종류(Marker·HangingEntity·ItemFrame)와 몹 사슬
        on(() -> {
            CraftEntity zombie = (CraftEntity) world.spawnEntity(new Location(world, at.x() + 0.5, at.y(), at.z() + 0.5), org.bukkit.entity.EntityType.ZOMBIE);
            CraftEntity marker = (CraftEntity) world.spawnEntity(new Location(world, at.x() + 0.5, at.y(), at.z() + 0.5), org.bukkit.entity.EntityType.MARKER);
            try {
                Entity z = zombie.getHandle();
                assertInstanceOf(Zombie.class, z);
                assertInstanceOf(Monster.class, z);
                assertInstanceOf(Mob.class, z);
                assertInstanceOf(net.minecraft.world.entity.monster.Enemy.class, z);
                assertEquals(zombie.getUniqueId(), z.getUUID());
                assertInstanceOf(Marker.class, marker.getHandle());
                assertFalse(marker.getHandle() instanceof LivingEntity);
            } finally {
                zombie.remove();
                marker.remove();
            }
            return null;
        });
        Entity frame = EntityType.byString("item_frame").orElseThrow().create(level);
        assertInstanceOf(ItemFrame.class, frame);
        assertInstanceOf(HangingEntity.class, frame);
        assertInstanceOf(ItemFrame.class, EntityType.byString("glow_item_frame").orElseThrow().create(level));
    }

    @Test
    void chestBoatClearContentEmptiesItsSlots() {
        on(() -> {
            CraftEntity boat = (CraftEntity) world.spawnEntity(new Location(world, at.x() + 0.5, at.y(), at.z() + 0.5), org.bukkit.entity.EntityType.CHEST_BOAT);
            try {
                Entity h = boat.getHandle();
                assertInstanceOf(ChestBoat.class, h);
                CompoundTag tag = h.saveWithoutId(new CompoundTag());
                ListTag items = new ListTag();
                CompoundTag stone = new CompoundTag();
                stone.putByte("Slot", (byte) 3);
                stone.putString("id", "minecraft:stone");
                stone.putInt("count", 5);
                items.add(stone);
                tag.put("Items", items);
                tag.putString("LootTable", "minecraft:chests/simple_dungeon");
                h.load(tag);
                CompoundTag before = h.saveWithoutId(new CompoundTag());
                assertTrue(before.contains("LootTable"), "전리품 표가 실렸다(바닐라 저장은 표가 있으면 Items 를 쓰지 않는다)");
                ((Clearable) h).clearContent();
                CompoundTag after = h.saveWithoutId(new CompoundTag());
                // 바닐라 clearChestVehicleContent: 전리품 표를 먼저 풀고(표가 지워짐) 칸을 모두 비운다 — 풀기를 빼면 표가 남는다
                assertFalse(after.contains("LootTable"), "전리품 표를 풀어 지웠다");
                assertEquals(0, after.getList("Items", Tag.TAG_COMPOUND).size(), "풀린 전리품까지 모두 비운다");
            } finally {
                boat.remove();
            }
            return null;
        });
    }

    @Test
    void writesFromAnotherRegionThreadThrow() {
        // 소유 규칙(NmsEntityOwner): 다른 region 스레드의 쓰기는 몸통 전에 ThreadOwnershipException
        // (몸통의 NmsEntities.call 도 같은 예외를 던져 이 시험만으로는 검사 줄을 붙잡지 못한다 — 검사가 몸통 첫 줄인지는 ShimSpecBuildTest 가 구조로 본다)
        CraftEntity pig = on(() -> (CraftEntity) world.spawnEntity(new Location(world, at.x() + 0.5, at.y(), at.z() + 0.5), org.bukkit.entity.EntityType.PIG));
        BlockPos far = new BlockPos(at.x() + 4000, at.y(), at.z());
        server.loadChunk(far.x() >> 4, far.z() >> 4);
        Entity h = on(pig::getHandle);
        assertThrows(org.domi.furnace.api.concurrent.ThreadOwnershipException.class, () -> server.onRegion(far, () -> {
            h.setYHeadRot(10.0F);
            return null;
        }));
        on(() -> {
            pig.remove();
            return null;
        });
    }
}
