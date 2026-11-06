// 바닐라 Abilities — 무적·비행·즉시 파괴·건축 가능·속도. 게임 모드가 앞의 넷을 정하고 NBT "abilities" 로 저장된다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.PlayerAbilities;
import org.domi.furnace.server.world.GameMode;

public final class Abilities {

    public boolean invulnerable;
    public boolean flying;
    public boolean mayfly;
    public boolean instabuild;
    public boolean mayBuild = true;
    public float flyingSpeed = 0.05f;
    public float walkingSpeed = 0.1f;

    /** 바닐라 GameType.updatePlayerAbilities. */
    public void applyGameMode(GameMode mode) {
        switch (mode) {
            case CREATIVE -> { mayfly = true; instabuild = true; invulnerable = true; }
            case SPECTATOR -> { mayfly = true; instabuild = false; invulnerable = true; flying = true; }
            default -> { mayfly = false; instabuild = false; invulnerable = false; flying = false; }
        }
        mayBuild = !(mode == GameMode.ADVENTURE || mode == GameMode.SPECTATOR);
    }

    public PlayerAbilities packet() {
        return new PlayerAbilities(invulnerable, flying, mayfly, instabuild, flyingSpeed, walkingSpeed);
    }

    public void save(NbtCompound tag) {
        NbtCompound a = new NbtCompound();
        a.putByte("invulnerable", (byte) (invulnerable ? 1 : 0));
        a.putByte("flying", (byte) (flying ? 1 : 0));
        a.putByte("mayfly", (byte) (mayfly ? 1 : 0));
        a.putByte("instabuild", (byte) (instabuild ? 1 : 0));
        a.putByte("mayBuild", (byte) (mayBuild ? 1 : 0));
        a.putFloat("flySpeed", flyingSpeed);
        a.putFloat("walkSpeed", walkingSpeed);
        tag.put("abilities", a);
    }

    public void load(NbtCompound tag) {
        if (!(tag.tags().get("abilities") instanceof NbtCompound a)) return;
        invulnerable = a.getBoolean("invulnerable");
        flying = a.getBoolean("flying");
        mayfly = a.getBoolean("mayfly");
        instabuild = a.getBoolean("instabuild");
        if (a.containsNumber("flySpeed")) {
            flyingSpeed = a.getFloat("flySpeed");
            walkingSpeed = a.getFloat("walkSpeed");
        }
        if (a.containsNumber("mayBuild")) mayBuild = a.getBoolean("mayBuild");
    }
}
