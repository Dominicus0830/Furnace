package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.item.ComponentPatch;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** 블록 부수기(ServerPlayerGameMode) — 기대값은 바닐라 공식(속도 / 경도 / 30·100, 경과 틱 + 1)을 손으로 계산했다. */
class BlockBreakingTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    static final Blocks B = Blocks.get();

    private static ItemStack stack(String item) { return new ItemStack(ItemContext.id("minecraft:item", item), 1); }

    private static ItemStack enchanted(String item, String ench, int level) {
        Map<String, Object> raw = Map.of("minecraft:enchantments", Map.of("levels", Map.of(ench, level)));
        return new ItemStack(ItemContext.id("minecraft:item", item), 1, ComponentPatch.of(raw));
    }

    private static Player player(GameMode mode, boolean onGround) {
        Player p = new Player(7, UUID.randomUUID(), "Breaker", null);
        p.vitals().loadGameTypes(null, mode, true);
        p.applyMove(0.5, -60, -1.5, 0, 30, onGround);
        return p;
    }

    private static BreakerActor actor(Player p, ItemStack hand) {
        return new BreakerActor(p, hand, ItemStack.EMPTY, ItemStack.EMPTY, 0, false, 36);
    }

    static final class Host implements BlockBreaking.Host {
        final List<Pos> resent = new ArrayList<>();
        public void sendBlockUpdate(BlockLevel level, Pos pos) { resent.add(pos); }
    }

    @Test
    void destroyProgress_followsToolAndHardness() {
        var level = new BlockPlacementTest.TestLevel();
        Pos pos = new Pos(0, -60, 0);
        int stone = B.defaultState("minecraft:stone");
        level.setBlock(pos, stone, 3);
        Player p = player(GameMode.SURVIVAL, true);
        BlockBehavior b = BlockBehaviors.of(stone);
        assertEquals(1.0F / 1.5F / 100.0F, b.getDestroyProgress(stone, actor(p, ItemStack.EMPTY), level, pos), 1e-7);
        assertEquals(8.0F / 1.5F / 30.0F, b.getDestroyProgress(stone, actor(p, stack("minecraft:diamond_pickaxe")), level, pos), 1e-7);
        // 효율 V: 8 + 5² + 1 = 34
        assertEquals(34.0F / 1.5F / 30.0F, b.getDestroyProgress(stone, actor(p, enchanted("minecraft:diamond_pickaxe", "minecraft:efficiency", 5)), level, pos), 1e-6);
        // 공중이면 ÷5
        Player air = player(GameMode.SURVIVAL, false);
        assertEquals(8.0F / 5.0F / 1.5F / 30.0F, b.getDestroyProgress(stone, actor(air, stack("minecraft:diamond_pickaxe")), level, pos), 1e-7);
        // 나무 곡괭이는 철광석 드롭 도구가 아니다(#incorrect_for_wooden_tool)
        int iron = B.defaultState("minecraft:iron_ore");
        assertFalse(actor(p, stack("minecraft:wooden_pickaxe")).hasCorrectToolForDrops(iron));
        assertTrue(actor(p, stack("minecraft:stone_pickaxe")).hasCorrectToolForDrops(iron));
        // 흑요석·기반암
        assertEquals(0.0F, BlockBehaviors.of(B.defaultState("minecraft:bedrock")).getDestroyProgress(B.defaultState("minecraft:bedrock"),
                actor(p, stack("minecraft:netherite_pickaxe")), level, pos));
    }

    @Test
    void survival_stopAfterEnoughTicks_destroys() {
        var level = new BlockPlacementTest.TestLevel();
        Pos pos = new Pos(0, -61, 0);   // 잔디(경도 0.6, 맨손 1/0.6/30 = 0.0556/틱)
        Player p = player(GameMode.SURVIVAL, true);
        var breaking = new BlockBreaking();
        var host = new Host();
        breaking.handleAction(level, actor(p, ItemStack.EMPTY), host, pos, BlockBreaking.START_DESTROY_BLOCK, 320, true);
        assertTrue(breaking.isDestroyingBlock());
        for (int i = 0; i < 12; i++) breaking.tick(level, () -> actor(p, ItemStack.EMPTY), host);
        // 13 × 0.0556 = 0.72 ≥ 0.7 → 바로 부서짐
        breaking.handleAction(level, actor(p, ItemStack.EMPTY), host, pos, BlockBreaking.STOP_DESTROY_BLOCK, 320, true);
        assertEquals("minecraft:air", B.describe(level.getBlockState(pos)));
        assertTrue(host.resent.isEmpty());
    }

    @Test
    void survival_stopTooEarly_finishesByDelayedDestroy() {
        var level = new BlockPlacementTest.TestLevel();
        Pos pos = new Pos(0, -61, 0);
        Player p = player(GameMode.SURVIVAL, true);
        var breaking = new BlockBreaking();
        var host = new Host();
        breaking.handleAction(level, actor(p, ItemStack.EMPTY), host, pos, BlockBreaking.START_DESTROY_BLOCK, 320, true);
        for (int i = 0; i < 3; i++) breaking.tick(level, () -> actor(p, ItemStack.EMPTY), host);
        breaking.handleAction(level, actor(p, ItemStack.EMPTY), host, pos, BlockBreaking.STOP_DESTROY_BLOCK, 320, true);
        assertTrue(breaking.hasDelayedDestroy());
        assertEquals("minecraft:grass_block[snowy=false]", B.describe(level.getBlockState(pos)));
        // 진행도 = (경과 틱 + 1) × 0.0556 이 1 이 되는 틱(경과 17 → 18 × 0.0556 = 1.0)까지 이어 계산
        int ticks = 0;
        while (breaking.hasDelayedDestroy() && ticks < 40) {
            breaking.tick(level, () -> actor(p, ItemStack.EMPTY), host);
            ticks++;
        }
        assertEquals("minecraft:air", B.describe(level.getBlockState(pos)));
        assertEquals(14, ticks, "지연 파괴는 진행도가 1 이 되는 틱(경과 17)에 끝난다");
    }

    @Test
    void creative_breaksAtStart_butNotWithSword() {
        var level = new BlockPlacementTest.TestLevel();
        Player p = player(GameMode.CREATIVE, true);
        var breaking = new BlockBreaking();
        var host = new Host();
        Pos a = new Pos(0, -61, 0), b = new Pos(1, -61, 0);
        breaking.handleAction(level, actor(p, ItemStack.EMPTY), host, a, BlockBreaking.START_DESTROY_BLOCK, 320, true);
        assertEquals("minecraft:air", B.describe(level.getBlockState(a)));
        breaking.handleAction(level, actor(p, stack("minecraft:diamond_sword")), host, b, BlockBreaking.START_DESTROY_BLOCK, 320, true);
        assertNotEquals("minecraft:air", B.describe(level.getBlockState(b)));
        assertEquals(List.of(b), host.resent);
    }

    @Test
    void survival_pickaxeLosesDurability() {
        var level = new BlockPlacementTest.TestLevel();
        Pos pos = new Pos(0, -60, 0);
        level.setBlock(pos, B.defaultState("minecraft:stone"), 3);
        Player p = player(GameMode.SURVIVAL, true);
        var breaking = new BlockBreaking();
        var host = new Host();
        // 효율 V 네더라이트(9 + 26 = 35 → 35/1.5/30 = 0.78 ≥ … 시작에서 1 미만) — 몇 틱 뒤 완료
        ItemStack pick = enchanted("minecraft:netherite_pickaxe", "minecraft:efficiency", 5);
        BreakerActor a = actor(p, pick);
        breaking.handleAction(level, a, host, pos, BlockBreaking.START_DESTROY_BLOCK, 320, true);
        breaking.tick(level, () -> actor(p, pick), host);
        BreakerActor stop = actor(p, pick);
        breaking.handleAction(level, stop, host, pos, BlockBreaking.STOP_DESTROY_BLOCK, 320, true);
        assertEquals("minecraft:air", B.describe(level.getBlockState(pos)));
        assertTrue(stop.handChanged(BlockActor.MAIN_HAND));
        assertEquals(1, ItemTools.damage(stop.itemInHand(BlockActor.MAIN_HAND)));
    }

    @Test
    void adventure_cannotBreakWithoutCanBreak() {
        var level = new BlockPlacementTest.TestLevel();
        Player p = player(GameMode.ADVENTURE, true);
        var breaking = new BlockBreaking();
        var host = new Host();
        Pos pos = new Pos(0, -61, 0);
        breaking.handleAction(level, actor(p, stack("minecraft:diamond_shovel")), host, pos, BlockBreaking.START_DESTROY_BLOCK, 320, true);
        assertFalse(breaking.isDestroyingBlock());
        assertEquals(List.of(pos), host.resent);
    }
}
