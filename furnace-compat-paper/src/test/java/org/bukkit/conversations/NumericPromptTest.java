package org.bukkit.conversations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NumericPromptTest {

    /**
     * Paper 1.21.1 이 쓰는 commons-lang3 3.14.0 {@code NumberUtils.isCreatable}·{@code createNumber} 를 실제 jar 로 돌린 결과(형:값, 판별에서
     * 떨어지면 invalid, 변환이 던지면 EX 예외 이름). PR #338 검토가 쓴 입력 99개.
     */
    private static final String[][] LANG3 = {
            {"1", "Integer:1"},
            {"-1", "Integer:-1"},
            {"+1", "Integer:1"},
            {"07", "Integer:7"},
            {"08", "invalid"},
            {"09", "invalid"},
            {"0", "Integer:0"},
            {"00", "Integer:0"},
            {"-0", "Integer:0"},
            {"1.5", "Float:1.5"},
            {"1.", "Float:1.0"},
            {"- 1", "invalid"},
            {".5", "Float:0.5"},
            {"-.5", "Float:-0.5"},
            {"1e3", "Float:1000.0"},
            {"1E3", "Float:1000.0"},
            {"1e+3", "Float:1000.0"},
            {"1e-3", "Float:0.001"},
            {"1.5e3", "Float:1500.0"},
            {"1f", "Float:1.0"},
            {"1.5f", "Float:1.5"},
            {"1d", "Double:1.0"},
            {"1.5D", "Double:1.5"},
            {"1l", "Long:1"},
            {"1L", "Long:1"},
            {"0x10", "Integer:16"},
            {"0X1f", "Integer:31"},
            {"-0x10", "Integer:-16"},
            {"0x", "invalid"},
            {"0xFFFFFFFF", "Long:4294967295"},
            {"0x7fffffff", "Integer:2147483647"},
            {"0x80000000", "Long:2147483648"},
            {"0x1234567890", "Long:78187493520"},
            {"#10", "invalid"},
            {"-#10", "invalid"},
            {"2147483647", "Integer:2147483647"},
            {"2147483648", "Long:2147483648"},
            {"9223372036854775807", "Long:9223372036854775807"},
            {"9223372036854775808", "BigInteger:9223372036854775808"},
            {"123456789012345678901234567890", "BigInteger:123456789012345678901234567890"},
            {"1.23456789", "Double:1.23456789"},
            {"3.14159265358979", "Double:3.14159265358979"},
            {"0.1", "Float:0.1"},
            {"0.10000001", "Float:0.10000001"},
            {"1e39", "Double:1.0E39"},
            {"1e400", "BigDecimal:1E+400"},
            {"1e-50", "Double:1.0E-50"},
            {"1e-400", "BigDecimal:1E-400"},
            {"0.0", "Float:0.0"},
            {"0.000", "Float:0.0"},
            {"1.0000000001", "Double:1.0000000001"},
            {"16777217", "Integer:16777217"},
            {"16777217.0", "Double:1.6777217E7"},
            {"1e", "invalid"},
            {"e3", "invalid"},
            {"1.2.3", "invalid"},
            {"", "invalid"},
            {"  1", "invalid"},
            {" 1", "invalid"},
            {"1 ", "invalid"},
            {"abc", "invalid"},
            {"1,000", "invalid"},
            {"?", "invalid"},
            {"?", "invalid"},
            {"Infinity", "invalid"},
            {"NaN", "invalid"},
            {"0b101", "invalid"},
            {"1_000", "invalid"},
            {"--1", "invalid"},
            {"1e3L", "invalid"},
            {"1.5L", "invalid"},
            {"1Lf", "invalid"},
            {"0x1L", "invalid"},
            {"1.0e10f", "Float:1.0E10"},
            {"3.4028236e38", "Double:3.4028236E38"},
            {"1.17549435E-38", "Double:1.17549435E-38"},
            {"1.4E-45", "Float:1.4E-45"},
            {"1e-46", "Double:1.0E-46"},
            {"100000000000000000000.5", "Float:1.0E20"},
            {"0.12345678", "Float:0.12345678"},
            {"0.1234567890123456789", "BigDecimal:0.1234567890123456789"},
            {"12345678.9", "Double:1.23456789E7"},
            {"123456789.0", "Double:1.23456789E8"},
            {"1.000000000000000001", "Float:1.0"},
            {"-0", "Integer:0"},
            {"0.", "Float:0.0"},
            {"-.0", "Float:-0.0"},
            {"1D", "Double:1.0"},
            {"1F", "Float:1.0"},
            {"0777", "Integer:511"},
            {"0778", "invalid"},
            {"-07", "Integer:-7"},
            {"0x0", "Integer:0"},
            {"0e0", "Float:0.0"},
            {"1e0f", "Float:1.0"},
            {"9999999999999999999999d", "Double:1.0E22"},
            {"1e308", "Double:1.0E308"},
            {"1.7976931348623159e308", "BigDecimal:1.7976931348623159E+308"},
            {"2e-45f", "Float:1.4E-45"},
    };

    @Test
    void numericPromptMatchesCommonsLang3() {
        List<String> diff = new ArrayList<>();
        for (String[] row : LANG3) {
            String got;
            if (!NumericPrompt.isNumber(row[0])) {
                got = "invalid";
            } else {
                try {
                    Number n = NumericPrompt.createNumber(row[0]);
                    got = n == null ? "null" : n.getClass().getSimpleName() + ":" + n;
                } catch (Throwable t) {
                    got = "EX " + t.getClass().getSimpleName();
                }
            }
            if (!got.equals(row[1])) diff.add("\"" + row[0] + "\" 기대 " + row[1] + " 실제 " + got);
        }
        assertEquals(List.of(), diff);
    }
}
