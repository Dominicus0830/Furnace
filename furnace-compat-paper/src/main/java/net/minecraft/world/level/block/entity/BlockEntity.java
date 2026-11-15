package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtBridge;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.compat.nms.NmsBlockStates;
import org.domi.furnace.compat.nms.UnsupportedNmsCall;

/**
 * 블록 엔티티(Mojang 이름 shim, N2b-6). 두 가지로 쓴다.
 * <ul>
 * <li>레벨 없이({@code level == null}, {@code EntityBlock.newBlockEntity}) - 저장하면 갓 만든 바닐라 블록 엔티티와 같은 NBT(서버가 같은 종류를 만들어 저장한 것).</li>
 * <li>월드의 살아 있는 블록 엔티티 보기({@link #furnaceInWorld}) - 저장하면 서버의 지금 블록 엔티티 NBT.</li>
 * </ul>
 * 종류별 필드·메서드(컨테이너·틱·컴포넌트 등)는 두지 않았다.
 */
public abstract class BlockEntity {

    private final BlockEntityType<?> type;
    protected Level level;
    protected final BlockPos worldPosition;
    protected boolean remove;
    private BlockState blockState;

    public BlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        this.type = type;
        this.worldPosition = pos.immutable();
        this.blockState = state;
    }

    /**
     * 호환 모듈 전용 - 월드의 (stateId 인) 칸에 있는 블록 엔티티의 보기. 이 칸의 블록이 블록 엔티티를 만드는 종류가 아니면 null.
     * saveWithId 등은 서버의 살아 있는 블록 엔티티를 읽는다: 그 칸을 가진 region 스레드면 바로, 아니면 서버 future 로 기다린다(최대 10초, 넘기면 UnsupportedNmsCall).
     * 청크 이동·제거는 따라가지 않는다(저장 때 그 칸에 블록 엔티티가 없으면 IllegalStateException).
     */
    public static BlockEntity furnaceInWorld(Level level, BlockPos pos, int stateId) {
        BlockState state = net.minecraft.world.level.block.Block.stateById(stateId);
        if (state == null || !(state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock eb)) return null;
        BlockEntity be = eb.newBlockEntity(pos, state);
        if (be != null) be.level = level;
        return be;
    }

    public Level getLevel() { return level; }

    public void setLevel(Level level) { this.level = level; }

    public boolean hasLevel() { return level != null; }

    public BlockPos getBlockPos() { return worldPosition; }

    public BlockState getBlockState() { return blockState; }

    public void setBlockState(BlockState state) { this.blockState = state; }

    public BlockEntityType<?> getType() { return type; }

    public boolean isRemoved() { return remove; }

    public void setRemoved() { remove = true; }

    public void clearRemoved() { remove = false; }

    /** 바닐라 saveWithoutMetadata: 종류별 필드(+ 남은 컴포넌트)만, id·좌표 없음. */
    public final CompoundTag saveWithoutMetadata(HolderLookup.Provider registries) {
        NbtCompound c = base();
        c.remove("id");
        c.remove("x");
        c.remove("y");
        c.remove("z");
        return (CompoundTag) NbtBridge.fromFurnace(c);
    }

    /** 바닐라 saveWithId: saveWithoutMetadata + "id". */
    public final CompoundTag saveWithId(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        tag.putString("id", BlockEntityType.getKey(type).toString());
        return tag;
    }

    /** 바닐라 saveWithFullMetadata: saveWithoutMetadata + id + x·y·z. */
    public final CompoundTag saveWithFullMetadata(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithId(registries);
        tag.putInt("x", worldPosition.getX());
        tag.putInt("y", worldPosition.getY());
        tag.putInt("z", worldPosition.getZ());
        return tag;
    }

    /** 서버에서 읽은 저장 꼴(복사본). 레벨이 없으면 이 상태의 갓 만든 모습, 있으면 살아 있는 블록 엔티티. */
    private NbtCompound base() {
        if (level == null) {
            NbtCompound n = NmsBlockStates.bridge().defaultBlockEntityNbt(blockState.furnaceStateId());
            if (n == null) throw new IllegalStateException("블록 엔티티를 만들지 않는 블록 상태: " + blockState);
            return n;
        }
        var world = level.getWorld().furnaceHandle();
        var pos = new org.domi.furnace.api.world.BlockPos(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
        java.util.Optional<NbtCompound> nbt;
        try {
            nbt = world.blockEntityNbtNow(pos);
        } catch (org.domi.furnace.api.concurrent.ThreadOwnershipException e) {
            try {
                nbt = world.blockEntityNbt(pos).get(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new UnsupportedNmsCall("BlockEntity.save*", "다른 region 의 블록 엔티티를 기다리다 중단됐다: " + pos);
            } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException ex) {
                throw new UnsupportedNmsCall("BlockEntity.save*", "소유 region 스레드에서 블록 엔티티 NBT 를 읽지 못했다(" + pos + "): " + ex);
            }
        }
        return nbt.orElseThrow(() -> new IllegalStateException("그 칸에 블록 엔티티가 없다(제거됐거나 청크가 내려갔다): " + pos));
    }

    @Override
    public String toString() { return getClass().getSimpleName() + "{" + BlockEntityType.getKey(type) + "@" + worldPosition + "}"; }
}
