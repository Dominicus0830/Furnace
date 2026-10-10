// 바닐라 SharedSuggestionProvider.suggest — 입력이 후보의 앞이나 '_'·'.' 다음 부분과 맞으면 제안.
package org.domi.furnace.server.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.server.command.brigadier.Suggestions;
import org.domi.furnace.server.command.brigadier.SuggestionsBuilder;

public final class SharedSuggestions {

    private SharedSuggestions() {}

    public static CompletableFuture<Suggestions> suggest(Iterable<String> values, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String s : values) if (matchesSubStr(remaining, s.toLowerCase(Locale.ROOT))) builder.suggest(s);
        return builder.buildFuture();
    }

    public static CompletableFuture<Suggestions> suggest(String[] values, SuggestionsBuilder builder) {
        return suggest(java.util.Arrays.asList(values), builder);
    }

    /** 바닐라 SharedSuggestionProvider.TextCoordinates — 좌표 인자 제안 후보("~ ~ ~" 또는 "^ ^ ^"). */
    public record TextCoordinates(String x, String y, String z) {
        public static final TextCoordinates DEFAULT_LOCAL = new TextCoordinates("^", "^", "^");
        public static final TextCoordinates DEFAULT_GLOBAL = new TextCoordinates("~", "~", "~");
    }

    /** 바닐라 suggest2DCoordinates: 입력한 좌표 수에 따라 다음 칸을 채운 후보를 제안(검증을 통과한 것만). */
    public static CompletableFuture<Suggestions> suggest2DCoordinates(String remaining, Collection<TextCoordinates> candidates,
                                                                      SuggestionsBuilder builder, Predicate<String> validator) {
        List<String> out = new ArrayList<>();
        if (remaining == null || remaining.isEmpty()) {
            for (TextCoordinates c : candidates) {
                String full = c.x() + " " + c.z();
                if (validator.test(full)) {
                    out.add(c.x());
                    out.add(full);
                }
            }
        } else {
            String[] parts = remaining.split(" ");
            if (parts.length == 1) {
                for (TextCoordinates c : candidates) {
                    String full = parts[0] + " " + c.z();
                    if (validator.test(full)) out.add(full);
                }
            }
        }
        return suggest(out, builder);
    }

    static boolean matchesSubStr(String input, String substring) {
        for (int i = 0; !substring.startsWith(input, i); i++) {
            int dot = substring.indexOf('.', i);
            int under = substring.indexOf('_', i);
            if (Math.max(dot, under) < 0) return false;
            if (dot >= 0 && under >= 0) i = Math.min(under, dot);
            else i = dot >= 0 ? dot : under;
        }
        return true;
    }
}
