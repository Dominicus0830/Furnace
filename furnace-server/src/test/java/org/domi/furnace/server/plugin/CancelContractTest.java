// 이벤트 취소 계약 점검(docs/api/event-cancel-contract.md): 취소할 수 있는 이벤트는 하나도 빠짐없이 종류가 정해져 있고,
// 종류마다 맞는 시험이 실제로 있어야 하며, 계약 문서 표에도 올라 있어야 한다. 새 취소 가능 이벤트를 넣고 이 표를 안 고치면 이 시험이 실패한다.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarFile;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.server.entity.PlayerInventory;
import org.junit.jupiter.api.Test;

class CancelContractTest {

    /**
     * CLIENT_PREDICTED: 클라이언트가 결과를 먼저 보여 줬을 수 있어 코어가 되돌려 보낸다(칸·블록·능력·위치).
     * CLIENT_INPUT: 클라이언트가 직접 정하는 입력이라 되돌릴 방법이 없다 — 서버 상태만 그대로 둔다.
     * SERVER_DRIVEN: 서버가 시작한 변화라 클라이언트가 예측한 것이 없다 — 상태가 안 바뀌면 끝.
     */
    enum Kind { CLIENT_PREDICTED, CLIENT_INPUT, SERVER_DRIVEN }

    record Entry(Kind kind, String... tests) {}

    private static final String E2E = "plugin.CancelResyncE2ETest#";
    private static final String BLOCKS = "plugin.PluginApiBlockEventsTest#";
    private static final String STAGE3 = "plugin.PluginApiStage3Test#";
    private static final String WORLD = "plugin.EntityWorldApiTest#";
    private static final String FORM = "plugin.PluginApiFormEventsTest#";
    private static final String ENCHANT = "menu.EnchantmentMenuTest#";

    /** 이벤트 클래스 단순 이름 → 종류와 이를 증명하는 시험("패키지.클래스#메서드", org.domi.furnace.server 기준). */
    static final Map<String, Entry> TABLE = new TreeMap<>();

    static {
        // --- 블록
        TABLE.put("BlockBreakEvent", new Entry(Kind.CLIENT_PREDICTED, E2E + "blockBreakCancelKeepsBlockAndResendsIt", E2E + "blockBreakCancelResendsBlockEntityToo"));
        TABLE.put("BlockPlaceEvent", new Entry(Kind.CLIENT_PREDICTED, E2E + "blockPlaceCancelKeepsWorldAndResendsHandAndBlock"));
        TABLE.put("BlockIgniteEvent", new Entry(Kind.CLIENT_PREDICTED, E2E + "blockIgniteCancelKeepsFireOutAndResendsBlock"));
        TABLE.put("BlockBurnEvent", new Entry(Kind.SERVER_DRIVEN, BLOCKS + "burnEventReachesSubscriberAndCancelKeepsPlanks"));
        TABLE.put("BlockExplodeEvent", new Entry(Kind.SERVER_DRIVEN, STAGE3 + "blockExplodeNamesTheBedAndKeepEntityExplodeQuiet"));
        TABLE.put("BlockFromToEvent", new Entry(Kind.SERVER_DRIVEN, BLOCKS + "fromToCancelStopsWater"));
        TABLE.put("BlockGrowEvent", new Entry(Kind.SERVER_DRIVEN, BLOCKS + "growCancelKeepsCropAge"));
        TABLE.put("BlockPhysicsEvent", new Entry(Kind.SERVER_DRIVEN, BLOCKS + "physicsCancelKeepsTorchWithoutSupport"));
        TABLE.put("BlockPistonEvent", new Entry(Kind.SERVER_DRIVEN, BLOCKS + "pistonExtendListsBlocksAndCancelStopsIt", BLOCKS + "stickyRetractCancelLeavesBlockBehind"));
        TABLE.put("LeavesDecayEvent", new Entry(Kind.SERVER_DRIVEN, BLOCKS + "leavesDecayCancelKeepsLeaves"));
        // --- 엔티티
        TABLE.put("EntitySpawnEvent", new Entry(Kind.CLIENT_PREDICTED, E2E + "projectileLaunchCancelKeepsSnowballAndResendsHand",
                E2E + "entitySpawnCancelKeepsArmorStandItemAndResendsHand", E2E + "entitySpawnCancelClearsFishingHook",
                STAGE3 + "projectileAndItemSpawnAreSubEventsOfEntitySpawn"));
        TABLE.put("EntityBreedEvent", new Entry(Kind.SERVER_DRIVEN, STAGE3 + "breedCancelKeepsParentsInLoveAndNoChild"));
        TABLE.put("EntityDamageEvent", new Entry(Kind.SERVER_DRIVEN, WORLD + "spawnDamageDeathEventsAndCancellation"));
        TABLE.put("EntityExplodeEvent", new Entry(Kind.SERVER_DRIVEN, STAGE3 + "entityExplodeCanEditListAndCancel"));
        TABLE.put("EntityTameEvent", new Entry(Kind.SERVER_DRIVEN, STAGE3 + "tameCancelKeepsWolfWild"));
        TABLE.put("EntityTargetEvent", new Entry(Kind.SERVER_DRIVEN, STAGE3 + "targetEventFiresOnChangeOnlyAndCancelKeepsOldTarget"));
        TABLE.put("ProjectileHitEvent", new Entry(Kind.SERVER_DRIVEN, STAGE3 + "projectileHitCancelLetsSnowballFlyOn"));
        // --- 인벤토리
        TABLE.put("InventoryClickEvent", new Entry(Kind.CLIENT_PREDICTED, E2E + "inventoryClickCancelResendsWholeMenu"));
        TABLE.put("InventoryOpenEvent", new Entry(Kind.SERVER_DRIVEN, E2E + "inventoryOpenCancelOpensNothing"));
        TABLE.put("InventoryMoveItemEvent", new Entry(Kind.SERVER_DRIVEN, BLOCKS + "hopperMoveCanChangeItemAndCancel"));
        TABLE.put("FurnaceBurnEvent", new Entry(Kind.SERVER_DRIVEN, "block.FurnaceBlockEntityTest#furnaceBurnCancelKeepsFuelAndFireOut"));
        TABLE.put("FurnaceSmeltEvent", new Entry(Kind.SERVER_DRIVEN, "block.FurnaceBlockEntityTest#furnaceSmeltCancelKeepsInputAndOutput"));
        // --- 플레이어
        TABLE.put("PlayerInteractEvent", new Entry(Kind.CLIENT_PREDICTED, E2E + "interactAirCancelResendsHand", E2E + "interactBlockCancelResendsHandAndBothBlocks",
                E2E + "leftClickBlockCancelResendsBlock"));
        TABLE.put("PlayerInteractEntityEvent", new Entry(Kind.CLIENT_PREDICTED, E2E + "interactEntityCancelResendsHandAndDoesNothing"));
        TABLE.put("PlayerDropItemEvent", new Entry(Kind.CLIENT_PREDICTED, E2E + "dropOneCancelReturnsToHeldSlotAndResendsInventory",
                E2E + "dropAllCancelReturnsWholeStackToHeldSlot", WORLD + "playerMoveDropAndPickupEventsCanCancel"));
        TABLE.put("PlayerItemHeldEvent", new Entry(Kind.CLIENT_PREDICTED, E2E + "itemHeldCancelResendsSelectedSlot"));
        TABLE.put("PlayerItemConsumeEvent", new Entry(Kind.CLIENT_PREDICTED, E2E + "consumeCancelKeepsFoodAndResendsHand"));
        TABLE.put("PlayerToggleFlightEvent", new Entry(Kind.CLIENT_PREDICTED, E2E + "toggleFlightCancelResendsAbilities"));
        TABLE.put("PlayerMoveEvent", new Entry(Kind.CLIENT_PREDICTED, E2E + "moveCancelTeleportsBackAndKeepsPosition", WORLD + "cancelledMoveOnCheckedPathCorrectsOnceBypassingTeleportEvent"));
        TABLE.put("PlayerToggleSneakEvent", new Entry(Kind.CLIENT_INPUT, E2E + "toggleSneakAndSprintCancelKeepServerState"));
        TABLE.put("PlayerToggleSprintEvent", new Entry(Kind.CLIENT_INPUT, E2E + "toggleSneakAndSprintCancelKeepServerState"));
        TABLE.put("PlayerSwapHandItemsEvent", new Entry(Kind.SERVER_DRIVEN, E2E + "swapHandsCancelLeavesBothHandsAlone"));
        TABLE.put("PlayerPickupItemEvent", new Entry(Kind.SERVER_DRIVEN, WORLD + "playerMoveDropAndPickupEventsCanCancel"));
        TABLE.put("PlayerTeleportEvent", new Entry(Kind.SERVER_DRIVEN, E2E + "teleportCancelKeepsPositionAndSendsNothing"));
        TABLE.put("PlayerGameModeChangeEvent", new Entry(Kind.SERVER_DRIVEN, E2E + "gameModeChangeCancelKeepsMode"));
        TABLE.put("PlayerBedEnterEvent", new Entry(Kind.SERVER_DRIVEN, "entity.PlayerSleepingTest#bedEnterCancelLeavesPlayerAwakeAndBedFree"));
        TABLE.put("PlayerKickEvent", new Entry(Kind.SERVER_DRIVEN, STAGE3 + "kickEventChangesReasonOrCancels"));
        TABLE.put("AsyncChatEvent", new Entry(Kind.SERVER_DRIVEN, E2E + "chatCancelBroadcastsNothing"));
        TABLE.put("PlayerCommandPreprocessEvent", new Entry(Kind.SERVER_DRIVEN, E2E + "commandPreprocessCancelRunsNothing"));
        // --- 3단계 셋째 묶음(블록 형성·탈것·양조·마법 부여·포털·책·낚시) — 모두 서버가 시작하거나 서버 답을 기다리는 변화
        TABLE.put("BlockFadeEvent", new Entry(Kind.SERVER_DRIVEN, FORM + "coralBlockFadeCancelKeepsLiveCoral"));
        TABLE.put("BlockFormEvent", new Entry(Kind.SERVER_DRIVEN, FORM + "lavaMeetingWaterFormsObsidianUnlessCancelled", FORM + "mushroomSpreadEventHasSourceAndCancelBlocksIt"));
        TABLE.put("BrewEvent", new Entry(Kind.SERVER_DRIVEN, FORM + "brewingFuelAndBrewEventsCanBeCancelledOrTuned"));
        TABLE.put("BrewingStandFuelEvent", new Entry(Kind.SERVER_DRIVEN, FORM + "brewingFuelCancelKeepsBlazePowderAndFuel"));
        TABLE.put("PrepareItemEnchantEvent", new Entry(Kind.SERVER_DRIVEN, ENCHANT + "prepareEnchantCancelOffersNothing"));
        TABLE.put("EnchantItemEvent", new Entry(Kind.SERVER_DRIVEN, ENCHANT + "enchantItemCancelKeepsItemLapisLevelsAndSeed"));
        TABLE.put("EntityMountEvent", new Entry(Kind.SERVER_DRIVEN, FORM + "entityMountAndDismountCancelKeepRidingState"));
        TABLE.put("EntityDismountEvent", new Entry(Kind.SERVER_DRIVEN, FORM + "entityMountAndDismountCancelKeepRidingState"));
        TABLE.put("VehicleEnterEvent", new Entry(Kind.SERVER_DRIVEN, FORM + "vehicleAndMountEventsFireInOrderAndCancelStopsRiding"));
        TABLE.put("VehicleExitEvent", new Entry(Kind.SERVER_DRIVEN, FORM + "vehicleAndMountEventsFireInOrderAndCancelStopsRiding"));
        TABLE.put("EntityPortalEvent", new Entry(Kind.SERVER_DRIVEN, "entity.PortalCancelTest#entityPortalCancelStopsTheTravel"));
        TABLE.put("PlayerPortalEvent", new Entry(Kind.SERVER_DRIVEN, "net.DimensionChangeTest#playerPortalCancelKeepsPlayerInOverworld"));
        TABLE.put("PortalCreateEvent", new Entry(Kind.SERVER_DRIVEN, "entity.PortalCancelTest#portalCreateCancelLeavesTheFrameEmpty"));
        TABLE.put("PlayerEditBookEvent", new Entry(Kind.SERVER_DRIVEN, E2E + "editBookCancelLeavesBookAlone"));
        TABLE.put("PlayerFishEvent", new Entry(Kind.SERVER_DRIVEN, E2E + "fishCastCancelThrowsNoHookAndLeavesRodAlone"));
        // --- 서버
        TABLE.put("ServerCommandEvent", new Entry(Kind.SERVER_DRIVEN, "plugin.PluginSystemTest#eventBusOrdersByPriorityAndHonorsCancellation"));
    }

    /** API 모듈에서 Cancellable 을 직접 구현한 이벤트 클래스(하위 클래스는 부모 항목을 따른다). */
    static List<Class<?>> cancellableEvents() throws Exception {
        URI loc = Cancellable.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        File base = new File(loc);
        List<String> names = new ArrayList<>();
        if (base.isDirectory()) {
            Path root = base.toPath();
            try (var walk = Files.walk(root)) {
                walk.filter(f -> f.toString().endsWith(".class")).forEach(f -> names.add(root.relativize(f).toString().replace(File.separatorChar, '.').replaceAll("\\.class$", "")));
            }
        } else {
            try (JarFile jar = new JarFile(base)) {
                jar.stream().filter(e -> e.getName().endsWith(".class"))
                        .forEach(e -> names.add(e.getName().replace('/', '.').replaceAll("\\.class$", "")));
            }
        }
        List<Class<?>> out = new ArrayList<>();
        for (String n : names) {
            if (!n.startsWith("org.domi.furnace.api.event.") || n.contains("$")) continue;
            Class<?> c = Class.forName(n, false, Cancellable.class.getClassLoader());
            if (!c.isInterface() && Arrays.asList(c.getInterfaces()).contains(Cancellable.class)) out.add(c);
        }
        return out;
    }

    @Test
    void everyCancellableEventHasAKindAndNoStaleEntryRemains() throws Exception {
        var found = new TreeSet<String>();
        for (Class<?> c : cancellableEvents()) found.add(c.getSimpleName());
        assertFalse(found.isEmpty(), "이벤트 클래스를 찾지 못했다");
        var missing = new TreeSet<>(found);
        missing.removeAll(TABLE.keySet());
        assertTrue(missing.isEmpty(), "취소할 수 있는 이벤트가 이 표에 없다 — 종류(Kind)와 시험을 정하고 docs/api/event-cancel-contract.md 에도 올릴 것: " + missing);
        var stale = new TreeSet<>(TABLE.keySet());
        stale.removeAll(found);
        assertTrue(stale.isEmpty(), "표에는 있는데 더는 취소할 수 없는(또는 없는) 이벤트: " + stale);
    }

    @Test
    void everyEntryNamesRealTests() throws Exception {
        for (var e : TABLE.entrySet()) {
            assertTrue(e.getValue().tests().length > 0, e.getKey() + " 에 증명 시험이 없다");
            for (String ref : e.getValue().tests()) {
                String[] parts = ref.split("#");
                Class<?> cls = Class.forName("org.domi.furnace.server." + parts[0]);
                var m = Arrays.stream(cls.getDeclaredMethods()).filter(x -> x.getName().equals(parts[1])).findFirst();
                assertTrue(m.isPresent(), e.getKey() + ": 시험 메서드가 없다 " + ref);
                assertTrue(m.get().isAnnotationPresent(Test.class), e.getKey() + ": @Test 가 아니다 " + ref);
            }
        }
    }

    @Test
    void contractDocListsEveryEventWithItsKind() throws Exception {
        Path doc = Path.of("..", "docs", "api", "event-cancel-contract.md");
        assertTrue(Files.exists(doc), "계약 문서가 없다: " + doc.toAbsolutePath());
        String text = Files.readString(doc, StandardCharsets.UTF_8);
        for (var e : TABLE.entrySet()) {
            String row = Arrays.stream(text.split("\n")).filter(l -> l.startsWith("|") && l.contains("`" + e.getKey() + "`")).findFirst().orElse(null);
            assertNotNull(row, "계약 문서 표에 " + e.getKey() + " 행이 없다");
            assertTrue(row.contains(e.getValue().kind().name()), "계약 문서의 " + e.getKey() + " 종류가 " + e.getValue().kind() + " 가 아니다: " + row);
        }
    }

    @Test
    void windowSlotAndInventoryIndexAreInverse() {
        for (int i = 0; i <= 40; i++) assertEquals(i, PlayerInventory.inventoryIndex(PlayerInventory.windowSlot(i)), "Inventory 번호 " + i);
        for (int w = 0; w <= 4; w++) assertEquals(-1, PlayerInventory.inventoryIndex(w), "제작 칸에는 Inventory 번호가 없다");
        assertEquals(0, PlayerInventory.inventoryIndex(36));
        assertEquals(40, PlayerInventory.inventoryIndex(45));
        assertEquals(39, PlayerInventory.inventoryIndex(5), "머리");
    }
}
