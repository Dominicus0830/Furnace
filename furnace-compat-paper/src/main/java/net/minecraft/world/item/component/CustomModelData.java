package net.minecraft.world.item.component;

/** 모델 번호(Mojang 이름 shim — 바닐라 1.21.1 레코드와 같은 모양). */
public record CustomModelData(int value) {
    public static final CustomModelData DEFAULT = new CustomModelData(0);
}
