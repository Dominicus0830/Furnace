package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.domi.furnace.compat.CompatTestPlugin;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** NMS shim 5번 묶음 앞부분: 엔티티·플레이어 모습과 탑승 읽기(Axiom 엔티티 조작·voxy 먼 플레이어 모습이 부르는 것). */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsEntityLookTest {

    FurnaceTestServer server;
    TestPlayer tp;
    CraftPlayer bukkit;
    CraftWorld world;
    org.domi.furnace.api.world.BlockPos at;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).exposeCompatToNative(true).plugin(CompatTestPlugin.class).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Looker");
        server.tick(3);
        bukkit = (CraftPlayer) Compat.player(tp.player());
        world = (CraftWorld) bukkit.getWorld();
        var loc = tp.player().location();
        at = new org.domi.furnace.api.world.BlockPos((int) Math.floor(loc.x()) + 3, (int) Math.floor(loc.y()) + 1, (int) Math.floor(loc.z()) + 3);
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T on(java.util.function.Supplier<T> task) { return server.onRegion(at, task); }

    void command(int action) {
        int id = bukkit.getEntityId();
        tp.sendPacket(0x25, buf -> {   // player_command: 엔티티 번호, 동작, 점프 세기
            org.domi.furnace.server.net.io.Types.writeVarInt(buf, id);
            org.domi.furnace.server.net.io.Types.writeVarInt(buf, action);
            org.domi.furnace.server.net.io.Types.writeVarInt(buf, 0);
        });
        server.tick(2);
    }

    @Test
    void playerLookFromAnyThread() throws Exception {
        ServerPlayer p = bukkit.getHandle();
        var loc = tp.player().location();
        assertEquals((int) Math.floor(loc.x()), p.getBlockX());
        assertEquals((int) Math.floor(loc.z()), p.getBlockZ());
        assertFalse(p.isCrouching());
        assertFalse(p.isSwimming());
        assertFalse(p.isInvisible());
        assertFalse(p.isFallFlying());
        assertFalse(p.isSpectator());
        assertFalse(p.isPassenger());
        assertNull(p.getVehicle());
        assertFalse(p.hasDisconnected());
        assertTrue(p.hasPermissions(0));
        assertFalse(p.hasPermissions(1), "op 가 아니면 권한 단계 0");
        assertEquals("Looker", p.getName().getString());
        assertSame(net.minecraft.core.RegistryAccess.SERVER, p.registryAccess());
        command(0);   // PRESS_SHIFT_KEY — 다음 틱에 웅크림 자세
        assertTrue(p.isCrouching(), "시험 스레드(틱 스레드 아님)에서도 플레이어는 최근 값");
        command(1);   // RELEASE_SHIFT_KEY
        assertFalse(p.isCrouching());
        on(() -> {
            bukkit.setGameMode(org.bukkit.GameMode.SPECTATOR);
            return null;
        });
        server.tick(1);
        assertTrue(p.isSpectator());
        on(() -> {
            bukkit.setGameMode(org.bukkit.GameMode.SURVIVAL);
            return null;
        });
        server.tick(1);
        assertFalse(p.isSpectator());
    }

    @Test
    void ridingOnTheOwnerAndFromAnotherThread() throws Exception {
        CraftEntity boat = on(() -> (CraftEntity) world.spawnEntity(new Location(world, at.x() + 0.5, at.y(), at.z() + 0.5), org.bukkit.entity.EntityType.BOAT));
        CraftEntity pig = on(() -> (CraftEntity) world.spawnEntity(new Location(world, at.x() + 0.5, at.y(), at.z() + 0.5), org.bukkit.entity.EntityType.PIG));
        CraftEntity chick = on(() -> (CraftEntity) world.spawnEntity(new Location(world, at.x() + 0.5, at.y(), at.z() + 0.5), org.bukkit.entity.EntityType.CHICKEN));
        Entity b = boat.getHandle(), p = pig.getHandle(), c = chick.getHandle();
        on(() -> {
            assertTrue(boat.addPassenger(pig));
            assertTrue(pig.addPassenger(chick));
            assertTrue(b.hasPassenger(e -> e == p));
            assertFalse(b.hasPassenger(e -> e == c), "바로 탄 승객만");
            assertTrue(p.isPassenger());
            assertSame(b, p.getVehicle());
            assertSame(p, c.getVehicle());
            assertEquals(List.of(b, p, c), b.getSelfAndPassengers().toList());
            List<Entity> indirect = new java.util.ArrayList<>();
            b.getIndirectPassengers().forEach(indirect::add);
            assertEquals(List.of(p, c), indirect);
            assertFalse(p.isInvisible());
            assertFalse(p.isCrouching());
            assertEquals(b.getBlockY(), (int) Math.floor(b.getY()));
            return null;
        });
        // 탄 엔티티의 움직임은 첫 틱 뒤 매 틱 같다(바닐라 rideTick 이 0 으로 되돌린 뒤 중력 한 번) — 그 뒤 값을 기준으로
        server.tick(3);
        String owner = on(() -> p.getKnownMovement().toString());
        // 다른 스레드(틱 스레드 아님): 주인 region 에 물어 같은 답
        var seen = new AtomicReference<Object>();
        var t = new Thread(() -> {
            try {
                seen.set(List.of(p.getVehicle() == b, b.getSelfAndPassengers().count(), p.getKnownMovement().toString()));
            } catch (Throwable e) {
                seen.set(e);
            }
        });
        t.start();
        for (int i = 0; i < 200 && t.isAlive(); i++) {
            server.tick(1);
            t.join(50);
        }
        assertEquals(List.of(true, 3L, owner), seen.get());
        on(() -> {
            chick.remove();
            pig.remove();
            boat.remove();
            return null;
        });
    }

    @Test
    void smallVanillaShapes() {
        assertEquals("minecraft:player", EntityType.getKey(EntityType.PLAYER).toString());
        assertFalse(EntityType.PLAYER.canSerialize());
        assertFalse(EntityType.byString("lightning_bolt").orElseThrow().canSerialize());
        assertTrue(EntityType.byString("pig").orElseThrow().canSerialize());
        assertTrue(Entity.RemovalReason.KILLED.shouldDestroy());
        assertFalse(Entity.RemovalReason.KILLED.shouldSave());
        assertTrue(Entity.RemovalReason.UNLOADED_TO_CHUNK.shouldSave());
        assertFalse(Entity.RemovalReason.CHANGED_DIMENSION.shouldDestroy());
        assertTrue(Level.isInSpawnableBounds(new BlockPos(29_999_999, 19_999_999, -30_000_000)));
        assertFalse(Level.isInSpawnableBounds(new BlockPos(30_000_000, 0, 0)));
        assertFalse(Level.isInSpawnableBounds(new BlockPos(0, -20_000_001, 0)));
        var age = net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_7;
        assertEquals(0, age.min);
        assertEquals(7, age.max);
        var set = net.minecraft.core.HolderSet.direct(Blocks.STONE.builtInRegistryHolder());
        assertTrue(Blocks.STONE.defaultBlockState().is(set));
        assertFalse(Blocks.DIRT.defaultBlockState().is(set));
        assertSame(world.getHandle().getChunkIfLoaded(at.x() >> 4, at.z() >> 4),
                world.getHandle().getChunkIfLoaded(new BlockPos(at.x(), at.y(), at.z())));
    }
}
