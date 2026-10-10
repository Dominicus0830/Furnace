// 명령을 실행하는 주체 — 플레이어 또는 콘솔. 메시지를 받고 권한을 가진다.
package org.domi.furnace.api.command;

import org.domi.furnace.api.audience.Audience;
import org.domi.furnace.api.permission.Permissible;

public interface CommandSender extends Audience, Permissible {

    /** 플레이어 이름 또는 "Server"(콘솔, 바닐라와 같다). */
    String name();
}
