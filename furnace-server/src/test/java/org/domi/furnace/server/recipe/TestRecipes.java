// 시험용: 로컬 바닐라 데이터로 레시피 매니저를 한 번 만들어 공유한다(없으면 null → 시험은 건너뛴다).
package org.domi.furnace.server.recipe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.domi.furnace.server.data.PackRepository;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.data.ResourceManager;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.net.registry.TagSet;

public final class TestRecipes {

    private TestRecipes() { }

    private static RecipeManager manager;
    private static TagRegistries regs;
    private static TagSet tagSet;
    private static boolean tried;

    /** 로컬 바닐라 데이터가 있으면 레시피 매니저, 없으면 null. 아이템 레지스트리·태그도 ItemContext 에 설치한다. */
    public static synchronized RecipeManager manager() throws Exception {
        if (tried) {   // 다른 시험이 ItemContext 를 덮어썼을 수 있으니 매번 다시 건다
            if (manager != null) { org.domi.furnace.server.item.ItemContext.install(regs); org.domi.furnace.server.item.ItemContext.installTags(tagSet); }
            return manager;
        }
        tried = true;
        Path root = Path.of("../tools/vanilla-data/server-1.21.1");
        if (!Files.isDirectory(root.resolve("data/minecraft/recipe"))) return null;
        PackRepository repo = new PackRepository(root, null);
        repo.reload();
        repo.setSelected(List.of("vanilla"));
        regs = new TagRegistries(RegistrySet.load("registries/1.21.1.txt"));
        org.domi.furnace.server.item.ItemContext.install(regs);
        try (ResourceManager rm = repo.openResourceManager()) {
            TagSet tags = TagSet.of(regs.loadAll(rm));
            tagSet = tags;
            org.domi.furnace.server.item.ItemContext.installTags(tags);
            manager = RecipeManager.load(rm, new RecipeContext() {
                public Integer itemId(ResourceId item) { return regs.id("minecraft:item", item); }
                public int[] itemTag(ResourceId tag) { return tags.ids("minecraft:item", tag.toString()); }
                public int serializerId(String type) { return RecipeManager.serializerIndex(type); }
            });
        }
        return manager;
    }

    public static ItemStack item(String name, int n) {
        return new ItemStack(regs.id("minecraft:item", ResourceId.parse("minecraft:" + name)), n);
    }
}
