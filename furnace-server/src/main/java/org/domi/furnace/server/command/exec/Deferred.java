// 지연 결과(Furnace 전용): 명령 본문이 source.defer 로 넘긴 미완료 결과 future 와, 결과 처리에 필요한 forked 여부·명령 문자열.
package org.domi.furnace.server.command.exec;

import java.util.concurrent.CompletableFuture;

public record Deferred(CompletableFuture<Integer> future, boolean forked, String input) {}
