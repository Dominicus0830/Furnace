# 몹 목표(몹 목표 설계 M1, 2026-11-24 — 공유 설계 furnace/designs/mob-goals-shim-design-2026-11-24.md). 선택기·감싼 목표·목표 shim 은 서버 몹의 선택기를
# 보는 손 조각이고(주인 스레드 규칙은 NmsGoals 가 몹 기준으로 건다 — 목표는 엔티티가 아니라 owner= 를 쓰지 않고 read=any 로 적는다),
# 바닐라 목표 클래스는 멤버 없는 class 줄(instanceof·상속 사슬용 — 서버 목표를 그 클래스의 객체로 보인다, 생성기 전용 생성자로 만든다).
import org.domi.furnace.compat.nms.NmsGoals
class net.minecraft.world.entity.ai.goal.Goal
  <init> -> @hand
  canUse -> @each(NmsGoals.canUse) read=any
  canContinueToUse -> @hand read=any
  isInterruptable -> @hand read=any
  start -> @hand read=any
  stop -> @hand read=any
  requiresUpdateEveryTick -> @hand read=any
  tick -> @hand read=any
  setFlags -> @hand read=any
  getFlags -> @hand read=any
  adjustedTickDelay -> @hand read=any
  reducedTickDelay -> @hand pure
  toString -> @hand pure
# Paper 순서(UNKNOWN_BEHAVIOR 가 0 — Paper 1.21.1 jar 의 정적 초기화 순서)
class net.minecraft.world.entity.ai.goal.Goal$Flag
  UNKNOWN_BEHAVIOR
  MOVE
  LOOK
  JUMP
  TARGET
class net.minecraft.world.entity.ai.goal.WrappedGoal
  <init> -> @hand
  canUse -> @hand read=any
  canContinueToUse -> @hand read=any
  isInterruptable -> @hand read=any
  start -> @hand read=any
  stop -> @hand read=any
  requiresUpdateEveryTick -> @hand read=any
  adjustedTickDelay -> @hand read=any
  tick -> @hand read=any
  setFlags -> @hand read=any
  getFlags -> @hand read=any
  canBeReplacedBy -> @hand read=any
  getGoal -> @hand read=any
  getPriority -> @hand read=any
  isRunning() -> @hand read=any
  equals -> @hand read=any
  hashCode -> @hand read=any
class net.minecraft.world.entity.ai.goal.GoalSelector
  addGoal -> @hand read=any
  removeGoal -> @hand read=any
  removeAllGoals -> @hand read=any
  getAvailableGoals -> @hand read=any
  tick -> @hand read=any
  tickRunningGoals -> @hand read=any
  disableControlFlag -> @hand read=any
  enableControlFlag -> @hand read=any
  setControlFlag -> @hand read=any
  inactiveTick -> @hand read=any
  hasTasks -> @hand read=any
# 바닐라 목표 클래스(서명표에서 — ai/goal·ai/goal/target 의 바깥 클래스 전부, 열거형 BoatGoals 빼고)
class net.minecraft.world.entity.ai.goal.AvoidEntityGoal
  <init>(Lnet/minecraft/world/entity/PathfinderMob;Ljava/lang/Class;FDD)V -> @hand
  <init>(Lnet/minecraft/world/entity/PathfinderMob;Ljava/lang/Class;FDDLjava/util/function/Predicate;)V -> @hand
  <init>(Lnet/minecraft/world/entity/PathfinderMob;Ljava/lang/Class;Ljava/util/function/Predicate;FDDLjava/util/function/Predicate;)V -> @hand
class net.minecraft.world.entity.ai.goal.BegGoal
class net.minecraft.world.entity.ai.goal.BreakDoorGoal
class net.minecraft.world.entity.ai.goal.BreathAirGoal
  <init> -> @hand
class net.minecraft.world.entity.ai.goal.BreedGoal
  <init>(Lnet/minecraft/world/entity/animal/Animal;D)V -> @hand
  <init>(Lnet/minecraft/world/entity/animal/Animal;DLjava/lang/Class;)V -> @hand
class net.minecraft.world.entity.ai.goal.CatLieOnBedGoal
class net.minecraft.world.entity.ai.goal.CatSitOnBlockGoal
class net.minecraft.world.entity.ai.goal.ClimbOnTopOfPowderSnowGoal
class net.minecraft.world.entity.ai.goal.DolphinJumpGoal
class net.minecraft.world.entity.ai.goal.DoorInteractGoal
class net.minecraft.world.entity.ai.goal.EatBlockGoal
class net.minecraft.world.entity.ai.goal.FleeSunGoal
  <init> -> @hand
class net.minecraft.world.entity.ai.goal.FloatGoal
  <init> -> @hand
class net.minecraft.world.entity.ai.goal.FollowBoatGoal
class net.minecraft.world.entity.ai.goal.FollowFlockLeaderGoal
class net.minecraft.world.entity.ai.goal.FollowMobGoal
class net.minecraft.world.entity.ai.goal.FollowOwnerGoal
class net.minecraft.world.entity.ai.goal.FollowParentGoal
  <init> -> @hand
class net.minecraft.world.entity.ai.goal.GolemRandomStrollInVillageGoal
class net.minecraft.world.entity.ai.goal.InteractGoal
class net.minecraft.world.entity.ai.goal.JumpGoal
class net.minecraft.world.entity.ai.goal.LandOnOwnersShoulderGoal
class net.minecraft.world.entity.ai.goal.LeapAtTargetGoal
  <init> -> @hand
class net.minecraft.world.entity.ai.goal.LlamaFollowCaravanGoal
class net.minecraft.world.entity.ai.goal.LookAtPlayerGoal
  <init>(Lnet/minecraft/world/entity/Mob;Ljava/lang/Class;F)V -> @hand
  <init>(Lnet/minecraft/world/entity/Mob;Ljava/lang/Class;FF)V -> @hand
  <init>(Lnet/minecraft/world/entity/Mob;Ljava/lang/Class;FFZ)V -> @hand
class net.minecraft.world.entity.ai.goal.LookAtTradingPlayerGoal
class net.minecraft.world.entity.ai.goal.MeleeAttackGoal
  <init> -> @hand
class net.minecraft.world.entity.ai.goal.MoveBackToVillageGoal
class net.minecraft.world.entity.ai.goal.MoveThroughVillageGoal
class net.minecraft.world.entity.ai.goal.MoveToBlockGoal
class net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal
  <init> -> @hand
class net.minecraft.world.entity.ai.goal.MoveTowardsTargetGoal
  <init> -> @hand
class net.minecraft.world.entity.ai.goal.OcelotAttackGoal
class net.minecraft.world.entity.ai.goal.OfferFlowerGoal
class net.minecraft.world.entity.ai.goal.OpenDoorGoal
  <init> -> @hand
class net.minecraft.world.entity.ai.goal.PanicGoal
  <init>(Lnet/minecraft/world/entity/PathfinderMob;D)V -> @hand
  <init>(Lnet/minecraft/world/entity/PathfinderMob;DLjava/util/function/Function;)V -> @hand
  <init>(Lnet/minecraft/world/entity/PathfinderMob;DLnet/minecraft/tags/TagKey;)V -> @hand
class net.minecraft.world.entity.ai.goal.PathfindToRaidGoal
class net.minecraft.world.entity.ai.goal.RandomLookAroundGoal
  <init> -> @hand
class net.minecraft.world.entity.ai.goal.RandomStandGoal
class net.minecraft.world.entity.ai.goal.RandomStrollGoal
  <init>(Lnet/minecraft/world/entity/PathfinderMob;D)V -> @hand
  <init>(Lnet/minecraft/world/entity/PathfinderMob;DI)V -> @hand
  <init>(Lnet/minecraft/world/entity/PathfinderMob;DIZ)V -> @hand
class net.minecraft.world.entity.ai.goal.RandomSwimmingGoal
  <init> -> @hand
class net.minecraft.world.entity.ai.goal.RangedAttackGoal
class net.minecraft.world.entity.ai.goal.RangedBowAttackGoal
class net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal
class net.minecraft.world.entity.ai.goal.RemoveBlockGoal
class net.minecraft.world.entity.ai.goal.RestrictSunGoal
  <init> -> @hand
class net.minecraft.world.entity.ai.goal.RunAroundLikeCrazyGoal
class net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal
class net.minecraft.world.entity.ai.goal.StrollThroughVillageGoal
class net.minecraft.world.entity.ai.goal.SwellGoal
class net.minecraft.world.entity.ai.goal.TemptGoal
  <init> -> @hand
class net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal
class net.minecraft.world.entity.ai.goal.TryFindWaterGoal
class net.minecraft.world.entity.ai.goal.UseItemGoal
class net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal
  <init> -> @hand
class net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal
  <init>(Lnet/minecraft/world/entity/PathfinderMob;D)V -> @hand
  <init>(Lnet/minecraft/world/entity/PathfinderMob;DF)V -> @hand
class net.minecraft.world.entity.ai.goal.ZombieAttackGoal
class net.minecraft.world.entity.ai.goal.target.DefendVillageTargetGoal
class net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal
  <init> -> @hand
class net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal
  <init>(Lnet/minecraft/world/entity/Mob;Ljava/lang/Class;IZZLjava/util/function/Predicate;)V -> @hand
  <init>(Lnet/minecraft/world/entity/Mob;Ljava/lang/Class;Z)V -> @hand
  <init>(Lnet/minecraft/world/entity/Mob;Ljava/lang/Class;ZLjava/util/function/Predicate;)V -> @hand
  <init>(Lnet/minecraft/world/entity/Mob;Ljava/lang/Class;ZZ)V -> @hand
class net.minecraft.world.entity.ai.goal.target.NearestAttackableWitchTargetGoal
class net.minecraft.world.entity.ai.goal.target.NearestHealableRaiderTargetGoal
class net.minecraft.world.entity.ai.goal.target.NonTameRandomTargetGoal
class net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal
class net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal
class net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal
class net.minecraft.world.entity.ai.goal.target.TargetGoal
# 몹: 두 선택기 필드(플러그인이 바로 읽는다 — 생성기 전용 생성자에서 채운다)와 AI 끄기
class net.minecraft.world.entity.Mob  owner=org.domi.furnace.compat.nms.NmsEntityOwner
  goalSelector = @hand
  targetSelector = @hand
  isNoAi -> @hand read=owner
  setNoAi -> @hand write
