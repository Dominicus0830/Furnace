// 클라이언트가 이름으로 아는 제안 공급자(바닐라 SuggestionProviders). 그 밖의 서버 측 제안은 minecraft:ask_server 로 보낸다.
package org.domi.furnace.server.command;

import org.domi.furnace.server.command.brigadier.SuggestionProvider;

public interface NamedSuggestionProvider extends SuggestionProvider<CommandSource> {

    String ASK_SERVER = "minecraft:ask_server";

    String id();
}
