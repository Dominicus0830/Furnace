package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.packet.PacketStruct;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * NMS shim 5e-3c-2(Axiom): 아이템 스트림 코덱, 바이옴 다시 보내기 패킷, 엔티티 전부, 블록·유체 예약 틱.
 * 아이템 바이트 기대값은 Paper 1.21.1 jar 에서 같은 스택을 {@code ItemStack.OPTIONAL_STREAM_CODEC}·{@code STREAM_CODEC} 으로 쓴 결과다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsAxiomTicksAndWireTest {

    FurnaceTestServer server;
    TestPlayer tp;
    ServerLevel level;
    int baseX, baseZ;
    static final int Y = 150;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Ticks");
        server.tick(3);
        var loc = tp.player().location();
        baseX = Math.floorDiv((int) Math.floor(loc.x()), 16) * 16;
        baseZ = Math.floorDiv((int) Math.floor(loc.z()), 16) * 16;
        level = ((CraftWorld) Compat.player(tp.player()).getWorld()).getHandle();
        server.execute("forceload add " + baseX + " " + baseZ + " " + (baseX + 15) + " " + (baseZ + 15));
        server.tick(5);
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T on(BlockPos p, Supplier<T> task) { return server.onRegion(new org.domi.furnace.api.world.BlockPos(p.getX(), p.getY(), p.getZ()), task); }

    void run(BlockPos p, Runnable task) { on(p, () -> { task.run(); return null; }); }

    BlockState at(BlockPos p) { return on(p, () -> level.getBlockState(p)); }

    static RegistryFriendlyByteBuf buf() { return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.SERVER); }

    static RegistryFriendlyByteBuf buf(byte... bytes) { return new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), RegistryAccess.SERVER); }

    static ItemStack stack(String type, int count, Map<String, String> components, Set<String> removed) {
        return ItemStack.fromFurnace(new org.domi.furnace.api.item.ItemStack(type, Math.max(count, 0), components, removed), count);
    }

    @Test
    void itemStreamCodecsWritePaperBytes() {
        Map<String, String> comps = new LinkedHashMap<>();
        comps.put("minecraft:max_stack_size", "16");
        comps.put("minecraft:damage", "5");
        comps.put("minecraft:custom_name", "'\"Hi\"'");
        Object[][] cases = {
            {ItemStack.EMPTY, "00"},
            {stack("minecraft:diamond", 3, Map.of(), Set.of()), "03a5060000"},
            {stack("minecraft:stick", 300, Map.of(), Set.of()), "ac02d0060000"},
            // Paper 는 컴포넌트를 넣은 순서(01 max_stack_size, 03 damage, 05 custom_name)로 쓴다: 01c60603010110030505080002486908.
            // Furnace 는 이름 순서(custom_name, damage, max_stack_size)다 — 같은 아이템으로 읽힌다(아래에서 Paper 바이트를 읽어 확인, user-todo 5e-3c-2 ①)
            {stack("minecraft:diamond_sword", 1, comps, Set.of("minecraft:rarity")), "01c60603010508000248690305011008"},
            {stack("minecraft:diamond", 0, Map.of(), Set.of()), "00"},
        };
        for (Object[] c : cases) {
            ItemStack s = (ItemStack) c[0];
            var b = buf();
            ItemStack.OPTIONAL_STREAM_CODEC.encode(b, s);
            assertEquals(c[1], ByteBufUtil.hexDump(b), "쓰기 " + s);
            b.writeByte(0x7F);   // 뒤에 남은 바이트는 읽지 않는다
            ItemStack back = ItemStack.OPTIONAL_STREAM_CODEC.decode(b);
            assertEquals(1, b.readableBytes(), "다시 읽기는 한 스택만");
            assertEquals(s.getCount(), back.getCount());
            assertSame(s.getItem(), back.getItem());
            if (s.isEmpty()) {
                assertSame(ItemStack.EMPTY, back);
                assertEquals("Empty ItemStack not allowed", assertThrows(EncoderException.class, () -> ItemStack.STREAM_CODEC.encode(buf(), s)).getMessage());
            } else {
                var nb = buf();
                ItemStack.STREAM_CODEC.encode(nb, s);
                assertEquals(c[1], ByteBufUtil.hexDump(nb));
                assertEquals(s.furnaceValue(), ItemStack.STREAM_CODEC.decode(nb).furnaceValue());
            }
        }
        var paper = buf(io.netty.buffer.ByteBufUtil.decodeHexDump("01c60603010110030505080002486908"));
        assertEquals(((ItemStack) cases[3][0]).furnaceValue(), ItemStack.STREAM_CODEC.decode(paper).furnaceValue(), "Paper 순서 바이트도 같은 아이템");
        assertEquals(0, paper.readableBytes());
        var zero = buf((byte) 0, (byte) 7);
        assertEquals("Empty ItemStack not allowed", assertThrows(DecoderException.class, () -> ItemStack.STREAM_CODEC.decode(zero)).getMessage());
        assertEquals(1, zero.readerIndex());
        var neg = buf((byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x0F, (byte) 9);
        assertSame(ItemStack.EMPTY, ItemStack.OPTIONAL_STREAM_CODEC.decode(neg), "음수 개수는 빈 스택");
        assertEquals(5, neg.readerIndex());
    }

    @Test
    void chunksBiomesPacketCarriesEachSectionBiomeContainer() {
        BlockPos p = new BlockPos(baseX + 1, Y, baseZ + 1);
        ClientboundChunksBiomesPacket made = on(p, () -> ClientboundChunksBiomesPacket.forChunks(List.of(level.getChunkAt(p))));
        assertEquals(1, made.chunkBiomeData().size());
        var data = made.chunkBiomeData().get(0);
        assertEquals(new net.minecraft.world.level.ChunkPos(baseX >> 4, baseZ >> 4), data.pos());
        LevelChunk chunk = on(p, () -> level.getChunkAt(p));
        byte[] expected = on(p, () -> {
            var b = Unpooled.buffer();
            for (var s : chunk.getSections()) s.getBiomes().write(new net.minecraft.network.FriendlyByteBuf(b));
            return ByteBufUtil.getBytes(b);
        });
        assertArrayEquals(expected, data.buffer());
        assertArrayEquals(expected, ByteBufUtil.getBytes(data.getReadBuffer()));

        var conn = ((org.bukkit.craftbukkit.entity.CraftPlayer) Compat.player(tp.player())).getHandle().connection;
        run(p, () -> conn.send(made));
        var sent = tp.awaitPacket(PacketTypes.Clientbound.CHUNKS_BIOMES, g -> true);
        var entries = (List<?>) sent.get("chunk_biome_data");
        assertEquals(1, entries.size());
        PacketStruct e = (PacketStruct) entries.get(0);
        assertEquals(net.minecraft.world.level.ChunkPos.asLong(baseX >> 4, baseZ >> 4), e.get("pos"));
        assertArrayEquals(expected, (byte[]) e.get("buffer"));
    }

    @Test
    void getAllIsTheWorldEntitySnapshotFromAnyThread() {
        List<Entity> all = new ArrayList<>();
        level.getEntities().getAll().forEach(all::add);
        assertEquals(Bukkit.getWorlds().get(0).getEntities().size(), all.size());
        assertTrue(all.contains(((org.bukkit.craftbukkit.entity.CraftPlayer) Compat.player(tp.player())).getHandle()), "플레이어도 든다");
    }

    @Test
    void fluidTickSpreadsTheGivenWaterLikeVanilla() {
        BlockPos p = new BlockPos(baseX + 4, Y, baseZ + 4);
        run(p, () -> {
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                level.setBlock(p.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 2 | 16);
                level.setBlock(p.offset(dx, 0, dz), Blocks.AIR.defaultBlockState(), 2 | 16);
            }
            level.setBlock(p, Blocks.WATER.defaultBlockState(), 2 | 16);
        });
        // 빈 유체는 아무것도 하지 않는다
        run(p, () -> Fluids.EMPTY.defaultFluidState().tick(level, p.east()));
        assertSame(Blocks.AIR, at(p.east()).getBlock());
        run(p, () -> Fluids.WATER.defaultFluidState().tick(level, p));
        for (BlockPos n : List.of(p.east(), p.west(), p.north(), p.south())) {
            FluidState f = at(n).getFluidState();
            assertSame(Fluids.FLOWING_WATER, f.getType(), "옆으로 흐른다 " + n);
            assertEquals(7, f.getAmount());
        }
        assertTrue(at(p.east().east()).isAir(), "한 번의 틱은 한 칸만");
        // 주인 region 이 아닌 스레드는 거절
        assertThrows(ThreadOwnershipException.class, () -> Fluids.WATER.defaultFluidState().tick(level, p));
    }

    @Test
    void blockTickRunsTheGivenStateNotTheWorldOne() {
        BlockPos p = new BlockPos(baseX + 10, Y, baseZ + 10);
        run(p, () -> {
            level.setBlock(p, Blocks.STONE.defaultBlockState(), 2 | 16);
            level.setBlock(p.above(), Blocks.STONE.defaultBlockState(), 2 | 16);
        });
        // 바닐라 FarmBlock.tick: 위가 막혀 살 수 없으면 흙으로 — 칸의 지금 상태(돌)가 아니라 받은 상태(경작지)의 동작이다
        run(p, () -> Blocks.FARMLAND.defaultBlockState().tick(level, p, level.getRandom()));
        assertSame(Blocks.DIRT, at(p).getBlock());
        assertThrows(ThreadOwnershipException.class, () -> Blocks.FARMLAND.defaultBlockState().tick(level, p, level.getRandom()));
        BlockPos far = new BlockPos(baseX + 100_000, Y, baseZ);
        assertThrows(IllegalStateException.class, () -> Blocks.FARMLAND.defaultBlockState().tick(level, far, level.getRandom()), "올라와 있지 않은 청크");
        assertFalse(at(p).isAir());
    }
}
