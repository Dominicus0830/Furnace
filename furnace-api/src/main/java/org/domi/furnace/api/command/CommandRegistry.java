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

    /** 명령 문자열을 실행(앞의 '/' 는 있어도 없어도 된다). 성공 여부(바닐라 결과 값 > 0 또는 플러그인 명령 실행). */
    boolean dispatch(CommandSender sender, String commandLine);

    void unregisterAll(FurnacePlugin plugin);
}
