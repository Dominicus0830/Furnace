package org.bukkit.block;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

/**
 * 블록 한 칸. 읽기(getType·getBlockData)는 그 칸을 가진 region 스레드에서만 된다(이벤트 처리기 안은 해당 — 아니면 ThreadOwnershipException).
 * 쓰기(setType·setBlockData)는 소유 스레드면 곧바로, 아니면 소유 스레드로 예약된다(이웃 갱신·클라이언트 알림은 Furnace 의 setBlock 과 같다).
 * BlockState(표지판·상자 등)는 아직 없다.
 */
public interface Block extends org.bukkit.metadata.Metadatable {

    World getWorld();

    /** 좌표를 한 long 에 담은 값(Paper, 1.18.1 부터 deprecated). */
    @Deprecated
    default long getBlockKey() { return Block.getBlockKey(getX(), getY(), getZ()); }

    /** x 27 비트·z 27 비트·y 10 비트(위쪽)로 묶는다. */
    @Deprecated
    static long getBlockKey(int x, int y, int z) { return ((long) x & 0x7FFFFFF) | (((long) z & 0x7FFFFFF) << 27) | ((long) y << 54); }

    @Deprecated
    static int getBlockKeyX(long packed) { return (int) ((packed << 37) >> 37); }

    @Deprecated
    static int getBlockKeyY(long packed) { return (int) (packed >> 54); }

    @Deprecated
    static int getBlockKeyZ(long packed) { return (int) ((packed << 10) >> 37); }

    int getX();

    int getY();

    int getZ();

    Location getLocation();

    /** loc 에 이 블록 위치를 채워 돌려준다(loc 이 null 이면 null). */
    Location getLocation(Location loc);

    Chunk getChunk();

    /** 지금 종류. 소유 스레드 전용, 청크가 로드돼 있지 않으면 IllegalStateException. */
    Material getType();

    BlockData getBlockData();

    /** 이 칸의 지금 상태 스냅숏(블록 엔티티 내용은 담지 않는다). */
    BlockState getState();

    /**
     * Paper getState(useSnapshot). false 면 블록 엔티티 상태(TileState)의 isSnapshot()=false 이고 영속 데이터를 그 칸 블록 엔티티에서 곧바로
     * 읽고 쓴다(update 불필요). 이름·표지판 글 같은 다른 값은 Furnace 에서는 여전히 update 때 실린다.
     */
    BlockState getState(boolean useSnapshot);

    /** 밝기(하늘빛은 지금 하늘 어두움을 뺀 값, 블록빛과 큰 값). 소유 스레드 전용. */
    byte getLightLevel();

    /** 하늘빛(낮밤 무관). 소유 스레드 전용. */
    byte getLightFromSky();

    /** 블록빛. 소유 스레드 전용. */
    byte getLightFromBlocks();

    /** 도구 없이 부순다(도구 검사 없음, 효과·경험치 없음). 소유 스레드 전용. */
    boolean breakNaturally();

    /** tool 이 null 이면 도구 검사 없음. 맞는 도구가 아니면 드롭 없이 칸만 지우고 false. */
    boolean breakNaturally(org.bukkit.inventory.ItemStack tool);

    default boolean breakNaturally(boolean triggerEffect) { return breakNaturally(triggerEffect, false); }

    boolean breakNaturally(boolean triggerEffect, boolean dropExperience);

    default boolean breakNaturally(org.bukkit.inventory.ItemStack tool, boolean triggerEffect) { return breakNaturally(tool, triggerEffect, false); }

    boolean breakNaturally(org.bukkit.inventory.ItemStack tool, boolean triggerEffect, boolean dropExperience);

    /** 도구 없이 부술 때의 드롭(전리품 표를 굴린다). 소유 스레드 전용. */
    java.util.Collection<org.bukkit.inventory.ItemStack> getDrops();

    /** tool 이 null 이면 도구 검사 없음, 맞는 도구가 아니면 빈 목록. */
    java.util.Collection<org.bukkit.inventory.ItemStack> getDrops(org.bukkit.inventory.ItemStack tool);

    /** 이웃 갱신을 하며 기본 상태로 바꾼다. 블록이 아닌 종류면 IllegalArgumentException, 청크가 로드돼 있지 않으면 IllegalStateException. */
    void setType(Material type);

    /** applyPhysics 가 거짓이면(이웃 갱신 없이 바꾸기) UnsupportedFeature — Furnace 의 setBlock 은 항상 이웃을 갱신한다. */
    void setType(Material type, boolean applyPhysics);

    void setBlockData(BlockData data);

    void setBlockData(BlockData data, boolean applyPhysics);

    boolean isEmpty();

    boolean isLiquid();

    Block getRelative(int modX, int modY, int modZ);

    Block getRelative(BlockFace face);

    Block getRelative(BlockFace face, int distance);

    /** 이웃 블록이 어느 면인지(이웃이 아니면 null). */
    BlockFace getFace(Block block);

    /** 이 칸의 바이옴(World#getBiome 과 같은 규칙 — 소유 스레드 전용, 플러그인 바이옴은 CUSTOM). */
    default Biome getBiome() { return getWorld().getBiome(getX(), getY(), getZ()); }

    /** 이 칸이 든 4×4×4 바이옴 칸을 바꾼다(World#setBiome 과 같다). */
    default void setBiome(Biome biome) { getWorld().setBiome(getX(), getY(), getZ(), biome); }
}
