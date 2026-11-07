// Structures.locate 의 결과 — 가장 가까운 구조물의 시작점.
package org.domi.furnace.api.structure;

/** @param structureId 찾은 구조물 종류(minecraft:village_plains …) — 태그로 찾았으면 그 안에서 실제 찾은 것 */
public record StructureLocation(String structureId, int x, int y, int z) {
}
