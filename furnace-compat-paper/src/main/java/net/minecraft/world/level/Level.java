package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.bukkit.craftbukkit.CraftWorld;
import org.domi.furnace.compat.nms.NmsChunks;
import org.domi.furnace.compat.nms.UnsupportedNmsCall;

/**
 * 월드 shim(Mojang 이름). 실제 객체는 늘 {@link net.minecraft.server.level.ServerLevel} 이다.
 * <p>N1/N2a: 블록 읽기·쓰기와 청크 얻기. 읽기·쓰기는 그 칸의 청크를 가진 region 스레드(이벤트 처리기·entityScheduler·전역 빌림 구간)에서만 되고
 * 아니면 ThreadOwnershipException 이다(Paper 는 주 스레드 하나). Paper 와 다른 점: 로드돼 있지 않은 청크를 동기로 불러오지 않는다 —
 * 그런 칸을 읽거나 쓰면 IllegalStateException(로드 여부는 {@link #hasChunk}·{@link #getBlockStateIfLoaded} 로 본다).
 */
public abstract class Level implements LevelAccessor {

    public static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("overworld"));
    public static final ResourceKey<Level> NETHER = ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("the_nether"));
    public static final ResourceKey<Level> END = ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("the_end"));

    private final CraftWorld world;

    protected Level(CraftWorld world) { this.world = java.util.Objects.requireNonNull(world, "world"); }

    /** CraftBukkit 이 더한 메서드: 이 월드의 Bukkit 객체. */
    public CraftWorld getWorld() { return world; }

    private org.domi.furnace.api.world.World furnace() { return world.furnaceHandle(); }

    /** 서버 레지스트리 묶음(H7b, {@link net.minecraft.server.MinecraftServer#registryAccess()} 와 같다). */
    public net.minecraft.core.RegistryAccess registryAccess() { return net.minecraft.core.RegistryAccess.SERVER; }

    public boolean isClientSide() { return false; }

    /** 이 월드의 차원 키(minecraft:overworld 등). */
    public ResourceKey<Level> dimension() { return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(furnace().dimension())); }

    /**
     * 바닐라 Level#getEntity(int): 그 네트워크 id 의 이 월드 엔티티(플레이어 포함), 없으면 null — ProtocolLib 의 getEntityFromID 가 쓴다.
     * Paper·Folia 는 어느 스레드에서나 기다림 없이 돌려준다. Furnace 도 그 엔티티의 Bukkit 객체를 이미 알면(같은 UUID 캐시) 어느 스레드에서나
     * 곧바로 마지막 스냅샷의 shim 을 준다. 처음 보는 엔티티는 스냅샷을 그 엔티티를 가진 region 이 떠야 해서: 그 region 스레드(빌림 구간 포함)면 곧바로,
     * 틱 스레드가 아닌 스레드(네트워크·비동기)는 최대 1초 기다리고, 틱 스레드가 남의 region 의 처음 보는 엔티티를 찾으면 교착을 피하려고
     * ThreadOwnershipException 이다(Paper·Folia 와 다른 점).
     */
    public net.minecraft.world.entity.Entity getEntity(int id) { return null; }   // 서버 월드(ServerLevel)가 재정의한다

    // ------------------------------------------------------------------ 높이·청크(N2a)

    @Override
    public int getMinBuildHeight() { return NmsChunks.bridge().minY(furnace()); }

    @Override
    public int getHeight() { return NmsChunks.bridge().height(furnace()); }

    @Override
    public abstract ChunkSource getChunkSource();

    /** 로드된 청크의 보기. 로드돼 있지 않으면 IllegalStateException(Paper 는 동기로 불러온다). */
    public LevelChunk getChunk(int x, int z) { return (LevelChunk) getChunk(x, z, ChunkStatus.FULL, true); }

    public LevelChunk getChunkAt(BlockPos pos) { return getChunk(pos.getX() >> 4, pos.getZ() >> 4); }

    @Override
    public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean create) { return getChunkSource().getChunk(x, z, status, create); }

    /** 지금 로드돼 있는가(아무 스레드). */
    @Override
    public boolean hasChunk(int x, int z) { return NmsChunks.bridge().isLoaded(furnace(), x, z); }

    public boolean isLoaded(BlockPos pos) { return !isOutsideBuildHeight(pos) && hasChunkAt(pos); }

    /** Paper: 로드된 청크면 그 보기, 아니면 null(불러오지 않는다). 아무 스레드. */
    public LevelChunk getChunkIfLoaded(int x, int z) { return (LevelChunk) getChunk(x, z, ChunkStatus.FULL, false); }

    // ------------------------------------------------------------------ 블록 읽기(N1)

    /** 높이 밖은 void_air(바닐라). */
    @Override
    public BlockState getBlockState(BlockPos pos) {
        if (isOutsideBuildHeight(pos)) return Blocks.VOID_AIR.defaultBlockState();
        return getChunk(pos.getX() >> 4, pos.getZ() >> 4).getBlockState(pos);
    }

    /** Paper: 높이 밖은 void_air, 청크가 로드돼 있지 않으면 null. */
    @Override
    public BlockState getBlockStateIfLoaded(BlockPos pos) {
        if (isOutsideBuildHeight(pos)) return Blocks.VOID_AIR.defaultBlockState();
        LevelChunk c = getChunkIfLoaded(pos.getX() >> 4, pos.getZ() >> 4);
        return c == null ? null : c.getBlockState(pos);
    }

    /** 높이 밖은 빈 유체(바닐라). */
    @Override
    public FluidState getFluidState(BlockPos pos) {
        if (isOutsideBuildHeight(pos)) return Fluids.EMPTY.defaultFluidState();
        return getChunkAt(pos).getFluidState(pos);
    }

    /**
     * 바닐라 Level#getHeight(종류, x, z): 그 칸 위로 처음 비는 y. 청크가 로드돼 있지 않으면 최저 높이(바닐라와 같다 — 불러오지 않는다).
     * 월드 가로 경계(±30,000,000) 밖의 해수면 + 1 은 아직 없다.
     */
    @Override
    public int getHeight(Heightmap.Types type, int x, int z) {
        if (x < -30_000_000 || z < -30_000_000 || x >= 30_000_000 || z >= 30_000_000) {
            throw new UnsupportedNmsCall("Level#getHeight(월드 경계 밖)", "경계 밖의 답(해수면 + 1)은 차원의 해수면이 필요해 아직 없다");
        }
        if (!hasChunk(x >> 4, z >> 4)) return getMinBuildHeight();
        return getChunk(x >> 4, z >> 4).getHeight(type, x & 15, z & 15) + 1;
    }

    // ------------------------------------------------------------------ 블록 쓰기(N1)

    /** 바닐라 Block.UPDATE_MOVE_BY_PISTON. */
    private static final int UPDATE_MOVE_BY_PISTON = 64;

    /**
     * 바닐라 Level#setBlock 그대로(서버의 바닐라 포팅 ChunkLevel.setBlock): 플래그 1(이웃)·2(클라이언트 — 틱 끝에 섹션별로 모아 보낸다)·
     * 16(모양 갱신 안 함)·32(모양 갱신으로 부서진 이웃의 아이템 안 떨굼)·64(피스톤이 옮김)와 남은 깊이(maxUpdateDepth)를 바닐라 뜻대로 쓴다.
     * 4·8 은 클라이언트 쪽 다시 그리기라 서버에서 뜻이 없다. 바뀌었으면 true, 같은 상태거나 높이 밖이면 false.
     */
    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int maxUpdateDepth) {
        if (isOutsideBuildHeight(pos)) return false;
        if (!hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) throw NmsChunks.notLoaded(furnace(), pos.getX() >> 4, pos.getZ() >> 4, "Level#setBlock");
        return NmsChunks.bridge().setBlockNow(furnace(), pos.getX(), pos.getY(), pos.getZ(), state.furnaceStateId(), flags, maxUpdateDepth);
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags) { return setBlock(pos, state, flags, 512); }

    public boolean setBlockAndUpdate(BlockPos pos, BlockState state) { return setBlock(pos, state, 3); }

    /** 바닐라: 그 칸의 유체만 남긴다(물에 잠긴 블록이면 물). */
    @Override
    public boolean removeBlock(BlockPos pos, boolean moved) {
        FluidState fluid = getFluidState(pos);
        return setBlock(pos, fluid.createLegacyBlock(), 3 | (moved ? UPDATE_MOVE_BY_PISTON : 0));
    }

    @Override
    public String toString() { return getClass().getSimpleName() + "[" + world.getName() + "]"; }
}
