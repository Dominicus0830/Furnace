// 바닐라 VaultState: 금고 블록 상태(vault_state) — inactive → active(열쇠를 기다림) → unlocking → ejecting(보상을 하나씩 내놓음) → active/inactive.
// 전이·진입/이탈 효과는 VaultBlockEntity.Server 가 부른다. 빛 밝기: inactive 6, 그 밖 12. 클라이언트 입자·주변 소리는 클라이언트 몫.
package org.domi.furnace.server.block.entity;

public enum VaultState {
    INACTIVE("inactive", 6),
    ACTIVE("active", 12),
    UNLOCKING("unlocking", 12),
    EJECTING("ejecting", 12);

    private final String serializedName;
    private final int lightLevel;

    VaultState(String name, int lightLevel) {
        this.serializedName = name;
        this.lightLevel = lightLevel;
    }

    public String getSerializedName() { return serializedName; }

    public int lightLevel() { return lightLevel; }

    public static VaultState byName(String name) {
        for (VaultState s : values()) if (s.serializedName.equals(name)) return s;
        return INACTIVE;
    }
}
