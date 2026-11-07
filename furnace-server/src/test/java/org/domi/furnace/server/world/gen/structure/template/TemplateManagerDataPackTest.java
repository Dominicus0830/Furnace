// 데이터 팩 구조물 템플릿(Q7): data/<ns>/structure/**.nbt 읽기 — 위 팩이 아래 팩을 덮고, 월드 generated 가 데이터 팩을 덮고,
// 리로드로 저장소가 비며, 목록(listTemplates)에 보이고, 옛 DataVersion 파일도 읽힌다.
package org.domi.furnace.server.world.gen.structure.template;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.domi.furnace.server.data.PackResources;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.data.ResourceManager;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.world.BlockRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemplateManagerDataPackTest {

    private static final BlockRegistry REG = BlockRegistry.load("blocks/1.21.1.txt");

    @TempDir Path tmp;

    @AfterEach
    void reset() { TemplateManager.installDataPacks(null); }

    /** 1×1×1 템플릿 NBT: 블록 하나. dataVersion 이 0 이하면 DataVersion 태그를 뺀다. */
    private static NbtCompound template(String block, int dataVersion) {
        NbtCompound root = new NbtCompound();
        NbtList size = new NbtList(NbtIo.TAG_INT);
        for (int i = 0; i < 3; i++) size.add(1);
        root.putList("size", size);
        NbtList palette = new NbtList(NbtIo.TAG_COMPOUND);
        NbtCompound entry = new NbtCompound();
        entry.putString("Name", block);
        palette.add(entry);
        root.putList("palette", palette);
        NbtList blocks = new NbtList(NbtIo.TAG_COMPOUND);
        NbtCompound b = new NbtCompound();
        NbtList pos = new NbtList(NbtIo.TAG_INT);
        for (int i = 0; i < 3; i++) pos.add(0);
        b.putList("pos", pos);
        b.putInt("state", 0);
        blocks.add(b);
        root.putList("blocks", blocks);
        root.putList("entities", new NbtList(NbtIo.TAG_COMPOUND));
        if (dataVersion > 0) root.putInt("DataVersion", dataVersion);
        return root;
    }

    private static void write(Path file, NbtCompound root) throws IOException {
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file)) {
            NbtIo.writeGzip(out, root, "");
        }
    }

    private Path pack(String name, String ns, String path, String block, int dataVersion) throws IOException {
        Path root = tmp.resolve(name);
        write(root.resolve("data").resolve(ns).resolve("structure").resolve(path + ".nbt"), template(block, dataVersion));
        return root;
    }

    /** packs: 아래→위. */
    private static ResourceManager manager(Path... roots) {
        List<PackResources> packs = new java.util.ArrayList<>();
        for (Path r : roots) packs.add(new PackResources.PathPack(r.getFileName().toString(), r));
        return new ResourceManager(packs, List.of());
    }

    private TemplateManager mgr() { return new TemplateManager(tmp.resolve("world").resolve("generated"), REG); }

    private static int stateOf(StructureTemplate t) { return t.palettes().get(0).get(0).state(); }

    @Test
    void readsTemplateFromDataPack() throws IOException {
        TemplateManager.installDataPacks(manager(pack("p1", "mypack", "buildings/hut", "minecraft:stone", 3955)));
        TemplateManager m = mgr();
        StructureTemplate t = m.get(ResourceId.parse("mypack:buildings/hut")).orElseThrow();
        assertEquals(1, t.sizeX);
        assertEquals(REG.stateIdOrAir("minecraft:stone"), stateOf(t));
        assertTrue(m.get(ResourceId.parse("mypack:nope")).isEmpty(), "없는 이름은 없음");
        assertTrue(m.get(ResourceId.parse("other:buildings/hut")).isEmpty(), "이름공간이 다르면 없음");
    }

    @Test
    void upperPackOverridesLowerPack() throws IOException {
        Path low = pack("low", "mypack", "hut", "minecraft:stone", 3955);
        Path high = pack("high", "mypack", "hut", "minecraft:dirt", 3955);
        TemplateManager.installDataPacks(manager(low, high));
        assertEquals(REG.stateIdOrAir("minecraft:dirt"), stateOf(mgr().get(ResourceId.parse("mypack:hut")).orElseThrow()));
    }

    @Test
    void worldGeneratedOverridesDataPack() throws IOException {
        TemplateManager.installDataPacks(manager(pack("p1", "mypack", "hut", "minecraft:stone", 3955)));
        write(tmp.resolve("world/generated/mypack/structures/hut.nbt"), template("minecraft:gold_block", 3955));
        assertEquals(REG.stateIdOrAir("minecraft:gold_block"), stateOf(mgr().get(ResourceId.parse("mypack:hut")).orElseThrow()));
    }

    @Test
    void dataPackOverridesBuiltinMinecraftTemplate() throws IOException {
        TemplateManager.installDataPacks(manager(pack("p1", "minecraft", "igloo/top", "minecraft:stone", 3955)));
        StructureTemplate t = mgr().get(ResourceId.parse("minecraft:igloo/top")).orElseThrow();
        assertEquals(1, t.sizeX, "내장 igloo/top 이 아니라 팩의 1칸 템플릿");
    }

    @Test
    void builtinStillWorksWithoutDataPacks() {
        TemplateManager.installDataPacks(null);
        assertTrue(mgr().get(ResourceId.parse("minecraft:igloo/top")).isPresent());
    }

    @Test
    void reloadClearsRepository() throws IOException {
        TemplateManager.installDataPacks(manager(pack("a", "mypack", "hut", "minecraft:stone", 3955)));
        TemplateManager shared = TemplateManager.forWorld(tmp.resolve("world"), REG);
        assertEquals(REG.stateIdOrAir("minecraft:stone"), stateOf(shared.get(ResourceId.parse("mypack:hut")).orElseThrow()));
        TemplateManager.installDataPacks(manager(pack("b", "mypack", "hut", "minecraft:dirt", 3955)));
        assertEquals(REG.stateIdOrAir("minecraft:dirt"), stateOf(shared.get(ResourceId.parse("mypack:hut")).orElseThrow()), "리로드 뒤 새 팩에서 다시 읽음");
    }

    @Test
    void listTemplatesMergesPacksAndGenerated() throws IOException {
        Path low = pack("low", "mypack", "a/b", "minecraft:stone", 3955);
        write(low.resolve("data/mypack/structure/c.nbt"), template("minecraft:stone", 3955));
        Files.writeString(low.resolve("data/mypack/structure/readme.txt"), "x");
        Path high = pack("high", "mypack", "a/b", "minecraft:dirt", 3955);   // 겹친 이름은 한 번만
        TemplateManager.installDataPacks(manager(low, high));
        write(tmp.resolve("world/generated/minecraft/structures/saved.nbt"), template("minecraft:stone", 3955));
        List<String> ids = mgr().listTemplates().stream().map(ResourceId::toString).toList();
        assertEquals(List.of("mypack:a/b", "mypack:c", "minecraft:saved"), ids, "path 먼저 정렬(바닐라 ResourceLocation 순서)");
    }

    @Test
    void oldDataVersionTemplateIsStillRead() throws IOException {
        TemplateManager.installDataPacks(manager(pack("p1", "mypack", "old", "minecraft:stone", 2975)));
        assertTrue(mgr().get(ResourceId.parse("mypack:old")).isPresent(), "데이터 픽서가 없어도 읽는다(경고만)");
        TemplateManager.installDataPacks(manager(pack("p2", "mypack", "none", "minecraft:stone", 0)));
        assertTrue(mgr().get(ResourceId.parse("mypack:none")).isPresent(), "DataVersion 없는 파일도 읽는다");
    }

    @Test
    void corruptFileIsLoggedAndMissing() throws IOException {
        Path root = tmp.resolve("bad");
        Files.createDirectories(root.resolve("data/mypack/structure"));
        Files.writeString(root.resolve("data/mypack/structure/broken.nbt"), "not nbt");
        TemplateManager.installDataPacks(manager(root));
        assertTrue(mgr().get(ResourceId.parse("mypack:broken")).isEmpty());
    }
}
