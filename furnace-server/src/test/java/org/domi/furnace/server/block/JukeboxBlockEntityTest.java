// 주크박스 블록 엔티티 시험: 넣기(has_record·1010)·재생 신호 15·비교기 출력·길이+20틱 뒤 자동 정지(1011)·꺼내기(난수 순서)·저장 왕복.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.entity.JukeboxBlockEntity;
import org.domi.furnace.server.block.types.JukeboxBlock;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.item.JukeboxSongs;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class JukeboxBlockEntityTest {

    static final class Lvl extends BlockPlacementTest.TestLevel {
        final List<int[]> events = new ArrayList<>();
        final List<double[]> items = new ArrayList<>();
        /** nextFloat 만 순서대로 0.1, 0.2, 0.3 … 을 내는 난수(호출 순서 검증용). */
        final RandomSource scripted = new RandomSource() {
            int n;
            public int nextInt() { return 0; }
            public int nextInt(int bound) { return 0; }
            public long nextLong() { return 0; }
            public float nextFloat() { return 0.1F * ++n; }
            public double nextDouble() { return 0; }
            public boolean nextBoolean() { return false; }
            public double nextGaussian() { return 0; }
        };
        @Override public RandomSource random() { return scripted; }
        @Override public void levelEvent(BlockActor except, int type, Pos pos, int data) { events.add(new int[] {type, data}); }
        @Override public void spawnItemEntity(double x, double y, double z, ItemStack s) { items.add(new double[] {x, y, z}); }
    }

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
        JukeboxSongs.put("minecraft:13", 178.0F, 1);
    }

    static ItemStack disc() { return new ItemStack(ItemContext.id("minecraft:item", "minecraft:music_disc_13"), 1); }

    @Test
    void insertPlayComparatorAndFinish() {
        assumeTrue(JukeboxSongs.of(disc()) != null, "아이템 기본 컴포넌트 없음");
        Lvl level = new Lvl();
        Pos pos = new Pos(1, -60, 1);
        int off = B.defaultState("minecraft:jukebox");
        level.setBlock(pos, off, 3);
        var be = new JukeboxBlockEntity(pos, off);
        level.setBlockEntity(be);
        var block = (JukeboxBlock) B.behavior(off);
        assertEquals(0, block.getAnalogOutputSignal(off, level, pos));
        be.insert(disc(), null, level);
        int on = level.getBlockState(pos);
        assertTrue(B.prop("has_record").is(on));
        assertTrue(be.isPlaying());
        assertEquals(15, block.getSignal(on, level, pos, 0));
        assertEquals(1, block.getAnalogOutputSignal(on, level, pos));
        assertEquals(1010, level.events.get(0)[0]);
        int total = 178 * 20 + 20;
        for (int i = 0; i < total; i++) be.tick(level);
        assertTrue(be.isPlaying(), "길이+20 틱 전까지는 재생 중");
        be.tick(level);
        assertFalse(be.isPlaying());
        assertEquals(1011, level.events.get(level.events.size() - 1)[0]);
        assertEquals(0, block.getSignal(on, level, pos, 0));
        assertEquals(1, block.getAnalogOutputSignal(on, level, pos), "음반은 그대로라 비교기 출력은 남는다");
    }

    @Test
    void popOutUsesVanillaRandomOrderAndClearsState() {
        assumeTrue(JukeboxSongs.of(disc()) != null, "아이템 기본 컴포넌트 없음");
        Lvl level = new Lvl();
        Pos pos = new Pos(1, -60, 1);
        int off = B.defaultState("minecraft:jukebox");
        level.setBlock(pos, off, 3);
        var be = new JukeboxBlockEntity(pos, off);
        level.setBlockEntity(be);
        be.insert(disc(), null, level);
        be.popOutTheItem(level);
        assertTrue(be.theItem().isEmpty());
        assertFalse(be.isPlaying());
        assertFalse(B.prop("has_record").is(level.getBlockState(pos)));
        assertEquals(1011, level.events.get(level.events.size() - 1)[0]);
        // 위치 난수는 x, y, z 순서로 nextFloat 세 번(스크립트 난수의 첫 세 값)
        double ex = 1.5 + (0.1F - 0.5F) * 0.7F, ey = -60 + 1.01 + (0.2F - 0.5F) * 0.7F, ez = 1.5 + (0.3F - 0.5F) * 0.7F;
        assertEquals(1, level.items.size());
        assertArrayEquals(new double[] {ex, ey, ez}, level.items.get(0));
    }

    @Test
    void saveRoundTripKeepsDiscAndProgress() {
        assumeTrue(JukeboxSongs.of(disc()) != null, "아이템 기본 컴포넌트 없음");
        Lvl level = new Lvl();
        Pos pos = new Pos(1, -60, 1);
        int off = B.defaultState("minecraft:jukebox");
        level.setBlock(pos, off, 3);
        var be = new JukeboxBlockEntity(pos, off);
        be.insert(disc(), null, level);
        for (int i = 0; i < 45; i++) be.tick(level);
        var back = new JukeboxBlockEntity(pos, level.getBlockState(pos));
        back.load(be.saveWithoutMetadata());
        assertEquals("minecraft:music_disc_13", ItemTools.itemName(back.theItem()));
        assertTrue(back.isPlaying());
        assertEquals(45, back.ticksSinceSongStarted());
    }
}
