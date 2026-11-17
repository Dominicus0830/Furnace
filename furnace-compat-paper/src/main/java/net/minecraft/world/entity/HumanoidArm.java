package net.minecraft.world.entity;

/** 주 팔 shim(Mojang 이름, ProtocolLib 다리 2b-3). 바닐라 1.21.1 과 같은 상수·번호(왼쪽 0, 오른쪽 1). */
public enum HumanoidArm implements net.minecraft.util.StringRepresentable {
    LEFT(0, "left", "options.mainHand.left"),
    RIGHT(1, "right", "options.mainHand.right");

    private final int id;
    private final String name;
    private final String translationKey;

    HumanoidArm(int id, String name, String translationKey) {
        this.id = id;
        this.name = name;
        this.translationKey = translationKey;
    }

    public HumanoidArm getOpposite() { return this == LEFT ? RIGHT : LEFT; }

    public int getId() { return id; }

    public String getKey() { return translationKey; }

    @Override
    public String getSerializedName() { return name; }
}
