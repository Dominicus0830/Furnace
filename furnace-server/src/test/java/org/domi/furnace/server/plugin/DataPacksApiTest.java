// T-141 E4b: Furnace.dataPacks() — 목록·켜짐·찾기·켜고 끄기(Paper PaperDatapack.setEnabled: /datapack 기본 자리, 이미 그 상태면 아무것도 안 함, 없는 팩 ISE).
package org.domi.furnace.server.plugin;

import org.domi.furnace.api.GameVersion;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.datapack.DataPacks;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.data.DataPackManager;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataPacksApiTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    DataPackManager packs;

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        Path vanilla = tmp.resolve("vanilla");
        Files.createDirectories(vanilla.resolve("data/minecraft/tags/item"));
        Files.writeString(vanilla.resolve("data/minecraft/tags/item/x.json"), "{\"values\":[]}");
        pack("mine", "\"pack\":{\"description\":\"내 팩\",\"pack_format\":48}");
        pack("old", "\"pack\":{\"description\":\"옛 팩\",\"pack_format\":10}");
        packs = DataPackManager.start(vanilla, tmp, org.domi.furnace.server.world.storage.LevelData.create("world", 1L, true, false,
                org.domi.furnace.server.world.GameMode.SURVIVAL, false, org.domi.furnace.server.world.Difficulty.EASY, List.of("vanilla"), List.of()),
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt"), true, List.of("vanilla"), List.of());
        core.dataPacks(packs);
    }

    private void pack(String name, String mcmeta) throws Exception {
        Path p = tmp.resolve("datapacks/" + name);
        Files.createDirectories(p.resolve("data"));
        Files.writeString(p.resolve("pack.mcmeta"), "{" + mcmeta + "}");
    }

    @AfterEach
    void tearDown() throws Exception {
        core.plugins().close();
        sched.close();
        if (packs != null) packs.close();
        TestServers.drainIoPools();
    }

    private static List<String> ids(List<DataPacks.Pack> l) { return l.stream().map(DataPacks.Pack::id).toList(); }

    @Test
    void listsAndTogglesLikeDatapackCommand() throws Exception {
        DataPacks api = core.plugins().dataPacks();
        assertEquals(List.of("file/mine", "file/old", "vanilla"), ids(api.available()));
        var mine = api.find("file/mine").orElseThrow();
        assertEquals(DataPacks.Source.WORLD, mine.source());
        assertEquals(DataPacks.Compatibility.COMPATIBLE, mine.compatibility());
        assertEquals("내 팩", mine.description().plainText());
        assertFalse(mine.required());
        var old = api.find("file/old").orElseThrow();
        assertEquals(DataPacks.Compatibility.TOO_OLD, old.compatibility());
        var vanilla = api.find("vanilla").orElseThrow();
        assertEquals(DataPacks.Source.BUILT_IN, vanilla.source());
        assertTrue(vanilla.enabled());
        assertTrue(api.find("nope").isEmpty());

        // 처음 시작에 켜진 것: vanilla + 새로 보인 월드 팩(자동으로 켜짐), 호환 안 되는 팩은 꺼진 채로(바닐라)
        List<String> before = ids(api.enabled());
        assertTrue(before.contains("vanilla"), before.toString());

        assertTrue(api.find("file/mine").orElseThrow().enabled());
        assertTrue(api.available().stream().filter(p -> p.id().equals("file/mine")).findFirst().orElseThrow().enabled());
        assertTrue(api.available().stream().filter(p -> p.id().equals("vanilla")).findFirst().orElseThrow().enabled());
        assertTrue(api.enabled().stream().allMatch(DataPacks.Pack::enabled));

        api.setEnabled("file/mine", false).get(20, TimeUnit.SECONDS);
        assertFalse(ids(api.enabled()).contains("file/mine"));
        assertFalse(api.available().stream().filter(p -> p.id().equals("file/mine")).findFirst().orElseThrow().enabled(), "available 의 켜짐 표시");
        assertTrue(api.available().stream().filter(p -> p.id().equals("vanilla")).findFirst().orElseThrow().enabled());
        assertTrue(api.enabled().stream().allMatch(DataPacks.Pack::enabled));
        assertFalse(api.find("file/mine").orElseThrow().enabled());
        assertTrue(api.setEnabled("file/mine", false).isDone(), "이미 꺼져 있으면 바로 끝난다");

        api.setEnabled("file/mine", true).get(20, TimeUnit.SECONDS);
        List<String> after = ids(api.enabled());
        assertEquals("file/mine", after.get(after.size() - 1), "/datapack enable 기본 자리(위쪽 끝)");
        assertTrue(api.find("file/mine").orElseThrow().enabled());

        var e = assertThrows(IllegalStateException.class, () -> api.setEnabled("nope", true));
        assertEquals("Cannot toggle state of pack that doesn't exist: nope", e.getMessage());
    }

    @Test
    void refreshSeesNewFolderWithoutEnablingIt() throws Exception {
        DataPacks api = core.plugins().dataPacks();
        List<String> enabled = ids(api.enabled());
        pack("later", "\"pack\":{\"description\":\"나중\",\"pack_format\":48}");
        assertTrue(api.find("file/later").isEmpty());
        api.refresh();
        assertFalse(api.find("file/later").orElseThrow().enabled());
        assertEquals(enabled, ids(api.enabled()), "refresh 는 켜진 목록을 바꾸지 않는다");
    }
}
