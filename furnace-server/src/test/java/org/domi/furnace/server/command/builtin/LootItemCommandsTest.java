// /item replace|modify 와 /loot 의 플레이어·생물 칸, 오류 키, 전리품 표 인자(id·인라인) 파싱, give 대상.
package org.domi.furnace.server.command.builtin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.ItemSlots;
import org.domi.furnace.server.command.arguments.ResourceOrIdArgument;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.entity.EquipmentSlot;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Zombie;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LootItemCommandsTest {

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private Player player;
    private final List<String> out = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
        ItemContext.installTags(org.domi.furnace.server.net.registry.TagSet.load("registries/tags-1.21.1.txt"));
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        core.addConsoleListener(t -> out.add(t.getString()));
        player = new Player(5, UUID.randomUUID(), "Steve", new EmbeddedChannel());
    }

    @AfterEach
    void tearDown() { sched.close(); }

    /** 플레이어(권한 4)로 실행 — 메시지는 플레이어 연결로 가므로 결과는 상태로 본다. */
    private void run(String command) {
        core.commands().performPrefixedCommandAsync(CommandSource.player(core, player).withPermission(4), command).join();
    }

    /** 콘솔로 실행하고 콘솔 출력. */
    private String console(String command) {
        out.clear();
        core.commands().performPrefixedCommandAsync(CommandSource.console(core), command).join();
        return String.join("\n", out);
    }

    private static String name(ItemStack s) { return s.isEmpty() ? "empty" : ItemTools.itemName(s) + "x" + s.count(); }

    @Test
    void itemReplaceEntityPlayerSlots() {
        run("item replace entity @s weapon.mainhand with minecraft:stone 5");
        assertEquals("minecraft:stonex5", name(player.inventory().get(player.inventory().heldWindowSlot())));
        run("item replace entity @s armor.head from entity @s weapon.mainhand");
        assertEquals("minecraft:stonex5", name(ItemSlots.of(player, 103).get()));
        assertEquals("minecraft:stonex5", name(player.inventory().get(5)), "머리 칸은 창 칸 5");
        run("item replace entity @s inventory.0 with minecraft:dirt");
        assertEquals("minecraft:dirtx1", name(player.inventory().get(9)));
        run("item replace entity @s enderchest.3 with minecraft:dirt 2");
        assertEquals("minecraft:dirtx2", name(player.enderChest().get(3)));
        run("item replace entity @s weapon.offhand with minecraft:dirt 3");
        assertEquals("minecraft:dirtx3", name(player.inventory().get(45)));
    }

    @Test
    void mobEquipmentSlots() {
        var level = new BlockPlacementTest.TestLevel();
        var z = new Zombie(level, level.nextEntityId());
        var access = ItemSlots.of(z, 102);
        assertNotNull(access);
        ItemStack s = new ItemStack(ItemContext.id("minecraft:item", "minecraft:iron_chestplate"), 1);
        assertTrue(access.set(s));
        assertEquals("minecraft:iron_chestplatex1", name(z.getItemBySlot(EquipmentSlot.CHEST)));
        assertNull(ItemSlots.of(z, 0), "생물에는 인벤토리 칸이 없다");
        assertNull(ItemSlots.of(z, 499));
    }

    @Test
    void lootTableArgumentParsing() throws Exception {
        var arg = ResourceOrIdArgument.lootTable();
        StringReader r = new StringReader("minecraft:chests/simple_dungeon rest");
        assertEquals("minecraft:chests/simple_dungeon", arg.parse(r).id());
        assertEquals(" rest", r.getRemaining());
        assertEquals("minecraft:foo", arg.parse(new StringReader("foo")).id());
        var inline = arg.parse(new StringReader("{pools:[{rolls:1,entries:[]}]}"));
        assertNull(inline.id());
        assertEquals("{\"pools\":[{\"rolls\":1,\"entries\":[]}]}", inline.inline().toString());
        CommandSyntaxException e = assertThrows(CommandSyntaxException.class, () -> arg.parse(new StringReader("foo{")));
        assertEquals("argument.resource_or_id.invalid", e.getRawMessage().translationKey());
        assertEquals("minecraft:loot_table", arg.networkId());
        assertEquals("minecraft:loot_modifier", ResourceOrIdArgument.lootModifier().networkId());
    }

    @Test
    void errorKeys() {
        assertTrue(console("item modify block 0 0 0 container.0 foo:bar").contains("Failed to parse"), out.toString());
        assertTrue(console("loot spawn 0 0 0 loot foo:bar").contains("Failed to parse"), out.toString());
        assertTrue(console("item replace block 0 0 0 container.0 with minecraft:stone 100").contains("Integer must not be more than 99"), out.toString());
        // 플레이어에게 없는 칸(말 안장)은 SlotAccess.NULL → 바닐라 no_changed.known_item / source.no_such_slot
        assertNull(ItemSlots.of(player, 400));
        ItemStack before = player.inventory().get(player.inventory().heldWindowSlot());
        run("item replace entity @s horse.saddle with minecraft:stone");
        assertSame(before, player.inventory().get(player.inventory().heldWindowSlot()));
    }

    @Test
    void lootGiveAddsToInventory() throws Exception {
        ItemStack diamonds = new ItemStack(ItemContext.id("minecraft:item", "minecraft:diamond"), 3);
        List<List<ItemStack>> seen = new ArrayList<>();
        int n = LootCommand.playerGive(List.of(player), List.of(diamonds), seen::add).join();
        assertEquals(1, n);
        assertEquals(1, seen.get(0).size());
        assertEquals("minecraft:diamondx3", name(player.inventory().get(36)));
    }
}
