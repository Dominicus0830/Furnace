// 상자 블록엔티티: 월드좌표 + 27칸(저장 꼴은 ChunkSnapshot·AnvilChunkCodec 이 직접 다룬다). 아이템·전리품 표·이름은 ContainerBlockEntity.
// 종류는 minecraft:chest 또는 minecraft:trapped_chest(덫 상자). 여는 사람이 생기면 뚜껑 소리와 block_event(1, 여는 사람 수)를 낸다.
package org.domi.furnace.server.world;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.ChestBlock;
import org.domi.furnace.server.entity.ItemStack;

public final class ChestBlockEntity extends org.domi.furnace.server.block.entity.ContainerBlockEntity {

    public static final int SLOTS = 27;

    public final int x, y, z; // 월드 좌표(섹션 내부 아님)

    public ChestBlockEntity(int x, int y, int z, ItemStack[] items) { this("minecraft:chest", x, y, z, items); }

    public ChestBlockEntity(String type, int x, int y, int z, ItemStack[] items) {
        super(type, new Pos(x, y, z), -1, items);
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static ChestBlockEntity empty(int x, int y, int z) { return empty("minecraft:chest", x, y, z); }

    public static ChestBlockEntity empty(String type, int x, int y, int z) {
        ItemStack[] items = new ItemStack[SLOTS];
        java.util.Arrays.fill(items, ItemStack.EMPTY);
        return new ChestBlockEntity(type, x, y, z, items);
    }

    @Override public String defaultTitleKey() { return "container.chest"; }


    @Override protected boolean randomizable() { return true; }

    @Override protected boolean usesOpenersCounter() { return true; }

    @Override protected void onOpen(BlockLevel level, Pos pos, int state) { playSound(level, pos, state, "minecraft:block.chest.open"); }

    @Override protected void onClose(BlockLevel level, Pos pos, int state) { playSound(level, pos, state, "minecraft:block.chest.close"); }

    /** 열린 수가 바뀌면 클라이언트 뚜껑 애니메이션용 block_event(1, 수)를 보낸다. */
    @Override protected void openerCountChanged(BlockLevel level, Pos pos, int state, int old, int now) {
        level.blockEvent(pos, B.block(state), 1, now);
        if (type().equals("minecraft:trapped_chest")) {   // TrappedChestBlockEntity: 여는 수가 신호가 된다
            level.updateNeighborsAt(pos, B.block(state));
            level.updateNeighborsAt(pos.below(), B.block(state));
        }
    }

    /** 바닐라 ChestBlockEntity.triggerEvent: 1 = 뚜껑(클라이언트로 보낸다). */
    @Override public boolean triggerEvent(int type, int data) { return type == 1 || super.triggerEvent(type, data); }

    @Override public boolean isValid(int state) { return B.is(state, "chest") || B.is(state, "trapped_chest"); }

    /** playSound: 더블 상자는 두 칸 한가운데에서, 소리 높이는 level.random(). */
    private static void playSound(BlockLevel level, Pos pos, int state, String sound) {
        double sx = pos.x() + 0.5, sy = pos.y() + 0.5, sz = pos.z() + 0.5;
        if (B.behavior(state) instanceof ChestBlock cb && !cb.isSingle(state)) {
            int d = ChestBlock.getConnectedDirection(state);
            sx += org.domi.furnace.server.block.Dir.DX[d] * 0.5;
            sz += org.domi.furnace.server.block.Dir.DZ[d] * 0.5;
        }
        level.playSound(null, sx, sy, sz, sound, "blocks", 0.5F, level.random().nextFloat() * 0.1F + 0.9F);
    }
}
