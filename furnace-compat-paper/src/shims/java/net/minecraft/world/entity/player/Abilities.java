package net.minecraft.world.entity.player;

import org.domi.furnace.api.internal.ChunkInternals;

/**
 * 바닐라 플레이어 능력(Mojang 이름 shim, NMS shim 5c — Axiom 비행 속도). 바닐라는 플레이어가 든 객체 자체라 필드를 고치면 곧바로 서버 상태다.
 * Furnace 에서 그 상태는 서버가 들고 있으므로, {@code ServerPlayer#getAbilities} 가 주는 이 객체는 서버 값을 비춘 보기다:
 * <ul>
 *   <li>{@link #setFlyingSpeed}·{@link #setWalkingSpeed} 는 곧바로 서버에 쓴다(바닐라처럼 패킷은 보내지 않는다).</li>
 *   <li>공개 필드를 직접 고친 것은 다음 세터나 {@code ServerPlayer#onUpdateAbilities}(바닐라처럼 패킷도 보낸다) 때 서버에 쓴다. 그 전에는 서버의
 *       판정(비행·무적 등)이 아직 옛 값을 본다 — 바닐라에서 필드를 고친 뒤 onUpdateAbilities 를 부르는 흔한 꼴은 결과가 같다.</li>
 *   <li>{@code getAbilities} 는 고친 필드가 없으면 서버의 지금 값으로 다시 맞춘다(게임 모드가 바뀌면 따라온다).</li>
 * </ul>
 * {@code new Abilities()} 로 만든 플레이어 없는 객체는 바닐라처럼 값만 든다.
 */
class Abilities {

    public boolean invulnerable;
    public boolean flying;
    public boolean mayfly;
    public boolean instabuild;
    public boolean mayBuild = true;
    public float flyingSpeed = 0.05F;
    public float walkingSpeed = 0.1F;

    private final org.domi.furnace.api.entity.Player owner;
    /** 마지막으로 서버와 맞춘 값(플레이어 것만) — 지금 필드와 다르면 아직 서버에 쓰지 않은 고침이 있다. */
    private ChunkInternals.PlayerAbilities synced;

    public Abilities() { this.owner = null; }

    private Abilities(org.domi.furnace.api.entity.Player owner) { this.owner = owner; }

    /** 호환 모듈 전용 — 그 플레이어의 능력 보기. */
    public static Abilities furnaceOf(org.domi.furnace.api.entity.Player owner) {
        Abilities a = new Abilities(owner);
        a.furnaceRefresh();
        return a;
    }

    private ChunkInternals.PlayerAbilities now() {
        return new ChunkInternals.PlayerAbilities(invulnerable, flying, mayfly, instabuild, mayBuild, flyingSpeed, walkingSpeed);
    }

    /** 호환 모듈 전용 — 고친 필드가 없으면 서버 값으로 다시 맞춘다. */
    public synchronized void furnaceRefresh() {
        if (owner == null || synced != null && !synced.equals(now())) return;
        apply(org.domi.furnace.compat.nms.NmsChunks.bridge().playerAbilities(owner));
    }

    /** 호환 모듈 전용 — 지금 필드를 서버에 쓴다(send 면 능력 패킷도). */
    public synchronized void furnacePush(boolean send) {
        if (owner == null) return;
        var v = now();
        org.domi.furnace.compat.nms.NmsChunks.bridge().setPlayerAbilities(owner, v, send);
        synced = v;
    }

    private void apply(ChunkInternals.PlayerAbilities v) {
        invulnerable = v.invulnerable();
        flying = v.flying();
        mayfly = v.mayfly();
        instabuild = v.instabuild();
        mayBuild = v.mayBuild();
        flyingSpeed = v.flyingSpeed();
        walkingSpeed = v.walkingSpeed();
        synced = v;
    }

    public float getFlyingSpeed() { return flyingSpeed; }

    public void setFlyingSpeed(float flyingSpeed) {
        this.flyingSpeed = flyingSpeed;
        furnacePush(false);
    }

    public float getWalkingSpeed() { return walkingSpeed; }

    public void setWalkingSpeed(float walkingSpeed) {
        this.walkingSpeed = walkingSpeed;
        furnacePush(false);
    }
}
