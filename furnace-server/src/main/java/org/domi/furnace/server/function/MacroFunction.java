// 바닐라 MacroFunction(ij) + FunctionBuilder(ih) 의 매크로 부분 + StringTemplate(il).
// '$' 줄은 템플릿으로 들고 있다가 호출 때 인자 compound 로 치환해 파싱한다. 치환 결과(인자 문자열 목록)별 8개 LRU 캐시.
package org.domi.furnace.server.function;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.item.Snbt;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.command.exec.UnboundEntryAction;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public final class MacroFunction implements CommandFunction {

    /** 바닐라 ij.a: "#" + 소수 최대 15자리, US 기호. DecimalFormat 은 스레드 안전하지 않아 이 객체로 잠근다. */
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#");
    static {
        DECIMAL_FORMAT.setMaximumFractionDigits(15);
        DECIMAL_FORMAT.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.US));
    }
    private static final int MAX_CACHE_ENTRIES = 8;

    private final ResourceId id;
    private final List<Entry> entries;
    private final List<String> parameters;
    /** 접근 순서 LRU(바닐라 Object2ObjectLinkedOpenHashMap.getAndMoveToLast + removeFirst). */
    private final LinkedHashMap<List<String>, PlainTextFunction> cache = new LinkedHashMap<>(8, 0.75f, true);

    MacroFunction(ResourceId id, List<Entry> entries, List<String> parameters) {
        this.id = id;
        this.entries = entries;
        this.parameters = parameters;
    }

    @Override
    public ResourceId id() { return id; }

    /** 매크로 함수는 인스턴스화해야 줄이 생긴다. */
    @Override
    public List<UnboundEntryAction> entries() {
        throw new IllegalStateException("매크로 함수 " + id + " 는 instantiate 뒤에 실행해야 한다");
    }

    public List<String> parameters() { return parameters; }

    /** 테스트: 캐시에 든 인스턴스 수. */
    public synchronized int cachedInstances() { return cache.size(); }

    @Override
    public synchronized PlainTextFunction instantiate(NbtCompound arguments, CommandDispatcher<CommandSource> dispatcher)
            throws FunctionInstantiationException {
        if (arguments == null) {
            throw new FunctionInstantiationException(Text.translatable("commands.function.error.missing_arguments", Text.text(id.toString())));
        }
        List<String> values = new ArrayList<>(parameters.size());
        for (String key : parameters) {
            Object tag = arguments.tags().get(key);
            if (tag == null) {
                throw new FunctionInstantiationException(Text.translatable("commands.function.error.missing_argument", Text.text(id.toString()), key));
            }
            values.add(stringify(tag));
        }
        PlainTextFunction cached = cache.get(values);   // 접근 순서 맵이라 get 이 맨 뒤로 옮긴다
        if (cached != null) return cached;
        if (cache.size() >= MAX_CACHE_ENTRIES) cache.remove(cache.keySet().iterator().next());
        PlainTextFunction f = substituteAndParse(values, dispatcher);
        cache.put(values, f);
        return f;
    }

    /** 바닐라 ij.a(uy): float/double 은 15자리 소수, byte/short/long 은 숫자만, 문자열은 그대로, 그 밖(int·리스트·compound·배열)은 SNBT. */
    public static String stringify(Object tag) {
        if (tag instanceof Float f) {
            synchronized (DECIMAL_FORMAT) { return DECIMAL_FORMAT.format(f.floatValue()); }
        }
        if (tag instanceof Double d) {
            synchronized (DECIMAL_FORMAT) { return DECIMAL_FORMAT.format(d.doubleValue()); }
        }
        if (tag instanceof Byte b) return String.valueOf(b.byteValue());
        if (tag instanceof Short s) return String.valueOf(s.shortValue());
        if (tag instanceof Long l) return String.valueOf(l.longValue());
        if (tag instanceof String s) return s;   // StringTag.getAsString 은 따옴표 없는 원문
        return Snbt.write(tag);
    }

    private PlainTextFunction substituteAndParse(List<String> values, CommandDispatcher<CommandSource> dispatcher) throws FunctionInstantiationException {
        List<UnboundEntryAction> out = new ArrayList<>(entries.size());
        List<String> lineArgs = new ArrayList<>(values.size());
        for (Entry e : entries) {
            lineArgs.clear();
            for (int idx : e.parameters()) lineArgs.add(values.get(idx));
            out.add(e.instantiate(lineArgs, dispatcher, id));
        }
        // 바닐라 ij.a: 접미사는 매개변수 이름 목록의 hashCode — 함수마다 고정(인자 값과 무관)
        return new PlainTextFunction(id.withPath(id.path() + "/" + parameters.hashCode()), List.copyOf(out));
    }

    // ---------------------------------------------------------------- 줄

    /** 바닐라 ij.a: 줄 하나(평범한 줄 또는 매크로 줄). */
    interface Entry {
        int[] parameters();

        UnboundEntryAction instantiate(List<String> args, CommandDispatcher<CommandSource> dispatcher, ResourceId function)
                throws FunctionInstantiationException;
    }

    /** 바닐라 ij.c: 로드 때 파싱해 둔 줄. */
    record PlainEntry(UnboundEntryAction action) implements Entry {
        private static final int[] NONE = new int[0];

        public int[] parameters() { return NONE; }

        public UnboundEntryAction instantiate(List<String> args, CommandDispatcher<CommandSource> dispatcher, ResourceId function) { return action; }
    }

    /** 바닐라 ij.b: 템플릿 + 함수 인자 목록 안의 변수 위치 + 컴파일 소스. */
    record MacroEntry(Template template, int[] parameters, CommandSource compilationContext) implements Entry {
        public UnboundEntryAction instantiate(List<String> args, CommandDispatcher<CommandSource> dispatcher, ResourceId function)
                throws FunctionInstantiationException {
            String command = template.substitute(args);
            try {
                return CommandFunction.parseCommand(dispatcher, compilationContext, new StringReader(command));
            } catch (CommandSyntaxException e) {
                throw new FunctionInstantiationException(
                        Text.translatable("commands.function.error.parse", Text.text(function.toString()), command, e.getMessage()));
            }
        }
    }

    /** 바닐라 StringTemplate(il): 조각과 변수 이름. */
    record Template(List<String> segments, List<String> variables) {

        static Template fromString(String input, int lineNumber) {
            List<String> segments = new ArrayList<>();
            List<String> variables = new ArrayList<>();
            int len = input.length();
            int last = 0;
            int idx = input.indexOf('$');
            while (idx != -1) {
                if (idx != len - 1 && input.charAt(idx + 1) == '(') {
                    segments.add(input.substring(last, idx));
                    int end = input.indexOf(')', idx + 1);
                    if (end == -1) throw new IllegalArgumentException("Unterminated macro variable in macro '" + input + "' on line " + lineNumber);
                    String name = input.substring(idx + 2, end);
                    if (!isValidVariableName(name)) throw new IllegalArgumentException("Invalid macro variable name '" + name + "' on line " + lineNumber);
                    variables.add(name);
                    last = end + 1;
                    idx = input.indexOf('$', last);
                } else {
                    idx = input.indexOf('$', idx + 1);
                }
            }
            if (last == 0) throw new IllegalArgumentException("Macro without variables on line " + lineNumber);
            if (last != len) segments.add(input.substring(last));
            return new Template(List.copyOf(segments), List.copyOf(variables));
        }

        private static boolean isValidVariableName(String s) {
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (!Character.isLetterOrDigit(c) && c != '_') return false;
            }
            return true;
        }

        String substitute(List<String> args) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < variables.size(); i++) {
                sb.append(segments.get(i)).append(args.get(i));
                CommandFunction.checkCommandLineLength(sb);
            }
            if (segments.size() > variables.size()) sb.append(segments.get(segments.size() - 1));
            CommandFunction.checkCommandLineLength(sb);
            return sb.toString();
        }
    }

    // ---------------------------------------------------------------- 빌더(바닐라 ih)

    /** 매크로 줄이 나오기 전엔 평범한 줄 목록, 나온 뒤엔 Entry 목록. */
    static final class Builder {
        private List<UnboundEntryAction> plainEntries = new ArrayList<>();
        private List<Entry> macroEntries;
        private final List<String> macroArguments = new ArrayList<>();

        void addCommand(UnboundEntryAction action) {
            if (macroEntries != null) macroEntries.add(new PlainEntry(action));
            else plainEntries.add(action);
        }

        private int getArgumentIndex(String name) {
            int i = macroArguments.indexOf(name);
            if (i == -1) {
                i = macroArguments.size();
                macroArguments.add(name);
            }
            return i;
        }

        void addMacro(String line, int lineNumber, CommandSource compilationContext) {
            Template t = Template.fromString(line, lineNumber);
            if (plainEntries != null) {
                macroEntries = new ArrayList<>(plainEntries.size() + 1);
                for (UnboundEntryAction a : plainEntries) macroEntries.add(new PlainEntry(a));
                plainEntries = null;
            }
            int[] idx = new int[t.variables().size()];
            for (int i = 0; i < idx.length; i++) idx[i] = getArgumentIndex(t.variables().get(i));
            macroEntries.add(new MacroEntry(t, idx, compilationContext));
        }

        CommandFunction build(ResourceId id) {
            return macroEntries != null
                    ? new MacroFunction(id, List.copyOf(macroEntries), List.copyOf(macroArguments))
                    : new PlainTextFunction(id, List.copyOf(plainEntries));
        }
    }
}
