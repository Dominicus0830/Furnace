package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Supplier;
import net.minecraft.DetectedVersion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.domi.furnace.compat.CompatTestPlugin;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** NMS shim 5번 묶음(VeinMiner 가 부르는 41곳): 손 칸 되쓰기·허기·ClickEvent·팩 버전·불 블록. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsVeinMinerSurfaceTest {

    FurnaceTestServer server;
    TestPlayer tp;
    CraftPlayer bukkit;
    ServerPlayer handle;
    ServerLevel level;
    BlockPos base;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).exposeCompatToNative(true).plugin(CompatTestPlugin.class).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Veiner");
        server.tick(3);
        bukkit = (CraftPlayer) Compat.player(tp.player());
        handle = bukkit.getHandle();
        level = ((CraftWorld) bukkit.getWorld()).getHandle();
        var loc = tp.player().location();
        base = new BlockPos((int) Math.floor(loc.x()) + 2, (int) Math.floor(loc.y()) + 1, (int) Math.floor(loc.z()) + 2);
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T on(Supplier<T> task) { return server.onRegion(new org.domi.furnace.api.world.BlockPos(base.getX(), base.getY(), base.getZ()), task); }

    @Test
    void handDamageWritesThroughToThePlayersInventory() {
        var put = tp.player().inventory().set(0, org.domi.furnace.api.item.ItemStack.of("minecraft:diamond_pickaxe"));
        for (int i = 0; i < 20 && !put.isDone(); i++) server.tick(1);
        put.join();
        ItemStack hand = on(() -> handle.getItemInHand(InteractionHand.MAIN_HAND));
        on(() -> { hand.setDamageValue(7); return null; });
        int d = -1;
        for (int i = 0; i < 20 && d != 7; i++) {
            d = on(() -> handle.getItemInHand(InteractionHand.MAIN_HAND).getDamageValue());
            server.tick(1);
        }
        assertEquals(7, d);
    }

    @Test
    void foodDataIsAViewOfThePlayer() {
        var food = on(() -> handle.getFoodData());
        on(() -> { food.setFoodLevel(10); return null; });
        assertEquals(10, (int) on(() -> food.getFoodLevel()));
        assertTrue(on(() -> food.needsFood()));
    }

    @Test
    void clickEventKeepsActionAndValue() {
        var e = new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/say hi");
        assertEquals(ClickEvent.Action.RUN_COMMAND, e.getAction());
        assertEquals("/say hi", e.getValue());
        var style = net.minecraft.network.chat.Style.EMPTY.withClickEvent(e);
        assertEquals(e, style.getClickEvent());
        assertEquals(null, net.minecraft.network.chat.Style.EMPTY.getHoverEvent());
    }

    @Test
    void packVersionsAreVanilla1_21_1() {
        assertEquals(34, DetectedVersion.BUILT_IN.getPackVersion(PackType.CLIENT_RESOURCES));
        assertEquals(48, DetectedVersion.BUILT_IN.getPackVersion(PackType.SERVER_DATA));
    }

    @Test
    void fireBlocksAreBaseFireBlocks() {
        assertTrue(Blocks.FIRE instanceof net.minecraft.world.level.block.BaseFireBlock);
        assertTrue(Blocks.SOUL_FIRE instanceof net.minecraft.world.level.block.BaseFireBlock);
        assertFalse(Blocks.STONE instanceof net.minecraft.world.level.block.BaseFireBlock);
        assertNotNull(Items.DIAMOND_PICKAXE);
    }
}
