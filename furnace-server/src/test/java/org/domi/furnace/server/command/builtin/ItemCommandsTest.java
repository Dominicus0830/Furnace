// 아이템 인자(item_stack·item_predicate·item_slot(s)) 파싱·오류 키, /give 나눠 넣기·가득 참 떨굼, /clear 세기·지우기, 메시지 분기.
package org.domi.furnace.server.command.builtin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.command.arguments.ItemArgument;
import org.domi.furnace.server.command.arguments.ItemPredicateArgument;
import org.domi.furnace.server.command.arguments.SlotArgument;
import org.domi.furnace.server.command.arguments.SlotsArgument;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.item.ItemContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ItemCommandsTest {

    @BeforeEach
    void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
        ItemContext.installTags(org.domi.furnace.server.net.registry.TagSet.load("registries/tags-1.21.1.txt"));
    }

    private static int id(String item) { return ItemContext.id("minecraft:item", item); }

    private static String errorKey(ThrowingParse p) {
        CommandSyntaxException e = assertThrows(CommandSyntaxException.class, p::run);
        return e.getRawMessage().translationKey();
    }

    interface ThrowingParse { void run() throws CommandSyntaxException; }

    private static ItemArgument.ItemInput item(String s) throws CommandSyntaxException { return ItemArgument.item().parse(new StringReader(s)); }

    private static ItemPredicateArgument.Result pred(String s) throws CommandSyntaxException {
        return ItemPredicateArgument.itemPredicate().parse(new StringReader(s));
    }

    @Test
    void itemArgumentParsesIdAndComponents() throws Exception {
        var in = item("stone");
        assertEquals("minecraft:stone", in.item());
        assertTrue(in.components().isEmpty());
        StringReader r = new StringReader("minecraft:diamond 5");
        ItemArgument.item().parse(r);
        assertEquals(17, r.getCursor(), "아이템 뒤 공백 앞에서 멈춘다");
        var stack = item("stone[max_stack_size=16]").createItemStack(3, false);
        assertEquals(16, ItemArgument.maxStackSize(stack));
        assertEquals(3, stack.count());
        // 기본값과 같은 값은 패치에서 빠진다
        assertTrue(item("stone[max_stack_size=64]").createItemStack(1, false).patch().isEmpty());
        var removed = item("stone[!max_stack_size]").components().entries();
        assertTrue(removed.containsKey("minecraft:max_stack_size") && removed.get("minecraft:max_stack_size") == null);
        assertNotNull(item("stone[custom_name='\"hi\"', max_stack_size=2]"));
    }

    @Test
    void itemArgumentErrorKeys() {
        assertEquals("argument.item.id.invalid", errorKey(() -> item("no_such_item")));
        assertEquals("arguments.item.component.unknown", errorKey(() -> item("stone[foo=1]")));
        assertEquals("arguments.item.component.malformed", errorKey(() -> item("stone[max_stack_size=500]")));
        assertEquals("arguments.item.component.repeated", errorKey(() -> item("stone[max_stack_size=5,max_stack_size=6]")));
        assertEquals("arguments.item.component.expected", errorKey(() -> item("stone[max_stack_size=5,")));
        assertEquals("parsing.expected", errorKey(() -> item("stone[max_stack_size 5]")));
        assertEquals("arguments.item.malformed", errorKey(() -> item("diamond_sword[max_stack_size=5]")));
        assertEquals("arguments.item.overstacked", errorKey(() -> item("stone").createItemStack(65, true)));
        // 오류 뒤 커서는 처음 자리로
        StringReader r = new StringReader("stone[foo=1]");
        assertThrows(CommandSyntaxException.class, () -> ItemArgument.item().parse(r));
        assertEquals(0, r.getCursor());
    }

    @Test
    void itemPredicateMatches() throws Exception {
        ItemStack stone = new ItemStack(id("stone"), 3);
        ItemStack log = new ItemStack(id("oak_log"), 1);
        assertTrue(pred("*").test(stone));
        assertTrue(pred("stone").test(stone));
        assertFalse(pred("stone").test(log));
        assertTrue(pred("#minecraft:logs").test(log));
        assertFalse(pred("#minecraft:logs").test(stone));
        assertTrue(pred("*[count=3]").test(stone));
        assertFalse(pred("*[count={min:4}]").test(stone));
        assertTrue(pred("*[count~{max:3}]").test(stone));
        assertTrue(pred("stone[max_stack_size=64]").test(stone));
        assertFalse(pred("stone[!max_stack_size]").test(stone));
        assertTrue(pred("*[custom_name|max_stack_size]").test(stone));
        assertFalse(pred("*[custom_name]").test(stone));
        assertTrue(pred("*[]").test(stone));
        assertEquals("argument.item.id.invalid", errorKey(() -> pred("no_such_item")));
        assertEquals("arguments.item.tag.unknown", errorKey(() -> pred("#minecraft:no_such_tag")));
        assertEquals("arguments.item.component.unknown", errorKey(() -> pred("*[foo=1]")));
        assertEquals("arguments.item.predicate.unknown", errorKey(() -> pred("*[foo~{}]")));
    }

    @Test
    void slotArguments() throws Exception {
        assertEquals(98, SlotArgument.slot().parse(new StringReader("weapon")));
        assertEquals(103, SlotArgument.slot().parse(new StringReader("armor.head")));
        assertEquals(209, SlotArgument.slot().parse(new StringReader("enderchest.9")));
        assertEquals(502, SlotArgument.slot().parse(new StringReader("player.crafting.2")));
        assertEquals("slot.only_single_allowed", errorKey(() -> SlotArgument.slot().parse(new StringReader("container.*"))));
        assertEquals("slot.unknown", errorKey(() -> SlotArgument.slot().parse(new StringReader("nope"))));
        assertEquals(5, SlotsArgument.slots().parse(new StringReader("armor.*")).size());
        assertEquals(27, SlotsArgument.slots().parse(new StringReader("inventory.*")).size());
    }

    private static Player player() { return new Player(1, UUID.randomUUID(), "A", new ConcurrentEmbeddedChannel()); }

    @Test
    void giveSplitsIntoMaxStacksThenDropsWhenFull() throws Exception {
        Player p = player();
        List<String> fx = new ArrayList<>();
        ItemCommands.GiveEffects rec = new ItemCommands.GiveEffects() {
            public void fakeDrop(ItemStack one) { fx.add("fake" + one.count()); }

            public void drop(ItemStack rest) { fx.add("drop" + rest.count()); }
        };
        ItemCommands.giveTo(p, item("stone"), 130, rec);
        assertEquals(List.of("fake1", "fake1", "fake1"), fx, "64·64·2 를 모두 넣으면 덩이마다 가짜 아이템");
        assertEquals(64, p.inventory().get(36).count());
        assertEquals(64, p.inventory().get(37).count());
        assertEquals(2, p.inventory().get(38).count());
        // 남은 33 칸을 흙으로 채운다
        fx.clear();
        ItemCommands.giveTo(p, item("dirt"), 64 * 33, rec);
        fx.clear();
        ItemCommands.giveTo(p, item("dirt"), 70, rec);   // 빈 칸이 없다: 덩이(64·6)마다 떨굼
        assertEquals(List.of("drop64", "drop6"), fx);
        fx.clear();
        ItemCommands.giveTo(p, item("stone"), 64, rec);   // 덜 찬 돌 칸(62 자리) + 2 떨굼
        assertEquals(List.of("drop2"), fx);
    }

    @Test
    void pickupPitchUsesTwoFloatsInOrder() {
        LegacyRandomSource a = new LegacyRandomSource(42), b = new LegacyRandomSource(42);
        float f1 = b.nextFloat(), f2 = b.nextFloat();
        assertEquals(((f1 - f2) * 0.7F + 1.0F) * 2.0F, ItemCommands.pickupPitch(a));
    }

    @Test
    void clearCountsAndRemoves() throws Exception {
        Player p = player();
        p.inventory().set(36, new ItemStack(id("stone"), 10));
        p.inventory().set(9, new ItemStack(id("stone"), 5));
        p.inventory().set(2, new ItemStack(id("stone"), 1));   // 제작 칸
        p.inventory().set(5, new ItemStack(id("diamond_helmet"), 1));
        var stone = pred("stone");
        assertEquals(16, ItemCommands.clearOrCount(p, stone, 0), "세기만");
        assertEquals(10, p.inventory().get(36).count());
        assertEquals(12, ItemCommands.clearOrCount(p, stone, 12), "핫바 → 주 인벤토리 순으로 12개");
        assertTrue(p.inventory().get(36).isEmpty());
        assertEquals(3, p.inventory().get(9).count());
        assertEquals(1, p.inventory().get(2).count());
        assertEquals(5, ItemCommands.clearOrCount(p, s -> true, -1));
        assertTrue(p.inventory().get(2).isEmpty());
        assertTrue(p.inventory().get(5).isEmpty());
    }

    @Test
    void messagesBranchOnTargetCount() throws Exception {
        Player a = player(), b = player();
        assertEquals("commands.clear.success.single", ItemCommands.clearMessage(3, List.of(a), -1).translationKey());
        assertEquals("commands.clear.success.multiple", ItemCommands.clearMessage(3, List.of(a, b), 5).translationKey());
        assertEquals("commands.clear.test.single", ItemCommands.clearMessage(3, List.of(a), 0).translationKey());
        assertEquals("commands.clear.test.multiple", ItemCommands.clearMessage(3, List.of(a, b), 0).translationKey());
        ItemStack proto = item("stone").createItemStack(1, false);
        Text one = ItemCommands.giveMessage(5, proto, List.of(a));
        Text many = ItemCommands.giveMessage(5, proto, List.of(a, b));
        assertEquals("commands.give.success.single", one.translationKey());
        assertEquals("commands.give.success.single", many.translationKey(), "바닐라 1.21.1 은 여러 명에도 single 키");
        assertEquals("chat.square_brackets", ItemCommands.displayName(proto).translationKey());
    }
}
