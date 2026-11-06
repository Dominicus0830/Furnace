// 서버 시작 완료("Done") 직후 — 모든 플러그인이 켜진 뒤 한 번. 시작 스레드에서 불린다.
package org.domi.furnace.api.event.server;

import org.domi.furnace.api.event.Event;

public final class ServerLoadEvent extends Event {}
