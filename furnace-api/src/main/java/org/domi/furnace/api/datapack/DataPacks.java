// 데이터 팩 목록·켜고 끄기(바닐라 PackRepository 보기) — Furnace.dataPacks(). 아무 스레드에서 불러도 된다.
// 목록은 부를 때의 사본이다. 켜고 끄면 /datapack enable·disable 과 같이 리소스를 다시 읽는다(비동기, 끝나면 future 완료).
package org.domi.furnace.api.datapack;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.api.text.Text;

public interface DataPacks {

    /** 바닐라 PackSource(PLUGIN 은 Furnace 플러그인 jar 팩). */
    enum Source { DEFAULT, BUILT_IN, FEATURE, WORLD, SERVER, PLUGIN }

    /** 바닐라 PackCompatibility. */
    enum Compatibility { TOO_OLD, TOO_NEW, COMPATIBLE }

    /**
     * 팩 하나. id 는 /datapack 이 보이는 이름(vanilla, file/이름, …). enabled 는 이 사본을 만들 때 켜져 있었는가.
     * displayName 은 바닐라 채팅 링크([이름 (출처)], 켜졌으면 초록·아니면 빨강).
     */
    record Pack(String id, Text title, Text description, boolean required, Compatibility compatibility,
                Set<String> requestedFeatures, Source source, boolean enabled, Text displayName) {}

    /** 폴더를 다시 훑어 새 팩·사라진 팩을 반영한다(켜진 목록은 그대로 — 바닐라 PackRepository.reload). */
    void refresh();

    /** 알려진 모든 팩(이름 순). */
    List<Pack> available();

    /** 켜진 팩(적용 순서, 뒤가 위). */
    List<Pack> enabled();

    Optional<Pack> find(String id);

    /**
     * 켜거나 끈다. 이미 그 상태면 아무것도 안 하고 바로 완료. 켤 때 자리는 /datapack enable 기본(팩의 기본 자리 끝, 고정 팩은 건너뜀).
     * 없는 id 면 IllegalStateException(Paper 와 같은 문구). 데이터 팩이 없는 서버(시험용)면 UnsupportedOperationException.
     */
    CompletableFuture<Void> setEnabled(String id, boolean enabled);
}
