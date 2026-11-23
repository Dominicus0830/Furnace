# 엔티티 데이터 칸 상수(엔티티 데이터 설계 D2). tools/compat/entity-data-spec.py 가 Paper 서버 서명표에서 만든다 — 손으로 고치지 않는다.
# public/protected 인 EntityDataAccessor 정적 필드만. 값(칸 번호·값 종류)은 실행 때 NmsEntityData.accessor 가 칸 번호표에서 찾는다.
class net.minecraft.world.entity.Display
  DATA_POS_ROT_INTERPOLATION_DURATION_ID = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.Display.class, "DATA_POS_ROT_INTERPOLATION_DURATION_ID")
class net.minecraft.world.entity.Display$TextDisplay
  DATA_BACKGROUND_COLOR_ID = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.Display.TextDisplay.class, "DATA_BACKGROUND_COLOR_ID")
  DATA_LINE_WIDTH_ID = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.Display.TextDisplay.class, "DATA_LINE_WIDTH_ID")
class net.minecraft.world.entity.TamableAnimal
  DATA_FLAGS_ID = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.TamableAnimal.class, "DATA_FLAGS_ID")
  DATA_OWNERUUID_ID = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.TamableAnimal.class, "DATA_OWNERUUID_ID")
class net.minecraft.world.entity.animal.Fox
  DATA_TRUSTED_ID_0 = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.animal.Fox.class, "DATA_TRUSTED_ID_0")
  DATA_TRUSTED_ID_1 = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.animal.Fox.class, "DATA_TRUSTED_ID_1")
class net.minecraft.world.entity.animal.IronGolem
  DATA_FLAGS_ID = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.animal.IronGolem.class, "DATA_FLAGS_ID")
class net.minecraft.world.entity.animal.camel.Camel
  DASH = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.animal.camel.Camel.class, "DASH")
  LAST_POSE_CHANGE_TICK = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.animal.camel.Camel.class, "LAST_POSE_CHANGE_TICK")
class net.minecraft.world.entity.animal.goat.Goat
  DATA_HAS_LEFT_HORN = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.animal.goat.Goat.class, "DATA_HAS_LEFT_HORN")
  DATA_HAS_RIGHT_HORN = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.animal.goat.Goat.class, "DATA_HAS_RIGHT_HORN")
class net.minecraft.world.entity.boss.enderdragon.EnderDragon
  DATA_PHASE = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.boss.enderdragon.EnderDragon.class, "DATA_PHASE")
class net.minecraft.world.entity.item.FallingBlockEntity
  DATA_START_POS = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.item.FallingBlockEntity.class, "DATA_START_POS")
class net.minecraft.world.entity.monster.Shulker
  DATA_ATTACH_FACE_ID = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.monster.Shulker.class, "DATA_ATTACH_FACE_ID")
  DATA_COLOR_ID = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.monster.Shulker.class, "DATA_COLOR_ID")
  DATA_PEEK_ID = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.monster.Shulker.class, "DATA_PEEK_ID")
class net.minecraft.world.entity.monster.Skeleton
  DATA_STRAY_CONVERSION_ID = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.monster.Skeleton.class, "DATA_STRAY_CONVERSION_ID")
class net.minecraft.world.entity.monster.Vex
  DATA_FLAGS_ID = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.monster.Vex.class, "DATA_FLAGS_ID")
class net.minecraft.world.entity.monster.Zombie
  DATA_DROWNED_CONVERSION_ID = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.monster.Zombie.class, "DATA_DROWNED_CONVERSION_ID")
class net.minecraft.world.entity.monster.ZombieVillager
  DATA_CONVERTING_ID = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.monster.ZombieVillager.class, "DATA_CONVERTING_ID")
class net.minecraft.world.entity.monster.piglin.AbstractPiglin
  DATA_IMMUNE_TO_ZOMBIFICATION = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.monster.piglin.AbstractPiglin.class, "DATA_IMMUNE_TO_ZOMBIFICATION")
class net.minecraft.world.entity.projectile.FireworkRocketEntity
  DATA_ATTACHED_TO_TARGET = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.projectile.FireworkRocketEntity.class, "DATA_ATTACHED_TO_TARGET")
  DATA_ID_FIREWORKS_ITEM = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.projectile.FireworkRocketEntity.class, "DATA_ID_FIREWORKS_ITEM")
  DATA_SHOT_AT_ANGLE = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.projectile.FireworkRocketEntity.class, "DATA_SHOT_AT_ANGLE")
class net.minecraft.world.entity.projectile.FishingHook
  DATA_HOOKED_ENTITY = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.projectile.FishingHook.class, "DATA_HOOKED_ENTITY")
class net.minecraft.world.entity.raid.Raider
  IS_CELEBRATING = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.raid.Raider.class, "IS_CELEBRATING")
class net.minecraft.world.entity.vehicle.MinecartCommandBlock
  DATA_ID_COMMAND_NAME = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.vehicle.MinecartCommandBlock.class, "DATA_ID_COMMAND_NAME")
class net.minecraft.world.entity.vehicle.VehicleEntity
  DATA_ID_DAMAGE = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.vehicle.VehicleEntity.class, "DATA_ID_DAMAGE")
  DATA_ID_HURT = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.vehicle.VehicleEntity.class, "DATA_ID_HURT")
  DATA_ID_HURTDIR = org.domi.furnace.compat.nms.NmsEntityData.accessor(net.minecraft.world.entity.vehicle.VehicleEntity.class, "DATA_ID_HURTDIR")
