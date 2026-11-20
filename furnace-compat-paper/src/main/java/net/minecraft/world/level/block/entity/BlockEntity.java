package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtBridge;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.compat.nms.NmsBlockStates;
import org.domi.furnace.compat.nms.NmsChunks;

/**
 * 블록 엔티티(Mojang 이름 shim, N2b-6·T-198). 두 가지가 있다.
 * <ul>
 * <li><b>떨어진</b> 블록 엔티티 — 플러그인이 만든 것({@code EntityBlock.newBlockEntity}·생성자). 자기 내용을 스스로 든다: 처음엔 그 상태의 갓 만든 바닐라
 *     블록 엔티티와 같은 NBT, {@link #loadWithComponents} 하면 서버가 바닐라처럼 읽고 다시 쓴 꼴. {@link #setLevel} 해도 월드의 것이 되지 않는다(바닐라 그대로).</li>
 * <li><b>월드의</b> 블록 엔티티 보기 — 청크 shim 이 준 것({@code LevelChunk#getBlockEntity}·{@code getBlockEntities}·{@code Level#getBlockEntity}).
 *     서버 블록 엔티티 객체 하나에 묶이고(바닐라: 그 객체 자체), 같은 청크 shim 에서는 같은 칸·같은 서버 객체면 같은 보기다. 저장·싣기·setChanged 는 그 칸의
 *     주인 region 스레드에서 서버 객체에 하고, 다른 스레드의 싣기·setChanged 는 거울 일지로 주인이 재생한다(N2b-4). 지워진 뒤에도 그 객체의 마지막 내용을 저장한다
 *     (FAWE 가 지운 상자를 되돌리기 기록에 남긴다).</li>
 * </ul>
 * 종류별 필드·메서드(컨테이너·틱·컴포넌트 등)는 두지 않았다.
 */
public abstract class BlockEntity {

    private final BlockEntityType<?> type;
    protected Level level;
    protected final BlockPos worldPosition;
    /** 바닐라 필드(지움 표시) — FAWE 는 리플렉션으로 직접 세운다. */
    protected boolean remove;
    private BlockState blockState;

    // ---- 월드의 보기(T-198)
    /** 이 보기를 준 청크 shim(떨어진 블록 엔티티는 null). */
    private ChunkAccess furnaceChunk;
    /** 묶인 서버 블록 엔티티 손잡이(ChunkInternals). 다른 스레드가 만든 보기는 주인이 처음 볼 때 묶는다. */
    private volatile Object furnaceHandle;
    /** 떨어진 블록 엔티티의 내용(saveWithoutMetadata 꼴, id 포함). null 이면 그 상태의 갓 만든 모습. */
    private volatile NbtCompound furnaceData;
    /** 월드의 보기: 다른 스레드가 지운 서버 블록 엔티티를 주인이 지우기 직전에 저장한 꼴(saveWithFullMetadata). 지운 객체는 더 바뀌지 않아 다른 스레드가 기다리지 않고 쓴다. */
    private volatile NbtCompound furnaceSnapshot;

    public BlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        this.type = type;
        this.worldPosition = pos.immutable();
        this.blockState = state;
    }

    /**
     * 호환 모듈 전용 - 청크 shim 의 블록 엔티티 보기(그 상태의 블록이 블록 엔티티를 만들지 않으면 null). handle 이 null 이면 주인이 처음 볼 때 묶는다.
     */
    public static BlockEntity furnaceView(ChunkAccess chunk, BlockPos pos, BlockState state, Object handle) {
        if (state == null || !(state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock eb)) return null;
        BlockEntity be = eb.newBlockEntity(pos, state);
        if (be == null) return null;
        be.level = chunk instanceof net.minecraft.world.level.chunk.LevelChunk lc ? lc.getLevel() : null;
        be.furnaceChunk = chunk;
        be.furnaceHandle = handle;
        return be;
    }

    /** 호환 모듈 전용: 월드의 보기인가(청크 shim 이 준 것). */
    public final boolean furnaceIsView() { return furnaceChunk != null; }

    /** 호환 모듈 전용: 묶인 서버 손잡이(없으면 null). */
    public final Object furnaceHandle() { return furnaceHandle; }

    /** 호환 모듈 전용: 지운 뒤의 저장 꼴(없으면 null — 복사하지 않은 그 객체). */
    public final NbtCompound furnaceSnapshot() { return furnaceSnapshot; }

    /** 호환 모듈 전용(주인 스레드): 지운 뒤의 저장 꼴을 둔다. */
    public final void furnaceSnapshot(NbtCompound saved) { furnaceSnapshot = saved; }

    /** 호환 모듈 전용(주인 스레드): 아직 묶이지 않았으면 묶는다. */
    public final void furnaceBind(Object handle) {
        if (furnaceHandle == null) furnaceHandle = handle;
    }

    /** 호환 모듈 전용(주인 스레드, 만들기 재생): 지워진 옛 객체에 묶여 있으면 새로 만든 객체로 바꿔 묶는다(검토 R5). */
    public final void furnaceRebindIfRemoved(Object handle) {
        Object h = furnaceHandle;
        if (h == null || (h != handle && NmsChunks.bridge().blockEntityRemoved(h))) furnaceHandle = handle;
    }

    public Level getLevel() { return level; }

    public void setLevel(Level level) { this.level = level; }

    public boolean hasLevel() { return level != null; }

    public BlockPos getBlockPos() { return worldPosition; }

    public BlockState getBlockState() { return blockState; }

    public void setBlockState(BlockState state) { this.blockState = state; }

    public BlockEntityType<?> getType() { return type; }

    /** 바닐라 isRemoved. 월드의 보기는 묶인 서버 블록 엔티티가 지워졌어도 참(게임이 부순 상자 등). */
    public boolean isRemoved() {
        if (remove) return true;
        Object h = furnaceHandle;
        return h != null && NmsChunks.bridge().blockEntityRemoved(h);
    }

    public void setRemoved() { remove = true; }

    public void clearRemoved() { remove = false; }

    /**
     * 바닐라 loadWithComponents: 종류별 필드·영속 데이터·컴포넌트를 싣는다(저장 표시·패킷·이웃 알림 없음 — 바닐라 그대로). 월드의 보기는 서버 블록 엔티티에
     * (다른 스레드면 거울 일지로 주인이 재생), 떨어진 것은 자기 내용에.
     */
    public final void loadWithComponents(CompoundTag tag, HolderLookup.Provider registries) {
        NbtCompound n = (NbtCompound) NbtBridge.toFurnace(tag);
        if (furnaceChunk != null) {
            furnaceSnapshot = null;   // 지운 객체에도 실을 수 있다(바닐라) — 그 뒤 저장은 객체에서
            furnaceChunk.furnaceBlockEntityLoad(this, n);
            return;
        }
        NbtCompound out = NmsChunks.bridge().normalizeBlockEntityNbt(blockState.furnaceStateId(), n);
        if (out == null) throw new IllegalStateException("block state that has no block entity: " + blockState);
        furnaceData = out;
    }

    /**
     * 호환 모듈 전용 — 바닐라 clearContent(T-211 3단계, {@link net.minecraft.world.Clearable} 인 종류가 부른다). 월드의 보기는 서버 블록 엔티티를 바닐라처럼
     * 비운다(주크박스는 재생을 멈추고 장식 단지는 전리품을 먼저 푼다). 떨어진 블록 엔티티는 내용을 NBT 로만 들고 있어 레벨 동작을 같게 낼 수 없다 — UnsupportedNmsCall.
     */
    protected final void furnaceClearContent() {
        if (furnaceChunk == null) {
            throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("Clearable#clearContent",
                    "clearing a block entity that is not in a world (" + this + ") is not supported - it keeps its contents only as NBT");
        }
        furnaceChunk.furnaceBlockEntityClear(this);
    }

    /** 바닐라 setChanged: 레벨이 있으면 {@link #setChanged(Level, BlockPos, BlockState)}. */
    public void setChanged() {
        if (level != null) setChanged(level, worldPosition, blockState);
    }

    /**
     * 바닐라 정적 setChanged: 그 칸 청크 저장 표시, 블록이 공기가 아니면 비교기 쪽 알림(updateNeighbourForOutputSignal). 그 칸의 주인 스레드면 바로, 다른
     * 스레드면 거울 일지로 주인이 재생한다. 청크가 로드돼 있지 않으면 아무것도 하지 않는다(바닐라 blockEntityChanged 와 같다).
     */
    protected static void setChanged(Level level, BlockPos pos, BlockState state) {
        level.furnaceBlockEntityChanged(pos);
    }

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

    /** 저장 꼴(복사본). 월드의 보기는 묶인 서버 블록 엔티티(지워졌으면 그 마지막 내용), 떨어진 것은 자기 내용. */
    private NbtCompound base() {
        if (furnaceChunk != null) return furnaceChunk.furnaceBlockEntitySave(this);
        NbtCompound d = furnaceData;
        if (d != null) return d.copy();
        NbtCompound n = NmsBlockStates.bridge().defaultBlockEntityNbt(blockState.furnaceStateId());
        if (n == null) throw new IllegalStateException("block state that has no block entity: " + blockState);
        return n;
    }

    @Override
    public String toString() { return getClass().getSimpleName() + "{" + BlockEntityType.getKey(type) + "@" + worldPosition + "}"; }
}
