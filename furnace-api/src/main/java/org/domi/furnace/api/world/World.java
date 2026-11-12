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

    /**
     * 그 청크를 이 월드의 생성기(시드·월드젠 훅 포함)로 처음부터 다시 만들어 바꾼다(불만 표 41번). 결과 블록은 같은 시드의 새 월드에서
     * 그 청크가 처음 만들어질 때와 같다 — 이웃 청크에서 넘어오는 나무·구조물 조각도 포함하고, 이 청크에서 이웃으로 넘치는 장식은
     * 이웃을 바꾸지 않는다. 블록·바이옴·블록 엔티티·예약 틱·구조물 참조·POI 를 바꾸고, 빛을 다시 계산하고, 그 청크를 보는
     * 플레이어에게 다시 보낸다. 그 청크를 가진 region 스레드에서만 바꾸므로 다른 region 은 건드리지 않는다.
     * 로드돼 있지 않으면 먼저 불러온다. 아무 스레드. 내린 월드면 false.
     */
    default CompletableFuture<Boolean> regenerateChunk(int chunkX, int chunkZ) {
        return regenerateChunk(chunkX, chunkZ, ChunkRegeneration.DEFAULT);
    }

    /** {@link #regenerateChunk(int, int)} 에 엔티티 처리를 고른 판. */
    default CompletableFuture<Boolean> regenerateChunk(int chunkX, int chunkZ, ChunkRegeneration options) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("regenerateChunk"));
    }

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

    /**
     * spawnEntity 의 동기판 — center 칸을 가진 region 스레드에서만(아니면 {@link org.domi.furnace.api.concurrent.ThreadOwnershipException}),
     * 곧바로 만들고 엔티티를 돌려준다. 스폰 이벤트가 취소되면 null. 청크가 로드돼 있지 않으면 IllegalStateException, 종류·위치가 잘못이면 IllegalArgumentException.
     */
    default Entity spawnEntityNow(String type, Location at) { throw new UnsupportedOperationException("spawnEntityNow"); }

    /** nearbyEntities 의 동기판 — center 칸을 가진 region 스레드에서만(아니면 ThreadOwnershipException), 곧바로 목록을 돌려준다. 청크가 로드돼 있지 않으면 빈 목록. */
    default List<Entity> nearbyEntitiesNow(Location center, double radius) { throw new UnsupportedOperationException("nearbyEntitiesNow"); }

    /**
     * box 와 충돌 상자가 겹치는(경계만 닿는 것은 제외) 엔티티와 플레이어 — 바닐라 Level.getEntities(null, box). region 스레드에서는 지금 스레드가
     * 돌리는 region 의 엔티티만 본다(상자 가운데가 어디든, 상자가 아무리 커도 예외 없이 — 남의 region 엔티티는 빠진다). 전역 스레드의 빌림 구간
     * ({@code openRegionAccess})에서는 상자와 겹치는 로드된 region 을 모두 빌려 합친다. 그 밖의 스레드는
     * {@link org.domi.furnace.api.concurrent.ThreadOwnershipException}. 사라진 엔티티는 빠진다.
     */
    default List<Entity> entitiesInBoxNow(Aabb box) { throw new UnsupportedOperationException("entitiesInBoxNow"); }

    /**
     * 블록 광선 판정(바닐라 Level.clip) — 선분 (fx,fy,fz)→(tx,ty,tz) 가 처음 맞는 블록. 시작점이 든 칸의 region 스레드에서만(아니면
     * ThreadOwnershipException). 안 맞으면 empty. ignorePassable 이면 충돌 모양(지나갈 수 있는 블록은 건너뛴다), 아니면 윤곽 모양.
     * canCollide 가 false 를 주는 칸은 블록도 유체도 건너뛴다(null 이면 모두 본다 — Paper 의 canCollide). canCollide 는 그 region 스레드에서 불린다.
     * 로드되지 않은(또는 그 region 이 갖지 않은) 청크에 닿으면 거기서 빗나간 것으로 끝낸다(청크를 만들지 않는다).
     */
    default Optional<BlockHit> rayTraceBlocksNow(double fx, double fy, double fz, double tx, double ty, double tz, FluidPick fluid, boolean ignorePassable,
            java.util.function.Predicate<BlockPos> canCollide) {
        throw new UnsupportedOperationException("rayTraceBlocksNow");
    }

    /**
     * 네트워크 엔티티 id({@link Entity#entityId()}, 패킷이 엔티티를 가리키는 번호)로 이 월드의 엔티티나 플레이어를 찾는다(Paper 의
     * ServerLevel#getEntity(int)). 그 엔티티를 가진 region 에서 읽은 새 핸들로 완료하고, 없거나 다른 월드에 있거나 사라졌으면 empty.
     * 엔티티가 region 을 옮겨 가는 도중이면 몇 번 다시 찾는다. 아무 스레드.
     */
    default CompletableFuture<Optional<Entity>> entityById(int entityId) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("entityById"));
    }

    /**
     * 그 id 의 엔티티·플레이어 UUID(없으면 empty). 엔티티 상태는 읽지 않고 id → UUID 만 보므로 어느 스레드에서나 기다림 없이 된다 —
     * 호환 계층이 이미 아는 Bukkit 객체를 소유 스레드가 아닌 곳에서도 곧바로 돌려줄 때 쓴다.
     */
    default Optional<java.util.UUID> entityUuidById(int entityId) { throw new UnsupportedOperationException("entityUuidById"); }

    /**
     * entityById 의 동기판 — 그 엔티티를 가진 region 스레드에서만(옛 동기 작업의 빌림 구간 포함, 아니면
     * {@link org.domi.furnace.api.concurrent.ThreadOwnershipException}) 곧바로 핸들을 돌려준다. 없으면 empty.
     * 플레이어는 어느 스레드에서나 된다.
     */
    default Optional<Entity> entityByIdNow(int entityId) { throw new UnsupportedOperationException("entityByIdNow"); }

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

    /** {@link #biomeAt} 의 동기 판 — 그 칸을 가진 region 스레드에서만 된다(아니면 ThreadOwnershipException). 높이 밖은 끝 칸으로 죈다. 청크가 없으면 empty. */
    default Optional<String> biomeNow(BlockPos pos) { throw new UnsupportedOperationException("biomeNow"); }

    /**
     * 그 칸이 든 4×4×4 바이옴 칸 하나를 바꾸는 동기 판 — 그 칸을 가진 region 스레드에서만 된다(아니면 ThreadOwnershipException). 높이 밖은 끝 칸으로 죈다.
     * 클라이언트에 다시 보내지 않는다(Paper CraftWorld.setBiome 과 같다). 모르는 키는 IllegalArgumentException, 청크가 없으면 false.
     */
    default boolean setBiomeNow(BlockPos pos, String biome) { throw new UnsupportedOperationException("setBiomeNow"); }

    // ------------------------------------------------------------------ 소리·입자

    /**
     * 그 지점에서 소리를 낸다(바닐라 Level.playSeededSound): 이 차원 접속자 중 크기에 따른 거리(크기 1 이하 16칸, 넘으면 16×크기) 안에 있는 사람에게.
     * seed 는 소리 변형을 고르는 클라이언트 난수 씨앗. 아무 스레드.
     */
    default void playSound(org.domi.furnace.api.audience.Sound sound, double x, double y, double z, long seed) {
        throw new UnsupportedOperationException("playSound");
    }

    /** 씨앗을 서버 난수로 정한다. */
    default void playSound(org.domi.furnace.api.audience.Sound sound, double x, double y, double z) {
        playSound(sound, x, y, z, java.util.concurrent.ThreadLocalRandom.current().nextLong());
    }

    /**
     * 엔티티에 붙어 따라 움직이는 소리(sound_entity). 그 엔티티를 화면에 보고 있는 사람과, 엔티티가 플레이어면 그 자신에게 보낸다
     * (Paper World#playSound(Entity, …) 와 같다). 엔티티가 이 월드에 없으면 아무 일도 없다. 아무 스레드.
     */
    default void playSound(org.domi.furnace.api.audience.Sound sound, org.domi.furnace.api.entity.Entity entity, long seed) {
        throw new UnsupportedOperationException("playSound");
    }

    /**
     * 입자를 낸다(바닐라 ServerLevel.sendParticles): receivers(null 이면 이 차원 접속자 모두) 중 칸 가운데가 그 지점에서 32칸(force 면 512칸)
     * 미만인 사람에게 한 패킷을 보낸다. 퍼짐·속도는 float 로 줄여 싣는다. 모르는 입자 종류면 IllegalArgumentException. 아무 스레드.
     */
    default void spawnParticle(ParticleEffect particle, java.util.Collection<? extends org.domi.furnace.api.entity.Player> receivers,
                               double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double speed, boolean force) {
        throw new UnsupportedOperationException("spawnParticle");
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

    /** region 번호로(같은 차원 안에서 유일). 없거나 그새 합쳐져 사라졌으면 empty. */
    default java.util.Optional<RegionInfo> region(int id) {
        for (RegionInfo r : regions()) if (r.id() == id) return java.util.Optional.of(r);
        return java.util.Optional.empty();
    }

    /**
     * 그 청크를 가진 region 의 최근 5초 TPS(초당 게임 틱 수). region 이 없거나 막 생겨 아직 잴 수 없으면 empty.
     * 값은 그 region 의 목표({@code /tick rate}, 기본 20)를 넘을 수 있다(/tick sprint 중). 아무 스레드.
     */
    default java.util.OptionalDouble tpsAt(int chunkX, int chunkZ) {
        return regionAt(chunkX, chunkZ).filter(r -> !Double.isNaN(r.tps())).map(r -> java.util.OptionalDouble.of(r.tps()))
                .orElse(java.util.OptionalDouble.empty());
    }

    default java.util.OptionalDouble tpsAt(BlockPos pos) { return tpsAt(pos.chunkX(), pos.chunkZ()); }

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
