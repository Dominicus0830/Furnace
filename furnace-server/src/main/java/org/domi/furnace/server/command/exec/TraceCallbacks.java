// 바닐라 TraceCallbacks(hv) — 명령 실행 추적기. /debug function 이 실행 컨텍스트에 달면 실행 코어가 명령 시작·결과·함수 호출·오류를 알린다.
// 전역 스레드에서만 불린다(실행 컨텍스트와 같다).
package org.domi.furnace.server.command.exec;

import org.domi.furnace.server.data.ResourceId;

public interface TraceCallbacks extends AutoCloseable {

    /** 명령(수정자 단계를 포함한 입력 전체)을 시작한다. depth 는 함수 호출 깊이. */
    void onCommand(int depth, String command);

    /** 명령이 끝나 결과를 냈다. */
    void onReturn(int depth, String command, int result);

    /** 명령 오류(문법·실행 오류 메시지의 평문). */
    void onError(String message);

    /** 함수를 부른다. size 는 함수 줄(항목) 수. */
    void onCall(int depth, ResourceId function, int size);

    @Override
    void close();
}
