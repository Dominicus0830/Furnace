// 바닐라 ComponentUtils.updateForEntity — 명령이 보내기 직전에 텍스트 안의 선택자를 이름 목록으로 풀어 쓴다.
// 점수(score)·NBT 내용은 점수판·NBT 경로 시스템이 없어 풀지 않고 그대로 둔다(가정: 바닐라는 값으로 치환).
package org.domi.furnace.server.command;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.api.text.Content;
import org.domi.furnace.api.text.HoverEvent;
import org.domi.furnace.api.text.NamedTextColor;
import org.domi.furnace.api.text.Style;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.arguments.EntitySelector;
import org.domi.furnace.server.command.arguments.EntitySelectorParser;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.entity.Player;

public final class ComponentUtils {

    private static final int MAX_DEPTH = 100;

    private ComponentUtils() {}

    /** 바닐라 DEFAULT_SEPARATOR — 회색 ", ". */
    public static Text defaultSeparator() { return Text.text(", ").color(NamedTextColor.GRAY); }

    public static Text updateForEntity(CommandSource source, Text component, Player entity, int depth) throws CommandSyntaxException {
        if (source == null || depth > MAX_DEPTH) return component;
        depth++;
        Content content = component.content();
        Text out;
        if (content instanceof Content.Selector sel) {
            out = resolveSelector(source, sel, entity, depth);
        } else if (content instanceof Content.Translatable tr) {
            List<Object> args = new ArrayList<>(tr.args().size());
            for (Object a : tr.args()) args.add(a instanceof Text t ? updateForEntity(source, t, entity, depth) : a);
            out = Text.of(new Content.Translatable(tr.key(), tr.fallback(), args), Style.empty(), List.of());
        } else {
            out = Text.of(content, Style.empty(), List.of());
        }
        for (Text sibling : component.children()) out = out.append(updateForEntity(source, sibling, entity, depth));
        return out.style(resolveStyle(source, component.style(), entity, depth));
    }

    private static Style resolveStyle(CommandSource source, Style style, Player entity, int depth) throws CommandSyntaxException {
        if (style.hoverEvent() instanceof HoverEvent.ShowText st) {
            return style.hoverEvent(HoverEvent.showText(updateForEntity(source, st.text(), entity, depth)));
        }
        return style;
    }

    /** 바닐라 SelectorContents.resolve — 선택자를 풀어 찾은 대상의 이름을 구분자로 잇는다. 선택자 문법이 틀리면 빈 텍스트. */
    private static Text resolveSelector(CommandSource source, Content.Selector sel, Player entity, int depth) throws CommandSyntaxException {
        EntitySelector selector;
        try {
            selector = new EntitySelectorParser(new StringReader(sel.pattern()), true).parse();
        } catch (CommandSyntaxException e) {
            return Text.empty();
        }
        Text sep = sel.separator() == null ? defaultSeparator() : updateForEntity(source, sel.separator(), entity, depth);
        List<Player> found = selector.findEntities(source);
        Text out = Text.empty();
        for (int i = 0; i < found.size(); i++) {
            if (i > 0) out = out.append(sep);
            out = out.append(Text.text(found.get(i).name()));
        }
        return out;
    }
}
