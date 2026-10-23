// 블록 정의 표(바닐라 1.21.1 reports/blocks.json → blocks/definitions_1.21.1.txt): 블록 번호, 정의 종류(바닐라 블록 클래스 종류),
// 상태 id 범위, 기본 상태, 속성 배치. 상태 id 는 바닐라처럼 속성 이름순 혼합 기수(마지막 속성이 가장 빨리 바뀜)라서
// 속성 읽기·바꾸기가 나눗셈·덧셈 한 번이다.
package org.domi.furnace.server.block;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Blocks {

    private static volatile Blocks INSTANCE;

    final int blockCount;
    final int stateCount;
    final String[] names;
    final String[] types;
    final int[] first;
    final int[] stateCountOf;
    final int[] defaultState;
    final String[] paramsJson;
    final short[] blockOfState;
    final Map<String, Integer> byName = new HashMap<>();
    final Map<String, Prop> props = new HashMap<>();
    /** 블록별 속성 목록(이름순). */
    final Prop[][] propsOf;

    public static Blocks get() {
        Blocks b = INSTANCE;
        if (b == null) {
            synchronized (Blocks.class) {
                if (INSTANCE == null) INSTANCE = load();
                b = INSTANCE;
            }
        }
        return b;
    }

    private record Line(String name, String type, int first, int def, String props, String params) {}

    private static Blocks load() {
        List<Line> lines = new ArrayList<>();
        try (InputStream in = Blocks.class.getClassLoader().getResourceAsStream("blocks/definitions_1.21.1.txt")) {
            if (in == null) throw new IllegalStateException("blocks/definitions_1.21.1.txt 없음");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] p = line.split(" ", 6);
                lines.add(new Line(p[0], p[1], Integer.parseInt(p[2]), Integer.parseInt(p[3]), p[4], p[5]));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        lines.sort((a, b) -> Integer.compare(a.first, b.first));
        return new Blocks(lines);
    }

    private Blocks(List<Line> lines) {
        blockCount = lines.size();
        names = new String[blockCount];
        types = new String[blockCount];
        first = new int[blockCount];
        stateCountOf = new int[blockCount];
        defaultState = new int[blockCount];
        paramsJson = new String[blockCount];
        propsOf = new Prop[blockCount][];
        int total = 0;
        List<int[]> layouts = new ArrayList<>();
        for (int b = 0; b < blockCount; b++) {
            Line l = lines.get(b);
            names[b] = l.name;
            types[b] = l.type;
            first[b] = l.first;
            defaultState[b] = l.def;
            paramsJson[b] = l.params;
            byName.put(l.name, b);
            int count = 1;
            if (!l.props.equals("-")) {
                String[] ps = l.props.split(";");
                for (String p : ps) count *= p.substring(p.indexOf('=') + 1).split("\\|").length;
            }
            stateCountOf[b] = count;
            total = Math.max(total, l.first + count);
        }
        stateCount = total;
        blockOfState = new short[stateCount];
        for (int b = 0; b < blockCount; b++) {
            for (int s = 0; s < stateCountOf[b]; s++) blockOfState[first[b] + s] = (short) b;
        }
        for (int b = 0; b < blockCount; b++) {
            Line l = lines.get(b);
            if (l.props.equals("-")) {
                propsOf[b] = new Prop[0];
                continue;
            }
            String[] ps = l.props.split(";");
            Prop[] list = new Prop[ps.length];
            int stride = 1;
            for (int i = ps.length - 1; i >= 0; i--) {
                String name = ps[i].substring(0, ps[i].indexOf('='));
                String[] values = ps[i].substring(ps[i].indexOf('=') + 1).split("\\|");
                Prop p = props.computeIfAbsent(name, n -> new Prop(n, blockCount));
                p.define(b, stride, values);
                list[i] = p;
                stride *= values.length;
            }
            propsOf[b] = list;
        }
    }

    // ------------------------------------------------------------------ 질의

    public int blockCount() { return blockCount; }

    public int stateCount() { return stateCount; }

    /** 상태 → 블록 번호. */
    public int blockOf(int state) { return blockOfState[state]; }

    public String name(int block) { return names[block]; }

    public String nameOfState(int state) { return names[blockOfState[state]]; }

    /** 정의 종류(바닐라 블록 코덱 이름, 예 minecraft:stair). */
    public String type(int block) { return types[block]; }

    public int firstState(int block) { return first[block]; }

    public int stateCountOf(int block) { return stateCountOf[block]; }

    public int defaultState(int block) { return defaultState[block]; }

    /** 블록 이름 → 번호(없으면 -1). */
    public int block(String name) {
        Integer b = byName.get(name);
        return b == null ? -1 : b;
    }

    /** 블록 이름 → 기본 상태(없으면 -1). */
    public int defaultState(String name) {
        int b = block(name);
        return b < 0 ? -1 : defaultState[b];
    }

    /** 정의 매개변수(나무 종류 등 — type·properties 를 뺀 바닐라 코덱 필드). */
    public JsonObject params(int block) { return JsonParser.parseString(paramsJson[block]).getAsJsonObject(); }

    /** 속성 키(이름이 같은 속성은 블록마다 값 목록이 달라도 한 키). 없으면 빈 키. */
    public Prop prop(String name) {
        Prop p = props.get(name);
        return p != null ? p : new Prop(name, blockCount);
    }

    public Prop[] propsOf(int block) { return propsOf[block]; }

    public boolean is(int state, int block) { return blockOfState[state] == block; }

    /** 디버그·로그용 "이름[속성=값,…]". */
    public String describe(int state) {
        int b = blockOfState[state];
        if (propsOf[b].length == 0) return names[b];
        StringBuilder sb = new StringBuilder(names[b]).append('[');
        for (int i = 0; i < propsOf[b].length; i++) {
            if (i > 0) sb.append(',');
            Prop p = propsOf[b][i];
            sb.append(p.name()).append('=').append(p.valueName(state));
        }
        return sb.append(']').toString();
    }
}
