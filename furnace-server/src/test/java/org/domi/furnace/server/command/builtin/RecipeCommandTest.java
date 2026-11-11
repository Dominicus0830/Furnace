// /recipe give|take: 새로 배운 수만 세고(바닐라 ServerRecipeBook), 이미 아는 것·모르는 레시피·알려지지 않은 이름은 오류 키로 끝난다.
package org.domi.furnace.server.command.builtin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.data.DataPackManager;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecipeCommandTest {

    @TempDir Path tmp;
    private static final Path VANILLA = Path.of("../tools/vanilla-data/server-1.21.1");
    FurnaceSchedulerImpl sched;
    ServerCore core;
    DataPackManager packs;
    Player player;

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    @BeforeEach
    void setUp() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(VANILLA.resolve("data/minecraft/recipe")), "로컬 바닐라 데이터 없음");
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        core.world().setRegistries(org.domi.furnace.server.world.BlockRegistry.load("blocks/1.21.1.txt"), org.domi.furnace.server.world.ItemRegistry.load("items/1.21.1.txt"));
        packs = DataPackManager.start(VANILLA, tmp, org.domi.furnace.server.world.storage.LevelData.create("world", 1L, true, false,
                org.domi.furnace.server.world.GameMode.SURVIVAL, false, org.domi.furnace.server.world.Difficulty.EASY, List.of("vanilla"), List.of()),
                RegistrySet.load("registries/1.21.1.txt"), true, List.of("vanilla"), List.of());
        core.dataPacks(packs);
        player = new Player(core.world().entityIds().next(), UUID.randomUUID(), "Steve", new ConcurrentEmbeddedChannel());
        player.applyMove(0.5, -60, 0.5, 0, 0, true);
        var in = new CountDownLatch(1);
        core.world().runAt(0.5, 0.5, r -> {
            r.adopt(player);
            in.countDown();
        });
        assertTrue(in.await(10, TimeUnit.SECONDS));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (sched != null) sched.close();
        if (packs != null) packs.close();
        TestServers.drainIoPools();
    }

    private CommandSource console() { return CommandSource.console(core); }

    private int run(String cmd) {
        return core.commands().performPrefixedCommandAsync(console(), cmd).join().result();
    }

    @Test
    void giveThenGiveAgainFailsAndTakeForgets() {
        assertNotNull(core.recipes(), "서버가 레시피를 읽었어야 한다");
        assertEquals(1, run("recipe give Steve minecraft:stick"), "새로 배운 수");
        assertEquals(0, run("recipe give Steve minecraft:stick"), "이미 아는 레시피는 실패(ERROR_GIVE_FAILED)");
        assertEquals(1, run("recipe take Steve minecraft:stick"));
        assertEquals(0, run("recipe take Steve minecraft:stick"), "모르는 것을 잊게 하면 실패(ERROR_TAKE_FAILED)");
    }

    @Test
    void unknownRecipeIsRejected() {
        assertEquals(0, run("recipe give Steve minecraft:no_such_recipe"));
        assertEquals(0, run("recipe take Steve minecraft:no_such_recipe"));
    }

    @Test
    void starGivesEveryRecipe() {
        int all = core.recipes().size();
        assertTrue(all > 100, "기본 레시피 수 " + all);
        int learned = run("recipe give Steve *");
        assertTrue(learned > 0 && learned < all, "특수 레시피(바닐라 isSpecial)는 책에 넣지 않는다: " + learned + "/" + all);
        assertEquals(learned, run("recipe take Steve *"), "배운 것만큼 잊는다");
    }

    @Test
    void commandNeedsOperatorPermission() {
        var low = CommandSource.player(core, player).withPermission(0);
        assertEquals(0, core.commands().performPrefixedCommandAsync(low, "recipe give Steve minecraft:stick").join().result());
        assertEquals(0, run("recipe take Nobody minecraft:stick"), "없는 대상은 실패");
    }
}
