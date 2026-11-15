// 블록 태그 표(tags-1.21.1.txt·데이터 팩)의 블록 숫자 id 는 블록 레지스트리 순서이고, B.isTag 는 그 id 를 Blocks 의 블록 번호로 그대로 쓴다.
// 그 가정(두 순서가 같다)과, jar 의 옛 블록 태그 표(block_tags.txt)가 같은 태그 표와 같은 내용인지를 지킨다.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.net.registry.TagSet;
import org.domi.furnace.server.world.gen.feature.BlockTags;
import org.junit.jupiter.api.Test;

class BlockTagRegistryIdentityTest {

    private static List<String> registryBlockNames() throws Exception {
        List<String> names = new ArrayList<>();
        try (var in = BlockTagRegistryIdentityTest.class.getClassLoader().getResourceAsStream("registries/builtin-1.21.1.txt")) {
            for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                if (line.startsWith("minecraft:block ")) names.add(line.substring("minecraft:block ".length()).strip());
            }
        }
        return names;
    }

    @Test
    void blockRegistryOrderEqualsBlocksNumbering() throws Exception {
        List<String> reg = registryBlockNames();
        assertEquals(reg.size(), B.BLOCKS.blockCount());
        for (int i = 0; i < reg.size(); i++) assertEquals(reg.get(i), B.BLOCKS.name(i), "블록 번호 " + i);
    }

    @Test
    void jarBlockTagsMatchTheSyncedTagTable() throws Exception {
        List<String> reg = registryBlockNames();
        TagSet set = TagSet.load("registries/tags-1.21.1.txt");
        var tags = set.tagsOf("minecraft:block");
        assertFalse(tags.isEmpty());
        for (var e : tags.entrySet()) {
            List<String> fromTable = new ArrayList<>();
            for (int id : e.getValue()) fromTable.add(reg.get(id));
            assertEquals(fromTable, BlockTags.get().blocksOf(e.getKey()), e.getKey()); // 내용·순서 모두
        }
    }
}
