package ca.spottedleaf.moonrise.patches.chunk_system.io;

import ca.spottedleaf.concurrentutil.executor.Cancellable;
import ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor;
import java.util.function.BiConsumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.domi.furnace.compat.nms.NmsStorage;

/**
 * Paper(moonrise) 지역 파일 IO 스레드의 정적 읽기 길(NMS shim 5e — voxy 가 먼 거리 지형을 디스크에서 읽는다). Furnace 는 서버의 IO 차례(그 파일의 앞선 저장 뒤)에서
 * 읽는다 — 저장 대기분을 돌려주는 Paper 와 끝 상태가 같다. 우선순위는 받기만 한다(Furnace IO 차례는 들어온 순서). 저장·우선순위 바꾸기·비우기는 없다.
 */
public final class RegionFileIOThread {

    private RegionFileIOThread() {}

    /** Paper 와 같은 차례: region/·poi/·entities/. */
    public enum RegionFileType {
        CHUNK_DATA, POI_DATA, ENTITY_DATA
    }

    /** 지금 스레드가 지역 파일 IO 스레드인가. Furnace 는 읽은 결과·콜백을 서버 비동기 일꾼에서 내고 IO 차례에서 플러그인 코드를 돌리지 않으므로 늘 거짓. */
    public static boolean isRegionFileThread() { return false; }

    /**
     * 디스크에서 그 청크의 데이터를 읽어 콜백(데이터 또는 null, 오류 또는 null)에 넘긴다. 아무 스레드. Paper 는 콜백을 IO 스레드에서 부르지만 Furnace 의 IO 차례는
     * 저장도 맡으므로 서버 비동기 일꾼에서 부른다(콜백 안에서 {@link #loadData} 로 이어 읽어도 막히지 않는다).
     */
    public static Cancellable loadDataAsync(ServerLevel world, int chunkX, int chunkZ, RegionFileType type, BiConsumer<CompoundTag, Throwable> onComplete,
                                           boolean intendingToBlock) {
        return loadDataAsync(world, chunkX, chunkZ, type, onComplete, intendingToBlock, PrioritisedExecutor.Priority.NORMAL);
    }

    /** {@link #loadDataAsync(ServerLevel, int, int, RegionFileType, BiConsumer, boolean)} 와 같다(우선순위는 받기만 한다). */
    public static Cancellable loadDataAsync(ServerLevel world, int chunkX, int chunkZ, RegionFileType type, BiConsumer<CompoundTag, Throwable> onComplete,
                                           boolean intendingToBlock, PrioritisedExecutor.Priority priority) {
        if (!PrioritisedExecutor.Priority.isValidPriority(priority)) throw new IllegalArgumentException("Invalid priority: " + priority);
        java.util.concurrent.atomic.AtomicBoolean claimed = new java.util.concurrent.atomic.AtomicBoolean();
        NmsStorage.read(world, type.ordinal(), chunkX, chunkZ, "RegionFileIOThread#loadDataAsync").whenComplete((tag, err) -> {
            if (claimed.compareAndSet(false, true)) onComplete.accept(err == null ? tag : null, err == null ? null : NmsStorage.unwrap(err));
        });
        return () -> claimed.compareAndSet(false, true);
    }

    /**
     * 디스크에서 그 청크의 데이터를 읽어 돌려준다(없으면 null). 읽을 때까지 기다린다 — Paper 처럼 어느 스레드에서나 막고 기다리며, 구역 스레드에서 부르면 그 구역 틱이
     * 디스크 읽기만큼 늦는다.
     */
    public static CompoundTag loadData(ServerLevel world, int chunkX, int chunkZ, RegionFileType type, PrioritisedExecutor.Priority priority)
            throws java.io.IOException {
        try {
            return NmsStorage.read(world, type.ordinal(), chunkX, chunkZ, "RegionFileIOThread#loadData").join();
        } catch (java.util.concurrent.CompletionException e) {
            Throwable t = NmsStorage.unwrap(e);
            if (t instanceof java.io.IOException io) throw io;
            if (t instanceof RuntimeException re) throw re;
            if (t instanceof Error er) throw er;
            throw new java.io.IOException(t);
        }
    }
}
