// MiniMessage 4.17 표준 태그 해석기(패키지 내부). 색, 꾸밈, reset, click, hover, key, lang, insert, font, newline, selector, score, nbt, 그라디언트 계열을 다룬다.
package org.domi.furnace.api.text.markup;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.domi.furnace.api.text.ClickEvent;
import org.domi.furnace.api.text.Content;
import org.domi.furnace.api.text.HoverEvent;
import org.domi.furnace.api.text.NamedTextColor;
import org.domi.furnace.api.text.Style;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.text.TextColor;
import org.domi.furnace.api.text.TextDecoration;

final class StandardTags implements TagResolver {

    static final StandardTags INSTANCE = new StandardTags();

    private static final Set<String> NAMES = Set.of("bold", "b", "italic", "i", "em", "underlined", "u", "underline", "strikethrough", "st",
            "obfuscated", "obf", "color", "c", "colour", "reset", "click", "hover", "key", "lang", "tr", "translate", "lang_or", "tr_or",
            "translate_or", "insert", "insertion", "font", "newline", "br", "selector", "sel", "score", "nbt", "data", "gradient", "rainbow",
            "transition", "pride", "pre");

    private static final Tag[] DECO_ON = new Tag[5], DECO_OFF = new Tag[5];

    static {
        for (TextDecoration d : TextDecoration.values()) {
            DECO_ON[d.ordinal()] = Tag.styling(Style.empty().decoration(d, true));
            DECO_OFF[d.ordinal()] = Tag.styling(Style.empty().decoration(d, false));
        }
    }

    private StandardTags() {}

    private static TextDecoration decoration(String n) {
        return switch (n) {
            case "bold", "b" -> TextDecoration.BOLD;
            case "italic", "i", "em" -> TextDecoration.ITALIC;
            case "underlined", "u", "underline" -> TextDecoration.UNDERLINED;
            case "strikethrough", "st" -> TextDecoration.STRIKETHROUGH;
            case "obfuscated", "obf" -> TextDecoration.OBFUSCATED;
            default -> null;
        };
    }

    @Override
    public boolean has(String name) {
        if (name.isEmpty()) return false;
        char c0 = name.charAt(0);
        if (c0 == '#') return TextColor.fromHexString(name) != null;
        if (c0 == '!') return decoration(name.substring(1)) != null;
        return NAMES.contains(name) || NamedTextColor.byName(name) != null;
    }

    @Override
    public Tag resolve(String name, ArgumentQueue args, Context ctx) {
        char c0 = name.charAt(0);
        if (c0 == '#') {
            TextColor col = TextColor.fromHexString(name);
            return col == null ? null : Tag.styling(Style.style(col));
        }
        boolean neg = c0 == '!';
        String n = neg ? name.substring(1) : name;
        TextDecoration deco = decoration(n);
        if (deco != null) {
            boolean v = !neg;
            if (args.hasNext()) {
                ArgumentQueue.Argument a = args.pop();
                if (a.isFalse()) v = !v;
                else if (!a.isTrue()) throw ctx.newException("decoration tag argument must be true/false: '" + a.value() + "'");
            }
            return (v ? DECO_ON : DECO_OFF)[deco.ordinal()];
        }
        if (neg) return null;
        switch (n) {
            case "color", "c", "colour" -> {
                return Tag.styling(Style.style(color(args.popOr("the color tag needs a color").value(), ctx)));
            }
            case "reset" -> { return Tag.RESET; }
            case "click" -> { return click(args, ctx); }
            case "hover" -> { return hover(args, ctx); }
            case "key" -> { return Tag.selfClosingInserting(Text.keybind(args.popOr("the key tag needs a key name").value())); }
            case "lang", "tr", "translate" -> {
                String key = args.popOr("the lang tag needs a translation key").value();
                return Tag.selfClosingInserting(Text.translatable(key, textArgs(args, ctx)));
            }
            case "lang_or", "tr_or", "translate_or" -> {
                String key = args.popOr("the lang_or tag needs a translation key").value();
                String fallback = args.popOr("the lang_or tag needs a fallback text").value();
                return Tag.selfClosingInserting(Text.of(new Content.Translatable(key, fallback, textArgs(args, ctx)), Style.empty(), List.of()));
            }
            case "insert", "insertion" -> { return Tag.styling(Style.empty().insertion(args.popRestOr("the insert tag needs the text to insert"))); }
            case "font" -> {
                String f = args.popRestOr("the font tag needs a font key");
                return Tag.styling(Style.empty().font(f.indexOf(':') < 0 ? "minecraft:" + f : f));
            }
            case "newline", "br" -> {
                int count = 1;
                if (args.hasNext()) {
                    OptionalInt c = args.pop().asInt();
                    if (c.isEmpty() || c.getAsInt() < 0) throw ctx.newException("the newline count is not an integer");
                    count = c.getAsInt();
                }
                if (count == 1) return Tag.selfClosingInserting(Text.newline());
                return Tag.selfClosingInserting(Text.text("\n".repeat(count)));
            }
            case "selector", "sel" -> {
                String pattern = args.popOr("the selector tag needs a selector").value();
                Text sep = args.hasNext() ? ctx.deserialize(args.popRestOr("")) : null;
                return Tag.selfClosingInserting(sep == null ? Text.selector(pattern) : Text.selector(pattern, sep));
            }
            case "score" -> {
                String sn = args.popOr("the score tag needs a name").value();
                String obj = args.popOr("the score tag needs an objective").value();
                return Tag.selfClosingInserting(Text.score(sn, obj));
            }
            case "nbt", "data" -> { return nbt(args, ctx); }
            case "gradient" -> { return ColorEffects.gradient(args, ctx); }
            case "rainbow" -> { return ColorEffects.rainbow(args, ctx); }
            case "transition" -> { return ColorEffects.transition(args, ctx); }
            case "pride" -> { return ColorEffects.pride(args, ctx); }
            default -> { }
        }
        TextColor named = NamedTextColor.byName(n);
        return named == null ? null : Tag.styling(Style.style(named));
    }

    // ------------------------------------------------------------------ 개별 태그

    static TextColor colorOrNull(String v) {
        if (v.startsWith("#")) return TextColor.fromHexString(v);
        return NamedTextColor.byName(v);
    }

    static TextColor color(String v, Context ctx) {
        TextColor c = colorOrNull(v);
        if (c == null) throw ctx.newException("'" + v + "' is not a valid color");
        return c;
    }

    private static List<Object> textArgs(ArgumentQueue args, Context ctx) {
        List<Object> list = new ArrayList<>(args.remaining());
        while (args.hasNext()) list.add(ctx.deserialize(args.pop().value()));
        return list;
    }

    private static boolean isUuid(String s) {
        try {
            UUID.fromString(s);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String namespaced(String id) { return id.indexOf(':') < 0 ? "minecraft:" + id : id; }

    private static Tag click(ArgumentQueue args, Context ctx) {
        String an = args.popOr("the click tag needs an action").lowerValue();
        ClickEvent.Action a = ClickEvent.Action.byKey(an);
        if (a == null) throw ctx.newException("unknown click action: '" + an + "'");
        String value = args.popRestOr("the click tag needs a value");
        return Tag.styling(Style.empty().clickEvent(new ClickEvent(a, value)));
    }

    private static Tag hover(ArgumentQueue args, Context ctx) {
        String an = args.popOr("the hover tag needs an action").lowerValue();
        HoverEvent ev;
        switch (an) {
            case "show_text" -> ev = HoverEvent.showText(ctx.deserialize(args.popRestOr("show_text needs the text to show")));
            case "show_item" -> {
                String id = args.popOr("show_item needs an item id").value();
                // "minecraft:diamond" 는 ':' 때문에 두 인자로 갈라진다 — 다음 인자가 정수가 아니면 경로 부분이다
                if (args.hasNext() && args.peek().asInt().isEmpty()) id = id + ":" + args.pop().value();
                id = namespaced(id);
                int count = 1;
                String comps = null;
                if (args.hasNext()) {
                    OptionalInt c = args.pop().asInt();
                    if (c.isEmpty()) throw ctx.newException("the show_item count is not an integer");
                    count = c.getAsInt();
                    if (args.hasNext()) comps = args.popRestOr("");
                }
                ev = new HoverEvent.ShowItem(id, count, comps);
            }
            case "show_entity" -> {
                String type = args.popOr("show_entity needs an entity type").value();
                if (args.remaining() >= 2 && !isUuid(args.peek().value())) type = type + ":" + args.pop().value(); // 네임스페이스가 붙은 종류
                type = namespaced(type);
                String uid = args.popOr("show_entity needs a UUID").value();
                UUID id;
                try {
                    id = UUID.fromString(uid);
                } catch (IllegalArgumentException e) {
                    throw ctx.newException("not a valid UUID: '" + uid + "'");
                }
                Text name = args.hasNext() ? ctx.deserialize(args.popRestOr("")) : null;
                ev = new HoverEvent.ShowEntity(type, id, name);
            }
            default -> throw ctx.newException("unknown hover action: '" + an + "'");
        }
        return Tag.styling(Style.empty().hoverEvent(ev));
    }

    private static Tag nbt(ArgumentQueue args, Context ctx) {
        String type = args.popOr("the nbt tag needs a type (block/entity/storage)").lowerValue();
        String id = args.popOr("the nbt tag needs a target").value();
        String path = args.popOr("the nbt tag needs a path").value();
        Content.NbtSource src = switch (type) {
            case "block" -> new Content.NbtSource.Block(id);
            case "entity" -> new Content.NbtSource.Entity(id);
            case "storage" -> new Content.NbtSource.Storage(id);
            default -> throw ctx.newException("the nbt type must be one of block/entity/storage: '" + type + "'");
        };
        boolean interpret = false;
        Text sep = null;
        while (args.hasNext()) {
            String v = args.pop().value();
            if (v.equalsIgnoreCase("interpret")) interpret = true;
            else sep = ctx.deserialize(v);
        }
        return Tag.selfClosingInserting(Text.nbt(path, interpret, sep, src));
    }
}
