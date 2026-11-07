// 차원 하나의 월드. 블록 접근은 소유 region 스레드로 넘어가므로 비동기이며, 동기 접근은 소유 스레드에서만 허용한다(위반 시 예외).
package org.domi.furnace.api.world;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.inventory.Inventory;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.sched.TaskScheduler;

public interface World {

    /** 레벨 이름(Paper 와 같은 규칙: 오버월드 world, 네더 world_nether, 엔드 world_the_end). 불변. 아무 스레드. */
    String name();

    /** 차원 키(minecraft:overworld). 불변. 아무 스레드. */
    String dimension();

    /** 이 월드의 생성 시드(차원별 시드를 정하지 않았으면 주 월드 시드 — 바닐라). */
    long seed();

    /** 차원 타입 키(minecraft:overworld, 플러그인 타입이면 myplugin:deep). 불변. */
    default String dimensionType() { return dimension(); }

    /** 저장 폴더 배치·기본 생성기를 정한 환경. 불변. */
    default WorldEnvironment environment() { return WorldEnvironment.NORMAL; }

    /** 아직 올라와 있는가(추가 월드를 내리면 false). 최근 값. */
    default boolean isLoaded() { return true; }

    /** 게임룰 값(문자열 true/false/숫자), 모르는 이름이면 null. 최근 값 — 아무 스레드. */
    String gameRule(String name);

    /** 지금 로드된 청크 수(근사, 진단용). */
    int loadedChunkCount();

    /**
     * 그 칸의 살아 있는 손잡이. 아무것도 읽지 않고 만들기도 싸다 — 읽기·쓰기는 {@link Block} 의 규칙(소유 스레드 동기, 아니면 Async)을 따른다.
     * 아무 스레드.
     */
    Block block(BlockPos pos);

    /**
     * 그 칸의 블록 복사본. 소유 region 스레드에서 읽는다. 청크가 로드돼 있지 않으면 empty(청크를 만들지 않는다).
     * 아무 스레드에서 부를 수 있다. 결과를 같은 region 스레드에서 {@code get()} 으로 기다리지 말 것.
     */
    CompletableFuture<Optional<BlockSnapshot>> blockAt(BlockPos pos);

    /**
     * 그 칸을 data 로 바꾼다(이웃 갱신 + 클라이언트 방송, 바닐라 setBlock 플래그 11). 소유 region 스레드에서 실행된다.
     * 청크가 로드돼 있지 않거나 높이 밖이면 false. 알 수 없는 블록·속성이면 IllegalArgumentException 으로 완료된다.
     * BlockPlace/BlockBreak 이벤트는 거치지 않는다.
     */
    CompletableFuture<Boolean> setBlock(BlockPos pos, BlockData data);

    /**
     * 동기 읽기 — 호출한 스레드가 그 칸을 가진 region 스레드일 때만 된다(예: 그 region 에서 발화한 이벤트 처리기 안).
     * 아니면 {@link org.domi.furnace.api.concurrent.ThreadOwnershipException}.
     */
    Optional<BlockSnapshot> blockNow(BlockPos pos);

    /** 그 칸을 가진 region 의 스케줄러. */
    TaskScheduler scheduler(BlockPos pos);

    // ------------------------------------------------------------------ 청크

    /** 청크 좌표 핸들(로드하지 않는다). 아무 스레드. */
    Chunk chunkAt(int chunkX, int chunkZ);

    default Chunk chunkAt(BlockPos pos) { return chunkAt(pos.chunkX(), pos.chunkZ()); }

    /** 지금 로드돼 있는가(최근 값). */
    boolean isChunkLoaded(int chunkX, int chunkZ);

    // ------------------------------------------------------------------ 엔티티

    /**
     * type(minecraft:zombie) 엔티티를 at 에 만들어 넣는다 — 그 칸을 가진 region 스레드에서, /summon 과 같은 경로
     * (몹은 finalizeSpawn 으로 장비·변종을 고른다, 스폰 이유 COMMAND). EntitySpawnEvent 를 거친다: 취소되면 null 로 완료.
     * 모르는 종류·플레이어·낚시찌는 IllegalArgumentException, 청크가 로드돼 있지 않으면 IllegalStateException 으로 완료(청크를 만들지 않는다).
     */
    CompletableFuture<Entity> spawnEntity(String type, Location at);

    /**
     * center 에서 radius 안(축 정렬 상자 안, 거리 제곱 비교)의 엔티티와 플레이어. center 칸을 가진 region 에서 읽는다 —
     * 그 region 에 없는(다른 region 이 가진 청크에 있는) 엔티티는 빠진다. radius 는 0..128.
     */
    CompletableFuture<List<Entity>> nearbyEntities(Location center, double radius);

    // ------------------------------------------------------------------ 블록 칸

    /**
     * 그 칸 블록 엔티티의 칸(상자·통·셜커 상자·깔때기·발사기·공급기·화로류·양조기·제작기). 칸이 없는 블록이거나 로드되지 않았으면 empty.
     * 큰 상자는 그 반쪽(27칸)만 — 두 반쪽을 합친 보기는 다음 단계. 읽기·쓰기는 그 칸을 가진 region 스레드에서 실행되고,
     * 그 사이 블록이 부서졌으면 IllegalStateException 으로 완료된다.
     */
    CompletableFuture<Optional<Inventory>> inventoryAt(BlockPos pos);

    // ------------------------------------------------------------------ 바이옴

    /**
     * 그 칸의 바이옴 키(minecraft:plains, 플러그인 바이옴은 myplugin:glow). 바이옴은 4칸 단위 칸(quart)이라 같은 칸의 블록은 같은 값이다.
     * 소유 region 스레드에서 읽는다. 청크가 로드돼 있지 않으면 empty(청크를 만들지 않는다). 아무 스레드.
     */
    default CompletableFuture<Optional<String>> biomeAt(BlockPos pos) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("biomeAt"));
    }

    /**
     * 그 칸이 든 바이옴 칸(4x4x4)을 biome 으로 바꾸고, 그 청크를 보는 플레이어에게 바이옴을 다시 보낸다(/fillbiome 과 같은 경로).
     * 청크가 로드돼 있지 않으면 false. 모르는 바이옴 키면 IllegalArgumentException 으로 완료된다.
     */
    default CompletableFuture<Boolean> setBiome(BlockPos pos, String biome) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("setBiome"));
    }

    // ------------------------------------------------------------------ region 정보

    /**
     * 그 청크를 가진 region 의 관측 사본. 그 청크의 구역이 어느 region 에도 속하지 않으면 empty. 아무 스레드,
     * 값은 잰 순간의 것이다(region 은 합쳐지거나 나뉜다).
     */
    default java.util.Optional<RegionInfo> regionAt(int chunkX, int chunkZ) { return java.util.Optional.empty(); }

    default java.util.Optional<RegionInfo> regionAt(BlockPos pos) { return regionAt(pos.chunkX(), pos.chunkZ()); }

    /** 이 차원의 살아 있는 region 모두(id 순). */
    default List<RegionInfo> regions() { return List.of(); }

    // ------------------------------------------------------------------ 스폰 정책·엔티티 수

    /** 이 월드의 자연 스폰 정책(기본은 바닐라 상수 — 바꾸지 않으면 스폰 루프는 바닐라 그대로). 아무 스레드. */
    default SpawnPolicy spawnPolicy() { throw new UnsupportedOperationException("spawnPolicy"); }

    /**
     * 지금 로드된 청크 전체의 엔티티 수(플레이어 포함). region 마다 자기 청크를 세어 합친다 — 한 region 의 결과가 아니라 서로 다른
     * 틱 순간의 값이 섞일 수 있다. 로드되지 않은 청크의 엔티티는 세지 않는다.
     */
    default CompletableFuture<EntityCounts> entityCounts() {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("entityCounts"));
    }

    // ------------------------------------------------------------------ 마을·POI·습격

    /**
     * 그 칸이 마을 안인가(바닐라 isVillage — 점유된 마을 POI 가 든 구역에서 구역 1칸 이내). 그 칸을 가진 region 이 올려 둔 청크만 본다.
     * 청크가 로드돼 있지 않으면 false.
     */
    default CompletableFuture<Boolean> isVillage(BlockPos pos) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("isVillage"));
    }

    /**
     * 그 칸이 든 구역에서 마을 중심 구역까지의 구역(16칸) 거리(0 = 마을 중심 구역, 최대 6 이고 더 멀면 7). 청크가 로드돼 있지 않으면 7.
     */
    default CompletableFuture<Integer> sectionsToVillage(BlockPos pos) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("sectionsToVillage"));
    }

    /**
     * center 에서 radius(칸, 구 모양 거리 안) 안의 POI. types 가 비어 있으면 모든 종류(키: minecraft:home …). 가까운 순.
     * 그 칸을 가진 region 이 올려 둔 청크만 본다(다른 region 청크의 POI 는 빠진다). radius 는 0..128.
     */
    default CompletableFuture<java.util.List<PointOfInterest>> pointsOfInterest(BlockPos center, int radius, java.util.Set<String> types, PoiOccupancy occupancy) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("pointsOfInterest"));
    }

    default CompletableFuture<java.util.List<PointOfInterest>> pointsOfInterest(BlockPos center, int radius) {
        return pointsOfInterest(center, radius, java.util.Set.of(), PoiOccupancy.ANY);
    }

    /** 지금 올라와 있는 활성 습격 전부(번호 순). 중심 청크가 내려가 쉬는 습격은 빠진다. */
    default CompletableFuture<java.util.List<RaidInfo>> raids() {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("raids"));
    }

    /** 그 칸 가까이(바닐라 96칸 안)의 활성 습격 중 중심이 가장 가까운 것. 없으면 empty. 그 칸을 가진 region 의 습격만 본다. */
    default CompletableFuture<Optional<RaidInfo>> raidAt(BlockPos pos) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("raidAt"));
    }

    /** 그 번호의 습격을 멈춘다(바닐라 Raid.stop — 보스바가 사라지고 습격자는 남는다). 멈췄으면 true, 모르는 번호면 false. */
    default CompletableFuture<Boolean> stopRaid(int id) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("stopRaid"));
    }

    // ------------------------------------------------------------------ 블록 엔티티 NBT (/data block 과 같은 의미)

    /**
     * 그 칸 블록 엔티티(상자·표지판·생성기·신호기 …)의 NBT 사본(/data get block 과 같은 내용 — x·y·z·id·components 포함).
     * 그 칸을 가진 region 스레드에서 읽는다. 로드되지 않았거나 블록 엔티티가 없으면 empty(청크를 만들지 않는다).
     */
    CompletableFuture<Optional<NbtCompound>> blockEntityNbt(BlockPos pos);

    /**
     * 그 칸을 가진 region 스레드에서 블록 엔티티 NBT 를 읽어 edit 로 고치고, 바뀌었으면 다시 싣는다(/data modify·merge block 과 같은 경로:
     * 바닐라 loadWithComponents → setChanged → 클라이언트 갱신). 바뀌었으면 true, 그대로거나 블록 엔티티가 없거나 로드되지 않았으면 false.
     * 위치 키(x·y·z)를 바꿔도 블록 엔티티는 옮겨지지 않는다(바닐라와 같다).
     */
    CompletableFuture<Boolean> editBlockEntityNbt(BlockPos pos, java.util.function.Consumer<NbtCompound> edit);

    /** patch 를 합친다(/data merge block). 바뀌었으면 true. */
    default CompletableFuture<Boolean> mergeBlockEntityNbt(BlockPos pos, NbtCompound patch) {
        NbtCompound p = patch.copy();
        return editBlockEntityNbt(pos, n -> n.merge(p));
    }
}
