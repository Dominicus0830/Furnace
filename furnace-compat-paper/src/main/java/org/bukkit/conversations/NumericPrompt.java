// 숫자 대답 단계. 숫자 판별·변환은 Bukkit 이 쓰는 commons-lang 2 NumberUtils(isNumber·createNumber) 규칙을 따른다:
// 10진·16진(0x)·지수·형 접미사(l L f F d D), 정수는 Integer → Long → BigInteger(앞자리 0 은 8진수, Integer.decode),
// 소수는 Float → Double → BigDecimal(그 형으로 무한대가 되거나 0 이 아닌 값이 0 이 되면 다음 형). 변환에 실패하면 정수 0 으로 넘긴다.
package org.bukkit.conversations;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.regex.Pattern;

public abstract class NumericPrompt extends ValidatingPrompt {

    private static final Pattern NUMBER =
            Pattern.compile("-?(?:0[xX][0-9a-fA-F]+|(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?[dDfF]?|\\d+[lL])");

    public NumericPrompt() {}

    @Override
    protected boolean isInputValid(ConversationContext context, String input) {
        if (!isNumber(input)) return false;
        Number n;
        try {
            n = createNumber(input);
        } catch (NumberFormatException e) {
            return false;   // commons-lang 도 isNumber 뒤 createNumber 가 던지면 이 검사 밖으로 예외가 샌다 — 여기서는 틀린 대답으로 본다
        }
        return isNumberValid(context, n);
    }

    /** 숫자 범위 검사(기본: 모두 맞음). */
    protected boolean isNumberValid(ConversationContext context, Number input) { return true; }

    @Override
    protected Prompt acceptValidatedInput(ConversationContext context, String input) {
        try {
            return acceptValidatedInput(context, createNumber(input));
        } catch (NumberFormatException e) {
            return acceptValidatedInput(context, Integer.valueOf(0));
        }
    }

    protected abstract Prompt acceptValidatedInput(ConversationContext context, Number input);

    @Override
    protected String getFailedValidationText(ConversationContext context, String invalidInput) {
        if (isNumber(invalidInput)) {
            try {
                return getFailedValidationText(context, createNumber(invalidInput));
            } catch (NumberFormatException e) {
                return getInputNotNumericText(context, invalidInput);
            }
        }
        return getInputNotNumericText(context, invalidInput);
    }

    /** 숫자가 아닌 대답에 보일 문구(기본 없음). */
    protected String getInputNotNumericText(ConversationContext context, String invalidInput) { return null; }

    /** 범위 밖 숫자에 보일 문구(기본 없음). */
    protected String getFailedValidationText(ConversationContext context, Number invalidInput) { return null; }

    static boolean isNumber(String s) { return s != null && NUMBER.matcher(s).matches(); }

    static Number createNumber(String s) {
        String body = s.startsWith("-") ? s.substring(1) : s;
        if (body.startsWith("0x") || body.startsWith("0X")) return Integer.decode(s);
        char last = s.charAt(s.length() - 1);
        boolean qualified = !Character.isDigit(last) && last != '.';
        String num = qualified ? s.substring(0, s.length() - 1) : s;
        boolean decimal = num.indexOf('.') >= 0 || num.indexOf('e') >= 0 || num.indexOf('E') >= 0;
        if (qualified && (last == 'l' || last == 'L')) {
            try {
                return Long.decode(num);
            } catch (NumberFormatException e) {
                return new BigInteger(num);
            }
        }
        if (!qualified && !decimal) {
            try {
                return Integer.decode(num);
            } catch (NumberFormatException e) {
                try {
                    return Long.decode(num);
                } catch (NumberFormatException e2) {
                    return new BigInteger(num);
                }
            }
        }
        boolean allZeros = num.chars().noneMatch(c -> c >= '1' && c <= '9');
        {   // 접미사 없음·f·d 모두 Float 부터 (commons-lang 2.6 과 같다)
            float f = Float.parseFloat(num);
            if (!(Float.isInfinite(f) || (f == 0.0f && !allZeros))) return f;
        }
        double d = Double.parseDouble(num);
        if (!(Double.isInfinite(d) || (d == 0.0d && !allZeros))) return d;
        return new BigDecimal(num);
    }
}
