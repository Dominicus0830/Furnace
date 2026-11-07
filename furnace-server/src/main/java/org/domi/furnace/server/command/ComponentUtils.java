// 바닐라 ComponentUtils.updateForEntity — 명령이 보내기 직전(표지판은 레벨에서 읽을 때) 텍스트 안의 선택자·점수를 풀어 쓴다.
// 선택자(SelectorContents): 찾은 대상의 표시 이름을 formatList 로 잇는다 — 하나면 그 표시 이름 자체(스타일은 원래 텍스트 것으로 바뀐다), 여럿이면 빈 텍스트에.
// 점수(ScoreContents): 이름(선택자면 대상 하나)·목표로 점수판을 찾아 숫자 형식으로. 없으면 빈 텍스트.
// 차이: 선택자는 플레이어만 찾는다(일반 엔티티는 region 이 나눠 가져 동기 조회가 안 된다). NBT 내용(NbtContents)은 풀지 않고 그대로 둔다.
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
        } else if (content instanceof Content.Score sc) {
            out = resolveScore(source, sc, entity);
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

    /**
     * 바닐라 SelectorContents.resolve — 구분자를 먼저 풀고, 선택자로 찾은 대상의 표시 이름을 formatList(대상, 구분자, getDisplayName) 로 잇는다.
     * 선택자 문법이 틀리면 빈 텍스트.
     */
    private static Text resolveSelector(CommandSource source, Content.Selector sel, Player entity, int depth) throws CommandSyntaxException {
        EntitySelector selector = parseSelector(sel.pattern());
        if (selector == null) return Text.empty();
        Text sep = sel.separator() == null ? defaultSeparator() : updateForEntity(source, sel.separator(), entity, depth);
        List<Player> found = selector.findEntities(source);
        return formatList(found, sep);
    }

    /** 바닐라 ComponentUtils.formatList(목록, 구분자, getDisplayName): 비면 빈 텍스트, 하나면 그 표시 이름, 여럿이면 빈 텍스트에 구분자로 잇는다. */
    static Text formatList(List<Player> found, Text sep) {
        if (found.isEmpty()) return Text.empty();
        if (found.size() == 1) return found.get(0).displayName();
        Text out = Text.empty();
        for (int i = 0; i < found.size(); i++) {
            if (i > 0) out = out.append(sep);
            out = out.append(found.get(i).displayName());
        }
        return out;
    }

    /** SelectorContents·ScoreContents.parseSelector: 선택자 허용 파서(이름도 선택자가 된다). 틀리면 null. */
    private static EntitySelector parseSelector(String pattern) {
        try {
            return new EntitySelectorParser(new StringReader(pattern), true).parse();
        } catch (CommandSyntaxException e) {
            return null;
        }
    }

    /** StyledFormat.NO_STYLE: 점수를 스타일 없는 숫자 글자로. */
    private static final org.domi.furnace.server.scores.NumberFormat NO_STYLE = new org.domi.furnace.server.scores.NumberFormat.Styled(Style.empty());

    /**
     * 바닐라 ScoreContents.resolve: 주인 = 이름이 선택자로 읽히고 대상이 있으면 그 하나(둘 이상이면 오류), 아니면 이름 그대로.
     * 주인이 "*" 이고 entity 가 있으면 entity. 점수판에 목표·점수가 있으면 formatValue(목표 형식 또는 NO_STYLE), 아니면 빈 텍스트.
     */
    private static Text resolveScore(CommandSource source, Content.Score sc, Player entity) throws CommandSyntaxException {
        org.domi.furnace.server.scores.ScoreHolder holder = findTargetName(source, sc.name());
        if (entity != null && org.domi.furnace.server.scores.ScoreHolder.WILDCARD_NAME.equals(holder.scoreboardName())) {
            holder = org.domi.furnace.server.scores.ScoreHolder.of(entity);
        }
        var server = source.getServer();
        if (server == null) return Text.empty();
        var board = server.scoreboard();
        var objective = board.getObjective(sc.objective());
        if (objective == null) return Text.empty();
        var info = board.getPlayerScoreInfo(holder, objective);
        if (info == null) return Text.empty();
        return info.formatValue(objective.numberFormatOrDefault(NO_STYLE));
    }

    /** ScoreContents.findTargetName. */
    private static org.domi.furnace.server.scores.ScoreHolder findTargetName(CommandSource source, String name) throws CommandSyntaxException {
        EntitySelector selector = parseSelector(name);
        if (selector != null) {
            List<Player> found = selector.findEntities(source);
            if (!found.isEmpty()) {
                if (found.size() != 1) throw org.domi.furnace.server.command.arguments.EntityArgument.ERROR_NOT_SINGLE_ENTITY.create();
                return org.domi.furnace.server.scores.ScoreHolder.of(found.get(0));
            }
        }
        return org.domi.furnace.server.scores.ScoreHolder.forNameOnly(name);
    }
}
