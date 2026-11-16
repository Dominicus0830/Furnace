package net.minecraft.server.level;

import java.util.concurrent.CompletableFuture;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * 청크 자리 하나(Mojang 이름 shim, N2b-6): {@code ChunkMap#getVisibleChunkIfPresent} 가 준다. Furnace 는 청크 자리 객체를 따로 두지 않으므로
 * 부를 때마다 서버 청크를 본다 — 로드돼 있으면 그 청크 보기({@link LevelChunk}), 아니면 {@link #UNLOADED_LEVEL_CHUNK}.
 * Furnace 의 로드된 청크는 늘 다 만든 청크이고 서버가 틱 등급을 알려 주지 않으므로, 틱·엔티티 틱 미래도 로드된 청크를 준다 —
 * <b>Paper 와 다른 점</b>: Paper 는 청크가 그 틱 등급에 못 미치면 아직 안 끝난 미래를 준다(문서 compat-nms-n2b6 "Paper 와 다른 점"). 블록 변경 알림(blockChanged)은 아직 없다.
 */
public class ChunkHolder {

    public static final ChunkResult<LevelChunk> UNLOADED_LEVEL_CHUNK = ChunkResult.error("Unloaded level chunk");
    private static final CompletableFuture<ChunkResult<LevelChunk>> UNLOADED_LEVEL_CHUNK_FUTURE = CompletableFuture.completedFuture(UNLOADED_LEVEL_CHUNK);

    private final ServerLevel level;
    private final ChunkPos pos;

    ChunkHolder(ServerLevel level, ChunkPos pos) {
        this.level = level;
        this.pos = pos;
    }

    public ChunkPos getPos() { return pos; }

    public LevelChunk getFullChunkNow() { return level.getChunkSource().getChunkNow(pos.x, pos.z); }

    public LevelChunk getFullChunkNowUnchecked() { return getFullChunkNow(); }

    private CompletableFuture<ChunkResult<LevelChunk>> loadedFuture() {
        LevelChunk c = getFullChunkNow();
        return c == null ? UNLOADED_LEVEL_CHUNK_FUTURE : CompletableFuture.completedFuture(ChunkResult.of(c));
    }

    public CompletableFuture<ChunkResult<LevelChunk>> getTickingChunkFuture() { return loadedFuture(); }

    public CompletableFuture<ChunkResult<LevelChunk>> getEntityTickingChunkFuture() { return loadedFuture(); }

    public CompletableFuture<ChunkResult<LevelChunk>> getFullChunkFuture() { return loadedFuture(); }

    public final LevelChunk getTickingChunk() { return getFullChunkNow(); }

    public LevelChunk getChunkToSend() { return getFullChunkNow(); }

    /** Paper(Moonrise) 청크 시스템에는 이 필드의 대응이 없어 어댑터들이 늘 참으로 본다 — 여기서도 로드돼 있으면 참. */
    public boolean wasAccessibleSinceLastSave() { return getFullChunkNow() != null; }

    @Override
    public String toString() { return "ChunkHolder[" + level.getWorld().getName() + " " + pos + "]"; }
}
