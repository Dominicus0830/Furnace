// Clearable.tryClear 시험(T-211 3단계): 바닐라 clearContent 종류별 — 담는 블록 엔티티는 칸만(전리품 표 그대로), 장식 단지는 전리품을 먼저 풀고,
// 주크박스는 재생을 멈추고(has_record 끄기·1011) 뒤이은 블록 바꾸기에서 음반을 떨구지 않으며, 독서대는 책·쪽만(has_book 그대로). 그 밖은 비울 수 없다.
package org.domi.furnace.server.block;

import org.domi.furnace.api.GameVersion;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.entity.BarrelBlockEntity;
import org.domi.furnace.server.block.entity.BellBlockEntity;
import org.domi.furnace.server.block.entity.Clearables;
import org.domi.furnace.server.block.entity.DecoratedPotBlockEntity;
import org.domi.furnace.server.block.entity.JukeboxBlockEntity;
import org.domi.furnace.server.block.entity.LecternBlockEntity;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.JukeboxSongs;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClearablesTest {

    static final class Lvl extends BlockPlacementTest.TestLevel {
        final List<Integer> events = new ArrayList<>();
        final List<ItemStack> spawned = new ArrayList<>();
        final List<Object> unpacked = new ArrayList<>();
        @Override public void levelEvent(BlockActor except, int type, Pos pos, int data) { events.add(type); }
        @Override public void spawnItemEntity(double x, double y, double z, ItemStack s) { spawned.add(s); }
        @Override public void unpackContainerLoot(Object container) {
            unpacked.add(container);
            if (container instanceof DecoratedPotBlockEntity pot) {   // 전리품 표 풀기의 흉내: 표를 지우고 칸을 채운다
                pot.lootTable = null;
                pot.items[0] = item("minecraft:diamond", 3);
            }
        }
    }

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt")));
        JukeboxSongs.put("minecraft:13", 178.0F, 1);
    }

    static ItemStack item(String id, int count) { return new ItemStack(ItemContext.id("minecraft:item", id), count); }

    @Test
    void containerClearsSlotsAndKeepsTheLootTable() {
        Lvl level = new Lvl();
        Pos pos = new Pos(1, -60, 1);
        int state = B.defaultState("minecraft:barrel");
        level.setBlock(pos, state, 3);
        var be = new BarrelBlockEntity(pos, state);
        level.setBlockEntity(be);
        be.items[0] = item("minecraft:stone", 5);
        be.items[26] = item("minecraft:dirt", 1);
        be.lootTable = "minecraft:chests/simple_dungeon";
        assertTrue(Clearables.isClearable(be));
        assertTrue(Clearables.tryClear(be, level));
        for (ItemStack s : be.items) assertTrue(s.isEmpty());
        assertEquals("minecraft:chests/simple_dungeon", be.lootTable, "바닐라 getItems().clear() 는 전리품 표를 건드리지 않는다");
        assertTrue(level.unpacked.isEmpty());
    }

    @Test
    void decoratedPotUnpacksItsLootBeforeEmptying() {
        Lvl level = new Lvl();
        Pos pos = new Pos(1, -60, 1);
        int state = B.defaultState("minecraft:decorated_pot");
        level.setBlock(pos, state, 3);
        var pot = new DecoratedPotBlockEntity(pos, state);
        level.setBlockEntity(pot);
        pot.lootTable = "minecraft:pots/trial_chambers/corridor";
        assertTrue(Clearables.tryClear(pot, level));
        assertEquals(List.of(pot), level.unpacked, "removeTheItem → splitTheItem 이 unpackLootTable(null) 을 먼저 부른다");
        assertNull(pot.lootTable);
        assertTrue(pot.theItem().isEmpty());
    }

    @Test
    void jukeboxStopsAndTheFollowingReplaceDropsNothing() {
        Lvl level = new Lvl();
        Pos pos = new Pos(1, -60, 1);
        int off = B.defaultState("minecraft:jukebox");
        level.setBlock(pos, off, 3);
        var be = new JukeboxBlockEntity(pos, off);
        level.setBlockEntity(be);
        ItemStack disc = item("minecraft:music_disc_13", 1);
        assumeTrue(JukeboxSongs.of(disc) != null, "아이템 기본 컴포넌트 없음");
        be.insert(disc, null, level);
        assertTrue(be.isPlaying());
        assertTrue(Clearables.tryClear(be, level));
        assertTrue(be.theItem().isEmpty());
        assertFalse(be.isPlaying());
        assertFalse(B.prop("has_record").is(level.getBlockState(pos)), "setTheItem(EMPTY) 가 has_record 를 끈다(flag 2)");
        assertEquals(1011, level.events.get(level.events.size() - 1));
        level.setBlock(pos, B.defaultState("minecraft:stone"), 3);   // /setblock 의 다음 걸음
        assertTrue(level.spawned.isEmpty(), "비운 뒤에는 onRemove 가 음반을 떨구지 않는다");
    }

    @Test
    void lecternDropsTheBookButKeepsHasBook() {
        Lvl level = new Lvl();
        Pos pos = new Pos(1, -60, 1);
        int state = B.prop("has_book").with(B.defaultState("minecraft:lectern"), true);
        level.setBlock(pos, state, 3);
        var be = new LecternBlockEntity(pos, state);
        level.setBlockEntity(be);
        be.setBook(item("minecraft:writable_book", 1), level);
        assertTrue(be.hasBook());
        assertTrue(Clearables.tryClear(be, level));
        assertFalse(be.hasBook());
        assertEquals(0, be.page());
        assertTrue(B.prop("has_book").is(level.getBlockState(pos)), "바닐라 setBook(EMPTY) 은 블록 상태를 바꾸지 않는다");
    }

    @Test
    void otherBlockEntitiesAreNotClearable() {
        Lvl level = new Lvl();
        Pos pos = new Pos(1, -60, 1);
        int state = B.defaultState("minecraft:bell");
        var bell = new BellBlockEntity(pos, state);
        assertFalse(Clearables.isClearable(bell));
        assertFalse(Clearables.tryClear(bell, level));
        assertFalse(Clearables.tryClear(null, level));
    }
}
