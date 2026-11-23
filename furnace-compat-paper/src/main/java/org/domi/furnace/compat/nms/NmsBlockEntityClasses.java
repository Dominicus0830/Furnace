// 생성 파일 — tools/compat/gen-nms-block-entities.py 로 만든다(손으로 고치지 않는다).
package org.domi.furnace.compat.nms;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.entity.CalibratedSculkSensorBlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.entity.ConduitBlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.entity.DaylightDetectorBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.DropperBlockEntity;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.entity.SculkCatalystBlockEntity;
import net.minecraft.world.level.block.entity.SculkSensorBlockEntity;
import net.minecraft.world.level.block.entity.SculkShriekerBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;

/**
 * 블록 엔티티 종류 id → 바닐라 1.21.1 클래스 표(N2b-6). {@code EntityBlock.newBlockEntity} 가 종류에 맞는 클래스를 만들어 주어,
 * 플러그인의 {@code instanceof ChestBlockEntity}·{@code SignBlockEntity}·{@code AbstractFurnaceBlockEntity} 같은 종류별 판별이 Paper 와 같게 돈다.
 * 표에 없는 종류(바닐라 1.21.1 에 없는 id)는 종류만 아는 일반 {@link BlockEntity} 로 만든다.
 */
public final class NmsBlockEntityClasses {

    private NmsBlockEntityClasses() {}

    /**
     * 바닐라 {@code EntityBlock#newBlockEntity(pos, state)} — 블록 엔티티 있는 블록 클래스 전부의 공통 연결(명세 {@code @each}, T-231). 그 상태의 블록 엔티티
     * 종류로 만든 떨어진 블록 엔티티. 움직이는 피스톤처럼 기본 내용이 없는 종류는 바닐라처럼 null.
     */
    public static BlockEntity newBlockEntity(net.minecraft.world.level.block.Block self, BlockPos pos, BlockState state) {
        var bridge = NmsBlockStates.bridge();
        String type = bridge.blockEntityType(state.furnaceStateId());
        if (type == null || bridge.defaultBlockEntityNbt(state.furnaceStateId()) == null) return null;   // 움직이는 피스톤: 바닐라도 null
        return create(type, pos, state);
    }

    /** 그 종류의 블록 엔티티(클래스는 바닐라와 같다). */
    public static BlockEntity create(String typeId, BlockPos pos, BlockState state) {
        return switch (typeId) {
            case "minecraft:banner" -> new BannerBlockEntity(pos, state);
            case "minecraft:barrel" -> new BarrelBlockEntity(pos, state);
            case "minecraft:beacon" -> new BeaconBlockEntity(pos, state);
            case "minecraft:bed" -> new BedBlockEntity(pos, state);
            case "minecraft:beehive" -> new BeehiveBlockEntity(pos, state);
            case "minecraft:bell" -> new BellBlockEntity(pos, state);
            case "minecraft:blast_furnace" -> new BlastFurnaceBlockEntity(pos, state);
            case "minecraft:brewing_stand" -> new BrewingStandBlockEntity(pos, state);
            case "minecraft:brushable_block" -> new BrushableBlockEntity(pos, state);
            case "minecraft:sculk_sensor" -> new SculkSensorBlockEntity(pos, state);
            case "minecraft:calibrated_sculk_sensor" -> new CalibratedSculkSensorBlockEntity(pos, state);
            case "minecraft:campfire" -> new CampfireBlockEntity(pos, state);
            case "minecraft:chest" -> new ChestBlockEntity(pos, state);
            case "minecraft:trapped_chest" -> new TrappedChestBlockEntity(pos, state);
            case "minecraft:chiseled_bookshelf" -> new ChiseledBookShelfBlockEntity(pos, state);
            case "minecraft:command_block" -> new CommandBlockEntity(pos, state);
            case "minecraft:comparator" -> new ComparatorBlockEntity(pos, state);
            case "minecraft:conduit" -> new ConduitBlockEntity(pos, state);
            case "minecraft:crafter" -> new CrafterBlockEntity(pos, state);
            case "minecraft:daylight_detector" -> new DaylightDetectorBlockEntity(pos, state);
            case "minecraft:decorated_pot" -> new DecoratedPotBlockEntity(pos, state);
            case "minecraft:dispenser" -> new DispenserBlockEntity(pos, state);
            case "minecraft:dropper" -> new DropperBlockEntity(pos, state);
            case "minecraft:enchanting_table" -> new EnchantingTableBlockEntity(pos, state);
            case "minecraft:ender_chest" -> new EnderChestBlockEntity(pos, state);
            case "minecraft:end_portal" -> new TheEndPortalBlockEntity(pos, state);
            case "minecraft:end_gateway" -> new TheEndGatewayBlockEntity(pos, state);
            case "minecraft:furnace" -> new FurnaceBlockEntity(pos, state);
            case "minecraft:smoker" -> new SmokerBlockEntity(pos, state);
            case "minecraft:sign" -> new SignBlockEntity(pos, state);
            case "minecraft:hanging_sign" -> new HangingSignBlockEntity(pos, state);
            case "minecraft:hopper" -> new HopperBlockEntity(pos, state);
            case "minecraft:jigsaw" -> new JigsawBlockEntity(pos, state);
            case "minecraft:jukebox" -> new JukeboxBlockEntity(pos, state);
            case "minecraft:lectern" -> new LecternBlockEntity(pos, state);
            case "minecraft:piston" -> new PistonMovingBlockEntity(pos, state);
            case "minecraft:sculk_catalyst" -> new SculkCatalystBlockEntity(pos, state);
            case "minecraft:sculk_shrieker" -> new SculkShriekerBlockEntity(pos, state);
            case "minecraft:shulker_box" -> new ShulkerBoxBlockEntity(pos, state);
            case "minecraft:skull" -> new SkullBlockEntity(pos, state);
            case "minecraft:mob_spawner" -> new SpawnerBlockEntity(pos, state);
            case "minecraft:structure_block" -> new StructureBlockEntity(pos, state);
            case "minecraft:trial_spawner" -> new TrialSpawnerBlockEntity(pos, state);
            case "minecraft:vault" -> new VaultBlockEntity(pos, state);
            default -> new BlockEntity(BlockEntityType.furnaceOf(typeId), pos, state) {};
        };
    }
}
