# 엔티티 사슬(T-232, 2026-11-23). 엔티티 객체는 종류 표(kind-classes-<판>.txt 의 E 줄)대로 그 종류의 바닐라 클래스로 만들고
# (NmsEntityKinds), 사슬 위 클래스(Mob·Animal·HangingEntity…)와 인터페이스의 선언은 생성기가 잠금 파일에서 만든다.
# 몸통이 있는 클래스는 손 조각(src/shims/java)이다. 사슬 맨 위 Entity 조각이 생성기 전용 생성자로 재료(Bukkit 객체·아직 월드에 없는 엔티티)를 받는다.
#
# 검사 종류(T-232 시운전 판정, compat-shim-entity-chain-t232 기록 2장): 손 몸통은 아래처럼 적어 잠금 파일이 바닐라 모양(접근·final·서술자)을 묶고
# read=owner·write 는 몸통 첫 호출이 NmsEntityOwner 검사인지 빌드 시험이 본다. 몸통이 Bukkit API 를 거치는 멤버(체력·속성·손 아이템, 월드 엔티티 moveTo,
# Player 의 게임 모드·프로필 읽기)는 그 API 의 스레드 규칙(주인에게 넘기기)을 따르므로 여기 적지 않는다 — 소유 검사를 앞에 두면 동작이 바뀐다.
import org.domi.furnace.compat.nms.NmsEntityOps
class net.minecraft.world.entity.Entity  owner=org.domi.furnace.compat.nms.NmsEntityOwner
  # 스냅샷 값(핸들을 만든 때·아직 월드에 없으면 기록한 값)
  getUUID -> @hand read=snapshot
  getStringUUID -> @hand read=snapshot
  getId -> @hand read=snapshot
  getX() -> @hand read=snapshot
  getY() -> @hand read=snapshot
  getZ() -> @hand read=snapshot
  getYRot -> @hand read=snapshot
  getXRot -> @hand read=snapshot
  getYHeadRot -> @hand read=snapshot
  getBukkitYaw -> @hand read=snapshot
  position() -> @hand read=snapshot
  blockPosition() -> @hand read=snapshot
  chunkPosition() -> @hand read=snapshot
  getBlockX -> @hand read=snapshot
  getBlockY -> @hand read=snapshot
  getBlockZ -> @hand read=snapshot
  isRemoved -> @hand read=snapshot
  isAlive -> @hand read=snapshot
  # 바뀌지 않는 값
  getType -> @hand read=any
  level() -> @hand read=any
  getServer -> @hand read=any
  registryAccess -> @hand read=any
  getBukkitEntity -> @hand read=any
  isSpectator -> @hand pure
  # 주인 스레드에서 지금 모습을 읽는다(틱 스레드가 아니면 주인 region 에 맡겨 기다린다)
  isCrouching -> @hand read=owner
  isSwimming -> @hand read=owner
  isInvisible -> @hand read=owner
  hasPermissions -> @hand read=owner
  getKnownMovement -> @hand read=owner
  getVehicle -> @hand read=owner
  isPassenger -> @hand read=owner
  hasPassenger(Ljava/util/function/Predicate;) -> @hand read=owner
  getSelfAndPassengers -> @hand read=owner
  getIndirectPassengers -> @hand read=owner
  save -> @hand read=owner
  saveAsPassenger(Lnet/minecraft/nbt/CompoundTag;)Z -> @hand read=owner
  saveWithoutId(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag; -> @hand read=owner
  # 주인 스레드에서 바꾼다
  discard() -> @hand write
  remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V -> @hand write
  setPosRaw(DDD)V -> @hand write
  teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z -> @hand write
  setYHeadRot -> @hand write
  startRiding(Lnet/minecraft/world/entity/Entity;)Z -> @hand write
  startRiding(Lnet/minecraft/world/entity/Entity;Z)Z -> @hand write
  stopRiding() -> @hand write
  ejectPassengers -> @hand write
  load -> @hand write
  absMoveTo(DDDFF)V -> @hand write
  setUUID -> @hand write
class net.minecraft.world.entity.LivingEntity  owner=org.domi.furnace.compat.nms.NmsEntityOwner
  isAlive -> @hand read=snapshot
  isFallFlying -> @hand read=owner
class net.minecraft.world.entity.player.Player  owner=org.domi.furnace.compat.nms.NmsEntityOwner
  isSwimming -> @hand read=owner
class net.minecraft.world.entity.item.ItemEntity
  getItem -> @hand read=snapshot
# 담는 탈것(상자 배·상자/깔때기 수레): 손 shim Clearable 의 추상 메서드를 바닐라처럼 이 둘이 갖는다
import org.domi.furnace.compat.nms.NmsEntityOps
class net.minecraft.world.entity.vehicle.AbstractMinecartContainer  owner=org.domi.furnace.compat.nms.NmsEntityOwner
  clearContent -> NmsEntityOps.clearContent write
class net.minecraft.world.entity.vehicle.ChestBoat  owner=org.domi.furnace.compat.nms.NmsEntityOwner
  clearContent -> NmsEntityOps.clearContent write
# 걸이 엔티티(Axiom 이 소환·조작 때 돌린다): 방향·돌림. 월드 밖 엔티티는 조각이 태그대로 들고 조작을 기록한다
class net.minecraft.world.entity.decoration.HangingEntity  owner=org.domi.furnace.compat.nms.NmsEntityOwner
  getDirection -> @hand read=owner
  rotate -> @hand write
class net.minecraft.world.entity.decoration.ItemFrame  owner=org.domi.furnace.compat.nms.NmsEntityOwner
  getRotation -> @hand read=owner
  setRotation(I)V -> @hand write
# 돌림(걸이 엔티티 rotate 의 인자). 패킷 생성기가 빈 열거형으로 채우던 것을 명세로 옮겼다
class net.minecraft.world.level.block.Rotation
  NONE
  CLOCKWISE_90
  CLOCKWISE_180
  COUNTERCLOCKWISE_90
