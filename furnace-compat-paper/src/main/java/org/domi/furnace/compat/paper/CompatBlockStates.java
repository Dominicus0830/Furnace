// 생성됨: tools/compat/gen-bukkit-block-state-classes.py — 손으로 고치려면 이 줄을 지운다.
package org.domi.furnace.compat.paper;

/**
 * 블록 엔티티 종류 → Paper 와 같은 형의 블록 상태(Paper CraftBlockStates 의 등록표). 블록 엔티티가 없는 블록·표에 없는 종류(양조대)는
 * 일반 {@link CompatBlockState}. dimension 이 null 이면 놓이지 않은 상태.
 */
final class CompatBlockStates {

    private CompatBlockStates() {}

    static CompatBlockState create(String dimension, org.domi.furnace.api.world.BlockPos pos, org.bukkit.block.data.BlockData data) {
        String be = Compat.furnace().gameRegistries().blockEntityType(((CompatBlockData) data).furnace().type());
        if (be == null) return new CompatBlockState(dimension, pos, data);
        return switch (be) {
            case "minecraft:sign" -> new org.bukkit.craftbukkit.block.CraftSign(dimension, pos, data);
            case "minecraft:hanging_sign" -> new org.bukkit.craftbukkit.block.CraftHangingSign(dimension, pos, data);
            case "minecraft:skull" -> new org.bukkit.craftbukkit.block.CraftSkull(dimension, pos, data);
            case "minecraft:command_block" -> new org.bukkit.craftbukkit.block.CraftCommandBlock(dimension, pos, data);
            case "minecraft:banner" -> new org.bukkit.craftbukkit.block.CraftBanner(dimension, pos, data);
            case "minecraft:shulker_box" -> new org.bukkit.craftbukkit.block.CraftShulkerBox(dimension, pos, data);
            case "minecraft:bed" -> new org.bukkit.craftbukkit.block.CraftBed(dimension, pos, data);
            case "minecraft:beehive" -> new org.bukkit.craftbukkit.block.CraftBeehive(dimension, pos, data);
            case "minecraft:campfire" -> new org.bukkit.craftbukkit.block.CraftCampfire(dimension, pos, data);
            case "minecraft:barrel" -> new org.bukkit.craftbukkit.block.CraftBarrel(dimension, pos, data);
            case "minecraft:brewing_stand" -> new org.bukkit.craftbukkit.block.CraftBrewingStand(dimension, pos, data);
            case "minecraft:beacon" -> new org.bukkit.craftbukkit.block.CraftBeacon(dimension, pos, data);
            case "minecraft:bell" -> new org.bukkit.craftbukkit.block.CraftBell(dimension, pos, data);
            case "minecraft:blast_furnace" -> new org.bukkit.craftbukkit.block.CraftBlastFurnace(dimension, pos, data);
            case "minecraft:chest" -> new org.bukkit.craftbukkit.block.CraftChest(dimension, pos, data);
            case "minecraft:trapped_chest" -> new org.bukkit.craftbukkit.block.CraftChest(dimension, pos, data);
            case "minecraft:chiseled_bookshelf" -> new org.bukkit.craftbukkit.block.CraftChiseledBookshelf(dimension, pos, data);
            case "minecraft:comparator" -> new org.bukkit.craftbukkit.block.CraftComparator(dimension, pos, data);
            case "minecraft:conduit" -> new org.bukkit.craftbukkit.block.CraftConduit(dimension, pos, data);
            case "minecraft:daylight_detector" -> new org.bukkit.craftbukkit.block.CraftDaylightDetector(dimension, pos, data);
            case "minecraft:decorated_pot" -> new org.bukkit.craftbukkit.block.CraftDecoratedPot(dimension, pos, data);
            case "minecraft:dispenser" -> new org.bukkit.craftbukkit.block.CraftDispenser(dimension, pos, data);
            case "minecraft:dropper" -> new org.bukkit.craftbukkit.block.CraftDropper(dimension, pos, data);
            case "minecraft:enchanting_table" -> new org.bukkit.craftbukkit.block.CraftEnchantingTable(dimension, pos, data);
            case "minecraft:ender_chest" -> new org.bukkit.craftbukkit.block.CraftEnderChest(dimension, pos, data);
            case "minecraft:end_gateway" -> new org.bukkit.craftbukkit.block.CraftEndGateway(dimension, pos, data);
            case "minecraft:end_portal" -> new org.bukkit.craftbukkit.block.CraftEndPortal(dimension, pos, data);
            case "minecraft:furnace" -> new org.bukkit.craftbukkit.block.CraftFurnaceFurnace(dimension, pos, data);
            case "minecraft:hopper" -> new org.bukkit.craftbukkit.block.CraftHopper(dimension, pos, data);
            case "minecraft:jigsaw" -> new org.bukkit.craftbukkit.block.CraftJigsaw(dimension, pos, data);
            case "minecraft:jukebox" -> new org.bukkit.craftbukkit.block.CraftJukebox(dimension, pos, data);
            case "minecraft:lectern" -> new org.bukkit.craftbukkit.block.CraftLectern(dimension, pos, data);
            case "minecraft:piston" -> new org.bukkit.craftbukkit.block.CraftMovingPiston(dimension, pos, data);
            case "minecraft:sculk_catalyst" -> new org.bukkit.craftbukkit.block.CraftSculkCatalyst(dimension, pos, data);
            case "minecraft:sculk_sensor" -> new org.bukkit.craftbukkit.block.CraftSculkSensor(dimension, pos, data);
            case "minecraft:calibrated_sculk_sensor" -> new org.bukkit.craftbukkit.block.CraftCalibratedSculkSensor(dimension, pos, data);
            case "minecraft:sculk_shrieker" -> new org.bukkit.craftbukkit.block.CraftSculkShrieker(dimension, pos, data);
            case "minecraft:smoker" -> new org.bukkit.craftbukkit.block.CraftSmoker(dimension, pos, data);
            case "minecraft:mob_spawner" -> new org.bukkit.craftbukkit.block.CraftCreatureSpawner(dimension, pos, data);
            case "minecraft:structure_block" -> new org.bukkit.craftbukkit.block.CraftStructureBlock(dimension, pos, data);
            case "minecraft:brushable_block" -> new org.bukkit.craftbukkit.block.CraftBrushableBlock(dimension, pos, data);
            case "minecraft:crafter" -> new org.bukkit.craftbukkit.block.CraftCrafter(dimension, pos, data);
            case "minecraft:trial_spawner" -> new org.bukkit.craftbukkit.block.CraftTrialSpawner(dimension, pos, data);
            case "minecraft:vault" -> new org.bukkit.craftbukkit.block.CraftVault(dimension, pos, data);
            default -> new CompatBlockState(dimension, pos, data);
        };
    }
}
