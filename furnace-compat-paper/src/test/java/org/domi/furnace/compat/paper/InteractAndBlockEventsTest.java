package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.bukkit.block.BlockFace;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.player.PlayerInteractEvent;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.world.BlockSnapshot;
import org.domi.furnace.compat.paper.fixtures.Jars;
import org.domi.furnace.compat.paper.fixtures.PaperDetectPlugin;
import org.domi.furnace.compat.paper.fixtures.RecorderPlugin;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** FAWE 묶음: Paper 판별 클래스, PlayerInteractEvent 의 두 결과 ↔ Furnace 취소, 블록·화로 이벤트 되돌려 쓰기, 아직 안 알리는 이벤트 등록. */
class InteractAndBlockEventsTest {

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() {
        RecorderPlugin.reset();
        PaperDetectPlugin.LOG.clear();
    }

    private Path recorderJar() throws Exception {
        return Jars.bukkit(tmp.resolve("recorder.jar"), """
                name: Recorder
                version: '1'
                main: org.domi.furnace.compat.paper.fixtures.RecorderPlugin
                api-version: '1.21'
                """, Map.of("config.yml", "a: 1\n"), RecorderPlugin.class);
    }

    private static final String W = "minecraft:overworld";
    private static final BlockPos POS = new BlockPos(1, 64, 2);

    private static PlayerInteractEvent click(Player p, PlayerInteractEvent.Action a, ItemStack item) {
        boolean block = a == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || a == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK;
        return new PlayerInteractEvent(p, a, item, block ? new BlockSnapshot(W, POS, BlockData.of("minecraft:stone")) : null, block ? "north" : null, true);
    }

    private static org.bukkit.event.player.PlayerInteractEvent lastInteract() {
        org.bukkit.event.player.PlayerInteractEvent last = null;
        for (var e : RecorderPlugin.SEEN) if (e instanceof org.bukkit.event.player.PlayerInteractEvent i) last = i;
        assertNotNull(last, "PlayerInteractEvent 가 처리기에 와야 한다");
        return last;
    }

    @Test
    void paperIsDetectedByClassPresence() throws Exception {
        Path jar = Jars.bukkit(tmp.resolve("detect.jar"), """
                name: Detect
                version: '1'
                main: org.domi.furnace.compat.paper.fixtures.PaperDetectPlugin
                api-version: '1.21'
                """, Map.of(), PaperDetectPlugin.class);
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(jar).start()) {
            assertEquals(List.of("paper=true"), PaperDetectPlugin.LOG);
        }
    }

    @Test
    void airClickLooksLikeCraftBukkit() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            Player p = server.join("Dana").player();
            server.tick(2);
            var f = server.api().events().call(click(p, PlayerInteractEvent.Action.RIGHT_CLICK_AIR, ItemStack.EMPTY));
            var b = lastInteract();
            assertEquals(Action.RIGHT_CLICK_AIR, b.getAction());
            assertNull(b.getClickedBlock());
            assertNull(b.getItem(), "빈 손이면 null");
            assertEquals(BlockFace.SOUTH, b.getBlockFace(), "공기 클릭의 면은 SOUTH");
            assertEquals(Result.DENY, b.useInteractedBlock());
            assertEquals(Result.DEFAULT, b.useItemInHand());
            assertTrue(b.isCancelled(), "Bukkit: 블록이 없으면 처음부터 isCancelled()");
            assertFalse(f.isCancelled(), "아무것도 막지 않았으니 Furnace 쪽은 그대로 진행");
        }
    }

    @Test
    void interactResultsFoldIntoFurnaceCancel() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            Player p = server.join("Dana").player();
            server.tick(2);
            var bus = server.api().events();
            var stick = ItemStack.of("minecraft:stick");

            RecorderPlugin.ACTIONS.put("PlayerInteractEvent", e -> ((org.bukkit.event.player.PlayerInteractEvent) e).setCancelled(true));
            assertTrue(bus.call(click(p, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, stick)).isCancelled());
            var b = lastInteract();
            assertEquals(EquipmentSlot.HAND, b.getHand());
            assertEquals(BlockFace.NORTH, b.getBlockFace());
            assertEquals(org.bukkit.Material.STICK, b.getMaterial());

            // 아이템만 막아도 오른쪽 블록 클릭은 막는다(Furnace 는 둘을 나눠 하지 못해 더 막는 쪽)
            RecorderPlugin.ACTIONS.put("PlayerInteractEvent", e -> ((org.bukkit.event.player.PlayerInteractEvent) e).setUseItemInHand(Result.DENY));
            assertTrue(bus.call(click(p, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, stick)).isCancelled());
            // 왼쪽 블록 클릭: CraftBukkit 은 생존·모험 모드에서 아이템 DENY 로도 캐기 시작을 막고, 창조 모드는 그 검사 전에 부순다
            p.setGameMode(org.domi.furnace.api.entity.GameMode.SURVIVAL);
            assertTrue(bus.call(click(p, PlayerInteractEvent.Action.LEFT_CLICK_BLOCK, stick)).isCancelled());
            p.setGameMode(org.domi.furnace.api.entity.GameMode.CREATIVE);
            assertFalse(bus.call(click(p, PlayerInteractEvent.Action.LEFT_CLICK_BLOCK, stick)).isCancelled());
            p.setGameMode(org.domi.furnace.api.entity.GameMode.SURVIVAL);
            // 공기 클릭은 아이템 쓰기만 본다
            assertTrue(bus.call(click(p, PlayerInteractEvent.Action.RIGHT_CLICK_AIR, stick)).isCancelled());

            RecorderPlugin.ACTIONS.put("PlayerInteractEvent", e -> ((org.bukkit.event.player.PlayerInteractEvent) e).setUseInteractedBlock(Result.DENY));
            assertTrue(bus.call(click(p, PlayerInteractEvent.Action.LEFT_CLICK_BLOCK, stick)).isCancelled());

            // 앞선 Furnace 처리기가 취소한 클릭은 Bukkit 에서 두 결과 모두 DENY 로 보인다
            RecorderPlugin.ACTIONS.clear();
            var ev = click(p, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, stick);
            ev.setCancelled(true);
            assertTrue(bus.call(ev).isCancelled());
            assertEquals(Result.DENY, lastInteract().useItemInHand());
            assertEquals(Result.DENY, lastInteract().useInteractedBlock());
        }
    }

    @Test
    void blockAndFurnaceValuesWriteBack() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            server.tick(2);
            var bus = server.api().events();
            RecorderPlugin.ACTIONS.put("BlockRedstoneEvent", e -> ((org.bukkit.event.block.BlockRedstoneEvent) e).setNewCurrent(3));
            assertEquals(3, bus.call(new org.domi.furnace.api.event.block.BlockRedstoneEvent(W, POS, "minecraft:redstone_wire", 0, 15)).newCurrent());

            RecorderPlugin.ACTIONS.put("FurnaceBurnEvent", e -> {
                var fb = (org.bukkit.event.inventory.FurnaceBurnEvent) e;
                assertEquals(org.bukkit.Material.COAL, fb.getFuel().getType());
                fb.setBurnTime(40);
                fb.setBurning(false);
            });
            var burn = bus.call(new org.domi.furnace.api.event.inventory.FurnaceBurnEvent(W, POS, "minecraft:furnace", ItemStack.of("minecraft:coal"), 1600));
            assertEquals(40, burn.burnTime());
            assertFalse(burn.isBurning());

            RecorderPlugin.ACTIONS.put("FurnaceSmeltEvent", e -> ((org.bukkit.event.block.BlockCookEvent) e).setResult(new org.bukkit.inventory.ItemStack(org.bukkit.Material.GOLD_INGOT, 2)));
            var smelt = bus.call(new org.domi.furnace.api.event.inventory.FurnaceSmeltEvent(W, POS, "minecraft:furnace",
                    ItemStack.of("minecraft:iron_ore"), ItemStack.of("minecraft:iron_ingot")));
            assertEquals("minecraft:gold_ingot", smelt.result().type());
            assertEquals(2, smelt.result().amount());
            assertTrue(RecorderPlugin.SEEN.stream().anyMatch(e -> e instanceof org.bukkit.event.inventory.FurnaceSmeltEvent), "화로 제련은 FurnaceSmeltEvent 로 온다");

            RecorderPlugin.ACTIONS.put("LeavesDecayEvent", e -> ((org.bukkit.event.block.LeavesDecayEvent) e).setCancelled(true));
            assertTrue(bus.call(new org.domi.furnace.api.event.block.LeavesDecayEvent(W, POS, BlockData.of("minecraft:oak_leaves"))).isCancelled());

            RecorderPlugin.ACTIONS.put("BlockFromToEvent", e -> {
                var ft = (org.bukkit.event.block.BlockFromToEvent) e;
                assertEquals(BlockFace.DOWN, ft.getFace());
                assertEquals(63, ft.getToBlock().getY());
                ft.setCancelled(true);
            });
            assertTrue(bus.call(new org.domi.furnace.api.event.block.BlockFromToEvent(W, POS, "minecraft:water", new BlockPos(1, 63, 2), "down")).isCancelled());
        }
    }

    @Test
    void notYetFiredEventsAreKnownAndUnbridged() {
        for (String n : List.of("org.bukkit.event.block.BlockPlaceEvent", "org.bukkit.event.block.NotePlayEvent", "org.bukkit.event.world.WorldInitEvent",
                "org.bukkit.event.player.PlayerCommandSendEvent", "org.bukkit.event.entity.EntityChangeBlockEvent")) {
            Class<? extends org.bukkit.event.Event> c;
            try {
                c = Class.forName(n).asSubclass(org.bukkit.event.Event.class);
            } catch (ClassNotFoundException e) {
                throw new AssertionError(n + " 클래스가 있어야 한다", e);
            }
            assertTrue(EventBridge.isKnownButNotFired(c), n);
            assertFalse(EventBridge.isBridged(c), n);
        }
        // ItemSpawnEvent 는 Bukkit 처럼 EntitySpawnEvent 의 처리기 목록을 같이 쓴다(그래서 등록 시 "안 알림" 경고도 받는다)
        assertThrows(NoSuchMethodException.class, () -> org.bukkit.event.entity.ItemSpawnEvent.class.getDeclaredMethod("getHandlerList"));
        assertTrue(EventBridge.isKnownButNotFired(org.bukkit.event.entity.EntitySpawnEvent.class));
        // 다리가 있는 이벤트는 "안 알림" 목록에 없어야 한다
        for (Class<?> c : EventBridge.bridgedEvents()) assertFalse(EventBridge.isKnownButNotFired(c.asSubclass(org.bukkit.event.Event.class)), c.getName());
    }
}
