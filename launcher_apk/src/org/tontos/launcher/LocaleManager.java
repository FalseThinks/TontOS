package org.tontos.launcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * High-performance Locale Architecture & Cache Manager for TontOS.
 *
 * Dynamically indexes all available Android runtime locales in O(N log N) time on startup,
 * separating Languages and Regions into independent O(1) indexed registries.
 */
public final class LocaleManager {
    private static final List<LocaleItem> ALL_LOCALES;
    private static final List<LocaleItem> UNIQUE_LANGUAGES;
    private static final List<RegionItem> UNIQUE_REGIONS;

    private static final Map<String, LocaleItem> INDEX;
    private static final Map<String, LocaleItem> LANGUAGE_INDEX;
    private static final Map<String, LocaleItem> LANGUAGE_FALLBACK;
    private static final Map<String, RegionItem> REGION_INDEX;

    public static final RegionItem DEFAULT_REGION;

    static {
        Locale[] rawLocales = Locale.getAvailableLocales();
        Set<String> seenTags = new HashSet<>();
        List<LocaleItem> list = new ArrayList<>();
        Map<String, LocaleItem> indexMap = new HashMap<>();
        Map<String, LocaleItem> fallbackMap = new HashMap<>();

        for (Locale l : rawLocales) {
            if (l == null) continue;
            String lang = l.getLanguage();
            if (lang == null || lang.trim().isEmpty() || "und".equalsIgnoreCase(lang)) {
                continue;
            }

            String tag = l.toLanguageTag();
            if (tag == null || tag.trim().isEmpty() || "und".equalsIgnoreCase(tag)) {
                continue;
            }

            // Deduplicate internal duplicates
            if (!seenTags.add(tag)) {
                continue;
            }

            String displayLang = capitalize(l.getDisplayLanguage(l));
            if (displayLang.isEmpty()) {
                displayLang = capitalize(l.getDisplayLanguage(Locale.ENGLISH));
            }
            if (displayLang.isEmpty()) {
                displayLang = lang;
            }

            String displayName = buildDisplayName(l, displayLang);
            LocaleItem item = new LocaleItem(tag, displayName, displayLang, l);
            list.add(item);
        }

        // Sort alphabetically by display name in O(N log N)
        list.sort((a, b) -> {
            int cmp = a.getDisplayName().compareToIgnoreCase(b.getDisplayName());
            if (cmp != 0) return cmp;
            return a.getTag().compareToIgnoreCase(b.getTag());
        });

        // Index in Map for O(1) lookup
        for (LocaleItem item : list) {
            String tag = item.getTag();
            String code = item.getCode();
            String display = item.getDisplayName();

            indexMap.put(tag, item);
            indexMap.put(tag.toLowerCase(Locale.ROOT), item);

            indexMap.put(code, item);
            indexMap.put(code.toLowerCase(Locale.ROOT), item);

            indexMap.put(display, item);
            indexMap.put(display.toLowerCase(Locale.ROOT), item);

            String baseLang = item.getLanguage().toLowerCase(Locale.ROOT);
            if (!fallbackMap.containsKey(baseLang) || isPreferredRegionalVariant(item)) {
                fallbackMap.put(baseLang, item);
            }
        }

        // Ensure default fallback is indexed
        indexMap.put(LocaleItem.DEFAULT.getTag(), LocaleItem.DEFAULT);
        indexMap.put(LocaleItem.DEFAULT.getCode(), LocaleItem.DEFAULT);

        ALL_LOCALES = Collections.unmodifiableList(list);
        INDEX = Collections.unmodifiableMap(indexMap);
        LANGUAGE_FALLBACK = Collections.unmodifiableMap(fallbackMap);

        // --- Build Independent Languages List ---
        Map<String, LocaleItem> langMap = new HashMap<>();
        for (LocaleItem item : list) {
            String langCode = item.getLanguage().toLowerCase(Locale.ROOT);
            if (!langMap.containsKey(langCode) || isPreferredRegionalVariant(item)) {
                Locale langOnlyLocale = new Locale(langCode);
                String display = capitalize(langOnlyLocale.getDisplayLanguage(langOnlyLocale));
                if (display.isEmpty()) {
                    display = capitalize(langOnlyLocale.getDisplayLanguage(Locale.ENGLISH));
                }
                if (display.isEmpty()) {
                    display = langCode;
                }
                LocaleItem pureLang = new LocaleItem(langCode, display, display, langOnlyLocale);
                langMap.put(langCode, pureLang);
            }
        }
        List<LocaleItem> languagesList = new ArrayList<>(langMap.values());
        languagesList.sort((a, b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName()));
        UNIQUE_LANGUAGES = Collections.unmodifiableList(languagesList);

        Map<String, LocaleItem> langIndex = new HashMap<>();
        for (LocaleItem lang : UNIQUE_LANGUAGES) {
            langIndex.put(lang.getTag(), lang);
            langIndex.put(lang.getTag().toLowerCase(Locale.ROOT), lang);
            langIndex.put(lang.getDisplayName().toLowerCase(Locale.ROOT), lang);
        }
        LANGUAGE_INDEX = Collections.unmodifiableMap(langIndex);

        // --- Build Independent Regions List ---
        String[] isoCountries = Locale.getISOCountries();
        List<RegionItem> regions = new ArrayList<>();
        Map<String, RegionItem> regionMap = new HashMap<>();

        for (String cCode : isoCountries) {
            if (cCode == null || cCode.trim().isEmpty()) continue;
            Locale cLocale = new Locale("", cCode);
            String countryName = cLocale.getDisplayCountry();
            if (countryName == null || countryName.trim().isEmpty()) {
                countryName = cLocale.getDisplayCountry(Locale.ENGLISH);
            }
            if (countryName == null || countryName.trim().isEmpty()) {
                countryName = cCode;
            }
            RegionItem rItem = new RegionItem(cCode, countryName, cLocale);
            regions.add(rItem);
            regionMap.put(cCode.toUpperCase(Locale.ROOT), rItem);
            regionMap.put(countryName.toLowerCase(Locale.ROOT), rItem);
        }

        regions.sort((a, b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName()));
        UNIQUE_REGIONS = Collections.unmodifiableList(regions);
        REGION_INDEX = Collections.unmodifiableMap(regionMap);

        RegionItem defR = regionMap.get("US");
        if (defR == null && !regions.isEmpty()) defR = regions.get(0);
        DEFAULT_REGION = defR != null ? defR : new RegionItem("US", "United States", Locale.US);
    }

    private LocaleManager() {}

    public static List<LocaleItem> getAllAvailableLocales() {
        return ALL_LOCALES;
    }

    public static List<LocaleItem> getAvailableLanguages() {
        return UNIQUE_LANGUAGES;
    }

    public static List<RegionItem> getAvailableRegions() {
        return UNIQUE_REGIONS;
    }

    public static LocaleItem getLanguage(String code) {
        if (code == null || code.trim().isEmpty()) {
            return LANGUAGE_INDEX.get("en") != null ? LANGUAGE_INDEX.get("en") : LocaleItem.DEFAULT;
        }
        String clean = code.trim().toLowerCase(Locale.ROOT);
        if (clean.contains("-")) {
            clean = clean.substring(0, clean.indexOf('-'));
        }
        LocaleItem match = LANGUAGE_INDEX.get(clean);
        if (match != null) return match;
        match = LANGUAGE_FALLBACK.get(clean);
        if (match != null) return match;
        return LANGUAGE_INDEX.get("en") != null ? LANGUAGE_INDEX.get("en") : LocaleItem.DEFAULT;
    }

    public static RegionItem getRegion(String code) {
        if (code == null || code.trim().isEmpty()) {
            return DEFAULT_REGION;
        }
        String clean = code.trim().toUpperCase(Locale.ROOT);
        if (clean.contains("-")) {
            clean = clean.substring(clean.lastIndexOf('-') + 1);
        } else if (clean.contains("_")) {
            clean = clean.substring(clean.lastIndexOf('_') + 1);
        }
        RegionItem match = REGION_INDEX.get(clean);
        if (match != null) return match;
        return DEFAULT_REGION;
    }

    public static LocaleItem getLocale(String code) {
        if (code == null || code.trim().isEmpty()) {
            return LocaleItem.DEFAULT;
        }

        String query = code.trim();
        LocaleItem match = INDEX.get(query);
        if (match != null) return match;

        String queryLower = query.toLowerCase(Locale.ROOT);
        match = INDEX.get(queryLower);
        if (match != null) return match;

        String tagFormat = query.replace('_', '-').toLowerCase(Locale.ROOT);
        match = INDEX.get(tagFormat);
        if (match != null) return match;

        String codeFormat = query.replace('-', '_').toLowerCase(Locale.ROOT);
        match = INDEX.get(codeFormat);
        if (match != null) return match;

        int sep = tagFormat.indexOf('-');
        String baseLang = (sep > 0 ? tagFormat.substring(0, sep) : tagFormat).toLowerCase(Locale.ROOT);
        match = LANGUAGE_FALLBACK.get(baseLang);
        if (match != null) return match;

        return LocaleItem.DEFAULT;
    }

    public static Locale getJavaLocale(String code) {
        return getLocale(code).getLocale();
    }

    public static int getAvailableCount() {
        return ALL_LOCALES.size();
    }

    private static String buildDisplayName(Locale l, String displayLang) {
        String displayCountry = capitalize(l.getDisplayCountry(l));
        String displayScript = capitalize(l.getDisplayScript(l));

        StringBuilder sb = new StringBuilder(displayLang);
        if (!displayScript.isEmpty() && !displayCountry.isEmpty()) {
            sb.append(" (").append(displayScript).append(", ").append(displayCountry).append(")");
        } else if (!displayCountry.isEmpty()) {
            sb.append(" (").append(displayCountry).append(")");
        } else if (!displayScript.isEmpty()) {
            sb.append(" (").append(displayScript).append(")");
        }
        return sb.toString();
    }

    private static String capitalize(String text) {
        if (text == null || text.isEmpty()) return "";
        int firstChar = text.codePointAt(0);
        return new StringBuilder(text.length())
                .appendCodePoint(Character.toUpperCase(firstChar))
                .append(text.substring(Character.charCount(firstChar)))
                .toString();
    }

    private static boolean isPreferredRegionalVariant(LocaleItem item) {
        String lang = item.getLanguage();
        String country = item.getCountry();
        if (country.isEmpty()) return true;

        if ("en".equalsIgnoreCase(lang) && "US".equalsIgnoreCase(country)) return true;
        if ("es".equalsIgnoreCase(lang) && "ES".equalsIgnoreCase(country)) return true;
        if ("de".equalsIgnoreCase(lang) && "DE".equalsIgnoreCase(country)) return true;
        if ("fr".equalsIgnoreCase(lang) && "FR".equalsIgnoreCase(country)) return true;
        if ("pl".equalsIgnoreCase(lang) && "PL".equalsIgnoreCase(country)) return true;
        if ("ja".equalsIgnoreCase(lang) && "JP".equalsIgnoreCase(country)) return true;
        if ("zh".equalsIgnoreCase(lang) && "CN".equalsIgnoreCase(country)) return true;
        return false;
    }
}
