// PR #500 후속(A13-08): /reload 읽기가 실패하면 바닐라처럼 옛 팩 선택과 옛 태그가 그대로 남는다.
package org.domi.furnace.server.data;

import org.domi.furnace.api.GameVersion;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.domi.furnace.server.ItemContextExtension;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.storage.LevelData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(ItemContextExtension.class)
class DataPackReloadRestoreTest {

    @TempDir Path tmp;

    private static void write(Path p, String s) throws Exception {
        Files.createDirectories(p.getParent());
        Files.writeString(p, s);
    }

    private static String tag(String... values) {
        StringBuilder sb = new StringBuilder("{\"replace\":false,\"values\":[");
        for (int i = 0; i < values.length; i++) sb.append(i == 0 ? "" : ",").append('"').append(values[i]).append('"');
        return sb.append("]}").toString();
    }

    @Test
    void failedReloadKeepsOldSelectionAndTags() throws Exception {
        Path vanilla = tmp.resolve("vanilla");
        write(vanilla.resolve("data/minecraft/tags/block/climbable.json"), tag("minecraft:ladder"));
        Path pack = tmp.resolve("datapacks/tagger");
        write(pack.resolve("pack.mcmeta"), "{\"pack\":{\"description\":\"t\",\"pack_format\":48}}");
        write(pack.resolve("data/minecraft/tags/block/climbable.json"), tag("minecraft:stone"));
        var packs = DataPackManager.start(vanilla, tmp, LevelData.create("world", 1L, true, false, org.domi.furnace.server.world.GameMode.SURVIVAL,
                false, org.domi.furnace.server.world.Difficulty.EASY, List.of("vanilla"), List.of()),
                RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt"), true, List.of("vanilla"), List.of());
        try {
            List<String> before = packs.repository().selectedIds();
            assertTrue(before.contains("file/tagger"), before.toString());
            assertTrue(B.isTag(B.defaultState("minecraft:stone"), "minecraft:climbable"));

            AtomicBoolean fail = new AtomicBoolean();
            packs.functionLoader(rm -> {
                if (fail.get()) throw new IllegalStateException("읽기 실패 시험");
                return org.domi.furnace.server.function.FunctionLibrary.EMPTY;
            });
            fail.set(true);
            var ex = assertThrows(ExecutionException.class, () -> packs.reload(List.of("vanilla"), Runnable::run).get(30, TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class, ex.getCause());
            assertEquals(before, packs.repository().selectedIds(), "읽기가 실패하면 옛 선택이 그대로다");
            assertTrue(B.isTag(B.defaultState("minecraft:stone"), "minecraft:climbable"), "옛 태그도 그대로");

            fail.set(false);   // 복원된 선택으로 다음 리로드가 정상 동작한다
            packs.reload(List.of("vanilla"), Runnable::run).get(30, TimeUnit.SECONDS);
            assertEquals(List.of("vanilla"), packs.repository().selectedIds());
            assertFalse(B.isTag(B.defaultState("minecraft:stone"), "minecraft:climbable"));
        } finally {
            packs.close();
        }
    }
}
