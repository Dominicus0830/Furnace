package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
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

/** NMS shim 5b: 월드의 엔티티 바꾸기(Axiom ManipulateEntity 흐름 — 저장·합치기·싣기·옮기기·태우기). */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsEntityEditTest {

    FurnaceTestServer server;
    TestPlayer tp;
    CraftPlayer bukkit;
    CraftWorld world;
    ServerLevel level;
    org.domi.furnace.api.world.BlockPos at;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).exposeCompatToNative(true).plugin(CompatTestPlugin.class).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Editor");
        server.tick(3);
        bukkit = (CraftPlayer) Compat.player(tp.player());
        world = (CraftWorld) bukkit.getWorld();
        level = world.getHandle();
        var loc = tp.player().location();
        at = new org.domi.furnace.api.world.BlockPos((int) Math.floor(loc.x()) + 3, (int) Math.floor(loc.y()), (int) Math.floor(loc.z()) + 3);
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T on(java.util.function.Supplier<T> task) { return server.onRegion(at, task); }

    CraftEntity spawn(org.bukkit.entity.EntityType type) {
        return on(() -> (CraftEntity) world.spawnEntity(new Location(world, at.x() + 0.5, at.y(), at.z() + 0.5), type));
    }

    static double[] pos(CompoundTag t) {
        var p = t.getList("Pos", 6);
        return new double[] {p.getDouble(0), p.getDouble(1), p.getDouble(2)};
    }

    @Test
    void saveMergeLoadAndMoveLikeAxiom() {
        CraftEntity cow = spawn(org.bukkit.entity.EntityType.COW);
        Entity c = cow.getHandle();
        on(() -> {
            CompoundTag tag = c.saveWithoutId(new CompoundTag());
            assertFalse(tag.contains("id"), "saveWithoutId 에는 id 가 없다");
            assertEquals(cow.getUniqueId(), tag.getUUID("UUID"));
            double[] before = pos(tag);
            // Axiom: 저장한 것에 고친 값을 합쳐 다시 싣고, 자리는 원래대로 되돌린 뒤 teleportTo
            tag.putBoolean("Invulnerable", true);
            tag.putBoolean("NoAI", true);
            c.load(tag);
            c.setPosRaw(before[0], before[1], before[2]);
            CompoundTag after = c.saveWithoutId(new CompoundTag());
            assertTrue(after.getBoolean("Invulnerable"));
            assertTrue(after.getBoolean("NoAI"));
            assertEquals(cow.getUniqueId(), after.getUUID("UUID"), "같은 UUID 로 실었다");
            assertTrue(c.teleportTo(level, before[0] + 1.5, before[1], before[2], Set.of(), 90.0F, 120.0F));
            c.setYHeadRot(45.0F);
            CompoundTag moved = c.saveWithoutId(new CompoundTag());
            assertEquals(before[0] + 1.5, pos(moved)[0]);
            assertEquals(90.0F, moved.getList("Rotation", 5).getFloat(0));
            assertEquals(90.0F, moved.getList("Rotation", 5).getFloat(1), "위아래 각은 ±90 으로 자른다(바닐라 teleportTo)");
            // 다른 UUID 를 실으면 서버의 엔티티 색인이 깨지므로 거절(조용히 바꾸지 않는다)
            CompoundTag other = moved.copy();
            other.putUUID("UUID", UUID.randomUUID());
            assertThrows(UnsupportedNmsCall.class, () -> c.load(other));
            assertEquals(cow.getUniqueId(), c.saveWithoutId(new CompoundTag()).getUUID("UUID"));
            cow.remove();
            return null;
        });
    }

    @Test
    void ridingAndEjecting() {
        CraftEntity boat = spawn(org.bukkit.entity.EntityType.BOAT);
        CraftEntity pig = spawn(org.bukkit.entity.EntityType.PIG);
        Entity b = boat.getHandle(), p = pig.getHandle();
        on(() -> {
            assertTrue(p.startRiding(b, true));
            assertTrue(b.hasPassenger(e -> e == p));
            assertFalse(p.startRiding(b, true), "이미 탄 탈것이면 거짓");
            p.stopRiding();
            assertFalse(p.isPassenger());
            assertFalse(p.startRiding(b), "내린 직후에는 강제 없이 못 탄다(바닐라 boardingCooldown 60 틱)");
            assertTrue(p.startRiding(b, true));
            b.ejectPassengers();
            assertFalse(b.hasPassenger(e -> true));
            pig.remove();
            boat.remove();
            return null;
        });
    }

    @Test
    void playersAndWrongThreads() {
        ServerPlayer me = bukkit.getHandle();
        CompoundTag saved = me.saveWithoutId(new CompoundTag());   // 플레이어 저장 읽기는 아무 스레드
        assertTrue(saved.contains("SelectedItemSlot"));
        assertFalse(saved.contains("SelectedItem"), "/data 비교용 키는 바닐라 saveWithoutId 에 없다");
        assertThrows(UnsupportedNmsCall.class, () -> me.setPosRaw(0, 100, 0));
        CraftEntity sheep = spawn(org.bukkit.entity.EntityType.SHEEP);
        Entity s = sheep.getHandle();
        var err = assertThrows(IllegalStateException.class, () -> server.onGlobal(() -> s.saveWithoutId(new CompoundTag())));
        assertTrue(err instanceof org.domi.furnace.api.concurrent.ThreadOwnershipException
                || err.getCause() instanceof org.domi.furnace.api.concurrent.ThreadOwnershipException, String.valueOf(err));
        // 틱 스레드가 아니면 주인 region 을 기다린다
        var seen = new java.util.concurrent.atomic.AtomicReference<Object>();
        var t = new Thread(() -> {
            try {
                seen.set(s.saveWithoutId(new CompoundTag()).getUUID("UUID"));
            } catch (Throwable e) {
                seen.set(e);
            }
        });
        t.start();
        for (int i = 0; i < 200 && t.isAlive(); i++) {
            server.tick(1);
            try {
                t.join(50);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
        assertEquals(sheep.getUniqueId(), seen.get());
        on(() -> {
            sheep.remove();
            return null;
        });
    }
}
