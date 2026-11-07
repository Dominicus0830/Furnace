// 월드 영역을 구조물 템플릿으로 저장하는 선택(Structures.save).
package org.domi.furnace.api.structure;

import java.util.Objects;

/**
 * @param includeEntities  영역 안 엔티티(플레이어 제외)도 담는다. 기본 false(구조물 블록의 "엔티티 무시" 기본값)
 * @param author           템플릿 작성자(구조물 블록의 작성자 칸). 파일에는 쓰이지 않고 메모리 템플릿에만 남는다(바닐라와 같다)
 * @param toDisk           월드 폴더 generated/&lt;네임스페이스&gt;/structures/&lt;이름&gt;.nbt 로도 쓴다. 거짓이면 메모리 저장소만 갱신한다(구조물 블록의 레드스톤 저장과 같다)
 */
public record SaveOptions(boolean includeEntities, String author, boolean toDisk) {

    public static final SaveOptions DEFAULT = new SaveOptions(false, "", true);

    public SaveOptions {
        Objects.requireNonNull(author, "author");
    }

    public SaveOptions withEntities(boolean include) { return new SaveOptions(include, author, toDisk); }

    public SaveOptions withAuthor(String a) { return new SaveOptions(includeEntities, a, toDisk); }

    public SaveOptions withToDisk(boolean d) { return new SaveOptions(includeEntities, author, d); }
}
