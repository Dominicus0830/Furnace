// 데이터 팩: 덮어쓰기·filter·오버레이·zip·태그 replace·새 팩 자동 켜기·level.dat 반영.
package org.domi.furnace.server.data;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.domi.furnace.server.world.storage.LevelData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataPackTest {

    @TempDir Path tmp;

    static void write(Path p, String s) throws IOException {
        Files.createDirectories(p.getParent());
        Files.writeString(p, s);
    }

    static String meta(int format, String extra) {
        return "{\"pack\":{\"description\":\"test\",\"pack_format\":" + format + "}" + extra + "}";
    }

    static String read(ResourceManager.Resource r) throws IOException {
        try (InputStream in = r.open().get()) { return new String(in.readAllBytes(), StandardCharsets.UTF_8); }
    }

    @Test
    void higherPackOverridesAndFilterHidesLowerPacks() throws IOException {
        Path world = tmp.resolve("datapacks");
        write(world.resolve("a/pack.mcmeta"), meta(48, ""));
        write(world.resolve("a/data/x/loot_table/one.json"), "A1");
        write(world.resolve("a/data/x/loot_table/two.json"), "A2");
        write(world.resolve("a/data/y/recipe/r.json"), "AR");
        write(world.resolve("b/pack.mcmeta"), meta(48, ",\"filter\":{\"block\":[{\"namespace\":\"y\"}]}"));
        write(world.resolve("b/data/x/loot_table/one.json"), "B1");
        PackRepository repo = new PackRepository(null, world);
        repo.reload();
        repo.setSelected(List.of("file/a", "file/b"));
        try (ResourceManager rm = repo.openResourceManager()) {
            assertEquals("B1", read(rm.getResource(ResourceId.parse("x:loot_table/one.json")).orElseThrow()));
            assertEquals("A2", read(rm.getResource(ResourceId.parse("x:loot_table/two.json")).orElseThrow()));
            // b 의 filter 가 y 네임스페이스를 아래 팩(a)에서 숨긴다.
            assertTrue(rm.getResource(ResourceId.parse("y:recipe/r.json")).isEmpty());
            assertEquals(2, rm.listResources("loot_table", id -> true).size());
            assertTrue(rm.listResources("recipe", id -> true).isEmpty());
            List<ResourceManager.Resource> stack = rm.getResourceStack(ResourceId.parse("x:loot_table/one.json"));
            assertEquals(List.of("file/a", "file/b"), stack.stream().map(ResourceManager.Resource::packId).toList());
        }
        // 순서를 뒤집으면 a 가 위 — b 의 filter 는 아래(없음)에만 적용된다.
        repo.setSelected(List.of("file/b", "file/a"));
        try (ResourceManager rm = repo.openResourceManager()) {
            assertEquals("A1", read(rm.getResource(ResourceId.parse("x:loot_table/one.json")).orElseThrow()));
            assertTrue(rm.getResource(ResourceId.parse("y:recipe/r.json")).isPresent());
        }
    }

    @Test
    void overlaysAndZipPacks() throws IOException {
        Path world = tmp.resolve("datapacks");
        write(world.resolve("o/pack.mcmeta"), meta(48, ",\"overlays\":{\"entries\":[{\"formats\":[40,50],\"directory\":\"new\"},"
            + "{\"formats\":{\"min_inclusive\":10,\"max_inclusive\":20},\"directory\":\"old\"}]}"));
        write(world.resolve("o/data/x/f/a.json"), "base");
        write(world.resolve("o/new/data/x/f/a.json"), "overlay");
        write(world.resolve("o/old/data/x/f/a.json"), "old");
        Files.createDirectories(world);
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(world.resolve("z.zip")))) {
            z.putNextEntry(new ZipEntry("pack.mcmeta"));
            z.write(meta(30, ",\"pack\":{\"description\":\"z\",\"pack_format\":30,\"supported_formats\":[30,60]}").getBytes());
            z.putNextEntry(new ZipEntry("data/x/f/b.json"));
            z.write("zip".getBytes());
        }
        PackRepository repo = new PackRepository(null, world);
        repo.reload();
        assertEquals(Pack.Compatibility.COMPATIBLE, repo.pack("file/z.zip").compatibility());
        repo.setSelected(List.of("file/o", "file/z.zip"));
        try (ResourceManager rm = repo.openResourceManager()) {
            assertEquals("overlay", read(rm.getResource(ResourceId.parse("x:f/a.json")).orElseThrow()));
            assertEquals("zip", read(rm.getResource(ResourceId.parse("x:f/b.json")).orElseThrow()));
            assertEquals(List.of("x:f/a.json", "x:f/b.json"),
                rm.listResources("f", id -> true).keySet().stream().map(Object::toString).toList());
        }
    }

    @Test
    void tagReplaceAndMissingReferences() throws IOException {
        Path world = tmp.resolve("datapacks");
        write(world.resolve("a/pack.mcmeta"), meta(48, ""));
        write(world.resolve("a/data/minecraft/tags/item/t.json"), "{\"values\":[\"minecraft:stone\",\"#minecraft:u\"]}");
        write(world.resolve("a/data/minecraft/tags/item/u.json"), "{\"values\":[\"minecraft:dirt\",\"minecraft:stone\"]}");
        write(world.resolve("a/data/minecraft/tags/item/bad.json"), "{\"values\":[\"minecraft:nope\"]}");
        write(world.resolve("a/data/minecraft/tags/item/opt.json"), "{\"values\":[{\"id\":\"minecraft:nope\",\"required\":false},\"minecraft:dirt\"]}");
        write(world.resolve("b/pack.mcmeta"), meta(48, ""));
        write(world.resolve("b/data/minecraft/tags/item/u.json"), "{\"replace\":true,\"values\":[\"minecraft:glass\"]}");
        PackRepository repo = new PackRepository(null, world);
        repo.reload();
        repo.setSelected(List.of("file/a", "file/b"));
        Map<String, Integer> ids = Map.of("minecraft:stone", 1, "minecraft:dirt", 2, "minecraft:glass", 3);
        try (ResourceManager rm = repo.openResourceManager()) {
            Map<String, int[]> tags = TagLoader.load(rm, "minecraft:item", id -> ids.get(id.toString()));
            assertArrayEquals(new int[] {3}, tags.get("minecraft:u"));          // b 가 replace
            assertArrayEquals(new int[] {1, 3}, tags.get("minecraft:t"));       // 중첩 태그는 풀린 u 를 쓴다
            assertArrayEquals(new int[] {2}, tags.get("minecraft:opt"));        // 선택 엔트리는 없어도 된다
            assertFalse(tags.containsKey("minecraft:bad"));                    // 필수 참조가 없으면 태그를 싣지 않는다
        }
    }

    @Test
    void newWorldAutoEnablesWorldPacksButNotFeaturePacks() throws IOException {
        Path vanilla = tmp.resolve("vanilla");
        write(vanilla.resolve("data/minecraft/tags/item/x.json"), "{\"values\":[]}");
        write(vanilla.resolve("data/minecraft/datapacks/bundle/pack.mcmeta"), meta(48, ",\"features\":{\"enabled\":[\"minecraft:bundle\"]}"));
        Path worldDir = tmp.resolve("world");
        write(worldDir.resolve("datapacks/mine/pack.mcmeta"), meta(48, ""));
        LevelData ld = LevelData.create("world", 1L, true, false, org.domi.furnace.server.world.GameMode.SURVIVAL, false,
            org.domi.furnace.server.world.Difficulty.EASY, List.of("vanilla"), List.of());
        try (DataPackManager m = DataPackManager.start(vanilla, worldDir, ld,
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt"), true, List.of("vanilla"), List.of())) {
            assertEquals(List.of("vanilla", "file/mine"), m.repository().selectedIds());
            assertEquals(List.of("vanilla", "file/mine"), ld.enabledPacks);
            assertEquals(List.of("bundle"), ld.disabledPacks);
            assertEquals(List.of("minecraft:vanilla"), ld.enabledFeatures);
            // 켜기: bundle 은 기본 자리(위)에 들어간다.
            List<Pack> l = new java.util.ArrayList<>(m.repository().selected());
            Pack.insert(l, m.repository().pack("bundle"), false);
            assertEquals(List.of("vanilla", "file/mine", "bundle"), l.stream().map(Pack::id).toList());
            // 다시 찾기: 끄지 않은 새 팩만 붙는다.
            write(worldDir.resolve("datapacks/later/pack.mcmeta"), meta(48, ""));
            assertEquals(List.of("vanilla", "file/mine", "file/later"), m.discoverNewPacks(m.repository().selectedIds()));
        }
    }

    @Test
    void vanillaDataExtractsFromLocalJar() throws IOException {
        Path jar = Path.of("../tools/vanilla-data/minecraft_server.1.21.1.jar");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(jar), "로컬 바닐라 jar 없음");
        System.setProperty("furnace.vanillaJar", jar.toString());
        try {
            Path root = VanillaData.ensure(tmp);
            assertTrue(Files.isRegularFile(root.resolve("data/minecraft/recipe/stone.json")));
            assertTrue(Files.isRegularFile(root.resolve("data/minecraft/datapacks/bundle/pack.mcmeta")));
            assertEquals(VanillaData.SERVER_JAR_SHA1, Files.readString(root.resolve(".complete")).trim());
            long t = System.nanoTime();
            assertEquals(root, VanillaData.ensure(tmp));   // 두 번째는 바로 돌아온다
            assertTrue(System.nanoTime() - t < 2_000_000_000L);
            // 상대 경로로 불러도(서버가 실제로 쓰는 "." 처럼) 같은 폴더를 돌려준다.
            Path rel = Path.of("").toAbsolutePath().relativize(tmp);
            assertEquals(root, VanillaData.ensure(rel));
        } finally {
            System.clearProperty("furnace.vanillaJar");
        }
    }
}
