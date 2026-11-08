// 플러그인 명령 등록소. 등록하면 "이름"과 "플러그인이름:이름" 두 가지로 쓸 수 있고(Bukkit·Paper 와 같다),
// 접속자들의 명령 트리(자동완성)도 바로 갱신된다. 플러그인이 꺼지면 그 명령은 자동으로 빠진다.
package org.domi.furnace.api.command;

import java.util.Collection;
import java.util.List;
import org.domi.furnace.api.plugin.FurnacePlugin;

public interface CommandRegistry {

    /**
     * @param label   명령 이름(소문자, 공백·콜론 없음)
     * @param aliases 별칭(같은 규칙)
     * @return 실제로 등록된 이름들(바닐라·다른 플러그인 명령과 겹친 이름은 namespaced 형태만 등록된다)
     */
    Collection<String> register(FurnacePlugin plugin, String label, String description, List<String> aliases, BasicCommand command);

    default Collection<String> register(FurnacePlugin plugin, String label, BasicCommand command) {
        return register(plugin, label, "", List.of(), command);
    }

    /**
     * 명령 문자열을 실행 큐에 넣는다(앞의 '/' 는 있어도 없어도 된다). 어느 스레드에서 불러도 되며 전역 스레드로 넘어가 실행된다.
     * 돌려주는 값은 접수 여부(true)다 — 명령 결과는 실행이 끝난 뒤 정해지므로 여기서 알 수 없다.
     */
    boolean dispatch(CommandSender sender, String commandLine);

    /**
     * 명령 트리의 루트에 그 이름의 명령이 있는가(바닐라·플러그인·"플러그인:이름" 모두, 대소문자 구분). 어느 스레드에서 불러도 된다.
     * 없는 이름을 {@link #dispatch} 하면 바닐라 오류 문장만 나온다 — Bukkit.dispatchCommand 가 false 를 돌려줄지 가릴 때 쓴다.
     */
    boolean hasCommand(String name);

    void unregisterAll(FurnacePlugin plugin);

    /**
     * Brigadier 식 트리를 같은 명령 트리에 등록한다(BasicCommand 와 섞여 쓰이고, 이름 충돌 규칙도 같다).
     * 루트는 literal 이어야 한다. 별칭마다 같은 트리를 한 벌씩 더 붙인다.
     * @return 실제로 등록된 이름들
     */
    Collection<String> register(FurnacePlugin plugin, CommandBuilder root, String description, List<String> aliases);

    default Collection<String> register(FurnacePlugin plugin, CommandBuilder root) {
        return register(plugin, root, "", List.of());
    }

    /** 이 플러그인이 등록한 명령 하나(이름 또는 "플러그인:이름")를 빼고 접속자들의 트리를 다시 보낸다. 없으면 false. */
    boolean unregister(FurnacePlugin plugin, String name);

    /** 한 발신자의 명령 트리를 다시 보낸다(권한이 바뀐 뒤 등). 콘솔이면 아무 일도 없다. */
    void updateCommands(CommandSender sender);

    /** 접속자 모두의 명령 트리를 다시 보낸다. */
    void updateCommands();
}
