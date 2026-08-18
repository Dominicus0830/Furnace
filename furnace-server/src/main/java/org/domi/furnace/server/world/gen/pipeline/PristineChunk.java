package org.domi.furnace.server.world.gen.pipeline;

import java.util.List;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.structure.StructureStart;

// CARVERS 상태(장식 이전) pristine 스냅샷 — (seed,cx,cz)의 순수함수라 캐시 안전.
// col은 절대 직접 mutate 금지 — 사용할 때는 항상 col.copy()로 떠서 쓴다(decoration이 원본을 변형하므로).
// biomeIds/starts는 decoration 경로에서 읽기만 하므로(FeatureWorldView 확인) 복사 없이 공유해도 안전.
record PristineChunk(ChunkColumn col, int[] biomeIds, List<StructureStart> starts) {}
