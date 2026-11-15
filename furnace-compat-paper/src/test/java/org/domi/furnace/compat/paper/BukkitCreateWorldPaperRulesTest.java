package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.domi.furnace.compat.paper.fixtures.Jars;
import org.domi.furnace.compat.paper.fixtures.RecorderPlugin;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** (PR #388 독립 검토 시험을 옮김) Paper 1.21.1 CraftServer#createWorld·getWorld 대조. 각 단언은 Paper 동작. */
class BukkitCreateWorldPaperRulesTest {

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { RecorderPlugin.reset(); }

    private FurnaceTestServer start() throws Exception {
        Path jar = Jars.bukkit(tmp.resolve("recorder.jar"), """
                name: Recorder
                version: '1'
                main: org.domi.furnace.compat.paper.fixtures.RecorderPlugin
                api-version: '1.21'
                """, Map.of("config.yml", "a: 1\n"), RecorderPlugin.class);
        return FurnaceTestServer.builder().failFast(false).multiWorld(true).pluginJar(jar).start();
    }

    private static <T> T onRegion(World bw, int x, int z, java.util.function.Supplier<T> task) throws Exception {
        var fw = ((org.bukkit.craftbukkit.CraftWorld) bw).furnaceHandle();
        assertTrue(fw.loadChunkAsync(x >> 4, z >> 4, true).get(60, java.util.concurrent.TimeUnit.SECONDS));
        return fw.scheduler(new org.domi.furnace.api.world.BlockPos(x, 0, z)).submit(task).get(60, java.util.concurrent.TimeUnit.SECONDS);
    }

    /** Paper getWorld(name) = worlds.get(name.toLowerCase) — 대소문자 무시. createWorld 도 그래서 그 월드를 돌려준다. */
    @Test
    void getWorldAndCreateWorldIgnoreCase() throws Exception {
        try (FurnaceTestServer server = start()) {
            World w = new WorldCreator("Mine").type(WorldType.FLAT).createWorld();
            String r1 = String.valueOf(Bukkit.getWorld("mine") == w);
            String r2;
            try {
                r2 = String.valueOf(Bukkit.createWorld(new WorldCreator("mine")) == w);
            } catch (RuntimeException e) {
                r2 = e.toString();
            }
            System.out.println("[R388C] getWorld(mine)==Mine " + r1 + " / createWorld(mine) " + r2);
            assertEquals("true", r1);
            assertEquals("true", r2);
        }
    }

    /** Paper: 이름은 있는데 키가 다르면 IllegalArgumentException("Cannot create a world with key ..."). */
    @Test
    void sameNameDifferentKeyIsRejected() throws Exception {
        try (FurnaceTestServer server = start()) {
            World w = new WorldCreator("keyed").createWorld();
            String r;
            try {
                r = "returned same=" + (Bukkit.createWorld(new WorldCreator("keyed", new NamespacedKey("demo", "other"))) == w);
            } catch (IllegalArgumentException e) {
                r = "IAE " + e.getMessage();
            }
            System.out.println("[R388C] " + r);
            assertTrue(r.startsWith("IAE"), r);
        }
    }

    /** Paper: 이미 있는 폴더는 level.dat 의 생성기(평지)를 쓴다 — new WorldCreator(name) 의 기본 NORMAL 로 바뀌지 않는다. */
    @Test
    void existingFlatFolderStaysFlat() throws Exception {
        try (FurnaceTestServer server = start()) {
            World flat = Bukkit.createWorld(new WorldCreator("lobby").type(WorldType.FLAT).generateStructures(false));
            var fw = ((org.bukkit.craftbukkit.CraftWorld) flat).furnaceHandle();
            assertTrue(server.api().unloadWorld(fw, true).get(60, java.util.concurrent.TimeUnit.SECONDS));
            World again = Bukkit.createWorld(new WorldCreator("lobby"));   // 흔한 "있는 월드 불러오기" 꼴
            Material far = onRegion(again, 4000, 4000, () -> again.getBlockAt(4000, -61, 4000).getType());
            String r = "type=" + again.getWorldType() + " structures=" + again.canGenerateStructures() + " far(-61)=" + far;
            System.out.println("[R388C] " + r);
            assertEquals("type=FLAT structures=false far(-61)=GRASS_BLOCK", r);
        }
    }
}
