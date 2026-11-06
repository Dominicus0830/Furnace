// 그림 변종(minecraft:painting_variant) 정의 — 버전 중립. 크기는 블록 단위 1~16, assetId 는 클라 리소스 팩의 그림 텍스처
// (예: "myplugin:sunset" → textures/painting/sunset.png). 그림을 놓을 때 무작위로 나오려면 minecraft:placeable 태그에 넣는다.
package org.domi.furnace.api.registry;

public record PaintingVariant(int width, int height, String assetId) {

    public PaintingVariant {
        if (width < 1 || width > 16 || height < 1 || height > 16) throw new IllegalArgumentException("크기는 1~16: " + width + "x" + height);
        java.util.Objects.requireNonNull(assetId, "assetId");
    }
}
