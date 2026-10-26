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

    /** 이 클래스가 만든 IO 풀. 퇴장 저장이 IO 풀에서 늦게 돌아 @TempDir 삭제와 겹치지 않게 테스트 끝에 비운다(IoPoolDrainExtension). */
    static final java.util.Queue<java.util.concurrent.ExecutorService> IO_POOLS = new java.util.concurrent.ConcurrentLinkedQueue<>();

    /** 등록된 IO 풀을 닫고 남은 저장 작업이 끝날 때까지 기다린다. */
    public static void drainIoPools() throws InterruptedException {
        java.util.concurrent.ExecutorService e;
        while ((e = IO_POOLS.poll()) != null) {
            e.shutdown();
            if (!e.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("IO 풀이 30초 안에 끝나지 않았다");
        }
    }

    public static ServerCore core(Path tmp, FurnaceSchedulerImpl sched) {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        World world = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, 1, 16, 2, 2,
                reg.directBits(), 7, ioPool());
        world.worldDir(tmp);
        ServerPropertiesFile settings = new ServerPropertiesFile(tmp.resolve("server.properties"));
        PlayerLists lists = new PlayerLists(tmp, settings);
        LevelData level = LevelData.create("world", 1234L, true, false, GameMode.SURVIVAL, false, Difficulty.EASY, List.of("vanilla"), List.of());
        return new ServerCore(settings, lists, new GameProfileCache(ProfileLookup.NONE, tmp.resolve("usercache.json")), world, level, sched);
    }

    /** 테스트 끝에 비워지는 단일 스레드 IO 풀. worldDir 을 @TempDir 로 쓰는 World 는 이것을 쓴다. */
    public static java.util.concurrent.ExecutorService ioPool() {
        var e = java.util.concurrent.Executors.newSingleThreadExecutor();
        IO_POOLS.add(e);
        return e;
    }
}
