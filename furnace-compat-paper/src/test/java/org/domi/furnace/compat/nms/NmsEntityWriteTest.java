package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * T-212 1-가: FAWE 의 엔티티 복사·지우기 길({@code PaperweightGetBlocks.entities} → {@code Entity#save}, 지우기 동기 작업 →
 * {@code ServerLevel#getEntities().get(uuid)}·{@code Entity#discard}). 기준은 서버가 그 엔티티에 대해 내는 저장 NBT(/data 와 같은 길 + "id")다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsEntityWriteTest {

    FurnaceTestServer server;
    TestPlayer tp;
    ServerLevel level;
    CraftWorld world;
    BlockPos at;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Entities");
        server.tick(3);
        var loc = tp.player().location();
        at = new BlockPos((int) Math.floor(loc.x()) + 3, (int) Math.floor(loc.y()) + 1, (int) Math.floor(loc.z()) + 3);
        world = (CraftWorld) Compat.player(tp.player()).getWorld();
        level = world.getHandle();
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T on(java.util.function.Supplier<T> task) { return server.onRegion(at, task); }

    Location loc() { return new Location(world, at.x() + 0.5, at.y(), at.z() + 0.5); }

    CraftEntity spawn(org.bukkit.entity.EntityType type) { return on(() -> (CraftEntity) world.spawnEntity(loc(), type)); }

    /** 서버가 낸 저장 꼴: /data entity(saveWithoutId) + "id". */
    org.domi.furnace.api.nbt.NbtCompound truth(CraftEntity e) throws Exception {
        var f = e.furnaceHandle().readNbt();   // 엔티티 대기열: 다음 틱에 돈다
        server.tick(1);
        var n = f.get(10, TimeUnit.SECONDS);
        n.putString("id", e.getType().getKey().toString());
        return n;
    }

    @Test
    void saveOnOwnerIsTheServersSaveWithId() throws Exception {
        CraftEntity pig = spawn(org.bukkit.entity.EntityType.PIG);
        on(() -> { pig.setCustomName("Bob"); return null; });
        CompoundTag tag = new CompoundTag();
        tag.putString("keep", "me");   // 바닐라 save 는 받은 묶음에 더한다
        assertTrue(on(() -> pig.getHandle().save(tag)));
        assertEquals("me", tag.getString("keep"));
        tag.remove("keep");
        assertEquals("minecraft:pig", tag.getString("id"));
        assertEquals(pig.getUniqueId(), tag.getUUID("UUID"));
        assertEquals(truth(pig), tag.furnace());
        on(() -> { pig.remove(); return null; });
    }

    @Test
    void passengerIsSavedByItsVehicle() throws Exception {
        CraftEntity pig = spawn(org.bukkit.entity.EntityType.PIG);
        CraftEntity chicken = spawn(org.bukkit.entity.EntityType.CHICKEN);
        assertTrue(on(() -> pig.addPassenger(chicken)));
        assertFalse(on(() -> chicken.getHandle().save(new CompoundTag())), "탄 것은 거짓(바닐라 save: isPassenger)");
        CompoundTag tag = new CompoundTag();
        assertTrue(on(() -> pig.getHandle().save(tag)));
        var passengers = tag.getList("Passengers", 10);
        assertEquals(1, passengers.size());
        assertEquals("minecraft:chicken", passengers.getCompound(0).getString("id"));
        on(() -> { chicken.remove(); pig.remove(); return null; });
    }

    @Test
    void workerThreadWaitsForTheOwner() throws Exception {
        CraftEntity cow = spawn(org.bukkit.entity.EntityType.COW);
        Entity handle = on(cow::getHandle);
        CompoundTag tag = new CompoundTag();
        // FAWE 작업 스레드(틱 스레드가 아님): 주인 region 에 보내 기다린다
        boolean saved = CompletableFuture.supplyAsync(() -> handle.save(tag)).get(20, TimeUnit.SECONDS);
        assertTrue(saved);
        assertEquals(truth(cow), tag.furnace());
        on(() -> { cow.remove(); return null; });
    }

    /**
     * 핸들이 아는 자리가 이제 다른 region 의 것일 때(엔티티가 걸어간 뒤 region 이 갈라진 경우 등): 그 자리의 주인에게 보냈다가 엔티티가 없으면
     * 엔티티를 따라가는 대기열로 한 번 더. 시험은 서버 엔티티를 잠깐 먼 자리에 둔 채 핸들을 찍어 "낡은 자리" 핸들을 만든다(같은 엔티티 객체).
     */
    @Test
    void workerThreadFollowsTheEntityWhenItsSnapshotIsInAnotherRegion() throws Exception {
        CraftEntity cow = spawn(org.bukkit.entity.EntityType.COW);
        Entity shim = on(cow::getHandle);
        BlockPos far = new BlockPos(at.x() + 4000, at.y(), at.z());
        server.loadChunk(far.x() >> 4, far.z() >> 4);
        var host = (org.domi.furnace.server.plugin.PluginHost) server.api();
        var stale = on(() -> {
            var se = org.domi.furnace.api.internal.Internals.get().entityNow(cow.furnaceHandle(), org.domi.furnace.server.entity.Entity.class);
            double x = se.x(), y = se.y(), z = se.z();
            se.setPosRaw(far.x() + 0.5, far.y(), far.z() + 0.5);
            var h = org.domi.furnace.server.plugin.EntityHandle.of(host, se);
            se.setPosRaw(x, y, z);
            return h;
        });
        assertEquals(far.x() + 0.5, stale.location().x());
        assertNotSame(on(() -> server.internals().world().regionizer().ownerOf(at.x() >> 4, at.z() >> 4)),
                server.onRegion(far, () -> server.internals().world().regionizer().ownerOf(far.x() >> 4, far.z() >> 4)), "다른 region");
        cow.furnaceHandle(stale);
        CompoundTag tag = new CompoundTag();
        var f = CompletableFuture.supplyAsync(() -> shim.save(tag));
        for (int i = 0; i < 200 && !f.isDone(); i++) server.tick(1);
        assertTrue(f.get(20, TimeUnit.SECONDS));
        assertEquals(at.x() + 0.5, tag.getList("Pos", 6).getDouble(0), "엔티티가 실제로 있는 자리를 저장했다");
        assertEquals(cow.getUniqueId(), tag.getUUID("UUID"));
        on(() -> { cow.remove(); return null; });
    }

    @Test
    void globalThreadOutsideABorrowCannotWait() {
        CraftEntity cow = spawn(org.bukkit.entity.EntityType.COW);
        Entity handle = on(cow::getHandle);
        var err = assertThrows(IllegalStateException.class, () -> server.onGlobal(() -> handle.save(new CompoundTag())));
        assertTrue(err.getCause() instanceof ThreadOwnershipException || err instanceof ThreadOwnershipException
                || err.getMessage().contains("ThreadOwnership"), String.valueOf(err));
        on(() -> { cow.remove(); return null; });
    }

    @Test
    void discardRemovesTheEntityAndSaveThenFails() {
        CraftEntity sheep = spawn(org.bukkit.entity.EntityType.SHEEP);
        var uuid = sheep.getUniqueId();
        Entity handle = on(sheep::getHandle);
        assertSame(handle, on(() -> level.getEntities().get(uuid)), "FAWE 는 지울 엔티티를 UUID 로 찾는다");
        on(() -> { handle.discard(); return null; });
        assertTrue(handle.isRemoved());
        server.tick(1);
        assertNull(on(() -> level.getEntities().get(uuid)), "월드에서 빠졌다");
        assertFalse(on(() -> handle.save(new CompoundTag())), "지워진(DISCARDED) 엔티티는 저장하지 않는다");
        on(() -> { handle.discard(); return null; });   // 다시 지워도 아무 일 없다
    }

    @Test
    void playerIsNeverSavedNorDiscarded() {
        CraftPlayer p = (CraftPlayer) Compat.player(tp.player());
        assertFalse(p.getHandle().save(new CompoundTag()), "플레이어는 저장하지 않는 종류");
        assertThrows(UnsupportedNmsCall.class, () -> p.getHandle().discard());
    }
}
