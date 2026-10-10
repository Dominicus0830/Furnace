// 바닐라 데이터(로컬 tools/vanilla-data/server-1.21.1)로 만든 update_recipes 본문이 바닐라 1.21.1 서버가 보낸 것과
// 바이트 단위로 같은지(순서·인코딩 전부). 기대값은 바닐라 서버에 접속해 캡처한 본문의 SHA-256.
package org.domi.furnace.server.recipe;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.domi.furnace.server.data.PackRepository;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.data.ResourceManager;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.net.registry.TagSet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class VanillaRecipesTest {

    static final String VANILLA_UPDATE_RECIPES_SHA256 = "bd4193d33199e1850fe20f115cc7c02be824a48bd7635603ba7e1ef353dfe6ce";

    @Test
    void updateRecipesMatchesVanillaServerBytes() throws Exception {
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
            assertEquals(1290, m.size());
            String dump = System.getProperty("furnace.recipeDump");
            if (dump != null) Files.write(Path.of(dump), m.networkBody());
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(m.networkBody()));
            assertEquals(VANILLA_UPDATE_RECIPES_SHA256, sha);
        }
    }

    @Test
    void shapedPatternShrinksLikeVanilla() {
        assertArrayEquals(new String[] {"#", "#"}, Recipe.Shaped.shrink(List.of("  ", " #", " #")));
        assertArrayEquals(new String[] {"# #", "###"}, Recipe.Shaped.shrink(List.of("# #", "###", "   ")));
        assertArrayEquals(new String[0], Recipe.Shaped.shrink(List.of("   ", "   ")));
    }
}
