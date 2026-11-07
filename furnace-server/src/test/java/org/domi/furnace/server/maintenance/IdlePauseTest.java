// 빈 서버 일시정지: 바닐라 1.21.2+ tickServer 처럼 빈 틱이 문턱(초×20)에 닿는 틱부터 건너뛰고, 그때 한 번 저장한다.
// sprint 중이거나 접속자가 생기면 다시 센다. 설정 기본은 꺼짐, 잘못된 값은 기동 실패.
package org.domi.furnace.server.maintenance;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class IdlePauseTest {

    private final List<String> calls = new ArrayList<>();

    private IdlePause pause(int seconds, boolean flush) {
        return new IdlePause(seconds, flush, new IdlePause.Hooks() {
            @Override public void holdWorlds(boolean on) { calls.add("hold " + on); }
            @Override public void saveAll() { calls.add("save"); }
            @Override public void flushCaches() { calls.add("flush"); }
        }, Runnable::run);
    }

    @Test
    void pausesAtThreshold_andResumesWhenSomeoneJoins() {
        IdlePause p = pause(1, true);
        for (int i = 0; i < 19; i++) assertFalse(p.onGlobalTick(true, false), "틱 " + i);
        assertTrue(calls.isEmpty());
        assertTrue(p.onGlobalTick(true, false), "20번째 빈 틱부터 멈춘다");
        assertEquals(List.of("hold true", "save", "flush"), calls);
        assertTrue(p.paused());
        assertTrue(p.onGlobalTick(true, false));
        assertEquals(3, calls.size(), "저장은 들어갈 때 한 번");
        assertFalse(p.onGlobalTick(false, false), "접속자가 생기면 그 틱부터 돈다");
        assertFalse(p.paused());
        assertEquals("hold false", calls.get(3));
        assertEquals(1, p.pauses());
    }

    @Test
    void sprintResetsTheCount() {
        IdlePause p = pause(1, false);
        for (int i = 0; i < 15; i++) p.onGlobalTick(true, false);
        p.onGlobalTick(true, true);
        for (int i = 0; i < 19; i++) assertFalse(p.onGlobalTick(true, false));
        assertTrue(p.onGlobalTick(true, false));
        assertEquals(List.of("hold true", "save"), calls, "flush-caches=false 면 비우지 않는다");
    }

    @Test
    void optionsDefaultOff_andValidate() {
        var d = MaintenanceOptions.from(new Properties(), w -> fail(w));
        assertFalse(d.idlePauseEnabled());
        assertFalse(d.backupEnabled());
        Properties p = new Properties();
        p.setProperty("idle-pause.empty-seconds", "60");
        p.setProperty("backup.interval-minutes", "2");
        p.setProperty("backup.keep", "0");
        p.setProperty("backup.format", "folder");
        List<String> warns = new ArrayList<>();
        var o = MaintenanceOptions.from(p, warns::add);
        assertEquals(60, o.idlePauseSeconds());
        assertEquals(MaintenanceOptions.BackupFormat.FOLDER, o.backupFormat());
        assertEquals(3, warns.size(), String.join("\n", warns));
        Properties bad = new Properties();
        bad.setProperty("backup.format", "tar");
        assertThrows(IllegalArgumentException.class, () -> MaintenanceOptions.from(bad, w -> { }));
        Properties neg = new Properties();
        neg.setProperty("idle-pause.empty-seconds", "-1");
        assertThrows(IllegalArgumentException.class, () -> MaintenanceOptions.from(neg, w -> { }));
    }
}
