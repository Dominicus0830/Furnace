package org.bukkit.craftbukkit.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.domi.furnace.compat.nms.UnsupportedNmsCall;

/**
 * Paper CapturedBlockState(T-211): 레벨의 쓰기 기록({@code Level#captureTreeGeneration}·{@code captureBlockStates})이 칸마다 남기는 상태.
 * 만들 때 그 칸의 지금 블록으로 시작하고, 기록이 {@link #setData}·{@link #setFlag} 로 마지막 쓰기를 싣는다.
 * Paper 와 다른 점: 나무가 놓은 벌집(bee_nest)을 월드에 반영하면 Paper 는 그 블록 엔티티에 벌 2~3 마리를 넣는데(레벨 난수), 블록 엔티티 shim 에
 * 벌 넣기가 아직 없어 그 경우는 쓰기 전에 {@link UnsupportedNmsCall} 이다.
 */
public final class CapturedBlockState extends CraftBlockState {

    private final boolean treeBlock;

    public CapturedBlockState(Block block, int flag, boolean treeBlock) {
        super(((org.bukkit.craftbukkit.CraftWorld) block.getWorld()).furnaceHandle().dimension(),
                new org.domi.furnace.api.world.BlockPos(block.getX(), block.getY(), block.getZ()), block.getBlockData());
        this.flag = flag;
        this.treeBlock = treeBlock;
    }

    @Override
    public boolean update(boolean force, boolean applyPhysics) {
        requireNoBees("CapturedBlockState#update");
        return super.update(force, applyPhysics);
    }

    /** Paper updatedTree: 레벨에 기록된 플래그로 쓴다(+ 벌 넣기). */
    private void updatedTree() {
        requireNoBees("CapturedBlockState#setBlockState");
        ((org.bukkit.craftbukkit.CraftWorld) getWorld()).getHandle().setBlock(getPosition(), getHandle(), getFlag());
    }

    private void requireNoBees(String what) {
        if (treeBlock && isPlaced() && getType() == Material.BEE_NEST) {
            throw new UnsupportedNmsCall(what + "(bee nest from a tree)",
                    "Paper puts 2-3 bees into the bee nest's block entity, and storing bees through the block entity shim does not exist yet");
        }
    }

    public static CapturedBlockState getBlockState(Level world, BlockPos pos, int flag) {
        return new CapturedBlockState(world.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ()), flag, false);
    }

    public static CapturedBlockState getTreeBlockState(Level world, BlockPos pos, int flag) {
        return new CapturedBlockState(world.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ()), flag, true);
    }

    public static void setBlockState(BlockState blockState) {
        if (blockState instanceof CapturedBlockState captured) captured.updatedTree();
        else blockState.update(true);
    }
}
