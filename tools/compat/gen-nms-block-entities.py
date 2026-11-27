#!/usr/bin/env python3
# 바닐라 1.21.1 블록 엔티티 클래스 계층(Mojang 이름)을 shim 소스로 만든다 — N2b-6.
# 플러그인이 `instanceof ChestBlockEntity`·`SignBlockEntity`·`AbstractFurnaceBlockEntity` 처럼 종류별 클래스로 가르는 코드가 돌게 하려는 것이다.
# 종류별 필드·메서드(컨테이너·틱 등)는 두지 않는다 — 저장·읽기는 BlockEntity 가 서버의 NBT 로 한다.
# 계층은 바닐라 1.21.1 BlockEntityType 등록 표와 각 클래스의 extends 를 따른다(타입 id → 클래스).
# 2026-11-24(T-229 이전): 손 shim 이 아니라 조각(src/shims/java)과 명세(src/shimspec/block-entity.spec)를 낸다 — 머리(접근·상위·인터페이스)는 생성기가 잠금 파일로 붙인다.
# 사용: python3 tools/compat/gen-nms-block-entities.py   (저장소 루트에서) 뒤에 :furnace-compat-paper:shimLock
import os

PKG = "net.minecraft.world.level.block.entity"
BASE = "furnace-compat-paper/src/shims/java/net/minecraft/world/level/block/entity"
SPEC = "furnace-compat-paper/src/shimspec/block-entity.spec"

# 추상 중간 클래스(바닐라: abstract) — 생성자는 (BlockEntityType, BlockPos, BlockState)
ABSTRACT = [
    ("BaseContainerBlockEntity", "BlockEntity"),
    ("RandomizableContainerBlockEntity", "BaseContainerBlockEntity"),
    ("AbstractFurnaceBlockEntity", "BaseContainerBlockEntity"),
]

# (타입 id, 클래스, 바닐라 상위 클래스) — 상위 클래스는 위 추상 클래스이거나 아래 다른 구체 클래스
CONCRETE = [
    ("banner", "BannerBlockEntity", "BlockEntity"),
    ("barrel", "BarrelBlockEntity", "RandomizableContainerBlockEntity"),
    ("beacon", "BeaconBlockEntity", "BlockEntity"),
    ("bed", "BedBlockEntity", "BlockEntity"),
    ("beehive", "BeehiveBlockEntity", "BlockEntity"),
    ("bell", "BellBlockEntity", "BlockEntity"),
    ("blast_furnace", "BlastFurnaceBlockEntity", "AbstractFurnaceBlockEntity"),
    ("brewing_stand", "BrewingStandBlockEntity", "BaseContainerBlockEntity"),
    ("brushable_block", "BrushableBlockEntity", "BlockEntity"),
    ("sculk_sensor", "SculkSensorBlockEntity", "BlockEntity"),
    ("calibrated_sculk_sensor", "CalibratedSculkSensorBlockEntity", "SculkSensorBlockEntity"),
    ("campfire", "CampfireBlockEntity", "BlockEntity"),
    ("chest", "ChestBlockEntity", "RandomizableContainerBlockEntity"),
    ("trapped_chest", "TrappedChestBlockEntity", "ChestBlockEntity"),
    ("chiseled_bookshelf", "ChiseledBookShelfBlockEntity", "BlockEntity"),
    ("command_block", "CommandBlockEntity", "BlockEntity"),
    ("comparator", "ComparatorBlockEntity", "BlockEntity"),
    ("conduit", "ConduitBlockEntity", "BlockEntity"),
    ("crafter", "CrafterBlockEntity", "RandomizableContainerBlockEntity"),
    ("daylight_detector", "DaylightDetectorBlockEntity", "BlockEntity"),
    ("decorated_pot", "DecoratedPotBlockEntity", "BlockEntity"),
    ("dispenser", "DispenserBlockEntity", "RandomizableContainerBlockEntity"),
    ("dropper", "DropperBlockEntity", "DispenserBlockEntity"),
    ("enchanting_table", "EnchantingTableBlockEntity", "BlockEntity"),
    ("ender_chest", "EnderChestBlockEntity", "BlockEntity"),
    ("end_portal", "TheEndPortalBlockEntity", "BlockEntity"),
    ("end_gateway", "TheEndGatewayBlockEntity", "TheEndPortalBlockEntity"),
    ("furnace", "FurnaceBlockEntity", "AbstractFurnaceBlockEntity"),
    ("smoker", "SmokerBlockEntity", "AbstractFurnaceBlockEntity"),
    ("sign", "SignBlockEntity", "BlockEntity"),
    ("hanging_sign", "HangingSignBlockEntity", "SignBlockEntity"),
    ("hopper", "HopperBlockEntity", "RandomizableContainerBlockEntity"),
    ("jigsaw", "JigsawBlockEntity", "BlockEntity"),
    ("jukebox", "JukeboxBlockEntity", "BlockEntity"),
    ("lectern", "LecternBlockEntity", "BlockEntity"),
    ("piston", "PistonMovingBlockEntity", "BlockEntity"),
    ("sculk_catalyst", "SculkCatalystBlockEntity", "BlockEntity"),
    ("sculk_shrieker", "SculkShriekerBlockEntity", "BlockEntity"),
    ("shulker_box", "ShulkerBoxBlockEntity", "RandomizableContainerBlockEntity"),
    ("skull", "SkullBlockEntity", "BlockEntity"),
    ("mob_spawner", "SpawnerBlockEntity", "BlockEntity"),
    ("structure_block", "StructureBlockEntity", "BlockEntity"),
    ("trial_spawner", "TrialSpawnerBlockEntity", "BlockEntity"),
    ("vault", "VaultBlockEntity", "BlockEntity"),
]

# 바닐라에서 Clearable 인 클래스(Container 를 거치거나 직접 — T-211 3단계). 하위 클래스는 물려받는다.
CLEARABLE = {"BaseContainerBlockEntity", "CampfireBlockEntity", "ChiseledBookShelfBlockEntity", "DecoratedPotBlockEntity", "JukeboxBlockEntity",
             "LecternBlockEntity"}

CLEAR_BODY = """
    /** 바닐라 clearContent(Clearable) — 월드의 블록 엔티티면 서버 블록 엔티티를 바닐라처럼 비운다(T-211 3단계). */
    @Override
    public void clearContent() { furnaceClearContent(); }
"""


def implements(name):
    return " implements net.minecraft.world.Clearable" if name in CLEARABLE else ""


def clear_body(name):
    return CLEAR_BODY if name in CLEARABLE else ""


GEN = "// 생성 조각 — tools/compat/gen-nms-block-entities.py 로 만든다(손으로 고치지 않는다).\n"
spec_lines = ["# 블록 엔티티 종류 계층(영역 1 손 shim 이전). 생성 파일 — tools/compat/gen-nms-block-entities.py. 머리는 잠금 파일, 몸통은 src/shims/java 조각"]


# 안에 든 열거형(패킷 shim 이 이 클래스의 중첩 타입을 쓴다 — 손으로 쓴 클래스가 있으면 shimgen 은 그 바깥 클래스를 만들지 않는다)
NESTED = {
    "JigsawBlockEntity": """
    /** 바닐라 JigsawBlockEntity.JointType(직소 블록 이음 종류). */
    public enum JointType {
        ROLLABLE, ALIGNED
    }
""",
    "BeaconBlockEntity": """
    /**
     * 바닐라 BeaconBlockEntity.playSound(level, pos, sound): 그 칸 가운데에서 블록 소리 분류로 크기·높이 1(level.playSound(null, pos, …)) — 그 칸
     * 근처 플레이어가 듣는다(T-198 — FAWE 가 신호기를 지운 뒤 끄는 소리를 낸다). 그 칸의 주인 스레드(Bukkit 동기 작업 포함)에서 부른다.
     */
    public static void playSound(net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.sounds.SoundEvent sound) {
        level.playSound(null, pos, sound, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
    }
""",
}

# 중첩 열거형은 명세에 `Outer$Name` 으로 상수 전부를 적는다(조각 안에 손으로 쓰면 손 shim 이 돼 명세를 못 붙인다)
NESTED_ENUMS = {
    "StructureBlockEntity": [("UpdateType", ["UPDATE_DATA", "SAVE_AREA", "LOAD_AREA", "SCAN_AREA"])],
    "CommandBlockEntity": [("Mode", ["SEQUENCE", "AUTO", "REDSTONE"])],
}

# 바닐라에서 block.entity 밖 패키지에 있는 클래스(움직이는 피스톤은 block.piston — 검토 N1)
OTHER_PKG = {"VaultBlockEntity": PKG + ".vault", "PistonMovingBlockEntity": "net.minecraft.world.level.block.piston"}


def pkg_of(name):
    return OTHER_PKG.get(name, PKG)


def path_of(name):
    return "furnace-compat-paper/src/shims/java/" + pkg_of(name).replace(".", "/") + "/" + name + ".java"


# 2026-11-24: 블록 엔티티는 모두 조각으로 옮겼다. 옛 손 방식(main/java 로 냄)은 HAND 에 이름을 넣으면 쓴다(지금은 비었다).
HAND = set()
# 생성기 스레드가 손으로 옮긴 것(조각 + enums.spec) — 이 스크립트는 건드리지 않는다
SKIP = {"JigsawBlockEntity"}
HAND_GEN = "// 생성 파일 — tools/compat/gen-nms-block-entities.py 로 만든다(손으로 고치지 않는다).\n"
MAIN = "furnace-compat-paper/src/main/java/"


# 몸통을 손으로 쓴 조각(Container 몸통 — NmsBlockContainer 로 잇는다) — 이 스크립트는 명세 줄만 내고 조각 파일은 덮어쓰지 않는다
HAND_FRAGMENT = {"BaseContainerBlockEntity", "DecoratedPotBlockEntity", "JukeboxBlockEntity", "ChiseledBookShelfBlockEntity"}


def write(path, text):
    if "/shims/" in path and os.path.basename(path)[:-5] in HAND_FRAGMENT:
        return
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


for name, parent in ABSTRACT:
    if name in HAND:
        write(MAIN + PKG.replace(".", "/") + "/" + name + ".java", f"""{HAND_GEN}package {PKG};

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 바닐라 {name}(Mojang 이름 shim, N2b-6). 플러그인이 종류별 클래스로 가르는 {{@code instanceof}} 와 형변환이 돌게 하는 계층 자리다 —
 * 종류별 필드·메서드(컨테이너·틱 등)는 두지 않았고 저장은 {{@link BlockEntity}} 가 서버의 NBT 로 한다.
 */
public abstract class {name} extends {parent}{implements(name)} {{

    protected {name}(BlockEntityType<?> type, BlockPos pos, BlockState state) {{ super(type, pos, state); }}
{clear_body(name)}}}
""")
        continue
    spec_lines.append(f"class {PKG}.{name}")
    write(f"{BASE}/{name}.java", f"""{GEN}package {PKG};

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 바닐라 {name}(Mojang 이름 shim, N2b-6). 플러그인이 종류별 클래스로 가르는 {{@code instanceof}} 와 형변환이 돌게 하는 계층 자리다 —
 * 종류별 필드·메서드(컨테이너·틱 등)는 두지 않았고 저장은 {{@link BlockEntity}} 가 서버의 NBT 로 한다.
 */
class {name} {{

    protected {name}(BlockEntityType<?> type, BlockPos pos, BlockState state) {{ super(type, pos, state); }}
{clear_body(name)}}}
""")

CONCRETE_NAMES = {c[1] for c in CONCRETE}
for tid, name, parent in CONCRETE:
    if name in SKIP: continue
    other = pkg_of(name) != PKG
    if name in HAND:
        extra = f"import {PKG}.BlockEntity;\nimport {PKG}.BlockEntityType;\n" if other else ""
        write(MAIN + pkg_of(name).replace(".", "/") + "/" + name + ".java", f"""{HAND_GEN}package {pkg_of(name)};

import net.minecraft.core.BlockPos;
{extra}import net.minecraft.world.level.block.state.BlockState;

/**
 * 바닐라 {name}(Mojang 이름 shim, N2b-6) — 블록 엔티티 종류 {{@code minecraft:{tid}}}. 종류별 필드·메서드는 두지 않았고
 * 플러그인이 종류별 클래스로 가르는 {{@code instanceof}} 와 형변환이 돌게 하는 계층 자리다(저장은 {{@link BlockEntity}} 가 서버의 NBT 로 한다).
 */
public class {name} extends {parent}{implements(name)} {{

    public {name}(BlockPos pos, BlockState state) {{ this({'BlockEntityType.furnaceOf("minecraft:%s")' % tid}, pos, state); }}

    /** 하위 종류가 쓴다(바닐라는 {name} 이 종류를 받는 생성자를 둔다). */
    protected {name}(BlockEntityType<?> type, BlockPos pos, BlockState state) {{ super(type, pos, state); }}
{NESTED.get(name, "")}{clear_body(name)}}}
""")
        continue
    spec_lines.append(f"class {pkg_of(name)}.{name}")
    for en, consts in NESTED_ENUMS.get(name, []):
        spec_lines.append(f"class {pkg_of(name)}.{name}${en}")
        spec_lines += ["  " + c for c in consts]
    extra = f"import {PKG}.BlockEntity;\nimport {PKG}.BlockEntityType;\n" if other else ""
    nested = NESTED.get(name, "")
    write(path_of(name), f"""{GEN}package {pkg_of(name)};

import net.minecraft.core.BlockPos;
{extra}import net.minecraft.world.level.block.state.BlockState;

/**
 * 바닐라 {name}(Mojang 이름 shim, N2b-6) — 블록 엔티티 종류 {{@code minecraft:{tid}}}. 종류별 필드·메서드는 두지 않았고
 * 플러그인이 종류별 클래스로 가르는 {{@code instanceof}} 와 형변환이 돌게 하는 계층 자리다(저장은 {{@link BlockEntity}} 가 서버의 NBT 로 한다).
 */
class {name} {{

    public {name}(BlockPos pos, BlockState state) {{ this({'BlockEntityType.furnaceOf("minecraft:%s")' % tid}, pos, state); }}

    /** 하위 종류가 쓴다(바닐라는 {name} 이 종류를 받는 생성자를 둔다). */
    protected {name}(BlockEntityType<?> type, BlockPos pos, BlockState state) {{ super(type, pos, state); }}
{nested}{clear_body(name)}}}
""")

# 종류 → 인스턴스 표(NmsBlockEntityClasses)는 2026-11-24 부터 손으로 고친다 — newBlockEntity 가 더해져 이 생성기가 더는 쓰지 않는다.
write(SPEC, "\n".join(spec_lines) + "\n")
print("abstract", len(ABSTRACT), "concrete", len(CONCRETE))
