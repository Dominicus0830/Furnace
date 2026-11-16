// 플러그인 API 데이터 픽서(NbtDataFixer) 배선 시험(#436 검토): 메서드마다 올바른 고침 조각에 이어졌는지, 입력은 안 건드리는지, 판 범위가 맞는지.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.domi.furnace.api.GameVersion;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NbtDataFixerImplTest {

    private final NbtDataFixerImpl fixer = new NbtDataFixerImpl();

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt")));
    }

    @Test
    void versionRange() {
        assertEquals(3955, fixer.currentVersion());
        assertEquals(1452, fixer.minSupportedVersion());
        assertTrue(fixer.supports(1631));
        assertTrue(fixer.supports(3955));
        assertFalse(fixer.supports(1451));
        assertFalse(fixer.supports(4000));
    }

    @Test
    void namesAreWiredToBlockAndItemTables() {
        // 같은 이름이라도 블록 표와 아이템 표 결과가 다르다 — rose_red 는 아이템만 red_dye, grass 는 둘 다 short_grass
        assertEquals("minecraft:short_grass", fixer.fixBlockName("minecraft:grass", 1631));
        assertEquals("minecraft:red_dye", fixer.fixItemName("minecraft:rose_red", 1631));
        assertEquals("minecraft:rose_red", fixer.fixBlockName("minecraft:rose_red", 1631));
    }

    @Test
    void compoundsAreWiredToTheirOwnFixers() {
        NbtCompound state = NbtCompound.parse("{Name:\"minecraft:sign\",Properties:{rotation:\"7\"}}");
        assertEquals("minecraft:oak_sign", fixer.fixBlockState(state, 1631).getString("Name"));

        NbtCompound item = NbtCompound.parse("{id:\"minecraft:rose_red\",Count:3b}");
        NbtCompound itemOut = fixer.fixItemStack(item, 1631);
        assertEquals("minecraft:red_dye", itemOut.getString("id"));
        assertEquals(3, itemOut.getInt("count"));

        NbtCompound sign = NbtCompound.parse("{id:\"minecraft:sign\",Text1:'{\"text\":\"a\"}',Text2:'',Text3:'',Text4:'',x:1,y:2,z:3}");
        NbtCompound signOut = fixer.fixBlockEntity(sign, 1631);
        assertTrue(signOut.contains("front_text"));
        assertFalse(signOut.contains("Text1"));

        NbtCompound pig = NbtCompound.parse("{id:\"minecraft:zombie_pigman\"}");
        assertEquals("minecraft:zombified_piglin", fixer.fixEntity(pig, 2230).getString("id"));

        // 서로 바꿔 이어졌다면 달라지는 쪽: 아이템 모양은 블록 엔티티·엔티티 픽서를 거쳐도 id 가 안 바뀐다
        assertEquals("minecraft:rose_red", fixer.fixBlockEntity(item, 1631).getString("id"));
        assertEquals("minecraft:rose_red", fixer.fixEntity(item, 1631).getString("id"));
    }

    @Test
    void inputIsNotMutatedAndUnsupportedVersionIsACopy() {
        NbtCompound item = NbtCompound.parse("{id:\"minecraft:rose_red\",Count:3b}");
        NbtCompound out = fixer.fixItemStack(item, 1631);
        assertEquals("minecraft:rose_red", item.getString("id"), "원본은 그대로");
        assertEquals(3, item.getByte("Count"));
        assertNotSame(item, out);
        NbtCompound same = fixer.fixItemStack(item, 3955);
        assertNotSame(item, same, "못 고치는·같은 판이어도 사본");
        assertEquals("minecraft:rose_red", same.getString("id"));
    }
}
