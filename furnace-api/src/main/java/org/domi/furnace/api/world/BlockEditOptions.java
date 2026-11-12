// 빠른 블록 쓰기(E7) 옵션. 바닐라 Level.setBlock 플래그(1 이웃 갱신, 2 클라이언트 알림, 16 모양 갱신 건너뛰기)와 /fill 의 방식에 있는
// 뜻만 연다 — 새 동작을 지어내지 않는다. 기본값({@link #DEFAULT})은 World.setBlock 을 칸마다 부른 것과 같다.
package org.domi.furnace.api.world;

import java.util.Objects;

/**
 * 불변. {@code BlockEditOptions.DEFAULT.neighborUpdates(NeighborUpdates.NONE).shapeUpdates(false)} 처럼 고친 사본을 만든다.
 *
 * <p>어느 옵션이든 블록 자체의 놓기·치우기 반응(바닐라 onPlace·onRemove — 예약 틱 걸기, 블록 엔티티 만들기·지우기, 레드스톤 선의 세기 계산,
 * 담긴 아이템 쏟기 등)과 빛 다시 계산(틱 끝에 한 번 묶어서), 마을 POI 갱신, 저장 표시는 바닐라대로 일어난다. 이벤트(BlockPlace 등)는
 * 거치지 않는다.
 */
public final class BlockEditOptions {

    /** 이웃 갱신(바닐라 neighborChanged — 레드스톤·모래 떨어짐·관측기 …)을 언제 할지. */
    public enum NeighborUpdates {
        /** 칸마다 바꾸는 즉시(바닐라 setBlock 플래그 1, World.setBlock 과 같다). */
        IMMEDIATE,
        /**
         * 바꾸는 동안은 하지 않고, 다 바꾼 뒤 바뀐 칸마다 한 번(바닐라 /fill·/clone 과 같다: 플래그 2 로 놓은 뒤 blockUpdated).
         * 여러 region 에 걸치면 region 묶음마다 그 묶음을 다 바꾼 뒤.
         */
        AFTER_EDIT,
        /** 하지 않는다(플래그 1 없음). */
        NONE
    }

    /** 클라이언트에 어떻게 알릴지. */
    public enum ClientUpdates {
        /** 바닐라처럼 바뀐 칸을 칸·섹션 갱신 패킷으로(플래그 2). */
        BLOCKS,
        /**
         * 바꾼 청크는 그 청크를 보는 사람에게 청크째 한 번 다시 보낸다(빛 포함). 많은 칸을 바꿀 때 칸 갱신보다 훨씬 작다.
         * 바꾼 청크 밖으로 번진 모양 갱신(계단·울타리 잇기 등)은 바닐라처럼 칸 갱신으로 간다. 클라이언트가 보는 결과는 BLOCKS 와 같다.
         */
        CHUNKS,
        /**
         * 알리지 않는다(플래그 2 없음 — 바꾼 칸과 거기서 번진 모양 갱신 모두). 클라이언트는 다시 받을 때까지 옛 블록을 본다.
         * 플레이어별 보기를 따로 보낼 때만 쓴다.
         */
        NONE
    }

    /** World.setBlock 과 같다: 즉시 이웃 갱신, 모양 갱신, 칸 갱신 패킷, 내용물 비우기 없음, 한 번에 다. */
    public static final BlockEditOptions DEFAULT = new BlockEditOptions(NeighborUpdates.IMMEDIATE, true, ClientUpdates.BLOCKS, false, 0);

    /**
     * 큰 범위 복구·붙여넣기용: 이웃·모양 갱신 없음, 청크째 다시 보내기, 바꾸는 칸의 내용물은 쏟지 않고 비운다.
     * 바닐라 setBlock 플래그 2|16 과 같은 뜻(클라이언트에는 청크째 보낸다): 이웃은 바뀐 줄 모르므로 지지대 없는 횃불도 그 자리에 남는다.
     * 다만 놓일 때 스스로 반응하는 블록(모래·자갈은 예약 틱을 걸어 떨어지고, 레드스톤 선은 세기를 계산한다)은 바닐라대로 움직인다.
     */
    public static final BlockEditOptions FAST = new BlockEditOptions(NeighborUpdates.NONE, false, ClientUpdates.CHUNKS, true, 0);

    private final NeighborUpdates neighborUpdates;
    private final boolean shapeUpdates;
    private final ClientUpdates clientUpdates;
    private final boolean clearContainers;
    private final int chunksPerTick;

    private BlockEditOptions(NeighborUpdates neighborUpdates, boolean shapeUpdates, ClientUpdates clientUpdates, boolean clearContainers,
                             int chunksPerTick) {
        this.neighborUpdates = Objects.requireNonNull(neighborUpdates, "neighborUpdates");
        this.shapeUpdates = shapeUpdates;
        this.clientUpdates = Objects.requireNonNull(clientUpdates, "clientUpdates");
        this.clearContainers = clearContainers;
        if (chunksPerTick < 0) throw new IllegalArgumentException("chunksPerTick < 0: " + chunksPerTick);
        this.chunksPerTick = chunksPerTick;
    }

    public NeighborUpdates neighborUpdates() { return neighborUpdates; }

    /** 이웃 갱신 시점을 바꾼 사본. */
    public BlockEditOptions neighborUpdates(NeighborUpdates v) { return new BlockEditOptions(v, shapeUpdates, clientUpdates, clearContainers, chunksPerTick); }

    /** 모양 갱신(바닐라 updateNeighbourShapes — 계단·울타리·유리판 잇기, 지지대 잃은 블록 부수기)을 하는가. false 면 플래그 16. */
    public boolean shapeUpdates() { return shapeUpdates; }

    public BlockEditOptions shapeUpdates(boolean v) { return new BlockEditOptions(neighborUpdates, v, clientUpdates, clearContainers, chunksPerTick); }

    public ClientUpdates clientUpdates() { return clientUpdates; }

    public BlockEditOptions clientUpdates(ClientUpdates v) { return new BlockEditOptions(neighborUpdates, shapeUpdates, v, clearContainers, chunksPerTick); }

    /**
     * 다른 상태로 바꾸는 칸의 담긴 아이템을 먼저 비우는가(바닐라 /fill·/setblock 의 Clearable.tryClear — 상자를 덮어써도 내용물을 쏟지 않는다).
     * false 면 바닐라 setBlock 처럼 바꾸는 순간 쏟아진다.
     */
    public boolean clearContainers() { return clearContainers; }

    public BlockEditOptions clearContainers(boolean v) { return new BlockEditOptions(neighborUpdates, shapeUpdates, clientUpdates, v, chunksPerTick); }

    /**
     * region 스레드가 한 틱에 적용할 최대 청크 수(0 = 한 번에 다). 큰 편집이 한 틱을 길게 붙잡지 않게 나눈다 — 나머지는 다음 틱에
     * 이어 간다(그 사이 다른 게임 진행이 끼어든다). AFTER_EDIT 이웃 갱신·CHUNKS 재전송은 틱 묶음마다.
     */
    public int chunksPerTick() { return chunksPerTick; }

    public BlockEditOptions chunksPerTick(int v) { return new BlockEditOptions(neighborUpdates, shapeUpdates, clientUpdates, clearContainers, v); }

    @Override
    public boolean equals(Object o) {
        return o instanceof BlockEditOptions b && b.neighborUpdates == neighborUpdates && b.shapeUpdates == shapeUpdates
                && b.clientUpdates == clientUpdates && b.clearContainers == clearContainers && b.chunksPerTick == chunksPerTick;
    }

    @Override
    public int hashCode() { return Objects.hash(neighborUpdates, shapeUpdates, clientUpdates, clearContainers, chunksPerTick); }

    @Override
    public String toString() {
        return "BlockEditOptions[neighbors=" + neighborUpdates + ", shapes=" + shapeUpdates + ", clients=" + clientUpdates
                + ", clearContainers=" + clearContainers + ", chunksPerTick=" + chunksPerTick + "]";
    }
}
