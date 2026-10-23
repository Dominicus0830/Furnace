// YAML 서브셋 파서 — 줄 단위 블록 구조 + 문자 단위 플로우/따옴표 스캐너 (단일 패스, 정규식 없음)
package org.domi.furnace.api.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

final class YamlParser {

    private static final int MAX_DEPTH = 256;

    private final String src;
    private final int len;

    /** 다음에 읽을 줄의 시작 오프셋. */
    private int pos;
    /** 현재 줄이 로드되어 있는지. cs 는 아직 소비하지 않은 내용의 시작. */
    private boolean hasLine;
    private int ls;
    private int cs;
    private int ce;
    private boolean started;
    private boolean docEnded;

    private final ArrayList<String> pending = new ArrayList<>();
    private HashMap<String, Object> anchors;
    private String propAnchor;
    private String propTag;
    private String quoted;
    private String headerComment;
    private int fi;
    private int depth;
    private int blockDepth;
    /** alias 로 복제할 수 있는 노드 수 상한 (billion laughs 방어). */
    private int aliasBudget = 2_000_000;

    private YamlParser(String text) {
        this.src = text;
        this.len = text.length();
    }

    static ConfigSection parse(String text) {
        if (text.indexOf('\r') >= 0) text = text.replace("\r\n", "\n").replace('\r', '\n');
        if (!text.isEmpty() && text.charAt(0) == '﻿') text = text.substring(1);
        return new YamlParser(text).parseDocument();
    }

    // ================================================================ 문서

    private ConfigSection parseDocument() {
        ConfigSection root;
        if (!loadLine()) {
            root = new ConfigSection();
            root.setHeader(Comments.extractHeader(pending, true));
            return root;
        }
        List<String> header = Comments.extractHeader(pending, false);
        Object node = parseBlockNode(-1);
        if (hasLine) finishLine();
        if (loadLine()) throw err(cs, "expected a single document with a consistent indentation");
        if (node == null) {
            root = new ConfigSection();
        } else if (node instanceof ConfigSection s) {
            root = s;
        } else {
            throw err(0, "the document root must be a mapping, but found " + (node instanceof List ? "a sequence" : "a scalar"));
        }
        root.setHeader(header);
        List<String> footer = Comments.trimTrailingBlanks(new ArrayList<>(pending));
        root.setFooter(footer);
        return root;
    }

    // ================================================================ 줄 처리

    private ConfigParseException err(int index, String msg) {
        return ConfigParseException.at(src, index, msg);
    }

    private static boolean ws(char c) {
        return c == ' ' || c == '\t';
    }

    /** 다음 내용 줄을 로드한다. 빈 줄/주석 줄은 pending 에 모은다. 문서가 끝났으면 false. */
    private boolean loadLine() {
        if (hasLine) return true;
        while (pos < len && !docEnded) {
            int start = pos;
            int e = src.indexOf('\n', start);
            if (e < 0) e = len;
            int i = start;
            while (i < e && src.charAt(i) == ' ') i++;
            int j = i;
            while (j < e && ws(src.charAt(j))) j++;
            if (j == e) {
                Comments.addBlank(pending);
                pos = e + 1;
                continue;
            }
            if (src.charAt(j) == '#') {
                pending.add(Comments.text(src, j + 1, e));
                pos = e + 1;
                continue;
            }
            if (src.charAt(i) == '\t') {
                throw err(i, "tab characters are not allowed for indentation (use spaces)");
            }
            if (i == start && src.charAt(i) == '%' && !started) { // %YAML 등 디렉티브 무시
                pos = e + 1;
                continue;
            }
            if (i == start && (isMarker(start, e, "---") || isMarker(start, e, "..."))) {
                boolean dashes = src.charAt(start) == '-';
                if (!dashes) {
                    docEnded = true;
                    break;
                }
                if (started) throw err(start, "multiple YAML documents are not supported");
                started = true;
                int k = start + 3;
                while (k < e && ws(src.charAt(k))) k++;
                if (k == e || src.charAt(k) == '#') {
                    pos = e + 1;
                    continue;
                }
                ls = start;
                cs = k;
                ce = e;
                pos = e + 1;
                hasLine = true;
                return true;
            }
            started = true;
            ls = start;
            cs = i;
            ce = e;
            pos = e + 1;
            hasLine = true;
            return true;
        }
        return false;
    }

    private boolean isMarker(int start, int e, String m) {
        return e - start >= 3 && src.startsWith(m, start) && (e - start == 3 || ws(src.charAt(start + 3)));
    }

    /** 현재 줄의 남은 부분이 공백/주석뿐인지 확인하고 줄 끝 주석을 반환한다. */
    private String finishLine() {
        while (cs < ce && ws(src.charAt(cs))) cs++;
        String c = null;
        if (cs < ce) {
            if (src.charAt(cs) != '#') throw err(cs, "unexpected content after the value (expected end of line)");
            c = Comments.text(src, cs + 1, ce);
        }
        hasLine = false;
        return c;
    }

    private void skipWs() {
        while (cs < ce && ws(src.charAt(cs))) cs++;
    }

    /** 오프셋 idx 가 속한 줄을 현재 줄로 만들고 cs 를 idx 로 둔다. */
    private void positionAt(int idx) {
        ls = src.lastIndexOf('\n', idx - 1) + 1;
        int e = src.indexOf('\n', idx);
        ce = e < 0 ? len : e;
        cs = idx;
        pos = ce + 1;
        hasLine = true;
    }

    private boolean isSeqDash(int i) {
        return src.charAt(i) == '-' && (i + 1 >= ce || ws(src.charAt(i + 1)));
    }

    private List<String> takePending() {
        if (pending.isEmpty()) return null;
        ArrayList<String> out = new ArrayList<>(pending);
        pending.clear();
        return out;
    }

    // ================================================================ 블록 노드

    /** 현재 줄(cs 부터)에서 시작하는 블록 노드. parentIndent 는 부모 들여쓰기(루트는 -1). */
    private Object parseBlockNode(int parentIndent) {
        int n = cs - ls;
        if (isSeqDash(cs)) return parseSequence(n);
        if (mappingColon() >= 0) return parseMapping(n);
        char c = src.charAt(cs);
        if (c == '?' && (cs + 1 >= ce || ws(src.charAt(cs + 1)))) {
            throw err(cs, "complex mapping keys ('? ') are not supported");
        }
        Object v = parseInlineValue(parentIndent, null);
        if (hasLine) finishLine();
        return v;
    }

    private ConfigSection parseMapping(int n) {
        if (++blockDepth > MAX_DEPTH) throw err(cs, "block collections are nested too deeply");
        ConfigSection map = new ConfigSection();
        ArrayList<ConfigSection> merges = null;
        while (loadLine()) {
            int ind = cs - ls;
            if (ind < n) break;
            if (ind > n) throw err(cs, "bad indentation of a mapping entry");
            if (isSeqDash(cs)) throw err(cs, "expected a mapping key but found a sequence entry");
            int keyPos = cs;
            int colon = mappingColon();
            if (colon < 0) {
                char c = src.charAt(cs);
                if (c == '?' && (cs + 1 >= ce || ws(src.charAt(cs + 1)))) {
                    throw err(cs, "complex mapping keys ('? ') are not supported");
                }
                throw err(cs, "could not find expected ':' after the mapping key");
            }
            boolean plainKey = src.charAt(cs) != '"' && src.charAt(cs) != '\'';
            String key;
            if (plainKey) {
                key = src.substring(cs, colon).stripTrailing();
            } else {
                parseQuoted(cs);
                key = quoted;
            }
            List<String> cm = takePending();
            cs = colon + 1;
            skipWs();
            readProps();
            String anchor = propAnchor;
            String tag = propTag;
            Object value;
            String ic;
            if (cs >= ce || src.charAt(cs) == '#') {
                ic = finishLine();
                value = parseNestedBlock(n, false);
                if (value == null && "!!str".equals(tag)) value = "";
            } else {
                value = parseInlineValue(n, tag);
                ic = hasLine ? finishLine() : headerComment;
            }
            if (anchor != null) registerAnchor(anchor, value);
            if (plainKey && key.equals("<<")) {
                if (merges == null) merges = new ArrayList<>();
                collectMerge(value, merges, keyPos);
                continue;
            }
            if (map.hasKey(key)) throw err(keyPos, "duplicate key '" + key + "'");
            map.putRaw(key, value);
            if (cm != null) map.setCommentsRaw(key, cm);
            if (ic != null) map.setInlineRaw(key, ic);
        }
        if (merges != null) {
            for (ConfigSection m : merges) {
                for (var e : m.rawMap().entrySet()) {
                    if (!map.hasKey(e.getKey())) map.putRaw(e.getKey(), ConfigSection.copyValue(e.getValue()));
                }
            }
        }
        blockDepth--;
        return map;
    }

    private void collectMerge(Object value, ArrayList<ConfigSection> merges, int at) {
        if (value instanceof ConfigSection s) {
            merges.add(s);
        } else if (value instanceof List<?> l) {
            for (Object o : l) {
                if (!(o instanceof ConfigSection s)) throw err(at, "merge key '<<' expects mappings");
                merges.add(s);
            }
        } else {
            throw err(at, "merge key '<<' expects a mapping or a list of mappings");
        }
    }

    private List<Object> parseSequence(int n) {
        if (++blockDepth > MAX_DEPTH) throw err(cs, "block collections are nested too deeply");
        ArrayList<Object> list = new ArrayList<>();
        while (loadLine()) {
            int ind = cs - ls;
            if (ind < n) break;
            if (ind > n) throw err(cs, "bad indentation of a sequence entry");
            if (!isSeqDash(cs)) break;
            pending.clear();
            cs++;
            skipWs();
            list.add(parseItem(n));
        }
        blockDepth--;
        return list;
    }

    /** '- ' 뒤의 항목. n 은 대시의 열. */
    private Object parseItem(int n) {
        if (cs >= ce || src.charAt(cs) == '#') {
            finishLine();
            return parseNestedBlock(n, true);
        }
        if (isSeqDash(cs)) return parseSequence(cs - ls);
        if (mappingColon() >= 0) return parseMapping(cs - ls);
        readProps();
        String anchor = propAnchor;
        String tag = propTag;
        Object value;
        if (cs >= ce || src.charAt(cs) == '#') {
            finishLine();
            value = parseNestedBlock(n, true);
        } else {
            value = parseInlineValue(n, tag);
            if (hasLine) finishLine();
        }
        if (anchor != null) registerAnchor(anchor, value);
        return value;
    }

    /** 키/대시 뒤가 비어 있을 때: 다음 줄들에서 더 깊게 들여쓴 블록 노드를 읽는다. */
    private Object parseNestedBlock(int n, boolean seqItem) {
        if (hasLine) finishLine();
        if (!loadLine()) return null;
        int ind = cs - ls;
        if (ind > n) return parseBlockNode(n);
        if (!seqItem && ind == n && isSeqDash(cs)) return parseSequence(n);
        return null;
    }

    private void registerAnchor(String name, Object value) {
        if (anchors == null) anchors = new HashMap<>();
        anchors.put(name, ConfigSection.copyValue(value));
    }

    private void readProps() {
        propAnchor = null;
        propTag = null;
        while (cs < ce) {
            char c = src.charAt(cs);
            if (c == '&' || c == '!') {
                int st = cs + (c == '&' ? 1 : 0);
                int e = cs;
                while (e < ce && !ws(src.charAt(e))) e++;
                String name = src.substring(st, e);
                if (c == '&') {
                    if (name.isEmpty()) throw err(cs, "anchor name is empty");
                    propAnchor = name;
                } else {
                    propTag = name;
                }
                cs = e;
                skipWs();
            } else {
                break;
            }
        }
    }

    /**
     * 현재 줄이 "키: ..." 형태면 ':' 의 오프셋, 아니면 -1. 따옴표 키는 한 줄 안에서만 인식한다.
     */
    private int mappingColon() {
        int i = cs;
        char c = src.charAt(i);
        if (c == '"' || c == '\'') {
            int q = i + 1;
            while (q < ce) {
                char ch = src.charAt(q);
                if (c == '"' && ch == '\\') {
                    q += 2;
                    continue;
                }
                if (ch == c) {
                    if (c == '\'' && q + 1 < ce && src.charAt(q + 1) == '\'') {
                        q += 2;
                        continue;
                    }
                    break;
                }
                q++;
            }
            if (q >= ce) return -1;
            q++;
            while (q < ce && ws(src.charAt(q))) q++;
            if (q < ce && src.charAt(q) == ':' && (q + 1 >= ce || ws(src.charAt(q + 1)))) return q;
            return -1;
        }
        if (c == '?' && (i + 1 >= ce || ws(src.charAt(i + 1)))) return -1;
        if (c == '[' || c == '{' || c == '&' || c == '*' || c == '!' || c == '|' || c == '>' || c == '#') return -1;
        for (; i < ce; i++) {
            char ch = src.charAt(i);
            if (ch == ':' && (i + 1 >= ce || ws(src.charAt(i + 1)))) return i;
            if (ch == '#' && i > cs && ws(src.charAt(i - 1))) return -1;
        }
        return -1;
    }

    // ================================================================ 값

    /** 현재 줄 cs 에 값이 시작한다(비어있지 않음). 값 끝에 cs 를 두고 반환한다(블록 스칼라는 hasLine=false). */
    private Object parseInlineValue(int n, String tag) {
        headerComment = null;
        char c = src.charAt(cs);
        switch (c) {
            case '|':
            case '>':
                return parseBlockScalar(n, c);
            case '[':
            case '{': {
                fi = cs;
                depth = 0;
                Object v = flowNode();
                positionAt(fi);
                return v;
            }
            case '"':
            case '\'': {
                int end = parseQuoted(cs);
                positionAt(end);
                return tag(tag, quoted, true);
            }
            case '*': {
                int e = cs + 1;
                while (e < ce && !ws(src.charAt(e))) e++;
                Object v = alias(src.substring(cs + 1, e), cs);
                cs = e;
                return v;
            }
            default:
                if (c == '-' && (cs + 1 >= ce || ws(src.charAt(cs + 1)))) {
                    throw err(cs, "block sequence entries are not allowed in this context");
                }
                return parsePlain(n, tag);
        }
    }

    private Object alias(String name, int at) {
        Object v = anchors == null ? null : anchors.get(name);
        if (v == null && (anchors == null || !anchors.containsKey(name))) {
            throw err(at, "found undefined alias '" + name + "'");
        }
        aliasBudget -= countNodes(v);
        if (aliasBudget < 0) throw err(at, "too many alias expansions (possible alias bomb)");
        return ConfigSection.copyValue(v);
    }

    private static int countNodes(Object v) {
        if (v instanceof ConfigSection s) {
            int n = 1;
            for (Object o : s.rawMap().values()) n += countNodes(o);
            return n;
        }
        if (v instanceof List<?> l) {
            int n = 1;
            for (Object o : l) n += countNodes(o);
            return n;
        }
        return 1;
    }

    private static Object tag(String tag, String text, boolean quoted) {
        if (tag == null) return quoted ? text : YamlScalars.resolve(text);
        switch (tag) {
            case "!!str":
                return text;
            case "!!float": {
                Object r = YamlScalars.resolve(text);
                return r instanceof Number num ? (Object) num.doubleValue() : r;
            }
            case "!!int":
            case "!!bool":
            case "!!null":
                return YamlScalars.resolve(text);
            default:
                return quoted ? text : YamlScalars.resolve(text);
        }
    }

    /** 평문 스칼라(여러 줄 이어붙임 포함). n 은 부모 들여쓰기: 이어지는 줄은 n 보다 깊어야 한다. */
    private Object parsePlain(int n, String tag) {
        int start = cs;
        int end = plainEnd(start, ce);
        String first = src.substring(start, end).stripTrailing();
        cs = end;
        StringBuilder sb = null;
        while (end >= ce) {
            int p = pos;
            int blanks = 0;
            int contentAt = -1;
            while (p < len) {
                int e = src.indexOf('\n', p);
                if (e < 0) e = len;
                int i = p;
                while (i < e && src.charAt(i) == ' ') i++;
                int j = i;
                while (j < e && ws(src.charAt(j))) j++;
                if (j == e) {
                    blanks++;
                    p = e + 1;
                    continue;
                }
                if (src.charAt(j) != '#' && i - p > n && !(i == p && (isMarker(p, e, "---") || isMarker(p, e, "...")))) {
                    contentAt = j;
                }
                break;
            }
            if (contentAt < 0) break;
            positionAt(contentAt);
            int e2 = plainEnd(contentAt, ce);
            String seg = src.substring(contentAt, e2).stripTrailing();
            if (sb == null) sb = new StringBuilder(first.length() + seg.length() + 16).append(first);
            if (blanks == 0) sb.append(' ');
            else for (int b = 0; b < blanks; b++) sb.append('\n');
            sb.append(seg);
            cs = e2;
            end = e2;
        }
        return tag(tag, sb == null ? first : sb.toString(), false);
    }

    /** 평문 스칼라의 끝(주석 시작 또는 줄 끝). "key: value" 처럼 매핑이 중첩되면 오류. */
    private int plainEnd(int from, int to) {
        for (int i = from; i < to; i++) {
            char ch = src.charAt(i);
            if (ch == ':' && (i + 1 >= to || ws(src.charAt(i + 1)))) {
                throw err(i, "mapping values are not allowed here (quote the text if it contains ': ')");
            }
            if (ch == '#' && i > from && ws(src.charAt(i - 1))) return i;
        }
        return to;
    }

    // ================================================================ 블록 스칼라

    private String parseBlockScalar(int n, char style) {
        int st = cs + 1;
        char chomp = 'c';
        int digit = 0;
        while (st < ce && !ws(src.charAt(st))) {
            char ch = src.charAt(st);
            if (ch == '+' || ch == '-') chomp = ch;
            else if (ch >= '1' && ch <= '9') digit = ch - '0';
            else throw err(st, "invalid block scalar header");
            st++;
        }
        cs = st;
        headerComment = finishLine();

        int blockIndent = digit > 0 ? Math.max(n, 0) + digit : -1;
        ArrayList<String> lines = new ArrayList<>();
        int p = pos;
        int afterText = pos;
        int trailing = 0;
        while (p < len) {
            int e = src.indexOf('\n', p);
            if (e < 0) e = len;
            int sp = 0;
            while (p + sp < e && src.charAt(p + sp) == ' ') sp++;
            boolean blank = true;
            for (int k = p + sp; k < e; k++) {
                if (!ws(src.charAt(k))) {
                    blank = false;
                    break;
                }
            }
            if (blank) {
                lines.add("");
                trailing++;
                p = e + 1;
                continue;
            }
            if (blockIndent < 0) {
                if (sp > n) blockIndent = sp;
                else break;
            } else if (sp < blockIndent) {
                break;
            }
            lines.add(src.substring(p + blockIndent, e));
            trailing = 0;
            p = e + 1;
            afterText = p;
        }
        // 끝의 빈 줄은 다음 키의 빈 줄 정보로 남겨 둔다
        pos = Math.min(afterText, len);
        for (int k = 0; k < trailing; k++) lines.remove(lines.size() - 1);

        StringBuilder sb = new StringBuilder();
        if (style == '|') {
            for (int k = 0; k < lines.size(); k++) {
                if (k > 0) sb.append('\n');
                sb.append(lines.get(k));
            }
        } else {
            fold(lines, sb);
        }
        boolean hasText = !lines.isEmpty();
        switch (chomp) {
            case '-':
                break;
            case '+':
                if (hasText) sb.append('\n');
                for (int k = 0; k < trailing; k++) sb.append('\n');
                break;
            default:
                if (hasText) sb.append('\n');
                break;
        }
        return sb.toString();
    }

    private static void fold(List<String> lines, StringBuilder sb) {
        boolean havePrev = false;
        boolean prevMore = false;
        int blanks = 0;
        for (String line : lines) {
            if (line.isEmpty()) {
                blanks++;
                continue;
            }
            boolean more = line.charAt(0) == ' ' || line.charAt(0) == '\t';
            if (!havePrev) {
                for (int b = 0; b < blanks; b++) sb.append('\n');
            } else if (!prevMore && !more) {
                if (blanks == 0) sb.append(' ');
                else for (int b = 0; b < blanks; b++) sb.append('\n');
            } else {
                for (int b = 0; b <= blanks; b++) sb.append('\n');
            }
            sb.append(line);
            prevMore = more;
            havePrev = true;
            blanks = 0;
        }
    }

    // ================================================================ 따옴표 스칼라

    /** start 의 따옴표로 시작하는 스칼라를 읽어 quoted 에 담고 닫는 따옴표 다음 오프셋을 반환한다. */
    private int parseQuoted(int start) {
        char q = src.charAt(start);
        StringBuilder sb = new StringBuilder();
        int i = start + 1;
        int keep = 0;
        while (true) {
            if (i >= len) throw err(start, "unterminated quoted scalar");
            char c = src.charAt(i);
            if (c == q) {
                if (q == '\'' && i + 1 < len && src.charAt(i + 1) == '\'') {
                    sb.append('\'');
                    keep = sb.length();
                    i += 2;
                    continue;
                }
                i++;
                break;
            }
            if (c == '\\' && q == '"') {
                if (i + 1 >= len) throw err(start, "unterminated quoted scalar");
                char e = src.charAt(i + 1);
                if (e == '\n') {
                    i += 2;
                    while (i < len && ws(src.charAt(i))) i++;
                    continue;
                }
                i += 2;
                switch (e) {
                    case '0' -> sb.append('\0');
                    case 'a' -> sb.append('\u0007');
                    case 'b' -> sb.append('\b');
                    case 't', '\t' -> sb.append('\t');
                    case 'n' -> sb.append('\n');
                    case 'v' -> sb.append('\u000B');
                    case 'f' -> sb.append('\f');
                    case 'r' -> sb.append('\r');
                    case 'e' -> sb.append('\u001B');
                    case ' ' -> sb.append(' ');
                    case '"' -> sb.append('"');
                    case '/' -> sb.append('/');
                    case '\\' -> sb.append('\\');
                    case 'N' -> sb.append('\u0085');
                    case '_' -> sb.append(' ');
                    case 'L' -> sb.append(' ');
                    case 'P' -> sb.append(' ');
                    case 'x' -> {
                        sb.append((char) hex(i, 2, start));
                        i += 2;
                    }
                    case 'u' -> {
                        sb.append((char) hex(i, 4, start));
                        i += 4;
                    }
                    case 'U' -> {
                        int cp = hex(i, 8, start);
                        if (cp > 0x10FFFF) throw err(i - 2, "invalid unicode escape");
                        sb.appendCodePoint(cp);
                        i += 8;
                    }
                    default -> throw err(i - 2, "unknown escape character '\\" + e + "'");
                }
                keep = sb.length();
                continue;
            }
            if (c == '\n') {
                int end = sb.length();
                while (end > keep && ws(sb.charAt(end - 1))) end--;
                sb.setLength(end);
                int nl = 1;
                i++;
                while (true) {
                    while (i < len && ws(src.charAt(i))) i++;
                    if (i < len && src.charAt(i) == '\n') {
                        nl++;
                        i++;
                    } else {
                        break;
                    }
                }
                if (nl == 1) sb.append(' ');
                else for (int k = 1; k < nl; k++) sb.append('\n');
                continue;
            }
            sb.append(c);
            i++;
        }
        quoted = sb.toString();
        return i;
    }

    private int hex(int at, int count, int errPos) {
        if (at + count > len) throw err(errPos, "unterminated quoted scalar");
        int v = 0;
        for (int k = 0; k < count; k++) {
            int d = Character.digit(src.charAt(at + k), 16);
            if (d < 0) throw err(at + k, "invalid hexadecimal digit in escape sequence");
            v = (v << 4) | d;
        }
        return v;
    }

    // ================================================================ 플로우 컬렉션

    private void skipFlowWs() {
        while (fi < len) {
            char c = src.charAt(fi);
            if (c == ' ' || c == '\t' || c == '\n') {
                fi++;
            } else if (c == '#' && (fi == 0 || ws(src.charAt(fi - 1)) || src.charAt(fi - 1) == '\n')) {
                int e = src.indexOf('\n', fi);
                fi = e < 0 ? len : e;
            } else {
                break;
            }
        }
    }

    private Object flowNode() {
        skipFlowWs();
        if (fi >= len) throw err(len, "unexpected end of input inside a flow collection");
        char c = src.charAt(fi);
        switch (c) {
            case '[':
                return flowSeq();
            case '{':
                return flowMap();
            case '"':
            case '\'':
                fi = parseQuoted(fi);
                return quoted;
            case '*': {
                int st = fi + 1;
                int e = st;
                while (e < len && !ws(src.charAt(e)) && src.charAt(e) != '\n' && src.charAt(e) != ',' && src.charAt(e) != ']' && src.charAt(e) != '}') e++;
                Object v = alias(src.substring(st, e), fi);
                fi = e;
                return v;
            }
            case '&':
            case '!': {
                String anchor = null;
                while (fi < len && (src.charAt(fi) == '&' || src.charAt(fi) == '!')) {
                    boolean isAnchor = src.charAt(fi) == '&';
                    int st = fi + (isAnchor ? 1 : 0);
                    int e = fi;
                    while (e < len && !ws(src.charAt(e)) && src.charAt(e) != '\n' && src.charAt(e) != ',' && src.charAt(e) != ']' && src.charAt(e) != '}') e++;
                    if (isAnchor) anchor = src.substring(st, e);
                    fi = e;
                    while (fi < len && ws(src.charAt(fi))) fi++;
                }
                Object v = flowNode();
                if (anchor != null) registerAnchor(anchor, v);
                return v;
            }
            default:
                return YamlScalars.resolve(flowPlain());
        }
    }

    private String flowPlain() {
        int start = fi;
        StringBuilder sb = null;
        int segStart = fi;
        while (fi < len) {
            char c = src.charAt(fi);
            if (c == ',' || c == ']' || c == '}') break;
            if (c == ':') {
                char nx = fi + 1 < len ? src.charAt(fi + 1) : '\n';
                if (nx == ' ' || nx == '\t' || nx == '\n' || nx == ',' || nx == ']' || nx == '}') break;
            }
            if (c == '#' && fi > start && ws(src.charAt(fi - 1))) break;
            if (c == '\n') {
                String seg = src.substring(segStart, fi).stripTrailing();
                int q = fi;
                int nls = 0;
                while (q < len) {
                    char d = src.charAt(q);
                    if (d == '\n') nls++;
                    else if (!ws(d)) break;
                    q++;
                }
                if (q >= len || src.charAt(q) == ',' || src.charAt(q) == ']' || src.charAt(q) == '}' || src.charAt(q) == '#') {
                    if (sb == null) sb = new StringBuilder();
                    sb.append(seg);
                    segStart = fi;
                    break;
                }
                if (sb == null) sb = new StringBuilder();
                sb.append(seg);
                if (nls == 1) sb.append(' ');
                else for (int k = 1; k < nls; k++) sb.append('\n');
                fi = q;
                segStart = q;
                continue;
            }
            fi++;
        }
        String tail = src.substring(segStart, fi).stripTrailing();
        String text = sb == null ? tail : sb.append(tail).toString();
        if (text.isEmpty()) {
            throw err(fi, fi < len ? "unexpected character '" + src.charAt(fi) + "' in flow collection" : "unexpected end of input");
        }
        return text;
    }

    private List<Object> flowSeq() {
        if (++depth > MAX_DEPTH) throw err(fi, "flow collections are nested too deeply");
        fi++;
        ArrayList<Object> list = new ArrayList<>();
        while (true) {
            skipFlowWs();
            if (fi >= len) throw err(len, "unterminated flow sequence ('[' is never closed)");
            char c = src.charAt(fi);
            if (c == ']') {
                fi++;
                break;
            }
            if (c == ',') throw err(fi, "unexpected ',' in flow sequence");
            int itemAt = fi;
            Object v = flowNode();
            skipFlowWs();
            if (fi < len && src.charAt(fi) == ':') { // [k: v] 단일 쌍 매핑
                fi++;
                skipFlowWs();
                Object val = null;
                if (fi < len && src.charAt(fi) != ',' && src.charAt(fi) != ']') val = flowNode();
                ConfigSection one = new ConfigSection();
                one.putRaw(String.valueOf(v), val);
                v = one;
                skipFlowWs();
            }
            list.add(v);
            if (fi >= len) throw err(len, "unterminated flow sequence ('[' is never closed)");
            c = src.charAt(fi);
            if (c == ',') {
                fi++;
            } else if (c == ']') {
                fi++;
                break;
            } else {
                throw err(fi, "expected ',' or ']' in flow sequence (item started at column " + (itemAt - src.lastIndexOf('\n', itemAt)) + ")");
            }
        }
        depth--;
        return list;
    }

    private ConfigSection flowMap() {
        if (++depth > MAX_DEPTH) throw err(fi, "flow collections are nested too deeply");
        fi++;
        ConfigSection map = new ConfigSection();
        while (true) {
            skipFlowWs();
            if (fi >= len) throw err(len, "unterminated flow mapping ('{' is never closed)");
            char c = src.charAt(fi);
            if (c == '}') {
                fi++;
                break;
            }
            if (c == ',') throw err(fi, "unexpected ',' in flow mapping");
            int keyAt = fi;
            String key;
            if (c == '"' || c == '\'') {
                fi = parseQuoted(fi);
                key = quoted;
            } else {
                key = flowPlain();
            }
            skipFlowWs();
            Object value = null;
            if (fi < len && src.charAt(fi) == ':') {
                fi++;
                skipFlowWs();
                if (fi < len && src.charAt(fi) != ',' && src.charAt(fi) != '}') value = flowNode();
                skipFlowWs();
            }
            if (map.hasKey(key)) throw err(keyAt, "duplicate key '" + key + "'");
            map.putRaw(key, value);
            if (fi >= len) throw err(len, "unterminated flow mapping ('{' is never closed)");
            c = src.charAt(fi);
            if (c == ',') {
                fi++;
            } else if (c == '}') {
                fi++;
                break;
            } else {
                throw err(fi, "expected ',' or '}' in flow mapping");
            }
        }
        depth--;
        return map;
    }
}
