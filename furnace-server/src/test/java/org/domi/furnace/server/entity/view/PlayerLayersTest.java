// 플레이어별 가상 블록·바이옴 층·개인 보더·장비 덮어쓰기가 나가는 패킷에 덧입혀지는지: 청크 본문 다시 쓰기, 블록 변경 패킷, 청크 재전송 유지.
package org.domi.furnace.server.entity.view;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.entity.EquipmentSlot;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.play.AddEntity;
import org.domi.furnace.server.net.protocol.play.BlockUpdate;
import org.domi.furnace.server.net.protocol.play.ChunkSerializer;
import org.domi.furnace.server.net.protocol.play.ChunksBiomes;
import org.domi.furnace.server.net.protocol.play.InitializeBorder;
import org.domi.furnace.server.net.protocol.play.LevelChunkWithLight;
import org.domi.furnace.server.net.protocol.play.Respawn;
import org.domi.furnace.server.net.protocol.play.SectionBlocksUpdate;
import org.domi.furnace.server.net.protocol.play.SetBorderSize;
import org.domi.furnace.server.net.protocol.play.SetEquipment;
import org.domi.furnace.server.world.ChunkSection;
import org.domi.furnace.server.world.ChunkSnapshot;
import org.domi.furnace.server.world.WorldBorder;
import org.junit.jupiter.api.Test;

class PlayerLayersTest {

    private static final int MIN_Y = -64, HEIGHT = 384, SECTIONS = HEIGHT / 16, BLOCK_BITS = 15, BIOME_BITS = 6;
    private static final BlockLayer.Dim DIM = new BlockLayer.Dim(MIN_Y, HEIGHT, BLOCK_BITS, BIOME_BITS);
    private static final String OVERWORLD = "minecraft:overworld";
    private static final Object PLUGIN = "plugin-a", OTHER = "plugin-b";
    private static final int STONE = 1, DIRT = 10, GLASS = 5000, PLAIN = 3, DESERT = 7;

    /** 모든 섹션이 돌이고 아래 4칸 층만 흙, 바이옴은 평원인 청크(cx, cz). */
    private static ChunkSnapshot snapshot(int cx, int cz) {
        ChunkSnapshot.Section[] secs = new ChunkSnapshot.Section[SECTIONS];
        for (int s = 0; s < SECTIONS; s++) {
            int[] blocks = new int[4096];
            java.util.Arrays.fill(blocks, s == 0 ? DIRT : s < 8 ? STONE : 0);
            int[] biomes = new int[64];
            java.util.Arrays.fill(biomes, PLAIN);
            int count = 0;
            for (int b : blocks) if (!ChunkSection.isAir(b)) count++;
            secs[s] = new ChunkSnapshot.Section((short) count, blocks, biomes);
        }
        return new ChunkSnapshot(cx, cz, new long[37], new long[37], secs, List.of(), null, null);
    }

    private static byte[] body(ChunkSnapshot snap) {
        ByteBuf buf = Unpooled.buffer();
        ChunkSerializer.serialize(buf, snap, BLOCK_BITS, BIOME_BITS, MIN_Y, true);
        byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        buf.release();
        return out;
    }

    /** 본문의 섹션마다 (비공기 수, 블록 4096, 바이옴 64)를 푼다. */
    private record Decoded(int[] nonAir, int[][] blocks, int[][] biomes, int restLength) {}

    private static Decoded decode(byte[] body) {
        ByteBuf in = Unpooled.wrappedBuffer(body);
        in.skipBytes(8);
        assertEquals(10, in.readByte());
        while (true) {
            byte t = in.readByte();
            if (t == 0) break;
            assertEquals(12, t);
            in.skipBytes(in.readUnsignedShort());
            in.skipBytes(in.readInt() * 8);
        }
        int len = Types.readVarInt(in);
        int end = in.readerIndex() + len;
        List<Integer> na = new ArrayList<>();
        List<int[]> bl = new ArrayList<>(), bi = new ArrayList<>();
        while (in.readerIndex() < end) {
            na.add((int) in.readShort());
            bl.add(ChunkBodyRewriter.readContainer(in, 4096, 8));
            bi.add(ChunkBodyRewriter.readContainer(in, 64, 3));
        }
        assertEquals(end, in.readerIndex());
        Decoded d = new Decoded(na.stream().mapToInt(i -> i).toArray(), bl.toArray(new int[0][]), bi.toArray(new int[0][]), body.length - end);
        in.release();
        return d;
    }

    private static int idx(int x, int y, int z) { return ((y - MIN_Y) & 15) << 8 | (z & 15) << 4 | (x & 15); }

    private static List<int[]> cells(int... xyzState) {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < xyzState.length; i += 4) out.add(new int[] {xyzState[i], xyzState[i + 1], xyzState[i + 2], xyzState[i + 3]});
        return out;
    }

    private static Player player() {
        Player p = new Player(910_000 + (int) (Math.random() * 1000), UUID.randomUUID(), "P", new org.domi.furnace.server.InlineEmbeddedChannel());
        p.viewRewriter().rewrite(p, respawn(OVERWORLD));   // 지금 차원을 알린다
        return p;
    }

    private static Respawn respawn(String dim) {
        return new Respawn(0, dim, 0L, 0, -1, false, null, null, 0, Respawn.KEEP_ALL);
    }

    // ------------------------------------------------------------------ 청크 본문

    @Test
    void chunkBodyCarriesBlockAndBiomeOverrides_otherCellsAndSectionsUntouched() {
        byte[] original = body(snapshot(2, -3));
        BlockLayer layer = new BlockLayer();
        // 청크 (2,-3): 월드 x = 32..47, z = -48..-33. y=-64(섹션 0)의 흙을 유리로, y=0(섹션 4)의 돌을 공기로
        layer.putBlocks(PLUGIN, OVERWORLD, DIM, cells(33, -64, -40, GLASS, 40, 0, -47, 0));
        layer.putBiomes(PLUGIN, OVERWORLD, DIM, cells(36, 100, -36, DESERT));   // 섹션 10 의 바이옴 칸 하나
        byte[] out = ChunkBodyRewriter.rewrite(original, DIM, layer.chunk(OVERWORLD, 2, -3));
        assertNotNull(out);

        Decoded before = decode(original), after = decode(out);
        assertEquals(before.restLength, after.restLength, "블록 엔티티·빛 부분은 그대로");
        assertEquals(GLASS, after.blocks[0][idx(33, -64, -40)]);
        assertEquals(0, after.blocks[4][idx(40, 0, -47)]);
        assertEquals(4095, after.nonAir[4], "공기가 하나 늘었으니 비공기 수가 하나 줄었다");
        assertEquals(4096, after.nonAir[0]);
        for (int s = 0; s < SECTIONS; s++) {
            for (int i = 0; i < 4096; i++) {
                boolean changed = (s == 0 && i == idx(33, -64, -40)) || (s == 4 && i == idx(40, 0, -47));
                if (!changed) assertEquals(before.blocks[s][i], after.blocks[s][i], "섹션 " + s + " 칸 " + i);
            }
        }
        // 바이옴 칸: x=36 → qx=(36&15)>>2=1, z=-36 → (z&15)=12 → qz=3, y=100 → (100+64)>>2=41 → 섹션 10, qy=1
        int biomeIdx = (1 << 4) | (3 << 2) | 1;
        assertEquals(DESERT, after.biomes[10][biomeIdx]);
        assertEquals(PLAIN, after.biomes[10][0]);
        assertEquals(PLAIN, after.biomes[3][biomeIdx]);
    }

    @Test
    void untouchedSectionsKeepTheirBytes() {
        byte[] original = body(snapshot(0, 0));
        BlockLayer layer = new BlockLayer();
        layer.putBlocks(PLUGIN, OVERWORLD, DIM, cells(1, 100, 1, GLASS));
        byte[] out = ChunkBodyRewriter.rewrite(original, DIM, layer.chunk(OVERWORLD, 0, 0));
        assertNotNull(out);
        // 앞부분(청크 좌표·높이맵)과 섹션 0 의 바이트가 같다
        int prefix = 8 + 1 + (1 + 2 + "MOTION_BLOCKING".length() + 4 + 37 * 8) + (1 + 2 + "WORLD_SURFACE".length() + 4 + 37 * 8) + 1;
        assertEquals(Unpooled.wrappedBuffer(original, 0, prefix), Unpooled.wrappedBuffer(out, 0, prefix));
    }

    @Test
    void overridesToManyDifferentStatesFallBackToDirectPalette() {
        byte[] original = body(snapshot(0, 0));
        BlockLayer layer = new BlockLayer();
        List<int[]> many = new ArrayList<>();
        for (int i = 0; i < 300; i++) many.add(new int[] {i & 15, 20 + (i >> 8), (i >> 4) & 15, 100 + i});   // 서로 다른 상태 300 개 → 간접 한도(256) 초과
        layer.putBlocks(PLUGIN, OVERWORLD, DIM, many);
        byte[] out = ChunkBodyRewriter.rewrite(original, DIM, layer.chunk(OVERWORLD, 0, 0));
        assertNotNull(out);
        Decoded d = decode(out);
        for (int[] c : many) assertEquals(c[3], d.blocks[(c[1] + 64) >> 4][idx(c[0], c[1], c[2])]);
    }

    @Test
    void emptyLayerOrBrokenBodyGivesNull() {
        byte[] original = body(snapshot(0, 0));
        assertNull(ChunkBodyRewriter.rewrite(original, DIM, null));
        BlockLayer layer = new BlockLayer();
        layer.putBlocks(PLUGIN, OVERWORLD, DIM, cells(1, 1, 1, GLASS));
        assertNull(ChunkBodyRewriter.rewrite(new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 5}, DIM, layer.chunk(OVERWORLD, 0, 0)), "높이맵이 아닌 본문은 손대지 않는다");
        assertNull(ChunkBodyRewriter.rewrite(java.util.Arrays.copyOf(original, 60), DIM, layer.chunk(OVERWORLD, 0, 0)), "잘린 본문");
    }

    // ------------------------------------------------------------------ 저장소

    @Test
    void layerOwnershipAndRemoval() {
        BlockLayer layer = new BlockLayer();
        assertFalse(layer.active());
        layer.putBlocks(PLUGIN, OVERWORLD, DIM, cells(1, 1, 1, GLASS, 2, 2, 2, GLASS));
        layer.putBlocks(OTHER, OVERWORLD, DIM, cells(1, 1, 1, DIRT));   // 같은 칸 — 마지막 쓴 쪽이 주인
        assertTrue(layer.active());
        assertEquals(DIRT, layer.block(OVERWORLD, 1, 1, 1).value());
        assertNull(layer.block(OVERWORLD, 9, 9, 9));
        assertNull(layer.block(OVERWORLD, 1, 1000, 1), "높이 밖");

        var r = layer.removeBlocks(PLUGIN, OVERWORLD, List.of(new int[] {1, 1, 1}, new int[] {2, 2, 2}));
        assertEquals(1, r.blocks().size(), "(1,1,1) 은 OTHER 가 주인이라 걷지 않는다");
        assertEquals(DIRT, layer.block(OVERWORLD, 1, 1, 1).value());
        assertNull(layer.block(OVERWORLD, 2, 2, 2));

        layer.putBiomes(PLUGIN, OVERWORLD, DIM, cells(0, 0, 0, DESERT));
        var all = layer.removeAll(PLUGIN);
        assertEquals(1, all.get(OVERWORLD).biomeChunks().size());
        assertTrue(layer.active(), "OTHER 의 층이 남았다");
        layer.removeAll(OTHER);
        assertFalse(layer.active());
        assertNull(layer.chunk(OVERWORLD, 0, 0));
    }

    // ------------------------------------------------------------------ ViewRewriter

    @Test
    void chunkResendKeepsTheOverride_andBlockUpdatesAreRewritten() {
        Player p = player();
        p.viewLayers().blocks.putBlocks(PLUGIN, OVERWORLD, DIM, cells(5, 10, 5, GLASS));
        byte[] real = body(snapshot(0, 0));

        for (int resend = 0; resend < 3; resend++) {   // 청크를 몇 번 다시 보내도 같은 모습
            Object r = p.viewRewriter().rewrite(p, LevelChunkWithLight.ofShared(real));
            LevelChunkWithLight sent = (LevelChunkWithLight) r;
            assertNull(sent.shared(), "덧입힌 본문은 공유 압축 프레임에 올리지 않는다");
            byte[] body = new byte[sent.body().readableBytes()];
            sent.body().getBytes(sent.body().readerIndex(), body);
            assertEquals(GLASS, decode(body).blocks[(10 + 64) >> 4][idx(5, 10, 5)]);
        }
        // 층이 없는 청크는 그대로(같은 객체)
        LevelChunkWithLight other = LevelChunkWithLight.ofShared(body(snapshot(7, 7)));
        assertSame(other, p.viewRewriter().rewrite(p, other));

        // 서버가 같은 칸을 진짜 블록으로 바꿔도 층이 이긴다
        BlockUpdate bu = (BlockUpdate) p.viewRewriter().rewrite(p, new BlockUpdate(5, 10, 5, STONE));
        assertEquals(GLASS, bu.stateId());
        BlockUpdate untouched = new BlockUpdate(6, 10, 5, STONE);
        assertSame(untouched, p.viewRewriter().rewrite(p, untouched));

        // 구역 변경 묶음: 층 칸만 바뀐다
        short[] pos = {(short) ((5 << 8) | (5 << 4) | 10), (short) ((6 << 8) | (5 << 4) | 10)};
        SectionBlocksUpdate su = (SectionBlocksUpdate) p.viewRewriter().rewrite(p, new SectionBlocksUpdate(0, 0, 0, pos, new int[] {STONE, STONE}));
        assertArrayEquals(new int[] {GLASS, STONE}, su.states());
    }

    @Test
    void layerFollowsTheClientsDimension() {
        Player p = player();
        p.viewLayers().blocks.putBlocks(PLUGIN, OVERWORLD, DIM, cells(5, 10, 5, GLASS));
        BlockUpdate bu = new BlockUpdate(5, 10, 5, STONE);
        assertNotSame(bu, p.viewRewriter().rewrite(p, bu));
        p.viewRewriter().rewrite(p, respawn("minecraft:the_nether"));
        assertSame(bu, p.viewRewriter().rewrite(p, bu), "다른 차원에서는 이 층이 닿지 않는다");
        p.viewRewriter().rewrite(p, respawn(OVERWORLD));
        assertNotSame(bu, p.viewRewriter().rewrite(p, bu));
    }

    @Test
    void biomePacketGetsOverrides_withoutTouchingTheOriginal() {
        Player p = player();
        p.viewLayers().blocks.putBiomes(PLUGIN, OVERWORLD, DIM, cells(4, -60, 4, DESERT));   // 섹션 0, 칸 (1,0,1)
        int[][] secs = new int[SECTIONS][64];
        for (int[] s : secs) java.util.Arrays.fill(s, PLAIN);
        var packet = new ChunksBiomes(List.of(new ChunksBiomes.Entry(0, 0, secs), new ChunksBiomes.Entry(5, 5, secs)), BIOME_BITS);
        ChunksBiomes out = (ChunksBiomes) p.viewRewriter().rewrite(p, packet);
        assertEquals(DESERT, out.chunks().get(0).sectionBiomes()[0][(1 << 4) | (1 << 2) | 1]);
        assertEquals(PLAIN, secs[0][(1 << 4) | (1 << 2) | 1], "원본(다른 사람과 같이 쓰는 패킷)은 그대로");
        assertSame(secs, out.chunks().get(1).sectionBiomes(), "층 없는 청크는 같은 배열");
    }

    // ------------------------------------------------------------------ 보더

    @Test
    void personalBorderReplacesTheServerBorderPackets() {
        Player p = player();
        InitializeBorder server = InitializeBorder.vanillaDefault();
        assertSame(server, p.viewRewriter().rewrite(p, server), "개인 보더가 없으면 그대로");

        WorldBorder own = new WorldBorder();
        own.setCenter(100, 200);
        own.setSize(50);
        p.viewLayers().border(own, PLUGIN);
        InitializeBorder out = (InitializeBorder) p.viewRewriter().rewrite(p, InitializeBorder.vanillaDefault());
        assertEquals(100, out.centerX());
        assertEquals(200, out.centerZ());
        assertEquals(50, out.newSize());
        assertNull(p.viewRewriter().rewrite(p, new SetBorderSize(1000)), "서버 보더 변경은 가지 않는다");

        p.viewLayers().border(null, null);
        SetBorderSize s = new SetBorderSize(1000);
        assertSame(s, p.viewRewriter().rewrite(p, s));
    }

    @Test
    void enforcedBorderOnlyWhenAsked() {
        Player p = player();
        WorldBorder own = new WorldBorder();
        p.viewLayers().border(own, PLUGIN);
        assertNull(p.viewLayers().enforcedBorder(), "보이기만 — 기본");
        p.viewLayers().borderEnforced(true);
        assertSame(own, p.viewLayers().enforcedBorder());
        p.viewLayers().border(null, null);
        assertNull(p.viewLayers().enforcedBorder(), "걷으면 enforce 도 끝");
    }

    // ------------------------------------------------------------------ 장비

    @Test
    void equipmentOverridesApplyToChangesAndFollowSpawn() {
        Player p = player();
        ItemStack helmet = new ItemStack(1, 1);
        p.viewLayers().equipment.put(PLUGIN, 77, "target", EquipmentSlot.HEAD, helmet);

        // 서버가 머리 칸을 바꾸면 덮어쓴 값이 간다, 다른 칸은 그대로
        ItemStack real = ItemStack.EMPTY;
        SetEquipment out = (SetEquipment) p.viewRewriter().rewrite(p, new SetEquipment(77, List.of(
                new SetEquipment.Entry(EquipmentSlot.HEAD, real), new SetEquipment.Entry(EquipmentSlot.MAINHAND, real))));
        assertEquals(helmet, out.slots().get(0).stack());
        assertEquals(real, out.slots().get(1).stack());
        SetEquipment otherEntity = new SetEquipment(78, List.of(new SetEquipment.Entry(EquipmentSlot.HEAD, real)));
        assertSame(otherEntity, p.viewRewriter().rewrite(p, otherEntity));

        // 스폰 뒤에 덮어쓴 장비가 붙는다(서버가 빈 장비라 따로 안 보내도)
        Object spawned = p.viewRewriter().rewrite(p, new AddEntity(77, UUID.randomUUID(), org.domi.furnace.server.entity.EntityType.registryId("minecraft:zombie"),
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        Packet[] pkts = (Packet[]) spawned;
        assertInstanceOf(AddEntity.class, pkts[0]);
        SetEquipment extra = (SetEquipment) pkts[pkts.length - 1];
        assertEquals(77, extra.entityId());
        assertEquals(helmet, extra.slots().get(0).stack());

        assertTrue(p.viewLayers().equipment.remove(PLUGIN, 77, EquipmentSlot.HEAD));
        assertFalse(p.viewLayers().equipment.active());
        assertFalse(p.viewLayers().equipment.remove(PLUGIN, 77, EquipmentSlot.HEAD));
    }

    @Test
    void inactiveLayersCostNothing() {
        Player p = player();
        assertFalse(p.viewLayers().active());
        BlockUpdate bu = new BlockUpdate(5, 10, 5, STONE);
        assertSame(bu, p.viewRewriter().rewrite(p, bu));
    }
}
