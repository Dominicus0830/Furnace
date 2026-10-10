// 바닐라 데이터(로컬 tools/vanilla-data/server-1.21.1 이 있을 때)를 데이터 팩으로 읽은 태그가,
// 바닐라 데이터 생성기 출력으로 만든 registries/tags-1.21.1.txt 와 태그·순서까지 같은지.
package org.domi.furnace.server.data;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.net.registry.TagSet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class VanillaTagsTest {

    static Path vanillaRoot() {
        Path p = Path.of("../tools/vanilla-data/server-1.21.1");
        Assumptions.assumeTrue(Files.isDirectory(p.resolve("data/minecraft/tags")), "로컬 바닐라 데이터 없음");
        return p;
    }

    @Test
    void datapackTagsMatchGeneratedTags() {
        PackRepository repo = new PackRepository(vanillaRoot(), null);
        repo.reload();
        repo.setSelected(List.of("vanilla"));
        TagSet expected = TagSet.load("registries/tags-1.21.1.txt");
        try (ResourceManager rm = repo.openResourceManager()) {
            Map<String, Map<String, int[]>> got = new TagRegistries(RegistrySet.load("registries/1.21.1.txt")).loadAll(rm);
            int compared = 0;
            for (String reg : expected.registries()) {
                Map<String, int[]> exp = expected.tagsOf(reg), act = got.get(reg);
                assertNotNull(act, reg);
                assertEquals(exp.keySet(), act.keySet(), reg + " 태그 목록");
                for (var e : exp.entrySet()) {
                    assertArrayEquals(e.getValue(), act.get(e.getKey()), reg + " " + e.getKey());
                    compared++;
                }
            }
            assertEquals(expected.tagCount(), compared);
        }
    }
}
