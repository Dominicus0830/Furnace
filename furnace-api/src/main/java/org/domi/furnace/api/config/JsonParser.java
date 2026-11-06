// 엄격한 RFC 8259 JSON 파서 — 루트는 객체여야 하며 중복 키/후행 쉼표/주석은 오류
package org.domi.furnace.api.config;

import java.util.ArrayList;
import java.util.List;

final class JsonParser {

    private static final int MAX_DEPTH = 512;

    private final String src;
    private final int len;
    private int i;
    private int depth;

    private JsonParser(String src) {
        this.src = src;
        this.len = src.length();
    }

    static ConfigSection parse(String text) {
        JsonParser p = new JsonParser(text);
        if (p.len > 0 && text.charAt(0) == '﻿') p.i = 1;
        p.skipWs();
        if (p.i >= p.len) throw p.err(p.i, "unexpected end of input (empty document)");
        if (text.charAt(p.i) != '{') throw p.err(p.i, "the JSON root must be an object");
        ConfigSection root = p.object();
        p.skipWs();
        if (p.i < p.len) throw p.err(p.i, "unexpected content after the JSON value");
        return root;
    }

    private ConfigParseException err(int at, String msg) {
        return ConfigParseException.at(src, at, msg);
    }

    private void skipWs() {
        while (i < len) {
            char c = src.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i++;
            else break;
        }
    }

    private Object value() {
        skipWs();
        if (i >= len) throw err(len, "unexpected end of input");
        char c = src.charAt(i);
        switch (c) {
            case '{':
                return object();
            case '[':
                return array();
            case '"':
                return string();
            case 't':
                literal("true");
                return Boolean.TRUE;
            case 'f':
                literal("false");
                return Boolean.FALSE;
            case 'n':
                literal("null");
                return null;
            default:
                if (c == '-' || (c >= '0' && c <= '9')) return number();
                throw err(i, "unexpected character '" + c + "'");
        }
    }

    private void literal(String word) {
        if (!src.startsWith(word, i)) throw err(i, "invalid literal (expected '" + word + "')");
        i += word.length();
    }

    private ConfigSection object() {
        if (++depth > MAX_DEPTH) throw err(i, "nesting is too deep");
        i++; // {
        ConfigSection map = new ConfigSection();
        skipWs();
        if (i < len && src.charAt(i) == '}') {
            i++;
            depth--;
            return map;
        }
        while (true) {
            skipWs();
            if (i >= len) throw err(len, "unterminated object");
            if (src.charAt(i) != '"') throw err(i, "expected a string key");
            int keyAt = i;
            String key = string();
            skipWs();
            if (i >= len || src.charAt(i) != ':') throw err(Math.min(i, len), "expected ':' after the key");
            i++;
            Object v = value();
            if (map.hasKey(key)) throw err(keyAt, "duplicate key '" + key + "'");
            map.putRaw(key, v);
            skipWs();
            if (i >= len) throw err(len, "unterminated object");
            char c = src.charAt(i);
            if (c == ',') {
                i++;
            } else if (c == '}') {
                i++;
                break;
            } else {
                throw err(i, "expected ',' or '}'");
            }
        }
        depth--;
        return map;
    }

    private List<Object> array() {
        if (++depth > MAX_DEPTH) throw err(i, "nesting is too deep");
        i++; // [
        ArrayList<Object> list = new ArrayList<>();
        skipWs();
        if (i < len && src.charAt(i) == ']') {
            i++;
            depth--;
            return list;
        }
        while (true) {
            list.add(value());
            skipWs();
            if (i >= len) throw err(len, "unterminated array");
            char c = src.charAt(i);
            if (c == ',') {
                i++;
            } else if (c == ']') {
                i++;
                break;
            } else {
                throw err(i, "expected ',' or ']'");
            }
        }
        depth--;
        return list;
    }

    private String string() {
        int start = i;
        i++; // "
        int plainStart = i;
        StringBuilder sb = null;
        while (true) {
            if (i >= len) throw err(start, "unterminated string");
            char c = src.charAt(i);
            if (c == '"') {
                String s = sb == null ? src.substring(plainStart, i) : sb.append(src, plainStart, i).toString();
                i++;
                return s;
            }
            if (c < 0x20) throw err(i, "control character in string must be escaped");
            if (c != '\\') {
                i++;
                continue;
            }
            if (sb == null) sb = new StringBuilder();
            sb.append(src, plainStart, i);
            if (i + 1 >= len) throw err(start, "unterminated string");
            char e = src.charAt(i + 1);
            switch (e) {
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case '/' -> sb.append('/');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    if (i + 6 > len) throw err(i, "incomplete unicode escape");
                    int v = 0;
                    for (int k = 2; k < 6; k++) {
                        int d = Character.digit(src.charAt(i + k), 16);
                        if (d < 0) throw err(i + k, "invalid hexadecimal digit in unicode escape");
                        v = (v << 4) | d;
                    }
                    sb.append((char) v); // 서로게이트 쌍은 \\uD83D\\uDE00 두 번에 걸쳐 그대로 이어붙는다
                    i += 4;
                }
                default -> throw err(i, "invalid escape character '\\" + e + "'");
            }
            i += 2;
            plainStart = i;
        }
    }

    private Object number() {
        int start = i;
        if (src.charAt(i) == '-') i++;
        if (i >= len) throw err(start, "invalid number");
        char c = src.charAt(i);
        if (c == '0') {
            i++;
            if (i < len && src.charAt(i) >= '0' && src.charAt(i) <= '9') throw err(i, "leading zeros are not allowed in numbers");
        } else if (c >= '1' && c <= '9') {
            while (i < len && src.charAt(i) >= '0' && src.charAt(i) <= '9') i++;
        } else {
            throw err(i, "invalid number");
        }
        boolean isInt = true;
        if (i < len && src.charAt(i) == '.') {
            isInt = false;
            i++;
            int d = i;
            while (i < len && src.charAt(i) >= '0' && src.charAt(i) <= '9') i++;
            if (i == d) throw err(i, "digits are required after the decimal point");
        }
        if (i < len && (src.charAt(i) == 'e' || src.charAt(i) == 'E')) {
            isInt = false;
            i++;
            if (i < len && (src.charAt(i) == '+' || src.charAt(i) == '-')) i++;
            int d = i;
            while (i < len && src.charAt(i) >= '0' && src.charAt(i) <= '9') i++;
            if (i == d) throw err(i, "digits are required in the exponent");
        }
        String text = src.substring(start, i);
        if (isInt) {
            try {
                return ConfigSection.intOrLong(Long.parseLong(text));
            } catch (NumberFormatException e) {
                // long 범위를 넘는 정수는 Double 로 근사
            }
        }
        return Double.parseDouble(text);
    }
}
