// 지형 생성 feature 를 플러그인이 놓은 결과(World#placeFeatureNow·growChorusPlantNow).
package org.domi.furnace.api.world;

import java.util.List;
import java.util.Objects;

/**
 * placed 는 feature 의 반환값이다(닿지 못한 청크가 있어 아무것도 놓지 않았으면 false).
 * writes 는 월드에 반영하지 않았을 때(apply=false)만 채워진다. 쓴 칸마다 하나씩, 처음 쓴 차례로 들어가고 그 칸의 마지막 상태를 담는다.
 * 상태가 그대로인 쓰기도 들어 있다. 부술 수 없는 블록(기반암·방벽·끝 차원문 틀·명령 블록 등 10종) 칸은 빠진다(Paper 의 나무 생성 기록과 같은 모양).
 */
public record FeaturePlacement(boolean placed, List<BlockSnapshot> writes) {

    public FeaturePlacement {
        writes = List.copyOf(Objects.requireNonNull(writes, "writes"));
    }
}
