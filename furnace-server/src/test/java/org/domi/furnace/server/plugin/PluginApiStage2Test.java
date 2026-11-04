// 플러그인 API 2단계: EntityScheduler(엔티티를 따라감·retired), 영속 데이터(엔티티·청크·플레이어, 저장 왕복), Chunk 핸들,
// 엔티티 스폰·조회, 블록·엔티티 칸 Inventory, 상인 거래, 왼쪽 클릭(LEFT_CLICK_BLOCK/AIR)·엔티티 우클릭 이벤트.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.entity.MerchantRecipe;
import org.domi.furnace.api.event.entity.EntitySpawnEvent;
import org.domi.furnace.api.event.player.PlayerInteractEntityEvent;
import org.domi.furnace.api.event.player.PlayerInteractEvent;
import org.domi.furnace.api.persistence.PersistentDataType;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.world.Location;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ChestBoat;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.entity.Villager;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.Region;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginApiStage2Test {

    private static final ChunkPos C00 = new ChunkPos(0, 0);
    private static final ChunkPos FAR = new ChunkPos(40, 0);   // 다른 region

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;
    private final TestPlugins.Gamma plugin = new TestPlugins.Gamma();

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        host = core.plugins();
        core.world().setRegistries(org.domi.furnace.server.world.BlockRegistry.load("blocks/1.21.1.txt"), org.domi.furnace.server.world.ItemRegistry.load("items/1.21.1.txt"));
        plugin.setEnabled(true);
        core.world().submitAtChunk(C00, r -> r.chunkAtBlocking(C00)).get(30, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() throws Exception {
        host.eventsImpl().unregisterAll(plugin);
        host.close();
        sched.close();
        TestServers.drainIoPools();
    }

    private org.domi.furnace.api.world.World api() { return host.world("minecraft:overworld"); }

    private <T> T at(ChunkPos cp, java.util.function.Function<Region, T> f) throws Exception {
        return core.world().submitAtChunk(cp, f).get(30, TimeUnit.SECONDS);
    }

    /** 그 region 을 n 번 틱한다(자동 틱과 별개로 — 같은 region 스레드에서 돈다). */
    private void tick(ChunkPos cp, int n) throws Exception {
        at(cp, r -> {
            for (int i = 0; i < n; i++) r.tick();
            return null;
        });
    }

    private static <T> T await(CompletableFuture<T> f) throws Exception { return f.get(30, TimeUnit.SECONDS); }

    private Cow spawnCow(Region r, double x, double z) {
        var level = r.level();
        Cow cow = new Cow(level, level.nextEntityId());
        cow.moveTo(x, -50, z, 0, 0);
        assertTrue(level.addFreshEntity(cow));
        return cow;
    }

    // ------------------------------------------------------------------ EntityScheduler

    @Test
    void entitySchedulerRunsOnOwnerFollowsHandoffAndRetires() throws Exception {
        at(FAR, r -> r.chunkAtBlocking(FAR));
        Cow[] cow = new Cow[1];
        var handle = at(C00, r -> {
            cow[0] = spawnCow(r, 8, 8);
            return EntityHandle.of(host, cow[0]);
        });
        var es = handle.entityScheduler();
        CompletableFuture<Boolean> first = new CompletableFuture<>();
        assertTrue(es.execute(() -> first.complete(((org.domi.furnace.api.concurrent.ThreadOwner) core.world().chunkScheduler(0, 0)).isOwnerThread()), null, 1));
        tick(C00, 2);
        assertTrue(await(first), "엔티티를 가진 region 스레드에서 돈다");

        // 다른 region 으로 넘긴다: 소 위치를 FAR 로 옮기면 다음 틱 handOffEntities 가 그 region 에 넘긴다
        at(C00, r -> {
            cow[0].moveTo(FAR.x() * 16 + 8, -50, 8, 0, 0);
            r.tick();
            return null;
        });
        CompletableFuture<Boolean> onFar = new CompletableFuture<>();
        es.execute(() -> onFar.complete(((org.domi.furnace.api.concurrent.ThreadOwner) core.world().chunkScheduler(FAR.x(), FAR.z())).isOwnerThread()), null, 1);
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!onFar.isDone() && System.nanoTime() < end) tick(FAR, 1);
        assertTrue(await(onFar), "넘어간 뒤의 작업은 새 region 스레드에서 돈다");

        // 사라지면 남은 작업 대신 retired, 이후 예약은 거부
        List<String> log = new CopyOnWriteArrayList<>();
        es.execute(() -> log.add("task"), () -> log.add("retired"), 100);
        var rep = es.runAtFixedRate(() -> log.add("rep"), () -> log.add("rep-retired"), 100, 5);
        assertNotNull(rep);
        at(FAR, r -> {
            cow[0].discard();
            return null;
        });
        assertEquals(List.of("retired", "rep-retired"), log);
        assertTrue(es.isRetired());
        assertFalse(es.execute(() -> log.add("late"), null, 0));
        assertNull(es.runDelayed(() -> log.add("late"), null, 0));
    }

    @Test
    void playerEntitySchedulerRunsOnPlayerTickAndRetiresOnDisconnect() throws Exception {
        Player p = player(8.5, -60, 8.5);
        at(C00, r -> {
            r.adopt(p);
            return null;
        });
        var es = host.player(p).entityScheduler();
        CompletableFuture<Boolean> ran = new CompletableFuture<>();
        assertTrue(es.execute(() -> ran.complete(p.ownerRegion != null && p.ownerRegion.ownsPlayer(p)), null, 0));
        tick(C00, 2);
        assertTrue(await(ran), "플레이어 소유 스레드에서 돈다");
        List<String> log = new ArrayList<>();
        es.execute(() -> log.add("task"), () -> log.add("retired"), 50);
        p.markDisconnected();
        assertEquals(List.of("retired"), log);
        assertTrue(es.isRetired());
    }

    // ------------------------------------------------------------------ 영속 데이터

    @Test
    void entityPersistentDataEditsOnOwnerAndSurvivesSave() throws Exception {
        Cow[] cow = new Cow[1];
        var handle = at(C00, r -> {
            cow[0] = spawnCow(r, 8, 8);
            return EntityHandle.of(host, cow[0]);
        });
        assertTrue(handle.persistentData().isEmpty());
        var edit = handle.editPersistentData(pdc -> {
            pdc.set("test:name", PersistentDataType.STRING, "bessie");
            pdc.set("test:count", PersistentDataType.INTEGER, 7);
            pdc.set("test:flag", PersistentDataType.BOOLEAN, true);
            pdc.set("test:longs", PersistentDataType.LONG_ARRAY, new long[] {1L, 2L});
            var nested = pdc.newContainer();
            nested.set("test:inner", PersistentDataType.DOUBLE, 1.5);
            pdc.set("test:nested", PersistentDataType.TAG_CONTAINER, nested);
        });
        tick(C00, 2);
        assertTrue(await(edit));
        var fresh = await(handle.refresh());
        var view = fresh.persistentData();
        assertEquals("bessie", view.get("test:name", PersistentDataType.STRING));
        assertEquals(7, view.get("test:count", PersistentDataType.INTEGER));
        assertTrue(view.get("test:flag", PersistentDataType.BOOLEAN));
        assertArrayEquals(new long[] {1L, 2L}, view.get("test:longs", PersistentDataType.LONG_ARRAY));
        assertEquals(1.5, view.get("test:nested", PersistentDataType.TAG_CONTAINER).get("test:inner", PersistentDataType.DOUBLE));
        assertFalse(view.has("test:count", PersistentDataType.STRING));
        assertThrows(IllegalArgumentException.class, () -> view.get("test:count", PersistentDataType.STRING), "종류가 다르면 예외(Bukkit 과 같다)");
        assertThrows(UnsupportedOperationException.class, () -> ((org.domi.furnace.api.persistence.PersistentDataContainer) view).set("test:x", PersistentDataType.INTEGER, 1));
        assertThrows(IllegalArgumentException.class, () -> view.has("Bad Key"));
        assertTrue(handle.persistentData().isEmpty(), "옛 핸들의 사본은 그대로");

        // 저장 왕복: BukkitValues 로 쓰고 읽는다
        NbtCompound saved = at(C00, r -> cow[0].saveWithoutId(new NbtCompound()));
        assertEquals("bessie", saved.getCompound("BukkitValues").getString("test:name"));
        assertEquals((byte) 1, saved.getCompound("BukkitValues").tags().get("test:flag"), "BOOLEAN 은 byte 1");
        String back = at(C00, r -> {
            Cow c2 = new Cow(r.level(), r.level().nextEntityId());
            c2.load(saved);
            return new NbtPersistentData(c2.persistentData(false), true).get("test:name", PersistentDataType.STRING);
        });
        assertEquals("bessie", back);
        NbtCompound plain = at(C00, r -> new Cow(r.level(), r.level().nextEntityId()).saveWithoutId(new NbtCompound()));
        assertFalse(plain.contains("BukkitValues"), "비었으면 쓰지 않는다");

        // 사라진 엔티티는 false
        at(C00, r -> {
            cow[0].discard();
            return null;
        });
        assertFalse(await(handle.editPersistentData(pdc -> fail("돌면 안 된다"))));
    }

    @Test
    void chunkHandlePersistentDataEntitiesAndUnloadedChunk() throws Exception {
        var w = api();
        var chunk = w.chunkAt(0, 0);
        assertTrue(chunk.isLoaded());
        assertEquals(chunk, w.chunkAt(new BlockPos(5, 0, 5)));
        assertTrue(await(chunk.persistentData()).orElseThrow().isEmpty());
        assertTrue(await(chunk.editPersistentData(pdc -> pdc.set("test:owner", PersistentDataType.STRING, "domi"))));
        assertEquals("domi", await(chunk.persistentData()).orElseThrow().get("test:owner", PersistentDataType.STRING));

        // 청크 저장 꼴에 ChunkBukkitValues 로 실리고 다시 읽힌다
        NbtCompound nbt = at(C00, r -> {
            var col = r.ownedColumn(C00);
            return new org.domi.furnace.server.world.anvil.AnvilChunkCodec(core.world().blockRegistry(), 0).toNbt(col, 0, 0);
        });
        assertEquals("domi", nbt.getCompound("ChunkBukkitValues").getString("test:owner"));
        var reread = new org.domi.furnace.server.world.anvil.AnvilChunkCodec(core.world().blockRegistry(), 0).fromNbt(nbt);
        assertEquals("domi", reread.extraNbt().getCompound("ChunkBukkitValues").getString("test:owner"));

        // 모두 지우면 키도 빠진다
        assertTrue(await(chunk.editPersistentData(pdc -> pdc.remove("test:owner"))));
        assertFalse(this.<Boolean>at(C00, r -> r.ownedColumn(C00).extraNbt() != null && r.ownedColumn(C00).extraNbt().contains("ChunkBukkitValues")));

        // 엔티티 목록
        UUID id = at(C00, r -> spawnCow(r, 3, 3).uuid());
        assertTrue(await(chunk.entities()).stream().anyMatch(e -> e.uniqueId().equals(id)));

        // 로드되지 않은 청크: 만들지 않고 빈 값/false
        int before = core.world().loadedChunkCount();
        var far = w.chunkAt(500, 500);
        assertFalse(far.isLoaded());
        assertEquals(Optional.empty(), await(far.persistentData()));
        assertFalse(await(far.editPersistentData(pdc -> fail("돌면 안 된다"))));
        assertEquals(List.of(), await(far.entities()));
        assertEquals(before, core.world().loadedChunkCount());
    }

    @Test
    void playerPersistentDataEditsOnOwnerAndRoundTripsThroughSaveData() throws Exception {
        Player p = player(8.5, -60, 8.5);
        at(C00, r -> {
            r.adopt(p);
            r.tick();
            return null;
        });
        var ph = host.player(p);
        var f = ph.editPersistentData(pdc -> pdc.set("test:kills", PersistentDataType.INTEGER, 3));
        tick(C00, 1);
        assertTrue(await(f));
        assertEquals(3, ph.persistentData().get("test:kills", PersistentDataType.INTEGER), "플레이어는 최근 값");
        NbtCompound out = new NbtCompound();
        p.savePersistentData(out);
        Player q = player(0, -60, 0);
        q.loadPersistentData(out);
        assertEquals(3, host.player(q).persistentData().get("test:kills", PersistentDataType.INTEGER));

        // 파일 저장: 비우면 BukkitValues 가 파일에서도 지워진다
        Path dir = tmp.resolve("w");
        var extra = new NbtCompound();
        p.savePersistentData(extra);
        org.domi.furnace.server.entity.PlayerData.save(dir, p.uuid(), 0, 0, 0, 0, 0, true, new org.domi.furnace.server.entity.ItemStack[PlayerInventory.SIZE], extra);
        assertTrue(org.domi.furnace.server.entity.PlayerData.load(dir, p.uuid()).root().contains("BukkitValues"));
        org.domi.furnace.server.entity.PlayerData.save(dir, p.uuid(), 0, 0, 0, 0, 0, true, new org.domi.furnace.server.entity.ItemStack[PlayerInventory.SIZE], new NbtCompound());
        assertFalse(org.domi.furnace.server.entity.PlayerData.load(dir, p.uuid()).root().contains("BukkitValues"));
    }

    // ------------------------------------------------------------------ 스폰·조회

    @Test
    void spawnEntityAndNearbyEntitiesAndCancellation() throws Exception {
        var w = api();
        var zombie = await(w.spawnEntity("zombie", new Location("minecraft:overworld", 6.5, -59, 6.5, 90f, 0f)));
        assertNotNull(zombie);
        assertEquals("minecraft:zombie", zombie.type());
        assertEquals(90f, zombie.location().yaw());
        assertTrue(this.<Boolean>at(C00, r -> r.level().entities().get(zombie.uniqueId()) != null), "월드에 들어갔다");

        Player p = player(7.5, -60, 7.5);
        at(C00, r -> {
            r.adopt(p);
            return null;
        });
        var near = await(w.nearbyEntities(new Location("minecraft:overworld", 6.5, -59, 6.5), 3));
        assertTrue(near.stream().anyMatch(e -> e.uniqueId().equals(zombie.uniqueId())));
        assertTrue(near.stream().anyMatch(e -> e instanceof org.domi.furnace.api.entity.Player), "플레이어도 포함");
        assertTrue(await(w.nearbyEntities(new Location("minecraft:overworld", 6.5, 100, 6.5), 3)).isEmpty());

        List<String> retired = new CopyOnWriteArrayList<>();
        host.eventsImpl().subscribe(plugin, EntitySpawnEvent.class, e -> {
            if (!e.entity().type().equals("minecraft:pig")) return;
            e.entity().entityScheduler().execute(() -> retired.add("ran"), () -> retired.add("retired"), 1);
            e.setCancelled(true);
        });
        assertNull(await(w.spawnEntity("minecraft:pig", new Location("minecraft:overworld", 4, -59, 4))), "스폰 이벤트 취소 = null");
        assertEquals(List.of("retired"), retired, "취소된 엔티티에 걸어 둔 작업은 retired 로 끝난다");

        var unknown = assertThrows(ExecutionException.class, () -> w.spawnEntity("minecraft:no_such", new Location("minecraft:overworld", 4, -59, 4)).get());
        assertInstanceOf(IllegalArgumentException.class, unknown.getCause());
        assertInstanceOf(IllegalArgumentException.class, assertThrows(ExecutionException.class,
                () -> w.spawnEntity("player", new Location("minecraft:overworld", 4, -59, 4)).get()).getCause());
        int before = core.world().loadedChunkCount();
        var unloaded = assertThrows(ExecutionException.class, () -> w.spawnEntity("cow", new Location("minecraft:overworld", 16 * 500, 64, 16 * 500)).get(30, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, unloaded.getCause());
        assertEquals(before, core.world().loadedChunkCount(), "스폰이 청크를 만들면 안 된다");
    }

    // ------------------------------------------------------------------ 칸

    @Test
    void chestInventoryAtReadsWritesOnOwnerAndFailsAfterBreak() throws Exception {
        var w = api();
        BlockPos pos = new BlockPos(2, -59, 2);
        assertTrue(await(w.setBlock(pos, BlockData.of("chest"))));
        var inv = await(w.inventoryAt(pos)).orElseThrow();
        assertEquals("minecraft:chest", inv.type());
        assertEquals(27, inv.size());
        await(inv.set(4, new org.domi.furnace.api.item.ItemStack("minecraft:diamond", 5)));
        assertEquals(5, await(inv.get(4)).amount());
        assertEquals("minecraft:diamond", await(inv.contents()).get(4).type());
        assertEquals(5, (int) this.<Integer>at(C00, r -> r.chestAt(2, -59, 2).items[4].count()), "서버 상자에 들어갔다");
        assertTrue(inv.set(99, org.domi.furnace.api.item.ItemStack.EMPTY).isCompletedExceptionally());

        assertTrue(await(w.setBlock(pos, BlockData.of("stone"))));
        var gone = assertThrows(ExecutionException.class, () -> inv.get(4).get(30, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, gone.getCause(), "부서진 상자는 쓰지 못한다");
        assertEquals(Optional.empty(), await(w.inventoryAt(pos)), "용기가 아닌 블록");
        assertEquals(Optional.empty(), await(w.inventoryAt(new BlockPos(16 * 500, 0, 0))), "로드 안 된 청크");
    }

    @Test
    void entityContainerAndVillagerInventoryAndTrades() throws Exception {
        Object[] made = at(C00, r -> {
            var level = r.level();
            ChestBoat boat = new ChestBoat(level, level.nextEntityId());
            boat.moveTo(10, -59, 10, 0, 0);
            level.addFreshEntity(boat);
            Villager v = new Villager(level, level.nextEntityId());
            v.moveTo(12, -59, 12, 0, 0);
            level.addFreshEntity(v);
            Cow cow = spawnCow(r, 4, 12);
            return new Object[] {EntityHandle.of(host, boat), EntityHandle.of(host, v), EntityHandle.of(host, cow), boat};
        });
        var boat = (EntityHandle) made[0];
        var villager = (EntityHandle) made[1];
        var cow = (EntityHandle) made[2];

        assertEquals(Optional.empty(), await(cow.container()), "칸이 없는 엔티티");
        var boatInv = await(boat.container()).orElseThrow();
        assertEquals(27, boatInv.size());
        var setBoat = boatInv.set(0, new org.domi.furnace.api.item.ItemStack("minecraft:stick", 3));
        tick(C00, 1);
        await(setBoat);
        assertEquals(3, (int) this.<Integer>at(C00, r -> ((ChestBoat) made[3]).chestVehicleItems()[0].count()));

        var vInv = await(villager.container()).orElseThrow();
        assertEquals(8, vInv.size());
        var setV = vInv.set(1, new org.domi.furnace.api.item.ItemStack("minecraft:bread", 2));
        var readV = vInv.get(1);
        tick(C00, 1);
        await(setV);
        assertEquals(2, await(readV).amount(), "같은 틱 안에서 순서대로");

        var merchant = assertInstanceOf(org.domi.furnace.api.entity.Merchant.class, villager);
        assertFalse(merchant.isTrading());
        var recipe = MerchantRecipe.of(new org.domi.furnace.api.item.ItemStack("minecraft:emerald", 2), org.domi.furnace.api.item.ItemStack.EMPTY,
                new org.domi.furnace.api.item.ItemStack("minecraft:diamond", 1), 3);
        var set = merchant.setTrades(List.of(recipe));
        tick(C00, 1);
        assertTrue(await(set));
        var trades = merchant.trades();
        tick(C00, 1);
        var got = await(trades);
        assertEquals(1, got.size());
        assertEquals("minecraft:emerald", got.get(0).ingredient1().type());
        assertEquals(2, got.get(0).ingredient1().amount());
        assertTrue(got.get(0).ingredient2().isEmpty());
        assertEquals("minecraft:diamond", got.get(0).result().type());
        assertEquals(3, got.get(0).maxUses());
        var bad = merchant.setTrades(List.of(MerchantRecipe.of(new org.domi.furnace.api.item.ItemStack("minecraft:no_item", 1), null,
                new org.domi.furnace.api.item.ItemStack("minecraft:diamond", 1), 1)));
        assertInstanceOf(IllegalArgumentException.class, assertThrows(ExecutionException.class, bad::get).getCause());
    }

    // ------------------------------------------------------------------ 이벤트

    @Test
    void leftClickBlockFiresOnStartDestroyAndCancelKeepsBlock() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        host.eventsImpl().subscribe(plugin, PlayerInteractEvent.class, e -> {
            log.add(e.action() + ":" + (e.clickedBlock() == null ? "-" : e.clickedBlock().type()) + ":" + e.blockFace());
            e.setCancelled(true);
        });
        Player p = player(0.5, -60, -1.5);
        p.vitals().setGameMode(org.domi.furnace.server.world.GameMode.CREATIVE);   // 크리에이티브는 시작 즉시 부순다 — 취소가 막아야 한다
        var pos = new org.domi.furnace.server.block.Pos(0, -60, 0);
        int[] state = at(C00, r -> {
            r.adopt(p);
            r.level().setBlock(pos, org.domi.furnace.server.block.B.defaultState("minecraft:stone"), 3);
            r.handleBlockBreakAction(p, pos, org.domi.furnace.server.block.BlockBreaking.START_DESTROY_BLOCK, 2, true);
            return new int[] {r.level().getBlockState(pos)};
        });
        assertEquals(List.of("LEFT_CLICK_BLOCK:minecraft:stone:north"), log);
        assertEquals(org.domi.furnace.server.block.B.defaultState("minecraft:stone"), state[0], "취소하면 부서지지 않는다");

        host.eventsImpl().unregisterAll(plugin);
        int after = at(C00, r -> {
            r.handleBlockBreakAction(p, pos, org.domi.furnace.server.block.BlockBreaking.START_DESTROY_BLOCK, 2, true);
            return r.level().getBlockState(pos);
        });
        assertTrue(org.domi.furnace.server.block.BlockInfo.get().isAir(after), "구독자가 없으면 그대로 부순다");
    }

    @Test
    void leftClickAirFiresOnlyWhenNothingIsInReach() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        host.eventsImpl().subscribe(plugin, PlayerInteractEvent.class, e -> log.add(e.action() + ":" + e.item().type()));
        Player p = player(8.5, -60, 8.5);
        p.inventory().set(36, org.domi.furnace.server.block.B.stack("minecraft:stick", 1));
        p.inventoryHost(new Player.InventoryHost() {
            public org.domi.furnace.server.entity.ItemStack slot(int ws) { return p.inventory().get(ws); }
            public int selected() { return 0; }
            public void replace(int ws, org.domi.furnace.server.entity.ItemStack expect, org.domi.furnace.server.entity.ItemStack now) { p.inventory().replace(ws, expect, now); }
            public int permissionLevel() { return 0; }
        });
        at(C00, r -> {
            r.adopt(p);
            p.applyMove(8.5, -60, 8.5, 0f, -90f, true);   // 하늘을 본다
            r.swingLeftClick(p, 0);
            r.swingLeftClick(p, 1);                         // 왼손 휘두르기는 아니다
            r.level().setBlock(new org.domi.furnace.server.block.Pos(8, -61, 8), org.domi.furnace.server.block.B.defaultState("minecraft:stone"), 3);
            p.applyMove(8.5, -60, 8.5, 0f, 90f, true);    // 발밑 블록을 본다
            r.swingLeftClick(p, 0);
            boolean blockSeen = log.size() == 1;
            p.applyMove(8.5, -60, 8.5, 0f, 0f, true);     // 남쪽(+z) 눈높이 앞에 소
            spawnCow(r, 8.5, 10.5).moveTo(8.5, -59, 10.5, 0, 0);
            r.swingLeftClick(p, 0);
            assertTrue(blockSeen, "블록을 보면 발화하지 않는다");
            return null;
        });
        assertEquals(List.of("LEFT_CLICK_AIR:minecraft:stick"), log, "블록·엔티티가 시선 안에 있으면 발화하지 않는다");
    }

    @Test
    void interactEntityEventCanCancel() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        host.eventsImpl().subscribe(plugin, PlayerInteractEntityEvent.class, e -> {
            log.add(e.target().type() + ":" + e.isMainHand() + ":" + e.item().type());
            e.setCancelled(true);
        });
        Player p = player(8.5, -60, 8.5);
        boolean allowed = at(C00, r -> {
            Cow cow = spawnCow(r, 9, 9);
            return r.level().gameEvents().playerInteractEntity(p, cow, 0, org.domi.furnace.server.block.B.stack("minecraft:wheat", 1));
        });
        assertFalse(allowed);
        assertEquals(List.of("minecraft:cow:true:minecraft:wheat"), log);
    }

    private Player player(double x, double y, double z) {
        Player p = new Player(core.world().entityIds().next(), UUID.randomUUID(), "A", new EmbeddedChannel());
        p.inventory(new PlayerInventory());
        p.applyMove(x, y, z, 0, 0, true);
        return p;
    }
}
