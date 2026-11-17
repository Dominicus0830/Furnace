package org.domi.furnace.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.api.event.server.AsyncTabCompleteEvent.Completion;
import org.domi.furnace.api.text.Text;
import org.jline.reader.Candidate;
import org.junit.jupiter.api.Test;

/** 콘솔 자동완성: handled 인 AsyncTabCompleteEvent 의 후보는 명령 트리 후보 뒤에 덧붙는다(Paper ConsoleCommandCompleter.addCompletions). */
class ConsoleTabCompleteMergeTest {

    private static Candidate c(String v) { return new Candidate(v, v, null, null, null, null, false); }

    @Test
    void appendsEventCandidatesAfterBrigadierOnesWithoutDuplicatesOrBlanks() {
        List<Candidate> out = new ArrayList<>(List.of(c("help"), c("heal")));
        ConsoleTerminal.appendEventCandidates(out, List.of(new Completion("heal", null), new Completion("", null),
                new Completion("hello", Text.text("tip")), new Completion("hello", null)));
        assertEquals(List.of("help", "heal", "hello"), out.stream().map(Candidate::value).toList());
        assertEquals("tip", out.get(2).descr());
    }

    @Test
    void emptyEventListLeavesBrigadierCandidatesAlone() {
        List<Candidate> out = new ArrayList<>(List.of(c("help")));
        ConsoleTerminal.appendEventCandidates(out, List.of());
        assertEquals(1, out.size());
    }
}
