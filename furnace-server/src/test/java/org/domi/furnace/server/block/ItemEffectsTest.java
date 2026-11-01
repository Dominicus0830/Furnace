// 아이템 사용 효과 시험: 염소 뿔 악기, 책 편집/서명, open_book 직렬화, 가마솥 씻기, 불길한 병, 이름표, 합창 열매 목적지 판정.
// 기대값은 디컴파일 손계산(Instruments 140틱/256, handleEditBook 변환, CauldronInteraction 3종, OminousBottleItem 120000틱).
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.domi.furnace.server.block.types.CauldronInteraction;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.BookEditing;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.ItemEffects;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.protocol.play.OpenBook;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ItemEffectsTest {

    @BeforeAll
    static void install() { ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt"))); }

    static ItemStack item(String name, int n) { return new ItemStack(ItemContext.id("minecraft:item", "minecraft:" + name), n); }

    @Test
    void goatHornInstruments() {
        var d = ItemEffects.instrumentOf(item("goat_horn", 1));
        assertEquals(new ItemEffects.Instrument("minecraft:item.goat_horn.sound.0", 140, 256.0F), d);
        var seek = ItemEffects.instrumentOf(ItemTools.with(item("goat_horn", 1), "minecraft:instrument", "minecraft:seek_goat_horn"));
        assertEquals("minecraft:item.goat_horn.sound.2", seek.sound());
        Map<String, Object> inline = new LinkedHashMap<>();
        inline.put("sound_event", "minecraft:block.bell.use");
        inline.put("use_duration", 20);
        inline.put("range", 8.0F);
        var custom = ItemEffects.instrumentOf(ItemTools.with(item("goat_horn", 1), "minecraft:instrument", inline));
        assertEquals(20, custom.useDuration());
        assertEquals(8.0F, custom.range());
    }

    static final class Lvl extends BlockPlacementTest.TestLevel {
        final List<String> sounds = new ArrayList<>();
        @Override public void playSound(BlockActor except, Pos pos, String sound, String source, float volume, float pitch) {
            sounds.add(sound + "/" + source + "/" + volume + "/" + pitch);
        }
    }

    @Test
    void goatHornUseStartsCooldownAndSound() {
        Lvl level = new Lvl();
        var played = new ArrayList<String>();
        var cooldown = new int[2];
        var actor = new BlockPlacementTest.Actor() {
            @Override public void addCooldown(int itemId, int ticks) { cooldown[0] = itemId; cooldown[1] = ticks; }
            @Override public void startUsingItem(int hand, int ticks) { played.add("use" + hand + ":" + ticks); }
            @Override public void awardStat(String s) { played.add(s); }
        };
        actor.hands[0] = item("goat_horn", 1);
        var r = ItemUse.useItem(level, actor, 0);
        assertEquals(InteractionResult.CONSUME, r);
        assertEquals(List.of("use0:140", "minecraft:used:minecraft:goat_horn"), played);
        assertEquals(140, cooldown[1]);
        assertEquals(actor.hands[0].itemId(), cooldown[0]);
    }

    @Test
    void bookWriteKeepsFirstHundredPagesAndRejectsOtherItems() {
        var book = item("writable_book", 1);
        List<String> pages = new ArrayList<>();
        for (int i = 0; i < 120; i++) pages.add("p" + i);
        var out = BookEditing.write(book, pages);
        var content = (Map<?, ?>) ItemTools.component(out, "minecraft:writable_book_content");
        var list = (List<?>) content.get("pages");
        assertEquals(100, list.size());
        assertEquals("p0", ((Map<?, ?>) list.get(0)).get("raw"));
        assertEquals("p99", ((Map<?, ?>) list.get(99)).get("raw"));
        assertNull(BookEditing.write(item("book", 1), pages));
        assertNull(BookEditing.sign(item("stone", 1), "t", pages, "a"));
        assertTrue(BookEditing.validSlot(0) && BookEditing.validSlot(8) && BookEditing.validSlot(40));
        assertFalse(BookEditing.validSlot(9) || BookEditing.validSlot(-1) || BookEditing.validSlot(41));
        assertEquals(36, BookEditing.windowSlot(0));
        assertEquals(44, BookEditing.windowSlot(8));
        assertEquals(45, BookEditing.windowSlot(40));
    }

    @Test
    void bookSignBecomesWrittenBookWithAuthorAndLiteralPages() {
        var book = BookEditing.write(item("writable_book", 1), List.of("x"));
        var out = BookEditing.sign(book, "제목", List.of("hi", "둘째"), "Steve");
        assertEquals("minecraft:written_book", ItemTools.itemName(out));
        assertEquals(1, out.count());
        assertNull(ItemTools.component(out, "minecraft:writable_book_content"));
        var c = (Map<?, ?>) ItemTools.component(out, "minecraft:written_book_content");
        assertEquals("제목", ((Map<?, ?>) c.get("title")).get("raw"));
        assertEquals("Steve", c.get("author"));
        assertEquals(Boolean.TRUE, c.get("resolved"));
        var pages = (List<?>) c.get("pages");
        assertEquals(2, pages.size());
        assertEquals("\"hi\"", ((Map<?, ?>) pages.get(0)).get("raw"));
    }

    @Test
    void openBookPacketBytes() {
        var buf = Unpooled.buffer();
        new OpenBook(1).write(buf);
        assertEquals(0x32, OpenBook.ID);
        assertArrayEquals(new byte[] {1}, java.util.Arrays.copyOf(buf.array(), buf.readableBytes()));
    }

    // ------------------------------------------------------------------ 가마솥 씻기

    static final Pos POS = new Pos(1, -60, 1);

    static final class Stats extends BlockPlacementTest.Actor {
        final List<String> stats = new ArrayList<>();
        final List<ItemStack> dropped = new ArrayList<>();
        @Override public void drop(ItemStack s) { dropped.add(s); }
        @Override public void awardStat(String s) { stats.add(s); }
    }

    private static int waterCauldron(Lvl level, int fill) {
        int s = LayeredFill.state(fill);
        level.setBlock(POS, s, 3);
        return level.getBlockState(POS);
    }

    /** water_cauldron 의 level 만 바꾼 상태. */
    static final class LayeredFill {
        static int state(int fill) { return B.prop("level").withInt(B.defaultState("water_cauldron"), fill); }
    }

    @Test
    void cauldronWashesDyedArmorBannerAndShulkerBox() {
        Lvl level = new Lvl();
        int st = waterCauldron(level, 3);
        var a = new Stats();
        var dyed = ItemTools.with(item("leather_helmet", 1), "minecraft:dyed_color", Map.of("rgb", 255));
        a.hands[0] = dyed;
        var r = CauldronInteraction.WATER.get(dyed).interact(st, level, POS, a, 0, dyed);
        assertEquals(ItemInteractionResult.sidedSuccess(false), r);
        assertNull(ItemTools.component(a.hands[0], "minecraft:dyed_color"));
        assertEquals(List.of("minecraft:clean_armor"), a.stats);
        assertEquals(2, B.prop("level").intValue(level.getBlockState(POS)));
        // 색 없는 가죽 모자는 넘긴다
        var plain = item("leather_helmet", 1);
        assertEquals(ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
                CauldronInteraction.WATER.get(plain).interact(level.getBlockState(POS), level, POS, a, 0, plain));
        // 깃발: 무늬 두 겹 → 한 겹, 3개 중 1개만 건넨다
        st = waterCauldron(level, 3);
        var layers = new ArrayList<Object>();
        layers.add(Map.of("pattern", "minecraft:stripe_bottom", "color", "red"));
        layers.add(Map.of("pattern", "minecraft:border", "color", "blue"));
        var banner = ItemTools.with(item("white_banner", 3), "minecraft:banner_patterns", layers);
        a.hands[0] = banner;
        a.stats.clear();
        CauldronInteraction.WATER.get(banner).interact(st, level, POS, a, 0, banner);
        assertEquals(List.of("minecraft:clean_banner"), a.stats);
        assertEquals(2, a.hands[0].count());   // 나머지 2개는 그대로(무늬 2겹), 씻은 1개는 인벤토리에 못 넣어 던져진다
        assertEquals(2, ((List<?>) ItemTools.component(a.hands[0], "minecraft:banner_patterns")).size());
        assertEquals(1, a.dropped.size());
        assertEquals(1, ((List<?>) ItemTools.component(a.dropped.get(0), "minecraft:banner_patterns")).size());
        assertEquals(2, B.prop("level").intValue(level.getBlockState(POS)));
        // 패턴 없는 깃발은 넘긴다
        var bare = item("white_banner", 1);
        assertEquals(ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
                CauldronInteraction.WATER.get(bare).interact(level.getBlockState(POS), level, POS, a, 0, bare));
        // 셜커 상자: 빨간 상자 → 무색 상자
        st = waterCauldron(level, 2);
        var box = item("red_shulker_box", 1);
        a.hands[0] = box;
        a.stats.clear();
        CauldronInteraction.WATER.get(box).interact(st, level, POS, a, 0, box);
        assertEquals("minecraft:shulker_box", ItemTools.itemName(a.hands[0]));
        assertEquals(List.of("minecraft:clean_shulker_box"), a.stats);
        assertEquals(1, B.prop("level").intValue(level.getBlockState(POS)));
    }

    // ------------------------------------------------------------------ 불길한 병 · 이름표 · 합창 열매

    @Test
    void ominousBottleGivesBadOmenWithAmplifier() {
        Lvl level = new Lvl();
        Player p = new Player(1, UUID.randomUUID(), "A", new EmbeddedChannel());
        var bottle = ItemTools.with(item("ominous_bottle", 1), "minecraft:ominous_bottle_amplifier", 3);
        ItemEffects.ominousBottleDrunk(p, level, bottle);
        var fx = p.vitals().activeEffects();
        assertEquals(1, fx.size());
        assertEquals(120000, fx.get(0).duration());
        assertEquals(3, fx.get(0).amplifier());
        assertFalse(fx.get(0).visible());
        assertEquals(1, level.sounds.size());
        assertTrue(level.sounds.get(0).startsWith("minecraft:item.ominous_bottle.dispose/players/1.0/1.0"));
    }

    @Test
    void nameTagNamesLivingEntityAndConsumesOneUnlessCreative() {
        Lvl level = new Lvl();
        Player p = new Player(1, UUID.randomUUID(), "A", new EmbeddedChannel());
        var cow = new Cow(level, level.nextEntityId());
        var tag = ItemTools.with(item("name_tag", 2), "minecraft:custom_name", "\"Bessie\"");
        int ws = p.inventory().heldWindowSlot();
        p.inventory().set(ws, tag);
        assertTrue(ItemEffects.nameTag(p, 0, tag, cow));
        assertTrue(cow.hasCustomName());
        assertTrue(cow.isPersistenceRequired());
        assertEquals(1, p.inventory().get(ws).count());
        // 이름 없는 이름표는 넘긴다
        assertFalse(ItemEffects.nameTag(p, 0, item("name_tag", 1), cow));
    }

    @Test
    void chorusTargetNeedsSolidFloorAndEmptyBody() {
        Lvl level = new Lvl();
        Player p = new Player(1, UUID.randomUUID(), "A", new EmbeddedChannel());
        // 슈퍼플랫: 바닥 y=-61 잔디 → 윗면 -60. y=-50 에서 내려가면 d3 = -60 에 선다
        double[] t = ItemEffects.randomTeleportTarget(p, level, 5.5, -50.0, 5.5);
        assertNotNull(t);
        assertEquals(-60.0, t[1], 1e-9);
        // 바닥 아래 땅속(-62.5): 몸이 블록에 겹쳐 실패, 아래에는 기반암이 있어 내려가 -63 에서 서려다 겹침
        assertNull(ItemEffects.randomTeleportTarget(p, level, 5.5, -62.5, 5.5));
        // 로드되지 않은 청크는 실패
        assertNull(ItemEffects.randomTeleportTarget(p, level, 5000.5, -50.0, 5000.5));
    }
}
