// 레시피 북: 바닐라 INIT 바이트(빈 책 = 11바이트 0), 배우기/잊기 규칙(특수 레시피 제외·중복 무시), NBT 왕복.
package org.domi.furnace.server.recipe;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.domi.furnace.server.data.PackRepository;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.data.ResourceManager;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.net.protocol.play.RecipeBookUpdate;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.net.registry.TagSet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class RecipeBookTest {

    private static byte[] bytes(RecipeBookUpdate p) {
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        byte[] b = new byte[buf.readableBytes()];
        buf.readBytes(b);
        return b;
    }

    @Test
    void emptyInitMatchesVanillaCapture() {
        // 바닐라 1.21.1 첫 접속 캡처: recipe(0x41) 본문 11바이트 전부 0
        assertArrayEquals(new byte[11], bytes(new RecipeBook().initPacket()));
    }

    @Test
    void addPacketMatchesVanillaCraftingTableUnlock() {
        // 바닐라 캡처: 첫 접속 직후 crafting_table 해금 ADD 본문 35바이트
        RecipeBook book = new RecipeBook();
        byte[] b = bytes(book.packet(RecipeBookUpdate.ADD, List.of(ResourceId.parse("minecraft:crafting_table"))));
        assertEquals(35, b.length);
        assertEquals(1, b[0]);
        assertEquals(1, b[9]);
        assertEquals(24, b[10]);
    }

    @Test
    void settingsAndHighlightsRoundTripThroughNbt() {
        RecipeBook book = new RecipeBook();
        book.setBookSetting(0, true, false);
        book.setBookSetting(3, false, true);
        book.setBookSetting(7, true, true);   // 범위 밖은 무시
        var tag = book.save();
        assertEquals((byte) 1, tag.getByte("isGuiOpen"));
        assertEquals((byte) 1, tag.getByte("isSmokerFilteringCraftable"));
        RecipeBook copy = new RecipeBook();
        copy.load(tag, null);
        assertTrue(copy.isOpen(0));
        assertTrue(copy.isFiltering(3));
        assertFalse(copy.isOpen(1));
    }

    @Test
    void learningFollowsVanillaRules() throws Exception {
        Path root = Path.of("../tools/vanilla-data/server-1.21.1");
        Assumptions.assumeTrue(Files.isDirectory(root.resolve("data/minecraft/recipe")), "로컬 바닐라 데이터 없음");
        PackRepository repo = new PackRepository(root, null);
        repo.reload();
        repo.setSelected(List.of("vanilla"));
        TagRegistries regs = new TagRegistries(RegistrySet.load("registries/1.21.1.txt"));
        try (ResourceManager rm = repo.openResourceManager()) {
            TagSet tags = TagSet.of(regs.loadAll(rm));
            RecipeManager m = RecipeManager.load(rm, new RecipeContext() {
                public Integer itemId(ResourceId item) { return regs.id("minecraft:item", item); }
                public int[] itemTag(ResourceId tag) { return tags.ids("minecraft:item", tag.toString()); }
                public int serializerId(String type) { return RecipeManager.serializerIndex(type); }
            });
            ResourceId table = ResourceId.parse("crafting_table");
            ResourceId dye = ResourceId.parse("armor_dye");          // 특수 레시피 — 배우지 않는다
            ResourceId missing = ResourceId.parse("no_such_recipe");
            RecipeBook book = new RecipeBook();
            assertEquals(List.of(table), book.addRecipes(List.of(table, dye, missing), m));
            assertTrue(book.willHighlight(table));
            assertEquals(List.of(), book.addRecipes(List.of(table), m));
            // 특수 레시피 14개(CustomRecipe 13 + map_extending)는 배우지 않는다
            assertEquals(m.size() - 1 - 14, book.addRecipes(m.ids(), m).size());

            var tag = book.save();
            // 알 수 없는 레시피는 읽을 때 버린다
            ((org.domi.furnace.server.net.io.nbt.NbtList) tag.tags().get("recipes")).add("minecraft:no_such_recipe");
            RecipeBook copy = new RecipeBook();
            copy.load(tag, m);
            assertEquals(book.initPacket().recipes().size(), copy.initPacket().recipes().size());
            assertEquals(book.initPacket().recipes(), copy.initPacket().recipes());

            book.removeHighlight(table);
            assertFalse(book.willHighlight(table));
            assertEquals(List.of(table), book.removeRecipes(List.of(table, table)));
            assertFalse(book.contains(table));
        }
    }
}
