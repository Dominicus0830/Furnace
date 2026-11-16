#!/usr/bin/env python3
# 바닐라 1.21.1 블록 엔티티 클래스 계층(Mojang 이름)을 shim 소스로 만든다 — N2b-6.
# 플러그인이 `instanceof ChestBlockEntity`·`SignBlockEntity`·`AbstractFurnaceBlockEntity` 처럼 종류별 클래스로 가르는 코드가 돌게 하려는 것이다.
# 종류별 필드·메서드(컨테이너·틱 등)는 두지 않는다 — 저장·읽기는 BlockEntity 가 서버의 NBT 로 한다.
# 계층은 바닐라 1.21.1 BlockEntityType 등록 표와 각 클래스의 extends 를 따른다(타입 id → 클래스).
# 사용: python3 tools/compat/gen-nms-block-entities.py   (저장소 루트에서)
import os

PKG = "net.minecraft.world.level.block.entity"
BASE = "furnace-compat-paper/src/main/java/net/minecraft/world/level/block/entity"
TABLE = "furnace-compat-paper/src/main/java/org/domi/furnace/compat/nms/NmsBlockEntityClasses.java"

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

GEN = "// 생성 파일 — tools/compat/gen-nms-block-entities.py 로 만든다(손으로 고치지 않는다).\n"


# 안에 든 열거형(패킷 shim 이 이 클래스의 중첩 타입을 쓴다 — 손으로 쓴 클래스가 있으면 shimgen 은 그 바깥 클래스를 만들지 않는다)
NESTED = {
    "JigsawBlockEntity": """
    /** 바닐라 JigsawBlockEntity.JointType(직소 블록 이음 종류). */
    public enum JointType {
        ROLLABLE, ALIGNED
    }
""",
    "StructureBlockEntity": """
    /** 바닐라 StructureBlockEntity.UpdateType(구조 블록 갱신 종류). */
    public enum UpdateType {
        UPDATE_DATA, SAVE_AREA, LOAD_AREA, SCAN_AREA
    }
""",
    "CommandBlockEntity": """
    /** 바닐라 CommandBlockEntity.Mode(명령 블록 종류). */
    public enum Mode {
        SEQUENCE, AUTO, REDSTONE
    }
""",
}

# 바닐라에서 하위 패키지에 있는 클래스
SUBPKG = {"VaultBlockEntity": "vault"}


def pkg_of(name):
    return PKG + ("." + SUBPKG[name] if name in SUBPKG else "")


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


for name, parent in ABSTRACT:
    write(f"{BASE}/{name}.java", f"""{GEN}package {PKG};

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 바닐라 {name}(Mojang 이름 shim, N2b-6). 플러그인이 종류별 클래스로 가르는 {{@code instanceof}} 와 형변환이 돌게 하는 계층 자리다 —
 * 종류별 필드·메서드(컨테이너·틱 등)는 두지 않았고 저장은 {{@link BlockEntity}} 가 서버의 NBT 로 한다.
 */
public abstract class {name} extends {parent} {{

    protected {name}(BlockEntityType<?> type, BlockPos pos, BlockState state) {{ super(type, pos, state); }}
}}
""")

CONCRETE_NAMES = {c[1] for c in CONCRETE}
for tid, name, parent in CONCRETE:
    sub = SUBPKG.get(name)
    extra = f"import {PKG}.BlockEntity;\nimport {PKG}.BlockEntityType;\n" if sub else ""
    nested = NESTED.get(name, "")
    write(f"{BASE}/{sub + '/' if sub else ''}{name}.java", f"""{GEN}package {pkg_of(name)};

import net.minecraft.core.BlockPos;
{extra}import net.minecraft.world.level.block.state.BlockState;

/**
 * 바닐라 {name}(Mojang 이름 shim, N2b-6) — 블록 엔티티 종류 {{@code minecraft:{tid}}}. 종류별 필드·메서드는 두지 않았고
 * 플러그인이 종류별 클래스로 가르는 {{@code instanceof}} 와 형변환이 돌게 하는 계층 자리다(저장은 {{@link BlockEntity}} 가 서버의 NBT 로 한다).
 */
public class {name} extends {parent} {{

    public {name}(BlockPos pos, BlockState state) {{ this({'BlockEntityType.furnaceOf("minecraft:%s")' % tid}, pos, state); }}

    /** 하위 종류가 쓴다(바닐라는 {name} 이 종류를 받는 생성자를 둔다). */
    protected {name}(BlockEntityType<?> type, BlockPos pos, BlockState state) {{ super(type, pos, state); }}
{nested}}}
""")

# 종류 → 인스턴스 표
cases = "\n".join(f'            case "minecraft:{tid}" -> new {name}(pos, state);' for tid, name, _ in CONCRETE)
imports = "\n".join(f"import {pkg_of(name)}.{name};" for _, name, _ in sorted(CONCRETE, key=lambda c: c[1]))
write(TABLE, f"""{GEN}package org.domi.furnace.compat.nms;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
{imports}

/**
 * 블록 엔티티 종류 id → 바닐라 1.21.1 클래스 표(N2b-6). {{@code EntityBlock.newBlockEntity}} 가 종류에 맞는 클래스를 만들어 주어,
 * 플러그인의 {{@code instanceof ChestBlockEntity}}·{{@code SignBlockEntity}}·{{@code AbstractFurnaceBlockEntity}} 같은 종류별 판별이 Paper 와 같게 돈다.
 * 표에 없는 종류(바닐라 1.21.1 에 없는 id)는 종류만 아는 일반 {{@link BlockEntity}} 로 만든다.
 */
public final class NmsBlockEntityClasses {{

    private NmsBlockEntityClasses() {{}}

    /** 그 종류의 블록 엔티티(클래스는 바닐라와 같다). */
    public static BlockEntity create(String typeId, BlockPos pos, BlockState state) {{
        return switch (typeId) {{
{cases}
            default -> new BlockEntity(BlockEntityType.furnaceOf(typeId), pos, state) {{}};
        }};
    }}
}}
""")
print("abstract", len(ABSTRACT), "concrete", len(CONCRETE))
