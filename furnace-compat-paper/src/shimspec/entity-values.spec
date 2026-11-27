# 엔티티 핵심 영역(영역 2) 손 shim 의 명세 이전(2026-11-24). 상수와 순서는 바닐라 1.21.1, 몸통은 손 조각(src/shims/java).
class net.minecraft.world.entity.Pose
  STANDING
  FALL_FLYING
  SLEEPING
  SWIMMING
  SPIN_ATTACK
  CROUCHING
  LONG_JUMPING
  DYING
  CROAKING
  USING_TONGUE
  SITTING
  ROARING
  SNIFFING
  EMERGING
  DIGGING
  SLIDING
  SHOOTING
  INHALING
  id()I -> @hand pure
  BY_ID = @hand
class net.minecraft.world.entity.player.ChatVisiblity
  FULL = (0, "options.chat.visibility.full")
  SYSTEM = (1, "options.chat.visibility.system")
  HIDDEN = (2, "options.chat.visibility.hidden")
  getId -> @hand pure
  getKey -> @hand pure
  byId -> @hand pure
class net.minecraft.world.entity.HumanoidArm
  LEFT = (0, "left", "options.mainHand.left")
  RIGHT = (1, "right", "options.mainHand.right")
  getOpposite -> @hand pure
  getId -> @hand pure
  getKey -> @hand pure
  getSerializedName -> @hand pure
class net.minecraft.world.entity.RelativeMovement
  X = (0)
  Y = (1)
  Z = (2)
  Y_ROT = (3)
  X_ROT = (4)
  unpack -> @hand pure
  pack -> @hand pure
  ALL = @hand
  ROTATION = @hand
class net.minecraft.world.entity.npc.VillagerType
  DESERT = @hand
  JUNGLE = @hand
  PLAINS = @hand
  SAVANNA = @hand
  SNOW = @hand
  SWAMP = @hand
  TAIGA = @hand
  byBiome -> @hand pure
class net.minecraft.world.entity.npc.VillagerData
  MIN_VILLAGER_LEVEL = @hand
  MAX_VILLAGER_LEVEL = @hand
  <init> -> @hand
  getType -> @hand pure
  getProfession -> @hand pure
  getLevel -> @hand pure
  setType -> @hand pure
  setProfession -> @hand pure
  setLevel -> @hand pure
  getMinXpPerLevel -> @hand pure
  getMaxXpPerLevel -> @hand pure
  canLevelUp -> @hand pure
class net.minecraft.world.entity.animal.WolfVariant
  <init> -> @hand
  wildTexture() -> @hand pure
  tameTexture() -> @hand pure
  angryTexture() -> @hand pure
  biomes() -> @hand pure
# 세터는 서버에 쓰는 다리(NmsChunks.bridge)의 스레드 규칙을 따르므로 적지 않는다(entity.spec 머리 주석과 같은 이유).
class net.minecraft.world.entity.player.Abilities
  invulnerable = @hand
  flying = @hand
  mayfly = @hand
  instabuild = @hand
  mayBuild = @hand
  flyingSpeed = @hand
  walkingSpeed = @hand
  <init> -> @hand
  getFlyingSpeed -> @hand read=any
  getWalkingSpeed -> @hand read=any
