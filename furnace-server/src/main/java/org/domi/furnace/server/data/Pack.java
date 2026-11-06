// 팩 하나(바닐라 Pack + PackLocationInfo + PackSelectionConfig). 열 때마다 새 PackResources(오버레이 반영)를 만든다.
package org.domi.furnace.server.data;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.domi.furnace.api.text.NamedTextColor;
import org.domi.furnace.api.text.Text;

public final class Pack {

    /** 바닐라 Pack.Position — 목록에 넣을 때 기본 자리. */
    public enum Position { TOP, BOTTOM }

    /** 바닐라 PackSource — 이름 꾸밈과 "새로 발견하면 자동으로 켜는가". */
    public enum Source {
        DEFAULT(null, true), BUILT_IN("pack.source.builtin", true), FEATURE("pack.source.feature", false),
        WORLD("pack.source.world", true), SERVER("pack.source.server", true),
        /** Furnace 플러그인이 jar 안에서 알린 팩 — 월드 팩처럼 새로 보이면 자동으로 켠다. 바닐라에 번역 키가 없어 글자 그대로 꾸민다. */
        PLUGIN(null, true);

        private final String key;
        public final boolean addAutomatically;

        Source(String key, boolean addAutomatically) { this.key = key; this.addAutomatically = addAutomatically; }

        /** "이름 (출처)" 회색 — 바닐라 PackSource.decorating. */
        public Text decorate(Text name) {
            if (this == PLUGIN) return Text.translatable("pack.nameAndSource", name, Text.text("plugin")).color(NamedTextColor.GRAY);
            return key == null ? name : Text.translatable("pack.nameAndSource", name, Text.translatable(key)).color(NamedTextColor.GRAY);
        }
    }

    /** 바닐라 PackCompatibility. */
    public enum Compatibility {
        TOO_OLD, TOO_NEW, COMPATIBLE;

        public boolean isCompatible() { return this == COMPATIBLE; }

        static Compatibility of(PackMetadata.FormatRange range, int current) {
            if (range.max() < current) return TOO_OLD;
            return current < range.min() ? TOO_NEW : COMPATIBLE;
        }
    }

    private final String id;
    private final Text title;
    private final Source source;
    private final PackMetadata metadata;
    private final Compatibility compatibility;
    private final boolean required, fixedPosition;
    private final Position defaultPosition;
    private final Function<List<String>, PackResources> opener;   // 오버레이 디렉터리 → 리소스

    Pack(String id, Text title, Source source, PackMetadata metadata, Compatibility compatibility, boolean required,
         Position defaultPosition, boolean fixedPosition, Function<List<String>, PackResources> opener) {
        this.id = id;
        this.title = title;
        this.source = source;
        this.metadata = metadata;
        this.compatibility = compatibility;
        this.required = required;
        this.defaultPosition = defaultPosition;
        this.fixedPosition = fixedPosition;
        this.opener = opener;
    }

    public String id() { return id; }
    public Text title() { return title; }
    public Text description() { return metadata.description(); }
    public Source source() { return source; }
    public PackMetadata metadata() { return metadata; }
    public Compatibility compatibility() { return compatibility; }
    public Set<String> requestedFeatures() { return metadata.features(); }
    public boolean required() { return required; }
    public boolean fixedPosition() { return fixedPosition; }
    public Position defaultPosition() { return defaultPosition; }

    /** 현재 형식의 오버레이를 반영해 연다. */
    public PackResources open() { return opener.apply(metadata.activeOverlays(PackRepository.DATA_PACK_FORMAT)); }

    /**
     * 바닐라 PackLocationInfo.createChatLink — [출처로 꾸민 id] (켜졌으면 초록·아니면 빨강), 삽입 텍스트 = id(필요하면 따옴표),
     * 호버 = 제목 + 줄바꿈 + 설명.
     */
    public Text chatLink(boolean enabled) {
        return Text.translatable("chat.square_brackets", source.decorate(Text.text(id)))
            .color(enabled ? NamedTextColor.GREEN : NamedTextColor.RED)
            .insertion(escapeIfRequired(id))
            .hoverText(Text.empty().append(title).append("\n").append(description()));
    }

    /** Brigadier StringArgumentType.escapeIfRequired. */
    static String escapeIfRequired(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(c >= '0' && c <= '9' || c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c == '_' || c == '-' || c == '.' || c == '+')) {
                StringBuilder b = new StringBuilder("\"");
                for (int j = 0; j < s.length(); j++) {
                    char d = s.charAt(j);
                    if (d == '\\' || d == '"') b.append('\\');
                    b.append(d);
                }
                return b.append('"').toString();
            }
        }
        return s;
    }

    /** 바닐라 Pack.Position.insert — 고정 자리 팩을 건너뛰며 기본 자리 쪽 끝에 넣고 그 인덱스를 돌려준다. */
    public static int insert(List<Pack> list, Pack pack, boolean flipPosition) {
        Position pos = flipPosition ? (pack.defaultPosition == Position.TOP ? Position.BOTTOM : Position.TOP) : pack.defaultPosition;
        if (pos == Position.BOTTOM) {
            int i = 0;
            while (i < list.size()) {
                Pack p = list.get(i);
                if (!p.fixedPosition || p.defaultPosition != pos) break;
                i++;
            }
            list.add(i, pack);
            return i;
        }
        int i = list.size() - 1;
        while (i >= 0) {
            Pack p = list.get(i);
            if (!p.fixedPosition || p.defaultPosition != pos) break;
            i--;
        }
        list.add(i + 1, pack);
        return i + 1;
    }

    @Override public boolean equals(Object o) { return o instanceof Pack p && p.id.equals(id); }

    @Override public int hashCode() { return id.hashCode(); }

    @Override public String toString() { return id; }
}
