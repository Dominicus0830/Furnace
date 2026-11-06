// YAML 평문 스칼라의 타입 해석(null/bool/int/float)과 작성 시 따옴표 필요 여부 판단
package org.domi.furnace.api.config;

final class YamlScalars {

    private YamlScalars() {}

    /**
     * 따옴표 없는 스칼라를 해석한다. null / Boolean / Integer·Long / Double / String.
     * SnakeYAML(YAML 1.1) 규칙을 따르되 60진법(12:30)과 timestamp 는 해석하지 않고 문자열로 둔다.
     */
    static Object resolve(String s) {
        int n = s.length();
        if (n == 0) return null;
        char c = s.charAt(0);
        if (c >= '0' && c <= '9' || c == '-' || c == '+' || c == '.') return resolveNumber(s);
        if (c == '~') return n == 1 ? null : s;
        switch (n) {
            case 2:
                if (s.equalsIgnoreCase("no")) return Boolean.FALSE;
                if (s.equalsIgnoreCase("on")) return Boolean.TRUE;
                break;
            case 3:
                if (s.equalsIgnoreCase("yes")) return Boolean.TRUE;
                if (s.equalsIgnoreCase("off")) return Boolean.FALSE;
                break;
            case 4:
                if (s.equalsIgnoreCase("true")) return Boolean.TRUE;
                if (s.equalsIgnoreCase("null")) return null;
                break;
            case 5:
                if (s.equalsIgnoreCase("false")) return Boolean.FALSE;
                break;
            default:
                break;
        }
        return s;
    }

    private static Object resolveNumber(String s) {
        int n = s.length();
        int i = 0;
        boolean neg = false;
        char c = s.charAt(0);
        if (c == '-' || c == '+') {
            neg = c == '-';
            i = 1;
            if (n == 1) return s;
        }
        char d = s.charAt(i);
        if (d == '.') {
            int rest = n - i - 1;
            if (rest == 3) {
                if (s.regionMatches(true, i + 1, "inf", 0, 3)) {
                    return neg ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
                }
                if (i == 0 && s.regionMatches(true, 1, "nan", 0, 3)) return Double.NaN;
            }
        } else if (d == '0' && i + 1 < n) {
            char x = s.charAt(i + 1);
            if (x == 'x' || x == 'X') return radix(s, i + 2, 16, neg);
            if (x == 'b' || x == 'B') return radix(s, i + 2, 2, neg);
            if (x == 'o' || x == 'O') return radix(s, i + 2, 8, neg);
        }
        // [정수부][.소수부][지수] 문법 검사
        int p = i;
        int intDigits = 0;
        while (p < n) {
            char ch = s.charAt(p);
            if (ch >= '0' && ch <= '9') intDigits++;
            else if (!(ch == '_' && p > i)) break;
            p++;
        }
        boolean dot = false;
        boolean exp = false;
        int fracDigits = 0;
        if (p < n && s.charAt(p) == '.') {
            dot = true;
            p++;
            while (p < n) {
                char ch = s.charAt(p);
                if (ch >= '0' && ch <= '9') fracDigits++;
                else if (ch != '_') break;
                p++;
            }
        }
        if (intDigits == 0 && fracDigits == 0) return s;
        if (p < n && (s.charAt(p) == 'e' || s.charAt(p) == 'E')) {
            exp = true;
            p++;
            if (p < n && (s.charAt(p) == '+' || s.charAt(p) == '-')) p++;
            int ed = 0;
            while (p < n && s.charAt(p) >= '0' && s.charAt(p) <= '9') {
                p++;
                ed++;
            }
            if (ed == 0) return s;
        }
        if (p != n) return s;
        String clean = s.indexOf('_') >= 0 ? s.replace("_", "") : s;
        if (!dot && !exp) {
            String digits = neg || clean.charAt(0) == '+' ? clean.substring(1) : clean;
            if (digits.length() > 1 && digits.charAt(0) == '0') {
                for (int k = 0; k < digits.length(); k++) if (digits.charAt(k) > '7') return s;
                return radix(digits, 0, 8, neg);
            }
            try {
                return ConfigSection.intOrLong(Long.parseLong(clean.charAt(0) == '+' ? clean.substring(1) : clean));
            } catch (NumberFormatException e) {
                return s; // long 범위를 넘는 정수는 문자열로 보존
            }
        }
        try {
            return Double.parseDouble(clean);
        } catch (NumberFormatException e) {
            return s;
        }
    }

    private static Object radix(String s, int from, int radix, boolean neg) {
        String digits = s.substring(from);
        if (digits.indexOf('_') >= 0) digits = digits.replace("_", "");
        if (digits.isEmpty()) return s;
        try {
            long v = Long.parseLong(digits, radix);
            return ConfigSection.intOrLong(neg ? -v : v);
        } catch (NumberFormatException e) {
            return s;
        }
    }

    /** 문자열을 평문으로 쓰면 다시 읽을 때 다른 값/구조가 되는지(= 따옴표 필요) 판단한다. */
    static boolean needsQuoting(String s) {
        int n = s.length();
        if (n == 0) return true;
        char f = s.charAt(0);
        char l = s.charAt(n - 1);
        if (f == ' ' || f == '\t' || l == ' ' || l == '\t' || l == ':') return true;
        switch (f) {
            case '-', '?', ':':
                if (n == 1 || s.charAt(1) == ' ' || s.charAt(1) == '\t') return true;
                break;
            case ',', '[', ']', '{', '}', '#', '&', '*', '!', '|', '>', '\'', '"', '%', '@', '`', '=':
                return true;
            default:
                break;
        }
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c == 0x7f || c == 0x85 || c == 0x2028 || c == 0x2029 || c == 0xFEFF) return true;
            if (c == ':' && i + 1 < n && s.charAt(i + 1) == ' ') return true;
            if (c == '#' && i > 0 && (s.charAt(i - 1) == ' ')) return true;
        }
        if (s.startsWith("---") || s.startsWith("...") || s.equals("<<")) return true;
        return !(resolve(s) instanceof String);
    }

    /** 홑따옴표로 표현할 수 없어 겹따옴표(이스케이프)가 필요한 문자가 있는지. */
    static boolean needsDoubleQuote(String s) {
        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c == 0x7f || c == 0x85 || c == 0x2028 || c == 0x2029 || c == 0xFEFF) return true;
            if (Character.isSurrogate(c)) {
                if (Character.isHighSurrogate(c) && i + 1 < n && Character.isLowSurrogate(s.charAt(i + 1))) {
                    i++;
                } else {
                    return true;
                }
            }
        }
        return false;
    }
}
