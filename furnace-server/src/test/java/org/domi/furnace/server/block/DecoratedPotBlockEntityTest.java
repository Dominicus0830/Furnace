// 장식 항아리 시험: 넣기(같은 아이템만·가득 차면 거절)·소리 높이·흔들림 블록 이벤트·비교기 신호·꺼내 쏟기·드롭(금 간 것은 조각)·저장 왕복.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.entity.DecoratedPotBlockEntity;
import org.domi.furnace.server.block.types.DecoratedPotBlock;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DecoratedPotBlockEntityTest {

    static final class Lvl extends BlockPlacementTest.TestLevel {
        final List<Object[]> sounds = new ArrayList<>();
        final List<int[]> blockEvents = new ArrayList<>();
        final List<ItemStack> dropped = new ArrayList<>();
        @Override public void playSound(BlockActor except, Pos pos, String sound, String source, float volume, float pitch) { sounds.add(new Object[] {sound, pitch}); }
        @Override public void blockEvent(Pos pos, int block, int type, int data) { blockEvents.add(new int[] {type, data}); }
        @Override public void dropItemStack(double x, double y, double z, ItemStack s) { if (!s.isEmpty()) dropped.add(s); }
    }

    @BeforeAll
    static void install() { ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt"))); }

    static ItemStack item(String name, int n) { return new ItemStack(ItemContext.id("minecraft:item", "minecraft:" + name), n); }

    static final Pos POS = new Pos(1, -60, 1);

    @Test
    void insertSameItemOnlyUpToStackLimitWithSoundAndWobble() {
        Lvl level = new Lvl();
        int state = B.defaultState("minecraft:decorated_pot");
        level.setBlock(POS, state, 3);
        var block = (DecoratedPotBlock) B.behavior(state);
        var actor = new BlockPlacementTest.Actor();
        actor.hands[0] = item("stone", 70);
        Hit hit = new Hit(POS, Dir.UP, 1.5, -59, 1.5, false);
        var r = block.useItemOn(actor.hands[0], state, level, POS, actor, 0, hit);
        assertEquals(ItemInteractionResult.SUCCESS, r);
        var be = (DecoratedPotBlockEntity) level.getBlockEntity(POS);
        assertEquals(1, be.theItem().count());
        assertEquals(69, actor.hands[0].count());
        assertEquals("minecraft:block.decorated_pot.insert", level.sounds.get(0)[0]);
        assertEquals(0.7F + 0.5F * (1.0F / 64.0F), (float) level.sounds.get(0)[1], 1e-6);
        assertArrayEquals(new int[] {1, 0}, level.blockEvents.get(0));
        // 다른 아이템은 거절
        actor.hands[0] = item("dirt", 5);
        assertEquals(ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION, block.useItemOn(actor.hands[0], state, level, POS, actor, 0, hit));
        // 가득 찰 때까지
        for (int i = 0; i < 63; i++) {
            actor.hands[0] = item("stone", 10);
            assertEquals(ItemInteractionResult.SUCCESS, block.useItemOn(actor.hands[0], state, level, POS, actor, 0, hit));
        }
        assertEquals(64, be.theItem().count());
        assertEquals(15, block.getAnalogOutputSignal(state, level, POS));
        actor.hands[0] = item("stone", 10);
        assertEquals(ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION, block.useItemOn(actor.hands[0], state, level, POS, actor, 0, hit));
        assertEquals(1.2F, (float) level.sounds.get(level.sounds.size() - 1)[1], 1e-6, "마지막 소리는 가득 찼을 때 0.7 + 0.5");
    }

    @Test
    void emptyHandWobblesNegativeWithFailSoundAndSignalZero() {
        Lvl level = new Lvl();
        int state = B.defaultState("minecraft:decorated_pot");
        level.setBlock(POS, state, 3);
        var block = (DecoratedPotBlock) B.behavior(state);
        var actor = new BlockPlacementTest.Actor();
        assertEquals(InteractionResult.SUCCESS, block.useWithoutItem(state, level, POS, actor, new Hit(POS, Dir.UP, 1.5, -59, 1.5, false)));
        assertEquals("minecraft:block.decorated_pot.insert_fail", level.sounds.get(0)[0]);
        assertArrayEquals(new int[] {1, 1}, level.blockEvents.get(0));
        assertEquals(0, block.getAnalogOutputSignal(state, level, POS));
        var be = (DecoratedPotBlockEntity) level.getBlockEntity(POS);
        assertTrue(be.triggerEvent(1, 1));
        assertEquals(1, be.lastWobbleStyle);
        assertFalse(be.triggerEvent(2, 0));
    }

    @Test
    void removalSpillsContentsAndDropsMatchCrackedState() {
        Lvl level = new Lvl();
        int state = B.defaultState("minecraft:decorated_pot");
        level.setBlock(POS, state, 3);
        var be = (DecoratedPotBlockEntity) level.getBlockEntity(POS);
        be.setSherds(List.of("minecraft:angler_pottery_sherd", "minecraft:brick", "minecraft:arms_up_pottery_sherd", "minecraft:brick"));
        be.setTheItem(item("diamond", 7), level);
        // 드롭(금 간 단지 조각·안 금 간 단지 하나)은 바닐라 전리품 표가 정한다 — BlockEntityLootTest.decoratedPotDropsItemWithDecorationsOrFourSherdsWhenCracked
        // 블록이 사라지면 내용물이 쏟아진다
        level.setBlock(POS, B.defaultState("minecraft:air"), 3);
        assertEquals(1, level.dropped.size());
        assertEquals(7, level.dropped.get(0).count());
    }

    @Test
    void saveRoundTripKeepsItemAndSherds() {
        Lvl level = new Lvl();
        var be = new DecoratedPotBlockEntity(POS, B.defaultState("minecraft:decorated_pot"));
        assertFalse(be.saveWithoutMetadata().contains("sherds"), "기본(전부 벽돌)이면 쓰지 않는다");
        be.setSherds(List.of("minecraft:skull_pottery_sherd", "minecraft:brick", "minecraft:brick", "minecraft:brick"));
        be.setTheItem(item("apple", 12), level);
        var back = new DecoratedPotBlockEntity(POS, B.defaultState("minecraft:decorated_pot"));
        back.load(be.saveWithoutMetadata());
        assertEquals("minecraft:skull_pottery_sherd", back.sherds[0]);
        assertEquals("minecraft:apple", ItemTools.itemName(back.theItem()));
        assertEquals(12, back.theItem().count());
    }
}
