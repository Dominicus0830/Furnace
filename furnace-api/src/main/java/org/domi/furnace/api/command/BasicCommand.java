// 간단한 명령(Paper BasicCommand 와 비슷): 이름 뒤의 인자를 공백으로 나눈 배열로 받는다.
// 실행·자동완성은 명령을 보낸 쪽의 연결 스레드(플레이어) 또는 콘솔 스레드에서 불린다 — 월드를 만질 때는 region 스케줄러로 넘긴다.
package org.domi.furnace.api.command;

import java.util.List;

@FunctionalInterface
public interface BasicCommand {

    /** @param args 인자(없으면 빈 배열). 마지막이 빈 문자열이면 사용자가 공백까지 친 것. */
    void execute(CommandSender sender, String[] args);

    /** 자동완성 후보(마지막 인자 자리에 들어갈 문자열). 기본은 없음. */
    default List<String> suggest(CommandSender sender, String[] args) { return List.of(); }

    /** 이 명령을 볼 수 있고 쓸 수 있는가. 기본은 {@link #permission()} 이 있으면 그 권한, 없으면 누구나. */
    default boolean canUse(CommandSender sender) {
        String p = permission();
        return p == null || sender.hasPermission(p);
    }

    /** 필요한 권한(없으면 null). */
    default String permission() { return null; }
}
