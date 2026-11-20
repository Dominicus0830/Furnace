package net.minecraft.world.food;

import net.minecraft.world.entity.player.Player;
import org.domi.furnace.compat.nms.NmsChunks;

/**
 * 플레이어의 허기·포화·소모(Mojang 이름 shim, NMS shim 5번 묶음). 값을 따로 들지 않는 보기다 — 읽을 때마다 서버 플레이어의 지금 값이고, 쓰면 서버의 같은 값이
 * 바뀐다(바닐라처럼 {@code Player#getFoodData()} 가 주는 것은 그 플레이어의 것이다). 틱(자연 회복·굶주림)·eat·NBT 는 서버 안에 있어 이 shim 에는 없다
 * (호출 위치에서 실패). 접속하지 않은 플레이어는 UnsupportedNmsCall.
 */
public class FoodData {

    private final Player player;

    /** 호환 모듈 전용({@code Player#getFoodData()} 가 만든다). */
    public FoodData(Player player) { this.player = java.util.Objects.requireNonNull(player, "player"); }

    private org.domi.furnace.api.entity.Player handle() {
        if (player.getBukkitEntity() instanceof org.bukkit.craftbukkit.entity.CraftPlayer cp) return cp.furnaceHandle();
        throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("FoodData", "only a connected player has food data");
    }

    private org.domi.furnace.api.internal.ChunkInternals.FoodState state() {
        try {
            return NmsChunks.bridge().foodNow(handle());
        } catch (IllegalArgumentException e) {
            throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("FoodData", e.getMessage());
        }
    }

    private void set(int level, float saturation, float exhaustion) { NmsChunks.bridge().setFoodNow(handle(), level, saturation, exhaustion); }

    public int getFoodLevel() { return state().level(); }

    public float getSaturationLevel() { return state().saturation(); }

    public float getExhaustionLevel() { return state().exhaustion(); }

    public boolean needsFood() { return getFoodLevel() < 20; }

    /** 바닐라: 0..20 으로 자르지 않고 그대로 넣지만 서버는 0..20 으로 자른다(바닐라 Player 도 읽을 때 같은 범위다). */
    public void setFoodLevel(int level) {
        var s = state();
        set(level, s.saturation(), s.exhaustion());
    }

    public void setSaturation(float saturation) {
        var s = state();
        set(s.level(), saturation, s.exhaustion());
    }

    public void setExhaustion(float exhaustion) {
        var s = state();
        set(s.level(), s.saturation(), exhaustion);
    }

    /** 바닐라 addExhaustion: 최대 40. */
    public void addExhaustion(float exhaustion) {
        var s = state();
        set(s.level(), s.saturation(), Math.min(s.exhaustion() + exhaustion, 40.0F));
    }
}
