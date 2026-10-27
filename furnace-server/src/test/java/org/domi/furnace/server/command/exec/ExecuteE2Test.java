// /execute E2 단계(if|unless entity·block·blocks·biome·loaded·dimension·predicate·items)의 참/거짓·반전·개수·메시지,
// 포크와의 상호작용(as 뒤에서 소스가 걸러지는지), 조회성 조건이 region 을 만들지 않는지.
package org.domi.furnace.server.command.exec;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.LiteralArgumentBuilder;
import org.domi.furnace.server.data.DataPackManager;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.DimensionType;
import org.domi.furnace.server.world.Dimensions;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExecuteE2Test {

    private static final DimensionType NETHER = new DimensionType("minecraft:the_nether", "minecraft:the_nether", -64, 384, 384,
            false, true, true, false, 8.0, 18000L, false, true, true, NoiseWorldSource.Dimension.NETHER);

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    ChunkColumn column;
    Cow[] cows;
    DataPackManager packs;
    final List<CommandSource> probed = Collections.synchronizedList(new ArrayList<>());
    final List<String> messages = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        column = core.world().awaitChunk(new ChunkPos(0, 0));
        cows = DeferredResultTest.spawnCows(core, 2);
        core.addConsoleListener(t -> messages.add(t.getString()));
        var reg = org.domi.furnace.server.world.BlockRegistry.load("blocks/1.21.1.txt");
        World nether = new World(NETHER, new SuperflatWorldSource(reg, 0), new org.domi.furnace.server.net.ChunkWorkerPool(1), sched, 1, 4, 1, 2, 2,
                reg.directBits(), 7, TestServers.ioPool(), TestServers.ioPool());
        Dimensions dims = new Dimensions();
        dims.register(core.world());
        dims.register(nether);
        core.dimensions(dims);
        core.commands().getDispatcher().register(LiteralArgumentBuilder.<CommandSource>literal("probe").executes(c -> {
            probed.add(c.getSource());
            return 1;
        }));
        inRegion(() -> {
            cows[0].addTag("a");
            cows[1].addTag("b");
        });
    }

    @AfterEach
    void tearDown() {
        sched.close();
        if (packs != null) packs.close();
    }

    private <T> T inRegion(java.util.function.Supplier<T> task) {
        return core.world().submitAtChunk(new ChunkPos(0, 0), r -> task.get()).orTimeout(10, TimeUnit.SECONDS).join();
    }

    private void inRegion(Runnable task) { inRegion(() -> { task.run(); return null; }); }

    private CommandResult run(String command) {
        messages.clear();
        return core.commands().performPrefixedCommandAsync(CommandSource.console(core), command).orTimeout(20, TimeUnit.SECONDS).join();
    }

    private void set(int x, int y, int z, String block) {
        int state = B.defaultState(block);
        inRegion(() -> column.setBlock(x, y, z, state));
    }

    private void loadPacks(String predicateName, String predicateJson) throws IOException {
        Path vanilla = tmp.resolve("vanilla");
        Files.createDirectories(vanilla.resolve("data/minecraft/tags/item"));
        Files.writeString(vanilla.resolve("data/minecraft/tags/item/x.json"), "{\"values\":[]}");
        Path pack = tmp.resolve("datapacks/mine");
        Files.createDirectories(pack.resolve("data/test/predicate"));
        Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"description\":\"t\",\"pack_format\":48}}");
        Files.writeString(pack.resolve("data/test/predicate/" + predicateName + ".json"), predicateJson);
        packs = DataPackManager.start(vanilla, tmp, org.domi.furnace.server.world.storage.LevelData.create("world", 1L, true, false,
                org.domi.furnace.server.world.GameMode.SURVIVAL, false, org.domi.furnace.server.world.Difficulty.EASY, List.of("vanilla"), List.of()),
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt"), true, List.of("vanilla"), List.of());
        core.dataPacks(packs);
    }

    // ---------------------------------------------------------------- entity

    @Test
    void entityTerminalCountsMatchesAndUnlessInvertsWithFailCount() {
        CommandResult r = run("execute if entity @e[type=cow]");
        assertEquals(new CommandResult(true, 2, 1), r, "if entity 의 결과는 일치 수");
        assertEquals(List.of("Test passed, count: 2"), messages);
        r = run("execute if entity @e[type=cow,tag=none]");
        assertEquals(new CommandResult(false, 0, 1), r);
        assertEquals(List.of("Test failed"), messages);
        r = run("execute unless entity @e[type=cow,tag=none]");
        assertEquals(new CommandResult(true, 1, 1), r);
        assertEquals(List.of("Test passed"), messages);
        r = run("execute unless entity @e[type=cow]");
        assertEquals(new CommandResult(false, 0, 1), r);
        assertEquals(List.of("Test failed, count: 2"), messages, "unless 가 실패하면 일치 수를 알린다");
    }

    @Test
    void entityForkKeepsOrDropsTheSource() {
        CommandResult r = run("execute if entity @e[tag=a] run tag @e[tag=b] add hit");
        assertEquals(1, r.callbacks());
        assertTrue(inRegion(() -> cows[1].getTags().contains("hit")));
        assertEquals(CommandResult.NONE, run("execute if entity @e[tag=none] run tag @e[tag=b] add miss"));
        assertFalse(inRegion(() -> cows[1].getTags().contains("miss")));
        assertEquals(1, run("execute unless entity @e[tag=none] run probe").callbacks());
        assertEquals(CommandResult.NONE, run("execute unless entity @e[tag=a] run probe"));
    }

    @Test
    void conditionsFilterForkedSourcesPerEntity() {
        // as 가 소 둘로 포크하고, 각 소스에서 @s[tag=a] 가 하나만 남긴다
        CommandResult r = run("execute as @e[type=cow] if entity @s[tag=a] run tag @s add only");
        assertEquals(1, r.callbacks());
        assertTrue(inRegion(() -> cows[0].getTags().contains("only")));
        assertFalse(inRegion(() -> cows[1].getTags().contains("only")));
        // unless 는 반대쪽만 남긴다
        assertEquals(1, run("execute as @e[type=cow] unless entity @s[tag=a] run tag @s add other").callbacks());
        assertTrue(inRegion(() -> cows[1].getTags().contains("other")));
        // 조건을 여러 번 이으면 모두 통과해야 한다
        assertEquals(1, run("execute as @e[type=cow] if entity @s[tag=a] unless entity @s[tag=b] if dimension minecraft:overworld run probe").callbacks());
    }

    // ---------------------------------------------------------------- block / loaded / dimension

    @Test
    void blockMatchesStateInOwningRegionAndReportsUnloadedAndOutOfWorld() {
        set(3, -60, 3, "minecraft:stone");
        assertEquals(new CommandResult(true, 1, 1), run("execute if block 3 -60 3 minecraft:stone"));
        assertEquals(List.of("Test passed"), messages);
        assertEquals(new CommandResult(false, 0, 1), run("execute if block 3 -60 3 minecraft:dirt"));
        assertEquals(new CommandResult(true, 1, 1), run("execute unless block 3 -60 3 minecraft:dirt"));
        assertEquals(new CommandResult(false, 0, 1), run("execute unless block 3 -60 3 minecraft:stone"));
        assertEquals(1, run("execute if block 3 -60 3 minecraft:stone run probe").callbacks());
        assertEquals(CommandResult.NONE, run("execute if block 3 -60 3 minecraft:dirt run probe"));
        // 상대 좌표는 소스 위치 기준
        assertEquals(1, run("execute positioned 3 -61 3 if block ~ ~1 ~ minecraft:stone run probe").callbacks());

        int before = core.world().regionCount();
        assertEquals(new CommandResult(false, 0, 1), run("execute if block 1000000 0 1000000 minecraft:stone"));
        assertEquals(List.of("That position is not loaded"), messages);
        assertEquals(CommandResult.NONE, run("execute unless block 1000000 0 1000000 minecraft:stone run probe"), "로드 안 된 칸은 unless 도 참이 아니라 오류");
        assertEquals(before, core.world().regionCount(), "조회성 조건이 region 을 만들었다");
        // 청크는 로드돼 있지만 월드 높이 밖
        assertEquals(new CommandResult(false, 0, 1), run("execute if block 3 5000 3 minecraft:stone"));
        assertEquals(List.of("That position is out of this world!"), messages);
    }

    @Test
    void loadedAndDimensionAreBooleans() {
        int before = core.world().regionCount();
        assertEquals(new CommandResult(true, 1, 1), run("execute if loaded 0 0 0"));
        assertEquals(new CommandResult(false, 0, 1), run("execute if loaded 1000000 0 1000000"));
        assertEquals(new CommandResult(true, 1, 1), run("execute unless loaded 1000000 0 1000000"));
        assertEquals(CommandResult.NONE, run("execute if loaded 1000000 0 1000000 run probe"));
        assertEquals(before, core.world().regionCount(), "if loaded 가 region 을 만들었다");

        assertEquals(new CommandResult(true, 1, 1), run("execute if dimension minecraft:overworld"));
        assertEquals(new CommandResult(false, 0, 1), run("execute if dimension minecraft:the_nether"));
        assertEquals(new CommandResult(true, 1, 1), run("execute unless dimension minecraft:the_nether"));
        assertEquals(1, run("execute in minecraft:the_nether if dimension minecraft:the_nether run probe").callbacks());
        assertEquals("minecraft:the_nether", probed.get(probed.size() - 1).level().dimension());
    }

    // ---------------------------------------------------------------- blocks

    @Test
    void blocksCountsMatchedCellsAndMaskedSkipsAir() {
        for (int x = 2; x <= 3; x++) for (int z = 2; z <= 3; z++) {
            set(x, -60, z, "minecraft:stone");
            set(x + 6, -60, z, "minecraft:stone");
        }
        // 2x1x2 영역 4칸, 같은 모양의 사본
        assertEquals(new CommandResult(true, 4, 1), run("execute if blocks 2 -60 2 3 -60 3 8 -60 2 all"));
        assertEquals(List.of("Test passed, count: 4"), messages);
        assertEquals(new CommandResult(false, 0, 1), run("execute unless blocks 2 -60 2 3 -60 3 8 -60 2 all"));
        assertEquals(List.of("Test failed, count: 4"), messages);
        // 사본 한 칸을 바꾸면 불일치
        set(9, -60, 3, "minecraft:dirt");
        assertEquals(new CommandResult(false, 0, 1), run("execute if blocks 2 -60 2 3 -60 3 8 -60 2 all"));
        assertEquals(new CommandResult(true, 1, 1), run("execute unless blocks 2 -60 2 3 -60 3 8 -60 2 all"));
        assertEquals(CommandResult.NONE, run("execute if blocks 2 -60 2 3 -60 3 8 -60 2 all run probe"));
        assertEquals(1, run("execute unless blocks 2 -60 2 3 -60 3 8 -60 2 all run probe").callbacks());
        // masked: 원본이 공기인 칸은 건너뛴다 — 위쪽 줄(y=-59)은 원본 전부 공기라 0칸이 일치(0 도 "있음")
        assertEquals(new CommandResult(true, 0, 1), run("execute if blocks 2 -59 2 3 -59 3 8 -59 2 masked"));
        assertEquals(List.of("Test passed, count: 0"), messages);
        assertEquals(new CommandResult(false, 0, 1), run("execute unless blocks 2 -59 2 3 -59 3 8 -59 2 masked"));
        assertEquals(List.of("Test failed, count: 0"), messages);
        // 사본의 흙 한 칸: masked 에서도 원본(돌)이 공기가 아니므로 불일치
        assertEquals(CommandResult.NONE, run("execute if blocks 2 -60 2 3 -60 3 8 -60 2 masked run probe"));
        // 영역이 겹쳐도 읽기만 하므로 자기 자신과는 항상 일치
        assertEquals(new CommandResult(true, 4, 1), run("execute if blocks 2 -60 2 3 -60 3 2 -60 2 all"));
    }

    @Test
    void blocksRejectsTooBigAndUnloadedAreas() {
        assertEquals(new CommandResult(false, 0, 1), run("execute if blocks 0 -64 0 15 100 15 0 -64 0 all"));
        assertEquals(List.of("Too many blocks in the specified area (maximum 32768, specified 42240)"), messages);
        int before = core.world().regionCount();
        // 대상이 다른 region/미로드 청크
        assertEquals(new CommandResult(false, 0, 1), run("execute if blocks 0 -60 0 1 -60 1 160 -60 160 all"));
        assertEquals(List.of("That position is not loaded"), messages);
        assertEquals(new CommandResult(false, 0, 1), run("execute if blocks 1000000 0 1000000 1000001 0 1000001 0 -60 0 all"));
        assertEquals(before, core.world().regionCount(), "if blocks 가 region 을 만들었다");
    }

    // ---------------------------------------------------------------- biome

    @Test
    void biomeComparesWithTheBiomeAtThePosition() throws Exception {
        loadPacks("always", "{\"condition\":\"minecraft:random_chance\",\"chance\":1.0}");
        var names = packs.loot().registries().entries("minecraft:worldgen/biome");
        assertFalse(names.isEmpty());
        long seed = core.biomeZoomSeed();
        int[] q = org.domi.furnace.server.world.BiomeZoom.quart(seed, 3, -60, 3);
        int id = inRegion(() -> core.world().regionizer().ownerOf(0, 0).biomeIdAtQuart(q[0], q[1], q[2]));
        assertTrue(id >= 0, "시험 청크에 바이옴이 있어야 한다");
        String here = names.get(id);
        String other = names.stream().filter(n -> !n.equals(here)).findFirst().orElseThrow();
        assertEquals(new CommandResult(true, 1, 1), run("execute if biome 3 -60 3 " + here));
        assertEquals(new CommandResult(false, 0, 1), run("execute if biome 3 -60 3 " + other));
        assertEquals(new CommandResult(true, 1, 1), run("execute unless biome 3 -60 3 " + other));
        assertEquals(CommandResult.NONE, run("execute if biome 3 -60 3 " + other + " run probe"));
        int before = core.world().regionCount();
        assertEquals(new CommandResult(false, 0, 1), run("execute if biome 1000000 0 1000000 " + here));
        assertEquals(before, core.world().regionCount());
        assertEquals(new CommandResult(false, 0, 1), run("execute if biome 3 -60 3 minecraft:not_a_biome"));
    }

    // ---------------------------------------------------------------- predicate

    @Test
    void predicateEvaluatesRegisteredAndInlineConditions() throws Exception {
        loadPacks("always", "{\"condition\":\"minecraft:random_chance\",\"chance\":1.0}");
        assertEquals(new CommandResult(true, 1, 1), run("execute if predicate test:always"));
        assertEquals(new CommandResult(false, 0, 1), run("execute unless predicate test:always"));
        assertEquals(new CommandResult(true, 1, 1), run("execute if predicate {condition:\"minecraft:inverted\",term:{condition:\"minecraft:random_chance\",chance:0.0}}"));
        assertEquals(CommandResult.NONE, run("execute if predicate {condition:\"minecraft:random_chance\",chance:0.0} run probe"));
        assertEquals(new CommandResult(false, 0, 1), run("execute if predicate test:missing"));
        // 소스 엔티티는 this_entity: entity_properties 의 type 으로 판정
        String isCow = "{condition:\"minecraft:entity_properties\",entity:\"this\",predicate:{type:\"minecraft:cow\"}}";
        assertEquals(2, run("execute as @e[type=cow] if predicate " + isCow + " run probe").callbacks());
        assertEquals(CommandResult.NONE, run("execute as @e[type=cow] unless predicate " + isCow + " run probe"));
    }

    // ---------------------------------------------------------------- items

    @Test
    void itemsEntitySumsMatchingSlots() {
        assertEquals(1, run("item replace entity @e[tag=a] weapon.mainhand with minecraft:stone 5").callbacks());
        assertEquals(new CommandResult(true, 5, 1), run("execute if items entity @e[tag=a] weapon.mainhand minecraft:stone"));
        assertEquals(List.of("Test passed, count: 5"), messages);
        assertEquals(new CommandResult(false, 0, 1), run("execute if items entity @e[tag=a] weapon.mainhand minecraft:dirt"));
        assertEquals(new CommandResult(false, 0, 1), run("execute unless items entity @e[tag=a] weapon.mainhand minecraft:stone"));
        assertEquals(List.of("Test failed, count: 5"), messages);
        assertEquals(new CommandResult(true, 1, 1), run("execute unless items entity @e[tag=b] weapon.mainhand minecraft:stone"));
        assertEquals(1, run("execute as @e[type=cow] if items entity @s weapon.mainhand minecraft:stone run probe").callbacks());
        // 대상이 없으면 오류(getEntities)
        assertEquals(new CommandResult(false, 0, 1), run("execute if items entity @e[tag=none] weapon.mainhand *"));
        assertEquals(List.of("No entity was found"), messages);
    }

    @Test
    void itemsBlockRequiresAContainer() {
        set(5, -60, 5, "minecraft:stone");
        int before = core.world().regionCount();
        assertEquals(new CommandResult(false, 0, 1), run("execute if items block 5 -60 5 container.0 *"));
        assertEquals(List.of("Source position 5, -60, 5 is not a container"), messages);
        assertEquals(new CommandResult(false, 0, 1), run("execute if items block 1000000 0 1000000 container.0 *"));
        assertEquals(before, core.world().regionCount());
    }
}
