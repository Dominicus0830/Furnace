package net.minecraft.server.level;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.domi.furnace.compat.i18n.CompatLang;
import org.slf4j.LoggerFactory;

/**
 * 서버 빛 엔진(Mojang 이름 shim, N2b-6). Paper(Starlight)의 {@link #starlight$serverRelightChunks} 만 있다.
 *
 * <p>Furnace 는 아직 플러그인이 바꾼 섹션의 빛을 다시 계산하지 않는다(결정 D2 — 다시 계산 경로는 N2b-5). 그래서 이 메서드는 빛을 건드리지 않고,
 * Paper 처럼 청크마다 {@code chunkCallback}, 끝에 {@code onComplete}(다시 계산한 청크 수)를 불러 플러그인의 후처리(청크 다시 보내기·티켓 풀기)가
 * 이어지게 한다. 빛은 바뀌기 전 값 그대로 남는다(Paper 는 다시 계산한 값). 처음 한 번 경고를 남긴다.
 */
public class ThreadedLevelLightEngine extends LevelLightEngine {

    private static volatile boolean warned;

    private final ServerLevel level;

    ThreadedLevelLightEngine(ServerLevel level) { this.level = level; }

    /** Paper: 그 청크들의 빛을 처음부터 다시 계산한다. Furnace: 다시 계산하지 않고 알림만(위 설명). 돌려주는 값은 받아들인 청크 수. */
    public final int starlight$serverRelightChunks(Collection<ChunkPos> chunks, Consumer<ChunkPos> chunkCallback, IntConsumer onComplete) {
        if (!warned) {
            warned = true;
            LoggerFactory.getLogger("Furnace").warn(CompatLang.t("compat.nms-relight-unsupported", level.getWorld().getName(), chunks.size()));
        }
        int n = 0;
        for (ChunkPos pos : chunks) {
            if (chunkCallback != null) chunkCallback.accept(pos);
            n++;
        }
        if (onComplete != null) onComplete.accept(n);
        return n;
    }
}
