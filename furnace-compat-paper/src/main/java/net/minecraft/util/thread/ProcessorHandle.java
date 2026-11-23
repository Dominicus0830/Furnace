package net.minecraft.util.thread;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/** 메시지를 받는 쪽(Mojang 이름 shim). {@code askEither}(DFU Either 형식 필요)는 두지 않았다. */
public interface ProcessorHandle<Msg> extends AutoCloseable {

    String name();

    void tell(Msg message);

    @Override
    default void close() {}

    default <Source> CompletableFuture<Source> ask(Function<? super ProcessorHandle<Source>, ? extends Msg> messageProvider) {
        CompletableFuture<Source> future = new CompletableFuture<>();
        Msg message = messageProvider.apply(of("ask future processor handle", future::complete));
        tell(message);
        return future;
    }

    static <Msg> ProcessorHandle<Msg> of(String name, Consumer<Msg> tell) {
        return new ProcessorHandle<>() {
            @Override
            public String name() { return name; }

            @Override
            public void tell(Msg message) { tell.accept(message); }

            @Override
            public String toString() { return name; }
        };
    }
}
