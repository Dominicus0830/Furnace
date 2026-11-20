package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.RegisteredListener;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

/**
 * NMS shim 5d-3: Axiom 블록 놓기(SetBlockPacketListener)가 부르는 길 — 블록 요청 ack, 플러그인이 만든 CraftBlockState 하위 클래스로 놓기 이벤트,
 * BlockItem.updateCustomBlockEntityTag, BlockEntity.applyComponentsFromItemStack, Block.setPlacedBy. 참값은 서버 쪽(서버 블록·블록 엔티티 저장·받은 패킷)이다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsBlockPlaceTest {

    FurnaceTestServer server;
    TestPlayer tp;
    ServerLevel level;
    ServerPlayer player;
    int cx, cz, baseY;

    /** Axiom SetBlockPacketListener$AxiomPlacingCraftBlockState 와 같은 꼴: Paper 생성자만 부르는 하위 클래스. */
    static final class PlacingState extends CraftBlockState {
        PlacingState(org.bukkit.World world, BlockPos pos, BlockState state) { super(world, pos, state); }
    }

    @TempDir
    static java.nio.file.Path tmp;

    @BeforeAll
    void start() throws Exception {
        // 권한 첨부는 이 서버가 불러온 플러그인이어야 서버 쪽 판정에 든다(PermissibleBase 거울)
        var jar = org.domi.furnace.compat.paper.fixtures.Jars.bukkit(tmp.resolve("recorder.jar"), """
                name: Recorder
                version: '1'
                main: org.domi.furnace.compat.paper.fixtures.RecorderPlugin
                api-version: '1.21'
                """, Map.of("config.yml", "a: 1\n"), org.domi.furnace.compat.paper.fixtures.RecorderPlugin.class);
        server = FurnaceTestServer.builder().failFast(false).pluginJar(jar).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Placer");
        server.tick(3);
        var loc = tp.player().location();
        cx = Math.floorDiv((int) Math.floor(loc.x()), 16);
        cz = Math.floorDiv((int) Math.floor(loc.z()), 16);
        baseY = (int) Math.floor(loc.y()) + 20;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) server.loadChunk(cx + dx, cz + dz);
        server.tick(2);
        CraftPlayer cp = (CraftPlayer) Compat.player(tp.player());
        player = cp.getHandle();
        level = ((CraftWorld) cp.getWorld()).getHandle();
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T on(BlockPos p, Supplier<T> task) { return server.onRegion(new org.domi.furnace.api.world.BlockPos(p.getX(), p.getY(), p.getZ()), task); }

    String serverBlock(BlockPos p) {
        return on(p, () -> tp.player().world().block(new org.domi.furnace.api.world.BlockPos(p.getX(), p.getY(), p.getZ())).data().toString());
    }

    /** 그 칸 블록 엔티티의 저장 꼴(서버 블록 엔티티를 보는 보기로 — 주인 스레드). */
    String serverBeSave(BlockPos p) {
        return on(p, () -> {
            var be = level.getBlockEntity(p);
            return be == null ? "" : be.saveWithId(level.registryAccess()).toString();
        });
    }

    static org.bukkit.plugin.Plugin fakePlugin() {
        return (org.bukkit.plugin.Plugin) java.lang.reflect.Proxy.newProxyInstance(NmsBlockPlaceTest.class.getClassLoader(),
                new Class<?>[] {org.bukkit.plugin.Plugin.class}, (proxy, m, args) -> switch (m.getName()) {
                    case "isEnabled" -> true;
                    case "getName" -> "AxiomLike";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "AxiomLike";
                    default -> m.getReturnType() == boolean.class ? false : null;
                });
    }

    static ItemStack item(String type, Map<String, String> components) {
        return ItemStack.fromFurnace(new org.domi.furnace.api.item.ItemStack(type, 1, components, Set.of()), 1);
    }

    @Test
    void ackBlockChangesReachesThePlayerAfterTheTick() {
        tp.clearReceived();
        player.connection.ackBlockChangesUpTo(4321);
        server.tick(2);
        var ack = tp.awaitPacket(PacketTypes.Clientbound.BLOCK_CHANGED_ACK, p -> Integer.valueOf(4321).equals(p.get(p.names().get(0))));
        assertNotNull(ack);
        var e = assertThrows(IllegalArgumentException.class, () -> player.connection.ackBlockChangesUpTo(-1));
        assertEquals("Expected packet sequence nr >= 0", e.getMessage());
    }

    /** 바닐라: 블록 변경 방송(레벨 틱)이 연결 틱의 ack 보다 먼저다 — 클라이언트는 ack 를 받을 때 이미 서버 블록을 안다. */
    @Test
    void ackComesAfterTheBlockUpdateOfTheSameTick() {
        BlockPos p = new BlockPos(cx * 16 + 10, baseY + 3, cz * 16 + 10);
        var fp = new org.domi.furnace.api.world.BlockPos(p.getX(), p.getY(), p.getZ());
        server.tick(2);   // 앞 시험들이 같은 구역에 남긴 변경을 먼저 내보낸다 — 그래야 이 칸 하나만 block_update 로 간다
        tp.clearReceived();
        on(p, () -> {
            level.setBlock(p, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
            player.connection.ackBlockChangesUpTo(5555);
            return null;
        });
        server.tick(2);
        tp.awaitPacket(PacketTypes.Clientbound.BLOCK_CHANGED_ACK, a -> Integer.valueOf(5555).equals(a.get(a.names().get(0))));
        List<org.domi.furnace.api.packet.Packet> got = tp.received();
        int update = -1, ack = -1;
        for (int i = 0; i < got.size(); i++) {
            var pk = got.get(i);
            if (update < 0 && pk instanceof org.domi.furnace.api.packet.clientbound.ClientboundBlockUpdatePacket u && u.pos().equals(fp)) update = i;
            if (ack < 0 && pk.type() == PacketTypes.Clientbound.BLOCK_CHANGED_ACK) ack = i;
        }
        assertTrue(update >= 0 && update < ack, "블록 갱신 " + update + ", ack " + ack + " " + got.stream().map(x -> x.type() + "").toList());
    }

    /** Paper canBuild: OP 가 하나라도 있고 놓는 이가 OP 가 아니면 스폰 보호 반경 안에서는 거짓, 반경 밖은 참. */
    @Test
    void spawnProtectionMakesCanBuildFalse() {
        var spawn = level.getWorld().getSpawnLocation();
        TestPlayer adminTp = server.join("SpawnAdmin");
        server.tick(2);
        var admin = Compat.player(adminTp.player());
        admin.setOp(true);
        try {
            CraftWorld world = level.getWorld();
            BlockPos in = new BlockPos(spawn.getBlockX(), baseY, spawn.getBlockZ());
            var inside = on(in, () -> CraftEventFactory.callBlockPlaceEvent(level, player, InteractionHand.MAIN_HAND,
                    new PlacingState(world, in, Blocks.AIR.defaultBlockState()), in.getX(), in.getY() - 1, in.getZ()));
            assertFalse(inside.canBuild(), "스폰 칸");
            BlockPos out = in.east(17);
            var outside = on(out, () -> CraftEventFactory.callBlockPlaceEvent(level, player, InteractionHand.MAIN_HAND,
                    new PlacingState(world, out, Blocks.AIR.defaultBlockState()), out.getX(), out.getY() - 1, out.getZ()));
            assertTrue(outside.canBuild(), "반경(16) 밖");
        } finally {
            admin.setOp(false);
            adminTp.close();
            server.tick(2);
        }
    }

    /** Paper: 즉시 짓기이고 minecraft.nbt.place 권한이 있으면 OP 아니어도 관리자용 블록(명령 블록) 데이터를 싣는다. */
    @Test
    void instabuildWithNbtPlacePermissionLoadsCommandBlockData() {
        BlockPos q = new BlockPos(cx * 16 + 12, baseY, cz * 16 + 12);
        ItemStack cmd = item("minecraft:command_block", Map.of("minecraft:block_entity_data", "{id:\"minecraft:command_block\",Command:\"say nbt\"}"));
        on(q, () -> { level.setBlock(q, Blocks.COMMAND_BLOCK.defaultBlockState(), 3); return null; });
        on(q, () -> { player.getAbilities().instabuild = true; player.onUpdateAbilities(); return null; });
        var att = player.getBukkitEntity().addAttachment(Bukkit.getPluginManager().getPlugin("Recorder"));
        try {
            assertFalse(on(q, () -> BlockItem.updateCustomBlockEntityTag(level, player, q, cmd)), "권한 없이 즉시 짓기만으로는 못 싣는다");
            att.setPermission("minecraft.nbt.place", true);
            assertTrue(on(q, () -> BlockItem.updateCustomBlockEntityTag(level, player, q, cmd)));
            assertTrue(serverBeSave(q).contains("say nbt"), serverBeSave(q));
        } finally {
            att.remove();
            on(q, () -> { player.getAbilities().instabuild = false; player.onUpdateAbilities(); return null; });
        }
    }

    @Test
    void placeEventsWithAPluginMadeCraftBlockState() {
        BlockPos p = new BlockPos(cx * 16 + 2, baseY, cz * 16 + 2);
        List<BlockPlaceEvent> seen = new ArrayList<>();
        var rl = new RegisteredListener(new org.bukkit.event.Listener() {}, (l, e) -> {
            BlockPlaceEvent ev = (BlockPlaceEvent) e;
            seen.add(ev);
            if (ev.getBlock().getY() == baseY + 1) ev.setCancelled(true);
        }, EventPriority.NORMAL, fakePlugin(), false);
        BlockPlaceEvent.getHandlerList().register(rl);
        try {
            CraftWorld world = level.getWorld();
            var one = on(p, () -> CraftEventFactory.callBlockPlaceEvent(level, player, InteractionHand.MAIN_HAND,
                    new PlacingState(world, p, Blocks.STONE.defaultBlockState()), p.getX(), p.getY() - 1, p.getZ()));
            assertSame(one, seen.get(0));
            assertFalse(one.isCancelled());
            assertTrue(one.canBuild(), "OP 가 없으면 스폰 보호가 없다");
            assertEquals(p.getX(), one.getBlockPlaced().getX());
            assertEquals(baseY - 1, one.getBlockAgainst().getY());
            assertEquals(org.bukkit.Material.STONE, one.getBlockReplacedState().getType(), "플러그인이 만든 상태가 그대로 바뀌기 전 상태");
            assertEquals(org.bukkit.inventory.EquipmentSlot.HAND, one.getHand());
            assertInstanceOf(org.bukkit.craftbukkit.block.CraftBlock.class, ((CraftBlockState) one.getBlockReplacedState()).getBlock());

            BlockPos up = p.above();
            List<org.bukkit.block.BlockState> states = List.of(new PlacingState(world, up, Blocks.AIR.defaultBlockState()),
                    new PlacingState(world, up.above(), Blocks.AIR.defaultBlockState()));
            BlockMultiPlaceEvent multi = on(p, () -> CraftEventFactory.callBlockMultiPlaceEvent(level, player, InteractionHand.OFF_HAND, states,
                    p.getX(), p.getY(), p.getZ()));
            assertSame(multi, seen.get(1), "여러 칸 이벤트도 BlockPlaceEvent 처리기가 받는다");
            assertTrue(multi.isCancelled());
            assertEquals(2, multi.getReplacedBlockStates().size());
            assertEquals(org.bukkit.inventory.EquipmentSlot.OFF_HAND, multi.getHand());
        } finally {
            BlockPlaceEvent.getHandlerList().unregister(rl);
        }
    }

    @Test
    void customBlockEntityDataAndComponentsFromTheItem() {
        BlockPos p = new BlockPos(cx * 16 + 4, baseY, cz * 16 + 4);
        ItemStack stack = item("minecraft:barrel", Map.of(
                "minecraft:block_entity_data", "{id:\"minecraft:barrel\",Items:[{Slot:3b,id:\"minecraft:emerald\",count:7}]}",
                "minecraft:custom_name", "'\"Loot\"'"));
        on(p, () -> {
            level.setBlock(p, Blocks.BARREL.defaultBlockState(), 3);
            assertTrue(BlockItem.updateCustomBlockEntityTag(level, player, p, stack));
            return null;
        });
        var saved = serverBeSave(p);
        assertTrue(saved.contains("minecraft:emerald"), saved);
        on(p, () -> {
            level.getBlockEntity(p).applyComponentsFromItemStack(stack);
            return null;
        });
        saved = serverBeSave(p);
        assertTrue(saved.contains("Loot"), saved);
        // 바닐라: 상자류는 container 컴포넌트가 없으면 빈 내용으로 읽는다(block_entity_data 로 실은 내용을 지운다 — BlockItem.place 와 같은 차례면 그렇다)
        assertTrue(saved.contains("Items:[]"), saved);

        // 관리자만 쓸 수 있는 종류(명령 블록): OP 아닌 서바이벌 플레이어는 싣지 못한다(Paper 와 같은 판정)
        BlockPos q = new BlockPos(cx * 16 + 6, baseY, cz * 16 + 6);
        ItemStack cmd = item("minecraft:command_block", Map.of("minecraft:block_entity_data", "{id:\"minecraft:command_block\",Command:\"say hi\"}"));
        boolean loaded = on(q, () -> {
            level.setBlock(q, Blocks.COMMAND_BLOCK.defaultBlockState(), 3);
            return BlockItem.updateCustomBlockEntityTag(level, player, q, cmd);
        });
        assertFalse(loaded);
        assertFalse(serverBeSave(q).contains("say hi"));
        // 아이템에 데이터가 없으면 거짓
        assertFalse(on(p, () -> BlockItem.updateCustomBlockEntityTag(level, player, p, item("minecraft:barrel", Map.of()))));
    }

    @Test
    void setPlacedByRunsTheVanillaBlockCode() {
        BlockPos foot = new BlockPos(cx * 16 + 8, baseY, cz * 16 + 8);
        BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        on(foot, () -> {
            level.setBlock(foot, bed, 3);
            bed.getBlock().setPlacedBy(level, foot, bed, player, item("minecraft:red_bed", Map.of()));
            return null;
        });
        String head = serverBlock(foot.east());
        assertTrue(head.startsWith("minecraft:red_bed") && head.contains("part=head") && head.contains("facing=east"), head);
        // 다른 생물은 놓는 이가 될 수 없다
        assertThrows(UnsupportedNmsCall.class, () -> on(foot, () -> {
            Blocks.STONE.setPlacedBy(level, foot.above(), Blocks.STONE.defaultBlockState(),
                    notAPlayer(), ItemStack.EMPTY);
            return null;
        }));
    }

    @Test
    void blockFacesLikePaper() {
        for (Direction d : Direction.values()) {
            var face = org.bukkit.craftbukkit.block.CraftBlock.notchToBlockFace(d);
            assertEquals(d.name(), face.name());
            assertSame(d, org.bukkit.craftbukkit.block.CraftBlock.blockFaceToNotch(face));
        }
        assertSame(org.bukkit.block.BlockFace.SELF, org.bukkit.craftbukkit.block.CraftBlock.notchToBlockFace(null));
        assertEquals(null, org.bukkit.craftbukkit.block.CraftBlock.blockFaceToNotch(org.bukkit.block.BlockFace.NORTH_EAST));
        assertEquals(null, org.bukkit.craftbukkit.block.CraftBlock.blockFaceToNotch(null));
    }

    /** ServerPlayer 가 아닌 생물 shim(월드 밖 돼지). */
    net.minecraft.world.entity.LivingEntity notAPlayer() {
        return (net.minecraft.world.entity.LivingEntity) net.minecraft.world.entity.EntityType.byString("pig").orElseThrow().create(level);
    }
}
