// 시험용: ServerLoginContext 의 패키지 전용 상태 JSON 을 다른 패키지 시험에서 부른다.
package org.domi.furnace.server.net;

import java.util.List;

public final class ServerLoginContextAccess {
    private ServerLoginContextAccess() {}

    public static ServerLoginContext offline(String motd) { return ServerLoginContext.offline(motd); }

    public static String statusJson(ServerLoginContext ctx, org.domi.furnace.server.plugin.GameEvents events) {
        return ctx.statusJson(List.of(), List.of(), events, null);
    }
}
