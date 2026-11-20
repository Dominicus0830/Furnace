package org.bukkit.craftbukkit.event;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Paper 이벤트 공장의 자리(NMS shim 5d — Axiom 블록 놓기). 플러그인이 직접 부르는 것만 있다. 이벤트는 Bukkit 처리기에게 그대로 보낸다(Paper 와 같다 —
 * 이 길은 서버의 블록 놓기가 아니므로 Furnace 처리기에는 가지 않는다).
 */
public class CraftEventFactory {

    /** Paper canBuild: 놓는 레벨이 오버월드이고 스폰 보호 반경 안이면(OP 가 있고 이 플레이어는 OP 가 아닐 때) 못 놓는다. */
    private static boolean canBuild(ServerLevel world, org.bukkit.entity.Player player, int x, int z) {
        return !org.domi.furnace.compat.nms.NmsChunks.bridge().underSpawnProtection(world.getWorld().furnaceHandle(),
                ((org.bukkit.craftbukkit.entity.CraftPlayer) player).furnaceHandle(), x, z);
    }

    private static ItemStack hand(org.bukkit.entity.Player player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? player.getInventory().getItemInMainHand() : player.getInventory().getItemInOffHand();
    }

    /** Paper: 한 칸 놓기 이벤트를 만들어 부른다. 놓인 칸은 바뀐 상태의 칸, 기댄 칸은 (clickedX, clickedY, clickedZ). */
    public static BlockPlaceEvent callBlockPlaceEvent(ServerLevel world, net.minecraft.world.entity.player.Player who, InteractionHand hand,
                                                      BlockState replacedBlockState, int clickedX, int clickedY, int clickedZ) {
        CraftWorld craftWorld = world.getWorld();
        org.bukkit.entity.Player player = (org.bukkit.entity.Player) who.getBukkitEntity();
        Block blockClicked = craftWorld.getBlockAt(clickedX, clickedY, clickedZ);
        Block placedBlock = replacedBlockState.getBlock();
        boolean canBuild = canBuild(world, player, placedBlock.getX(), placedBlock.getZ());
        EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND;
        BlockPlaceEvent event = new BlockPlaceEvent(placedBlock, replacedBlockState, blockClicked, hand(player, hand), player, canBuild, slot);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    /** Paper: 여러 칸 놓기 이벤트. 한 칸이라도 스폰 보호 안이면 못 놓는다. */
    public static BlockMultiPlaceEvent callBlockMultiPlaceEvent(ServerLevel world, net.minecraft.world.entity.player.Player who, InteractionHand hand,
                                                                List<BlockState> blockStates, int clickedX, int clickedY, int clickedZ) {
        CraftWorld craftWorld = world.getWorld();
        org.bukkit.entity.Player player = (org.bukkit.entity.Player) who.getBukkitEntity();
        Block blockClicked = craftWorld.getBlockAt(clickedX, clickedY, clickedZ);
        boolean canBuild = true;
        for (BlockState s : blockStates) {
            if (!canBuild(world, player, s.getX(), s.getZ())) {
                canBuild = false;
                break;
            }
        }
        EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND;
        BlockMultiPlaceEvent event = new BlockMultiPlaceEvent(blockStates, blockClicked, hand(player, hand), player, canBuild, slot);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }
}
