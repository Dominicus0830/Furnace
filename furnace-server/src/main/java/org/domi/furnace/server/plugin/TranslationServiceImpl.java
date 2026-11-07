// Furnace.translations(): 플러그인 몫 번역 저장소. 소유 플러그인이 꺼지면 PluginManagerImpl.disable 이 unregisterAll 을 부른다.
package org.domi.furnace.server.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.text.translation.GlobalRenderer;
import org.domi.furnace.api.text.translation.TranslationService;
import org.domi.furnace.api.text.translation.TranslationStore;

final class TranslationServiceImpl implements TranslationService {

    private final Map<FurnacePlugin, List<TranslationStore>> byOwner = new ConcurrentHashMap<>();

    @Override
    public synchronized TranslationStore register(FurnacePlugin owner, TranslationStore store) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(store, "store");
        if (!owner.isEnabled()) throw new IllegalStateException("꺼진 플러그인은 번역을 올릴 수 없다: " + owner.getClass().getName());
        List<TranslationStore> list = new ArrayList<>(byOwner.getOrDefault(owner, List.of()));
        if (!list.contains(store)) {
            list.add(store);
            byOwner.put(owner, List.copyOf(list));
        }
        store.registerGlobally();
        return store;
    }

    @Override
    public TranslationStore load(FurnacePlugin owner, String pattern, Locale defaultLocale, Locale... locales) {
        return load(owner, pattern, defaultLocale, TranslationStore.Format.VANILLA, locales);
    }

    @Override
    public TranslationStore load(FurnacePlugin owner, String pattern, Locale defaultLocale, TranslationStore.Format format, Locale... locales) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(defaultLocale, "defaultLocale");
        if (!pattern.contains("{locale}")) throw new IllegalArgumentException("pattern 에 {locale} 자리가 없다: " + pattern);
        if (!owner.isEnabled()) throw new IllegalStateException("꺼진 플러그인은 번역을 올릴 수 없다: " + owner.getClass().getName());
        List<Locale> all = new ArrayList<>(List.of(locales));
        if (!all.contains(defaultLocale)) all.add(0, defaultLocale);
        TranslationStore store = TranslationStore.create(defaultLocale, format);
        boolean any = false, hasDefault = false;
        for (Locale l : all) {
            Map<String, String> entries = read(owner, pattern.replace("{locale}", code(l)));
            if (entries == null) continue;
            store.registerAll(l, entries);
            any = true;
            if (l.equals(defaultLocale)) hasDefault = true;
        }
        if (!any) throw new IllegalArgumentException("번역 파일이 하나도 없다: " + pattern);
        if (!hasDefault) throw new IllegalArgumentException("기본 언어 번역 파일이 없다: " + pattern.replace("{locale}", code(defaultLocale)));
        return register(owner, store);
    }

    /** 마인크래프트식 언어 코드: en_us, ko_kr(나라가 없으면 언어만). */
    static String code(Locale l) {
        String lang = l.getLanguage();
        return l.getCountry().isEmpty() ? lang : lang + "_" + l.getCountry().toLowerCase(Locale.ROOT);
    }

    /** 데이터 폴더 파일 우선, 없으면 jar 리소스. 둘 다 없으면 null. */
    private static Map<String, String> read(FurnacePlugin owner, String name) {
        Path file = owner.dataFolder().resolve(name).normalize();
        if (!file.startsWith(owner.dataFolder().normalize())) throw new IllegalArgumentException("데이터 폴더 밖 경로: " + name);
        try {
            if (Files.isRegularFile(file)) {
                try (var in = Files.newInputStream(file)) { return parse(in); }
            }
            try (InputStream in = owner.resource(name)) {
                return in == null ? null : parse(in);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<String, String> parse(InputStream in) throws IOException {
        Properties p = new Properties();
        try (var r = new InputStreamReader(in, StandardCharsets.UTF_8)) { p.load(r); }
        Map<String, String> m = new LinkedHashMap<>();
        for (String k : p.stringPropertyNames()) m.put(k, p.getProperty(k));
        return m;
    }

    @Override
    public synchronized boolean unregister(FurnacePlugin owner, TranslationStore store) {
        List<TranslationStore> list = byOwner.get(owner);
        if (list == null || !list.contains(store)) return false;
        List<TranslationStore> rest = new ArrayList<>(list);
        rest.remove(store);
        if (rest.isEmpty()) byOwner.remove(owner); else byOwner.put(owner, List.copyOf(rest));
        store.unregisterGlobally();
        return true;
    }

    @Override
    public synchronized void unregisterAll(FurnacePlugin owner) {
        List<TranslationStore> list = byOwner.remove(owner);
        if (list != null) for (TranslationStore s : list) s.unregisterGlobally();
    }

    @Override
    public List<TranslationStore> stores(FurnacePlugin owner) { return byOwner.getOrDefault(owner, List.of()); }

    @Override
    public Text render(Text text, Locale locale) { return GlobalRenderer.render(text, locale); }
}
