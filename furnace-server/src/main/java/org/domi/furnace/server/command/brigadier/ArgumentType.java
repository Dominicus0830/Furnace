// Brigadier ArgumentType. 서버→클라 commands 패킷에 실을 인자 타입 정보(serializer id·속성)도 함께 정의한다.
package org.domi.furnace.server.command.brigadier;

import io.netty.buffer.ByteBuf;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface ArgumentType<T> {

    T parse(StringReader reader) throws CommandSyntaxException;

    default <S> T parse(StringReader reader, S source) throws CommandSyntaxException { return parse(reader); }

    default <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return Suggestions.empty();
    }

    default Collection<String> getExamples() { return List.of(); }

    /** command_argument_type 레지스트리 이름(예: brigadier:integer, minecraft:entity). */
    String networkId();

    /** commands 패킷의 인자 속성(없으면 아무것도 쓰지 않는다). */
    default void writeProperties(ByteBuf buf) {}
}
