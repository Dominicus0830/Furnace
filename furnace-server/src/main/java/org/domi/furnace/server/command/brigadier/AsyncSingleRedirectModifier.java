// 소스 하나를 소스 하나로 바꾸는 비동기 수정자(execute summon·positioned over).
package org.domi.furnace.server.command.brigadier;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface AsyncSingleRedirectModifier<S> {
    CompletableFuture<S> apply(CommandContext<S> context) throws CommandSyntaxException;
}
