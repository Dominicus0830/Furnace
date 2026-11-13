// 명령 큐 즉시 처리 문(CommandDrainGate): 명령 범위 표시, 예산을 다 쓰면 닫히고 새 전역 틱에 되살아나고, 옵션으로 끈다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.config.OperatorOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommandDrainGateTest {

    @BeforeEach
    void reset() {
        CommandDrainGate.newGlobalTick();
    }

    @AfterEach
    void restore() {
        reset();
        OperatorOptions.install(null);
        while (CommandDrainGate.inScope()) CommandDrainGate.exitScope();
    }

    @Test
    void scopeIsMarkedOnlyInsideAndNests() {
        assertFalse(CommandDrainGate.inScope());
        CommandDrainGate.inCommandScope(() -> {
            assertTrue(CommandDrainGate.inScope());
            CommandDrainGate.inCommandScope(() -> assertTrue(CommandDrainGate.inScope()));
            assertTrue(CommandDrainGate.inScope(), "안쪽이 나와도 바깥은 아직 명령 범위");
        });
        assertFalse(CommandDrainGate.inScope());
    }

    @Test
    void scopeIsPerThreadAndLeaksNowhereAfterAnException() throws Exception {
        assertThrows(IllegalStateException.class, () -> CommandDrainGate.inCommandScope(() -> { throw new IllegalStateException(); }));
        assertFalse(CommandDrainGate.inScope(), "예외 뒤에도 범위가 닫힌다");
        boolean[] other = {true};
        CommandDrainGate.inCommandScope(() -> {
            Thread t = new Thread(() -> other[0] = CommandDrainGate.inScope());
            t.start();
            try { t.join(); } catch (InterruptedException e) { throw new RuntimeException(e); }
        });
        assertFalse(other[0], "다른 스레드는 명령 범위가 아니다");
    }

    @Test
    void exitWithoutEnterDoesNotGoNegative() {
        CommandDrainGate.exitScope();
        assertFalse(CommandDrainGate.inScope());
        CommandDrainGate.enterScope();
        assertTrue(CommandDrainGate.inScope());
    }

    @Test
    void closesWhenTheTickBudgetIsSpentAndReopensOnTheNextGlobalTick() {
        assertTrue(CommandDrainGate.open());
        CommandDrainGate.charge(Region.URGENT_WAIT_BUDGET_NANOS - 1);
        assertTrue(CommandDrainGate.open(), "예산이 한 방울 남았다");
        assertEquals(1L, CommandDrainGate.remainingNanos());
        CommandDrainGate.charge(1);
        assertFalse(CommandDrainGate.open(), "예산 소진 — 예전처럼 다음 틱");
        assertEquals(0L, CommandDrainGate.remainingNanos());
        CommandDrainGate.newGlobalTick();
        assertEquals(0L, CommandDrainGate.usedNanos());
        assertTrue(CommandDrainGate.open());
    }

    @Test
    void optionOffKeepsTheGateShut() {
        var d = OperatorOptions.DEFAULT;
        OperatorOptions.install(new OperatorOptions(d.strictChatOrder(), d.unsignedChatAsSystem(), d.serverBrand(), d.announceSleepStatus(),
                d.confirmInterrupt(), d.interruptConfirmSeconds(), d.motdFormat(), d.gravityBlockPortalDupe(), d.sendNamespacedCommands(), false));
        assertFalse(CommandDrainGate.open());
    }
}
