# 월드젠 값·난수 클래스(영역 6 levelgen, 손 shim 이전 — 2026-11-24). 몸통은 src/shims 조각이 가진다(@hand). 값 객체라 공유 상태가 없어 pure.
# Furnace 전용 멤버(furnaceCreate·furnaceWorld 등)와 비공개 멤버는 조각에만 있다. Paper 에 있는데 아직 없는 멤버(코덱·위치 난수 공장 등)는 T-261 껍데기가 채운다.
class net.minecraft.world.level.levelgen.RandomSupport
  GOLDEN_RATIO_64 = -7046029254386353131L
  SILVER_RATIO_64 = 7640891576956012809L
  mixStafford13 -> @hand pure
  upgradeSeedTo128bitUnmixed -> @hand pure
  upgradeSeedTo128bit -> @hand pure
  seedFromHashOf -> @hand pure
  # generateUniqueSeed 는 시간·전역 씨앗을 써서 호출마다 값이 달라 pure 가 아니다 — 명세에 적지 않고 조각만 가진다(차분 시험이 견주지 못한다)
class net.minecraft.world.level.levelgen.BitRandomSource
  FLOAT_MULTIPLIER = 5.9604645E-8F
  DOUBLE_MULTIPLIER = 1.110223E-16F
  next
  nextInt()I -> @hand pure
  nextInt(I)I -> @hand pure
  nextLong -> @hand pure
  nextBoolean -> @hand pure
  nextFloat -> @hand pure
  nextDouble -> @hand pure
class net.minecraft.world.level.levelgen.MarsagliaPolarGaussian
  <init>(Lnet/minecraft/util/RandomSource;)V -> @hand
  reset -> @hand pure
  nextGaussian -> @hand pure
class net.minecraft.world.level.levelgen.PositionalRandomFactory
  at(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/util/RandomSource; -> @hand pure
  fromHashOf(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/util/RandomSource; -> @hand pure
  fromHashOf(Ljava/lang/String;)Lnet/minecraft/util/RandomSource;
  fromSeed
  at(III)Lnet/minecraft/util/RandomSource;
  parityConfigString
class net.minecraft.world.level.levelgen.SingleThreadedRandomSource
  forkPositional -> @hand pure
  <init>(J)V -> @hand
  fork -> @hand pure
  setSeed -> @hand pure
  next -> @hand pure
  nextGaussian -> @hand pure
class net.minecraft.world.level.levelgen.LegacyRandomSource
  forkPositional -> @hand pure
  <init>(J)V -> @hand
  fork -> @hand pure
  setSeed -> @hand pure
  next -> @hand pure
  nextGaussian -> @hand pure
class net.minecraft.world.level.levelgen.Xoroshiro128PlusPlus
  <init>(Lnet/minecraft/world/level/levelgen/RandomSupport$Seed128bit;)V -> @hand
  <init>(JJ)V -> @hand
  nextLong -> @hand pure
class net.minecraft.world.level.levelgen.XoroshiroRandomSource
  <init>(J)V -> @hand
  <init>(Lnet/minecraft/world/level/levelgen/RandomSupport$Seed128bit;)V -> @hand
  <init>(JJ)V -> @hand
  fork -> @hand pure
  forkPositional -> @hand pure
  setSeed -> @hand pure
  nextInt()I -> @hand pure
  nextInt(I)I -> @hand pure
  nextLong -> @hand pure
  nextBoolean -> @hand pure
  nextFloat -> @hand pure
  nextDouble -> @hand pure
  nextGaussian -> @hand pure
  consumeCount -> @hand pure
class net.minecraft.world.level.levelgen.ThreadSafeLegacyRandomSource
  <init>(J)V -> @hand
  fork -> @hand pure
  forkPositional -> @hand pure
  setSeed -> @hand pure
  next -> @hand pure
  nextGaussian -> @hand pure
class net.minecraft.world.level.levelgen.WorldOptions
  DEMO_OPTIONS = @hand
  <init>(JZZ)V -> @hand
  defaultWithRandomSeed -> @hand pure
  seed()J -> @hand pure
  generateStructures()Z -> @hand pure
  generateBonusChest()Z -> @hand pure
  isOldCustomizedWorld -> @hand pure
  withBonusChest -> @hand pure
  withStructures -> @hand pure
  withSeed -> @hand pure
  parseSeed -> @hand pure
  # randomSeed 도 같은 이유로 명세에 적지 않는다
class net.minecraft.world.level.levelgen.BelowZeroRetrogen
  targetStatus()Lnet/minecraft/world/level/chunk/status/ChunkStatus; -> @hand pure
  hasBedrockHoles -> @hand pure
  hasBedrockHole -> @hand pure
# 생성기·난수 상태: 월드 손잡이를 가진 Furnace 생성자만 있다(조각). 바닐라 멤버는 T-261 껍데기·연결이 채운다.
class net.minecraft.world.level.levelgen.DebugLevelSource
class net.minecraft.world.level.levelgen.FlatLevelSource
class net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator
class net.minecraft.world.level.levelgen.RandomState
class net.minecraft.world.level.levelgen.structure.BoundingBox
  <init>(Lnet/minecraft/core/BlockPos;)V -> @hand
  <init>(IIIIII)V -> @hand
  fromCorners -> @hand pure
  infinite -> @hand pure
  orientBox -> @hand pure
  intersectingChunks -> @hand pure
  intersects(Lnet/minecraft/world/level/levelgen/structure/BoundingBox;)Z -> @hand pure
  intersects(IIII)Z -> @hand pure
  encapsulatingPositions -> @hand pure
  encapsulatingBoxes -> @hand pure
  encapsulate(Lnet/minecraft/world/level/levelgen/structure/BoundingBox;)Lnet/minecraft/world/level/levelgen/structure/BoundingBox; -> @hand pure
  encapsulate(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/levelgen/structure/BoundingBox; -> @hand pure
  move(III)Lnet/minecraft/world/level/levelgen/structure/BoundingBox; -> @hand pure
  move(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/level/levelgen/structure/BoundingBox; -> @hand pure
  moved -> @hand pure
  inflatedBy(I)Lnet/minecraft/world/level/levelgen/structure/BoundingBox; -> @hand pure
  inflatedBy(III)Lnet/minecraft/world/level/levelgen/structure/BoundingBox; -> @hand pure
  isInside(Lnet/minecraft/core/Vec3i;)Z -> @hand pure
  isInside(III)Z -> @hand pure
  getLength -> @hand pure
  getXSpan -> @hand pure
  getYSpan -> @hand pure
  getZSpan -> @hand pure
  getCenter -> @hand pure
  forAllCorners -> @hand pure
  toString -> @hand pure
  equals -> @hand pure
  hashCode -> @hand pure
  minX()I -> @hand pure
  minY()I -> @hand pure
  minZ()I -> @hand pure
  maxX()I -> @hand pure
  maxY()I -> @hand pure
  maxZ()I -> @hand pure
