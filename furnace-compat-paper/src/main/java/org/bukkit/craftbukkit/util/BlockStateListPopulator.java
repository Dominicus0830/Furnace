package org.bukkit.craftbukkit.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import org.bukkit.craftbukkit.block.CraftBlockState;

/**
 * CraftBukkit BlockStateListPopulator(T-211) — Paper 1.21.1 그대로: 감싼 레벨 위에 쓰기를 자기 표에 쌓는 지형 생성 레벨. 읽기는 자기 표가 먼저,
 * 없으면 감싼 레벨(유체·하이트맵·빛·차원도 감싼 레벨). 쓰기마다 그 칸의 블록 상태 스냅숏({@link CraftBlockState}, 플래그·레벨 손잡이 포함)을 차례대로 남긴다.
 * 블록 엔티티가 있는 블록은 새 블록 엔티티를 만들어 둔다({@code newBlockEntity}). FAWE 는 이것을 상속해 쓰기를 서버 레벨로 돌린다.
 * Paper 와 다른 점: 레벨 데이터({@code getLevelData})·틱 순번({@code nextSubTickCount})·{@code refreshTiles}(블록 엔티티 상태 다시 뜨기)는 shim 이 없어 두지 않았다.
 */
public class BlockStateListPopulator extends DummyGeneratorAccess {

    private final LevelAccessor world;
    private final Map<BlockPos, BlockState> dataMap = new HashMap<>();
    private final Map<BlockPos, BlockEntity> entityMap = new HashMap<>();
    private final LinkedHashMap<BlockPos, CraftBlockState> list;

    public BlockStateListPopulator(LevelAccessor world) { this(world, new LinkedHashMap<>()); }

    private BlockStateListPopulator(LevelAccessor world, LinkedHashMap<BlockPos, CraftBlockState> list) {
        this.world = world;
        this.list = list;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        BlockState blockData = dataMap.get(pos);
        return blockData != null ? blockData : world.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        BlockState blockData = dataMap.get(pos);
        return blockData != null ? blockData.getFluidState() : world.getFluidState(pos);
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return entityMap.containsKey(pos) ? entityMap.get(pos) : world.getBlockEntity(pos);
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags) {
        pos = pos.immutable();
        list.remove(pos);
        dataMap.put(pos, state);
        entityMap.put(pos, state.getBlock() instanceof EntityBlock eb ? eb.newBlockEntity(pos, state) : null);
        CraftBlockState state1 = org.domi.furnace.compat.paper.CompatBlockState.generated(getMinecraftWorld().getWorld().furnaceHandle().dimension(),
                new org.domi.furnace.api.world.BlockPos(pos.getX(), pos.getY(), pos.getZ()), state);
        state1.setFlag(flags);
        state1.setWorldHandle(world);
        list.put(pos, state1);
        return true;
    }

    @Override
    public ServerLevel getMinecraftWorld() { return world.getMinecraftWorld(); }

    public void updateList() {
        for (org.bukkit.block.BlockState state : list.values()) state.update(true);
    }

    public Set<BlockPos> getBlocks() { return list.keySet(); }

    public List<CraftBlockState> getList() { return new ArrayList<>(list.values()); }

    public LevelAccessor getWorld() { return world; }

    @Override
    public int getMinBuildHeight() { return getWorld().getMinBuildHeight(); }

    @Override
    public int getHeight() { return getWorld().getHeight(); }

    @Override
    public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> state) { return state.test(getBlockState(pos)); }

    @Override
    public boolean isFluidAtPosition(BlockPos pos, Predicate<FluidState> state) { return state.test(getFluidState(pos)); }

    @Override
    public DimensionType dimensionType() {
        if (world instanceof net.minecraft.world.level.Level level) return level.dimensionType();
        if (world instanceof DummyGeneratorAccess dummy) return dummy.dimensionType();
        throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("BlockStateListPopulator#dimensionType", "the wrapped level has no dimension type in the shim");
    }

    @Override
    public RegistryAccess registryAccess() { return world.registryAccess(); }

    /** Paper: getLevelData 를 감싼 레벨에 묻는다(shim 은 레벨 데이터 대신 dayTime 을 직접). */
    @Override
    public long dayTime() { return world.dayTime(); }

    public <T extends BlockEntity> Optional<T> getBlockEntity(BlockPos pos, BlockEntityType<T> type) {
        BlockEntity tileentity = getBlockEntity(pos);
        @SuppressWarnings("unchecked")
        Optional<T> r = tileentity != null && tileentity.getType() == type ? Optional.of((T) tileentity) : Optional.empty();
        return r;
    }

    public BlockPos getHeightmapPos(Heightmap.Types heightmap, BlockPos pos) { return world.getHeightmapPos(heightmap, pos); }

    @Override
    public int getHeight(Heightmap.Types heightmap, int x, int z) { return world.getHeight(heightmap, x, z); }

    public int getRawBrightness(BlockPos pos, int ambientDarkness) {
        if (world instanceof net.minecraft.world.level.Level level) return level.getRawBrightness(pos, ambientDarkness);
        throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("BlockStateListPopulator#getRawBrightness", "the wrapped level has no light in the shim");
    }

    public int getBrightness(LightLayer type, BlockPos pos) {
        if (world instanceof net.minecraft.world.level.Level level) return level.getBrightness(type, pos);
        throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("BlockStateListPopulator#getBrightness", "the wrapped level has no light in the shim");
    }
}
