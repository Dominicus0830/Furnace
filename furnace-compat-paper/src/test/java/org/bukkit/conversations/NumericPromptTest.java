package org.bukkit.conversations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class NumericPromptTest {

    /** NumericPrompt 는 Bukkit 의 commons-lang 2 NumberUtils 규칙대로 판별·변환한다. */
    @Test
    void numericPromptFollowsCommonsLangRules() {
        assertTrue(NumericPrompt.isNumber("12"));
        assertTrue(NumericPrompt.isNumber("-1.5e3"));
        assertTrue(NumericPrompt.isNumber("0x1F"));
        assertTrue(NumericPrompt.isNumber("7L"));
        assertTrue(NumericPrompt.isNumber(".5"));
        assertFalse(NumericPrompt.isNumber("+1"));
        assertFalse(NumericPrompt.isNumber("1.0L"));
        assertFalse(NumericPrompt.isNumber("abc"));
        assertFalse(NumericPrompt.isNumber(""));
        assertEquals(12, NumericPrompt.createNumber("12"));
        assertEquals(8, NumericPrompt.createNumber("010"));            // Integer.decode — 앞자리 0 은 8진수
        assertEquals(31, NumericPrompt.createNumber("0x1F"));
        assertEquals(3_000_000_000L, NumericPrompt.createNumber("3000000000"));
        assertEquals(new BigInteger("99999999999999999999"), NumericPrompt.createNumber("99999999999999999999"));
        assertEquals(1.5f, NumericPrompt.createNumber("1.5"));
        assertEquals(1e300, NumericPrompt.createNumber("1e300"));      // Float 로는 무한대 → Double
        assertEquals(7L, NumericPrompt.createNumber("7L"));
    }
}
