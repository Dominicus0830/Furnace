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

    /** 레벨 이름. Paper 와 같은 규칙이며 오버월드는 world, 네더는 world_nether, 엔드는 world_the_end 이다. 불변. 아무 스레드. */
    String name();

    /** 차원 키(minecraft:overworld). 불변. 아무 스레드. */
    String dimension();

    /** 이 월드의 생성 시드(차원별 시드를 정하지 않았으면 주 월드 시드이며, 바닐라와 같다). */
    long seed();

    /** 차원 타입 키(minecraft:overworld, 플러그인 타입이면 myplugin:deep). 불변. */
    default String dimensionType() { return dimension(); }

    /** 저장 폴더 배치·기본 생성기를 정한 환경. 불변. */
    default WorldEnvironment environment() { return WorldEnvironment.NORMAL; }

    /** 아직 올라와 있는가(추가 월드를 내리면 false). 최근 값. */
    default boolean isLoaded() { return true; }

    /** 게임룰 값(문자열 true/false/숫자). 모르는 이름이면 null. 최근 값이며 아무 스레드. */
    String gameRule(String name);

    /** 지금 로드된 청크 수(근사, 진단용). */
    int loadedChunkCount();

    /**
     * 그 칸의 살아 있는 손잡이. 아무것도 읽지 않고 만들기도 싸다. 읽기·쓰기는 {@link Block} 의 규칙(소유 스레드 동기, 아니면 Async)을 따른다.
     * 아무 스레드.
     */
    Block block(BlockPos pos);

    /**
     * 그 칸의 블록 복사본. 소유 region 스레드에서 읽는다. 청크가 로드돼 있지 않으면 empty(청크를 만들지 않는다).
     * 아무 스레드에서 부를 수 있다. 결과를 같은 region 스레드에서 {@code get()} 으로 기다리지 말 것.
     */
    CompletableFuture<Optional<BlockSnapshot>> blockAt(BlockPos pos);

    /**
     * 그 칸을 data 로 바꾼다. 이웃을 갱신하고 클라이언트에 방송한다(바닐라 setBlock 플래그 11). 소유 region 스레드에서 실행된다.
     * 청크가 로드돼 있지 않거나 높이 밖이면 false 로 끝나고, 알 수 없는 블록·속성이면 IllegalArgumentException 으로 완료된다.
     * BlockPlace/BlockBreak 이벤트는 거치지 않는다.
     */
    CompletableFuture<Boolean> setBlock(BlockPos pos, BlockData data);

    /**
     * 빠른 블록 쓰기(E7). edit 이 적은 칸들을 한꺼번에 바꾼다.
     *
     * <p>edit 은 지금 이 스레드에서 바로 불린다(월드를 읽지 않고 목록만 쌓는다).
     * 적용은 청크마다 그 청크를 가진 region 스레드에서 한다. 여러 region 에 걸치면 region 마다 따로, 동시에 적용한다.
     * <p>한 청크 안에서는 아래 섹션부터, 섹션 안에서는 y·z·x 순서로 바꾼다(적은 순서가 아니다). 순서가 결과를 바꾸는 편집
     * (지지대와 그 위 블록을 같은 편집에서 바꾸며 IMMEDIATE 이웃 갱신 등)은 나눠서 하거나 AFTER_EDIT 을 쓴다.
     * <p>로드되지 않은 청크는 건너뛴다(만들거나 불러오지 않는다). 칸마다 바닐라 Level.setBlock 과 같은 길을 지나므로 결과는
     * 같은 옵션의 setBlock 을 그 순서로 부른 것과 같다. BlockPlace/BlockBreak 이벤트는 거치지 않는다.
     * <p>알 수 없는 블록·속성이면 IllegalArgumentException 으로 완료된다(아무 칸도 바꾸지 않는다).
     * <p>결과를 같은 region 스레드에서 {@code get()} 으로 기다리지 않는다.
     */
    default CompletableFuture<BlockEditResult> editBlocks(BlockEditOptions options, java.util.function.Consumer<BlockEditor> edit) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("editBlocks"));
    }

    /** {@link BlockEditOptions#DEFAULT} 로 바꾼다. World.setBlock 을 칸마다 부른 것과 같은 결과를 한꺼번에 얻는다. */
    default CompletableFuture<BlockEditResult> editBlocks(java.util.function.Consumer<BlockEditor> edit) {
        return editBlocks(BlockEditOptions.DEFAULT, edit);
    }

    /**
     * {@link #editBlocks} 의 동기 버전. 적은 칸의 청크가 모두 이 스레드가 돌리는 region 것일 때만 된다(예: 그 region 이벤트 처리기 안).
     * 아니면 아무 칸도 바꾸지 않고 {@link org.domi.furnace.api.concurrent.ThreadOwnershipException} 을 던진다. chunksPerTick 은 무시하고 한 번에 다 한다.
     */
    default BlockEditResult editBlocksNow(BlockEditOptions options, java.util.function.Consumer<BlockEditor> edit) {
        throw new UnsupportedOperationException("editBlocksNow");
    }

    /**
     * 동기 읽기. 호출한 스레드가 그 칸을 가진 region 스레드일 때만 된다(예: 그 region 에서 발화한 이벤트 처리기 안).
     * 아니면 {@link org.domi.furnace.api.concurrent.ThreadOwnershipException} 을 던진다.
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
     * 그 청크를 올린다(Paper World#getChunkAtAsync). 이미 올라와 있으면 그대로 둔다.
     * generate 가 거짓이면 디스크에 저장된 청크만 올리고, 없으면 새로 만들지 않고 false 로 끝난다.
     *
     * <p>결과는 그 청크를 가진 region 스레드에서 완료된다. 그래서 이어 붙인 작업이 거기서 곧바로 청크를 만질 수 있다.
     * <p>단 이미 끝난 Future 에 붙인 작업은 붙인 스레드에서 돈다(CompletableFuture 규칙이며 Paper 도 같다). 비동기 스레드에서 붙일 때
     * region 스레드에서 돌게 하려면 {@code thenAcceptAsync(..., scheduler(pos)::execute)} 처럼 그 칸의 스케줄러를 실행기로 준다.
     * <p>올린 청크는 Paper 플러그인 티켓처럼 20틱 붙잡고(레벨 33, 올려만 둔다), 그 뒤 플레이어·티켓이 없으면 퇴출 유예가 지나 보통 규칙대로 내려간다.
     * <p>내린 월드면 RejectedExecutionException 으로 끝난다. 아무 스레드.
     */
    default CompletableFuture<Boolean> loadChunkAsync(int chunkX, int chunkZ, boolean generate) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("loadChunkAsync"));
    }

    /**
     * 그 청크를 보고 있는 플레이어에게 청크를 통째로 다시 보낸다(Paper World#refreshChunk). 올라와 있지 않으면 false.
     * 보내기는 그 청크를 가진 region 스레드에서 곧 일어난다(지금 상태가 실린다). 아무 스레드.
     */
    default boolean refreshChunk(int chunkX, int chunkZ) { throw new UnsupportedOperationException("refreshChunk"); }

    /**
     * 그 청크를 이 월드의 생성기(시드·월드젠 훅 포함)로 처음부터 다시 만들어 바꾼다(불만 표 41번).
     *
     * <p>결과 블록은 같은 시드의 새 월드에서 그 청크가 처음 만들어질 때와 같다. 이웃 청크에서 넘어오는 나무·구조물 조각도 포함하고,
     * 이 청크에서 이웃으로 넘치는 장식은 이웃을 바꾸지 않는다.
     * <p>블록·바이옴·블록 엔티티·예약 틱·구조물 참조·POI 를 바꾸고, 빛을 다시 계산하고, 그 청크를 보는 플레이어에게 다시 보낸다.
     * 그 청크를 가진 region 스레드에서만 바꾸므로 다른 region 은 건드리지 않는다.
     * <p>로드돼 있지 않으면 먼저 불러온다. 아무 스레드. 내린 월드면 false.
     */
    default CompletableFuture<Boolean> regenerateChunk(int chunkX, int chunkZ) {
        return regenerateChunk(chunkX, chunkZ, ChunkRegeneration.DEFAULT);
    }

    /** {@link #regenerateChunk(int, int)} 에 엔티티 처리를 고르는 버전. */
    default CompletableFuture<Boolean> regenerateChunk(int chunkX, int chunkZ, ChunkRegeneration options) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("regenerateChunk"));
    }

    // ------------------------------------------------------------------ 엔티티

    /**
     * type(minecraft:zombie) 엔티티를 at 에 만들어 넣는다. 그 칸을 가진 region 스레드에서 /summon 과 같은 경로로 만든다
     * (몹은 finalizeSpawn 으로 장비·변종을 고르고, 스폰 이유는 COMMAND 다). EntitySpawnEvent 를 거치며, 취소되면 null 로 완료된다.
     * 모르는 종류·플레이어·낚시찌는 IllegalArgumentException 으로 완료되고, 청크가 로드돼 있지 않으면 IllegalStateException 으로 완료된다(청크를 만들지 않는다).
     */
    CompletableFuture<Entity> spawnEntity(String type, Location at);

    /**
     * center 에서 radius 안(축 정렬 상자 안, 거리 제곱 비교)의 엔티티와 플레이어. center 칸을 가진 region 에서 읽는다.
     * 그 region 에 없는(다른 region 이 가진 청크에 있는) 엔티티는 빠진다. radius 는 0..128.
     */
    CompletableFuture<List<Entity>> nearbyEntities(Location center, double radius);

    /**
     * spawnEntity 의 동기 버전. center 칸을 가진 region 스레드에서만 된다(아니면 {@link org.domi.furnace.api.concurrent.ThreadOwnershipException} 을 던진다).
     * 곧바로 만들고 엔티티를 돌려준다. 스폰 이벤트가 취소되면 null 이다. 청크가 로드돼 있지 않으면 IllegalStateException, 종류·위치가 잘못이면 IllegalArgumentException 을 던진다.
     */
    default Entity spawnEntityNow(String type, Location at) { throw new UnsupportedOperationException("spawnEntityNow"); }

    /** nearbyEntities 의 동기 버전. center 칸을 가진 region 스레드에서만 되고(아니면 ThreadOwnershipException), 곧바로 목록을 돌려준다. 청크가 로드돼 있지 않으면 빈 목록. */
    default List<Entity> nearbyEntitiesNow(Location center, double radius) { throw new UnsupportedOperationException("nearbyEntitiesNow"); }

    /**
     * box 와 충돌 상자가 겹치는(경계만 닿는 것은 제외) 엔티티와 플레이어(바닐라 Level.getEntities(null, box)).
     *
     * <p>region 스레드에서는 지금 스레드가 돌리는 region 의 엔티티만 본다. 상자 가운데가 어디든, 상자가 아무리 커도 예외 없이 남의 region 엔티티는 빠진다.
     * <p>전역 스레드의 빌림 구간({@code openRegionAccess})에서는 상자와 겹치는 로드된 region 을 모두 빌려 합친다.
     * 그 밖의 스레드는 {@link org.domi.furnace.api.concurrent.ThreadOwnershipException} 을 던진다. 사라진 엔티티는 빠진다.
     * <p>기본 설정(region-section-shift=3, region-merge-radius=1)에서 다른 region 은 지금 region 의 칸에서 축마다 적어도 129블록 떨어져 있다.
     * 그래서 지금 region 안의 점에서 128블록 이내 상자는 빠짐없이 보이고, 그보다 크면 빌림 구간을 쓴다(예전 기본 shift 4 에서는 256블록까지였다).
     */
    default List<Entity> entitiesInBoxNow(Aabb box) { throw new UnsupportedOperationException("entitiesInBoxNow"); }

    /**
     * 블록 광선 판정(바닐라 Level.clip). 선분 (fx,fy,fz)에서 (tx,ty,tz)까지 처음 맞는 블록을 찾는다.
     * 시작점이 든 칸의 region 스레드에서만 된다(아니면 ThreadOwnershipException). 안 맞으면 empty 다.
     *
     * <p>ignorePassable 이면 충돌 모양(지나갈 수 있는 블록은 건너뛴다)을, 아니면 윤곽 모양을 쓴다.
     * <p>canCollide 가 false 를 주는 칸은 블록도 유체도 건너뛴다(null 이면 모두 본다. Paper 의 canCollide). canCollide 는 그 region 스레드에서 불린다.
     * <p>로드되지 않은(또는 그 region 이 갖지 않은) 청크에 닿으면 거기서 빗나간 것으로 끝낸다(청크를 만들지 않는다).
     * <p>기본 설정(region-section-shift=3)에서 다른 region 은 시작 region 의 칸에서 축마다 적어도 129블록 떨어져 있다.
     * 그래서 128블록 이내 광선은 Paper 와 같고, 그보다 길면 남의 region 에 닿는 곳에서 빗나간 것으로 끝날 수 있다(예전 기본 shift 4 에서는 256블록까지).
     */
    default Optional<BlockHit> rayTraceBlocksNow(double fx, double fy, double fz, double tx, double ty, double tz, FluidPick fluid, boolean ignorePassable,
            java.util.function.Predicate<BlockPos> canCollide) {
        throw new UnsupportedOperationException("rayTraceBlocksNow");
    }

    /**
     * 네트워크 엔티티 id({@link Entity#entityId()}, 패킷이 엔티티를 가리키는 번호)로 이 월드의 엔티티나 플레이어를 찾는다(Paper 의
     * ServerLevel#getEntity(int)). 그 엔티티를 가진 region 에서 읽은 새 핸들로 완료하고, 없거나 다른 월드에 있거나 사라졌으면 empty 다.
     * 엔티티가 region 을 옮겨 가는 도중이면 몇 번 다시 찾는다. 아무 스레드.
     */
    default CompletableFuture<Optional<Entity>> entityById(int entityId) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("entityById"));
    }

    /**
     * 그 id 의 엔티티·플레이어 UUID(없으면 empty). 엔티티 상태는 읽지 않고 id 에서 UUID 만 보므로 어느 스레드에서나 기다림 없이 된다.
     * 호환 계층이 이미 아는 Bukkit 객체를 소유 스레드가 아닌 곳에서도 곧바로 돌려줄 때 쓴다.
     */
    default Optional<java.util.UUID> entityUuidById(int entityId) { throw new UnsupportedOperationException("entityUuidById"); }

    /**
     * entityById 의 동기 버전. 그 엔티티를 가진 region 스레드에서만 곧바로 핸들을 돌려준다(옛 동기 작업의 빌림 구간 포함, 아니면
     * {@link org.domi.furnace.api.concurrent.ThreadOwnershipException} 을 던진다). 없으면 empty 다.
     * 플레이어는 어느 스레드에서나 된다.
     */
    default Optional<Entity> entityByIdNow(int entityId) { throw new UnsupportedOperationException("entityByIdNow"); }

    /**
     * UUID 로 이 월드의 엔티티나 플레이어를 찾는다(Paper 의 ServerLevel#getEntity(UUID)). {@link #entityById} 와 같은 규칙이다. 그 엔티티를 가진
     * region 에서 읽은 새 핸들로 완료하고, 없거나 다른 월드에 있거나 사라졌으면 empty 다. 아무 스레드.
     */
    default CompletableFuture<Optional<Entity>> entityByUuid(java.util.UUID uuid) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("entityByUuid"));
    }

    /**
     * entityByUuid 의 동기 버전. 그 엔티티를 가진 region 스레드에서만 곧바로 핸들을 돌려준다(옛 동기 작업의 빌림 구간 포함, 아니면
     * {@link org.domi.furnace.api.concurrent.ThreadOwnershipException} 을 던진다). 없으면 empty 다. 플레이어는 어느 스레드에서나 된다.
     * 결과는 실행 이력(그 엔티티를 전에 본 적이 있는가)과 상관없이 그 순간 월드에 있는가로만 정해진다.
     */
    default Optional<Entity> entityByUuidNow(java.util.UUID uuid) { throw new UnsupportedOperationException("entityByUuidNow"); }

    /** 그 UUID 엔티티·플레이어의 네트워크 id(없으면 empty). 엔티티 상태는 읽지 않으므로 어느 스레드에서나 기다림 없이 된다. */
    default java.util.OptionalInt entityIdByUuid(java.util.UUID uuid) { throw new UnsupportedOperationException("entityIdByUuid"); }

    // ------------------------------------------------------------------ 블록 칸

    /**
     * 그 칸 블록 엔티티의 칸(상자·통·셜커 상자·깔때기·발사기·공급기·화로류·양조기·제작기). 칸이 없는 블록이거나 로드되지 않았으면 empty.
     * 큰 상자는 그 반쪽(27칸)만 다룬다. 두 반쪽을 합친 보기는 다음 단계에서 만든다. 읽기·쓰기는 그 칸을 가진 region 스레드에서 실행되고,
     * 그 사이 블록이 부서졌으면 IllegalStateException 으로 완료된다. 그 region 스레드에서 부르면 읽기·쓰기는 곧바로 끝난 Future 를 준다
     * (이어 붙인 thenAccept 도 그 자리에서 돈다). 다른 스레드에서 부르면 그 region 의 대기열에 들어간다.
     */
    CompletableFuture<Optional<Inventory>> inventoryAt(BlockPos pos);

    /**
     * {@link #inventoryAt} 의 소유 스레드 버전이다. 그 칸을 가진 region 스레드에서만(아니면 ThreadOwnershipException). 돌려준 Inventory 의 읽기·쓰기는
     * 그 region 스레드에서 부르면 곧바로 끝난 Future 를 준다(Bukkit 블록 인벤토리처럼 동기로 쓸 수 있다).
     */
    default Optional<Inventory> inventoryAtNow(BlockPos pos) { throw new UnsupportedOperationException("inventoryAtNow"); }

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

    /** {@link #biomeAt} 의 동기 버전이다. 그 칸을 가진 region 스레드에서만 된다(아니면 ThreadOwnershipException). 높이 밖은 끝 칸으로 죈다. 청크가 없으면 empty. */
    default Optional<String> biomeNow(BlockPos pos) { throw new UnsupportedOperationException("biomeNow"); }

    /**
     * 그 칸이 든 4×4×4 바이옴 칸 하나를 바꾸는 동기 버전이다. 그 칸을 가진 region 스레드에서만 된다(아니면 ThreadOwnershipException). 높이 밖은 끝 칸으로 죈다.
     * 클라이언트에 다시 보내지 않는다(Paper CraftWorld.setBiome 과 같다). 모르는 키는 IllegalArgumentException, 청크가 없으면 false.
     */
    default boolean setBiomeNow(BlockPos pos, String biome) { throw new UnsupportedOperationException("setBiomeNow"); }

    // ------------------------------------------------------------------ 소리·입자

    /**
     * 그 지점에서 소리를 낸다(바닐라 Level.playSeededSound): 이 차원 접속자 중 크기에 따른 거리(크기 1 이하 16칸, 넘으면 16×크기) 안에 있는 사람에게 들린다.
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
     * 그 청크를 가진 region 의 관측 사본. 그 청크의 구역이 어느 region 에도 속하지 않으면 empty. 아무 스레드.
     * 값은 잰 순간의 것이다(region 은 합쳐지거나 나뉜다).
     */
    default java.util.Optional<RegionInfo> regionAt(int chunkX, int chunkZ) { return java.util.Optional.empty(); }

    default java.util.Optional<RegionInfo> regionAt(BlockPos pos) { return regionAt(pos.chunkX(), pos.chunkZ()); }

    /** 이 차원의 살아 있는 region 모두(id 순). */
    default List<RegionInfo> regions() { return List.of(); }

    // ------------------------------------------------------------------ 스폰 정책·엔티티 수

    /** 이 월드의 자연 스폰 정책(기본은 바닐라 상수이며, 바꾸지 않으면 스폰 루프는 바닐라 그대로다). 아무 스레드. */
    default SpawnPolicy spawnPolicy() { throw new UnsupportedOperationException("spawnPolicy"); }

    /**
     * 지금 로드된 청크 전체의 엔티티 수(플레이어 포함). region 마다 자기 청크를 세어 합친다. 그래서 서로 다른
     * 틱 순간의 값이 섞일 수 있다. 로드되지 않은 청크의 엔티티는 세지 않는다.
     */
    default CompletableFuture<EntityCounts> entityCounts() {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("entityCounts"));
    }

    // ------------------------------------------------------------------ 마을·POI·습격

    /**
     * 그 칸이 마을 안인가(바닐라 isVillage. 점유된 마을 POI 가 든 구역에서 구역 1칸 이내). 그 칸을 가진 region 이 올려 둔 청크만 본다.
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

    /** 그 번호의 습격을 멈춘다(바닐라 Raid.stop. 보스바가 사라지고 습격자는 남는다). 멈췄으면 true, 모르는 번호면 false. */
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
     * 그 칸을 가진 region 스레드에서 블록 엔티티 NBT 를 읽어 edit 로 고치고, 바뀌었으면 다시 싣는다(/data modify·merge block 과 같은 경로로,
     * 바닐라 loadWithComponents, setChanged, 클라이언트 갱신 순서다). 바뀌었으면 true, 그대로거나 블록 엔티티가 없거나 로드되지 않았으면 false.
     * 위치 키(x·y·z)를 바꿔도 블록 엔티티는 옮겨지지 않는다(바닐라와 같다).
     */
    CompletableFuture<Boolean> editBlockEntityNbt(BlockPos pos, java.util.function.Consumer<NbtCompound> edit);

    /**
     * {@link #blockEntityNbt} 의 소유 스레드 버전이다. 그 칸을 가진 region 스레드에서만(아니면 ThreadOwnershipException).
     * 로드되지 않았거나 블록 엔티티가 없으면 empty.
     */
    default Optional<NbtCompound> blockEntityNbtNow(BlockPos pos) { throw new UnsupportedOperationException("blockEntityNbtNow"); }

    /**
     * 소유 스레드에서 블록 엔티티 NBT 를 통째로 싣는다(/data 쓰기와 같은 길로 loadWithComponents, setChanged, 클라이언트 갱신 순서이며, 위치 키는 그 칸으로 맞춘다).
     * 블록 엔티티가 없거나 로드되지 않았으면 false. 그 칸을 가진 region 스레드에서만(아니면 ThreadOwnershipException).
     */
    default boolean setBlockEntityNbtNow(BlockPos pos, NbtCompound nbt) { throw new UnsupportedOperationException("setBlockEntityNbtNow"); }

    /**
     * 그 칸 블록 엔티티의 플러그인 영속 데이터(저장 위치는 Paper 와 같은 블록 엔티티 NBT 의 PublicBukkitValues 이며 클라이언트로는 안 나간다)를
     * 소유 region 스레드에서 곧바로 고친다. 컨테이너는 콜백 안에서만 쓴다. 나머지 블록 엔티티 필드는 건드리지 않고(전체 NBT 를 다시 싣지 않음)
     * 블록 엔티티를 바뀜으로 표시한다. 로드되지 않았거나 블록 엔티티가 없으면 false. 그 칸을 가진 region 스레드에서만(아니면 ThreadOwnershipException).
     */
    default boolean editBlockEntityPersistentDataNow(BlockPos pos, java.util.function.Consumer<org.domi.furnace.api.persistence.PersistentDataContainer> edit) {
        throw new UnsupportedOperationException("editBlockEntityPersistentDataNow");
    }

    /** {@link #editBlockEntityPersistentDataNow} 를 그 칸을 가진 region 스레드에서 돌린다. 블록 엔티티가 없거나 로드되지 않았으면 false. */
    default CompletableFuture<Boolean> editBlockEntityPersistentData(BlockPos pos, java.util.function.Consumer<org.domi.furnace.api.persistence.PersistentDataContainer> edit) {
        throw new UnsupportedOperationException("editBlockEntityPersistentData");
    }

    /** patch 를 합친다(/data merge block). 바뀌었으면 true. */
    default CompletableFuture<Boolean> mergeBlockEntityNbt(BlockPos pos, NbtCompound patch) {
        NbtCompound p = patch.copy();
        return editBlockEntityNbt(pos, n -> n.merge(p));
    }

    // ---- 시간·날씨·스폰·폴더(T-141). 모든 차원(추가 월드 포함)이 오버월드의 시계를 함께 쓴다(바닐라 DerivedLevelData).
    // 그래서 오버월드가 아닌 월드에서 바꾸기는 아무 일도 하지 않고 false 다(바닐라 DerivedLevelData 의 set 이 무시되는 것과 같다).
    // 읽기·쓰기 모두 아무 스레드.

    /** 하루 시각(바닐라 Level.getDayTime, 24000 을 넘어 계속 는다). */
    default long dayTime() { throw new UnsupportedOperationException("dayTime"); }

    /** 하루 시각을 바꾸고 이 월드의 접속자에게 곧바로 알린다(Paper CraftWorld#setFullTime). 이 월드가 시계 주인이 아니면 false. */
    default boolean setDayTime(long time) { throw new UnsupportedOperationException("setDayTime"); }

    /** 월드가 흐른 틱(바닐라 Level.getGameTime). */
    default long gameTime() { throw new UnsupportedOperationException("gameTime"); }

    /** 이 월드가 시간·날씨의 주인인가(오버월드만 true). */
    default boolean ownsClock() { return true; }

    /** level.dat 의 생성기가 평지(또는 빈 월드)인가(바닐라 isFlatWorld). 클라이언트의 지평선·안개가 달라진다. 불변. */
    default boolean isFlat() { return false; }

    /** 이 월드를 만들 때 준 바이옴 공급자(T-210, {@link WorldCreator.Builder#biomeProvider}). 없으면 null. 불변. */
    default org.domi.furnace.api.worldgen.BiomeProvider biomeProvider() { return null; }

    /** 이 월드를 만들 때 단 월드젠 훅 객체(T-217, {@link WorldCreator.Builder#generationHooksObject}). 없으면 null. 불변. */
    default org.domi.furnace.api.worldgen.ChunkGenerationHooks generationHooksObject() { return null; }

    /**
     * 이 월드의 장식 단계 끝(바닐라 장식과 월드젠 훅의 decorate 뒤)에 도는 일을 단다(T-217 — Bukkit World#getPopulators 와 같은 자리).
     * 생성기와 무관하게 모든 월드에서 돌고, 바닐라 장식을 끈 청크에서도 돈다. 디버그 월드는 장식 단계가 없어 돌지 않는다(바닐라와 같다).
     * 단 뒤에 처음 생성되는 청크부터 적용되고, 단 순서대로 돈다. 생성 스레드 여러 개에서 동시에 불린다.
     */
    default void addChunkDecorator(org.domi.furnace.api.worldgen.ChunkDecorator decorator) { throw new UnsupportedOperationException("addChunkDecorator"); }

    /** {@link #addChunkDecorator} 로 단 것을 뗀다. 달려 있었으면 true. */
    default boolean removeChunkDecorator(org.domi.furnace.api.worldgen.ChunkDecorator decorator) { return false; }

    /** level.dat 의 구조물 생성 깃발(바닐라 WorldOptions.generateStructures). 불변. */
    default boolean generatesStructures() { return true; }

    /** 하드코어 월드인가(level.dat hardcore. 추가 월드는 주 월드 값을 따른다). */
    default boolean isHardcore() { return false; }

    /** 날씨 상태(level.dat 의 raining·thundering·rainTime·thunderTime·clearWeatherTime). */
    default WeatherState weather() { throw new UnsupportedOperationException("weather"); }

    /** 비 깃발·남은 틱들을 바꾼다(null 인 칸은 그대로). 다음 전역 틱의 날씨 순환이 이어받는다. 시계 주인이 아니면 false. */
    default boolean setWeather(Boolean raining, Boolean thundering, Integer rainTime, Integer thunderTime, Integer clearWeatherTime) {
        throw new UnsupportedOperationException("setWeather");
    }

    /** 월드 스폰(바닐라 공유 스폰: 블록 좌표와 바라보는 각도). */
    default Location spawnLocation() { throw new UnsupportedOperationException("spawnLocation"); }

    /**
     * 월드 스폰을 바꾼다(바닐라 ServerLevel.setDefaultSpawnPos: 스폰 청크 티켓을 옮기고 모든 접속자에게 알린다). 네더·엔드는 오버월드 스폰을
     * 함께 쓰므로 false. 추가 월드는 자기 level.dat 의 스폰을 바꾼다.
     */
    default boolean setSpawnLocation(int x, int y, int z, float angle) { throw new UnsupportedOperationException("setSpawnLocation"); }

    /** 이 차원의 저장 폴더(오버월드는 월드 폴더, 네더는 DIM-1, 엔드는 DIM1, 추가 월드는 그 월드 폴더). */
    default java.nio.file.Path folder() { throw new UnsupportedOperationException("folder"); }

    /**
     * 이 월드의 모든 엔티티(플레이어 포함) 핸들 스냅숏이다. 아무 스레드. 서버의 엔티티 색인(동시 맵)을 읽을 뿐이라 region 상태를 건드리지 않는다.
     * 핸들로 상태를 읽고 쓰는 것은 여느 때처럼 소유 스레드 규칙을 따른다.
     */
    default List<Entity> entitiesSnapshot() { throw new UnsupportedOperationException("entitiesSnapshot"); }

    /**
     * 그 청크 안(블록 좌표 기준)에 있는 살아 있는 엔티티와 플레이어(Paper Chunk#getEntities — isValid 로 거름). 그 청크를 가진 region 스레드 전용
     * (전역 스레드의 region 빌림 구간 포함), 아니면 ThreadOwnershipException.
     */
    default List<Entity> entitiesInChunkNow(int chunkX, int chunkZ) { throw new UnsupportedOperationException("entitiesInChunkNow"); }

    /** 그 칸의 밝기. 하늘빛(지금 하늘 어두움을 뺀 값)과 블록빛 중 큰 값(바닐라 getMaxLocalRawBrightness). 소유 스레드 전용. 청크가 없으면 IllegalStateException. */
    default int lightLevelNow(BlockPos pos) { throw new UnsupportedOperationException("lightLevelNow"); }

    /** 그 칸의 하늘빛(0..15, 낮밤 무관). 소유 스레드 전용. */
    default int skyLightNow(BlockPos pos) { throw new UnsupportedOperationException("skyLightNow"); }

    /** 그 칸의 블록빛(0..15). 소유 스레드 전용. */
    default int blockLightNow(BlockPos pos) { throw new UnsupportedOperationException("blockLightNow"); }

    /**
     * 그 칸을 이 도구로 부수면 나올 드롭(전리품 표를 굴리므로 월드 난수를 쓴다). tool 이 null 이면 도구 검사 없이 빈손으로 굴린다.
     * 도구가 있는데 맞는 도구가 아니면(도구가 필요한 블록) 빈 목록. 소유 스레드 전용.
     */
    default List<org.domi.furnace.api.item.ItemStack> dropsNow(BlockPos pos, org.domi.furnace.api.item.ItemStack tool) {
        throw new UnsupportedOperationException("dropsNow");
    }

    /**
     * 플레이어 없이 그 칸을 부순다(Paper Block#breakNaturally). 공기(air)가 아니고 도구 조건이 맞으면 드롭을 떨구고, triggerEffect 면 부서짐 효과를,
     * dropExperience 면 경험치를 낸다. 그 뒤 칸을 그 자리 유체(없으면 공기)로 바꾼다. 얼음은 물이 되고 거북알은 하나 줄어든다.
     * 칸을 바꿨고 드롭 조건도 맞았을 때만 true. 소유 스레드 전용, 청크가 없으면 IllegalStateException.
     */
    default boolean breakNaturallyNow(BlockPos pos, org.domi.furnace.api.item.ItemStack tool, boolean triggerEffect, boolean dropExperience) {
        throw new UnsupportedOperationException("breakNaturallyNow");
    }

    /**
     * 지형 생성 configured feature(예: "minecraft:oak") 하나를 pos 에 놓는다(Paper CraftRegionAccessor#generateTree 의 feature place).
     * random 이 feature 난수로 그대로 쓰여 그 Random 의 상태도 나아간다. apply 면 월드에 바로 반영하고(쓰기 flag 는 바닐라와 같다),
     * 아니면 월드를 바꾸지 않고 쓸 칸만 {@link FeaturePlacement#writes()} 로 돌려준다(가장자리 모양 갱신·블록 엔티티와, Paper 처럼 부술 수 없는 블록 칸은 빠진다).
     * 그 칸의 3×3 청크 밖이나 로드되지 않은·다른 region 청크에 닿으면 아무것도 하지 않고 placed=false.
     * 모르는 id 면 IllegalArgumentException, Furnace 가 아직 구현하지 않은 feature 면 UnsupportedOperationException.
     * 소유 스레드 전용, 그 칸 청크가 없으면 IllegalStateException.
     */
    default FeaturePlacement placeFeatureNow(String configuredFeature, BlockPos pos, java.util.Random random, boolean apply) {
        throw new UnsupportedOperationException("placeFeatureNow");
    }

    /** 후렴 식물을 키운다(바닐라 ChorusFlowerBlock.generatePlant(…, 8), flag 2). 바닥·주변을 검사하지 않고 항상 placed=true(닿지 못하면 false). 나머지는 placeFeatureNow 와 같다. */
    default FeaturePlacement growChorusPlantNow(BlockPos pos, java.util.Random random, boolean apply) {
        throw new UnsupportedOperationException("growChorusPlantNow");
    }

    /**
     * 떨어진 아이템을 만든다(Paper CraftWorld#dropItem: 줍기 지연 10틱, 스폰 이유 CUSTOM). naturally 면 바닐라처럼 월드 난수로 각 축
     * 0.25~0.75 를 더한 자리. beforeSpawn 은 월드에 넣기 전에 부른다(null 가능). 소유 region 스레드 전용. 스폰이 취소되면 null.
     */
    default Entity dropItemNow(Location at, org.domi.furnace.api.item.ItemStack stack, boolean naturally, java.util.function.Consumer<Entity> beforeSpawn) {
        throw new UnsupportedOperationException("dropItemNow");
    }
}
