// 테스트용 ServerCore(슈퍼플랫 월드·임시 폴더의 설정/목록 파일).
package org.domi.furnace.server;

import java.nio.file.Path;
import java.util.List;
import org.domi.furnace.server.config.ServerPropertiesFile;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.players.GameProfileCache;
import org.domi.furnace.server.players.PlayerLists;
import org.domi.furnace.server.players.ProfileLookup;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.Difficulty;
import org.domi.furnace.server.world.GameMode;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.domi.furnace.server.world.storage.LevelData;

public final class TestServers {

    private TestServers() {}

    public static ServerCore core(Path tmp, FurnaceSchedulerImpl sched) {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        World world = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, 1, 16, 2, 2,
                reg.directBits(), 7, java.util.concurrent.Executors.newSingleThreadExecutor());
        world.worldDir(tmp);
        ServerPropertiesFile settings = new ServerPropertiesFile(tmp.resolve("server.properties"));
        PlayerLists lists = new PlayerLists(tmp, settings);
        LevelData level = LevelData.create("world", 1234L, true, false, GameMode.SURVIVAL, false, Difficulty.EASY, List.of("vanilla"), List.of());
        return new ServerCore(settings, lists, new GameProfileCache(ProfileLookup.NONE, tmp.resolve("usercache.json")), world, level, sched);
    }
}
