package net.minecraft.server.level;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.domi.furnace.compat.nms.NmsChunks;

/**
 * 서버 빛 엔진(Mojang 이름 shim, N2b-6). Paper(Starlight)의 {@link #starlight$serverRelightChunks} 만 있다.
 *
 * <p>T-197: 서버가 실제로 빛을 다시 계산한다(설계 docs/feature-parity/compat-nms-n2b5-relight-2026-11-16.md). Paper 와 같이 로드된 청크만 받고,
 * 청크마다 그 청크와 둘레를 지금 블록으로 처음부터 계산해 그 청크에만 끼운 뒤 보는 플레이어에게 빛 패킷을 보내고, 청크마다 {@code chunkCallback},
 * 끝에 {@code onComplete}(받은 수)를 부른다. 콜백 스레드는 Paper 는 빛 일꾼 한 스레드, Furnace 는 요청마다 콜백 전용 스레드 한 줄이다(차례대로).
 * 층 듣기·checkBlock·getRawBrightness·queueSectionData(T-205)는 {@link LevelLightEngine} 에 있다.
 */
public class ThreadedLevelLightEngine extends LevelLightEngine {

    private final ServerLevel level;

    ThreadedLevelLightEngine(ServerLevel level) {
        super(level);
        this.level = level;
    }

    /** Paper: 그 청크들의 빛을 처음부터 다시 계산한다. 아무 스레드, 기다리지 않는다. 돌려주는 값은 받은(로드된) 청크 수. */
    public final int starlight$serverRelightChunks(Collection<ChunkPos> chunks, Consumer<ChunkPos> chunkCallback, IntConsumer onComplete) {
        LinkedHashSet<ChunkPos> unique = new LinkedHashSet<>(chunks);   // Paper 도 LinkedHashSet 으로 받는다
        int[] xs = new int[unique.size()], zs = new int[unique.size()];
        int i = 0;
        for (ChunkPos p : unique) {
            xs[i] = p.x;
            zs[i++] = p.z;
        }
        return NmsChunks.bridge().relightChunks(level.getWorld().furnaceHandle(), xs, zs,
                chunkCallback == null ? null : (x, z) -> chunkCallback.accept(new ChunkPos(x, z)), onComplete);
    }
}
