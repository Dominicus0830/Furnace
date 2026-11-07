// X1: 아이템 기본 최대 수량 기억이 이름 → 프로토타입 직접 조회와 모든 아이템에서 같고, 패치·재설치 규칙도 그대로인지.
package org.domi.furnace.server.item;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MaxStackSizeCacheTest {

    @BeforeAll
    static void install() { ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt"))); }

    private static int direct(int id) {
        String name = ItemContext.name("minecraft:item", id);
        return name == null ? 64 : ItemPrototypes.maxStackSize(name);
    }

    @Test
    void everyItemMatchesDirectLookupColdAndWarm() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));   // 기억을 비운 상태에서 시작
        int n = ItemContext.size("minecraft:item");
        assertTrue(n > 1000);
        for (int pass = 0; pass < 2; pass++) {
            for (int id = 0; id < n; id++) {
                ItemStack s = new ItemStack(id, 1, ComponentPatch.EMPTY);
                assertEquals(direct(id), ItemStackCodecs.maxStackSize(s), "item " + id + " pass " + pass);
            }
        }
        assertEquals(64, ItemStackCodecs.maxStackSize(new ItemStack(n + 5, 1, ComponentPatch.EMPTY)), "범위 밖 번호는 이름이 없어 64");
    }

    @Test
    void patchOverridesStillWin() {
        int stone = ItemContext.id("minecraft:item", "minecraft:stone");
        ItemStack plain = new ItemStack(stone, 1, ComponentPatch.EMPTY);
        assertEquals(64, ItemStackCodecs.maxStackSize(plain));
        ItemStack small = new ItemStack(stone, 1, ComponentPatch.of(Map.of("minecraft:max_stack_size", 16)));
        assertEquals(16, ItemStackCodecs.maxStackSize(small));
        java.util.HashMap<String, Object> removed = new java.util.HashMap<>();
        removed.put("minecraft:max_stack_size", null);
        ItemStack gone = new ItemStack(stone, 1, ComponentPatch.of(removed));
        assertEquals(1, ItemStackCodecs.maxStackSize(gone), "패치가 컴포넌트를 지우면 1");
        assertEquals(64, ItemStackCodecs.maxStackSize(plain), "패치 경로가 기억을 오염시키지 않는다");
    }
}
