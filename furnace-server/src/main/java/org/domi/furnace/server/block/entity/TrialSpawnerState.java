// 바닐라 TrialSpawnerState: 시련 생성기 블록 상태(trial_spawner_state). 전이(tickAndGetNext)는 TrialSpawner 가 한다.
// 입자 방출·회전 속도는 클라이언트 몫이라 빛 밝기와 "몹을 만들 수 있는가"만 남긴다.
package org.domi.furnace.server.block.entity;

public enum TrialSpawnerState {
    INACTIVE("inactive", 0, -1.0, false),
    WAITING_FOR_PLAYERS("waiting_for_players", 4, 200.0, true),
    ACTIVE("active", 8, 1000.0, true),
    WAITING_FOR_REWARD_EJECTION("waiting_for_reward_ejection", 8, -1.0, false),
    EJECTING_REWARD("ejecting_reward", 8, -1.0, false),
    COOLDOWN("cooldown", 0, -1.0, false);

    private final String serializedName;
    private final int lightLevel;
    private final double spinningMobSpeed;
    private final boolean isCapableOfSpawning;

    TrialSpawnerState(String name, int lightLevel, double spinningMobSpeed, boolean isCapableOfSpawning) {
        this.serializedName = name;
        this.lightLevel = lightLevel;
        this.spinningMobSpeed = spinningMobSpeed;
        this.isCapableOfSpawning = isCapableOfSpawning;
    }

    public String getSerializedName() { return serializedName; }

    public int lightLevel() { return lightLevel; }

    /** hasSpinningMob: 회전 속도가 0 이상 — 표시용 몹을 만들 수 있다. */
    public boolean hasSpinningMob() { return spinningMobSpeed >= 0.0; }

    /** isCapableOfSpawning: 몹/아이템 생성 입자·소리를 낸다(상태 ACTIVE·WAITING_FOR_PLAYERS). */
    public boolean isCapableOfSpawning() { return isCapableOfSpawning; }

    public static TrialSpawnerState byName(String name) {
        for (TrialSpawnerState s : values()) if (s.serializedName.equals(name)) return s;
        return INACTIVE;
    }
}
