package net.minecraft.world.food;

import net.minecraft.world.entity.player.Player;
import org.domi.furnace.compat.nms.NmsChunks;

/**
 * 플레이어의 허기·포화·소모(Mojang 이름 shim, NMS shim 5번 묶음). 값을 따로 들지 않는 보기다 — 읽을 때마다 서버 플레이어의 지금 값이고, 쓰면 서버의 같은 값이
 * 바뀐다(바닐라처럼 {@code Player#getFoodData()} 가 주는 것은 그 플레이어의 것이다). 틱(자연 회복·굶주림)·eat·NBT 는 서버 안에 있어 이 shim 에는 없다
 * (호출 위치에서 실패). 접속하지 않은 플레이어는 UnsupportedNmsCall.
 */
class FoodData {

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

    private void set(int field, float value) { NmsChunks.bridge().setFoodFieldNow(handle(), field, value); }

    public int getFoodLevel() { return state().level(); }

    public float getSaturationLevel() { return state().saturation(); }

    public float getExhaustionLevel() { return state().exhaustion(); }

    public boolean needsFood() { return getFoodLevel() < 20; }

    /** 서버는 0..20 으로 자른다(바닐라는 그대로 넣는다 — user-todo). 다른 두 값은 건드리지 않는다. */
    public void setFoodLevel(int level) { set(0, level); }

    public void setSaturation(float saturation) { set(1, saturation); }

    public void setExhaustion(float exhaustion) { set(2, exhaustion); }

    /** 바닐라 addExhaustion: 최대 40. 서버 안에서 한 번에 더한다. */
    public void addExhaustion(float exhaustion) { NmsChunks.bridge().addExhaustionNow(handle(), exhaustion); }
}
