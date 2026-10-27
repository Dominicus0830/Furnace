// 바닐라 NbtPathArgument(minecraft:nbt_path, 1.21.1) — NBT 경로와 get/count/set/insert/remove 연산. 속성 없음.
// 문법: 키(따옴표 가능) · [i] · [] · [{…}] · 키{…} · 맨 앞에서만 {…}. 마디 사이는 '.'(뒤가 '[' '{' 면 생략).
// 연산 대상은 NbtTags.thaw 로 배열을 NbtArray 로 바꾼 트리여야 한다(배열에 끼우기·빼기).
package org.domi.furnace.server.command.arguments;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.ArgumentType;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.DynamicCommandExceptionType;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.item.Snbt;
import org.domi.furnace.server.net.io.nbt.NbtArray;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.io.nbt.NbtTags;

public final class NbtPathArgument implements ArgumentType<NbtPathArgument.NbtPath> {

    private static final List<String> EXAMPLES = List.of("foo", "foo.bar", "foo[0]", "[0]", "[]", "{foo=bar}");
    public static final SimpleCommandExceptionType ERROR_INVALID_NODE = new SimpleCommandExceptionType(Text.translatable("arguments.nbtpath.node.invalid"));
    public static final SimpleCommandExceptionType ERROR_DATA_TOO_DEEP = new SimpleCommandExceptionType(Text.translatable("arguments.nbtpath.too_deep"));
    public static final DynamicCommandExceptionType ERROR_NOTHING_FOUND = new DynamicCommandExceptionType(p -> Text.translatable("arguments.nbtpath.nothing_found", p));
    static final DynamicCommandExceptionType ERROR_EXPECTED_LIST = new DynamicCommandExceptionType(t -> Text.translatable("commands.data.modify.expected_list", t));
    static final DynamicCommandExceptionType ERROR_INVALID_INDEX = new DynamicCommandExceptionType(i -> Text.translatable("commands.data.modify.invalid_index", i));

    public static NbtPathArgument nbtPath() { return new NbtPathArgument(); }

    public static NbtPath getPath(CommandContext<CommandSource> ctx, String name) { return ctx.getArgument(name, NbtPath.class); }

    @Override
    public NbtPath parse(StringReader reader) throws CommandSyntaxException {
        List<Node> nodes = new ArrayList<>();
        int start = reader.getCursor();
        Map<Node, Integer> ends = new IdentityHashMap<>();
        boolean root = true;
        while (reader.canRead() && reader.peek() != ' ') {
            Node n = parseNode(reader, root);
            nodes.add(n);
            ends.put(n, reader.getCursor() - start);
            root = false;
            if (reader.canRead()) {
                char c = reader.peek();
                if (c != ' ' && c != '[' && c != '{') reader.expect('.');
            }
        }
        return new NbtPath(reader.getString().substring(start, reader.getCursor()), nodes.toArray(new Node[0]), ends);
    }

    /** 문자열 하나를 경로로(NbtPath.of). */
    public static NbtPath of(String s) throws CommandSyntaxException { return new NbtPathArgument().parse(new StringReader(s)); }

    private static Node parseNode(StringReader reader, boolean root) throws CommandSyntaxException {
        return switch (reader.peek()) {
            case '"', '\'' -> readObjectNode(reader, reader.readString());
            case '[' -> {
                reader.skip();
                char c = reader.peek();
                if (c == '{') {
                    NbtCompound pattern = new TagParser(reader).readStruct();
                    reader.expect(']');
                    yield new MatchElementNode(pattern);
                } else if (c == ']') {
                    reader.skip();
                    yield AllElementsNode.INSTANCE;
                } else {
                    int i = reader.readInt();
                    reader.expect(']');
                    yield new IndexedElementNode(i);
                }
            }
            case '{' -> {
                if (!root) throw ERROR_INVALID_NODE.createWithContext(reader);
                yield new MatchRootObjectNode(new TagParser(reader).readStruct());
            }
            default -> readObjectNode(reader, readUnquotedName(reader));
        };
    }

    private static Node readObjectNode(StringReader reader, String name) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '{') return new MatchObjectNode(name, new TagParser(reader).readStruct());
        return new CompoundChildNode(name);
    }

    private static String readUnquotedName(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        while (reader.canRead() && isAllowedInUnquotedName(reader.peek())) reader.skip();
        if (reader.getCursor() == start) throw ERROR_INVALID_NODE.createWithContext(reader);
        return reader.getString().substring(start, reader.getCursor());
    }

    private static boolean isAllowedInUnquotedName(char c) {
        return c != ' ' && c != '"' && c != '\'' && c != '[' && c != ']' && c != '.' && c != '{' && c != '}';
    }

    static Predicate<Object> createTagPredicate(NbtCompound pattern) { return t -> NbtTags.compareNbt(pattern, t, true); }

    @Override
    public Collection<String> getExamples() { return EXAMPLES; }

    @Override
    public String networkId() { return "minecraft:nbt_path"; }

    // ------------------------------------------------------------------ CollectionTag 동작(리스트·배열)

    private static boolean isCollection(Object t) { return t instanceof NbtList || t instanceof NbtArray; }

    private static int size(Object c) { return c instanceof NbtList l ? l.size() : ((NbtArray) c).size(); }

    private static Object elementAt(Object c, int i) { return c instanceof NbtList l ? l.get(i) : ((NbtArray) c).get(i); }

    private static List<Object> elements(Object c) { return c instanceof NbtList l ? l.items() : ((NbtArray) c).elements(); }

    private static boolean addTag(Object c, int i, Object v) {
        if (c instanceof NbtList l) return l.addTag(i, v, NbtTags.typeOf(v));
        return ((NbtArray) c).addTag(i, v);
    }

    private static boolean setTag(Object c, int i, Object v) {
        if (c instanceof NbtList l) return l.setTag(i, v, NbtTags.typeOf(v));
        return ((NbtArray) c).setTag(i, v);
    }

    private static void removeAt(Object c, int i) {
        if (c instanceof NbtList l) l.remove(i);
        else ((NbtArray) c).remove(i);
    }

    private static void clear(Object c) {
        if (c instanceof NbtList l) l.clear();
        else ((NbtArray) c).clear();
    }

    /** ListTag.add(tag): 종류가 다르면 바닐라처럼 UnsupportedOperationException. */
    private static void listAdd(NbtList l, Object v) {
        if (!l.addTag(l.size(), v, NbtTags.typeOf(v))) {
            throw new UnsupportedOperationException("Trying to add tag of type " + NbtTags.typeOf(v) + " to list of " + l.elementType());
        }
    }

    // ------------------------------------------------------------------ 마디

    interface Node {
        void getTag(Object tag, List<Object> out);

        void getOrCreateTag(Object tag, Supplier<Object> child, List<Object> out);

        Object createPreferredParentTag();

        int setTag(Object tag, Supplier<Object> value);

        int removeTag(Object tag);

        default List<Object> get(List<Object> tags) { return collect(tags, this::getTag); }

        default List<Object> getOrCreate(List<Object> tags, Supplier<Object> child) { return collect(tags, (t, out) -> getOrCreateTag(t, child, out)); }

        default List<Object> collect(List<Object> tags, BiConsumer<Object, List<Object>> fn) {
            List<Object> out = new ArrayList<>();
            for (Object t : tags) fn.accept(t, out);
            return out;
        }
    }

    static final class AllElementsNode implements Node {
        static final AllElementsNode INSTANCE = new AllElementsNode();

        public void getTag(Object tag, List<Object> out) { if (isCollection(tag)) out.addAll(elements(tag)); }

        public void getOrCreateTag(Object tag, Supplier<Object> child, List<Object> out) {
            if (!isCollection(tag)) return;
            if (size(tag) == 0) {
                Object v = child.get();
                if (addTag(tag, 0, v)) out.add(v);
            } else {
                out.addAll(elements(tag));
            }
        }

        public Object createPreferredParentTag() { return new NbtList(org.domi.furnace.server.net.io.nbt.NbtIo.TAG_END); }

        public int setTag(Object tag, Supplier<Object> value) {
            if (!isCollection(tag)) return 0;
            int n = size(tag);
            if (n == 0) {
                addTag(tag, 0, value.get());
                return 1;
            }
            Object v = value.get();
            int same = 0;
            for (Object e : elements(tag)) if (NbtTags.equal(v, e)) same++;
            int changed = n - same;
            if (changed == 0) return 0;
            clear(tag);
            if (!addTag(tag, 0, v)) return 0;
            for (int i = 1; i < n; i++) addTag(tag, i, value.get());
            return changed;
        }

        public int removeTag(Object tag) {
            if (isCollection(tag)) {
                int n = size(tag);
                if (n > 0) {
                    clear(tag);
                    return n;
                }
            }
            return 0;
        }
    }

    record CompoundChildNode(String name) implements Node {
        public void getTag(Object tag, List<Object> out) {
            if (tag instanceof NbtCompound c) {
                Object v = c.tags().get(name);
                if (v != null) out.add(v);
            }
        }

        public void getOrCreateTag(Object tag, Supplier<Object> child, List<Object> out) {
            if (!(tag instanceof NbtCompound c)) return;
            Object v;
            if (c.contains(name)) v = c.tags().get(name);
            else {
                v = child.get();
                c.put(name, v);
            }
            out.add(v);
        }

        public Object createPreferredParentTag() { return new NbtCompound(); }

        public int setTag(Object tag, Supplier<Object> value) {
            if (tag instanceof NbtCompound c) {
                Object v = value.get();
                Object old = c.tags().put(name, v);
                if (!NbtTags.equal(v, old)) return 1;
            }
            return 0;
        }

        public int removeTag(Object tag) {
            if (tag instanceof NbtCompound c && c.contains(name)) {
                c.tags().remove(name);
                return 1;
            }
            return 0;
        }
    }

    record IndexedElementNode(int index) implements Node {
        public void getTag(Object tag, List<Object> out) {
            if (isCollection(tag)) {
                int n = size(tag);
                int i = index < 0 ? n + index : index;
                if (0 <= i && i < n) out.add(elementAt(tag, i));
            }
        }

        public void getOrCreateTag(Object tag, Supplier<Object> child, List<Object> out) { getTag(tag, out); }

        public Object createPreferredParentTag() { return new NbtList(org.domi.furnace.server.net.io.nbt.NbtIo.TAG_END); }

        public int setTag(Object tag, Supplier<Object> value) {
            if (isCollection(tag)) {
                int n = size(tag);
                int i = index < 0 ? n + index : index;
                if (0 <= i && i < n) {
                    Object old = elementAt(tag, i);
                    Object v = value.get();
                    if (!NbtTags.equal(v, old) && setTag(tag, i, v)) return 1;
                }
            }
            return 0;
        }

        private static boolean setTag(Object c, int i, Object v) { return NbtPathArgument.setTag(c, i, v); }

        public int removeTag(Object tag) {
            if (isCollection(tag)) {
                int n = size(tag);
                int i = index < 0 ? n + index : index;
                if (0 <= i && i < n) {
                    removeAt(tag, i);
                    return 1;
                }
            }
            return 0;
        }
    }

    static final class MatchElementNode implements Node {
        private final NbtCompound pattern;
        private final Predicate<Object> predicate;

        MatchElementNode(NbtCompound pattern) {
            this.pattern = pattern;
            this.predicate = createTagPredicate(pattern);
        }

        public void getTag(Object tag, List<Object> out) {
            if (tag instanceof NbtList l) for (Object e : l.items()) if (predicate.test(e)) out.add(e);
        }

        public void getOrCreateTag(Object tag, Supplier<Object> child, List<Object> out) {
            if (!(tag instanceof NbtList l)) return;
            boolean found = false;
            for (Object e : l.items()) {
                if (predicate.test(e)) {
                    out.add(e);
                    found = true;
                }
            }
            if (!found) {
                Object c = NbtTags.copy(pattern);
                listAdd(l, c);
                out.add(c);
            }
        }

        public Object createPreferredParentTag() { return new NbtList(org.domi.furnace.server.net.io.nbt.NbtIo.TAG_END); }

        public int setTag(Object tag, Supplier<Object> value) {
            int n = 0;
            if (tag instanceof NbtList l) {
                int size = l.size();
                if (size == 0) {
                    listAdd(l, value.get());
                    n++;
                } else {
                    for (int i = 0; i < size; i++) {
                        Object e = l.get(i);
                        if (predicate.test(e)) {
                            Object v = value.get();
                            if (!NbtTags.equal(v, e) && l.setTag(i, v, NbtTags.typeOf(v))) n++;
                        }
                    }
                }
            }
            return n;
        }

        public int removeTag(Object tag) {
            int n = 0;
            if (tag instanceof NbtList l) {
                for (int i = l.size() - 1; i >= 0; i--) {
                    if (predicate.test(l.get(i))) {
                        l.remove(i);
                        n++;
                    }
                }
            }
            return n;
        }
    }

    static final class MatchObjectNode implements Node {
        private final String name;
        private final NbtCompound pattern;
        private final Predicate<Object> predicate;

        MatchObjectNode(String name, NbtCompound pattern) {
            this.name = name;
            this.pattern = pattern;
            this.predicate = createTagPredicate(pattern);
        }

        public void getTag(Object tag, List<Object> out) {
            if (tag instanceof NbtCompound c) {
                Object v = c.tags().get(name);
                if (predicate.test(v)) out.add(v);
            }
        }

        public void getOrCreateTag(Object tag, Supplier<Object> child, List<Object> out) {
            if (!(tag instanceof NbtCompound c)) return;
            Object v = c.tags().get(name);
            if (v == null) {
                Object nv = NbtTags.copy(pattern);
                c.put(name, nv);
                out.add(nv);
            } else if (predicate.test(v)) {
                out.add(v);
            }
        }

        public Object createPreferredParentTag() { return new NbtCompound(); }

        public int setTag(Object tag, Supplier<Object> value) {
            if (tag instanceof NbtCompound c) {
                Object old = c.tags().get(name);
                if (predicate.test(old)) {
                    Object v = value.get();
                    if (!NbtTags.equal(v, old)) {
                        c.put(name, v);
                        return 1;
                    }
                }
            }
            return 0;
        }

        public int removeTag(Object tag) {
            if (tag instanceof NbtCompound c) {
                Object old = c.tags().get(name);
                if (predicate.test(old)) {
                    c.tags().remove(name);
                    return 1;
                }
            }
            return 0;
        }
    }

    static final class MatchRootObjectNode implements Node {
        private final Predicate<Object> predicate;

        MatchRootObjectNode(NbtCompound pattern) { this.predicate = createTagPredicate(pattern); }

        public void getTag(Object tag, List<Object> out) { if (tag instanceof NbtCompound && predicate.test(tag)) out.add(tag); }

        public void getOrCreateTag(Object tag, Supplier<Object> child, List<Object> out) { getTag(tag, out); }

        public Object createPreferredParentTag() { return new NbtCompound(); }

        public int setTag(Object tag, Supplier<Object> value) { return 0; }

        public int removeTag(Object tag) { return 0; }
    }

    // ------------------------------------------------------------------ 경로

    public static final class NbtPath {
        private final String original;
        private final Map<Node, Integer> nodeToOriginalPosition;
        private final Node[] nodes;

        NbtPath(String original, Node[] nodes, Map<Node, Integer> positions) {
            this.original = original;
            this.nodes = nodes;
            this.nodeToOriginalPosition = positions;
        }

        /** get: 찾은 태그들. 없으면 arguments.nbtpath.nothing_found. */
        public List<Object> get(Object tag) throws CommandSyntaxException {
            List<Object> l = Collections.singletonList(tag);
            for (Node n : nodes) {
                l = n.get(l);
                if (l.isEmpty()) throw createNotFoundException(n);
            }
            return l;
        }

        /** countMatching: 찾은 태그 수(없으면 0). */
        public int countMatching(Object tag) {
            List<Object> l = Collections.singletonList(tag);
            for (Node n : nodes) {
                l = n.get(l);
                if (l.isEmpty()) return 0;
            }
            return l.size();
        }

        private List<Object> getOrCreateParents(Object tag) throws CommandSyntaxException {
            List<Object> l = Collections.singletonList(tag);
            for (int i = 0; i < nodes.length - 1; i++) {
                Node n = nodes[i];
                Node next = nodes[i + 1];
                l = n.getOrCreate(l, next::createPreferredParentTag);
                if (l.isEmpty()) throw createNotFoundException(n);
            }
            return l;
        }

        /** getOrCreate: 없으면 만들어서 찾는다. */
        public List<Object> getOrCreate(Object tag, Supplier<Object> child) throws CommandSyntaxException {
            List<Object> parents = getOrCreateParents(tag);
            return nodes[nodes.length - 1].getOrCreate(parents, child);
        }

        private static int apply(List<Object> tags, java.util.function.ToIntFunction<Object> fn) {
            int n = 0;
            for (Object t : tags) n += fn.applyAsInt(t);
            return n;
        }

        /** isTooDeep: 깊이 512 이상이면 true. */
        public static boolean isTooDeep(Object tag, int depth) {
            if (depth >= 512) return true;
            if (tag instanceof NbtCompound c) {
                for (Object v : c.tags().values()) if (v != null && isTooDeep(v, depth + 1)) return true;
            } else if (tag instanceof NbtList l) {
                for (Object v : l.items()) if (isTooDeep(v, depth + 1)) return true;
            }
            return false;
        }

        /** set(tag, source): 바뀐 수. 첫 자리에는 복사본을, 그 뒤로는 또 복사한 것을 넣는다. */
        public int set(Object tag, Object source) throws CommandSyntaxException {
            if (isTooDeep(source, estimatePathDepth())) throw ERROR_DATA_TOO_DEEP.create();
            Object copy = NbtTags.copy(source);
            List<Object> parents = getOrCreateParents(tag);
            if (parents.isEmpty()) return 0;
            Node last = nodes[nodes.length - 1];
            boolean[] first = {false};
            return apply(parents, p -> last.setTag(p, () -> {
                if (!first[0]) {
                    first[0] = true;
                    return copy;
                }
                return NbtTags.copy(copy);
            }));
        }

        private int estimatePathDepth() { return nodes.length; }

        /** insert(index, root, tags): 각 대상 리스트에 끼운다. */
        public int insert(int index, NbtCompound root, List<Object> tags) throws CommandSyntaxException {
            List<Object> copies = new ArrayList<>(tags.size());
            for (Object t : tags) {
                Object c = NbtTags.copy(t);
                copies.add(c);
                if (isTooDeep(c, estimatePathDepth())) throw ERROR_DATA_TOO_DEEP.create();
            }
            Collection<Object> targets = getOrCreate(root, () -> new NbtList(org.domi.furnace.server.net.io.nbt.NbtIo.TAG_END));
            int n = 0;
            boolean copyAgain = false;
            for (Object target : targets) {
                if (!isCollection(target)) throw ERROR_EXPECTED_LIST.create(Snbt.write(NbtTags.freeze(NbtTags.copy(target))));
                boolean changed = false;
                int i = index < 0 ? size(target) + index + 1 : index;
                for (Object c : copies) {
                    try {
                        if (addTag(target, i, copyAgain ? NbtTags.copy(c) : c)) {
                            i++;
                            changed = true;
                        }
                    } catch (IndexOutOfBoundsException e) {
                        throw ERROR_INVALID_INDEX.create(i);
                    }
                }
                copyAgain = true;
                n += changed ? 1 : 0;
            }
            return n;
        }

        /** remove: 지운 수. */
        public int remove(Object tag) {
            List<Object> l = Collections.singletonList(tag);
            for (int i = 0; i < nodes.length - 1; i++) l = nodes[i].get(l);
            Node last = nodes[nodes.length - 1];
            return apply(l, last::removeTag);
        }

        private CommandSyntaxException createNotFoundException(Node n) {
            int pos = nodeToOriginalPosition.get(n);
            return ERROR_NOTHING_FOUND.create(original.substring(0, pos));
        }

        public String asString() { return original; }

        @Override
        public String toString() { return original; }
    }
}
