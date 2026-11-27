# 엔티티 사슬(T-232, 2026-11-23). 엔티티 객체는 종류 표(kind-classes-<판>.txt 의 E 줄)대로 그 종류의 바닐라 클래스로 만들고
# (NmsEntityKinds), 사슬 위 클래스(Mob·Animal·HangingEntity…)와 인터페이스의 선언은 생성기가 잠금 파일에서 만든다.
# 몸통이 있는 클래스는 손 조각(src/shims/java)이다. 사슬 맨 위 Entity 조각이 생성기 전용 생성자로 재료(Bukkit 객체·아직 월드에 없는 엔티티)를 받는다.
#
# 검사 종류(T-232 시운전 판정, compat-shim-entity-chain-t232 기록 2장): 손 몸통은 아래처럼 적어 잠금 파일이 바닐라 모양(접근·final·서술자)을 묶고
# read=owner·write 는 몸통 첫 호출이 NmsEntityOwner 검사인지 빌드 시험이 본다. 몸통이 Bukkit API 를 거치는 멤버(체력·속성·손 아이템, 월드 엔티티 moveTo,
# Player 의 게임 모드·프로필 읽기)는 그 API 의 스레드 규칙(주인에게 넘기기)을 따르므로 여기 적지 않는다 — 소유 검사를 앞에 두면 동작이 바뀐다.
import org.domi.furnace.compat.nms.NmsEntityOps
class net.minecraft.world.entity.Entity  owner=org.domi.furnace.compat.nms.NmsEntityOwner
  # >>> 엔티티 데이터 칸 상수(tools/compat/entity-data-spec.py 가 만든다 — 손으로 고치지 않는다)
  DATA_POSE = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.Entity.class, "DATA_POSE")
  DATA_SHARED_FLAGS_ID = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.Entity.class, "DATA_SHARED_FLAGS_ID")
  # <<< 엔티티 데이터 칸 상수
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
  getBbWidth -> @hand read=owner
  getBbHeight -> @hand read=owner
  getEyeHeight() -> @hand read=owner
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
  # 단순 설정(월드의 엔티티는 주인 스레드에서 서버가, 아직 월드에 없는 엔티티는 기록해 넣을 때 재생)
  setPos(DDD)V -> @hand write
  setPos(Lnet/minecraft/world/phys/Vec3;)V -> @hand write
  setRot -> @hand write
  setYRot -> @hand write
  setXRot -> @hand write
  teleportTo(DDD)V -> @hand write
  setInvisible -> @hand write
  setGlowingTag -> @hand write
  setSharedFlagOnFire -> @hand write
  setSilent -> @hand write
  setOnGround -> @hand write
  setCustomNameVisible -> @hand write
  setCustomName -> @hand write
  setTicksFrozen -> @hand write
  getTicksRequiredToFreeze -> @hand pure
class net.minecraft.world.entity.LivingEntity  owner=org.domi.furnace.compat.nms.NmsEntityOwner
  # >>> 엔티티 데이터 칸 상수(tools/compat/entity-data-spec.py 가 만든다 — 손으로 고치지 않는다)
  DATA_ARROW_COUNT_ID = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.LivingEntity.class, "DATA_ARROW_COUNT_ID")
  DATA_HEALTH_ID = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.LivingEntity.class, "DATA_HEALTH_ID")
  DATA_LIVING_ENTITY_FLAGS = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.LivingEntity.class, "DATA_LIVING_ENTITY_FLAGS")
  # <<< 엔티티 데이터 칸 상수
  isAlive -> @hand read=snapshot
  isFallFlying -> @hand read=owner
  isUsingItem -> @hand read=owner
  getUsedItemHand -> @hand read=owner
  startUsingItem(Lnet/minecraft/world/InteractionHand;)V -> @hand write
  startUsingItem(Lnet/minecraft/world/InteractionHand;Z)V -> @hand write
  stopUsingItem -> @hand write
  animateHurt -> @hand write
class net.minecraft.world.entity.player.Player  owner=org.domi.furnace.compat.nms.NmsEntityOwner
  # >>> 엔티티 데이터 칸 상수(tools/compat/entity-data-spec.py 가 만든다 — 손으로 고치지 않는다)
  DATA_PLAYER_MAIN_HAND = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.player.Player.class, "DATA_PLAYER_MAIN_HAND")
  DATA_PLAYER_MODE_CUSTOMISATION = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.player.Player.class, "DATA_PLAYER_MODE_CUSTOMISATION")
  DATA_SHOULDER_LEFT = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.player.Player.class, "DATA_SHOULDER_LEFT")
  DATA_SHOULDER_RIGHT = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.player.Player.class, "DATA_SHOULDER_RIGHT")
  # <<< 엔티티 데이터 칸 상수
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
  # >>> 엔티티 데이터 칸 상수(tools/compat/entity-data-spec.py 가 만든다 — 손으로 고치지 않는다)
  DATA_ITEM = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.decoration.ItemFrame.class, "DATA_ITEM")
  DATA_ROTATION = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.decoration.ItemFrame.class, "DATA_ROTATION")
  # <<< 엔티티 데이터 칸 상수
  getRotation -> @hand read=owner
  setRotation(I)V -> @hand write
# 돌림(걸이 엔티티 rotate 의 인자). 패킷 생성기가 빈 열거형으로 채우던 것을 명세로 옮겼다
class net.minecraft.world.level.block.Rotation
  NONE
  CLOCKWISE_90
  CLOCKWISE_180
  COUNTERCLOCKWISE_90
# 장비 칸(AxGraves·FancyNpcs 가 칸 번호·종류·이름으로 쓴다). 패킷 생성기가 빈 열거형으로 채우던 것을 명세로 옮겼다. 값은 Paper 1.21.1 javap(정적 초기화)
class net.minecraft.world.entity.EquipmentSlot
  MAINHAND
  OFFHAND
  FEET
  LEGS
  CHEST
  HEAD
  BODY
  getType -> @hand pure
  getIndex()I -> @hand pure
  getIndex(I)I -> @hand pure
  getFilterFlag -> @hand pure
  getName -> @hand pure
  getSerializedName -> @hand pure
  isArmor -> @hand pure
  byName -> @hand pure
# 자리를 받는 바닐라 생성자(GSit 이 앉는 자리로 만든다). (EntityType, Level) 생성자는 종류 표의 클래스마다 생성기가 붙인다(몸통 종류 FRESH, 명세에 쓰지 않음)
class net.minecraft.world.entity.decoration.ArmorStand
  # >>> 엔티티 데이터 칸 상수(tools/compat/entity-data-spec.py 가 만든다 — 손으로 고치지 않는다)
  DATA_BODY_POSE = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.decoration.ArmorStand.class, "DATA_BODY_POSE")
  DATA_CLIENT_FLAGS = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.decoration.ArmorStand.class, "DATA_CLIENT_FLAGS")
  DATA_HEAD_POSE = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.decoration.ArmorStand.class, "DATA_HEAD_POSE")
  DATA_LEFT_ARM_POSE = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.decoration.ArmorStand.class, "DATA_LEFT_ARM_POSE")
  DATA_LEFT_LEG_POSE = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.decoration.ArmorStand.class, "DATA_LEFT_LEG_POSE")
  DATA_RIGHT_ARM_POSE = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.decoration.ArmorStand.class, "DATA_RIGHT_ARM_POSE")
  DATA_RIGHT_LEG_POSE = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.decoration.ArmorStand.class, "DATA_RIGHT_LEG_POSE")
  # <<< 엔티티 데이터 칸 상수
  <init>(Lnet/minecraft/world/level/Level;DDD)V -> @hand
class net.minecraft.world.entity.AreaEffectCloud
  <init>(Lnet/minecraft/world/level/Level;DDD)V -> @hand
# 낙타 앉기·서기(FancyNpcs): 서버 낙타가 바닐라 규칙으로, 월드 밖 낙타는 기록·재생
class net.minecraft.world.entity.animal.camel.Camel  owner=org.domi.furnace.compat.nms.NmsEntityOwner
  # >>> 엔티티 데이터 칸 상수(tools/compat/entity-data-spec.py 가 만든다 — 손으로 고치지 않는다)
  DASH = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.animal.camel.Camel.class, "DASH")
  LAST_POSE_CHANGE_TICK = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.animal.camel.Camel.class, "LAST_POSE_CHANGE_TICK")
  # <<< 엔티티 데이터 칸 상수
  sitDown -> @hand write
  standUp -> @hand write
  standUpInstantly -> @hand write
