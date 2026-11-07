// 간단한 명령(Paper BasicCommand 와 비슷): 이름 뒤의 인자를 공백으로 나눈 배열로 받는다.
// 실행은 전역 스레드에서(명령 큐가 전역으로 모은다), 자동완성도 플레이어는 전역 스레드(ConnectionHandler 가 넘긴다)·콘솔은 콘솔 스레드에서 불린다 — 월드를 만질 때는 region 스케줄러로 넘긴다.
package org.domi.furnace.api.command;

import java.util.List;

@FunctionalInterface
public interface BasicCommand {

    /** @param args 인자(없으면 빈 배열). 마지막이 빈 문자열이면 사용자가 공백까지 친 것. */
    void execute(CommandSender sender, String[] args);

    /** 자동완성 후보(마지막 인자 자리에 들어갈 문자열). 기본은 없음. */
    default List<String> suggest(CommandSender sender, String[] args) { return List.of(); }

    /**
     * 입력한 이름(별칭·"플러그인:이름" 포함, 앞의 '/' 없이)까지 받는 실행. 서버는 이쪽을 부르고, 기본 구현은 {@link #execute(CommandSender, String[])} 로 넘긴다.
     * Bukkit 의 {@code onCommand(sender, command, label, args)} 처럼 쓰는 이름이 필요한 명령(호환 계층)이 덮어쓴다.
     */
    default void execute(CommandSender sender, String label, String[] args) { execute(sender, args); }

    /** {@link #execute(CommandSender, String, String[])} 와 같은 뜻의 자동완성 — 입력한 이름까지 받는다. */
    default List<String> suggest(CommandSender sender, String label, String[] args) { return suggest(sender, args); }

    /** 이 명령을 볼 수 있고 쓸 수 있는가. 기본은 {@link #permission()} 이 있으면 그 권한, 없으면 누구나. */
    default boolean canUse(CommandSender sender) {
        String p = permission();
        return p == null || sender.hasPermission(p);
    }

    /** 필요한 권한(없으면 null). */
    default String permission() { return null; }
}
