// 플레이어 엔티티 상태(region 스레드 소유). 채널로 패킷 전송(eventLoop 마샬).
package org.domi.furnace.server.entity;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.domi.furnace.server.config.Config;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.world.ChunkPos;

public final class Player implements Targetable, Rider, org.domi.furnace.server.map.MapData.Viewer {
    private Targetable lastHurtByMob, lastHurtMob;
    private int combatTicks, lastHurtByMobTimestamp, lastHurtMobTimestamp;

    /** 바닐라 lastHurtByPlayer·lastHurtByPlayerTime(플레이어가 때리면 100틱 동안 남는다). */
    private Player lastHurtByPlayer;
    private int lastHurtByPlayerTime;

    /** 틱마다 1 올라가는 전투 시계(LivingEntity.tickCount 에 해당). LivingEntity.baseTick 처럼 오래된 피격 기록을 지운다. */
    /** 전투 시계 값(바닐라 LivingEntity.tickCount 자리). */
    public int combatTicks() { return combatTicks; }

    void combatTick() {
        combatTicks++;
        if (lastHurtByPlayerTime > 0) lastHurtByPlayerTime--;
        else lastHurtByPlayer = null;
        if (lastHurtByMob != null) {
            if (!lastHurtByMob.isAlive()) setLastHurtByMob(null);
            else if (combatTicks - lastHurtByMobTimestamp > 100) setLastHurtByMob(null);
        }
    }

    /** LivingEntity.hurt: 원인 엔티티가 플레이어면 lastHurtByPlayerTime = 100. */
    void markHurtByPlayer(Player p) {
        lastHurtByPlayer = p;
        lastHurtByPlayerTime = 100;
    }

    /** 바닐라 getKillCredit: lastHurtByPlayer, 없으면 lastHurtByMob. */
    public Targetable getKillCredit() { return lastHurtByPlayer != null ? lastHurtByPlayer : lastHurtByMob; }

    /** 리스폰: 바닐라는 새 엔티티라 피격 기록이 없다. */
    void clearHurtRecords() {
        lastHurtByMob = null;
        lastHurtByPlayer = null;
        lastHurtByPlayerTime = 0;
    }

    @Override public Targetable getLastHurtByMob() { return lastHurtByMob; }

    @Override public int getLastHurtByMobTimestamp() { return lastHurtByMobTimestamp; }

    public void setLastHurtByMob(Targetable t) {
        lastHurtByMob = t;
        lastHurtByMobTimestamp = combatTicks;
    }

    @Override public Targetable getLastHurtMob() { return lastHurtMob; }

    @Override public int getLastHurtMobTimestamp() { return lastHurtMobTimestamp; }

    public void setLastHurtMob(Targetable t) {
        lastHurtMob = t instanceof EnderDragonPart ? null : t;   // 바닐라: LivingEntity 가 아니면 null
        lastHurtMobTimestamp = combatTicks;
    }


    private final int entityId;
    private final UUID uuid;
    private final String name;
    private final Channel channel;

    private volatile double x, y, z; // volatile — region 외 스레드(청크 전송 완료 콜백)서 chunkPos 읽음
    private float yaw, pitch;
    private boolean onGround;

    private ChunkPos lastCenter;                 // 마지막으로 SetCenterChunk 보낸 청크(null=미전송)
    private final Set<ChunkPos> loadedChunks = new HashSet<>();
    /** Entity.random — 엔티티 자신의 난수(RandomSource.create()). */
    private final org.domi.furnace.server.block.LegacyRandomSource random =
            new org.domi.furnace.server.block.LegacyRandomSource(java.util.concurrent.ThreadLocalRandom.current().nextLong());
    private final Set<ChunkPos> pendingChunks = new LinkedHashSet<>(); // 시야 진입했으나 아직 전송 안 한 청크(순서 보존). region 스레드 전용.
    private final Map<Integer, double[]> tracked = new HashMap<>(); // 관찰중 엔티티 → 마지막 전송 {x,y,z}
    private final Map<Integer, Integer> missingTicks = new HashMap<>(); // 추적중인데 후보서 사라진 틱 수(grace despawn)
    // 이 플레이어의 탭 목록에 올려둔 엔티티 → uuid. 시야 밖으로 나가 엔티티가 디스폰돼도 접속 중이면 탭엔 남기고,
    // 후보에서 완전히 사라졌을 때(퇴장 등)만 PlayerInfoRemove로 뺀다. region 스레드 전용.
    private final Map<Integer, UUID> listed = new HashMap<>();

    private volatile double[] pending;            // {x,y,z,yaw,pitch,onGround} IO→region 발행
    private volatile boolean disconnected;
    // 유효 view distance = min(서버 view-distance, 클라 요청). ClientInformation(config 0x00/play 0x0A)서 갱신.
    // 게임중 클라가 렌더거리 바꾸면 0x0A로 다시 옴 → 갱신. onEnterPlay서 초기 설정.
    private volatile int viewDistance = 10;
    // 열어둔 체스트 세션(M4c-3 슬라이스 B) — netty 스레드가 쓰고 region 스레드(breakBlock 강제닫기)가 읽음.
    // 메뉴: 인벤토리 창(containerId 0)은 항상 있고, 열린 창(상자 등)이 있으면 containerMenu 가 그것이다(바닐라 containerMenu/inventoryMenu).
    private volatile org.domi.furnace.server.menu.InventoryMenu inventoryMenu;
    private volatile org.domi.furnace.server.menu.AbstractContainerMenu containerMenu;
    private int containerCounter;

    /** 블록 부수기 상태(ServerPlayerGameMode) — 플레이어가 속한 region 스레드에서만. */
    private final org.domi.furnace.server.block.BlockBreaking breaking = new org.domi.furnace.server.block.BlockBreaking();

    /** 인벤토리를 가진 쪽(연결)이 내주는 통로. 인벤토리는 연결 스레드 것이라 region 은 사본을 읽고, 바꿀 때는 이 통로로 맡긴다. */
    public interface InventoryHost {
        /** 창 칸(0 결과, 5..8 머리·가슴·다리·발, 9..35 주, 36..44 핫바, 45 왼손)의 지금 스택. */
        ItemStack slot(int windowSlot);

        /** 손에 든 핫바 칸(0..8). */
        int selected();

        /** 그 칸이 아직 expect 이면 now 로 바꾼다(연결 스레드에서). */
        void replace(int windowSlot, ItemStack expect, ItemStack now);

        /** 권한 단계(관리자 블록 판정). */
        int permissionLevel();

        /** 파괴 직전 허가(플러그인 BlockBreakEvent). */
        default boolean allowBreak(int x, int y, int z, int state) { return true; }

        /** Level.mayInteract 의 스폰 보호 부분(블록 x, z). */
        default boolean mayInteract(int x, int z) { return true; }

        /** 그 창 칸이 바뀌었다(inventory_changed 발전 과제 — broadcastChanges 가 부른다). */
        default void inventoryChanged(int windowSlot) {}

        /** 열린 창이 expect 이면 서버가 닫는다(ServerPlayer.closeContainer — 상자가 사라졌을 때 등). 어느 스레드에서든. */
        default void closeContainer(org.domi.furnace.server.menu.AbstractContainerMenu expect) {}

        /** 임의 스레드(플러그인 텔레포트)에서 부르는 창 닫기 — 블록에 매인 창은 그 블록의 region 스레드로 넘겨 닫는다. 기본은 closeContainer. */
        default void closeContainerFromAnyThread(org.domi.furnace.server.menu.AbstractContainerMenu expect) { closeContainer(expect); }

        /** 블록에 매이지 않은 열린 창이 아직 유효한가(AbstractContainerMenu.stillValid). */
        default boolean menuStillValid(org.domi.furnace.server.menu.AbstractContainerMenu menu) { return true; }

        /**
         * 블록에 매인 열린 창(상자·제작대)의 유효성 검사 — 그 블록을 가진 region 스레드에서 불린다. 유효하지 않으면(블록이 사라졌거나 멀어졌거나
         * 청크가 내려갔으면) 서버가 닫는다.
         */
        default void validateBlockMenu(org.domi.furnace.server.world.Region region, org.domi.furnace.server.menu.AbstractContainerMenu menu) {}

        /** ServerPlayer.openHorseInventory(horse, container): 말이 있는 region 스레드에서 부른다. */
        default void openHorseInventory(AbstractHorse horse, org.domi.furnace.server.menu.Container container) {}

        /** 플러그인 Menu 를 연다(연결 스레드에서 이뤄진다). 어느 스레드에서든. */
        default void openPluginMenu(org.domi.furnace.server.plugin.MenuSession session) {}

        /** 열린 플러그인 Menu 의 제목을 바꾼다(같은 창 번호로 화면을 다시 열고 내용을 다시 보낸다). 연결 스레드에서 불러야 한다. */
        default void retitlePluginMenu(org.domi.furnace.server.plugin.MenuSession session, org.domi.furnace.api.text.Text title) {}

        /** 이 플레이어의 연결 스레드에서 실행한다. 어느 스레드에서든. */
        default void execute(Runnable task) { task.run(); }

        /** ServerPlayer.openMenu(엔티티 용기 — 상자 배·상자/깔때기 수레): 그 엔티티가 있는 region 스레드에서 부른다. */
        default void openEntityContainer(ContainerEntity entity) {}
    }

    private volatile InventoryHost inventoryHost;

    public org.domi.furnace.server.block.BlockBreaking breaking() { return breaking; }

    public org.domi.furnace.server.block.LegacyRandomSource random() { return random; }

    /** 플레이어 인벤토리(연결이 만든 것을 넘겨받는다 — 테스트용 기본값도 있다). */
    private volatile PlayerInventory inventory = new PlayerInventory();

    public PlayerInventory inventory() { return inventory; }

    private org.domi.furnace.server.attribute.AttributeMap attributes;

    /**
     * 바닐라 Player.createAttributes 의 속성 목록. 장비·효과·달리기 수정자는 syncAttributes 가 매 틱 맞추고, 바뀐 속성만
     * update_attributes 로 본인과 보는 사람에게 간다. 최대 체력·산소 보너스는 PlayerVitals 로 넘겨 쓴다(syncAttributes).
     * 공격력·채굴 속도 같은 계산은 PlayerAttributes.value 가 같은 수정자 함수(modifiers)로 직접 합성한다.
     */
    public synchronized org.domi.furnace.server.attribute.AttributeMap attributes() {
        if (attributes == null) {
            var m = new org.domi.furnace.server.attribute.AttributeMap();
            m.add(org.domi.furnace.server.attribute.Attribute.MAX_HEALTH, 20.0).add(org.domi.furnace.server.attribute.Attribute.KNOCKBACK_RESISTANCE, 0.0)
                    .add(org.domi.furnace.server.attribute.Attribute.ATTACK_KNOCKBACK, 0.0).add(org.domi.furnace.server.attribute.Attribute.ARMOR, 0.0)
                    .add(org.domi.furnace.server.attribute.Attribute.ARMOR_TOUGHNESS, 0.0).add(org.domi.furnace.server.attribute.Attribute.MAX_ABSORPTION, 0.0)
                    .add(org.domi.furnace.server.attribute.Attribute.STEP_HEIGHT, 0.6).add(org.domi.furnace.server.attribute.Attribute.SCALE, 1.0)
                    .add(org.domi.furnace.server.attribute.Attribute.GRAVITY, 0.08).add(org.domi.furnace.server.attribute.Attribute.SAFE_FALL_DISTANCE, 3.0)
                    .add(org.domi.furnace.server.attribute.Attribute.FALL_DAMAGE_MULTIPLIER, 1.0).add(org.domi.furnace.server.attribute.Attribute.JUMP_STRENGTH, 0.42)
                    .add(org.domi.furnace.server.attribute.Attribute.OXYGEN_BONUS, 0.0).add(org.domi.furnace.server.attribute.Attribute.BURNING_TIME, 1.0)
                    .add(org.domi.furnace.server.attribute.Attribute.EXPLOSION_KNOCKBACK_RESISTANCE, 0.0)
                    .add(org.domi.furnace.server.attribute.Attribute.WATER_MOVEMENT_EFFICIENCY, 0.0).add(org.domi.furnace.server.attribute.Attribute.MOVEMENT_EFFICIENCY, 0.0)
                    .add(org.domi.furnace.server.attribute.Attribute.ATTACK_DAMAGE, 1.0).add(org.domi.furnace.server.attribute.Attribute.MOVEMENT_SPEED, PlayerAttributes.base(org.domi.furnace.server.attribute.Attribute.MOVEMENT_SPEED))
                    .add(org.domi.furnace.server.attribute.Attribute.ATTACK_SPEED, 4.0).add(org.domi.furnace.server.attribute.Attribute.LUCK, 0.0)
                    .add(org.domi.furnace.server.attribute.Attribute.BLOCK_INTERACTION_RANGE, 4.5).add(org.domi.furnace.server.attribute.Attribute.ENTITY_INTERACTION_RANGE, 3.0)
                    .add(org.domi.furnace.server.attribute.Attribute.BLOCK_BREAK_SPEED, 1.0).add(org.domi.furnace.server.attribute.Attribute.SUBMERGED_MINING_SPEED, 0.2)
                    .add(org.domi.furnace.server.attribute.Attribute.SNEAKING_SPEED, 0.3).add(org.domi.furnace.server.attribute.Attribute.MINING_EFFICIENCY, 0.0)
                    .add(org.domi.furnace.server.attribute.Attribute.SWEEPING_DAMAGE_RATIO, 0.0);
            attributes = m;
        }
        return attributes;
    }

    public void inventory(PlayerInventory inv) { this.inventory = inv; }

    public InventoryHost inventoryHost() { return inventoryHost; }

    public void inventoryHost(InventoryHost h) { this.inventoryHost = h; }

    public Player(int entityId, UUID uuid, String name, Channel channel) {
        this.entityId = entityId;
        this.uuid = uuid;
        this.name = name;
        this.channel = channel;
    }

    /** ServerPlayer.wardenSpawnTracker(저장 키 warden_spawn_tracker — PlayerVitals 가 읽고 쓴다). */
    public WardenSpawnTracker wardenSpawnTracker() { return vitals.wardenSpawnTracker(); }

    /** 이동 방출(걸음·헤엄 게임 이벤트) 상태 — 소유 region 전용. */
    public final PlayerMovementEmission movementEmission = new PlayerMovementEmission();

    public void applyMove(double x, double y, double z, float yaw, float pitch, boolean onGround) {
        this.moveDx = x - this.x; this.moveDy = y - this.y; this.moveDz = z - this.z;
        this.x = x; this.y = y; this.z = z;
        this.yaw = yaw; this.pitch = pitch; this.onGround = onGround;
    }

    public void applyRotation(float yaw, float pitch, boolean onGround) {
        this.yaw = yaw; this.pitch = pitch; this.onGround = onGround;
    }

    public ChunkPos chunkPos() {
        return ChunkPos.ofBlock(x, z);
    }

    /** IO 스레드: 들어온 위치를 발행(단일 writer). */
    public void publishPosition(double x, double y, double z, float yaw, float pitch, boolean onGround) {
        this.pending = new double[]{x, y, z, yaw, pitch, onGround ? 1 : 0};
    }

    /** region 스레드: 발행된 위치를 권위 필드에 적용(단일 reader). 변경 있으면 true. 탄 동안은 회전만(위치는 탈것이 정한다 — 바닐라 handleMovePlayer). */
    public boolean drainPending() {
        double[] p = this.pending;
        if (p == null) return false;
        this.pending = null;
        if (sleepingPos != null) return false;   // 자는 동안은 클라이언트 위치·회전을 받지 않는다(handleMovePlayer 의 isSleeping 가지)
        if (vehicle != null) applyRotation((float) p[3], (float) p[4], p[5] != 0);
        else applyMove(p[0], p[1], p[2], (float) p[3], (float) p[4], p[5] != 0);
        return true;
    }

    // ------------------------------------------------------------------ 탑승(승객으로서의 플레이어)
    // 탈것은 region 스레드가 바꾸고 IO 스레드가 읽는다(위치 패킷을 회전만으로 다룰지). 입력(PlayerInput)은 IO 가 쓰고 region 이 읽는다.

    private volatile Entity vehicle;
    private int boardingCooldown;
    /** ServerPlayer.setPlayerInput 이 정하는 xxa·zza·jumping(탄 동안만 바뀐다). */
    private volatile float inputXxa, inputZza;
    private volatile boolean inputJumping;

    @Override public Entity vehicle() { return vehicle; }

    @Override
    public void setVehicleField(Entity v) {
        vehicle = v;
        if (v != null) ridingGeneration = spawnGeneration;
    }

    private int ridingGeneration;

    /** 플레이어 데이터 파일에 RootVehicle 이 남아 있다고 아는가(주인 region 스레드 전용, PlayerVehicleSave). */
    public boolean rootVehicleOnDisk;

    /** 연결이 단 저장 요청 입구(save-all·백업): 이벤트 루프에서 스냅샷을 떠 플레이어 데이터 실행기에 넣은 뒤 완료. 연결 전·시험은 null. */
    private volatile java.util.function.Supplier<java.util.concurrent.CompletableFuture<Void>> saveRequest;

    public void saveRequest(java.util.function.Supplier<java.util.concurrent.CompletableFuture<Void>> r) { this.saveRequest = r; }

    /**
     * 지금 상태를 저장하라고 요청한다(바닐라 PlayerList.saveAll 의 한 사람 몫). 반환 future 는 저장이 플레이어 데이터 실행기(world.playerIo)에
     * 들어간 뒤 완료 — 실제 기록은 그 실행기 순서대로 뒤따른다. 끊겼거나 입구가 없으면 바로 완료.
     */
    public java.util.concurrent.CompletableFuture<Void> requestSave() {
        var r = saveRequest;
        if (r == null || isDisconnected()) return java.util.concurrent.CompletableFuture.completedFuture(null);
        try {
            return r.get();
        } catch (RuntimeException e) {
            return java.util.concurrent.CompletableFuture.failedFuture(e);
        }
    }
    /** 퇴장 때 탈것 처리를 마쳤다(두 번 하지 않는다). */
    boolean quitMountHandled;

    /** 나가거나 다시 태어나 옛 플레이어가 사라진 경우(바닐라는 옛 ServerPlayer 를 지운다): 내릴 자리 없이 연결만 끊는다. */
    public void detachFromVehicle() { Entity.unlinkVehicle(this); }

    @Override public void setBoardingCooldown(int ticks) { boardingCooldown = ticks; }

    @Override public boolean isShiftKeyDown() { return sneaking; }

    @Override public float riderYRot() { return yaw; }

    @Override public float riderXRot() { return pitch; }

    @Override public String dimensionKey() { return dimension; }

    @Override public String typeName() { return "minecraft:player"; }

    @Override public String selectorName() { return name; }

    /** 바닐라 Entity.tags — 명령 스레드가 바꾸고 IO 스레드가 저장하므로 동기화한다. */
    private final java.util.Set<String> commandTags = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    @Override public java.util.Set<String> getTags() { synchronized (commandTags) { return java.util.Collections.unmodifiableSet(new java.util.HashSet<>(commandTags)); } }

    @Override public boolean addTag(String tag) { synchronized (commandTags) { return commandTags.size() < 1024 && commandTags.add(tag); } }

    @Override public boolean removeTag(String tag) { return commandTags.remove(tag); }

    /** 플레이어 NBT 의 "Tags"(문자열 목록)를 쓴다. 비었으면 쓰지 않는다. */
    public void saveTags(org.domi.furnace.server.net.io.nbt.NbtCompound out) {
        synchronized (commandTags) {
            if (commandTags.isEmpty()) return;
            var list = new org.domi.furnace.server.net.io.nbt.NbtList(org.domi.furnace.server.net.io.nbt.NbtIo.TAG_STRING);
            for (String t : commandTags) list.add(t);
            out.put("Tags", list);
        }
    }

    /** 플레이어 NBT 의 "Tags" 를 읽는다(최대 1024). */
    public void loadTags(org.domi.furnace.server.net.io.nbt.NbtCompound in) {
        synchronized (commandTags) {
            commandTags.clear();
            if (in.tags().get("Tags") instanceof org.domi.furnace.server.net.io.nbt.NbtList list) {
                int n = Math.min(list.size(), 1024);
                for (int i = 0; i < n; i++) commandTags.add(list.get(i) instanceof String t ? t : "");
            }
        }
    }

    /** canRide: 웅크리지 않았고 다시 타기 기다림이 끝났다(Entity.canRide). */
    @Override public boolean canRide(Entity v) { return !sneaking && boardingCooldown <= 0; }

    /** Player 의 탈것 부착점(0, 0.6, 0). */
    @Override public Vec3 vehicleAttachmentPoint(Entity v) { return EntityAttachments.rotate(EntityAttachments.PLAYER_VEHICLE, yaw); }

    /** positionRider 가 플레이어를 옮긴다(region 스레드). 클라이언트는 탈것에 맞춰 스스로 그리므로 보내지 않는다. */
    @Override
    public void setRidingPos(double nx, double ny, double nz) {
        this.moveDx = nx - this.x; this.moveDy = ny - this.y; this.moveDz = nz - this.z;
        this.x = nx; this.y = ny; this.z = nz;
    }

    public boolean startRiding(Entity v, boolean force) { return Entity.mount(this, v, force); }

    private boolean dismountForced;

    @Override public boolean dismountForced() { return dismountForced; }

    @Override public void setDismountForced(boolean forced) { dismountForced = forced; }

    /** ServerPlayer.stopRiding → LivingEntity.dismountVehicle → ServerPlayer.dismountTo(연결이 순간이동시킨다). */
    @Override
    public void stopRiding() {
        Entity v = vehicle;
        Entity.unlinkVehicle(this);
        if (v == null || v == vehicle) return;
        double nx, ny, nz;
        if (!v.isRemoved() && !org.domi.furnace.server.block.B.isTag(v.level().getBlockState(v.blockPosition()), "minecraft:portals")) {
            Vec3 p = v.getDismountLocationForPassenger(this);
            nx = p.x(); ny = p.y(); nz = p.z();
        } else {
            nx = x; ny = Math.max(y, v.y()); nz = z;
        }
        pending = null;
        setRidingPos(nx, ny, nz);
        teleportTo(nx, ny, nz);
    }

    /**
     * 탄 탈것이 내려앉아 승객에게 넘어온 낙하(Entity.causeFallDamage → Player.causeFallDamage).
     * 피해가 나면 작은/큰 낙하 소리(피해 4 초과면 큰 소리)를 본인 말고 주변에 낸다.
     */
    public void causeFallDamageAsPassenger(float distance, float multiplier, Entity.Damage source) {
        runOnOwner(() -> {
            float dmg = vitals.causePassengerFallDamage(distance, multiplier, source.type());
            var lvl = currentLevel;
            if ((int) dmg > 0 && lvl != null) Shields.playSoundExceptSelf(this, lvl, (int) dmg > 4 ? "minecraft:entity.player.big_fall" : "minecraft:entity.player.small_fall", 1.0F, 1.0F);
        });
    }

    // ------------------------------------------------------------------ 불(Entity.remainingFireTicks) — 쓰기는 소유 region 스레드

    /** Player.getFireImmuneTicks: 불에서 나온 뒤 20틱 안에 다시 들어서면 바로 붙지 않고, 불 속에 20틱 서 있으면 붙는다. */
    public static final int FIRE_IMMUNE_TICKS = 20;

    private volatile int remainingFireTicks = -FIRE_IMMUNE_TICKS;

    /** Entity.isInPowderSnow 대응: 이번 move 의 checkInsideBlocks 에서 가루눈에 들었다(afterMove 가 읽고 지운다). */
    private volatile boolean inPowderSnowThisMove;

    public void markInPowderSnow() {
        inPowderSnowThisMove = true;
        inPowderSnowThisTick = true;
    }

    /**
     * 냉각(aiStep freezing)이 보는 Entity.isInPowderSnow: baseTick 이 거짓으로 시작하고 이번 틱의 서버 move(checkInsideBlocks)가 켠다.
     * region 스레드 전용 — 플레이어 틱 머리에서 {@link #resetInPowderSnowTick} 로 지운다.
     */
    private boolean inPowderSnowThisTick;

    public boolean inPowderSnowThisTick() { return inPowderSnowThisTick; }

    public void resetInPowderSnowTick() { inPowderSnowThisTick = false; }

    /** 읽고 지운다. */
    public boolean takeInPowderSnow() { boolean v = inPowderSnowThisMove; inPowderSnowThisMove = false; return v; }

    /** isOnFire: 플레이어는 불에 면역인 종류가 아니다. */
    public boolean isOnFire() { return remainingFireTicks > 0; }

    public int remainingFireTicks() { return remainingFireTicks; }

    public void setRemainingFireTicks(int ticks) { remainingFireTicks = ticks; }

    /** Entity.clearFire: 0 으로(면역 틱으로 되돌리지 않는다). */
    public void clearFire() { remainingFireTicks = 0; }

    /** LivingEntity.igniteForTicks: 태우는 시간 속성(불 보호 인챈트가 줄인다)을 곱해 올림하고, 지금보다 길 때만 늘린다. */
    public void igniteForTicks(int ticks) {
        int t = (int) Math.ceil(ticks * PlayerAttributes.value(this, org.domi.furnace.server.attribute.Attribute.BURNING_TIME));
        if (remainingFireTicks < t) remainingFireTicks = t;
    }

    public void igniteForSeconds(float seconds) { igniteForTicks(Entity.floor(seconds * 20.0F)); }

    /** 다른 region 스레드(화살·불덩이·좀비)가 붙일 때: 주인 스레드에서 한다. */
    public void igniteForSecondsLater(float seconds) { runOnOwner(() -> igniteForSeconds(seconds)); }

    /** 저장 꼴의 Fire(short). */
    public void saveFire(org.domi.furnace.server.net.io.nbt.NbtCompound out) { out.putShort("Fire", (short) remainingFireTicks); }

    /** 접속 때 Fire 를 읽는다(없으면 면역 틱 그대로). */
    public void loadFire(org.domi.furnace.server.net.io.nbt.NbtCompound in) {
        if (in.tags().get("Fire") instanceof Short f) remainingFireTicks = f;
    }

    /** 바닐라 연결의 lastVehicle·vehicleFirstGood·vehicleLastGood(region 스레드 전용, VehicleMoves 가 쓴다). */
    Entity lastVehicle;
    final double[] vehicleFirstGood = new double[3], vehicleLastGood = new double[3];
    boolean clientVehicleIsFloating;
    /** aboveGroundVehicleTickCount: 조종하는 탈것이 계속 떠 있는 틱 수(너무 길면 "multiplayer.disconnect.flying" 킥). */
    int aboveGroundVehicleTickCount;

    /**
     * region 틱마다: 바닐라 baseTick 의 탑승 부분(탈것이 지워졌으면 내림·다시 타기 기다림) 뒤
     * 연결 틱의 탈것 기준점(조종 중인 뿌리 탈것의 지금 위치를 첫·마지막 정상 위치로).
     */
    public void tickRiding() {
        if (vehicle != null && ridingGeneration != spawnGeneration) detachFromVehicle();
        if (vehicle != null && vehicle.isRemoved()) Entity.stopRidingForced(this);
        if (boardingCooldown > 0) boardingCooldown--;
        Entity root = Entity.rootVehicleOf(this);
        if (root != null && root.getControllingPassenger() == this) {
            lastVehicle = root;
            vehicleFirstGood[0] = vehicleLastGood[0] = root.x();
            vehicleFirstGood[1] = vehicleLastGood[1] = root.y();
            vehicleFirstGood[2] = vehicleLastGood[2] = root.z();
            if (clientVehicleIsFloating) {
                if (++aboveGroundVehicleTickCount > PlayerMoveValidator.maximumFlyingTicks(VehicleMoves.gravityOf(root))) {
                    org.slf4j.LoggerFactory.getLogger(Player.class).warn("{} was kicked for floating a vehicle too long!", name());
                    kick(new org.domi.furnace.server.net.protocol.play.Disconnect(org.domi.furnace.api.text.Text.translatable("multiplayer.disconnect.flying")));
                }
            } else {
                clientVehicleIsFloating = false;
                aboveGroundVehicleTickCount = 0;
            }
        } else {
            lastVehicle = null;
            clientVehicleIsFloating = false;
            aboveGroundVehicleTickCount = 0;
        }
    }

    /** Player.rideTick(탈것 틱 뒤): 웅크리면(wantsToStopRiding) 내리고 웅크림을 푼다. 아니면 낙하 거리를 지우고 탈것이 자리를 잡아 준다. */
    public void rideTick() {
        if (sneaking && vehicle != null) {
            stopRiding();
            sneaking = false;
            return;
        }
        vitals.resetFallDistance();
        if (vehicle != null) vehicle.positionRider(this);
    }

    /** ServerPlayer.setPlayerInput: 탄 동안만 ±1 안의 값을 받고 점프·웅크림을 정한다(IO 스레드). */
    public void setPlayerInput(float xxa, float zza, boolean jumping, boolean shift) {
        if (vehicle == null) return;
        if (xxa >= -1.0F && xxa <= 1.0F) inputXxa = xxa;
        if (zza >= -1.0F && zza <= 1.0F) inputZza = zza;
        inputJumping = jumping;
        sneaking = shift;
    }

    public float inputXxa() { return inputXxa; }

    public float inputZza() { return inputZza; }

    public boolean inputJumping() { return inputJumping; }

    public void markDisconnected() {
        this.disconnected = true;
        bumpRoster();
        org.domi.furnace.server.entity.view.EntityViewRules.playerGone(this);   // 이 접속으로 건 보이기·변장 규칙
        apiTasks.retire();   // 플러그인 EntityScheduler: 접속 종료 = 엔티티가 사라짐
    }

    // ------------------------------------------------------------------ 플러그인(EntityScheduler·영속 데이터)

    private final EntityTasks apiTasks = new EntityTasks(this);
    /** 플러그인 영속 데이터(BukkitValues). 저장 스냅샷은 연결 스레드가 뜨므로 이 객체의 모니터로 보호한다. */
    private final org.domi.furnace.server.net.io.nbt.NbtCompound persistentData = new org.domi.furnace.server.net.io.nbt.NbtCompound();

    public EntityTasks apiTasks() { return apiTasks; }

    /** 영속 데이터를 잠근 채 다룬다(f 안에서만 그 compound 를 쓴다). */
    public <T> T withPersistentData(java.util.function.Function<org.domi.furnace.server.net.io.nbt.NbtCompound, T> f) {
        synchronized (persistentData) { return f.apply(persistentData); }
    }

    /** 플레이어 파일의 BukkitValues 를 읽는다(접속 때). */
    public void loadPersistentData(org.domi.furnace.server.net.io.nbt.NbtCompound root) {
        synchronized (persistentData) {
            persistentData.tags().clear();
            if (root.tags().get("BukkitValues") instanceof org.domi.furnace.server.net.io.nbt.NbtCompound c) persistentData.tags().putAll(c.copy().tags());
        }
    }

    /** 저장 꼴에 BukkitValues 를 쓴다(비었으면 쓰지 않는다 — 저장 쪽이 옛 값을 지운다). */
    public void savePersistentData(org.domi.furnace.server.net.io.nbt.NbtCompound out) {
        synchronized (persistentData) {
            if (!persistentData.tags().isEmpty()) out.putCompound("BukkitValues", persistentData.copy());
        }
    }

    // 접속자 집합(입장·퇴장·퇴장 표시)이 바뀔 때마다 오르는 전역 번호. 탭 목록 동기화가 바뀐 틱에만 전원을 훑게 한다(E-11).
    private static final java.util.concurrent.atomic.AtomicLong ROSTER_EPOCH = new java.util.concurrent.atomic.AtomicLong();

    /** 상태를 바꾼 뒤에 부른다(읽는 쪽은 번호를 먼저 읽고 훑으므로 그 사이 변경은 다음 틱에 다시 훑는다). */
    public static void bumpRoster() { ROSTER_EPOCH.incrementAndGet(); }

    public static long rosterEpoch() { return ROSTER_EPOCH.get(); }

    /** 이 플레이어의 탭 목록을 마지막으로 맞춘 번호(-1 = 다시 훑어야 함). 소유 region 스레드 전용. */
    public long tabEpoch = -1;
    public boolean isDisconnected() { return disconnected; }
    public int viewDistance() { return viewDistance; }
    public void viewDistance(int vd) { this.viewDistance = Math.max(Config.MIN_VIEW_DISTANCE, vd); }

    private volatile int clientViewDistance = Config.MAX_VIEW_DISTANCE;

    /** 클라이언트가 client_information 으로 알린 렌더 거리(서버 시야 거리가 바뀔 때 다시 min 을 잡는 데 쓴다). */
    public int clientViewDistance() { return clientViewDistance; }

    public void clientViewDistance(int vd) { this.clientViewDistance = vd; }

    /** 이 플레이어의 네트워크 채널. */
    public Channel channel() { return channel; }

    /** 패킷을 이 플레이어 채널로 전송(IO eventLoop 마샬). region 스레드서 호출 안전. */
    public void send(Packet packet) { send(packet, () -> true); }

    /** 이벤트 루프에서 쓰는 순간 current 가 거짓이면 버린다(리스폰 전에 모은 낡은 상태 패킷이 Respawn 뒤에 가지 않게). */
    public void send(Packet packet, java.util.function.BooleanSupplier current) {
        runOnLoop(() -> {
            if (!current.getAsBoolean()) return;
            Object view = viewRewriter.rewrite(this, packet);   // 플레이어별 변장(규칙이 없으면 그대로)
            if (view == packet) {
                if (!writeOne(packet)) return;
            } else if (view instanceof Packet[] many) {
                boolean any = false;
                for (Packet one : many) any |= writeOne(one);
                if (!any) return;
            } else if (view instanceof Packet one) {
                if (!writeOne(one)) return;
            } else {
                return;   // 변장 때문에 보내지 않는다(살아 있지 않은 모습에 보내는 속성 등)
            }
            // 보낼 때마다 flush(시스템 호출) 하지 않고, 같은 순간에 쌓인 패킷은 한 번에 내보낸다(플래그는 이벤트 루프 스레드에서만 만진다).
            if (!flushPending) {
                flushPending = true;
                runOnLoop(() -> {
                    flushPending = false;
                    channel.flush();
                });
            }
        });
    }

    private boolean flushPending;

    /**
     * 연결 이벤트 루프에서 돌린다. 루프가 이미 끝났으면(서버 종료 — 네트워크 정지가 루프 종료까지 기다린 뒤) 버린다: 끊긴 연결이라 보낼 곳이
     * 없고, 던지면 종료 직전 틱(끊긴 플레이어 정리 → 다른 플레이어에게 보내기)이 중간에 끊긴다.
     */
    private void runOnLoop(Runnable task) {
        try {
            channel.eventLoop().execute(task);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // 닫힌 루프 — 패킷을 버린다
        }
    }

    /** 이 연결의 엔티티 모습 바꾸기(변장) 상태 — 이벤트 루프에서만. */
    private final org.domi.furnace.server.entity.view.ViewRewriter viewRewriter = new org.domi.furnace.server.entity.view.ViewRewriter();

    /** 시험용. */
    public org.domi.furnace.server.entity.view.ViewRewriter viewRewriter() { return viewRewriter; }

    /** 이 접속의 화면 덮어쓰기(가상 블록·바이옴 층, 장비 덮어쓰기, 개인 월드 보더) — 아무 스레드. */
    private final org.domi.furnace.server.entity.view.ViewLayers viewLayers = new org.domi.furnace.server.entity.view.ViewLayers();

    public org.domi.furnace.server.entity.view.ViewLayers viewLayers() { return viewLayers; }

    /** 이벤트 루프에서: 인코딩해 쓴다. 플러그인 패킷 리스너가 취소했으면 false. */
    private boolean writeOne(Packet packet) {
        Object msg = org.domi.furnace.server.net.io.SharedChunkFrames.encode(channel, packet);
        if (msg == null) return false;
        channel.write(msg);
        return true;
    }

    private volatile org.domi.furnace.api.text.Text kickReason;

    /** 서버가 끊은 사유(퇴장 로그용), 없으면 null. */
    public org.domi.furnace.api.text.Text kickReason() { return kickReason; }

    /** 사유 패킷을 보낸 뒤 연결을 닫는다(중복 로그인 킥 등). 어느 스레드에서든 안전. */
    public void kick(Packet packet) {
        if (packet instanceof org.domi.furnace.server.net.protocol.play.Disconnect d && !isDisconnected()) {   // 플러그인 PlayerKickEvent: 사유 바꾸기·취소
            org.domi.furnace.api.text.Text reason = vitals().env().gameEvents().playerKick(this, d.reason());
            if (reason == null) return;
            if (reason != d.reason()) packet = new org.domi.furnace.server.net.protocol.play.Disconnect(reason);
        }
        kickForced(packet);
    }

    /** 취소할 수 없는 내보내기(같은 계정 중복 접속) — 플러그인 이벤트 없이. */
    public void kickForced(Packet packet) {
        if (packet instanceof org.domi.furnace.server.net.protocol.play.Disconnect d) {
            kickReason = d.reason();
            org.domi.furnace.api.text.Text r = localize(d.reason());
            if (r != d.reason()) packet = new org.domi.furnace.server.net.protocol.play.Disconnect(r);
        }
        final Packet out = packet;
        runOnLoop(() -> {
            ByteBuf buf = channel.alloc().buffer();
            Types.writeVarInt(buf, out.id());
            out.write(buf);
            channel.writeAndFlush(buf).addListener(io.netty.channel.ChannelFutureListener.CLOSE);
        });
    }

    public int entityId() { return entityId; }
    /** ServerPlayer.raidOmenPosition: 습격 징조를 받은 자리(끝날 때 습격을 연다). 저장은 PlayerVitals(raid_omen_position). */
    public org.domi.furnace.server.block.Pos raidOmenPosition() { return vitals().raidOmenPosition(); }

    public void setRaidOmenPosition(org.domi.furnace.server.block.Pos p) { vitals().raidOmenPosition(p); }

    public boolean isSpectator() { return gameMode() == org.domi.furnace.server.world.GameMode.SPECTATOR; }

    public UUID uuid() { return uuid; }
    public String name() { return name; }

    // 세션 서버가 준 프로필 속성(textures 등) — 탭 목록 add_player 로 다른 클라에 전달돼 스킨이 보인다.
    private volatile java.util.List<org.domi.furnace.server.players.GameProfile.Property> profileProperties = java.util.List.of();

    public java.util.List<org.domi.furnace.server.players.GameProfile.Property> profileProperties() { return profileProperties; }

    // 생존 상태(게임 모드·체력·허기·경험치·사망). 스레드 안전.
    private final PlayerVitals vitals = new PlayerVitals(this);

    public PlayerVitals vitals() { return vitals; }

    private final org.domi.furnace.server.recipe.RecipeBook recipeBook = new org.domi.furnace.server.recipe.RecipeBook();

    private volatile org.domi.furnace.server.advancement.PlayerAdvancements advancements;

    /** 바닐라 enteredNetherPosition: 오버월드에서 네더로 들어갈 때의 오버월드 위치(nether_travel 거리 기준). 아니면 null. */
    public volatile double[] enteredNetherPosition;

    /** 발전 과제 진행(바닐라 ServerPlayer.getAdvancements). 데이터 팩 없이 뜬 서버면 null. */
    public org.domi.furnace.server.advancement.PlayerAdvancements advancements() { return advancements; }

    public void advancements(org.domi.furnace.server.advancement.PlayerAdvancements a) { this.advancements = a; }

    /** 레시피 북(바닐라 ServerRecipeBook). */
    public org.domi.furnace.server.recipe.RecipeBook recipeBook() { return recipeBook; }

    /** 바닐라 ServerPlayer.awardRecipes — 새로 배운 것만 ADD 로 알린다. @return 새로 배운 수 */
    public int awardRecipes(java.util.Collection<org.domi.furnace.server.data.ResourceId> ids,
                            org.domi.furnace.server.recipe.RecipeManager recipes) {
        var added = recipeBook.addRecipes(ids, recipes);
        // 바닐라 ServerRecipeBook.addRecipes: 레시피마다 recipe_unlocked 를 ADD 패킷보다 먼저
        var adv = advancements;
        if (adv != null) for (var id : added) adv.trigger("minecraft:recipe_unlocked", id);
        if (!added.isEmpty()) send(recipeBook.packet(org.domi.furnace.server.net.protocol.play.RecipeBookUpdate.ADD, added));
        return added.size();
    }

    /** 바닐라 ServerPlayer.resetRecipes — 바닐라처럼 잊은 게 없어도 REMOVE 를 보낸다. @return 잊은 수 */
    public int resetRecipes(java.util.Collection<org.domi.furnace.server.data.ResourceId> ids) {
        var removed = recipeBook.removeRecipes(ids);
        send(recipeBook.packet(org.domi.furnace.server.net.protocol.play.RecipeBookUpdate.REMOVE, removed));
        return removed.size();
    }

    public org.domi.furnace.server.world.GameMode gameMode() { return vitals.gameMode(); }

    /** ServerGamePacketListenerImpl.dropSpamTickCount: 크리에이티브 버리기 한도(버릴 때 +20, 매 틱 -1, 200 이상이면 막음). */
    private final java.util.concurrent.atomic.AtomicInteger dropSpam = new java.util.concurrent.atomic.AtomicInteger();

    /** 크리에이티브 버리기를 허용하면 +20 하고 참. */
    public boolean tryCreativeDrop() {
        while (true) {
            int v = dropSpam.get();
            if (v >= 200) return false;
            if (dropSpam.compareAndSet(v, v + 20)) return true;
        }
    }

    public void tickDropSpam() { dropSpam.updateAndGet(v -> v > 0 ? v - 1 : 0); }

    /** hasInfiniteMaterials: 크리에이티브. */
    public boolean hasInfiniteMaterials() { return gameMode() == org.domi.furnace.server.world.GameMode.CREATIVE; }

    /** LivingEntity.take(entity, count): 그 엔티티를 보는 플레이어들에게 줍는 동작(take_item_entity). 제거되기 전에 부른다. */
    public void take(Entity e, int count) {
        if (!e.isRemoved() && e.tracker != null) {
            e.tracker.broadcast(new org.domi.furnace.server.net.protocol.play.TakeItemEntity(e.id(), entityId, count));
        }
    }

    /** 경험치 구슬 줍기 쿨다운(바닐라 takeXpDelay — 줍으면 2, 틱마다 감소). */
    public int takeXpDelay;

    /** 마지막으로 받아들인 이동 패킷의 변위(서버가 아는 deltaMovement 의 근사 — 밀려나기 계산에 쓴다). */
    private volatile double moveDx, moveDy, moveDz;

    // ------------------------------------------------------------------ Targetable(몹이 겨냥하는 대상으로서의 플레이어)

    @Override public int id() { return entityId; }

    /** 눈 높이: 잠(SLEEPING_DIMENSIONS) 0.2, 웅크림 1.27, 서기 1.62. */
    @Override public double eyeY() { return y + (fallFlying ? 0.4 : sleepingPos != null ? 0.2 : isSwimPose() || autoSpinAttackTicks > 0 ? 0.4 : isCrouchPose() ? 1.27 : 1.62); }

    /**
     * 서버가 계산하는 자세(Player.updatePlayerPose) — 소유 region 이 매 틱 정한다.
     * swimming: Entity 수영 플래그(질주 중 물속), crawling: 서 있을 틈이 없어 수영 자세로 눌린 경우, forcedCrouch: 서 있을 틈은 없고 웅크릴 틈은 있는 경우.
     */
    public volatile boolean swimming, crawling, forcedCrouch;

    /** 수영·기어가기 자세(키 0.6, 눈 0.4). */
    public boolean isSwimPose() { return swimming || crawling; }

    private boolean isCrouchPose() { return sneaking || forcedCrouch; }

    @Override public float bbWidth() { return fallFlying ? 0.6F : sleepingPos != null ? 0.2F : 0.6F; }

    /** 자세 크기(Player.POSES): 겉날개 0.6×0.6(눈 0.4), 잠 0.2, 웅크림 1.5, 서기 1.8. */
    @Override public float bbHeight() { return fallFlying ? 0.6F : sleepingPos != null ? 0.2F : isSwimPose() || autoSpinAttackTicks > 0 ? 0.6F : isCrouchPose() ? 1.5F : 1.8F; }

    @Override
    public AABB boundingBox() {
        double hw = bbWidth() / 2.0;
        return new AABB(x - hw, y, z - hw, x + hw, y + bbHeight(), z + hw);
    }

    // ------------------------------------------------------------------ 잠(바닐라 Player.sleepCounter·LivingEntity.DATA_SLEEPING_POS)
    // 소유 region 스레드가 쓰고, 전역 스레드(밤 건너뛰기)와 연결 스레드(이동 패킷 무시)가 읽는다.

    private volatile org.domi.furnace.server.block.Pos sleepingPos;
    private volatile int sleepCounter;
    /** 이 플레이어를 가진 region 의 블록 레벨(소유 region 이 매 틱 넣는다 — 피해로 깰 때 침대를 고친다). */
    public volatile org.domi.furnace.server.block.ChunkLevel currentLevel;

    public java.util.Optional<org.domi.furnace.server.block.Pos> getSleepingPos() { return java.util.Optional.ofNullable(sleepingPos); }

    public org.domi.furnace.server.block.Pos sleepingPos() { return sleepingPos; }

    public void setSleepingPos(org.domi.furnace.server.block.Pos p) { sleepingPos = p; }

    public boolean isSleeping() { return sleepingPos != null; }

    public int sleepCounter() { return sleepCounter; }

    public void sleepCounter(int v) { sleepCounter = v; }

    /** 자는 동안 소유 region 이 떠 둔 일어설 자리 {x, y, z, yaw}(자지 않으면 null) — 자는 중 접속 종료의 저장 위치. */
    private volatile double[] sleepStandUp;

    public double[] sleepStandUp() { return sleepStandUp; }

    public void sleepStandUp(double[] v) { sleepStandUp = v; }

    /** Player.isSleepingLongEnough: 자는 중이고 100틱 이상. */
    public boolean isSleepingLongEnough() { return sleepingPos != null && sleepCounter >= 100; }

    /** 서버 쪽 위치만 바꾼다(패킷 없음 — 바닐라 setPos). 소유 region 스레드. */
    public void setPosRaw(double x, double y, double z) {
        this.x = x; this.y = y; this.z = z;
    }

    /** 서버 쪽 회전만 바꾼다(setYRot·setXRot). */
    public void setRotRaw(float yaw, float pitch) {
        this.yaw = yaw; this.pitch = pitch;
    }

    @Override
    public org.domi.furnace.server.block.Pos blockPosition() { return new org.domi.furnace.server.block.Pos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)); }

    @Override public boolean isAlive() { return !vitals.isDeadOrDying(); }

    /** Player.canBeSeenAsEnemy: 무적 능력(크리에이티브·관전자)이 없고 살아 있다. */
    @Override
    public boolean canBeSeenAsEnemy() {
        var gm = gameMode();
        return isAlive() && gm != org.domi.furnace.server.world.GameMode.CREATIVE && gm != org.domi.furnace.server.world.GameMode.SPECTATOR;
    }

    /** getVisibilityPercent: 웅크리면 ×0.8, 투명이면 ×0.7×방어구 가림, 몹과 같은 종류 머리 아이템이면 ×0.5. */
    @Override
    public double visibilityPercent(Entity looker) {
        ItemStack head = equipment(EquipmentSlot.HEAD);
        return Visibility.percent(sneaking, isInvisible(), Visibility.armorCoverPercentage(head, equipment(EquipmentSlot.CHEST), equipment(EquipmentSlot.LEGS), equipment(EquipmentSlot.FEET)), head, looker);
    }

    /** 투명 효과가 있는가(LivingEntity.updateInvisibilityStatus: 공유 플래그 5). */
    public boolean isInvisible() { return vitals.hasEffect(org.domi.furnace.server.effect.MobEffect.INVISIBILITY); }

    /** 발광 효과가 있는가(LivingEntity.isCurrentlyGlowing 의 서버 쪽: 공유 플래그 6). */
    public boolean isCurrentlyGlowing() { return vitals.hasEffect(org.domi.furnace.server.effect.MobEffect.GLOWING); }

    @Override public boolean isPlayer() { return true; }

    @Override
    public boolean hurtByMob(Entity.Damage source, float amount, Mob attacker) {
        return vitals.hurt(org.domi.furnace.server.entity.damage.DamageType.byKey(source.type()), amount, attacker, shieldSource(source.type(), attacker, null));
    }

    @Override
    public void addDeltaMovement(double dx, double dy, double dz) {
        send(new org.domi.furnace.server.net.protocol.play.SetEntityMotion(entityId, moveDx + dx, moveDy + dy, moveDz + dz));
    }

    // 바닐라 구조: knockback 은 deltaMovement 만 바꾸고, 속도 패킷은 hurtMarked(markHurt)가 선 틱에 한 번만 나간다.
    // 방패로 완전히 막으면 markHurt 가 서지 않아 패킷이 0건이다. 여러 region 스레드가 맞출 수 있어 잠금으로 묶는다.
    private final Object knockbackLock = new Object();
    private double[] knockbackPending;
    private boolean hurtMarked;

    /** LivingEntity.markHurt: 이번 틱 끝에 쌓인 밀침 속도를 보낸다. */
    public void markHurt() {
        synchronized (knockbackLock) { hurtMarked = true; }
    }

    /** ServerEntity 의 `if (hurtMarked)`: 표시가 섰고 쌓인 속도가 있으면 set_entity_motion 한 번. 어느 쪽이든 쌓인 것은 비운다. */
    public void sendPendingKnockback() {
        double[] m;
        synchronized (knockbackLock) {
            m = hurtMarked ? knockbackPending : null;
            hurtMarked = false;
            knockbackPending = null;
        }
        if (m != null) send(new org.domi.furnace.server.net.protocol.play.SetEntityMotion(entityId, m[0], m[1], m[2]));
    }

    /**
     * LivingEntity.knockback: 막힘 저항을 곱하고, 방향이 거의 0 이면 무작위로 다시 뽑고, 속도를 쌓는다(보내기는 sendPendingKnockback).
     * 기준 속도는 마지막 이동 패킷의 이동량(서버가 플레이어 속도를 모른다).
     */
    @Override
    public void knockback(double strength, double dx, double dz) {
        strength *= 1.0 - attributes().value(org.domi.furnace.server.attribute.Attribute.KNOCKBACK_RESISTANCE);
        if (strength <= 0.0) return;
        var rnd = java.util.concurrent.ThreadLocalRandom.current();
        while (dx * dx + dz * dz < 1.0E-5F) {
            dx = (rnd.nextDouble() - rnd.nextDouble()) * 0.01;
            dz = (rnd.nextDouble() - rnd.nextDouble()) * 0.01;
        }
        double len = Math.sqrt(dx * dx + dz * dz);
        double nx = dx / len * strength, nz = dz / len * strength;
        synchronized (knockbackLock) {
            double[] base = knockbackPending != null ? knockbackPending : new double[] {moveDx, moveDy, moveDz};
            knockbackPending = new double[] {base[0] / 2.0 - nx, onGround ? Math.min(0.4, base[1] / 2.0 + strength) : base[1], base[2] / 2.0 - nz};
        }
    }

    /**
     * Player.hurtArmor → doHurtEquipment: 방어구(ArmorItem)마다 max(1, 피해/4) 내구도를 깎고 다 닳으면 부서진다(entity_event 49..52).
     * TODO(내구성 인챈트·불 저항 아이템).
     */
    public void hurtArmor(float amount) { hurtEquipment(amount, 0, 3); }

    /** Player.hurtHelmet(cmx.c) → doHurtEquipment(HEAD): 투구만 깎는다(떨어지는 모루·블록·종유석 — #damages_helmet). */
    public void hurtHelmet(float amount) { hurtEquipment(amount, 0, 0); }

    /** doHurtEquipment: 방어구 칸 first..last(0 머리 .. 3 발) 중 방어구가 max(1, 피해/4) 내구도를 잃는다. */
    private void hurtEquipment(float amount, int first, int last) {
        if (amount <= 0.0F) return;   // Inventory.hurtArmor: 피해 0(방패로 막음)이면 그대로
        amount /= 4.0F;
        if (amount < 1.0F) amount = 1.0F;
        int n = (int) amount;
        for (int i = first; i <= last; i++) {
            int ws = 5 + i;
            ItemStack stack = inventory.get(ws);
            String name = stack.isEmpty() ? "" : org.domi.furnace.server.item.ItemTools.itemName(stack);
            boolean armorItem = name.endsWith("_helmet") || name.endsWith("_chestplate") || name.endsWith("_leggings") || name.endsWith("_boots");
            if (!armorItem || !org.domi.furnace.server.item.ItemTools.isDamageable(stack)) continue;
            int nd = org.domi.furnace.server.item.ItemTools.damage(stack) + n;
            AdvTriggers.durability(this, stack, nd);
            if (nd >= org.domi.furnace.server.item.ItemTools.maxDamage(stack)) {
                inventory.set(ws, ItemStack.EMPTY);
                var ev = new org.domi.furnace.server.net.protocol.play.EntityEvent(entityId, (byte) (49 + i));
                send(ev);
                vitals.broadcastToTrackers(ev);
            } else {
                inventory.set(ws, org.domi.furnace.server.item.ItemTools.withDamage(stack, nd));
            }
        }
    }

    /** 방어구 칸(머리 5·가슴 6·다리 7·발 8)의 armor 속성 합 — 플레이어 기본 방어 0. */
    public float armorValue() {
        float sum = 0;
        String[] slots = {"head", "chest", "legs", "feet"};
        for (int i = 0; i < 4; i++) sum += org.domi.furnace.server.item.ItemTools.attributeBonus(inventory.get(5 + i), "minecraft:generic.armor", slots[i]);
        return Math.clamp(sum, 0.0F, 30.0F);
    }

    public float armorToughness() {
        float sum = 0;
        String[] slots = {"head", "chest", "legs", "feet"};
        for (int i = 0; i < 4; i++) sum += org.domi.furnace.server.item.ItemTools.attributeBonus(inventory.get(5 + i), "minecraft:generic.armor_toughness", slots[i]);
        return Math.clamp(sum, 0.0F, 20.0F);
    }

    /** 먹기·마시기 중인 손(없으면 -1)과 남은 틱 — region 스레드 전용. */
    public int usingHand = -1, usingTicks;

    /**
     * LivingEntity.startUsingItem 의 서버 쪽: 쓰는 손·남은 틱을 두고, 쓰던 중이 아니었으면 item_interact_start 게임 이벤트.
     * 손·틱을 바꾸는 곳은 모두 이것과 stopUsingItem 을 거친다(region 스레드).
     */
    public void startUsingItem(int hand, int ticks) {
        boolean was = usingHand >= 0;
        usingHand = hand;
        usingTicks = ticks;
        if (!was) itemInteractEvent(org.domi.furnace.server.world.gameevent.GameEvent.ITEM_INTERACT_START);
    }

    /** LivingEntity.stopUsingItem: 쓰던 중이었으면 멈추고 item_interact_finish(다 먹음·놓음·취소 모두). */
    public void stopUsingItem() {
        if (usingHand < 0) return;
        usingHand = -1;
        itemInteractEvent(org.domi.furnace.server.world.gameevent.GameEvent.ITEM_INTERACT_FINISH);
    }

    private void itemInteractEvent(org.domi.furnace.server.world.gameevent.GameEvent event) {
        var level = currentLevel;
        if (level != null) level.gameEvent(event, new Vec3(x(), y(), z()), org.domi.furnace.server.world.gameevent.GameEventContext.of(this));
    }

    private byte sentLivingFlags;
    private boolean usedOffhand;

    /** 사용 중 비트(1)·손 비트(2)·회전 공격 비트(4) — LivingEntity.DATA_LIVING_ENTITY_FLAGS(8). 손 비트는 바닐라처럼 사용이 끝나도 남는다. */
    public byte livingFlags() {
        if (usingHand >= 0) usedOffhand = usingHand == 1;
        return (byte) ((usingHand >= 0 ? 1 : 0) | (usedOffhand ? 2 : 0) | (autoSpinAttackTicks > 0 ? 4 : 0));
    }

    // ------------------------------------------------------------------ 근접 공격 상태(Player.attackStrengthTicker·autoSpinAttack*) — region 스레드 전용

    /** Player.attackStrengthTicker: 틱마다 1, 공격·주 손 아이템이 바뀌면 0. */
    public int attackStrengthTicker;
    /** Player.lastItemInMainHand(주 손이 다른 아이템으로 바뀌었는지 보는 데). */
    ItemStack lastItemInMainHand = ItemStack.EMPTY;
    /** 급류 회전 공격: 남은 틱·피해·쓴 삼지창. */
    public int autoSpinAttackTicks;
    public float autoSpinAttackDmg;
    public ItemStack autoSpinAttackItem;
    /** 지난 틱 기준 물에 닿았는가(Entity.wasTouchingWater) — region 이 생존 틱에서 갱신. */
    public boolean wasTouchingWater;
    /** wasTouchingWater 를 실제로 잰 적이 있는가(첨벙 판정의 firstTick 대용). */
    public boolean waterStateKnown;
    /** 지난 생존 틱 기준 사다리·덩굴 위인가(onClimbable). */
    public boolean onClimbable;

    /** getCurrentItemAttackStrengthDelay: 1 / 공격 속도 × 20. 공격 속도 = attack_speed 속성(기본 4 + 주 손 아이템, 성급함 +10%·피로 −10% × (증폭+1)). */
    public float attackStrengthDelay() {
        double speed = PlayerAttributes.value(this, org.domi.furnace.server.attribute.Attribute.ATTACK_SPEED);
        return (float) (1.0 / speed * 20.0);
    }

    /** getAttackStrengthScale(a): clamp((ticker + a) / delay, 0, 1). */
    public float attackStrengthScale(float a) { return Math.clamp(((float) attackStrengthTicker + a) / attackStrengthDelay(), 0.0F, 1.0F); }

    // ------------------------------------------------------------------ 팔 휘두르기(LivingEntity.swing / updateSwingTime)

    private boolean swinging;
    private int swingTime;

    public boolean isSwinging() { return swinging; }

    /** LivingEntity.getCurrentSwingDuration: 성급함·전달체 힘이면 6−(1+증폭), 채굴 피로면 6+(1+증폭)×2, 아니면 6. */
    public int currentSwingDuration() {
        var haste = vitals.getEffect(org.domi.furnace.server.effect.MobEffect.HASTE);
        var conduit = vitals.getEffect(org.domi.furnace.server.effect.MobEffect.CONDUIT_POWER);
        if (haste != null || conduit != null) {
            int amp = Math.max(haste == null ? 0 : haste.amplifier(), conduit == null ? 0 : conduit.amplifier());
            return 6 - (1 + amp);
        }
        var fatigue = vitals.getEffect(org.domi.furnace.server.effect.MobEffect.MINING_FATIGUE);
        return fatigue != null ? 6 + (1 + fatigue.amplifier()) * 2 : 6;
    }

    /**
     * LivingEntity.swing(hand, updateSelf): 휘두르는 중 절반을 넘겼거나 아니면 새로 휘두르고, 보는 플레이어에게 animate(주 손 0, 보조 손 3).
     * updateSelf 면 본인에게도(broadcastAndSend), 아니면 본인 제외(broadcast). 소유 region 스레드에서 부른다.
     */
    public void swing(int hand, boolean updateSelf) {
        if (!swinging || swingTime >= currentSwingDuration() / 2 || swingTime < 0) {
            swingTime = -1;
            swinging = true;
            var pkt = new org.domi.furnace.server.net.protocol.play.Animate(entityId,
                    hand == 0 ? org.domi.furnace.server.net.protocol.play.Animate.SWING_MAIN_HAND : org.domi.furnace.server.net.protocol.play.Animate.SWING_OFF_HAND);
            if (updateSelf) send(pkt);
            vitals.broadcastToTrackers(pkt);
        }
    }

    /** LivingEntity.updateSwingTime(Player.aiStep): 휘두르는 중이면 한 틱 진행, 길이를 채우면 끝. */
    public void tickSwing() {
        if (swinging) {
            swingTime++;
            if (swingTime >= currentSwingDuration()) {
                swingTime = 0;
                swinging = false;
            }
        } else {
            swingTime = 0;
        }
    }

    /** Player.tick 의 공격 쿨다운 부분: ticker++ 하고, 주 손이 다른 아이템이 되면 0 으로. */
    public void tickAttackStrength() {
        attackStrengthTicker++;
        ItemStack cur = inventory.get(inventory.heldWindowSlot());
        if (!lastItemInMainHand.equals(cur)) {   // ItemStack.matches(불변 레코드라 equals)
            if (!sameItem(lastItemInMainHand, cur)) attackStrengthTicker = 0;
            lastItemInMainHand = cur;
        }
    }

    private static boolean sameItem(ItemStack a, ItemStack b) { return a.isEmpty() ? b.isEmpty() : !b.isEmpty() && a.itemId() == b.itemId(); }

    /** startAutoSpinAttack(ticks, dmg, item): 회전 공격 비트를 켠다. */
    public void startAutoSpinAttack(int ticks, float dmg, ItemStack item) {
        autoSpinAttackTicks = ticks;
        removeEntitiesOnShoulder();   // startAutoSpinAttack: 어깨의 몹을 내려놓는다
        autoSpinAttackDmg = dmg;
        autoSpinAttackItem = item;
    }

    private byte sentSharedFlags;
    private int sentPose;

    /** Entity.DATA_SHARED_FLAGS(0): 불(1)·웅크림(2)·질주(8)·수영(0x10)·투명(0x20)·발광(0x40)·겉날개 비행(0x80). */
    public byte sharedFlags() {
        return (byte) ((isOnFire() ? 1 : 0) | (sneaking ? 2 : 0) | (sprinting ? 8 : 0) | (swimming ? 0x10 : 0)
                | (isInvisible() ? 0x20 : 0) | (isCurrentlyGlowing() ? 0x40 : 0) | (fallFlying ? 0x80 : 0));
    }

    /** Player.updatePlayerPose: 겉날개 비행 FALL_FLYING(1), 잠 2, 수영 3, 회전 공격 SPIN_ATTACK(4), 웅크림 CROUCHING(5), 아니면 STANDING(0). 수영·기어가기·끼임 검사는 {@link PlayerPose}. */
    public int pose() {
        int o = poseOverride;
        return o >= 0 ? o : computedPose();
    }

    /** 서버가 계산한 자세(플러그인 고정과 무관). */
    public int computedPose() { return fallFlying ? 1 : sleepingPos != null ? 2 : isSwimPose() ? 3 : autoSpinAttackTicks > 0 ? 4 : isCrouchPose() ? 5 : 0; }

    /** 플러그인이 고정한 자세(Entity.setPose) — -1 이면 서버 계산값. 다른 플레이어가 보는 자세 데이터만 바뀌고 판정 상자는 그대로다. */
    private volatile int poseOverride = -1;

    public void poseOverride(int pose) { poseOverride = pose; }

    public int poseOverride() { return poseOverride; }

    // ------------------------------------------------------------------ 겉날개 비행(LivingEntity 공유 플래그 7·fallFlyTicks) — 쓰기는 region 스레드
    private volatile boolean fallFlying;
    /** LivingEntity.fallFlyTicks. */
    public int fallFlyTicks;
    /** 서버가 굴리는 deltaMovement(비행 중에만 갱신 — 충돌 피해·로켓 가속에 쓴다). */
    public double flyVx, flyVy, flyVz;
    private boolean sharedFlagsForce;

    public boolean isFallFlying() { return fallFlying; }

    public void setFallFlying(boolean v) { fallFlying = v; }

    /** 값이 같아도 다음 동기에서 공유 플래그를 다시 보낸다(SynchedEntityData dirty). */
    public void forceSharedFlagsResend() { sharedFlagsForce = true; }

    /** 서버가 아는 deltaMovement: 비행 중이면 굴리는 값, 아니면 마지막 이동 패킷 변위. */
    public double[] serverMovement() { return fallFlying ? new double[] {flyVx, flyVy, flyVz} : knownMovement(); }

    private org.domi.furnace.server.block.Pos sentSleepingPos;

    private static org.domi.furnace.server.net.protocol.play.SetEntityData.Item sleepingPosItem(org.domi.furnace.server.block.Pos p) {
        return new org.domi.furnace.server.net.protocol.play.SetEntityData.Item(14, org.domi.furnace.server.net.protocol.play.SetEntityData.SERIALIZER_OPTIONAL_BLOCK_POS,
                p == null ? null : new int[] {p.x(), p.y(), p.z()});
    }

    private static org.domi.furnace.server.net.protocol.play.SetEntityData.Item flagItem(int index, byte v) {
        return new org.domi.furnace.server.net.protocol.play.SetEntityData.Item(index, org.domi.furnace.server.net.protocol.play.SetEntityData.SERIALIZER_BYTE, v);
    }

    /** 몸에 박힌 화살 수(LivingEntity.DATA_ARROW_COUNT 12). 20 × (30 − 수) 틱마다 하나씩 빠진다. */
    private int arrowCount, removeArrowTime, sentArrowCount;
    /** 클라이언트가 아는 DATA_TICKS_FROZEN(얼음 화면·하트, 다른 사람에게는 떨림) — region 스레드 전용. */
    private int sentTicksFrozen;

    private static org.domi.furnace.server.net.protocol.play.SetEntityData.Item ticksFrozenItem(int v) {
        return new org.domi.furnace.server.net.protocol.play.SetEntityData.Item(Entity.DATA_TICKS_FROZEN, org.domi.furnace.server.net.protocol.play.SetEntityData.SERIALIZER_INT, v);
    }

    public int arrowCount() { return arrowCount; }

    public void setArrowCount(int n) { arrowCount = n; }

    /** LivingEntity.tick 의 화살 제거(region 이 syncLivingFlags 앞에서 매 틱). */
    public void tickArrowCount() {
        if (arrowCount > 0) {
            if (removeArrowTime <= 0) removeArrowTime = 20 * (30 - arrowCount);
            if (--removeArrowTime <= 0) arrowCount--;
        }
    }

    /** LivingEntity.DATA_EFFECT_PARTICLES(10)·DATA_EFFECT_AMBIENCE_ID(11) — 마지막으로 보낸 값. */
    private java.util.List<org.domi.furnace.server.net.protocol.play.ParticleOptions> sentEffectParticles = java.util.List.of();
    private boolean sentEffectAmbient;

    private static org.domi.furnace.server.net.protocol.play.SetEntityData.Item effectParticlesItem(java.util.List<org.domi.furnace.server.net.protocol.play.ParticleOptions> l) {
        return new org.domi.furnace.server.net.protocol.play.SetEntityData.Item(LivingEntity.DATA_EFFECT_PARTICLES, org.domi.furnace.server.net.protocol.play.SetEntityData.SERIALIZER_PARTICLES, l);
    }

    private static org.domi.furnace.server.net.protocol.play.SetEntityData.Item effectAmbientItem(boolean v) {
        return new org.domi.furnace.server.net.protocol.play.SetEntityData.Item(LivingEntity.DATA_EFFECT_AMBIENT, org.domi.furnace.server.net.protocol.play.SetEntityData.SERIALIZER_BOOLEAN, v);
    }

    private static org.domi.furnace.server.net.protocol.play.SetEntityData.Item poseItem(int pose) {
        return new org.domi.furnace.server.net.protocol.play.SetEntityData.Item(6, org.domi.furnace.server.net.protocol.play.SetEntityData.SERIALIZER_POSE, pose);
    }

    /** Player.DATA_PLAYER_MODE_CUSTOMISATION(17, byte, 기본 0)·DATA_PLAYER_MAIN_HAND(18, byte, 기본 1=오른손) — 1.21.1 번호. */
    public static final int DATA_PLAYER_MODE_CUSTOMISATION = 17, DATA_PLAYER_MAIN_HAND = 18;

    /** Player.DATA_SHOULDER_LEFT(19)·RIGHT(20) — 1.21.1 번호(compound, 기본 빈 태그). */
    public static final int DATA_SHOULDER_LEFT = 19, DATA_SHOULDER_RIGHT = 20;

    /** 어깨에 앉은 몹의 NBT(비면 빈 compound). 다른 region 의 앵무새도 앉히므로 shoulderLock 으로 지킨다. */
    private final Object shoulderLock = new Object();
    private org.domi.furnace.server.net.io.nbt.NbtCompound shoulderLeft = new org.domi.furnace.server.net.io.nbt.NbtCompound(),
            shoulderRight = new org.domi.furnace.server.net.io.nbt.NbtCompound();
    /** timeEntitySatOnShoulder: 어깨에 앉힌 게임 시각. */
    private long timeEntitySatOnShoulder;
    private org.domi.furnace.server.net.io.nbt.NbtCompound sentShoulderLeft, sentShoulderRight;

    Object shoulderLock() { return shoulderLock; }

    public org.domi.furnace.server.net.io.nbt.NbtCompound shoulderLeft() { synchronized (shoulderLock) { return shoulderLeft; } }

    public org.domi.furnace.server.net.io.nbt.NbtCompound shoulderRight() { synchronized (shoulderLock) { return shoulderRight; } }

    void setShoulderLeft(org.domi.furnace.server.net.io.nbt.NbtCompound t) { synchronized (shoulderLock) { shoulderLeft = t; } }

    void setShoulderRight(org.domi.furnace.server.net.io.nbt.NbtCompound t) { synchronized (shoulderLock) { shoulderRight = t; } }

    long timeEntitySatOnShoulder() { synchronized (shoulderLock) { return timeEntitySatOnShoulder; } }

    void timeEntitySatOnShoulder(long t) { synchronized (shoulderLock) { timeEntitySatOnShoulder = t; } }

    private static boolean shoulderDiffers(org.domi.furnace.server.net.io.nbt.NbtCompound a, org.domi.furnace.server.net.io.nbt.NbtCompound b) {
        boolean ea = a == null || a.tags().isEmpty(), eb = b == null || b.tags().isEmpty();
        return !(ea && eb) && a != b;
    }

    private static org.domi.furnace.server.net.protocol.play.SetEntityData.Item shoulderItem(int index, org.domi.furnace.server.net.io.nbt.NbtCompound t) {
        return new org.domi.furnace.server.net.protocol.play.SetEntityData.Item(index, org.domi.furnace.server.net.protocol.play.SetEntityData.SERIALIZER_COMPOUND,
                t == null ? new org.domi.furnace.server.net.io.nbt.NbtCompound() : t);
    }

    /** ShoulderEntityLeft/Right 저장(비어 있지 않을 때만). */
    public void saveShoulders(org.domi.furnace.server.net.io.nbt.NbtCompound out) { PlayerShoulders.save(this, out); }

    public void loadShoulders(org.domi.furnace.server.net.io.nbt.NbtCompound in) { PlayerShoulders.load(this, in); }

    /** 앵무새를 어깨에 앉힌다(Player.setEntityOnShoulder). 앉혔으면 true. */
    public boolean setEntityOnShoulder(org.domi.furnace.server.net.io.nbt.NbtCompound tag) { return PlayerShoulders.setEntityOnShoulder(this, tag); }

    /** 어깨의 몹을 내려놓는다(Player.removeEntitiesOnShoulder). */
    public void removeEntitiesOnShoulder() { PlayerShoulders.removeEntitiesOnShoulder(this); }

    /** 클라 설정(ClientInformation)의 보이는 스킨 부위·주 손 — 연결 스레드가 쓰고 region 스레드가 syncLivingFlags 에서 보낸다. */
    private volatile byte skinParts, mainArm = 1;
    private byte sentSkinParts, sentMainArm = 1;

    /** 주 손: 0 왼손, 1 오른손. */
    public int mainArm() { return mainArm; }

    /** ServerPlayer.updateOptions: 스킨 부위 비트와 주 손(0 왼손, 1 오른손). */
    public void clientOptions(int skinParts, int mainArm) {
        this.skinParts = (byte) skinParts;
        this.mainArm = (byte) (mainArm == 0 ? 0 : 1);
    }

    /** 바뀐 엔티티 데이터(공유 플래그 0·자세 6·사용 중 플래그 8·효과 입자 10·11 등)를 본인과 보는 사람에게(ServerEntity.sendDirtyEntityData). */
    public void syncLivingFlags() {
        boolean reset = resendEntityData;
        if (reset) {   // 리스폰: 클라가 기본값 엔티티를 새로 만들었다 — 기본값과 다른 것을 다시 보낸다
            resendEntityData = false;
            sentSharedFlags = 0; sentPose = 0; sentLivingFlags = 0; sentSleepingPos = null; sentSkinParts = 0; sentMainArm = 1; sentShoulderLeft = null; sentShoulderRight = null;
            sentEffectParticles = java.util.List.of(); sentEffectAmbient = false;
            sentArrowCount = 0; arrowCount = 0; removeArrowTime = 0;
            sentTicksFrozen = 0;
        }
        byte shared = sharedFlags(), f = livingFlags();
        int pose = pose();
        var items = new java.util.ArrayList<org.domi.furnace.server.net.protocol.play.SetEntityData.Item>(3);
        if (shared != sentSharedFlags || sharedFlagsForce) items.add(flagItem(0, shared));
        sharedFlagsForce = false;
        if (pose != sentPose) items.add(poseItem(pose));
        int frozen = vitals.ticksFrozen();
        if (frozen != sentTicksFrozen) items.add(ticksFrozenItem(frozen));
        if (f != sentLivingFlags) items.add(flagItem(8, f));
        var ep = vitals.effectParticles();   // 효과 입자: 클라이언트가 이 목록을 보고 몸 주위에 뿌린다
        Boolean ambNow = vitals.effectsAmbient();
        boolean amb = ambNow == null ? sentEffectAmbient : ambNow;
        if (!ep.equals(sentEffectParticles)) items.add(effectParticlesItem(ep));
        if (amb != sentEffectAmbient) items.add(effectAmbientItem(amb));
        int arrows = arrowCount;
        if (arrows != sentArrowCount) items.add(new org.domi.furnace.server.net.protocol.play.SetEntityData.Item(LivingEntity.DATA_ARROW_COUNT, org.domi.furnace.server.net.protocol.play.SetEntityData.SERIALIZER_INT, arrows));
        var sp = sleepingPos;
        if (!java.util.Objects.equals(sp, sentSleepingPos)) items.add(sleepingPosItem(sp));
        byte skin = skinParts, arm = mainArm;
        if (skin != sentSkinParts) items.add(flagItem(DATA_PLAYER_MODE_CUSTOMISATION, skin));
        if (arm != sentMainArm) items.add(flagItem(DATA_PLAYER_MAIN_HAND, arm));
        var sl = shoulderLeft(); var sr = shoulderRight();
        if (shoulderDiffers(sl, sentShoulderLeft)) items.add(shoulderItem(DATA_SHOULDER_LEFT, sl));
        if (shoulderDiffers(sr, sentShoulderRight)) items.add(shoulderItem(DATA_SHOULDER_RIGHT, sr));
        if (items.isEmpty()) {
            if (reset) pairingDataSnap = null;   // 전부 기본값
            return;
        }
        sentSharedFlags = shared;
        sentPose = pose;
        sentLivingFlags = f;
        sentEffectParticles = ep;
        sentEffectAmbient = amb;
        sentArrowCount = arrows;
        sentTicksFrozen = frozen;
        sentSleepingPos = sp;
        sentSkinParts = skin;
        sentMainArm = arm;
        sentShoulderLeft = sl;
        sentShoulderRight = sr;
        pairingDataSnap = buildPairingData();
        var pkt = new org.domi.furnace.server.net.protocol.play.SetEntityData(entityId, items);
        send(pkt);
        vitals.broadcastToTrackers(pkt);
    }

    // ------------------------------------------------------------------ 장비 동기(LivingEntity.detectEquipmentUpdates) — region 스레드 전용

    private final ItemStack[] lastEquipment = new ItemStack[EquipmentSlot.values().length];
    { java.util.Arrays.fill(lastEquipment, ItemStack.EMPTY); }

    /** 그 장비 칸의 아이템: 주 손 = 든 핫바 칸, 보조 손 = 45, 방어구 = 5(머리)..8(발). 몸 칸은 늘 비어 있다. */
    public ItemStack equipment(EquipmentSlot slot) {
        PlayerInventory inv = inventory;
        return switch (slot) {
            case MAINHAND -> inv.get(inv.heldWindowSlot());
            case OFFHAND -> inv.get(45);
            case HEAD -> inv.get(5);
            case CHEST -> inv.get(6);
            case LEGS -> inv.get(7);
            case FEET -> inv.get(8);
            case BODY -> ItemStack.EMPTY;
        };
    }

    /** collectEquipmentChanges: 지난번과 다른(ItemStack.matches 아님) 칸들. 없으면 null. */
    java.util.List<org.domi.furnace.server.net.protocol.play.SetEquipment.Entry> equipmentChanges() {
        java.util.List<org.domi.furnace.server.net.protocol.play.SetEquipment.Entry> out = null;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack cur = equipment(slot), last = lastEquipment[slot.ordinal()];
            if (cur.equals(last) || cur.isEmpty() && last.isEmpty()) continue;
            if (out == null) out = new java.util.ArrayList<>();
            out.add(new org.domi.furnace.server.net.protocol.play.SetEquipment.Entry(slot, cur));
            lastEquipment[slot.ordinal()] = cur;
        }
        return out;
    }

    /** 바뀐 장비를 보는 사람에게(본인 제외 — 바닐라 ChunkMap.broadcast). */
    public void syncEquipment() {
        var changes = equipmentChanges();
        if (changes != null) {
            pairingEquipmentSnap = buildPairingEquipment();
            vitals.broadcastToTrackers(new org.domi.furnace.server.net.protocol.play.SetEquipment(entityId, changes));
        }
        syncAttributes();
    }

    // 바닐라 detectEquipmentUpdates 의 속성 수정자 갈아 끼우기(+ 효과 추가/제거·달리기 설정) + ServerEntity.sendChanges 의 update_attributes(본인 포함).
    // 입장(리스폰) 첫 동기엔 syncable 전부, 이후엔 바뀐 속성만 보낸다. 수정자 목록은 PlayerAttributes.modifiers 한 곳에서 나온다.
    private final ItemStack[] attributeEquipment = new ItemStack[EquipmentSlot.values().length];
    { java.util.Arrays.fill(attributeEquipment, ItemStack.EMPTY); }
    private volatile boolean armorAttributesSent;
    private boolean attributeInputsKnown;
    private java.util.List<Object> lastEffectSignature = java.util.List.of();
    private boolean lastSprintForAttributes;
    /** 이 동기가 단 수정자 id(속성별) — /attribute 로 사람이 단 수정자는 건드리지 않으려고 따로 기억한다. */
    private final java.util.Map<org.domi.furnace.server.attribute.Attribute, java.util.Set<String>> managedModifiers = new java.util.HashMap<>();
    private volatile org.domi.furnace.server.net.protocol.play.UpdateAttributes pairingAttributesSnap;
    private int lastLocationVersion = -1;
    private org.domi.furnace.server.attribute.AttributeModifier lastFrostModifier;

    /** 위치에 따라 붙는 수정자: 영혼 가속·서리 걸음(locationEffects) + 가루눈 얼음(이동 속도, PlayerVitals 의 tryAddFrost). */
    public java.util.Collection<org.domi.furnace.server.attribute.AttributeModifier> locatedModifiers(org.domi.furnace.server.attribute.Attribute attr) {
        var loc = locationEffects.modifiers(attr);
        var frost = attr == org.domi.furnace.server.attribute.Attribute.MOVEMENT_SPEED ? vitals.frostModifier() : null;
        if (frost == null) return loc;
        var out = new java.util.ArrayList<>(loc);
        out.add(frost);
        return out;
    }
    /** 위치 변화 인챈트(영혼 가속·서리 걸음)의 상태 — region 스레드 전용. */
    public final PlayerLocationEffects.State locationEffects = new PlayerLocationEffects.State();

    /** 리스폰·차원 이동으로 클라이언트가 새 플레이어를 만들었다 — 다음 동기에 속성을 전부 다시 보낸다. */
    public void markArmorAttributesResend() { armorAttributesSent = false; }

    /** 다른 region 스레드가 페어링 때 읽는 syncable 속성 스냅샷(불변 사본). 아직 동기 전이면 null. */
    public org.domi.furnace.server.net.protocol.play.UpdateAttributes pairingAttributes() { return pairingAttributesSnap; }

    /**
     * region 스레드 전용. 장비·효과·달리기가 바뀌었으면 맵의 수정자를 맞추고, 최대 체력·산소 보너스를 PlayerVitals 로 넘기고,
     * 바뀐 속성(처음이면 syncable 전부)을 본인과 보는 사람에게 보낸다.
     */
    public void syncAttributes() {
        var map = attributes();
        boolean changed = !attributeInputsKnown;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack cur = equipment(slot), last = attributeEquipment[slot.ordinal()];
            if (cur.equals(last) || cur.isEmpty() && last.isEmpty()) continue;
            attributeEquipment[slot.ordinal()] = cur;
            changed = true;
        }
        java.util.List<org.domi.furnace.server.effect.MobEffectInstance> effects = vitals.activeEffects();
        java.util.List<Object> sig = new java.util.ArrayList<>(effects.size() * 2);
        for (var e : effects) { sig.add(e.effect()); sig.add(e.amplifier()); }
        boolean sprint = sprinting;
        var frost = vitals.frostModifier();
        if (!sig.equals(lastEffectSignature) || sprint != lastSprintForAttributes || locationEffects.version() != lastLocationVersion
                || !java.util.Objects.equals(frost, lastFrostModifier)) changed = true;
        if (changed) {
            lastEffectSignature = sig;
            lastSprintForAttributes = sprint;
            lastLocationVersion = locationEffects.version();
            lastFrostModifier = frost;
            attributeInputsKnown = true;
            reconcileModifiers(map, effects, sprint);
        }
        pushVitalsInputs(map, effects);
        refreshAttributesSave(map);
        boolean first = !armorAttributesSent;
        java.util.List<org.domi.furnace.server.attribute.AttributeInstance> out = first ? map.syncable() : map.takeDirty();
        if (first) map.takeDirty();
        if (out.isEmpty()) return;
        armorAttributesSent = true;
        var pkt = org.domi.furnace.server.net.protocol.play.UpdateAttributes.snapshot(entityId, out);
        pairingAttributesSnap = first ? pkt : org.domi.furnace.server.net.protocol.play.UpdateAttributes.snapshot(entityId, map.syncable());
        send(pkt);
        vitals.broadcastToTrackers(pkt);
    }

    /**
     * /attribute 가 맵에 직접 단 기본값과 수정자(장비·효과·달리기·위치 효과 수정자는 제외). PlayerAttributes.value 가 이것을 합쳐 쓴다.
     * 소유 region 스레드 전용(managedModifiers 가 그 스레드 것이다).
     */
    public double attributeBase(org.domi.furnace.server.attribute.Attribute attr) {
        var inst = attributes().get(attr);
        return inst == null ? PlayerAttributes.base(attr) : inst.baseValue();
    }

    public java.util.List<org.domi.furnace.server.attribute.AttributeModifier> userModifiers(org.domi.furnace.server.attribute.Attribute attr) {
        var inst = attributes().get(attr);
        if (inst == null) return java.util.List.of();
        var managed = managedModifiers.get(attr);
        java.util.List<org.domi.furnace.server.attribute.AttributeModifier> out = new java.util.ArrayList<>();
        for (var m : inst.modifiers().values()) if (managed == null || !managed.contains(m.id())) out.add(m);
        return out;
    }

    /** 속성마다 원하는 수정자(PlayerAttributes.modifiers)와 맵을 맞춘다. 같은 수정자는 그대로 둬 바뀜 표시가 서지 않는다. */
    private void reconcileModifiers(org.domi.furnace.server.attribute.AttributeMap map, java.util.List<org.domi.furnace.server.effect.MobEffectInstance> effects, boolean sprint) {
        for (var inst : map.all()) {
            var attr = inst.attribute();
            var want = PlayerAttributes.modifiers(attr, attributeEquipment, effects, sprint, locatedModifiers(attr));
            var had = managedModifiers.get(attr);
            if (had != null) for (String id : had) if (!want.containsKey(id)) inst.removeModifier(id);
            for (var m : want.values()) if (!m.equals(inst.getModifier(m.id()))) inst.addOrReplaceModifier(m);
            if (want.isEmpty()) managedModifiers.remove(attr);
            else managedModifiers.put(attr, new java.util.HashSet<>(want.keySet()));
        }
    }

    // 속성 저장(바닐라 LivingEntity "attributes"): 기본값 + /attribute 로 단 수정자. 장비·효과·달리기·위치 효과 수정자(managedModifiers)는
    // 바닐라 transient 라 빼고 저장한다. 저장은 연결 스레드에서 하므로 소유 스레드가 바뀔 때마다 불변 스냅샷을 떠 둔다.
    private int attributesSaveVersion = -1;
    private volatile org.domi.furnace.server.net.io.nbt.NbtList attributesSave;

    private void refreshAttributesSave(org.domi.furnace.server.attribute.AttributeMap map) {
        if (map.version() == attributesSaveVersion && attributesSave != null) return;
        attributesSaveVersion = map.version();
        attributesSave = map.save((a, id) -> {
            var managed = managedModifiers.get(a);
            return managed != null && managed.contains(id);
        });
    }

    /** 저장용 "attributes" 를 붙인다(아무 스레드). 아직 한 번도 동기 전이면 붙이지 않아 파일의 기존 값이 남는다. */
    public void saveAttributes(org.domi.furnace.server.net.io.nbt.NbtCompound out) {
        var l = attributesSave;
        if (l != null) out.putList("attributes", l);
    }

    /**
     * 접속 때(region 에 넣기 전) 저장된 속성을 읽는다. 바닐라 readAdditionalSaveData 처럼 체력보다 먼저 — 최대 체력을 PlayerVitals 에
     * 넘겨 두어야 늘린 최대 체력 위의 체력이 20 으로 잘리지 않는다.
     */
    public void loadAttributes(org.domi.furnace.server.net.io.nbt.NbtCompound in) {
        if (!(in.tags().get("attributes") instanceof org.domi.furnace.server.net.io.nbt.NbtList l)) return;
        var map = attributes();
        map.load(l);
        attributesSave = l;
        attributesSaveVersion = map.version();
        var hp = map.get(org.domi.furnace.server.attribute.Attribute.MAX_HEALTH);
        vitals.attributeInputs(hp.baseValue(), java.util.List.copyOf(hp.modifiers().values()),
                map.value(org.domi.furnace.server.attribute.Attribute.OXYGEN_BONUS));
    }

    /** 최대 체력(기본값 + 효과 밖 수정자)과 산소 보너스를 PlayerVitals 가 쓰도록 넘기고, 최대 체력이 줄었으면 체력을 자른다. */
    private void pushVitalsInputs(org.domi.furnace.server.attribute.AttributeMap map, java.util.List<org.domi.furnace.server.effect.MobEffectInstance> effects) {
        var hp = map.get(org.domi.furnace.server.attribute.Attribute.MAX_HEALTH);
        var fromEffects = PlayerAttributes.effectModifiers(org.domi.furnace.server.attribute.Attribute.MAX_HEALTH, effects).keySet();
        java.util.List<org.domi.furnace.server.attribute.AttributeModifier> extra = new java.util.ArrayList<>();
        for (var m : hp.modifiers().values()) if (!fromEffects.contains(m.id())) extra.add(m);
        vitals.attributeInputs(hp.baseValue(), extra, map.value(org.domi.furnace.server.attribute.Attribute.OXYGEN_BONUS));
        pushFallInputs(map, effects);
    }

    /** 낙하 피해 입력: generic.safe_fall_distance·generic.fall_damage_multiplier 의 기본값과 효과 밖 수정자(효과 몫은 PlayerVitals 가 지금 효과로 더한다). */
    private void pushFallInputs(org.domi.furnace.server.attribute.AttributeMap map, java.util.List<org.domi.furnace.server.effect.MobEffectInstance> effects) {
        var safe = org.domi.furnace.server.attribute.Attribute.SAFE_FALL_DISTANCE;
        var mult = org.domi.furnace.server.attribute.Attribute.FALL_DAMAGE_MULTIPLIER;
        var si = map.get(safe);
        var mi = map.get(mult);
        if (si == null || mi == null) return;
        vitals.fallInputs(si.baseValue(), nonEffectModifiers(si, safe, effects), mi.baseValue(), nonEffectModifiers(mi, mult, effects));
    }

    private static java.util.List<org.domi.furnace.server.attribute.AttributeModifier> nonEffectModifiers(org.domi.furnace.server.attribute.AttributeInstance inst,
            org.domi.furnace.server.attribute.Attribute attr, java.util.List<org.domi.furnace.server.effect.MobEffectInstance> effects) {
        var fromEffects = PlayerAttributes.effectModifiers(attr, effects).keySet();
        java.util.List<org.domi.furnace.server.attribute.AttributeModifier> extra = new java.util.ArrayList<>();
        for (var m : inst.modifiers().values()) if (!fromEffects.contains(m.id())) extra.add(m);
        return extra;
    }

    /** 다른 region 스레드가 페어링 때 읽는 불변 스냅샷(소유 스레드가 동기 직후 공개). */
    private volatile org.domi.furnace.server.net.protocol.play.SetEquipment pairingEquipmentSnap;
    private volatile org.domi.furnace.server.net.protocol.play.SetEntityData pairingDataSnap;
    /** 리스폰(새 ServerPlayer 대응) 뒤 엔티티 데이터를 다시 보내라는 표시 — 연결 스레드가 켜고 소유 스레드가 처리. */
    private volatile boolean resendEntityData;

    /** 죽은 뒤·엔드 뒤 리스폰: 다음 syncLivingFlags 가 본인과 보는 사람에게 기본값이 아닌 데이터를 다시 보낸다. */
    public void markEntityDataResend() {
        resendEntityData = true;
        armorAttributesSent = false;
        runOnOwner(() -> PlayerLocationEffects.reset(this));   // 새 엔티티: 위치 효과 상태도 처음부터
        runOnOwner(() -> ElytraFlight.resetForRespawn(this));   // 새 엔티티: 겉날개 상태는 소유 스레드에서 지운다
        runOnOwner(() -> remainingFireTicks = -FIRE_IMMUNE_TICKS);   // 새 엔티티: 불도 처음 상태로
    }

    /** sendPairingData 의 장비(아무 스레드에서나 — 불변 스냅샷). 없으면 null. */
    public org.domi.furnace.server.net.protocol.play.SetEquipment pairingEquipment() { return pairingEquipmentSnap; }

    /** 비어 있지 않은 칸 전부(마지막으로 동기한 값) — 소유 스레드 전용. */
    private org.domi.furnace.server.net.protocol.play.SetEquipment buildPairingEquipment() {
        java.util.List<org.domi.furnace.server.net.protocol.play.SetEquipment.Entry> eq = new java.util.ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack st = lastEquipment[slot.ordinal()];
            if (!st.isEmpty()) eq.add(new org.domi.furnace.server.net.protocol.play.SetEquipment.Entry(slot, st));
        }
        return eq.isEmpty() ? null : new org.domi.furnace.server.net.protocol.play.SetEquipment(entityId, java.util.List.copyOf(eq));
    }

    /** ServerEntity.sendPairingData 의 기본값이 아닌 엔티티 데이터(새로 보게 된 사람에게, 불변 스냅샷). 없으면 null. */
    public org.domi.furnace.server.net.protocol.play.SetEntityData pairingData() { return pairingDataSnap; }

    private org.domi.furnace.server.net.protocol.play.SetEntityData buildPairingData() {
        var items = new java.util.ArrayList<org.domi.furnace.server.net.protocol.play.SetEntityData.Item>(3);
        if (sentSharedFlags != 0) items.add(flagItem(0, sentSharedFlags));
        if (sentPose != 0) items.add(poseItem(sentPose));
        if (sentTicksFrozen != 0) items.add(ticksFrozenItem(sentTicksFrozen));
        if (sentLivingFlags != 0) items.add(flagItem(8, sentLivingFlags));
        if (!sentEffectParticles.isEmpty()) items.add(effectParticlesItem(sentEffectParticles));
        if (sentEffectAmbient) items.add(effectAmbientItem(true));
        if (sentArrowCount != 0) items.add(new org.domi.furnace.server.net.protocol.play.SetEntityData.Item(LivingEntity.DATA_ARROW_COUNT, org.domi.furnace.server.net.protocol.play.SetEntityData.SERIALIZER_INT, sentArrowCount));
        if (sentSleepingPos != null) items.add(sleepingPosItem(sentSleepingPos));
        if (sentSkinParts != 0) items.add(flagItem(DATA_PLAYER_MODE_CUSTOMISATION, sentSkinParts));
        if (sentMainArm != 1) items.add(flagItem(DATA_PLAYER_MAIN_HAND, sentMainArm));
        if (sentShoulderLeft != null && !sentShoulderLeft.tags().isEmpty()) items.add(shoulderItem(DATA_SHOULDER_LEFT, sentShoulderLeft));
        if (sentShoulderRight != null && !sentShoulderRight.tags().isEmpty()) items.add(shoulderItem(DATA_SHOULDER_RIGHT, sentShoulderRight));
        return items.isEmpty() ? null : new org.domi.furnace.server.net.protocol.play.SetEntityData(entityId, java.util.List.copyOf(items));
    }

    /** 플레이어끼리 때릴 때 방패 내구 난수를 꺼낼 레벨(region 이 공격 직전에 넣는다). */
    org.domi.furnace.server.block.BlockLevel shieldLevel;

    /** 방패를 들고 막는 중인가(LivingEntity.isBlocking). */
    public boolean isBlocking() { return Shields.isBlocking(this); }

    /** 직접 원인 엔티티(투사체)가 따로 있는 피해: 막기 방향·관통은 그 엔티티로 본다. */
    public boolean hurtByVia(Entity.Damage source, float amount, Targetable attacker, Entity direct) {
        return vitals.hurt(org.domi.furnace.server.entity.damage.DamageType.byKey(source.type()), amount, attacker, shieldSource(source.type(), attacker, direct));
    }

    /** 피해 원인 위치: 직접 원인 엔티티(없으면 때린 몹) 위치. */
    public Shields.Source shieldSource(String type, Targetable attacker, Entity direct) {
        if (direct == null && attacker instanceof Player ap)   // 플레이어가 직접 때림: 위치는 그 플레이어, 막으면 밀리고 도끼면 방패가 풀린다
            return new Shields.Source(type, true, ap.x(), ap.z(), ap, true, false, shieldLevel);
        Entity d = direct != null ? direct : attacker instanceof Entity e ? e : null;
        if (d == null) return Shields.Source.none(type);
        boolean piercing = d instanceof AbstractArrow a && a.getPierceLevel() > 0;
        boolean living = d instanceof LivingEntity && !org.domi.furnace.server.entity.damage.DamageType.keyHasTag(type, "is_projectile");
        return new Shields.Source(type, true, d.x(), d.z(), d instanceof LivingEntity le ? le : null, living, piercing, d.level());
    }

    /** 쇠뇌 장전 소리(시작·중간)를 이미 냈는가 — 바닐라는 CrossbowItem 객체 필드라 전역이지만 여기선 플레이어별. */
    public boolean crossbowStartSound, crossbowMidSound;

    /** 던져 둔 낚시찌(Player.fishing). */
    public FishingHook fishing;

    private volatile org.domi.furnace.server.stats.PlayerStats stats;

    public org.domi.furnace.server.stats.PlayerStats stats() { return stats; }

    public void stats(org.domi.furnace.server.stats.PlayerStats s) { this.stats = s; }

    /** Player.awardStat(통계, n): 이름은 "minecraft:커스텀" 또는 "minecraft:종류:값". 통계가 아직 붙지 않은 플레이어(시험)는 무시. */
    public void awardStat(String stat, int amount) {
        var s = stats;
        if (s != null) s.increment(stat, amount);
        var b = scoreboard;
        if (b != null) b.onStat(this, stat, amount);   // 바닐라 ServerPlayer.awardStat: 통계 기준 목표에 더한다
    }

    /** 마지막으로 이 플레이어를 틱한 region(주인 판별용, 낡을 수 있다 — ownsPlayer 로 확인). */
    public volatile org.domi.furnace.server.world.Region ownerRegion;
    private final java.util.concurrent.ConcurrentLinkedQueue<Runnable> ownerTasks = new java.util.concurrent.ConcurrentLinkedQueue<>();

    /** 플레이어 소유 스레드에서 실행한다: 지금 주인 region 스레드면 바로, 아니면 다음 플레이어 틱에(순서 보존). */
    public void runOnOwner(Runnable task) {
        var r = ownerRegion;
        if (r != null && ownerTasks.isEmpty() && r.ownsPlayer(this)) task.run();
        else ownerTasks.add(task);
    }

    /**
     * 서명된 책 쪽 풀기가 바로 끝나지 않았을 때의 뒷부분(주인 스레드): 그 손에 아직 같은 책이 있으면 풀린 책으로 바꿔 알리고 open_book 을 보낸다.
     */
    public void finishBookOpen(int hand, ItemStack original, ItemStack resolved) {
        var inv = inventory();
        int ws = hand == 0 ? inv.heldWindowSlot() : 45;
        if (inv.get(ws) != original) return;   // replace 와 같은 기준(같은 객체)
        var host = inventoryHost();
        if (host != null) host.replace(ws, original, resolved);
        synchronized (menuLock()) { var m = containerMenu(); if (m != null) m.broadcastChanges(); }   // 칸 갱신 → open_book
        send(new org.domi.furnace.server.net.protocol.play.OpenBook(hand));
    }

    /**
     * 발전 과제 트리거를 플레이어 주인 스레드에서 부른다(발전 과제가 없는 서버면 아무것도 안 한다).
     * 호출 쪽 스레드에서만 볼 수 있는 값(상대 엔티티 등)은 람다 밖에서 {@code KillCredit.lootOf} 로 떠서 넘긴다.
     */
    public void triggerAdvancement(java.util.function.Consumer<org.domi.furnace.server.advancement.PlayerAdvancements> f) {
        if (advancements == null) return;
        runOnOwner(() -> {
            var a = advancements;
            if (a != null) f.accept(a);
        });
    }

    /** 지금 스레드가 이 플레이어를 가진 region 스레드인가. region 에 붙은 적 없는 플레이어(시험·단독 레벨)는 참으로 본다. */
    public boolean onOwnerThread() {
        var r = ownerRegion;
        return r == null || r.ownsPlayer(this);
    }

    /** 주인 region 이 플레이어 틱마다 부른다. */
    public void drainOwnerTasks() {
        Runnable t;
        while ((t = ownerTasks.poll()) != null) t.run();
        apiTasks.tick();   // 플러그인 EntityScheduler 작업
    }

    /** 바닐라 ServerPlayer.resetStat: 통계를 0 으로, 그 통계 기준 점수도 0. */
    public void resetStat(String stat) {
        var s = stats;
        if (s != null) s.set(stat, 0);
        var b = scoreboard;
        if (b != null) b.onStatReset(this, stat);
    }

    private volatile org.domi.furnace.server.scores.ServerScoreboard scoreboard;

    public org.domi.furnace.server.scores.ServerScoreboard scoreboard() { return scoreboard; }

    public void scoreboard(org.domi.furnace.server.scores.ServerScoreboard b) { this.scoreboard = b; }

    /** 바닐라 getTeam: 점수판 이름(접속 이름)이 든 팀(없으면 null). */
    public org.domi.furnace.server.scores.PlayerTeam team() {
        var b = scoreboard;
        return b == null || !b.hasTeams() ? null : b.getPlayersTeam(name);
    }

    /**
     * 바닐라 ServerPlayer.canHarmPlayer(other)/Player.canHarmPlayer: this 쪽 서버 설정 pvp 가 꺼져 있으면 거짓, 팀이 없으면 참,
     * 서로 다른 팀이면 참, 같은 팀이면 이 팀의 friendlyFire(아군 피해) 설정. 맞는 쪽·화살 주인·늑대 주인이 this 다.
     */
    public boolean canHarmPlayer(Player other, boolean pvp) {
        if (!pvp) return false;
        var mine = team();
        if (mine == null) return true;
        return !mine.isAlliedTo(other.team()) || mine.isAllowFriendlyFire();
    }

    /**
     * 바닐라 Player.getDisplayName(decorateDisplayNameComponent): 팀 서식(접두사·이름·접미사·색) + "/tell 이름 " 제안 클릭 + 엔티티 호버(show_entity:
     * minecraft:player, UUID, 이름) + 삽입 텍스트(이름).
     */
    public org.domi.furnace.api.text.Text displayName() {
        org.domi.furnace.api.text.Text n = org.domi.furnace.api.text.Text.text(name);
        return org.domi.furnace.server.scores.PlayerTeam.formatNameForTeam(team(), n)
                .clickEvent(new org.domi.furnace.api.text.ClickEvent(org.domi.furnace.api.text.ClickEvent.Action.SUGGEST_COMMAND, "/tell " + name + " "))
                .hoverEvent(org.domi.furnace.api.text.HoverEvent.showEntity("minecraft:player", uuid(), n))
                .insertion(name);
    }

    // 플러그인이 정한 표시 이름(Paper·Spigot setDisplayName) — 채팅 "<이름>" 에 쓴다. 접속을 끊으면 이 객체와 함께 사라진다.
    private volatile org.domi.furnace.api.text.Text customDisplayName;

    public org.domi.furnace.api.text.Text customDisplayName() { return customDisplayName; }

    public void customDisplayName(org.domi.furnace.api.text.Text name) { customDisplayName = name; }

    /** 채팅·말하기에 쓰는 이름: 플러그인이 정했으면 그것, 아니면 바닐라 getDisplayName. */
    public org.domi.furnace.api.text.Text chatDisplayName() {
        org.domi.furnace.api.text.Text c = customDisplayName;
        return c != null ? c : displayName();
    }

    // 플러그인 PlayerKickEvent 가 정한 퇴장 알림(없으면 평소대로). 끊긴 뒤 퇴장 처리에서 한 번 쓴다.
    private volatile boolean kickLeaveSet;
    private volatile org.domi.furnace.api.text.Text kickLeaveMessage;

    public void kickLeaveMessage(org.domi.furnace.api.text.Text message) {
        kickLeaveMessage = message;
        kickLeaveSet = true;
    }

    public boolean hasKickLeaveMessage() { return kickLeaveSet; }

    /** 정한 퇴장 알림, null 이면 알림 없음(hasKickLeaveMessage 가 참일 때만 뜻이 있다). */
    public org.domi.furnace.api.text.Text kickLeaveMessage() { return kickLeaveMessage; }

    // 리스폰할 때마다 증가 — 관찰자가 값이 바뀐 것을 보고 엔티티를 지웠다 다시 스폰한다(바닐라: 리스폰 = 새 ServerPlayer).
    private volatile int spawnGeneration;

    public int spawnGeneration() { return spawnGeneration; }

    public void bumpSpawnGeneration() { spawnGeneration++; }

    // 달리기·웅크리기(serverbound player_command). netty 가 쓰고 region 이 읽는다.
    private volatile boolean sprinting, sneaking;

    public boolean sprinting() { return sprinting; }
    public void sprinting(boolean v) { sprinting = v; }
    public boolean sneaking() { return sneaking; }
    public void sneaking(boolean v) { sneaking = v; }

    /**
     * 수락된 위치 패킷 하나(낙하·허기 소모 판정용). netty 가 넣고 소유 region 이 순서대로 꺼낸다.
     * teleport=true 는 서버가 위치를 정한 것(스폰·리스폰·텔레포트) — 이동량 없이 기준점만 옮긴다.
     */
    public record MoveSample(double x, double y, double z, boolean onGround, boolean teleport, PlayerMoveValidator.Check check) {
        public MoveSample(double x, double y, double z, boolean onGround, boolean teleport) { this(x, y, z, onGround, teleport, null); }
    }

    /**
     * 이동 패킷 검증 상태(ServerGamePacketListenerImpl 의 firstGood/lastGood·패킷 수·떠 있는 틱) — 소유 region 스레드 전용.
     */
    public final PlayerMoveValidator.State moveState = new PlayerMoveValidator.State();

    /** region 이 이동 패킷을 검증하는 중인가(소유 region 이 매 틱 켠다). 켜져 있으면 연결은 위치를 발행하지 않고 검증 대기열에만 넣는다. */
    public volatile boolean validatesMoves;

    /** 검증이 되돌림을 정한 뒤 연결이 텔레포트를 보내기 전까지의 사이 — 이 동안 들어온 검증 대기 패킷은 버린다. */
    public volatile boolean moveRejectHold;

    private final java.util.concurrent.ConcurrentLinkedQueue<MoveSample> moves = new java.util.concurrent.ConcurrentLinkedQueue<>();

    public void pushMove(double x, double y, double z, boolean onGround) {
        if (moves.size() < 256) moves.add(new MoveSample(x, y, z, onGround, false));
    }

    /** 검증 대기 이동 패킷(연결이 넣고 소유 region 이 순서대로 검증한다). */
    public void pushCheckedMove(PlayerMoveValidator.Check c, boolean onGround) {
        if (moves.size() < 256) moves.add(new MoveSample(c.x(), c.y(), c.z(), onGround, false, c));
    }

    public void pushTeleport(double x, double y, double z) {
        moves.add(new MoveSample(x, y, z, false, true));
    }

    // region 전용: 마지막으로 처리한 이동 샘플(낙하·소모 계산의 기준점). null = 아직 없음.
    private MoveSample moveAnchor;

    public MoveSample moveAnchor() { return moveAnchor; }

    public void moveAnchor(MoveSample m) { moveAnchor = m; }

    public MoveSample pollMove() { return moves.poll(); }

    /** 이동 대기열을 비운다. 서버가 위치를 정하는 텔레포트의 첫 단계라, 검증이 정한 되돌림 대기(moveRejectHold)도 여기서 푼다. */
    public void clearMoves() {
        moves.clear();
        moveRejectHold = false;
    }

    /** 이 플레이어에게 서버 텔레포트를 시킬 연결이 있는가. */
    public boolean hasTeleporter() { return teleporter != null; }

    // 클라이언트 언어(client_information, 소문자 en_us 형식).
    private volatile String locale = "en_us";

    public String locale() { return locale; }

    public void locale(String l) {
        String v = l == null || l.isEmpty() ? "en_us" : l;
        this.javaLocale = java.util.Locale.forLanguageTag(v.replace('_', '-'));
        this.locale = v;
    }

    private volatile java.util.Locale javaLocale = java.util.Locale.US;

    /** 클라이언트 언어(Java Locale). */
    public java.util.Locale javaLocale() { return javaLocale; }

    /**
     * 플러그인 번역 저장소(GlobalRenderer)가 아는 키를 이 플레이어 언어로 펼친다(Paper 의 GlobalTranslator 렌더링).
     * 저장소가 없거나 바뀐 곳이 없으면 같은 인스턴스 — 인코딩 캐시가 그대로 쓰인다.
     */
    public org.domi.furnace.api.text.Text localize(org.domi.furnace.api.text.Text t) {
        return org.domi.furnace.api.text.translation.GlobalRenderer.render(t, javaLocale);
    }

    // 플러그인 API 손잡이(처음 요청할 때 만든다).
    private volatile Object apiHandle;

    public Object apiHandle() { return apiHandle; }

    public void apiHandle(Object h) { this.apiHandle = h; }

    // 플러그인이 보는 연결(접속 단계 이벤트와 같은 객체). 연결 없이 만든 시험용 Player 는 null.
    private volatile org.domi.furnace.api.net.PlayerConnection connection;

    public org.domi.furnace.api.net.PlayerConnection connection() { return connection; }

    public void connection(org.domi.furnace.api.net.PlayerConnection c) { this.connection = c; }

    // 실제 접속 주소(Velocity 포워딩이면 프록시가 알려 준 주소).
    private volatile java.net.SocketAddress remoteAddress;

    public void remoteAddress(java.net.SocketAddress a) { this.remoteAddress = a; }

    public java.net.SocketAddress remoteAddress() {
        java.net.SocketAddress a = remoteAddress;
        return a != null ? a : channel.remoteAddress();
    }

    /** 바닐라 ServerPlayer.getIpAddress — 포트 없는 IP 문자열. */
    public String ipAddress() {
        java.net.SocketAddress a = remoteAddress();
        if (a instanceof java.net.InetSocketAddress isa && isa.getAddress() != null) return isa.getAddress().getHostAddress();
        return "<unknown>";
    }

    // 채팅 상태(서명 세션·last seen·시그니처 캐시·채팅 표시 설정). 연결이 만들 때 정한다.
    private volatile org.domi.furnace.server.chat.PlayerChatState chat;

    public org.domi.furnace.server.chat.PlayerChatState chat() {
        org.domi.furnace.server.chat.PlayerChatState c = chat;
        if (c == null) {
            synchronized (this) {
                if (chat == null) chat = new org.domi.furnace.server.chat.PlayerChatState(uuid, () -> false);
                c = chat;
            }
        }
        return c;
    }

    public void chat(org.domi.furnace.server.chat.PlayerChatState c) { this.chat = c; }

    /** 바닐라 ServerPlayer.sendSystemMessage — 채팅 숨김이면 액션바(overlay)만 받는다. */
    public void sendSystemMessage(org.domi.furnace.api.text.Text message, boolean overlay) {
        if (chat().acceptsSystemMessages(overlay)) send(new org.domi.furnace.server.net.protocol.play.SystemChat(localize(message), overlay));
    }

    // 클라이언트 설정 allowServerListings — 거부하면 상태 응답 sample 에 익명으로 나온다.
    private volatile boolean allowsListing = true;

    public boolean allowsListing() { return allowsListing; }

    public void allowsListing(boolean v) { this.allowsListing = v; }

    public void profileProperties(java.util.List<org.domi.furnace.server.players.GameProfile.Property> props) {
        this.profileProperties = java.util.List.copyOf(props);
    }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public boolean onGround() { return onGround; }

    // ---- 지도(MapData.Viewer)
    @Override public boolean removed() { return disconnected; }

    // 지금 있는 차원(레벨 키). 차원 이동 때 연결 스레드가 바꾸고 어디서나 읽는다.
    private volatile String dimension = "minecraft:overworld";

    @Override public String dimension() { return dimension; }

    public void dimension(String d) { this.dimension = d; }

    /** Inventory.contains(predicate): 칸 전체(주·핫바·갑옷·왼손 — 제작 칸 제외). */
    @Override public boolean holdsMap(java.util.function.Predicate<ItemStack> predicate) {
        for (int slot = 5; slot < PlayerInventory.SIZE; slot++) {
            ItemStack s = inventory.get(slot);
            if (!s.isEmpty() && predicate.test(s)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ 관전 시점(/spectate, 관전자의 왼쪽 클릭) — 바닐라 ServerPlayer.camera

    /** 카메라 대상. null 이면 자기 자신. 바꾸는 쪽은 이 플레이어의 소유 region 스레드뿐이고, 다른 스레드는 읽기만 한다. */
    private volatile Rider camera;

    /** 바닐라 getCamera: 카메라 대상(자기 자신이면 this). */
    public Rider cameraOrSelf() { Rider c = camera; return c == null ? this : c; }

    /** 자기 자신 외의 카메라 대상(없으면 null). */
    public Rider cameraTarget() { return camera; }

    /** 카메라 대상의 한 시점 표본(대상의 주인 스레드가 한 번에 뜬다). */
    public record CameraView(Rider target, double x, double y, double z, float yaw, float pitch, boolean alive) { }

    private volatile CameraView cameraView;
    /** 비소유 대상 표본을 갱신하는 요청이 진행 중인가 / 연속으로 대상을 못 찾은 횟수(소유 region 틱이 쓴다). */
    public final java.util.concurrent.atomic.AtomicBoolean cameraRefreshing = new java.util.concurrent.atomic.AtomicBoolean();
    public final java.util.concurrent.atomic.AtomicInteger cameraMisses = new java.util.concurrent.atomic.AtomicInteger();

    public CameraView cameraView() { return cameraView; }

    public void cameraView(CameraView v) { this.cameraView = v; }

    /** Region.setCamera 만 부른다. null 이면 자기 자신으로 되돌린다. */
    public void cameraField(Rider target) { this.camera = target; }

    private volatile java.util.function.Consumer<double[]> teleporter;

    public void teleporter(java.util.function.Consumer<double[]> t) { this.teleporter = t; }

    /** 서버가 플레이어를 (x,y,z)로 옮긴다 — 연결의 IO 스레드가 보정 텔레포트를 보내고 이동 대기열을 비운다. */
    public void teleportTo(double x, double y, double z) {
        if (!vitals().env().gameEvents().playerTeleport(this, x, y, z)) return;   // 플러그인 PlayerTeleportEvent 취소
        if (wakeThen(() -> teleportTo0(x, y, z))) return;
        teleportTo0(x, y, z);
    }

    private void teleportTo0(double x, double y, double z) {
        var t = teleporter;
        if (t != null) t.accept(new double[] {x, y, z});
    }

    /**
     * ServerPlayer.teleportTo(level, …) 의 첫머리(isSleeping → stopSleepInBed(true, true)): 자고 있으면 깨운 뒤에 then 을 한다. 깨우기는
     * 침대 칸·일어설 자리를 읽고 차원 잠 목록을 바꾸므로 소유 region 스레드에서 해야 한다 — 다른 스레드(플러그인·연결)가 불렀으면 소유
     * 스레드 큐에 깨우기와 then 을 차례로 넣는다(깨우기가 일어설 자리로 옮기는 보정이 목적지를 덮어쓰지 않게). 자고 있지 않으면 거짓
     * (호출한 쪽이 then 을 직접 한다).
     */
    private boolean wakeThen(Runnable then) {
        if (sleepingPos == null) return false;
        runOnOwner(() -> {
            if (sleepingPos != null) PlayerSleeping.stopSleepInBed(this, currentLevel, true, true);
            then.run();
        });
        return true;
    }

    /**
     * ServerPlayer.teleportRelative(dx, dy, dz): connection.teleport(x + dx, …, RelativeMovement.ALL) — 다섯 축 모두 상대값으로 보내
     * 클라이언트가 자기 위치에 dx·dy·dz 를 더한다(회전은 0 을 더한다). Paper 처럼 PlayerTeleportEvent 를 거친다.
     */
    public void teleportRelative(double dx, double dy, double dz) {
        double nx = x() + dx, ny = y() + dy, nz = z() + dz;
        if (!vitals().env().gameEvents().playerTeleport(this, nx, ny, nz)) return;
        if (wakeThen(() -> teleportRelative0(dx, dy, dz))) return;
        teleportRelative0(dx, dy, dz);
    }

    /** 깬 뒤의 위치(일어설 자리)에서 상대 이동을 더한다 — 자고 있던 중이면 침대 위가 아니라 일어선 자리 기준. */
    private void teleportRelative0(double dx, double dy, double dz) {
        var t = teleporter;
        if (t != null) t.accept(new double[] {x() + dx, y() + dy, z() + dz, Double.NaN, Double.NaN, dx, dy, dz});
    }

    /** connection.teleport(x, y, z, yRot, xRot): 회전까지 정해 보정 텔레포트. */
    public void teleportTo(double x, double y, double z, float yaw, float pitch) {
        if (!vitals().env().gameEvents().playerTeleport(this, x, y, z, yaw, pitch)) return;   // 플러그인 PlayerTeleportEvent 취소
        if (wakeThen(() -> correctTo(x, y, z, yaw, pitch))) return;
        correctTo(x, y, z, yaw, pitch);
    }

    /**
     * 플러그인 teleport 의 결과 규칙(Paper CraftPlayer#teleport): 연결이 없거나 끊겼거나 죽었으면, PlayerTeleportEvent 가 취소됐으면 false —
     * 아무것도 바뀌지 않는다. 그 밖에는 탈것을 타고 있으면 먼저 내리고(깨어 있어야 하니 자던 중이면 깨운 뒤) 보정 텔레포트를 보내
     * true. 결과는 이벤트가 정하므로 어느 스레드에서나 부를 수 있고, 내리기·깨우기는 소유 region 스레드로 넘어간다.
     */
    public boolean pluginTeleport(double x, double y, double z, float yaw, float pitch) {
        if (isDisconnected() || !hasTeleporter() || vitals().health() <= 0.0F) return false;   // 죽은 플레이어는 이벤트 없이 false(CraftPlayer#teleport)
        if (!vitals().env().gameEvents().playerTeleport(this, x, y, z, yaw, pitch)) return false;
        // CraftPlayer#teleport: 이벤트가 통과한 뒤 열린 창(상자·플러그인 메뉴 등)을 TELEPORT 사유로 닫는다 — 바닐라 이동 경로는 닫지 않는다.
        var ih = inventoryHost();
        var open = containerMenu;
        if (ih != null && hasOpenContainer()) ih.closeContainerFromAnyThread(open);
        Runnable go = () -> {
            if (vehicle != null) stopRiding();
            correctTo(x, y, z, yaw, pitch);
        };
        if (vehicle != null) runOnOwner(go);
        else if (!wakeThen(go)) go.run();
        return true;
    }

    // PlayerMoveEvent 용 "마지막으로 알린 자리"(CraftBukkit lastPosX·lastYaw 등) — 소유 region 스레드 전용(Region.processMoves).
    private boolean moveEventBaseSet;
    private double moveEventX, moveEventY, moveEventZ;
    private float moveEventYaw, moveEventPitch;

    /** 이동 이벤트의 기준이 있는가(텔레포트·접속 직후에는 없다 — 다음 이동 패킷이 그 자리를 기준으로 잡는다). */
    public boolean moveEventBaseSet() { return moveEventBaseSet; }

    public double moveEventX() { return moveEventX; }
    public double moveEventY() { return moveEventY; }
    public double moveEventZ() { return moveEventZ; }
    public float moveEventYaw() { return moveEventYaw; }
    public float moveEventPitch() { return moveEventPitch; }

    public void moveEventBase(double x, double y, double z, float yaw, float pitch) {
        moveEventBaseSet = true;
        moveEventX = x;
        moveEventY = y;
        moveEventZ = z;
        moveEventYaw = yaw;
        moveEventPitch = pitch;
    }

    public void clearMoveEventBase() { moveEventBaseSet = false; }

    /** 서버 보정 텔레포트(이동 검증 되돌림 등) — 플러그인 이벤트를 거치지 않는다(취소되면 클라이언트와 위치가 영구히 어긋난다). */
    public void correctTo(double x, double y, double z, float yaw, float pitch) {
        var t = teleporter;
        if (t != null) t.accept(new double[] {x, y, z, yaw, pitch});
    }

    // ------------------------------------------------------------------ 포털·차원 이동(region 스레드 전용 — 옮기는 동안은 어느 region 에도 없다)

    /** Player.getDimensionChangingDelay: 넘어간 뒤 포털을 다시 못 쓰는 틱. */
    public static final int DIMENSION_CHANGING_DELAY = 10;
    public int portalCooldown;
    private PortalProcessor portalProcess;
    /** ServerPlayer.seenCredits(저장)·wonGame(크레딧 화면 중). */
    public boolean seenCredits;
    public volatile boolean wonGame;

    public PortalProcessor portalProcess() { return portalProcess; }

    public void portalProcess(PortalProcessor p) { this.portalProcess = p; }

    public boolean isOnPortalCooldown() { return portalCooldown > 0; }

    /** Entity.setAsInsidePortal: 대기 중이면 대기만 다시 채우고, 같은 포털이면 위치·이번 틱 표시를 갱신, 아니면 새로 시작. */
    public void setAsInsidePortal(Portal portal, org.domi.furnace.server.block.Pos pos) {
        if (isOnPortalCooldown()) {
            portalCooldown = DIMENSION_CHANGING_DELAY;
        } else if (portalProcess != null && portalProcess.isSamePortal(portal)) {
            portalProcess.updateEntryPosition(pos);
            portalProcess.setAsInsidePortalThisTick(true);
        } else {
            portalProcess = new PortalProcessor(portal, pos);
        }
    }

    /**
     * 탈것과 함께 포털을 건넌 승객이 목적 region 에 편입되면 다시 탈 탈것(바닐라 changeDimension 끝의 startRiding(새 탈것, true)).
     * 떠난 쪽 region 이 쓰고 편입하는 region 이 한 번 꺼낸다(그동안 플레이어는 어느 region 에도 없다).
     */
    private volatile Entity carriedVehicle;

    public void carriedVehicle(Entity v) { this.carriedVehicle = v; }

    /** 다시 탈 탈것을 꺼내고 지운다(없으면 null). */
    public Entity takeCarriedVehicle() {
        Entity v = carriedVehicle;
        carriedVehicle = null;
        return v;
    }

    /** 연결 스레드 실행기(텔레포트와 같은 순서로 일을 넣는다). 연결이 없으면(시험) null. */
    private volatile java.util.function.Consumer<Runnable> connectionTasks;

    public void connectionTasks(java.util.function.Consumer<Runnable> c) { this.connectionTasks = c; }

    /**
     * 보정 텔레포트(correctTo — 이동 대기열을 비우고 위치를 보낸다)를 보내고, 연결 스레드가 그것을 처리한 뒤에 then 을 연결 스레드에서 한다
     * (같은 실행기라 앞서 넣은 텔레포트가 먼저 끝난다). 연결이 없으면 아무것도 하지 않고 거짓.
     */
    public boolean correctToThen(double x, double y, double z, float yaw, float pitch, Runnable then) {
        var t = teleporter;
        var c = connectionTasks;
        if (t == null || c == null) return false;
        t.accept(new double[] {x, y, z, yaw, pitch});
        c.accept(then);
        return true;
    }

    /** 차원 이동 도착(목적 월드, 목적지, 그때의 포털 대기) — 연결이 패킷을 보내고 목적 region 에 넣는다. */
    public interface DimensionChanger {
        void change(org.domi.furnace.server.world.World target, DimensionTransition t, int portalCooldown);

        /** 편입 넘기기판: 연결은 패킷을 다 보낸 뒤 handoff.sent 로 편입을 맡긴다. 기본은 넘기기 없이(시험용 기록기). */
        default void change(org.domi.furnace.server.world.World target, DimensionTransition t, int portalCooldown, ArrivalHandoff handoff) {
            handoff.detach();
            change(target, t, portalCooldown);
        }
    }

    private volatile DimensionChanger dimensionChanger;

    public void dimensionChanger(DimensionChanger c) { this.dimensionChanger = c; }

    /** 목적 region 이 연결의 패킷 보내기를 기다리는 최대 시간(ns). 넘으면 예전처럼 연결이 편입 작업을 보낸다(한 틱 늦게 들어간다). */
    static final long ARRIVAL_WAIT_NANOS = Long.getLong("furnace.dimensionArrivalWaitMicros", 2_000L) * 1_000L;

    /**
     * 목적 region 스레드에서: 연결 스레드로 넘긴다(연결이 없으면 거짓). 연결이 리스폰 패킷들을 보내는 동안 잠깐(ARRIVAL_WAIT_NANOS) 기다렸다가
     * 이 스레드가 그 칸 주인 region 이고 틱 사이면 그 자리에서 편입한다 — 바닐라처럼 넘어간 그 틱에 새 차원에 있다(M10). 아니면 연결이 편입 작업을 보낸다.
     */
    public boolean changeDimension(org.domi.furnace.server.world.World target, DimensionTransition t, int portalCooldown) {
        var c = dimensionChanger;
        if (c == null) return false;
        ArrivalHandoff h = new ArrivalHandoff();
        c.change(target, t, portalCooldown, h);
        h.awaitInline(ARRIVAL_WAIT_NANOS);
        return true;
    }

    /**
     * 차원 이동 도착 편입 넘기기(목적 region 스레드 ↔ 연결 스레드). 상태 0 기다림, 1 연결이 보냄(기다리던 쪽이 편입), 2 기다리지 않음(연결이 편입 작업을 보낸다).
     * 둘 중 정확히 하나만 편입한다.
     */
    public static final class ArrivalHandoff {
        private final java.util.concurrent.atomic.AtomicInteger state = new java.util.concurrent.atomic.AtomicInteger();
        private volatile Thread waiter;
        private volatile org.domi.furnace.server.world.World world;
        private volatile double x, z;
        private volatile java.util.function.Consumer<org.domi.furnace.server.world.Region> adopt;

        /** 기다리는 쪽이 없다(연결 스레드가 편입 작업을 보낸다). */
        public void detach() {
            if (state.compareAndSet(0, 2)) {
                Thread t = waiter;
                if (t != null) java.util.concurrent.locks.LockSupport.unpark(t);   // 기다리던 쪽을 바로 놓아준다
            }
        }

        /** 연결 스레드: 패킷을 다 보냈다 — 기다리는 쪽이 있으면 넘기고, 없으면 그 칸 주인 region 에 편입 작업을 보낸다. */
        public void sent(org.domi.furnace.server.world.World w, double x, double z, java.util.function.Consumer<org.domi.furnace.server.world.Region> adopt) {
            this.world = w;
            this.x = x;
            this.z = z;
            this.adopt = adopt;
            if (state.compareAndSet(0, 1)) {
                Thread t = waiter;
                if (t != null) java.util.concurrent.locks.LockSupport.unpark(t);
            } else {
                w.runAt(x, z, adopt);
            }
        }

        /** 목적 region 스레드: nanos 까지 기다린다. 연결이 보냈으면 지금 스레드가 그 칸 주인 region(틱 사이)일 때 그 자리에서, 아니면 그 region 에 넘긴다. */
        void awaitInline(long nanos) {
            if (state.get() == 0 && nanos > 0) {
                waiter = Thread.currentThread();
                long deadline = System.nanoTime() + nanos;
                while (state.get() == 0) {   // 1(보냄) 또는 2(detach — 편입 없음)가 되면 끝
                    long left = deadline - System.nanoTime();
                    if (left <= 0) break;
                    java.util.concurrent.locks.LockSupport.parkNanos(this, left);
                }
                waiter = null;
            }
            if (state.compareAndSet(0, 2)) return;   // 기다림을 넘겼다 — 연결이 보낼 때 편입 작업을 보낸다
            if (state.get() != 1) return;   // detach — 편입하지 않는다
            var w = world;
            ChunkPos cp = ChunkPos.ofBlock(x, z);
            var r = w.regionizer().ownerOf(cp.x(), cp.z());
            if (r != null && r.isOwnerThread() && r.betweenTicks()) {
                try {
                    adopt.accept(r);
                } catch (RuntimeException e) {   // 편입 실패가 차원 이동 콜백(되돌리기 판단) 위로 올라가지 않게
                    org.slf4j.LoggerFactory.getLogger(Player.class).error("[PORTAL] 도착 편입 실패", e);
                }
            } else {
                w.runAt(x, z, adopt);
            }
        }
    }

    /** 마지막으로 받은 이동 패킷의 이동량(블록/틱) — 던지기 속도에 더해진다(바닐라 getKnownMovement). */
    public double[] knownMovement() { return new double[] {moveDx, moveDy, moveDz}; }

    // ------------------------------------------------------------------ 아이템 재사용 대기(ItemCooldowns)

    private final java.util.concurrent.ConcurrentHashMap<Integer, int[]> cooldowns = new java.util.concurrent.ConcurrentHashMap<>();   // 아이템 → {시작, 끝} 틱
    private volatile int cooldownTick;

    public boolean isOnCooldown(int itemId) {
        int[] c = cooldowns.get(itemId);
        return c != null && c[1] > cooldownTick;
    }

    /** ItemCooldowns.addCooldown: 그 아이템을 ticks 동안 못 쓰게 하고 클라이언트에 알린다. */
    public void addCooldown(int itemId, int ticks) {
        cooldowns.put(itemId, new int[] {cooldownTick, cooldownTick + ticks});
        send(new org.domi.furnace.server.net.protocol.play.Cooldown(itemId, ticks));
    }

    /** ItemCooldowns.tick: 틱마다 한 칸 가고, 끝난 것은 지우며 끝났다고 알린다. */
    public void tickCooldowns() {
        int now = ++cooldownTick;
        if (cooldowns.isEmpty()) return;
        for (var it = cooldowns.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (e.getValue()[1] <= now) {
                it.remove();
                send(new org.domi.furnace.server.net.protocol.play.Cooldown(e.getKey(), 0));
            }
        }
    }
    private ChunkPos queuedCenter;               // 시야 청크를 대기열에 올린 마지막 중심(null=아직 안 함)
    public ChunkPos lastCenter() { return lastCenter; }
    public ChunkPos queuedCenter() { return queuedCenter; }
    public void queuedCenter(ChunkPos c) { this.queuedCenter = c; }
    public void lastCenter(ChunkPos c) { this.lastCenter = c; }
    private int trackedViewDistance = -1;        // 시야 청크를 마지막으로 잡을 때 쓴 거리(-1=아직 안 함). region 스레드 전용
    /** 바닐라 ChunkTrackingView.Positioned.viewDistance — 지금 추적 범위가 쓰는 거리. */
    public int trackedViewDistance() { return trackedViewDistance; }
    public void trackedViewDistance(int vd) { this.trackedViewDistance = vd; }
    public Set<ChunkPos> loadedChunks() { return loadedChunks; }
    public Set<ChunkPos> pendingChunks() { return pendingChunks; }

    /** 요청했지만 아직 끝나지 않은(생성·직렬화 중인) 청크 전송 수 — 완료 콜백은 다른 스레드서 줄인다. */
    private final java.util.concurrent.atomic.AtomicInteger chunkSendsInFlight = new java.util.concurrent.atomic.AtomicInteger();

    public java.util.concurrent.atomic.AtomicInteger chunkSendsInFlight() { return chunkSendsInFlight; }

    /** chunks-per-tick=auto: 이 플레이어 때문에 생성·디스크 로드를 걸어 둔(아직 안 끝난) 청크(아무 스레드). */
    private final java.util.Set<org.domi.furnace.server.world.ChunkPos> chunkPrefetches = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public java.util.Set<org.domi.furnace.server.world.ChunkPos> chunkPrefetches() { return chunkPrefetches; }

    /** chunks-per-tick=auto: 보낸 배치의 Finished 가 아직 안 나갔다(아무 스레드). */
    private volatile boolean chunkBatchOpen;

    public boolean chunkBatchOpen() { return chunkBatchOpen; }

    public void chunkBatchOpen(boolean open) { this.chunkBatchOpen = open; }

    private final org.domi.furnace.server.world.ChunkSendRate chunkSendRate = new org.domi.furnace.server.world.ChunkSendRate();

    /** 클라이언트가 알려 주는 틱당 청크 수에 맞춘 전송 속도(바닐라 PlayerChunkSender). */
    public org.domi.furnace.server.world.ChunkSendRate chunkSendRate() { return chunkSendRate; }

    private volatile String clientBrand;

    /** 클라이언트가 minecraft:brand 로 알린 이름(없으면 null). */
    public String clientBrand() { return clientBrand; }

    public void clientBrand(String brand) { this.clientBrand = brand; }

    private volatile org.domi.furnace.server.net.PluginChannels pluginChannels = new org.domi.furnace.server.net.PluginChannels();

    /** 클라이언트가 minecraft:register 로 알린 채널(연결과 같은 객체 — 접속 뒤 등록·해제도 반영된다). */
    public org.domi.furnace.server.net.PluginChannels pluginChannels() { return pluginChannels; }

    public void pluginChannels(org.domi.furnace.server.net.PluginChannels channels) { this.pluginChannels = channels; }

    /** 바닐라 ServerPlayer.lastActionTime(ms) — 이동 말고 행동(명령·채팅·사용·클릭 등)이 있을 때 갱신한다. player-idle-timeout 기준. */
    private volatile long lastActionTime = System.currentTimeMillis();

    public long lastActionTime() { return lastActionTime; }

    public void resetLastActionTime() { this.lastActionTime = System.currentTimeMillis(); }
    public Map<Integer, double[]> tracked() { return tracked; }
    public Map<Integer, Integer> missingTicks() { return missingTicks; }
    public Map<Integer, UUID> listed() { return listed; }

    private volatile int latencyMs;
    /** 탭 목록에 표시되는 핑(ms). KeepAlive 왕복으로 갱신. */
    public int latencyMs() { return latencyMs; }
    /** 바닐라와 같은 평활화: (이전*3 + 새값)/4 — 초기값 0 에서 시작하므로 첫 표본도 1/4 만 들어간다. */
    public void recordLatency(int sampleMs) {
        latencyMs = (latencyMs * 3 + sampleMs) / 4;
    }

    private final EnderChestInventory enderChest = new EnderChestInventory();

    /** 플레이어의 엔더 상자 27칸(어느 엔더 상자를 열든 같다). */
    public EnderChestInventory enderChest() { return enderChest; }

    public org.domi.furnace.server.menu.InventoryMenu inventoryMenu() { return inventoryMenu; }

    /** 지금 열린 창(없으면 인벤토리 창). 메뉴가 만들어지기 전(시험용 Player)에는 null. */
    public org.domi.furnace.server.menu.AbstractContainerMenu containerMenu() { return containerMenu; }

    /** 접속 때 인벤토리 창을 만들어 건다. */
    public void initMenus(org.domi.furnace.server.menu.InventoryMenu menu) {
        this.inventoryMenu = menu;
        this.containerMenu = menu;
    }

    public void setContainerMenu(org.domi.furnace.server.menu.AbstractContainerMenu menu) { this.containerMenu = menu; }

    /** 열린 창이 있는가(인벤토리 창이 아닌). */
    public boolean hasOpenContainer() { return containerMenu != null && containerMenu != inventoryMenu; }

    /** ServerPlayer.nextContainerCounter: 1..100 순환(0 은 인벤토리 창). */
    public int nextContainerCounter() {
        containerCounter = containerCounter % 100 + 1;
        return containerCounter;
    }

    /** 메뉴 상태(remote·stateId·커서)를 지키는 모니터 — 연결 스레드(클릭)와 region 스레드(틱 동기·줍기)가 함께 쓴다. */
    public Object menuLock() { return inventory; }
}
