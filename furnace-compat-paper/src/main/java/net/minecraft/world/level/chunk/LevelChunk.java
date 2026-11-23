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
 * 빛, 하이트맵, 저장 표시는 바닐라대로). 블록 엔티티는 읽기·만들기(IMMEDIATE)·지우기가 있고(T-198 — 보기는 서버 블록 엔티티에 묶인다), 틱 상태
 * (getFullStatus)는 바닐라 티켓 값(T-204), 엔티티는 아직 없다. 다른 스레드에서 읽으면 공개 스냅샷과 자기 쓰기(N2b-3·4), 다른 스레드의 쓰기는 거울 일지로 주인이 재생한다(N2b-4,
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

    /**
     * Paper {@code LevelChunk#setBiome}: 바이옴 칸 좌표(블록 좌표 &gt;&gt; 2)의 바이옴을 바꾼다 — 높이는 끝 칸으로 죈다({@link #getNoiseBiome} 와 같다).
     * 클라이언트에 다시 보내지도, 저장 표시를 하지도 않는다(부르는 쪽이 {@code setUnsaved}). 섹션의 바이옴 컨테이너 쓰기 길을 그대로 쓰므로 주인 스레드는
     * 바로, 다른 스레드는 거울 칸 일지로 서버에 간다.
     */
    @SuppressWarnings("unchecked")
    public void setBiome(int biomeX, int biomeY, int biomeZ, net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome) {
        int minQ = getMinBuildHeight() >> 2, maxQ = minQ + (getHeight() >> 2) - 1;
        int y = Math.max(minQ, Math.min(maxQ, biomeY));
        var container = (PalettedContainer<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>>) getSection(getSectionIndex(y << 2)).getBiomes();
        container.set(biomeX & 3, y & 3, biomeZ & 3, biome);
    }

    public FluidState getFluidState(int x, int y, int z) { return getBlockState(new BlockPos(x, y, z)).getFluidState(); }

    /**
     * 바닐라 LevelChunk.setBlockState 그대로(서버의 바닐라 포팅): 그 칸만 바꾸고 옛 상태를 돌려준다. 이웃·모양 갱신, 클라이언트 알림, POI 갱신은 없다.
     * 같은 상태·공기뿐인 섹션에 공기류·치우기 반응이 칸을 갈아 치움이면 null. 높이 밖이면 바닐라는 섹션 배열 밖 예외지만 여기서는 null.
     */
    public BlockState setBlockState(BlockPos pos, BlockState state, boolean moved) { return setBlockState(pos, state, moved, true); }

    /**
     * Paper LevelChunk.setBlockState(pos, state, moved, doPlace): doPlace 가 거짓이면 새 블록의 놓기 반응(onPlace)을 건너뛴다(블록 엔티티는 그대로 만든다 —
     * Paper 와 같다). 그 밖은 3인자 판과 같다(다른 스레드는 거울 일지 — 재생도 놓기 반응을 건너뛴다). 플러그인이 넣은 떨어진 섹션에는 반응이 없다.
     */
    public BlockState setBlockState(BlockPos pos, BlockState state, boolean moved, boolean doPlace) {
        int y = pos.getY();
        if (isOutsideBuildHeight(y)) return null;
        int slot = getSectionIndex(y);
        if (!furnaceMirror.dead() && furnaceOwner()) {
            furnaceMirror.ownerSync(slot);
            int old = NmsChunks.bridge().setBlockStateNow(furnaceWorld, pos.getX(), y, pos.getZ(), state.furnaceStateId(), moved, doPlace);
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
            if (furnaceMirror.chunkSet(s, slot, x, y, z, state.furnaceStateId(), old.furnaceStateId(), moved, doPlace)) return old;
            // 그 사이 그 섹션이 배열에서 빠져 떼어 내졌다 — 아래 바닐라 길로 그 섹션에(PR #396 검토 R2)
        }
        BlockState old = s.setBlockState(x & 15, y & 15, z & 15, state);
        return old == state ? null : old;
    }

    /**
     * 바닐라 LevelChunk.getFullStatus: 청크 홀더의 단계(바닐라 티켓 레벨 — Paper 는 플레이어 티켓을 시뮬레이션 거리로 걸어 다를 수 있다, T-204 설계 4장).
     * 내린 청크(이 로드가 끝난 보기)는 INACCESSIBLE. 주인 스레드면 바로, 틱 스레드가 아닌 스레드는 주인을 기다리고(최대 10초), 다른 region·전역 스레드는
     * ThreadOwnershipException({@link org.domi.furnace.compat.nms.NmsOwner}).
     */
    public net.minecraft.server.level.FullChunkStatus getFullStatus() {
        if (furnaceMirror.dead()) return net.minecraft.server.level.FullChunkStatus.INACCESSIBLE;
        var p = getPos();
        int s = org.domi.furnace.compat.nms.NmsOwner.call(furnaceWorld, p.getMinBlockX(), org.domi.furnace.compat.nms.NmsOwner.CHUNK_LEVEL, p.getMinBlockZ(), "LevelChunk#getFullStatus",
                () -> NmsChunks.bridge().fullChunkStatusNow(furnaceWorld, p.x, p.z));
        return net.minecraft.server.level.FullChunkStatus.furnaceOf(s);
    }

    // ------------------------------------------------------------------ 블록 엔티티(N2b-6 읽기, T-198 쓰기)

    /** 바닐라 LevelChunk.EntityCreationType: IMMEDIATE 는 없으면 만들고, QUEUED·CHECK 는 있는 것만(1.21.1 서버에서 둘은 같다). */
    public enum EntityCreationType {
        IMMEDIATE, QUEUED, CHECK
    }

    /**
     * Paper: 청크의 블록 엔티티 표(공개 필드). Furnace: 서버 블록 엔티티로 만드는 보기 — 읽기(get·containsKey·entrySet 등)는 {@link #getBlockEntities()} 와 같고,
     * {@code remove(pos)} 는 {@link #removeBlockEntity} 처럼 그 칸의 블록 엔티티를 청크에서 떼고(지움 표시·틱·게임 이벤트까지 — FAWE 의 신호기 지우기가 뒤이어
     * 리스너·틱을 떼고 지움 필드를 세우는 것과 끝 상태가 같다) 뗀 것을 돌려준다. {@code put} 은 없다(UnsupportedNmsCall — 표에 바로 넣으면 setBlockEntity 의 판정·옛 것 지우기를 건너뛴다. 넣기는 {@link #addAndRegisterBlockEntity}).
     */
    public final java.util.Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.entity.BlockEntity> blockEntities = new java.util.AbstractMap<>() {
        @Override
        public java.util.Set<Entry<net.minecraft.core.BlockPos, net.minecraft.world.level.block.entity.BlockEntity>> entrySet() {
            return java.util.Collections.unmodifiableMap(getBlockEntities()).entrySet();
        }

        @Override
        public net.minecraft.world.level.block.entity.BlockEntity get(Object key) {
            return key instanceof net.minecraft.core.BlockPos p ? getBlockEntity(p) : null;
        }

        @Override
        public boolean containsKey(Object key) { return get(key) != null; }

        @Override
        public net.minecraft.world.level.block.entity.BlockEntity remove(Object key) {
            return key instanceof net.minecraft.core.BlockPos p ? furnaceRemoveBlockEntity(p) : null;
        }

        @Override
        public net.minecraft.world.level.block.entity.BlockEntity put(net.minecraft.core.BlockPos key, net.minecraft.world.level.block.entity.BlockEntity value) {
            throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("LevelChunk#blockEntities.put", "putting into the chunk table directly skips the setBlockEntity checks; use addAndRegisterBlockEntity");
        }
    };

    /**
     * 바닐라 getBlockEntities: 이 청크의 블록 엔티티(칸 → 블록 엔티티 보기, 새 표 — 고쳐도 청크에 닿지 않는다). 아무 스레드(다른 스레드는 공개 판 + 자기 쓰기).
     * 보기는 칸·서버 블록 엔티티마다 같은 객체다.
     */
    public java.util.Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.entity.BlockEntity> getBlockEntities() { return furnaceBlockEntities(); }

    /** 바닐라 getBlockEntity(pos, 종류): IMMEDIATE 는 없고 블록이 블록 엔티티를 가지는 종류면 만든다(바닐라 Level#getBlockEntity 길). */
    public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(net.minecraft.core.BlockPos pos, EntityCreationType type) {
        return furnaceBlockEntity(pos, type == EntityCreationType.IMMEDIATE);
    }

    /**
     * 바닐라 removeBlockEntity: 그 칸의 블록 엔티티를 청크에서 떼고 지움 표시, 틱·게임 이벤트에서 뺀다(블록은 그대로, 저장 표시 없음). 주인이 아닌 스레드
     * (FAWE 일꾼)는 거울 일지 — 주인이 다음 맞춤 지점에 그 순서대로 지운다(같은 맞추기에서 들여오는 섹션보다 먼저, Paper 의 FAWE 순서와 같다).
     */
    public void removeBlockEntity(net.minecraft.core.BlockPos pos) { furnaceRemoveBlockEntity(pos); }

    /**
     * 바닐라 private 도우미(Paper 와 같은 이름·서명 — 어댑터가 리플렉션으로 찾아 부른다): 블록 엔티티의 게임 이벤트 청취를 청크에서 뗀다. Furnace 는 블록 엔티티를
     * 청크에서 뗄 때({@link #removeBlockEntity}·{@code blockEntities.remove}) 리스너도 함께 떼므로, 이미 떨어진 블록 엔티티면 할 일이 없다. 청크에 붙은 채
     * 리스너만 떼는 것은 없다(UnsupportedNmsCall).
     */
    private <T extends net.minecraft.world.level.block.entity.BlockEntity> void removeGameEventListener(T blockEntity, net.minecraft.server.level.ServerLevel level) {
        if (blockEntity.isRemoved() || getBlockEntity(blockEntity.getBlockPos()) != blockEntity) return;
        throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("LevelChunk#removeGameEventListener", "detaching only the game event listener of a block entity that stays in the chunk does not exist");
    }

    /**
     * 바닐라 addAndRegisterBlockEntity(NMS shim 5d — Axiom 블록 버퍼): 블록 엔티티를 그 칸의 것으로 넣고(setBlockEntity) 틱·게임 이벤트에 등록한다.
     * 바닐라 setBlockEntity 그대로: 그 칸 블록(이 스레드가 보는 것)이 블록 엔티티를 갖지 않으면 Paper 처럼 내부 오류를 남기고 넣지 않는다. 블록 엔티티가 든 상태가
     * 칸과 다르면 경고(종류가 못 받는 상태면 그 뒤 setBlockState 가 IllegalStateException — 바닐라 그대로)하고 칸 상태로 맞춘다. 그 칸에 있던 것은 지움 표시.
     * Furnace: 넣은 객체는 그 칸 서버 블록 엔티티의 보기가 된다(든 내용을 싣는다). 다른 스레드는 거울 일지로 주인이 넣는다. 이미 월드의 보기인 객체는 같은 칸에
     * 다시 넣는 것(바닐라: 같은 객체라 바뀌지 않는다)만 된다.
     */
    public void addAndRegisterBlockEntity(net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
        net.minecraft.core.BlockPos at = blockEntity.getBlockPos();
        if (blockEntity.furnaceIsView()) {
            if (getBlockEntity(at) == blockEntity) return;
            throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("LevelChunk#addAndRegisterBlockEntity", "putting a block entity that already belongs to a world"
                    + " into another position or chunk (" + blockEntity + ") is not supported");
        }
        BlockState here = getBlockState(at);
        if (!here.hasBlockEntity()) {
            String msg = "Trying to set block entity %s at position %s, but state %s does not allow it".formatted(blockEntity, at, here);
            LOG.error(msg, new IllegalStateException(msg));   // Paper: ServerInternalException 를 찍고 넣지 않는다
            return;
        }
        BlockState held = blockEntity.getBlockState();
        if (held != here) {
            if (!blockEntity.getType().isValid(here)) LOG.warn("Trying to set block entity {} at position {}, but state {} does not allow it", blockEntity, at, here);
            else if (here.getBlock() != held.getBlock()) LOG.warn("Block state mismatch on block entity {} in position {}, {} != {}, updating", blockEntity, at, here, held);
            blockEntity.setBlockState(here);
        }
        blockEntity.setLevel(level);
        blockEntity.clearRemoved();
        furnaceAddBlockEntity(blockEntity);
    }

    /**
     * 바닐라 private 도우미(Paper 와 같은 이름·서명 — Axiom 이 리플렉션으로 부른다): 블록 엔티티가 든 상태로 틱터를 다시 고른다. Furnace 서버는 틱마다 칸의 지금
     * 상태로 할 일을 고르고(모닥불은 켜졌는가로 굽기·식히기), 든 상태는 {@code setBlockState} 가 이미 서버에 넣었으므로 더 할 일이 없다.
     */
    private <T extends net.minecraft.world.level.block.entity.BlockEntity> void updateBlockEntityTicker(T blockEntity) {}

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(LevelChunk.class);

    /** 바닐라 private 도우미(위와 같은 규칙): 그 칸의 블록 엔티티 틱터를 뗀다. 그 칸에 블록 엔티티가 없으면(이미 뗐다) 할 일이 없다. 붙은 채 틱만 끄는 것은 없다. */
    private void removeBlockEntityTicker(net.minecraft.core.BlockPos pos) {
        if (getBlockEntity(pos) == null) return;
        throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("LevelChunk#removeBlockEntityTicker", "stopping the ticker of a block entity that stays in the chunk does not exist");
    }
}
