package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.shorts.ShortLinkedOpenHashSet;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.domi.furnace.compat.CompatTestPlugin;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * NMS shim 4번 묶음: Orebfuscator 의 1.21.1 Mojang 이름 어댑터가 타는 길의 작은 구멍들 — 상태 목록(ImmutableList), 블록 엔티티 블록인가, 레지스트리 태그 짝,
 * 청크 로드 여부·미래, 구역 블록 갱신 패킷의 Paper 생성자, 블록 엔티티 갱신 패킷.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsOrebfuscatorPathTest {

    FurnaceTestServer server;
    ServerLevel level;
    int cx, cz;
    org.domi.furnace.api.world.BlockPos at;
    /** 데이터 팩을 안 거는 시험 서버는 태그 표가 없다 — 태그 짝을 보려고 바닐라 표를 건다. */
    org.domi.furnace.server.net.registry.TagSet tagsBefore;

    @BeforeAll
    void start() {
        tagsBefore = org.domi.furnace.server.item.ItemContext.tagSet();
        if (tagsBefore == null) org.domi.furnace.server.item.ItemContext.installTags(org.domi.furnace.server.net.registry.TagSet.load(
                "registries/tags-" + org.domi.furnace.api.GameVersion.RESOURCE_TAG + ".txt"));
        server = FurnaceTestServer.builder().failFast(false).exposeCompatToNative(true).plugin(CompatTestPlugin.class).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        TestPlayer tp = server.join("Ore");
        server.tick(3);
        level = ((CraftWorld) Compat.player(tp.player()).getWorld()).getHandle();
        var loc = tp.player().location();
        cx = Math.floorDiv((int) Math.floor(loc.x()), 16);
        cz = Math.floorDiv((int) Math.floor(loc.z()), 16);
        at = new org.domi.furnace.api.world.BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()) + 6, (int) Math.floor(loc.z()));
    }

    @AfterAll
    void stop() {
        server.close();
        org.domi.furnace.server.item.ItemContext.installTags(tagsBefore);
    }

    @Test
    void possibleStatesAreAGuavaListInStateOrder() {
        var states = Blocks.OAK_STAIRS.getStateDefinition().getPossibleStates();
        assertInstanceOf(com.google.common.collect.ImmutableList.class, states);
        assertEquals(80, states.size(), "참나무 계단 상태 80개(바닐라)");
        assertSame(states, Blocks.OAK_STAIRS.getStateDefinition().getPossibleStates(), "한 번 만든 사본");
        for (int i = 1; i < states.size(); i++) assertEquals(states.get(0).furnaceStateId() + i, states.get(i).furnaceStateId(), "상태 번호 순서");
    }

    @Test
    void hasBlockEntityFollowsEntityBlock() {
        assertTrue(Blocks.CHEST.defaultBlockState().hasBlockEntity());
        assertTrue(Blocks.MOVING_PISTON.defaultBlockState().hasBlockEntity(), "바닐라: 움직이는 피스톤도 EntityBlock");
        assertFalse(Blocks.STONE.defaultBlockState().hasBlockEntity());
        assertFalse(Blocks.AIR.defaultBlockState().hasBlockEntity());
    }

    @Test
    void tagsPairEveryTagNameWithItsSet() {
        var pairs = BuiltInRegistries.BLOCK.getTags().toList();
        var pickaxe = pairs.stream().filter(p -> p.getFirst().location().toString().equals("minecraft:mineable/pickaxe")).findFirst().orElseThrow();
        assertTrue(pickaxe.getSecond().contains(Blocks.STONE.builtInRegistryHolder()));
        assertEquals(BuiltInRegistries.BLOCK.getTagNames().toList(), pairs.stream().map(p -> p.getFirst()).toList());
        for (var p : pairs) assertSame(p.getFirst(), p.getSecond().key());
    }

    @Test
    void chunkLoadedAndFuture() throws Exception {
        var cache = level.getChunkSource();
        assertTrue(cache.isChunkLoaded(cx, cz));
        assertFalse(cache.isChunkLoaded(5000, 5000));
        var f = cache.getChunkFuture(cx, cz, ChunkStatus.FULL, false);
        assertTrue(f.isDone(), "로드된 청크는 끝난 미래");
        var c = f.get().orElse(null);
        assertEquals(cx, c.getPos().x);
        assertEquals(cz, c.getPos().z);
        var far = cache.getChunkFuture(5000, 5000, ChunkStatus.FULL, false);
        for (int i = 0; i < 40 && !far.isDone(); i++) server.tick(1);
        assertFalse(far.get(1, TimeUnit.SECONDS).isSuccess(), "만들지 않으면 실패 결과");
    }

    @Test
    void sectionUpdateFromAShortSet() {
        var positions = new ShortLinkedOpenHashSet(new short[] {SectionPos.sectionRelativePos(new net.minecraft.core.BlockPos(1, 2, 3)), 0x0FFF});
        BlockState[] states = {Blocks.STONE.defaultBlockState(), Blocks.DIAMOND_ORE.defaultBlockState()};
        var pkt = new ClientboundSectionBlocksUpdatePacket(SectionPos.of(4, 5, 6), positions, states);
        Object[] v = pkt.schemaValues();
        assertEquals(SectionPos.of(4, 5, 6), v[0]);
        assertArrayEquals(positions.toShortArray(), (short[]) v[1], "칸 집합 순서 그대로");
        assertSame(states, v[2]);
    }

    /** 바닐라 getUpdatePacket: 표지판·신호기는 그 칸·종류·갱신 태그를 실은 패킷, 상자는 null. 주인 스레드·다른 스레드·떨어진 것 모두. */
    @Test
    void blockEntityUpdatePacket() throws Exception {
        var sign = new net.minecraft.core.BlockPos(at.x(), at.y(), at.z());
        var beacon = sign.above();
        var chest = beacon.above();
        String owner = server.onRegion(at, () -> {
            level.setBlock(sign, Blocks.OAK_SIGN.defaultBlockState(), 3);
            level.setBlock(beacon, Blocks.BEACON.defaultBlockState(), 3);
            level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
            assertEquals(null, level.getBlockEntity(chest).getUpdatePacket(), "바닐라 상자는 갱신 패킷을 내지 않는다");
            var b = (net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket) level.getBlockEntity(beacon).getUpdatePacket();
            assertEquals("minecraft:beacon", net.minecraft.world.level.block.entity.BlockEntityType.getKey(b.getType()).toString());
            assertEquals(beacon, b.getPos());
            var pkt = (net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket) level.getBlockEntity(sign).getUpdatePacket();
            assertEquals(sign, pkt.getPos());
            assertEquals("minecraft:sign", net.minecraft.world.level.block.entity.BlockEntityType.getKey(pkt.getType()).toString());
            assertTrue(pkt.getTag().contains("front_text") && pkt.getTag().contains("is_waxed"), pkt.getTag().toString());
            assertFalse(pkt.getTag().contains("id"), "갱신 태그에는 id·좌표가 없다");
            return pkt.getTag().toString();
        });
        server.tick(2);   // 다른 스레드가 보는 청크 거울은 틱 끝에 공개된다
        // 다른 스레드: 주인에게 물어 같은 태그
        var other = new java.util.concurrent.atomic.AtomicReference<Object>();
        var t = new Thread(() -> {
            try {
                var be = level.getChunkAt(sign).getBlockEntity(sign);
                other.set(((net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket) be.getUpdatePacket()).getTag().toString());
            } catch (Throwable e) {
                other.set(e);
            }
        });
        t.start();
        for (int i = 0; i < 200 && t.isAlive(); i++) { server.tick(1); t.join(50); }
        assertEquals(owner, other.get());
        // 떨어진(플러그인이 만든) 것
        var loose = ((net.minecraft.world.level.block.EntityBlock) Blocks.OAK_SIGN).newBlockEntity(net.minecraft.core.BlockPos.ZERO, Blocks.OAK_SIGN.defaultBlockState());
        var lp = (net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket) loose.getUpdatePacket();
        assertEquals(owner, lp.getTag().toString(), "같은 빈 표지판이면 같은 태그");
        var looseChest = ((net.minecraft.world.level.block.EntityBlock) Blocks.CHEST).newBlockEntity(net.minecraft.core.BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        assertEquals(null, looseChest.getUpdatePacket());
    }
}
