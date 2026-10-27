// 플레이어를 블록 행위자로: 위치·시선·게임 모드는 플레이어에서, 손의 스택은 요청 때 잡은 사본에서 읽는다.
// 손을 바꾸면 기록만 하고(changed), 인벤토리 반영은 인벤토리를 가진 쪽(연결 스레드)이 한다.
package org.domi.furnace.server.block;

import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.world.GameMode;

public class PlayerActor implements BlockActor {

    private final Player player;
    private final ItemStack[] hands;
    private final boolean[] changed = new boolean[2];
    private final int permissionLevel;

    /** 손 사본 없이(파괴 효과의 "이 플레이어 빼고" 같은 용도). */
    public PlayerActor(Player player) { this(player, ItemStack.EMPTY, ItemStack.EMPTY, 0); }

    public PlayerActor(Player player, ItemStack mainHand, ItemStack offHand, int permissionLevel) {
        this.player = player;
        this.hands = new ItemStack[] {mainHand == null ? ItemStack.EMPTY : mainHand, offHand == null ? ItemStack.EMPTY : offHand};
        this.permissionLevel = permissionLevel;
    }

    public Player player() { return player; }

    /** 그 손의 스택이 바뀌었는가. */
    public boolean handChanged(int hand) { return changed[hand]; }

    @Override public float yRot() { return player.yaw(); }
    @Override public float xRot() { return player.pitch(); }
    @Override public double x() { return player.x(); }
    @Override public double y() { return player.y(); }
    @Override public double z() { return player.z(); }
    @Override public double eyeHeight() { return player.sneaking() ? 1.27 : 1.62; }
    @Override public boolean isSecondaryUseActive() { return player.sneaking(); }
    @Override public boolean mayBuild() { return player.vitals().mayBuild(); }
    @Override public boolean canEat(boolean ignoreHunger) { return player.vitals().canEat(ignoreHunger); }
    @Override public void eat(int nutrition, float saturationModifier) { player.vitals().eat(nutrition, saturationModifier); }
    @Override public boolean instabuild() { return player.vitals().instabuild(); }
    @Override public boolean isSpectator() { return player.gameMode() == GameMode.SPECTATOR; }
    @Override public boolean isCreative() { return player.gameMode() == GameMode.CREATIVE; }
    @Override public int permissionLevel() { return permissionLevel; }
    @Override public ItemStack itemInHand(int hand) { return hands[hand]; }

    @Override
    public void setItemInHand(int hand, ItemStack stack) {
        hands[hand] = stack == null ? ItemStack.EMPTY : stack;
        changed[hand] = true;
    }

    @Override public Object handle() { return player; }
    @Override public java.util.UUID uuid() { return player.uuid(); }

    @Override
    public void openTextEdit(Pos pos, int state, boolean front) {
        player.send(new org.domi.furnace.server.net.protocol.play.BlockUpdate(pos.x(), pos.y(), pos.z(), state));
        player.send(new org.domi.furnace.server.net.protocol.play.OpenSignEditor(pos.x(), pos.y(), pos.z(), front));
    }
    @Override public boolean isOnCooldown(int itemId) { return player.isOnCooldown(itemId); }
    @Override public void addCooldown(int itemId, int ticks) { player.addCooldown(itemId, ticks); }
    @Override public double[] knownMovement() { return player.knownMovement(); }
    @Override public boolean onGround() { return player.onGround(); }

    @Override
    public org.domi.furnace.api.text.Text startSleepInBed(BlockLevel level, Pos pos) {
        if (!(level instanceof ChunkLevel cl)) return null;
        var problem = org.domi.furnace.server.entity.PlayerSleeping.startSleepInBed(player, cl, pos);
        return problem == null ? null : problem.message();
    }

    @Override
    public boolean respawnPointIs(String dimension, Pos pos) {
        int[] rp = player.vitals().respawnPos();
        return rp != null && rp[0] == pos.x() && rp[1] == pos.y() && rp[2] == pos.z() && dimension.equals(player.vitals().respawnDimension());
    }

    @Override
    public void setRespawnPosition(String dimension, Pos pos, float angle, boolean forced, boolean sendMessage) {
        player.vitals().setRespawnPosition(dimension, pos == null ? null : new int[] {pos.x(), pos.y(), pos.z()}, angle, forced, sendMessage);
    }

    /** 블록 상호작용 거리 판정(Player.canInteractWithBlock): 블록 상자와 눈 사이 거리 < 사거리 + buffer. 크리에이티브 사거리 5.0. */
    public boolean canInteractWithBlock(Pos pos, double buffer) {
        double r = (isCreative() ? 5.0 : 4.5) + buffer;
        double ex = x(), ey = y() + eyeHeight(), ez = z();
        double dx = Math.max(Math.max(pos.x() - ex, ex - (pos.x() + 1)), 0.0);
        double dy = Math.max(Math.max(pos.y() - ey, ey - (pos.y() + 1)), 0.0);
        double dz = Math.max(Math.max(pos.z() - ez, ez - (pos.z() + 1)), 0.0);
        return dx * dx + dy * dy + dz * dz < r * r;
    }
}
