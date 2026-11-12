// 명령 큐 즉시 처리 문(CommandDrainGate): 컨텍스트가 도는 동안만 열리고, 예산을 다 쓰면 닫히고, 새 전역 틱에 되살아나고, 옵션으로 끈다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.config.OperatorOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommandDrainGateTest {

    @BeforeEach
    void reset() {
        while (CommandDrainGate.contexts() > 0) CommandDrainGate.exit();
        CommandDrainGate.newGlobalTick();
    }

    @AfterEach
    void restore() {
        reset();
        OperatorOptions.install(null);
    }

    @Test
    void closedWithoutACommandContext() {
        assertFalse(CommandDrainGate.open());
    }

    @Test
    void opensWhileAContextRunsAndClosesWhenItEnds() {
        CommandDrainGate.enter();
        assertTrue(CommandDrainGate.open());
        CommandDrainGate.exit();
        assertFalse(CommandDrainGate.open());
    }

    @Test
    void closesWhenTheTickBudgetIsSpentAndReopensOnTheNextGlobalTick() {
        CommandDrainGate.enter();
        CommandDrainGate.charge(Region.URGENT_WAIT_BUDGET_NANOS - 1);
        assertTrue(CommandDrainGate.open(), "예산이 한 방울 남았다");
        CommandDrainGate.charge(1);
        assertFalse(CommandDrainGate.open(), "예산 소진 — 예전처럼 다음 틱");
        CommandDrainGate.newGlobalTick();
        assertEquals(0L, CommandDrainGate.usedNanos());
        assertTrue(CommandDrainGate.open());
    }

    @Test
    void optionOffKeepsTheGateShut() {
        var d = OperatorOptions.DEFAULT;
        OperatorOptions.install(new OperatorOptions(d.strictChatOrder(), d.unsignedChatAsSystem(), d.serverBrand(), d.announceSleepStatus(),
                d.confirmInterrupt(), d.interruptConfirmSeconds(), d.motdFormat(), d.gravityBlockPortalDupe(), d.sendNamespacedCommands(), false));
        CommandDrainGate.enter();
        assertFalse(CommandDrainGate.open());
    }

    @Test
    void unpairedExitDoesNotGoNegative() {
        CommandDrainGate.exit();
        assertEquals(0, CommandDrainGate.contexts());
        CommandDrainGate.enter();
        assertTrue(CommandDrainGate.open());
    }
}
