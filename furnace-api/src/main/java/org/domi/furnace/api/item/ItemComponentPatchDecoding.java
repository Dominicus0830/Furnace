// 패치 저장 꼴 읽기 결과(바닐라 DispatchedMapCodec.decode 의 부분 결과): 좋은 항목의 패치와 나쁜 항목의 오류·키. 불변.
package org.domi.furnace.api.item;

import java.util.List;

/**
 * @param patch      읽을 수 있었던 항목만 담은 패치
 * @param errors     나쁜 항목마다 오류 문구(입력 순서). 비어 있으면 전부 읽은 것
 * @param failedKeys 나쁜 항목의 키(받은 그대로, 입력 순서)
 */
public record ItemComponentPatchDecoding(ItemComponentPatch patch, List<String> errors, List<String> failedKeys) {

    public ItemComponentPatchDecoding {
        errors = List.copyOf(errors);
        failedKeys = List.copyOf(failedKeys);
    }

    public boolean ok() { return errors.isEmpty(); }
}
