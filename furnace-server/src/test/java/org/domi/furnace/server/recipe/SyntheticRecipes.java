// 시험용: 임시 폴더에 구운 철 레시피 하나만 쓴 가짜 데이터 팩으로 레시피 매니저를 만든다 — 로컬 바닐라 데이터가 없는 CI 에서도 화로 시험이 돈다.
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

public final class SyntheticRecipes {

    private SyntheticRecipes() { }

    private static TagRegistries regs;

    /** raw_iron → iron_ingot 굽기(200틱) 레시피 하나뿐인 매니저. ItemContext 에 아이템 레지스트리·태그도 설치한다. */
    public static RecipeManager manager(Path root) throws Exception {
        Path dir = root.resolve("data/minecraft/recipe");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("iron_ingot_from_smelting_raw_iron.json"),
                "{\"type\":\"minecraft:smelting\",\"category\":\"misc\",\"cookingtime\":200,\"experience\":0.7,"
                + "\"ingredient\":{\"item\":\"minecraft:raw_iron\"},\"result\":{\"id\":\"minecraft:iron_ingot\"}}");
        PackRepository repo = new PackRepository(root, null);
        repo.reload();
        repo.setSelected(List.of("vanilla"));
        regs = new TagRegistries(RegistrySet.load("registries/1.21.1.txt"));
        org.domi.furnace.server.item.ItemContext.install(regs);
        try (ResourceManager rm = repo.openResourceManager()) {
            TagSet tags = TagSet.of(regs.loadAll(rm));
            org.domi.furnace.server.item.ItemContext.installTags(tags);
            return RecipeManager.load(rm, new RecipeContext() {
                public Integer itemId(ResourceId item) { return regs.id("minecraft:item", item); }
                public int[] itemTag(ResourceId tag) { return tags.ids("minecraft:item", tag.toString()); }
                public int serializerId(String type) { return RecipeManager.serializerIndex(type); }
            });
        }
    }

    public static ItemStack item(String name, int n) {
        return new ItemStack(regs.id("minecraft:item", ResourceId.parse("minecraft:" + name)), n);
    }
}
