package net.minecraft.world.entity.decoration;

import net.minecraft.resources.ResourceLocation;

/**
 * 그림 무늬(Mojang 이름 shim, 엔티티 데이터 설계 D4-3). 바닐라 record 처럼 블록 단위 가로·세로와 그림 위치. 값은 데이터 팩 레지스트리
 * {@code minecraft:painting_variant}({@link org.domi.furnace.compat.nms.NmsVariants#paintings()}) 의 것이다. 코덱은 두지 않았다.
 */
record PaintingVariant {

    public int area() { return width() * height(); }
}
