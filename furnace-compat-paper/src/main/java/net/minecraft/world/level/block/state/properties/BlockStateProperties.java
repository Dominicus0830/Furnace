package net.minecraft.world.level.block.state.properties;

import net.minecraft.core.FrontAndTop;
import org.domi.furnace.api.world.BlockType.PropertyInfo.Kind;
import org.domi.furnace.compat.nms.NmsBlockProperties;

/**
 * 블록 상태 속성 상수(Mojang 이름 shim, N2b-6). 필드 이름·종류·속성 이름·값은 바닐라 1.21.1 그대로이고(tools/compat 생성기 — Paper jar 의 바이트코드에서
 * 이름만 뽑았다), 값은 블록 상태가 쓰는 속성 객체와 같은 객체다(같은 이름·종류·값 집합이면 블록 표의 그 속성 — 바닐라에서도 블록들이 이 상수를 나눠 쓴다).
 * FAWE 는 이 클래스의 필드를 훑어 속성 이름 → 속성 목록 표를 만든다.
 */
@SuppressWarnings("unchecked")
public class BlockStateProperties {

    public BlockStateProperties() {}

    public static final BooleanProperty ATTACHED = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "attached", "true", "false");
    public static final BooleanProperty BOTTOM = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "bottom", "true", "false");
    public static final BooleanProperty CONDITIONAL = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "conditional", "true", "false");
    public static final BooleanProperty DISARMED = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "disarmed", "true", "false");
    public static final BooleanProperty DRAG = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "drag", "true", "false");
    public static final BooleanProperty ENABLED = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "enabled", "true", "false");
    public static final BooleanProperty EXTENDED = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "extended", "true", "false");
    public static final BooleanProperty EYE = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "eye", "true", "false");
    public static final BooleanProperty FALLING = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "falling", "true", "false");
    public static final BooleanProperty HANGING = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "hanging", "true", "false");
    public static final BooleanProperty HAS_BOTTLE_0 = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "has_bottle_0", "true", "false");
    public static final BooleanProperty HAS_BOTTLE_1 = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "has_bottle_1", "true", "false");
    public static final BooleanProperty HAS_BOTTLE_2 = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "has_bottle_2", "true", "false");
    public static final BooleanProperty HAS_RECORD = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "has_record", "true", "false");
    public static final BooleanProperty HAS_BOOK = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "has_book", "true", "false");
    public static final BooleanProperty INVERTED = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "inverted", "true", "false");
    public static final BooleanProperty IN_WALL = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "in_wall", "true", "false");
    public static final BooleanProperty LIT = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "lit", "true", "false");
    public static final BooleanProperty LOCKED = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "locked", "true", "false");
    public static final BooleanProperty OCCUPIED = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "occupied", "true", "false");
    public static final BooleanProperty OPEN = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "open", "true", "false");
    public static final BooleanProperty PERSISTENT = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "persistent", "true", "false");
    public static final BooleanProperty POWERED = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "powered", "true", "false");
    public static final BooleanProperty SHORT = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "short", "true", "false");
    public static final BooleanProperty SIGNAL_FIRE = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "signal_fire", "true", "false");
    public static final BooleanProperty SNOWY = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "snowy", "true", "false");
    public static final BooleanProperty TRIGGERED = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "triggered", "true", "false");
    public static final BooleanProperty UNSTABLE = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "unstable", "true", "false");
    public static final BooleanProperty WATERLOGGED = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "waterlogged", "true", "false");
    public static final BooleanProperty BERRIES = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "berries", "true", "false");
    public static final BooleanProperty BLOOM = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "bloom", "true", "false");
    public static final BooleanProperty SHRIEKING = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "shrieking", "true", "false");
    public static final BooleanProperty CAN_SUMMON = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "can_summon", "true", "false");
    public static final EnumProperty<net.minecraft.core.Direction.Axis> HORIZONTAL_AXIS = (EnumProperty<net.minecraft.core.Direction.Axis>) NmsBlockProperties.vanilla(Kind.ENUM, "axis", "x", "z");
    public static final EnumProperty<net.minecraft.core.Direction.Axis> AXIS = (EnumProperty<net.minecraft.core.Direction.Axis>) NmsBlockProperties.vanillaEnum("axis", net.minecraft.core.Direction.Axis.class);
    public static final BooleanProperty UP = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "up", "true", "false");
    public static final BooleanProperty DOWN = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "down", "true", "false");
    public static final BooleanProperty NORTH = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "north", "true", "false");
    public static final BooleanProperty EAST = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "east", "true", "false");
    public static final BooleanProperty SOUTH = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "south", "true", "false");
    public static final BooleanProperty WEST = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "west", "true", "false");
    public static final DirectionProperty FACING = (DirectionProperty) NmsBlockProperties.vanilla(Kind.ENUM, "facing", "north", "east", "south", "west", "up", "down");
    public static final DirectionProperty FACING_HOPPER = (DirectionProperty) NmsBlockProperties.vanilla(Kind.ENUM, "facing", "down", "north", "south", "west", "east");
    public static final DirectionProperty HORIZONTAL_FACING = (DirectionProperty) NmsBlockProperties.vanilla(Kind.ENUM, "facing", "north", "south", "west", "east");
    public static final IntegerProperty FLOWER_AMOUNT = (IntegerProperty) NmsBlockProperties.vanilla(Kind.INTEGER, "flower_amount", "1", "2", "3", "4");
    public static final EnumProperty<net.minecraft.core.FrontAndTop> ORIENTATION = (EnumProperty<net.minecraft.core.FrontAndTop>) NmsBlockProperties.vanillaEnum("orientation", net.minecraft.core.FrontAndTop.class);
    public static final EnumProperty<AttachFace> ATTACH_FACE = (EnumProperty<AttachFace>) NmsBlockProperties.vanillaEnum("face", AttachFace.class);
    public static final EnumProperty<BellAttachType> BELL_ATTACHMENT = (EnumProperty<BellAttachType>) NmsBlockProperties.vanillaEnum("attachment", BellAttachType.class);
    public static final EnumProperty<WallSide> EAST_WALL = (EnumProperty<WallSide>) NmsBlockProperties.vanillaEnum("east", WallSide.class);
    public static final EnumProperty<WallSide> NORTH_WALL = (EnumProperty<WallSide>) NmsBlockProperties.vanillaEnum("north", WallSide.class);
    public static final EnumProperty<WallSide> SOUTH_WALL = (EnumProperty<WallSide>) NmsBlockProperties.vanillaEnum("south", WallSide.class);
    public static final EnumProperty<WallSide> WEST_WALL = (EnumProperty<WallSide>) NmsBlockProperties.vanillaEnum("west", WallSide.class);
    public static final EnumProperty<RedstoneSide> EAST_REDSTONE = (EnumProperty<RedstoneSide>) NmsBlockProperties.vanillaEnum("east", RedstoneSide.class);
    public static final EnumProperty<RedstoneSide> NORTH_REDSTONE = (EnumProperty<RedstoneSide>) NmsBlockProperties.vanillaEnum("north", RedstoneSide.class);
    public static final EnumProperty<RedstoneSide> SOUTH_REDSTONE = (EnumProperty<RedstoneSide>) NmsBlockProperties.vanillaEnum("south", RedstoneSide.class);
    public static final EnumProperty<RedstoneSide> WEST_REDSTONE = (EnumProperty<RedstoneSide>) NmsBlockProperties.vanillaEnum("west", RedstoneSide.class);
    public static final EnumProperty<DoubleBlockHalf> DOUBLE_BLOCK_HALF = (EnumProperty<DoubleBlockHalf>) NmsBlockProperties.vanillaEnum("half", DoubleBlockHalf.class);
    public static final EnumProperty<Half> HALF = (EnumProperty<Half>) NmsBlockProperties.vanillaEnum("half", Half.class);
    public static final EnumProperty<RailShape> RAIL_SHAPE = (EnumProperty<RailShape>) NmsBlockProperties.vanillaEnum("shape", RailShape.class);
    public static final EnumProperty<RailShape> RAIL_SHAPE_STRAIGHT = (EnumProperty<RailShape>) NmsBlockProperties.vanilla(Kind.ENUM, "shape", "north_south", "east_west", "ascending_east", "ascending_west", "ascending_north", "ascending_south");
    public static final IntegerProperty AGE_1 = (IntegerProperty) NmsBlockProperties.vanilla(Kind.INTEGER, "age", "0", "1");
    public static final IntegerProperty AGE_2 = (IntegerProperty) NmsBlockProperties.vanilla(Kind.INTEGER, "age", "0", "1", "2");
    public static final IntegerProperty AGE_3 = (IntegerProperty) NmsBlockProperties.vanilla(Kind.INTEGER, "age", "0", "1", "2", "3");
    public static final IntegerProperty AGE_4 = (IntegerProperty) NmsBlockProperties.vanilla(Kind.INTEGER, "age", "0", "1", "2", "3", "4");
    public static final IntegerProperty AGE_5 = (IntegerProperty) NmsBlockProperties.vanilla(Kind.INTEGER, "age", "0", "1", "2", "3", "4", "5");
    public static final IntegerProperty AGE_7 = (IntegerProperty) NmsBlockProperties.vanilla(Kind.INTEGER, "age", "0", "1", "2", "3", "4", "5", "6", "7");
    public static final IntegerProperty AGE_15 = (IntegerProperty) NmsBlockProperties.vanilla(Kind.INTEGER, "age", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15");
    public static final IntegerProperty AGE_25 = (IntegerProperty) NmsBlockProperties.vanilla(Kind.INTEGER, "age", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25");
    public static final IntegerProperty BITES = (IntegerProperty) NmsBlockProperties.vanilla(Kind.INTEGER, "bites", "0", "1", "2", "3", "4", "5", "6");
    public static final IntegerProperty CANDLES = (IntegerProperty) NmsBlockProperties.vanilla(Kind.INTEGER, "candles", "1", "2", "3", "4");
    public static final IntegerProperty DELAY = (IntegerProperty) NmsBlockProperties.vanilla(Kind.INTEGER, "delay", "1", "2", "3", "4");
    public static final IntegerProperty DISTANCE = (IntegerProperty) NmsBlockProperties.vanilla(Kind.INTEGER, "distance", "1", "2", "3", "4", "5", "6", "7");
    public static final IntegerProperty EGGS = (IntegerProperty) NmsBlockProperties.vanilla(Kind.INTEGER, "eggs", "1", "2", "3", "4");
    public static final IntegerProperty HATCH = (IntegerProperty) NmsBlockProperties.vanilla(Kind.INTEGER, "hatch", "0", "1", "2");
    public static final IntegerProperty LAYERS = (IntegerProperty) NmsBlockProperties.vanilla(Kind.INTEGER, "layers", "1", "2", "3", "4", "5", "6", "7", "8");
    public static final IntegerProperty LEVEL_CAULDRON = (IntegerProperty) NmsBlockProperties.vanilla(Kind.INTEGER, "level", "1", "2", "3");
    public static final IntegerProperty LEVEL_COMPOSTER = (IntegerProperty) NmsBlockProperties.vanilla(Kind.INTEGER, "level", "0", "1", "2", "3", "4", "5", "6", "7", "8");
    public static final IntegerProperty LEVEL_FLOWING = (IntegerProperty) NmsBlockProperties.vanilla(Kind.INTEGER, "level", "1", "2", "3", "4", "5", "6", "7", "8");
    public static final IntegerProperty LEVEL_HONEY = (IntegerProperty) NmsBlockProperties.vanilla(Kind.INTEGER, "honey_level", "0", "1", "2", "3", "4", "5");
    public static final IntegerProperty LEVEL = (IntegerProperty) NmsBlockProperties.vanilla(Kind.INTEGER, "level", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15");
    public static final IntegerProperty MOISTURE = (IntegerProperty) NmsBlockProperties.vanilla(Kind.INTEGER, "moisture", "0", "1", "2", "3", "4", "5", "6", "7");
    public static final IntegerProperty NOTE = (IntegerProperty) NmsBlockProperties.vanilla(Kind.INTEGER, "note", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24");
    public static final IntegerProperty PICKLES = (IntegerProperty) NmsBlockProperties.vanilla(Kind.INTEGER, "pickles", "1", "2", "3", "4");
    public static final IntegerProperty POWER = (IntegerProperty) NmsBlockProperties.vanilla(Kind.INTEGER, "power", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15");
    public static final IntegerProperty STAGE = (IntegerProperty) NmsBlockProperties.vanilla(Kind.INTEGER, "stage", "0", "1");
    public static final IntegerProperty STABILITY_DISTANCE = (IntegerProperty) NmsBlockProperties.vanilla(Kind.INTEGER, "distance", "0", "1", "2", "3", "4", "5", "6", "7");
    public static final IntegerProperty RESPAWN_ANCHOR_CHARGES = (IntegerProperty) NmsBlockProperties.vanilla(Kind.INTEGER, "charges", "0", "1", "2", "3", "4");
    public static final IntegerProperty ROTATION_16 = (IntegerProperty) NmsBlockProperties.vanilla(Kind.INTEGER, "rotation", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15");
    public static final EnumProperty<BedPart> BED_PART = (EnumProperty<BedPart>) NmsBlockProperties.vanillaEnum("part", BedPart.class);
    public static final EnumProperty<ChestType> CHEST_TYPE = (EnumProperty<ChestType>) NmsBlockProperties.vanillaEnum("type", ChestType.class);
    public static final EnumProperty<ComparatorMode> MODE_COMPARATOR = (EnumProperty<ComparatorMode>) NmsBlockProperties.vanillaEnum("mode", ComparatorMode.class);
    public static final EnumProperty<DoorHingeSide> DOOR_HINGE = (EnumProperty<DoorHingeSide>) NmsBlockProperties.vanillaEnum("hinge", DoorHingeSide.class);
    public static final EnumProperty<NoteBlockInstrument> NOTEBLOCK_INSTRUMENT = (EnumProperty<NoteBlockInstrument>) NmsBlockProperties.vanillaEnum("instrument", NoteBlockInstrument.class);
    public static final EnumProperty<PistonType> PISTON_TYPE = (EnumProperty<PistonType>) NmsBlockProperties.vanillaEnum("type", PistonType.class);
    public static final EnumProperty<SlabType> SLAB_TYPE = (EnumProperty<SlabType>) NmsBlockProperties.vanillaEnum("type", SlabType.class);
    public static final EnumProperty<StairsShape> STAIRS_SHAPE = (EnumProperty<StairsShape>) NmsBlockProperties.vanillaEnum("shape", StairsShape.class);
    public static final EnumProperty<StructureMode> STRUCTUREBLOCK_MODE = (EnumProperty<StructureMode>) NmsBlockProperties.vanillaEnum("mode", StructureMode.class);
    public static final EnumProperty<BambooLeaves> BAMBOO_LEAVES = (EnumProperty<BambooLeaves>) NmsBlockProperties.vanillaEnum("leaves", BambooLeaves.class);
    public static final EnumProperty<Tilt> TILT = (EnumProperty<Tilt>) NmsBlockProperties.vanillaEnum("tilt", Tilt.class);
    public static final DirectionProperty VERTICAL_DIRECTION = (DirectionProperty) NmsBlockProperties.vanilla(Kind.ENUM, "vertical_direction", "up", "down");
    public static final EnumProperty<DripstoneThickness> DRIPSTONE_THICKNESS = (EnumProperty<DripstoneThickness>) NmsBlockProperties.vanillaEnum("thickness", DripstoneThickness.class);
    public static final EnumProperty<SculkSensorPhase> SCULK_SENSOR_PHASE = (EnumProperty<SculkSensorPhase>) NmsBlockProperties.vanillaEnum("sculk_sensor_phase", SculkSensorPhase.class);
    public static final BooleanProperty CHISELED_BOOKSHELF_SLOT_0_OCCUPIED = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "slot_0_occupied", "true", "false");
    public static final BooleanProperty CHISELED_BOOKSHELF_SLOT_1_OCCUPIED = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "slot_1_occupied", "true", "false");
    public static final BooleanProperty CHISELED_BOOKSHELF_SLOT_2_OCCUPIED = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "slot_2_occupied", "true", "false");
    public static final BooleanProperty CHISELED_BOOKSHELF_SLOT_3_OCCUPIED = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "slot_3_occupied", "true", "false");
    public static final BooleanProperty CHISELED_BOOKSHELF_SLOT_4_OCCUPIED = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "slot_4_occupied", "true", "false");
    public static final BooleanProperty CHISELED_BOOKSHELF_SLOT_5_OCCUPIED = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "slot_5_occupied", "true", "false");
    public static final IntegerProperty DUSTED = (IntegerProperty) NmsBlockProperties.vanilla(Kind.INTEGER, "dusted", "0", "1", "2", "3");
    public static final BooleanProperty CRACKED = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "cracked", "true", "false");
    public static final BooleanProperty CRAFTING = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "crafting", "true", "false");
    public static final EnumProperty<net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState> TRIAL_SPAWNER_STATE = (EnumProperty<net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState>) NmsBlockProperties.vanillaEnum("trial_spawner_state", net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState.class);
    public static final EnumProperty<net.minecraft.world.level.block.entity.vault.VaultState> VAULT_STATE = (EnumProperty<net.minecraft.world.level.block.entity.vault.VaultState>) NmsBlockProperties.vanillaEnum("vault_state", net.minecraft.world.level.block.entity.vault.VaultState.class);
    public static final BooleanProperty OMINOUS = (BooleanProperty) NmsBlockProperties.vanilla(Kind.BOOLEAN, "ominous", "true", "false");
}
