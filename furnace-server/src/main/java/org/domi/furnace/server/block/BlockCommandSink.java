// 명령 블록·명령 블록 수레가 명령의 출력 대상이자 소스 정보(바닐라 BaseCommandBlock 의 CommandSource + createCommandSourceStack)일 때의 계약.
// 명령 컨텍스트는 전역 스레드에서 돌므로 이 메서드들은 전역 스레드에서 불릴 수 있다 — 읽는 값은 휘발성/불변이어야 하고, 블록·엔티티 상태 변경은 소유 스레드로 넘긴다.
package org.domi.furnace.server.block;

import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.entity.Rider;

public interface BlockCommandSink {

    /** 소스 이름(textName): 사용자 지정 이름 또는 "@". */
    String textName();

    /** 소스 표시 이름. */
    Text displayName();

    double x();

    double y();

    double z();

    float xRot();

    float yRot();

    /** 소스 엔티티(블록은 null, 수레는 그 수레). */
    Rider entity();

    /** CommandSource.acceptsSuccess: sendCommandFeedback 게임룰 && 출력 기록 중. */
    boolean acceptsSuccess();

    /** CommandSource.acceptsFailure: 출력 기록 중. */
    boolean acceptsFailure();

    /** CommandSource.shouldInformAdmins: commandBlockOutput 게임룰. */
    boolean shouldInformAdmins();

    /** CommandSource.sendSystemMessage: 마지막 출력에 "[HH:mm:ss] " 를 붙여 기록. */
    void sendSystemMessage(Text message);
}
