// Structures.save 의 결과.
package org.domi.furnace.api.structure;

/**
 * @param status  결과
 * @param info    저장한 템플릿의 요약. SAVED·WRITE_FAILED 일 때만(WRITE_FAILED 면 메모리에는 들어 있다), 아니면 null
 */
public record SaveResult(Status status, StructureTemplateInfo info) {

    public enum Status {
        /** 메모리 저장소를 갈아 끼웠고(toDisk 면 파일로도 썼다). */
        SAVED,
        /** 이름이 올바른 "네임스페이스:경로"가 아니다(대문자·'//'·'..' 등). */
        INVALID_NAME,
        /** 크기가 1 미만이거나 한 변이 48(구조물 블록 한계)을 넘는다. */
        INVALID_SIZE,
        /** 영역이 월드 높이 밖으로 나가거나 좌표가 ±30,000,000 밖이다. */
        OUT_OF_WORLD,
        /** 영역의 청크가 모두 한 region 에 올라와 있지 못했다 — 아무것도 저장하지 않았다. */
        NOT_LOADED,
        /** 메모리 저장소는 갈아 끼웠지만 파일을 쓰지 못했다(로그에 이유). */
        WRITE_FAILED
    }

    public boolean saved() { return status == Status.SAVED; }
}
