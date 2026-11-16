// 콘솔 줄 모양이 Paper 와 같은가: "[HH:mm:ss LEVEL]: 메시지", 라이브러리 로거는 [이름] 접두, 터미널일 때만 WARN 노랑·ERROR 빨강.
package org.domi.furnace.server.log;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.LoggingEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConsoleFormatTest {

    @AfterEach
    void resetColors() { AnsiMessageConverter.forceColorsForTest(null); }

    /** 실제 logback.xml 의 콘솔 패턴을 그대로 가져와(설정과 시험이 어긋나지 않게) 한 줄을 만든다. */
    private static String line(String logger, Level level, String message) throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/logback.xml"), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("name=\"SysOut\".*?<pattern>(.*?)</pattern>", Pattern.DOTALL).matcher(xml);
        assertTrue(m.find(), "SysOut pattern not found in logback.xml");
        LoggerContext ctx = new LoggerContext();
        PatternLayout layout = new PatternLayout();
        layout.setContext(ctx);
        layout.getInstanceConverterMap().put("fmsg", AnsiMessageConverter.class.getName());
        layout.getInstanceConverterMap().put("fprefix", ConsolePrefixConverter.class.getName());
        layout.getInstanceConverterMap().put("fcolor", LevelColorConverters.Start.class.getName());
        layout.getInstanceConverterMap().put("fcolorend", LevelColorConverters.End.class.getName());
        layout.setPattern(m.group(1).strip());
        layout.start();
        return layout.doLayout(new LoggingEvent(logger, ctx.getLogger(logger), level, message, null, null));
    }

    private static void assertLine(String regex, String actual) {
        assertTrue(actual.matches(regex + "\\R"), "got: " + actual.replace("\u001B", "<ESC>").replace("\n", "<LF>"));
    }

    @Test
    void consoleLineIsPaperShaped() throws Exception {
        AnsiMessageConverter.forceColorsForTest(false);
        assertLine("\\[\\d\\d:\\d\\d:\\d\\d INFO]: Done \\(1\\.0s\\)!", line("org.domi.furnace.server.FurnaceMain", Level.INFO, "Done (1.0s)!"));
        assertLine("\\[\\d\\d:\\d\\d:\\d\\d WARN]: careful", line("Furnace", Level.WARN, "careful"));
    }

    @Test
    void libraryLoggersGetTheirNameAsPrefixLikePaper() throws Exception {
        AnsiMessageConverter.forceColorsForTest(false);
        assertLine("\\[\\d\\d:\\d\\d:\\d\\d INFO]: \\[com\\.zaxxer\\.hikari\\.HikariDataSource] Start completed\\.",
                line("com.zaxxer.hikari.HikariDataSource", Level.INFO, "Start completed."));
        assertEquals("", ConsolePrefixConverter.prefix("net.minecraft.server.MinecraftServer"));
        assertEquals("", ConsolePrefixConverter.prefix("org.bukkit.craftbukkit.CraftServer"));
        assertEquals("", ConsolePrefixConverter.prefix("DailyJob"));   // 플러그인 로거: 메시지에 이미 [이름] 이 있다
        assertEquals("[org.sqlite.JDBC] ", ConsolePrefixConverter.prefix("org.sqlite.JDBC"));
    }

    @Test
    void warnAndErrorAreColoredOnlyWhenColorsAreOn() throws Exception {
        AnsiMessageConverter.forceColorsForTest(true);
        String warn = line("Furnace", Level.WARN, "w");
        String error = line("Furnace", Level.ERROR, "e");
        String info = line("Furnace", Level.INFO, "i");
        assertTrue(warn.startsWith("\u001B[33m") && warn.contains("WARN]: w\u001B[0m"), warn);   // 노랑 — 끝에서 되돌림
        assertTrue(error.startsWith("\u001B[31m") && error.contains("ERROR]: e\u001B[0m"), error);   // 빨강
        assertFalse(info.contains("\u001B["), info);
        AnsiMessageConverter.forceColorsForTest(false);
        assertFalse(line("Furnace", Level.WARN, "w").contains("\u001B["));
    }
}
