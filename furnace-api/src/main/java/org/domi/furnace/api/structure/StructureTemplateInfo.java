// 구조물 템플릿 요약.
package org.domi.furnace.api.structure;

/**
 * @param id            "네임스페이스:경로"
 * @param sizeX         크기(칸) — 회전 전
 * @param paletteCount  팔레트 수(대부분 1, 배·집 변형은 여럿)
 * @param blockCount    첫 팔레트의 칸 수(공기 포함, 구조물 빈 칸은 빠진다)
 * @param entityCount   템플릿에 든 엔티티 수
 * @param author        작성자(메모리 템플릿만 — 파일에는 없다). 없으면 ""
 * @param onDisk        월드 generated/ 에 .nbt 파일이 있는가
 */
public record StructureTemplateInfo(String id, int sizeX, int sizeY, int sizeZ, int paletteCount, int blockCount, int entityCount, String author,
                                    boolean onDisk) {
}
