package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
