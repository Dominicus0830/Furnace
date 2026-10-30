// 바닐라 ShulkerBoxBlockEntity: 27칸 용기. 여는 사람이 있으면 뚜껑 애니메이션 상태(닫힘→열리는 중→열림→닫히는 중)를 틱마다 0.1 씩 움직이고,
// 시작·끝 때 이웃 모양·블록을 갱신한다(관측자 등). 소리와 block_event(1, 여는 수)는 여는 즉시.
// 열리는 동안 뚜껑에 닿은 엔티티를 민다(moveCollidedEntities, 이 region 엔티티만 — 플레이어는 클라이언트가 스스로). TODO(블록): canOpen — 뚜껑 앞이 막혔는지.
package org.domi.furnace.server.block.entity;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;

public class ShulkerBoxBlockEntity extends ContainerBlockEntity {

    /** 뚜껑 상태: 닫힘·열리는 중·열림·닫히는 중. */
    public enum AnimationStatus { CLOSED, OPENING, OPENED, CLOSING }

    private AnimationStatus status = AnimationStatus.CLOSED;
    private float progress, progressOld;

    public ShulkerBoxBlockEntity(Pos pos, int state) { super("minecraft:shulker_box", pos, state, 27); }

    @Override public boolean isValid(int state) { return B.isTag(state, "minecraft:shulker_boxes"); }

    @Override public String defaultTitleKey() { return "container.shulkerBox"; }


    @Override protected boolean randomizable() { return true; }

    @Override protected boolean alwaysWriteItems() { return false; }

    public AnimationStatus animationStatus() { return status; }

    @Override public boolean ticking() { return true; }

    @Override protected boolean usesOpenersCounter() { return true; }

    /** 셜커 상자는 recheck 틱을 걸지 않는다(바닐라는 자기 openCount 만 센다). */
    @Override protected boolean rechecksOpeners() { return false; }

    @Override protected void onOpen(BlockLevel level, Pos pos, int state) { playSound(level, pos, "minecraft:block.shulker_box.open"); }

    @Override protected void onClose(BlockLevel level, Pos pos, int state) { playSound(level, pos, "minecraft:block.shulker_box.close"); }

    @Override protected void openerCountChanged(BlockLevel level, Pos pos, int state, int old, int now) {
        level.blockEvent(pos, B.block(state), 1, now);
    }

    private static void playSound(BlockLevel level, Pos pos, String sound) {
        level.playSound(null, pos, sound, "blocks", 0.5F, level.random().nextFloat() * 0.1F + 0.9F);
    }

    /** 바닐라 triggerEvent(1, n): 여는 수를 맞추고 0 이면 닫히는 중, 1 이면 열리는 중으로(클라이언트로도 보낸다). */
    @Override
    public boolean triggerEvent(int type, int data) {
        if (type != 1) return super.triggerEvent(type, data);
        openCount = data;
        if (data == 0) status = AnimationStatus.CLOSING;
        if (data == 1) status = AnimationStatus.OPENING;
        return true;
    }

    /** ShulkerBoxBlockEntity.tick → updateAnimation. */
    @Override
    public void tick(BlockLevel level) {
        progressOld = progress;
        int state = level.getBlockState(pos);
        switch (status) {
            case CLOSED -> progress = 0.0F;
            case OPENING -> {
                progress += 0.1F;
                if (progressOld == 0.0F) doNeighborUpdates(level, state);
                if (progress >= 1.0F) {
                    status = AnimationStatus.OPENED;
                    progress = 1.0F;
                    doNeighborUpdates(level, state);
                }
                moveCollidedEntities(level, state);
            }
            case OPENED -> progress = 1.0F;
            case CLOSING -> {
                progress -= 0.1F;
                if (progressOld == 1.0F) doNeighborUpdates(level, state);
                if (progress <= 0.0F) {
                    status = AnimationStatus.CLOSED;
                    progress = 0.0F;
                    doNeighborUpdates(level, state);
                }
            }
        }
    }

    /** moveCollidedEntities: 이번 틱 뚜껑이 움직인 얇은 상자(getProgressDeltaAabb)에 닿은 엔티티를 그 두께 + 0.01 만큼 연 쪽으로. */
    private void moveCollidedEntities(BlockLevel level, int state) {
        if (!B.isTag(state, "minecraft:shulker_boxes") || !(level instanceof org.domi.furnace.server.block.ChunkLevel cl)) return;
        int dir = org.domi.furnace.server.block.Dir.byName(FACING.valueName(state));
        var box = org.domi.furnace.server.entity.Shulker.progressAabb(1.0F, dir, progressOld, progress).move(pos.x(), pos.y(), pos.z());
        var list = cl.getEntities(null, box, e -> !e.isSpectator());
        for (var e : list) {
            if (e.ignoresPistonPush()) continue;
            e.move(org.domi.furnace.server.entity.Entity.MOVER_SHULKER_BOX, (box.getXsize() + 0.01) * org.domi.furnace.server.block.Dir.DX[dir],
                    (box.getYsize() + 0.01) * org.domi.furnace.server.block.Dir.DY[dir], (box.getZsize() + 0.01) * org.domi.furnace.server.block.Dir.DZ[dir]);
        }
    }

    private static final org.domi.furnace.server.block.Prop FACING = B.prop("facing");

    private void doNeighborUpdates(BlockLevel level, int state) {
        B.updateNeighbourShapes(state, level, pos, 3);
        level.updateNeighborsAt(pos, B.block(state));
    }
}
