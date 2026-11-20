package net.minecraft.world.entity;

import java.util.UUID;
import net.minecraft.world.level.Level;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.domi.furnace.compat.nms.UnsupportedNmsCall;

/**
 * 엔티티 shim(Mojang 이름, H6 최소 표면): {@code CraftEntity#getHandle()} 이 돌려준다. 서버 엔티티 객체가 아니라 그 Bukkit 객체를 거쳐
 * Furnace 엔티티 핸들(만든 시점의 스냅샷)을 읽는다 — 위치는 그 스냅샷 값이다. 같은 엔티티는 같은 shim 이다(CraftEntity 마다 하나).
 * 바닐라처럼 equals 는 같은 엔티티인가(바닐라는 네트워크 id, 여기는 UUID)다.
 */
public class Entity implements net.minecraft.network.syncher.SyncedDataHolder, net.minecraft.world.level.entity.EntityAccess {

    private static final System.Logger LOG = System.getLogger("FurnaceCompat");

    /** 월드의 엔티티면 그 Bukkit 객체. {@link EntityType#create} 로 만들어 아직 월드에 없으면 null — 넣는 순간 묶인다. */
    private volatile CraftEntity bukkit;
    /** 아직 월드에 없는 엔티티의 상태(T-212). 월드에 넣으면 null. */
    private volatile Detached detached;

    /** Paper 필드: 월드 생성 중에 넣는 엔티티인가(이벤트를 미룬다). 넣을 때 거짓이 된다(Paper addEntity 와 같다). */
    public boolean generation;
    /** Paper 필드: 스폰 이유. NMS 로 만든 엔티티를 넣을 때 엔티티에 붙는다. 월드의 엔티티 shim 에서는 읽어도 null 이다(채워 두지 않는다). */
    public org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason spawnReason;

    /** 호환 모듈 전용(CraftEntity 가 만든다). 플러그인이 상속해 새 엔티티를 만드는 것은 지원하지 않는다(설계 등급 C). */
    public Entity(CraftEntity bukkit) { this.bukkit = java.util.Objects.requireNonNull(bukkit, "bukkit"); }

    /** {@link EntityType#create} 전용: 아직 월드에 없는 엔티티(바닐라 생성자처럼 자리 0,0,0·방향 0). */
    Entity(EntityType<?> type, Level level) { this.detached = new Detached(type, level); }

    /** CraftBukkit 이 더한 메서드: 이 엔티티의 Bukkit 객체. 아직 월드에 넣지 않은 엔티티는 UnsupportedNmsCall. */
    public CraftEntity getBukkitEntity() {
        CraftEntity b = bukkit;
        if (b == null) throw notInWorld("Entity#getBukkitEntity");
        return b;
    }

    private org.domi.furnace.api.entity.Entity f() { return getBukkitEntity().furnaceHandle(); }

    private static UnsupportedNmsCall notInWorld(String what) {
        return new UnsupportedNmsCall(what, "the entity was made with EntityType#create and is not in a world yet - Furnace makes the server entity when"
                + " it is added (ServerLevel#addFreshEntity)");
    }

    public UUID getUUID() {
        Detached d = detached;
        if (d != null) {
            if (d.uuid == null) {
                throw new UnsupportedNmsCall("Entity#getUUID", "the random UUID of an entity made with EntityType#create is chosen when it is added to a world"
                        + " - call setUUID or load a tag with \"UUID\" first");
            }
            return d.uuid;
        }
        return f().uniqueId();
    }

    public String getStringUUID() { return getUUID().toString(); }

    /** 바닐라 {@code level().getServer()} — Furnace 의 월드는 모두 이 서버의 것이다(월드에 아직 없는 엔티티도). */
    public net.minecraft.server.MinecraftServer getServer() { return net.minecraft.server.MinecraftServer.getServer(); }

    public double getX() { Detached d = detached; return d != null ? d.x : f().location().x(); }

    public double getY() { Detached d = detached; return d != null ? d.y : f().location().y(); }

    public double getZ() { Detached d = detached; return d != null ? d.z : f().location().z(); }

    public float getYRot() { Detached d = detached; return d != null ? d.yRot : f().location().yaw(); }

    public float getXRot() { Detached d = detached; return d != null ? d.xRot : f().location().pitch(); }

    /** 바닐라 Entity#getYHeadRot: 살아 있는 엔티티의 머리 방향(그 밖은 0). */
    public float getYHeadRot() { return f() instanceof org.domi.furnace.api.entity.LivingEntity ? f().headYaw() : 0.0F; }

    /** CraftBukkit 이 더한 메서드: Bukkit Location 의 yaw 가 되는 값(살아 있는 엔티티는 머리 방향, 그 밖은 몸 방향). */
    public float getBukkitYaw() { return f().headYaw(); }

    /** 바닐라: 위치(스냅샷 값). */
    public net.minecraft.world.phys.Vec3 position() { return new net.minecraft.world.phys.Vec3(getX(), getY(), getZ()); }

    /** 바닐라: 발 밑 칸 좌표(각 축 내림). */
    public net.minecraft.core.BlockPos blockPosition() { return net.minecraft.core.BlockPos.containing(getX(), getY(), getZ()); }

    /** 바닐라: 있는 청크 좌표. */
    public net.minecraft.world.level.ChunkPos chunkPosition() { return new net.minecraft.world.level.ChunkPos(blockPosition()); }

    /** 바닐라: 엔티티 종류(키로 같은 객체). */
    public EntityType<?> getType() {
        Detached d = detached;
        return d != null ? d.type : EntityType.of(net.minecraft.resources.ResourceLocation.parse(f().type()));
    }

    /** 이 엔티티가 있는 월드의 shim(아직 월드에 없으면 만들 때 준 레벨). */
    public Level level() {
        Detached d = detached;
        return d != null ? d.level : ((CraftWorld) getBukkitEntity().getWorld()).getHandle();
    }

    /** 바닐라: 지워졌는가(스냅샷 시점, 이 shim 으로 {@link #discard} 했으면 참). */
    public boolean isRemoved() {
        Detached d = detached;
        return d != null ? d.removed : discarded || !f().isValid();
    }

    /** 이 shim 의 {@link #discard} 가 엔티티를 지웠다(스냅샷은 그 뒤에도 살아 있는 값이다). */
    private volatile boolean discarded;

    private org.domi.furnace.api.world.World furnaceWorld() { return ((CraftWorld) getBukkitEntity().getWorld()).furnaceHandle(); }

    /**
     * 바닐라 {@code Entity#save}(T-212): 그 엔티티를 가진 스레드에서 서버가 저장한 NBT("id" 포함, 승객까지)를 tag 에 넣고 참. 승객·플레이어·저장하지 않는
     * 상태·이미 사라진 엔티티는 거짓(tag 는 그대로). 스레드 규칙은 {@link org.domi.furnace.compat.nms.NmsEntities}(FAWE 작업 스레드는 주인을 기다린다).
     * 내용은 서버가 월드에 저장하는 꼴과 같다 — Paper 가 더하는 Bukkit·Spigot·Paper 키(WorldUUID·Spigot.ticksLived·Paper.Origin 등)는 없다.
     */
    public boolean save(net.minecraft.nbt.CompoundTag tag) {
        if (detached != null) throw notInWorld("Entity#save");
        var h = f();
        if (h instanceof org.domi.furnace.api.entity.Player) return false;   // 플레이어는 저장하지 않는 종류(바닐라 getEncodeId null)
        var b = org.domi.furnace.compat.nms.NmsChunks.bridge();
        org.domi.furnace.api.nbt.NbtCompound saved = org.domi.furnace.compat.nms.NmsEntities.call(furnaceWorld(), h, "Entity#save", () -> b.entitySaveNow(h));
        if (saved == null) return false;
        net.minecraft.nbt.CompoundTag t = net.minecraft.nbt.CompoundTag.fromFurnace(saved);
        for (String k : t.getAllKeys()) tag.put(k, t.get(k));
        return true;
    }

    /**
     * 바닐라 {@code Entity#discard}(T-212): 지움 이유 DISCARDED 로 지운다 — Paper 도 원인 없는 discard 에는 EntityRemoveEvent 를 내지 않는다. 이미 사라졌으면
     * 아무 일 없다. 스레드 규칙은 {@link #save} 와 같다(FAWE 는 동기 작업 = 전역 스레드의 빌림 구간에서 부른다).
     */
    public void discard() {
        Detached d = detached;
        if (d != null) {   // 바닐라: 넣기 전에 지운 엔티티는 addFreshEntity 가 거짓
            d.removed = true;
            return;
        }
        var h = f();
        if (h instanceof org.domi.furnace.api.entity.Player) {
            throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("Entity#discard", "discarding a player entity is not supported - kick the player instead");
        }
        var b = org.domi.furnace.compat.nms.NmsChunks.bridge();
        org.domi.furnace.compat.nms.NmsEntities.call(furnaceWorld(), h, "Entity#discard", () -> {
            b.entityDiscardNow(h);
            return null;
        });
        discarded = true;
    }

    /** 바닐라 Entity: 지워지지 않았는가. LivingEntity 는 체력도 본다. */
    public boolean isAlive() { return !isRemoved(); }

    /** 네트워크 엔티티 id(바닐라 getId — Bukkit getEntityId 와 같은 번호). 아직 월드에 없으면 UnsupportedNmsCall(번호는 넣을 때 정해진다). */
    public int getId() {
        if (detached != null) throw notInWorld("Entity#getId");
        return f().entityId();
    }

    // ------------------------------------------------------------------ 모습·탑승 읽기 (NMS shim 5번 묶음)

    /** 바닐라 getBlockX·Y·Z: 지금 칸 좌표(내림). */
    public final int getBlockX() { return net.minecraft.util.Mth.floor(getX()); }

    public final int getBlockY() { return net.minecraft.util.Mth.floor(getY()); }

    public final int getBlockZ() { return net.minecraft.util.Mth.floor(getZ()); }

    /** 바닐라: 그 레벨의 레지스트리 묶음. */
    public net.minecraft.core.RegistryAccess registryAccess() { return level().registryAccess(); }

    /** 마지막으로 읽은 모습 — 그 뒤 엔티티가 사라지면 이 값으로 답한다(바닐라도 지운 엔티티의 필드는 그대로 읽힌다). 틀린 스레드에는 쓰지 않는다. */
    private volatile org.domi.furnace.api.internal.ChunkInternals.EntityLook lastLook;

    private static final org.domi.furnace.api.internal.ChunkInternals.EntityLook FRESH =
            new org.domi.furnace.api.internal.ChunkInternals.EntityLook((byte) 0, 0, 0, 0, 0, false, false, 0, null, java.util.List.of());

    /**
     * 지금 모습. 플레이어는 아무 스레드에서 최근 값(Paper 처럼 경쟁 읽기). 다른 엔티티는 주인 스레드에서 읽고, 다른 스레드 규칙은
     * {@link org.domi.furnace.compat.nms.NmsEntities}(틱 스레드가 아니면 기다리고, 다른 region·전역 스레드는 ThreadOwnershipException).
     * 아직 월드에 없는 엔티티는 바닐라 생성자의 모습(플래그 0·서 있음·속도 0·탑승 없음).
     */
    protected org.domi.furnace.api.internal.ChunkInternals.EntityLook furnaceLook(String what) {
        if (detached != null) return FRESH;
        var h = f();
        var b = org.domi.furnace.compat.nms.NmsChunks.bridge();
        org.domi.furnace.api.internal.ChunkInternals.EntityLook l;
        try {
            l = h instanceof org.domi.furnace.api.entity.Player ? b.entityLook(h)
                    : org.domi.furnace.compat.nms.NmsEntities.call(furnaceWorld(), h, what, () -> b.entityLook(h));
        } catch (org.domi.furnace.api.concurrent.ThreadOwnershipException wrongThread) {
            throw wrongThread;   // 틀린 스레드는 예전 값으로 덮지 않는다(같은 IllegalStateException 계열이라 먼저 거른다)
        } catch (IllegalStateException gone) {
            l = lastLook;
            if (l == null) throw gone;
            return l;
        }
        lastLook = l;
        return l;
    }

    private boolean sharedFlag(int bit, String what) { return (furnaceLook(what).sharedFlags() & 1 << bit) != 0; }

    /** 바닐라 isCrouching: 자세가 웅크림. */
    public boolean isCrouching() { return furnaceLook("Entity#isCrouching").pose() == Pose.CROUCHING.ordinal(); }

    /** 바닐라 isSwimming: 공유 플래그 4. 플레이어는 날거나 관전 중이면 거짓(Player 가 덮는다). */
    public boolean isSwimming() { return sharedFlag(4, "Entity#isSwimming"); }

    /** 바닐라 isInvisible: 공유 플래그 5. */
    public boolean isInvisible() { return sharedFlag(5, "Entity#isInvisible"); }

    /** 바닐라 hasPermissions(단계): 권한 단계가 그 이상인가. 엔티티는 0, 플레이어는 op 단계. */
    public boolean hasPermissions(int level) { return furnaceLook("Entity#hasPermissions").permissionLevel() >= level; }

    /** 바닐라: 엔티티는 관전자가 아니다(ServerPlayer 가 덮는다). */
    public boolean isSpectator() { return false; }

    /**
     * 바닐라 getKnownMovement: 조종하는 플레이어가 있으면 그가 알린 움직임, 아니면 지금 속도. 플레이어는 클라이언트가 알린 움직임 — Furnace 는 마지막
     * 이동 패킷이 옮긴 거리(새 자리 − 이전 자리)를 쓴다. 바닐라는 처리기가 따로 재는 값이라 한 틱에 이동 패킷이 여럿이거나 순간이동 직후에는 다를 수
     * 있다(가정 — 바닐라 대조 시험 없음).
     */
    public net.minecraft.world.phys.Vec3 getKnownMovement() {
        var l = furnaceLook("Entity#getKnownMovement");
        return new net.minecraft.world.phys.Vec3(l.knownX(), l.knownY(), l.knownZ());
    }

    /** 바닐라 getVehicle: 탄 탈것(없으면 null). 탈것은 같은 region 에 있다. */
    public Entity getVehicle() {
        java.util.UUID v = furnaceLook("Entity#getVehicle").vehicle();
        return v == null ? null : byUuid(v, "Entity#getVehicle");
    }

    /** 바닐라 isPassenger: 탈것이 있는가. */
    public boolean isPassenger() { return furnaceLook("Entity#isPassenger").vehicle() != null; }

    /** 바닐라 hasPassenger(조건): 바로 탄 승객 중 조건에 맞는 것이 있는가. */
    public boolean hasPassenger(java.util.function.Predicate<Entity> predicate) {
        for (Entity p : passengersNow("Entity#hasPassenger")) if (predicate.test(p)) return true;
        return false;
    }

    /** 바닐라 getSelfAndPassengers: 자기 다음에 승객마다 그 승객과 그 위 승객(깊이 우선, 타는 순서). */
    public java.util.stream.Stream<Entity> getSelfAndPassengers() {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(this),
                passengersNow("Entity#getSelfAndPassengers").stream().flatMap(Entity::getSelfAndPassengers));
    }

    /** 바닐라 getIndirectPassengers: 승객과 그 위 승객 모두(자기 빼고). 돌 때마다 지금 승객을 다시 본다(바닐라도 스트림을 새로 만든다). */
    public Iterable<Entity> getIndirectPassengers() {
        return () -> passengersNow("Entity#getIndirectPassengers").stream().flatMap(Entity::getSelfAndPassengers).iterator();
    }

    private java.util.List<Entity> passengersNow(String what) {
        var ids = furnaceLook(what).passengers();
        if (ids.isEmpty()) return java.util.List.of();
        java.util.List<Entity> out = new java.util.ArrayList<>(ids.size());
        for (var id : ids) {
            Entity e = byUuid(id, what);
            if (e != null) out.add(e);
        }
        return out;
    }

    /**
     * 이미 만든 Bukkit 객체가 있으면 그것(어느 스레드나 된다), 없으면 그 월드에서 찾는다. 다른 region·전역 스레드에서 다른 region 의 엔티티를 찾아야 하면
     * {@link #furnaceLook} 과 같이 ThreadOwnershipException(조용히 null 을 주면 isPassenger() 참과 모순). 틱 스레드가 아니면 주인 region 을 기다린다.
     * 그 사이 사라진 엔티티는 null(바닐라도 사라진 탈것은 목록에서 빠진다).
     */
    private Entity byUuid(java.util.UUID id, String what) {
        CraftEntity cached = org.domi.furnace.compat.paper.Compat.cachedEntity(id);
        if (cached != null) return cached.getHandle();
        var w = furnaceWorld();
        java.util.Optional<org.domi.furnace.api.entity.Entity> now;
        try {
            now = w.entityByUuidNow(id);
        } catch (org.domi.furnace.api.concurrent.ThreadOwnershipException wrongThread) {
            if (org.domi.furnace.compat.thread.PrimaryThread.isPrimary() || org.domi.furnace.api.Furnace.server().scheduler().isTickThread()) {
                throw new org.domi.furnace.api.concurrent.ThreadOwnershipException(what + ": entity " + id + " belongs to another region - call it on its"
                        + " owning region (current thread: " + Thread.currentThread().getName() + ")");
            }
            var e = org.domi.furnace.compat.paper.Compat.entityByUuid(w, id);   // 틱 스레드가 아니다: 주인 region 에 물어 기다린다
            return e instanceof CraftEntity c ? c.getHandle() : null;
        }
        var e = now.map(org.domi.furnace.compat.paper.Compat::entity).orElse(null);
        return e instanceof CraftEntity c ? c.getHandle() : null;
    }

    // ------------------------------------------------------------------ 아직 월드에 없는 엔티티 (T-212: FAWE //paste -e)

    /**
     * 바닐라 {@code Entity#load(tag)}. {@link EntityType#create} 로 만든 엔티티에만 된다 — 조작은 기록해 두었다가 월드에 넣을 때 서버 엔티티에 같은 차례로
     * 재생한다. 월드의 엔티티는 UnsupportedNmsCall(살아 있는 엔티티의 UUID 를 바꿔 버린다 — Furnace API 의 editNbt 를 쓴다).
     */
    public void load(net.minecraft.nbt.CompoundTag tag) {
        Detached d = detachedFor("Entity#load");
        d.ops.add(new org.domi.furnace.api.internal.ChunkInternals.EntityOp.Load(tag.furnace()));
        // 바닐라 load 의 자리·방향·UUID(넣을 region 을 고르고 게터가 같은 값을 주게)
        net.minecraft.nbt.ListTag pos = tag.getList("Pos", 6), rot = tag.getList("Rotation", 5);
        d.x = clamp(pos.getDouble(0), 3.0000512E7);
        d.y = clamp(pos.getDouble(1), 2.0E7);
        d.z = clamp(pos.getDouble(2), 3.0000512E7);
        float yr = rot.getFloat(0), xr = rot.getFloat(1);
        if (Float.isFinite(yr)) d.yRot = yr;
        if (Float.isFinite(xr)) d.xRot = xr;
        if (tag.hasUUID("UUID")) d.uuid = tag.getUUID("UUID");
    }

    /** 바닐라 {@code Entity#absMoveTo(x, y, z, yRot, xRot)}. 아직 월드에 없는 엔티티에만 된다({@link #load} 와 같은 규칙). */
    public void absMoveTo(double x, double y, double z, float yRot, float xRot) {
        Detached d = detachedFor("Entity#absMoveTo");
        d.ops.add(new org.domi.furnace.api.internal.ChunkInternals.EntityOp.AbsMoveTo(x, y, z, yRot, xRot));
        d.x = Math.clamp(x, -3.0E7, 3.0E7);
        d.y = y;
        d.z = Math.clamp(z, -3.0E7, 3.0E7);
        d.yRot = yRot % 360.0F;
        d.xRot = Math.clamp(xRot, -90.0F, 90.0F) % 360.0F;
    }

    /**
     * 바닐라 {@code Entity#moveTo(pos)}: 방향은 그대로 두고 그 자리로 옮긴다(이벤트·탈것 내림 없는 "자리 바꿈"). 월드의 엔티티는 Furnace 가 자리만 바꾸는 길이
     * teleport 뿐이라 그것으로 옮긴다 — 부른 스레드가 주인이면 곧바로, 아니면 주인에게 맡긴다(Bukkit {@code teleport} 와 같은 규칙). 플레이어는 클라이언트에
     * 알리는 길이 teleport 라 UnsupportedNmsCall 이다. 아직 월드에 없는 엔티티는 {@link #absMoveTo} 처럼 기록해 넣을 때 재생한다.
     */
    public void moveTo(net.minecraft.world.phys.Vec3 pos) { moveTo(pos.x, pos.y, pos.z); }

    /** 바닐라 {@code Entity#moveTo(x, y, z)}: 방향은 그대로. {@link #moveTo(net.minecraft.world.phys.Vec3)} 참고. */
    public void moveTo(double x, double y, double z) { moveTo(x, y, z, getYRot(), getXRot()); }

    /** 바닐라 {@code Entity#moveTo(x, y, z, yRot, xRot)}. {@link #moveTo(net.minecraft.world.phys.Vec3)} 참고. */
    public void moveTo(double x, double y, double z, float yRot, float xRot) {
        if (detached != null) {
            absMoveTo(x, y, z, yRot, xRot);
            return;
        }
        if (f() instanceof org.domi.furnace.api.entity.Player) {
            throw new UnsupportedNmsCall("Entity#moveTo", "moving a player this way would not tell the client - use the Bukkit teleport");
        }
        getBukkitEntity().teleport(new org.bukkit.Location(getBukkitEntity().getWorld(), x, y, z, yRot, xRot));
    }

    /** 바닐라 {@code Entity#setUUID}. 아직 월드에 없는 엔티티에만 된다({@link #load} 와 같은 규칙). */
    public void setUUID(UUID uuid) {
        Detached d = detachedFor("Entity#setUUID");
        d.ops.add(new org.domi.furnace.api.internal.ChunkInternals.EntityOp.SetUuid(uuid));
        d.uuid = uuid;
    }

    private Detached detachedFor(String what) {
        Detached d = detached;
        if (d == null) {
            throw new UnsupportedNmsCall(what, "only an entity made with EntityType#create (not in a world yet) can be changed this way - for an entity in"
                    + " a world use the Bukkit API or Furnace Entity#editNbt");
        }
        return d;
    }

    private static double clamp(double v, double bound) { return Math.clamp(v, -bound, bound); }

    /**
     * 호환 모듈 전용: Paper {@code ServerLevel#addEntity}(events = 참: {@code addFreshEntity(entity, reason)}) 와 {@code EntityLookup#addNewEntity}(거짓: 이벤트 없음).
     * 그 자리의 주인 region 에서 서버 엔티티를 만들어 기록한 조작을 재생해 넣고, 넣었으면 이 shim 을 그 엔티티의 {@code getHandle()} 로 묶는다.
     */
    public boolean furnaceAddToWorld(net.minecraft.server.level.ServerLevel level, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason, boolean events) {
        if (events) generation = false;
        Detached d = detached;
        if (d == null) {
            if (!events) return false;   // Paper: 이미 있는 UUID 라 거절
            LOG.log(System.Logger.Level.ERROR, "Attempted Double World add on " + this, new Throwable());
            return true;
        }
        if (events && spawnReason == null) spawnReason = reason;
        if (d.removed) return false;
        String id = EntityType.getKey(d.type).toString();
        int x = (int) Math.floor(d.x), y = (int) Math.floor(d.y), z = (int) Math.floor(d.z);
        var world = level.getWorld().furnaceHandle();
        var ops = java.util.List.copyOf(d.ops);
        String why = spawnReason == null ? null : spawnReason.name();
        var b = org.domi.furnace.compat.nms.NmsChunks.bridge();
        var r = org.domi.furnace.compat.nms.NmsOwner.call(world, x, y, z, "ServerLevel#addFreshEntity",
                () -> b.addNmsEntityNow(world, x, z, id, ops, why, events));
        if (r.added() == null) {
            if (r.discarded()) d.removed = true;   // Paper doEntityAddEventCalling: 이벤트 취소·처리기가 지움이면 그 엔티티를 discard
            return false;
        }
        CraftEntity c = (CraftEntity) org.domi.furnace.compat.paper.Compat.entity(r.added());
        c.adoptHandle(this);
        bukkit = c;
        detached = null;
        return true;
    }

    /** 바닐라 지움 이유(이름·순서·두 판정이 1.21.1 과 같다). */
    public enum RemovalReason {
        KILLED(true, false), DISCARDED(true, false), UNLOADED_TO_CHUNK(false, true), UNLOADED_WITH_PLAYER(false, false), CHANGED_DIMENSION(false, false);

        private final boolean destroy, save;

        RemovalReason(boolean destroy, boolean save) {
            this.destroy = destroy;
            this.save = save;
        }

        public boolean shouldDestroy() { return destroy; }

        public boolean shouldSave() { return save; }
    }

    /** 아직 월드에 없는 엔티티의 상태. 바닐라 생성자처럼 자리 0,0,0·방향 0 에서 시작한다. 한 스레드가 만들고 넣는다(FAWE 동기 작업). */
    private static final class Detached {
        final EntityType<?> type;
        final Level level;
        final java.util.List<org.domi.furnace.api.internal.ChunkInternals.EntityOp> ops = new java.util.ArrayList<>();
        double x, y, z;
        float yRot, xRot;
        UUID uuid;
        boolean removed;

        Detached(EntityType<?> type, Level level) {
            this.type = type;
            this.level = level;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return detached == null && o instanceof Entity e && e.detached == null && e.getUUID().equals(getUUID());
    }

    @Override
    public int hashCode() { return detached != null ? System.identityHashCode(this) : getUUID().hashCode(); }

    @Override
    public String toString() {
        Detached d = detached;
        if (d != null) {
            return String.format(java.util.Locale.ROOT, "%s['%s'/%s, l='%s', x=%.2f, y=%.2f, z=%.2f]", getClass().getSimpleName(), EntityType.getKey(d.type),
                    d.uuid, d.level, d.x, d.y, d.z);
        }
        var l = f().location();
        return String.format(java.util.Locale.ROOT, "%s['%s'/%s, l='%s', x=%.2f, y=%.2f, z=%.2f]", getClass().getSimpleName(), getBukkitEntity().getName(), getUUID(), l.world(), l.x(), l.y(), l.z());
    }
}
