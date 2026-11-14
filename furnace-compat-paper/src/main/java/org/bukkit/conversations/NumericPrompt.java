// 숫자 대답 단계. 숫자 판별·변환은 Paper 1.21.1 이 쓰는 commons-lang3 3.14.0 NumberUtils(isNumber=isCreatable·createNumber) 규칙 그대로다:
// 부호(+·-), 16진(0x·0X·#), 앞자리 0 의 8진, 지수, 형 접미사(l L f F d D). 접미사 없는 소수는 Float 와 Double 의 문자열이 같을 때만 Float,
// 아니면 BigDecimal 과 값이 같으면 Double, 아니면 BigDecimal. 두 함수는 commons-lang3 3.14.0(Apache License 2.0,
// Copyright The Apache Software Foundation)의 NumberUtils·StringUtils 에서 옮겨 왔다 — 서버에 commons-lang3 를 싣지 않으므로.
package org.bukkit.conversations;

import java.math.BigDecimal;
import java.math.BigInteger;

public abstract class NumericPrompt extends ValidatingPrompt {

    public NumericPrompt() {}

    /** Paper 와 같다 — isNumber 를 통과했는데 createNumber 가 던지면 예외가 그대로 나간다(대화가 그 예외를 로그에 남긴다). */
    @Override
    protected boolean isInputValid(ConversationContext context, String input) {
        return isNumber(input) && isNumberValid(context, createNumber(input));
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
        if (isNumber(invalidInput)) return getFailedValidationText(context, createNumber(invalidInput));
        return getInputNotNumericText(context, invalidInput);
    }

    /** 숫자가 아닌 대답에 보일 문구(기본 없음). */
    protected String getInputNotNumericText(ConversationContext context, String invalidInput) { return null; }

    /** 범위 밖 숫자에 보일 문구(기본 없음). */
    protected String getFailedValidationText(ConversationContext context, Number invalidInput) { return null; }

    // ---- commons-lang3 3.14.0 NumberUtils 에서 옮김(Apache License 2.0)

    /** NumberUtils.isCreatable. */
    static boolean isNumber(final String str) {
        if (str == null || str.isEmpty()) return false;
        final char[] chars = str.toCharArray();
        int sz = chars.length;
        boolean hasExp = false;
        boolean hasDecPoint = false;
        boolean allowSigns = false;
        boolean foundDigit = false;
        final int start = chars[0] == '-' || chars[0] == '+' ? 1 : 0;
        if (sz > start + 1 && chars[start] == '0' && str.indexOf('.') < 0) {   // 앞자리 0 — 소수가 아니면
            if (chars[start + 1] == 'x' || chars[start + 1] == 'X') {           // 16진
                int i = start + 2;
                if (i == sz) return false;
                for (; i < chars.length; i++) {
                    if ((chars[i] < '0' || chars[i] > '9') && (chars[i] < 'a' || chars[i] > 'f') && (chars[i] < 'A' || chars[i] > 'F')) return false;
                }
                return true;
            }
            if (Character.isDigit(chars[start + 1])) {                           // 8진
                for (int i = start + 1; i < chars.length; i++) {
                    if (chars[i] < '0' || chars[i] > '7') return false;
                }
                return true;
            }
        }
        sz--;   // 마지막 글자(형 접미사일 수 있음)는 따로 본다
        int i = start;
        while (i < sz || i < sz + 1 && allowSigns && !foundDigit) {
            if (chars[i] >= '0' && chars[i] <= '9') {
                foundDigit = true;
                allowSigns = false;
            } else if (chars[i] == '.') {
                if (hasDecPoint || hasExp) return false;
                hasDecPoint = true;
            } else if (chars[i] == 'e' || chars[i] == 'E') {
                if (hasExp || !foundDigit) return false;
                hasExp = true;
                allowSigns = true;
            } else if (chars[i] == '+' || chars[i] == '-') {
                if (!allowSigns) return false;
                allowSigns = false;
                foundDigit = false;   // 지수 부호 뒤에는 숫자가 있어야 한다
            } else {
                return false;
            }
            i++;
        }
        if (i < chars.length) {
            if (chars[i] >= '0' && chars[i] <= '9') return true;
            if (chars[i] == 'e' || chars[i] == 'E') return false;
            if (chars[i] == '.') {
                if (hasDecPoint || hasExp) return false;
                return foundDigit;
            }
            if (!allowSigns && (chars[i] == 'd' || chars[i] == 'D' || chars[i] == 'f' || chars[i] == 'F')) return foundDigit;
            if (chars[i] == 'l' || chars[i] == 'L') return foundDigit && !hasExp && !hasDecPoint;
            return false;
        }
        return !allowSigns && foundDigit;
    }

    /** NumberUtils.createNumber. */
    static Number createNumber(final String str) {
        if (str == null) return null;
        if (isBlank(str)) throw new NumberFormatException("A blank string is not a valid number");
        final String[] hexPrefixes = {"0x", "0X", "#"};
        final int length = str.length();
        final int offset = str.charAt(0) == '+' || str.charAt(0) == '-' ? 1 : 0;
        int pfxLen = 0;
        for (final String pfx : hexPrefixes) {
            if (str.startsWith(pfx, offset)) {
                pfxLen += pfx.length() + offset;
                break;
            }
        }
        if (pfxLen > 0) {   // 16진 — 앞의 0 을 떼고 자릿수로 형을 고른다
            char firstSigDigit = 0;
            for (int i = pfxLen; i < length; i++) {
                firstSigDigit = str.charAt(i);
                if (firstSigDigit != '0') break;
                pfxLen++;
            }
            final int hexDigits = length - pfxLen;
            if (hexDigits > 16 || hexDigits == 16 && firstSigDigit > '7') return createBigInteger(str);
            if (hexDigits > 8 || hexDigits == 8 && firstSigDigit > '7') return Long.decode(str);
            return Integer.decode(str);
        }
        final char lastChar = str.charAt(length - 1);
        final String mant;
        final String dec;
        final String exp;
        final int decPos = str.indexOf('.');
        final int expPos = str.indexOf('e') + str.indexOf('E') + 1;   // 둘 다 있지는 않다고 본다(원문과 같다)
        final boolean requestType = !Character.isDigit(lastChar) && lastChar != '.';
        if (decPos > -1) {
            if (expPos > -1) {
                if (expPos < decPos || expPos > length) throw new NumberFormatException(str + " is not a valid number.");
                dec = str.substring(decPos + 1, expPos);
            } else {
                dec = str.substring(decPos + 1, requestType ? length - 1 : length);
            }
            mant = getMantissa(str, decPos);
        } else {
            if (expPos > -1) {
                if (expPos > length) throw new NumberFormatException(str + " is not a valid number.");
                mant = getMantissa(str, expPos);
            } else {
                mant = getMantissa(str, requestType ? length - 1 : length);
            }
            dec = null;
        }
        if (requestType) {
            exp = expPos > -1 && expPos < length - 1 ? str.substring(expPos + 1, length - 1) : null;
            final String numeric = str.substring(0, length - 1);
            switch (lastChar) {
                case 'l':
                case 'L':
                    if (dec == null && exp == null
                            && (!numeric.isEmpty() && numeric.charAt(0) == '-' && isDigits(numeric.substring(1)) || isDigits(numeric))) {
                        try {
                            return Long.decode(numeric);
                        } catch (final NumberFormatException ignored) {
                            // long 보다 크다
                        }
                        return createBigInteger(numeric);
                    }
                    throw new NumberFormatException(str + " is not a valid number.");
                case 'f':
                case 'F':
                    try {
                        final Float f = Float.valueOf(str);
                        if (!(f.isInfinite() || f.floatValue() == 0.0F && !isZero(mant, dec))) return f;
                    } catch (final NumberFormatException ignored) {
                        // 아래로
                    }
                    // fall through
                case 'd':
                case 'D':
                    try {
                        final Double d = Double.valueOf(str);
                        if (!(d.isInfinite() || d.doubleValue() == 0.0D && !isZero(mant, dec))) return d;
                    } catch (final NumberFormatException ignored) {
                        // 아래로
                    }
                    try {
                        return createBigDecimal(numeric);
                    } catch (final NumberFormatException ignored) {
                        // 아래로
                    }
                    // fall through
                default:
                    throw new NumberFormatException(str + " is not a valid number.");
            }
        }
        exp = expPos > -1 && expPos < length - 1 ? str.substring(expPos + 1) : null;
        if (dec == null && exp == null) {   // 정수: Integer → Long → BigInteger
            try {
                return Integer.decode(str);
            } catch (final NumberFormatException ignored) {
                // 아래로
            }
            try {
                return Long.decode(str);
            } catch (final NumberFormatException ignored) {
                // 아래로
            }
            return createBigInteger(str);
        }
        try {   // 소수: Float → Double → BigDecimal
            final Float f = Float.valueOf(str);
            final Double d = Double.valueOf(str);
            if (!f.isInfinite() && !(f.floatValue() == 0.0F && !isZero(mant, dec)) && f.toString().equals(d.toString())) return f;
            if (!d.isInfinite() && !(d.doubleValue() == 0.0D && !isZero(mant, dec))) {
                final BigDecimal b = createBigDecimal(str);
                if (b.compareTo(BigDecimal.valueOf(d.doubleValue())) == 0) return d;
                return b;
            }
        } catch (final NumberFormatException ignored) {
            // 아래로
        }
        return createBigDecimal(str);
    }

    private static BigDecimal createBigDecimal(final String str) {
        if (isBlank(str)) throw new NumberFormatException("A blank string is not a valid number");
        return new BigDecimal(str);
    }

    private static BigInteger createBigInteger(final String str) {
        if (str.isEmpty()) throw new NumberFormatException("An empty string is not a valid number");
        int pos = 0;
        int radix = 10;
        boolean negate = false;
        final char char0 = str.charAt(0);
        if (char0 == '-') {
            negate = true;
            pos = 1;
        } else if (char0 == '+') {
            pos = 1;
        }
        if (str.startsWith("0x", pos) || str.startsWith("0X", pos)) {
            radix = 16;
            pos += 2;
        } else if (str.startsWith("#", pos)) {
            radix = 16;
            pos++;
        } else if (str.startsWith("0", pos) && str.length() > pos + 1) {
            radix = 8;
            pos++;
        }
        final BigInteger value = new BigInteger(str.substring(pos), radix);
        return negate ? value.negate() : value;
    }

    private static String getMantissa(final String str, final int stopPos) {
        final char firstChar = str.charAt(0);
        final boolean hasSign = firstChar == '-' || firstChar == '+';
        return hasSign ? str.substring(1, stopPos) : str.substring(0, stopPos);
    }

    private static boolean isAllZeros(final String str) {
        if (str == null) return true;
        for (int i = str.length() - 1; i >= 0; i--) {
            if (str.charAt(i) != '0') return false;
        }
        return true;
    }

    private static boolean isZero(final String mant, final String dec) { return isAllZeros(mant) && isAllZeros(dec); }

    /** StringUtils.isNumeric — 비지 않고 모두 유니코드 숫자. */
    private static boolean isDigits(final String str) {
        if (str == null || str.isEmpty()) return false;
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isDigit(str.charAt(i))) return false;
        }
        return true;
    }

    /** StringUtils.isBlank. */
    private static boolean isBlank(final String str) {
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isWhitespace(str.charAt(i))) return false;
        }
        return true;
    }
}
