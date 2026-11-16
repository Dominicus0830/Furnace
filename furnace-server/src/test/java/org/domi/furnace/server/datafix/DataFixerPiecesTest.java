// 조각 단위 데이터 픽서(T-199): 같은 고침 표를 블록 이름·상태·아이템 이름·아이템 칸에 하나씩 적용했을 때, 구조물 템플릿 전체를 고친 것과 같은 결과가 나오는지.
// FAWE 의 LegacyMapper 가 legacy.json 의 1.13.2(1631) 이름을 올리는 길이다. 기대값은 StructureDataFixerTest 가 이미 확인한 단계 표(바닐라 1.21.1 DataFixers)를 따른다.
package org.domi.furnace.server.datafix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.domi.furnace.api.GameVersion;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.Snbt;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DataFixerPiecesTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt")));
    }

    private static NbtCompound nbt(String snbt) { return (NbtCompound) Snbt.parse(snbt); }

    @Test
    void blockNamesFrom1631() {
        // FAWE LegacyMapper 가 "Unknown block" 으로 적던 이름들(1.13.2 → 1.21.1)
        assertEquals("minecraft:short_grass", StructureDataFixer.fixBlockName("minecraft:grass", 1631));
        assertEquals("minecraft:oak_sign", StructureDataFixer.fixBlockName("minecraft:sign", 1631));
        assertEquals("minecraft:oak_wall_sign", StructureDataFixer.fixBlockName("minecraft:wall_sign", 1631));
        assertEquals("minecraft:dirt_path", StructureDataFixer.fixBlockName("minecraft:grass_path", 1631));
        // 이름공간이 없어도 붙여서 본다
        assertEquals("minecraft:short_grass", StructureDataFixer.fixBlockName("grass", 1631));
        // 이미 새 이름이면 그대로, 같은 판·밖의 판이면 아무것도 안 한다
        assertEquals("minecraft:stone", StructureDataFixer.fixBlockName("minecraft:stone", 1631));
        assertEquals("minecraft:grass", StructureDataFixer.fixBlockName("minecraft:grass", 3955));
        assertEquals("minecraft:grass", StructureDataFixer.fixBlockName("minecraft:grass", 1451));
        assertEquals("minecraft:grass", StructureDataFixer.fixBlockName("minecraft:grass", 4000));
        // grass(3692) 보다 새 판의 데이터는 grass 를 그대로 둔다(그때는 이미 풀 블록이 아니라 grass_block 이다)
        assertEquals("minecraft:grass", StructureDataFixer.fixBlockName("minecraft:grass", 3692));
    }

    @Test
    void itemNamesFrom1631() {
        assertEquals("minecraft:oak_sign", StructureDataFixer.fixItemName("minecraft:sign", 1631));
        assertEquals("minecraft:red_dye", StructureDataFixer.fixItemName("minecraft:rose_red", 1631));
        assertEquals("minecraft:green_dye", StructureDataFixer.fixItemName("minecraft:cactus_green", 1631));
        assertEquals("minecraft:yellow_dye", StructureDataFixer.fixItemName("minecraft:dandelion_yellow", 1631));
        assertEquals("minecraft:short_grass", StructureDataFixer.fixItemName("minecraft:grass", 1631));
        assertEquals("minecraft:stone", StructureDataFixer.fixItemName("minecraft:stone", 1631));
        assertEquals("minecraft:rose_red", StructureDataFixer.fixItemName("minecraft:rose_red", 3955));
    }

    @Test
    void blockStateKeepsPropertiesAndFixesWallSides() {
        NbtCompound sign = StructureDataFixer.fixBlockState(nbt("{Name:\"minecraft:sign\",Properties:{rotation:\"7\",waterlogged:\"false\"}}"), 1631);
        assertEquals("minecraft:oak_sign", sign.getString("Name"));
        assertEquals("7", ((NbtCompound) sign.tags().get("Properties")).getString("rotation"));
        // 담장(2503): 옛 true/false → low/none
        NbtCompound wall = StructureDataFixer.fixBlockState(nbt("{Name:\"minecraft:cobblestone_wall\",Properties:{east:\"true\",north:\"false\",south:\"false\",up:\"true\",waterlogged:\"false\",west:\"false\"}}"), 1631);
        NbtCompound p = (NbtCompound) wall.tags().get("Properties");
        assertEquals("low", p.getString("east"));
        assertEquals("none", p.getString("north"));
        assertEquals("true", p.getString("up"));
    }

    @Test
    void itemStackAndBlockEntityPieces() {
        // 1631 의 아이템은 tag 시대 꼴 — 이름이 바뀌고 Count 가 count 로(컴포넌트화)
        NbtCompound it = StructureDataFixer.fixItem(nbt("{id:\"minecraft:rose_red\",Count:3b,Damage:0s}"), 1631);
        assertEquals("minecraft:red_dye", it.getString("id"));
        assertEquals(3, it.getInt("count"));
        // 표지판 블록 엔티티(3439): 앞뒤 글 꼴로
        NbtCompound be = StructureDataFixer.fixBlockEntityPiece(nbt("{id:\"minecraft:sign\",Text1:'{\"text\":\"a\"}',Text2:'',Text3:'',Text4:'',x:1,y:2,z:3}"), 1631);
        assertEquals("minecraft:sign", be.getString("id"));
        assertEquals(true, be.tags().get("front_text") instanceof NbtCompound);
    }

    @Test
    void sameOrUnsupportedVersionReturnsTheInput() {
        NbtCompound st = nbt("{Name:\"minecraft:grass\"}");
        assertSame(st, StructureDataFixer.fixBlockState(st, 3955));
        assertSame(st, StructureDataFixer.fixBlockState(st, 1000));
    }
}
