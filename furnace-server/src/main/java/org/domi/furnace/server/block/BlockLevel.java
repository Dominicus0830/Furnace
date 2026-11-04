// 블록 동작이 보는 월드(바닐라 Level/LevelAccessor/LevelReader 중 블록 코드가 쓰는 부분). 서버 쪽만(isClientSide = false).
package org.domi.furnace.server.block;

import org.domi.furnace.server.entity.ItemStack;

public interface BlockLevel {

    // ------------------------------------------------------------------ 읽기

    int getBlockState(Pos pos);

    /** 유체 상태(Fluids). */
    int getFluidState(Pos pos);

    int minBuildHeight();

    /** 상한(제외). */
    int maxBuildHeight();

    default boolean isOutsideBuildHeight(int y) { return y < minBuildHeight() || y >= maxBuildHeight(); }

    /** isInWorldBounds: 높이 안이고 x,z 가 ±30000000 안. */
    default boolean isInWorldBounds(Pos p) {
        return !isOutsideBuildHeight(p.y()) && p.x() >= -30000000 && p.z() >= -30000000 && p.x() < 30000000 && p.z() < 30000000;
    }

    /** 그 청크가 로드돼 있는가(hasChunkAt). */
    boolean hasChunkAt(Pos pos);

    default boolean isEmptyBlock(Pos pos) { return BlockInfo.get().isAir(getBlockState(pos)); }

    default boolean isWaterAt(Pos pos) { return Fluids.isWater(getFluidState(pos)); }

    /**
     * 상자(월드 좌표)에 걸친 엔티티 수 — 플레이어 포함, 관전자·블록 동작을 무시하는 엔티티 제외(압력판 getEntityCount·덫줄).
     * livingOnly 면 생물만(돌 압력판).
     */
    default int countTriggeringEntities(double x0, double y0, double z0, double x1, double y1, double z1, boolean livingOnly) { return 0; }

    /** EntityGetter.isUnobstructed(null, shape): 건설을 막는 엔티티(관전자 제외)의 상자와 겹치지 않는가. shape 는 월드 좌표. */
    boolean isUnobstructed(Shape worldShape);

    /** CollisionGetter.isUnobstructed(state, pos, ctx): 충돌 모양이 비었거나 엔티티와 겹치지 않으면. */
    default boolean isUnobstructed(int state, Pos pos, BlockActor collisionEntity) {
        Shape s = BlockBehaviors.of(state).getCollisionShape(state, this, pos, collisionEntity);
        return s.isEmpty() || isUnobstructed(s.move(pos.x(), pos.y(), pos.z()));
    }

    // ------------------------------------------------------------------ 쓰기(바닐라 플래그: Block.UPDATE_*)

    int UPDATE_NEIGHBORS = 1, UPDATE_CLIENTS = 2, UPDATE_INVISIBLE = 4, UPDATE_IMMEDIATE = 8, UPDATE_KNOWN_SHAPE = 16,
            UPDATE_SUPPRESS_DROPS = 32, UPDATE_MOVE_BY_PISTON = 64, UPDATE_NONE = 4, UPDATE_ALL = 3, UPDATE_ALL_IMMEDIATE = 11,
            UPDATE_LIMIT = 512;

    boolean setBlock(Pos pos, int state, int flags, int recursionLeft);

    default boolean setBlock(Pos pos, int state, int flags) { return setBlock(pos, state, flags, UPDATE_LIMIT); }

    default boolean setBlockAndUpdate(Pos pos, int state) { return setBlock(pos, state, UPDATE_ALL); }

    /** removeBlock: 그 자리 유체의 레거시 블록으로(3 | 이동 중이면 64). */
    default boolean removeBlock(Pos pos, boolean moving) {
        return setBlock(pos, Fluids.createLegacyBlock(getFluidState(pos)), UPDATE_ALL | (moving ? UPDATE_MOVE_BY_PISTON : 0));
    }

    boolean destroyBlock(Pos pos, boolean dropBlock, BlockActor breaker, int recursionLeft);

    default boolean destroyBlock(Pos pos, boolean dropBlock) { return destroyBlock(pos, dropBlock, null, UPDATE_LIMIT); }

    /** Level.destroyBlock(pos, drop, entity): 부순 쪽이 블록 행위자가 아닌 엔티티(투사체 등)일 때. 게임 이벤트 원인만 엔티티로 싣는다. */
    default boolean destroyBlockByEntity(Pos pos, boolean dropBlock, Object entity) { return destroyBlock(pos, dropBlock); }

    // ------------------------------------------------------------------ 이웃·모양 갱신

    void updateNeighborsAt(Pos pos, int sourceBlock);

    void updateNeighborsAtExceptFromFacing(Pos pos, int sourceBlock, int skipDir);

    void neighborChanged(Pos pos, int sourceBlock, Pos fromPos);

    void neighborChanged(int state, Pos pos, int sourceBlock, Pos fromPos, boolean moved);

    void neighborShapeChanged(int dir, int neighborState, Pos pos, Pos neighborPos, int flags, int recursionLeft);

    default void blockUpdated(Pos pos, int block) { updateNeighborsAt(pos, block); }

    void updateNeighbourForOutputSignal(Pos pos, int block);

    // ------------------------------------------------------------------ 예약 틱

    void scheduleTick(Pos pos, int block, int delay, int priority);

    default void scheduleTick(Pos pos, int block, int delay) { scheduleTick(pos, block, delay, 0); }

    void scheduleFluidTick(Pos pos, int fluidType, int delay);

    boolean hasScheduledTick(Pos pos, int block);

    /** 지금 틱에 실행될 예약이 있는가(getBlockTicks().willTickThisTick). */
    boolean willTickThisTick(Pos pos, int block);

    // ------------------------------------------------------------------ 레드스톤(바닐라 SignalGetter)

    /** getSignal(pos, dir): 그 블록이 dir 쪽(이웃 쪽에서 본 방향)으로 내는 신호 — 전도체면 직접 신호 최댓값. */
    int getSignal(Pos pos, int dir);

    int getDirectSignal(Pos pos, int dir);

    int getDirectSignalTo(Pos pos);

    boolean hasSignal(Pos pos, int dir);

    boolean hasNeighborSignal(Pos pos);

    int getBestNeighborSignal(Pos pos);

    // ------------------------------------------------------------------ 효과(소리·레벨 이벤트·블록 이벤트·게임 이벤트)

    /** Level.playSound(except, pos, 소리, 분류, 크기, 높이) — 블록 가운데에서. */
    void playSound(BlockActor except, Pos pos, String sound, String source, float volume, float pitch);

    void playSound(BlockActor except, double x, double y, double z, String sound, String source, float volume, float pitch);

    void levelEvent(BlockActor except, int type, Pos pos, int data);

    /**
     * ServerLevel.sendParticles(옵션, x,y,z, 개수, 퍼짐 dx,dy,dz, 속도): 주변(칸 가운데 32 미만) 플레이어에게 보내고 그 수를 돌려준다.
     * 위치 오프셋 난수는 클라이언트가 계산하므로 서버 난수를 쓰지 않는다. 입자를 모르는 레벨은 아무것도 보내지 않는다.
     */
    default int sendParticles(org.domi.furnace.server.net.protocol.play.ParticleOptions p, double x, double y, double z,
            int count, double dx, double dy, double dz, double speed) { return 0; }

    /** ServerLevel.globalLevelEvent: globalSoundEvents 게임룰이면 모든 플레이어에게, 아니면 levelEvent. */
    default void globalLevelEvent(int type, Pos pos, int data) { levelEvent(null, type, pos, data); }

    void blockEvent(Pos pos, int block, int type, int data);

    /** 게임 이벤트(스컬크 감지 등) — 진동 체계가 들어오기 전까지는 기록만. */
    void gameEvent(String event, Pos pos, BlockActor source, int state);

    /** gameEvent 와 같되 출처가 엔티티(Entity·Player)일 수 있다. */
    default void gameEventFrom(String event, Pos pos, Object source, int state) {
        gameEvent(event, pos, source instanceof BlockActor a ? a : null, state);
    }

    // ------------------------------------------------------------------ 기타

    /**
     * Level.getRawBrightness(pos, skyDarken): max(하늘빛 - skyDarken, 블록빛).
     * TODO(빛): 빛 엔진 전까지 15(하늘이 보이는 바깥과 같은 값) — 어두운 지하에서만 바닐라와 달라진다.
     */
    default int getRawBrightness(Pos pos, int skyDarken) { return 15 - skyDarken; }

    /**
     * Level.skyDarken(updateSkyBrightness): 해 높이·비·천둥으로 정한 하늘빛 감소량(0..11).
     * 바닐라는 틱 머리에 한 번 계산해 두지만 같은 틱 안에서는 값이 같다.
     */
    default int skyDarken() {
        float rain = rainLevel();
        double d0 = 1.0 - rain * 5.0F / 16.0;
        double d1 = 1.0 - thunderLevel() * rain * 5.0F / 16.0;
        double d2 = 0.5 + 2.0 * Math.clamp(Dir.cos(B.timeOfDay(this) * (float) (Math.PI * 2)), -0.25, 0.25);
        return (int) ((1.0 - d2 * d0 * d1) * 11.0);
    }

    /** 숨은 블록이 부서질 때 좀벌레를 만든다(엔티티가 없는 레벨은 아무것도 안 한다). */
    default void spawnSilverfishAt(Pos pos) {}

    /** 거북알이 부화할 때 아기 거북 하나(나이 -24000, 집 = pos)를 만든다. index 는 알 번호. */
    default void spawnBabyTurtle(Pos pos, int index) {}

    /**
     * 개구리알이 부화할 때 올챙이 하나를 만든다(FrogspawnBlock.spawnTadpoles 의 한 번).
     * x·z 는 칸 좌표에 더할 오프셋, yRot 은 몸 방향. 올챙이는 persistenceRequired 다.
     */
    default void spawnTadpole(Pos pos, double xOffset, double zOffset, float yRot) {}

    /** 스니퍼 알이 부화할 때 아기 스니퍼를 칸 가운데(getCenter)에 만든다. yRot 은 몸 방향. */
    default void spawnBabySniffer(Pos pos, float yRot) {}

    /**
     * PiglinAi.angerNearbyPiglins: 플레이어 상자에서 16칸 안의 한가한 피글린을 그 플레이어에게 화나게 한다.
     * 엔티티가 없는 레벨·플레이어가 아닌 행위자는 아무것도 안 한다.
     */
    default void angerNearbyPiglins(BlockActor player, boolean requireLineOfSight) {}

    /** 호박 머리로 완성된 골렘을 만든다(눈 골렘 또는 플레이어가 만든 철 골렘). bottom 은 모양의 맨 아래 가운데 칸. */
    default void spawnGolem(boolean iron, Pos bottom) {}

    /** 점화된 TNT 엔티티를 만든다(fuse < 0 이면 기본 80틱). 엔티티가 없는 레벨은 아무것도 안 한다. */
    default void spawnPrimedTnt(Pos pos, BlockActor igniter, int fuse) {}

    /** spawnPrimedTnt 와 같되 주인을 생물로 바로 준다(불붙은 투사체를 쏜 쪽). */
    default void spawnPrimedTntOwned(Pos pos, org.domi.furnace.server.entity.Targetable owner, int fuse) {}

    /** FallingBlockEntity.fall(level, pos, state): 그 자리를 유체(없으면 공기)로 바꾸고 떨어지는 블록 엔티티를 만든다. 만든 엔티티(없으면 null). */
    default Object spawnFallingBlock(Pos pos, int state) { return null; }

    /** WitherSkullBlock.checkSpawn 성공: 위더를 (bottomCenter 칸 가운데, 높이 +0.55)에 무적 상태로 만든다. 줄이 x 축이면 axisX. */
    default void spawnWither(Pos bottomCenter, boolean axisX) {}

    /** 스폰 알: 그 종류 몹을 만든다(EntityType.spawn). 만들었으면 true. 몹이 없는 레벨은 false. */
    default boolean spawnMobFromEgg(String entityType, Pos pos, boolean snapToGround, boolean extendDown) { return false; }

    /** 스폰 이유를 지정하는 변형(발사기 = DISPENSER). 기본은 이유를 무시하고 위 메서드로. */
    default boolean spawnMobFromEgg(String entityType, Pos pos, boolean snapToGround, boolean extendDown,
            org.domi.furnace.server.entity.spawn.SpawnReason reason) { return spawnMobFromEgg(entityType, pos, snapToGround, extendDown); }

    /** LevelReader.getMaxLocalRawBrightness(pos) = getRawBrightness(pos, skyDarken). */
    default int getMaxLocalRawBrightness(Pos pos) { return getRawBrightness(pos, skyDarken()); }

    /** Level.getBrightness(LightLayer.SKY, pos): 하늘빛. TODO(빛): 빛 엔진 전까지 15(하늘이 보이는 곳과 같은 값). */
    default int getSkyLight(Pos pos) { return 15; }

    /** Level.getBrightness(LightLayer.BLOCK, pos): 블록빛. TODO(빛): 빛 엔진 전까지 0(주변에 광원이 없는 곳과 같은 값). */
    default int getBlockLight(Pos pos) { return 0; }

    /** level.getBiome(pos).is(#태그). */
    default boolean biomeIs(Pos pos, String tag) { return false; }

    /** Level.isRainingAt(pos): 비가 오고, 하늘이 보이고, 그 바이옴이 비가 오는 곳. 기본 구현은 거짓(ChunkLevel 이 덮어쓴다). */
    default boolean isRainingAt(Pos pos) { return false; }

    // ------------------------------------------------------------------ 지형 생성 기능(묘목·뼛가루)

    /** registry 의 configured feature(없으면 null). 레지스트리에 없는 것과 미구현(supported()=false)을 구분한다. */
    default LevelFeature configuredFeature(String id) { return null; }

    /** registry 의 placed feature(없으면 null). */
    default LevelFeature placedFeature(String id) { return null; }

    /** 그 바이옴의 첫 꽃 feature 의 내부 placed feature(getFlowerFeatures().get(0).config().feature()). 없으면 null. */
    default LevelFeature biomeFlowerFeature(String biome) { return null; }

    /**
     * ConfiguredFeature.place / PlacedFeature.place(level, generator, random, pos): 레벨 난수를 그대로 이어 쓰며 놓는다.
     * 미지원이면 false(아무것도 바꾸지 않음).
     */
    default boolean placeFeature(LevelFeature feature, Pos pos, RandomSource random) { return false; }

    /** Level.getBiome(pos) 의 바이옴 id(확대 지터 포함). 모르면 null. */
    default String biomeAt(int x, int y, int z) { return null; }

    RandomSource random();

    long gameTime();

    long dayTime();

    boolean isRaining();

    boolean isThundering();

    /** Level.getRainLevel(1.0F). */
    default float rainLevel() { return isRaining() ? 1f : 0f; }

    /** 천둥 세기(비 세기를 곱하기 전). */
    default float thunderLevel() { return isThundering() ? 1f : 0f; }

    /** 게임룰 값(불리언). */
    boolean gameRule(String name);

    int gameRuleInt(String name);

    /** 0 평화 .. 3 어려움. */
    int difficulty();

    /** 차원 id(minecraft:overworld …). */
    String dimension();

    boolean ultraWarm();

    /** dimensionType().hasSkyLight(). */
    default boolean hasSkyLight() { return true; }

    /** 월드 보더 안인가(isWithinBounds). */
    boolean withinBorder(Pos pos);

    /** Level.getHeight(WORLD_SURFACE, x, z): 가장 높은 블록 바로 위 y. 높이 지도가 없는 레벨은 상한. */
    default int worldSurfaceHeight(int x, int z) { return maxBuildHeight(); }

    /**
     * Block.pushEntitiesUp(from, to, level, pos): from → to 로 바뀌며 새로 생긴 충돌 부분에 걸친 엔티티를 그 위로 올린다. to 를 돌려준다.
     * 엔티티가 없는 레벨(월드 생성 등)은 아무것도 하지 않는다.
     */
    default int pushEntitiesUp(int from, int to, Pos pos) { return to; }

    /** 상자와 겹치는 플레이어들(Level.getEntitiesOfClass(Player.class, box) 의 플레이어 쪽). 플레이어가 없는 레벨은 아무도 없다. */
    default void forEachPlayerIn(org.domi.furnace.server.entity.AABB box, java.util.function.Consumer<org.domi.furnace.server.entity.Player> c) {}

    /** 블록 엔티티(없으면 null — 블록 엔티티 체계가 들어오기 전에는 상자만). */
    Object getBlockEntity(Pos pos);

    /** ContainerOpenersCounter.getEntitiesOfClass(Player, 칸 상자 ± radius): 그 용기를 연 창을 가진 플레이어 수. */
    default int countContainerOpeners(Pos pos, double radius, Object container) { return 0; }

    /** Level.blockEntityChanged: 그 칸의 청크를 저장 대상으로. */
    default void blockEntityChanged(Pos pos) {}

    /** Level.setBlockEntity: 그 칸 상태가 블록 엔티티를 가질 때만 둔다(틱 받는 종류면 틱 목록에). */
    default void setBlockEntity(org.domi.furnace.server.block.entity.BlockEntity be) {}

    /** ServerLevel.isHandlingTick: 예약 틱(블록·유체)을 돌리는 중인가. */
    default boolean isHandlingTick() { return false; }

    void removeBlockEntity(Pos pos);

    /** Level.sendBlockUpdated(pos, old, new, flags): 그 칸을 변경 방송 대상으로(상태가 같아도 블록 갱신·블록 엔티티 데이터를 다시 보낸다). */
    default void sendBlockUpdated(Pos pos) {}

    /** Level.getPlayerByUUID: 이 레벨의 접속 중인 플레이어(없으면 null). */
    default org.domi.furnace.server.entity.Player playerByUuid(java.util.UUID uuid) { return null; }

    /**
     * SignBlockEntity.executeClickCommandsIfPresent 의 명령 실행: 권한 2, 위치 = 칸 가운데, 이름 = 플레이어(없으면 "Sign").
     * 명령은 전역 스레드에서 돈다(바닐라는 그 자리에서 바로 실행). 서버가 없는 레벨은 아무것도 안 한다.
     */
    default void runSignCommand(BlockActor player, Pos pos, String command) {}

    /**
     * 그 칸을 소유한 region 스레드에서 task 를 돌린다(이미 그 스레드면 바로, 아니면 큐로). 주인 region 이 없으면(청크 내려감) 버린다.
     * task 에는 그때 그 칸을 다루는 레벨이 온다(region 이 합쳐지면 처음 레벨과 다를 수 있다). 기본 구현은 바로 실행(시험·단독 레벨).
     */
    default void runOnOwner(Pos pos, java.util.function.Consumer<BlockLevel> task) { task.accept(this); }

    /** server.properties enable-command-block. */
    default boolean commandBlockEnabled() { return false; }

    /** 명령 블록 명령 실행(LevelServices.runBlockCommand). done 은 어느 스레드에서든 불린다. */
    default void runBlockCommand(BlockCommandSink sink, String command, java.util.function.IntConsumer done) { done.accept(0); }

    /** 행위자에게 액션바 메시지(예: build.tooHigh). */
    void sendActionBar(BlockActor actor, org.domi.furnace.api.text.Text text);

    /** 블록에서 아이템 떨구기(Block.popResource) — 아이템 엔티티를 만든다. */
    void popResource(Pos pos, ItemStack stack);

    /** Containers.dropContents(level, pos, container): 칸마다 dropItemStack(빈 칸도 위치 난수 3번), 칸은 비운다. */
    default void dropContents(Pos pos, ItemStack[] items) {}

    /** 아이템 엔티티 하나를 그 자리에 만든다(기본 줍기 지연, 난수 소비 없음 — 위치는 부르는 쪽이 정한다). */
    default void spawnItemEntity(double x, double y, double z, ItemStack stack) {}

    /** Containers.dropItemStack(level, x, y, z, stack): 칸 안 임의 위치에서 쪼개 떨군다(위치·속도 난수는 레벨 난수). */
    default void dropItemStack(double x, double y, double z, ItemStack stack) {}

    /** 열지 않은 전리품 상자를 먼저 채운다(RandomizableContainer.getItem 의 unpackLootTable(null)). */
    default void unpackContainerLoot(Object container) {}

    /** 서버 레시피 매니저(org.domi.furnace.server.recipe.RecipeManager, 없으면 null). */
    default Object recipes() { return null; }

    /** Block.popResourceFromFace(level, pos, dir, stack). */
    void popResourceFromFace(Pos pos, int dir, ItemStack stack);

    // ------------------------------------------------------------------ 드롭(바닐라 Block.dropResources·getDrops·popExperience)

    /** 그 블록의 전리품 표(BLOCK 문맥) 결과 — origin 은 칸 가운데. explosionRadius 가 null 이면 폭발 아님. blockEntity 는 collectComponents(없으면 null). */
    default java.util.List<ItemStack> lootDrops(String lootTable, int state, Pos pos, BlockActor entity, ItemStack tool, Float explosionRadius,
                                                java.util.Map<String, Object> blockEntity) {
        return java.util.List.of();
    }

    /** Block.dropResources(state, level, pos, blockEntity, entity, tool): 드롭을 하나씩 popResource 한 뒤 spawnAfterBreak(…, true). */
    default void dropResources(int state, Pos pos, Object blockEntity, BlockActor entity, ItemStack tool) {
        BlockBehavior b = BlockBehaviors.of(state);
        for (ItemStack s : b.getDrops(state, this, pos, blockEntity, entity, tool)) popResource(pos, s);
        b.spawnAfterBreak(state, this, pos, tool, true);
    }

    /** Block.dropResources(state, level, pos). */
    default void dropResources(int state, Pos pos) { dropResources(state, pos, null, null, ItemStack.EMPTY); }

    /** Block.popExperience: doTileDrops 이면 경험치 구슬(ExperienceOrb.award). 구슬은 자기 난수만 쓴다. */
    default void popExperience(Pos pos, int amount) {}

    /** ExperienceOrb.award(level, 위치, amount). */
    default void awardExperience(double x, double y, double z, int amount) {}

    /** ServerLevel.destroyBlockProgress: 부수는 사람을 뺀 32칸 안 플레이어에게 금 가는 단계(0..9, 그 밖이면 지움). */
    default void destroyBlockProgress(int breakerId, Pos pos, int progress) {}
}
