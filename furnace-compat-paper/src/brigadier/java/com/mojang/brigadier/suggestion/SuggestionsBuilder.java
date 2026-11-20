package com.mojang.brigadier.suggestion;

import java.util.concurrent.CompletableFuture;

/** Brigadier 1.3.10 대역(컴파일 전용). */
public class SuggestionsBuilder {
    public CompletableFuture<Suggestions> buildFuture() { return CompletableFuture.completedFuture(new Suggestions()); }
}
