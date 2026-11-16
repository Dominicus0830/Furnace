package net.minecraft.world.level.chunk;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.domi.furnace.compat.nms.NmsChunks;

/**
 * 로드된 청크 shim(Mojang 이름, N2a): 서버 청크의 <b>보기</b>({@link ChunkAccess} 참고). {@code ServerLevel#getChunk} 등이 돌려준다.
 * 블록 쓰기 {@link #setBlockState} 는 바닐라처럼 그 칸만 바꾼다(이웃·모양 갱신, 클라이언트 알림, POI 없음 — 블록 자체의 놓기·치우기 반응, 블록 엔티티,
 * 빛, 하이트맵, 저장 표시는 바닐라대로). 블록 엔티티 칸 목록(getBlockEntitiesPos)은 있고, 블록 엔티티 객체(getBlockEntity)·엔티티·틱 상태
 * (getFullStatus)는 아직 없다. 다른 스레드에서 읽으면 공개 스냅샷과 자기 쓰기(N2b-3·4), 다른 스레드의 쓰기는 거울 일지로 주인이 재생한다(N2b-4,
 * {@link ChunkAccess} 참고). 청크가 내려가면 이 shim 의 섹션은 그 순간 내용으로 굳는다(바닐라: 떨어진 LevelChunk).
 */
public class LevelChunk extends ChunkAccess {

    /** 이 청크의 월드(Paper 는 공개 필드 — 어댑터가 바로 읽는다). */
    public final net.minecraft.server.level.ServerLevel level;
    /** Paper 가 더한 청크 좌표 필드(N2b-6). */
    public final int locX;
    public final int locZ;
    /**
     * Paper: 청크 시스템이 올려 둔 동안 참. Furnace 는 이 보기를 로드된 청크로만 만들므로 참으로 시작한다(내린 뒤 옛 보기도 참으로 남는다 —
     * 내림 여부는 {@code getLevel().hasChunk} 로 본다).
     */
    public boolean loaded = true;
    /** 바닐라: 참이면 저장하지 않는다. Furnace 는 이 필드를 보지 않는다(받기만 한다 — 설계 3.8). */
    public boolean mustNotSave;
    private volatile boolean lightCorrect = true;

    private LevelChunk(Level level, int chunkX, int chunkZ) {
        super(level.getWorld().furnaceHandle(), chunkX, chunkZ);
        this.level = (net.minecraft.server.level.ServerLevel) level;
        this.locX = chunkX;
        this.locZ = chunkZ;
    }

    /** 바닐라 ChunkAccess.isLightCorrect: 마지막으로 setLightCorrect 한 값(Furnace 빛은 늘 계산돼 있어 처음은 참). */
    public boolean isLightCorrect() { return lightCorrect; }

    /** 바닐라 ChunkAccess.setLightCorrect: 표시를 바꾸고 저장 표시를 세운다(설계 R5c — 바닐라처럼). 빛을 다시 계산하지는 않는다. */
    public void setLightCorrect(boolean correct) {
        this.lightCorrect = correct;
        setUnsaved(true);
    }

    /** 호환 모듈 전용: 로드된 청크의 새 보기(로드 여부는 부르는 쪽이 확인한다). 정체성 캐시({@code NmsChunks.loadedChunk})만 부른다. */
    public static LevelChunk furnaceView(Level level, int chunkX, int chunkZ) { return new LevelChunk(level, chunkX, chunkZ); }

    public Level getLevel() { return level; }

    /**
     * 높이 밖·공기뿐인 섹션은 minecraft:air(바닐라 LevelChunk.getBlockState — 섹션의 바닐라 hasOnlyAir(nonEmptyBlockCount == 0)를 본다. 동굴 공기만
     * 있는 섹션도 air 를 주고, 컨테이너 원시 쓰기로 개수가 0 인 채 블록이 든 섹션도 바닐라처럼 air 를 준다).
     */
    @Override
    public BlockState getBlockState(BlockPos pos) {
        int y = pos.getY();
        if (isOutsideBuildHeight(y)) return Blocks.AIR.defaultBlockState();
        if (!furnaceMirror.dead() && furnaceOwner()) return NmsChunks.state(furnaceVanillaStateIdAt(pos.getX(), y, pos.getZ()));   // 맞춘 뒤 살아 있는 값
        // 다른 스레드·내린 청크: 바닐라처럼 섹션 배열을 거친다 — 플러그인이 넣은 섹션·자기 쓰기·떼어 낸 내용을 그대로 읽는다(N2b-4)
        LevelChunkSection s = sections[getSectionIndex(y)];
        if (s == null || s.hasOnlyAir()) return Blocks.AIR.defaultBlockState();
        return s.getBlockState(pos.getX() & 15, y & 15, pos.getZ() & 15);
    }

    public FluidState getFluidState(int x, int y, int z) { return getBlockState(new BlockPos(x, y, z)).getFluidState(); }

    /**
     * 바닐라 LevelChunk.setBlockState 그대로(서버의 바닐라 포팅): 그 칸만 바꾸고 옛 상태를 돌려준다. 이웃·모양 갱신, 클라이언트 알림, POI 갱신은 없다.
     * 같은 상태·공기뿐인 섹션에 공기류·치우기 반응이 칸을 갈아 치움이면 null. 높이 밖이면 바닐라는 섹션 배열 밖 예외지만 여기서는 null.
     */
    public BlockState setBlockState(BlockPos pos, BlockState state, boolean moved) {
        int y = pos.getY();
        if (isOutsideBuildHeight(y)) return null;
        int slot = getSectionIndex(y);
        if (!furnaceMirror.dead() && furnaceOwner()) {
            furnaceMirror.ownerSync(slot);
            int old = NmsChunks.bridge().setBlockStateNow(furnaceWorld, pos.getX(), y, pos.getZ(), state.furnaceStateId(), moved);
            return old < 0 ? null : NmsChunks.state(old);
        }
        // 다른 스레드(N2b-4, 결정 D1): 바닐라처럼 그 자리 섹션을 본다. 서버 섹션의 보기면 일지에 쌓아 주인이 바닐라 LevelChunk.setBlockState 를 그대로 재생한다
        // (블록 엔티티·빛·하이트맵·저장 표시까지). 플러그인이 넣은 떨어진 섹션이면 그 섹션에 쓴다(들여오기가 가져간다 — 그 밖 부수 효과는 없다).
        LevelChunkSection s = sections[slot];
        if (s == null) return null;
        if (s.hasOnlyAir() && state.isAir()) return null;
        int x = pos.getX(), z = pos.getZ();
        if (!furnaceMirror.dead() && s.furnaceChunk() == this && s.furnaceIndex() == slot && s.getStates().furnaceLiveNow()) {
            BlockState old = s.getBlockState(x & 15, y & 15, z & 15);
            if (old == state) return null;
            if (furnaceMirror.chunkSet(s, slot, x, y, z, state.furnaceStateId(), old.furnaceStateId(), moved)) return old;
            // 그 사이 그 섹션이 배열에서 빠져 떼어 내졌다 — 아래 바닐라 길로 그 섹션에(PR #396 검토 R2)
        }
        BlockState old = s.setBlockState(x & 15, y & 15, z & 15, state);
        return old == state ? null : old;
    }

    // ------------------------------------------------------------------ 블록 엔티티(N2b-6 — 읽기 보기만)

    /**
     * Paper: 청크의 블록 엔티티 표(공개 필드). Furnace: 부를 때마다 서버의 블록 엔티티 칸으로 만드는 읽기 보기({@link #getBlockEntities()} 와 같다).
     * 지우기·넣기는 아직 없다(UnsupportedNmsCall — 블록 엔티티 일지는 TASK).
     */
    public final java.util.Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.entity.BlockEntity> blockEntities = new java.util.AbstractMap<>() {
        @Override
        public java.util.Set<Entry<net.minecraft.core.BlockPos, net.minecraft.world.level.block.entity.BlockEntity>> entrySet() {
            return java.util.Collections.unmodifiableMap(getBlockEntities()).entrySet();
        }

        @Override
        public net.minecraft.world.level.block.entity.BlockEntity remove(Object key) {
            throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("LevelChunk#blockEntities.remove", "editing the chunk block entity table directly does not exist yet");
        }
    };

    /**
     * 바닐라 getBlockEntities: 이 청크의 블록 엔티티(칸 → 블록 엔티티). Furnace: 서버의 블록 엔티티 칸마다 살아 있는 블록 엔티티 보기({@code BlockEntity#furnaceInWorld})
     * 로 만든 새 표 — 고쳐도 청크에 닿지 않는다. 아무 스레드(다른 스레드는 공개 스냅샷의 칸 목록과 그 칸의 블록).
     */
    public java.util.Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.entity.BlockEntity> getBlockEntities() {
        java.util.Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.entity.BlockEntity> out = new java.util.HashMap<>();
        for (net.minecraft.core.BlockPos p : getBlockEntitiesPos()) {
            var be = net.minecraft.world.level.block.entity.BlockEntity.furnaceInWorld(level, p, getBlockState(p).furnaceStateId());
            if (be != null) out.put(p, be);
        }
        return out;
    }

    /** 바닐라 getBlockEntity(pos): 그 칸의 블록 엔티티 보기, 없으면 null(만들지 않는다 — 바닐라 CHECK 와 같다). */
    public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(net.minecraft.core.BlockPos pos) {
        if (!getBlockEntitiesPos().contains(pos)) return null;
        return net.minecraft.world.level.block.entity.BlockEntity.furnaceInWorld(level, pos, getBlockState(pos).furnaceStateId());
    }

    /** 바닐라 removeBlockEntity: 아직 없다 — 다른 스레드의 블록 엔티티 지우기는 거울 일지에 블록 엔티티가 들어가야 한다(N2b-4 4장 2, TASK). */
    public void removeBlockEntity(net.minecraft.core.BlockPos pos) {
        throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("LevelChunk#removeBlockEntity", "removing block entities through NMS does not exist yet (block entity journal)");
    }

    /**
     * 바닐라 private 도우미(Paper 와 같은 이름·서명 — 어댑터가 리플렉션으로 찾아 부른다): 블록 엔티티의 게임 이벤트 청취를 청크에서 뗀다.
     * 블록 엔티티를 이 청크에서 떼는 길 자체가 아직 없다({@link #removeBlockEntity} 와 같은 블록 엔티티 일지 — TASK)라 {@code removeBlockEntity} 처럼 던진다.
     */
    private <T extends net.minecraft.world.level.block.entity.BlockEntity> void removeGameEventListener(T blockEntity, net.minecraft.server.level.ServerLevel level) {
        throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("LevelChunk#removeGameEventListener", "detaching a block entity's game event listener needs the block entity journal, which does not exist yet");
    }

    /** 바닐라 private 도우미(위와 같은 규칙): 그 칸의 블록 엔티티 틱터를 청크에서 뗀다. 블록 엔티티 일지가 생길 때까지 던진다. */
    private void removeBlockEntityTicker(net.minecraft.core.BlockPos pos) {
        throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("LevelChunk#removeBlockEntityTicker", "detaching a block entity ticker needs the block entity journal, which does not exist yet");
    }
}
