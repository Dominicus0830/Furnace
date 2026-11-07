// 구조물 템플릿을 놓는 선택(Structures.place). /place template 와 구조물 블록의 불러오기가 쓰는 같은 배치 경로의 설정이다.
package org.domi.furnace.api.structure;

import java.util.Objects;

/**
 * @param rotation         회전(원점 둘레, 바닐라 StructurePlaceSettings)
 * @param mirror           대칭
 * @param includeEntities  템플릿에 든 엔티티(액자·그림·갑옷 거치대 …)도 만든다. 기본 true(/place template 와 같다 — 구조물 블록은 기본 꺼짐)
 * @param integrity        0..1 — 1 보다 작으면 블록마다 이 확률로만 놓는다(구조물 블록의 "완전성", BlockRot 프로세서)
 * @param seed             integrity·팔레트 고르기·상자 전리품 시드를 정하는 난수 시드. 0 이면 지금 시각(바닐라와 같다)
 * @param palette          팔레트가 여럿인 템플릿(예: 배의 색 변형)에서 쓸 번호. -1 이면 바닐라처럼 난수로 고른다
 */
public record PlaceOptions(StructureRotation rotation, StructureMirror mirror, boolean includeEntities, float integrity, long seed, int palette) {

    public static final PlaceOptions DEFAULT = new PlaceOptions(StructureRotation.NONE, StructureMirror.NONE, true, 1.0F, 0L, -1);

    public PlaceOptions {
        Objects.requireNonNull(rotation, "rotation");
        Objects.requireNonNull(mirror, "mirror");
        if (!(integrity >= 0.0F && integrity <= 1.0F)) throw new IllegalArgumentException("integrity 는 0..1: " + integrity);
        if (palette < -1) throw new IllegalArgumentException("palette 는 -1 이상: " + palette);
    }

    public PlaceOptions withRotation(StructureRotation r) { return new PlaceOptions(r, mirror, includeEntities, integrity, seed, palette); }

    public PlaceOptions withMirror(StructureMirror m) { return new PlaceOptions(rotation, m, includeEntities, integrity, seed, palette); }

    public PlaceOptions withEntities(boolean include) { return new PlaceOptions(rotation, mirror, include, integrity, seed, palette); }

    public PlaceOptions withIntegrity(float i) { return new PlaceOptions(rotation, mirror, includeEntities, i, seed, palette); }

    public PlaceOptions withSeed(long s) { return new PlaceOptions(rotation, mirror, includeEntities, integrity, s, palette); }

    public PlaceOptions withPalette(int p) { return new PlaceOptions(rotation, mirror, includeEntities, integrity, seed, p); }
}
