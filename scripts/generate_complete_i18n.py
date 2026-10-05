import json, re

with open("/tmp/base_langs.json") as f:
    data = json.load(f)

# Sorted list of keys based on en
keys_ordered = list(data["en"].keys())

LANGS = ["en", "de", "pl", "es", "fr", "it", "pt", "ru", "uk", "zh", "ja", "nl"]
LANG_NAMES = {
    "en": "ENGLISH (en)",
    "de": "GERMAN (de)",
    "pl": "POLISH (pl)",
    "es": "SPANISH (es)",
    "fr": "FRENCH (fr)",
    "it": "ITALIAN (it)",
    "pt": "PORTUGUESE (pt)",
    "ru": "RUSSIAN (ru)",
    "uk": "UKRAINIAN (uk)",
    "zh": "CHINESE (zh)",
    "ja": "JAPANESE (ja)",
    "nl": "DUTCH (nl)"
}

out = []
out.append("package org.tontos.launcher;")
out.append("")
out.append("import android.content.Context;")
out.append("import android.content.res.Configuration;")
out.append("import android.content.res.Resources;")
out.append("import java.util.HashMap;")
out.append("import java.util.Locale;")
out.append("import java.util.Map;")
out.append("")
out.append("/**")
out.append(" * Universal Internationalization & UI Localization Engine for TontOS.")
out.append(" * Provides instant in-memory translations for all UI keys across all supported languages,")
out.append(" * and updates Android runtime Resources configuration on demand.")
out.append(" */")
out.append("public final class I18n {")
out.append("")
out.append("    // ----------------------------------------------------")
out.append("    // String Constant Keys (139 verified UI keys)")
out.append("    // ----------------------------------------------------")

for k in keys_ordered:
    raw_val = k.replace("KEY_", "").lower()
    out.append(f'    public static final String {k} = "{raw_val}";')

out.append("")
out.append("    // Compatibility Aliases")
out.append("    public static final String KEY_EQ_BAND_3600HZ = KEY_EQ_BAND_3_6KHZ;")
out.append("    public static final String KEY_EQ_BAND_1 = KEY_EQ_BAND_60HZ;")
out.append("    public static final String KEY_EQ_BAND_2 = KEY_EQ_BAND_230HZ;")
out.append("    public static final String KEY_EQ_BAND_3 = KEY_EQ_BAND_910HZ;")
out.append("    public static final String KEY_EQ_BAND_4 = KEY_EQ_BAND_3_6KHZ;")
out.append("    public static final String KEY_EQ_BAND_5 = KEY_EQ_BAND_14KHZ;")
out.append("")
out.append("    private static final Map<String, Map<String, String>> TRANSLATIONS = new HashMap<>();")
out.append("")
out.append("    static {")

def escape_java(s):
    # Escape quotes and backslashes for Java string literal
    res = s.replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n')
    return res

for lang in LANGS:
    out.append(f"        // ==========================================")
    out.append(f"        // --- {LANG_NAMES[lang]} ---")
    out.append(f"        // ==========================================")
    out.append(f"        Map<String, String> {lang} = new HashMap<>();")
    l_map = data[lang]
    for k in keys_ordered:
        val = l_map.get(k, data["en"][k])
        escaped = escape_java(val)
        out.append(f'        {lang}.put({k}, "{escaped}");')
    out.append(f'        TRANSLATIONS.put("{lang}", {lang});')
    out.append("")

out.append("    }")
out.append("")
out.append("    private I18n() {}")
out.append("")
out.append("    /**")
out.append("     * Resolves a translated UI string for the currently set active language.")
out.append("     * If not found, falls back safely to English.")
out.append("     */")
out.append("    public static String get(String key, String langCode) {")
out.append("        if (langCode != null) {")
out.append("            String base = langCode.contains(\"-\") ? langCode.substring(0, langCode.indexOf('-')) : langCode;")
out.append("            base = base.toLowerCase(Locale.ROOT);")
out.append("            Map<String, String> langMap = TRANSLATIONS.get(base);")
out.append("            if (langMap != null && langMap.containsKey(key)) {")
out.append("                return langMap.get(key);")
out.append("            }")
out.append("        }")
out.append("        Map<String, String> en = TRANSLATIONS.get(\"en\");")
out.append("        if (en != null && en.containsKey(key)) {")
out.append("            return en.get(key);")
out.append("        }")
out.append("        return key;")
out.append("    }")
out.append("")
out.append("    /**")
out.append("     * Resolves a translated UI string formatted with arguments using the target locale.")
out.append("     */")
out.append("    public static String format(String key, String langCode, Object... args) {")
out.append("        String pattern = get(key, langCode);")
out.append("        try {")
out.append("            Locale locale = (langCode != null && !langCode.isEmpty())")
out.append("                ? (langCode.contains(\"-\") ? new Locale(langCode.substring(0, langCode.indexOf('-'))) : new Locale(langCode))")
out.append("                : Locale.getDefault();")
out.append("            return String.format(locale, pattern, args);")
out.append("        } catch (Exception e) {")
out.append("            return String.format(pattern, args);")
out.append("        }")
out.append("    }")
out.append("")
out.append("    /**")
out.append("     * Updates Android runtime Context Resources and default Locale with the specified")
out.append("     * language and region, enabling automatic date/time/number localization.")
out.append("     */")
out.append("    public static void applyLocale(Context context, String languageCode, String regionCode) {")
out.append("        if (languageCode == null || languageCode.isEmpty()) languageCode = \"en\";")
out.append("        if (regionCode == null) regionCode = \"\";")
out.append("")
out.append("        Locale targetLocale;")
out.append("        if (!regionCode.isEmpty()) {")
out.append("            targetLocale = new Locale(languageCode, regionCode);")
out.append("        } else {")
out.append("            targetLocale = new Locale(languageCode);")
out.append("        }")
out.append("")
out.append("        Locale.setDefault(targetLocale);")
out.append("        if (context != null) {")
out.append("            Resources res = context.getResources();")
out.append("            Configuration config = new Configuration(res.getConfiguration());")
out.append("            config.setLocale(targetLocale);")
out.append("            res.updateConfiguration(config, res.getDisplayMetrics());")
out.append("        }")
out.append("    }")
out.append("}")
out.append("")

java_code = "\n".join(out)

with open("launcher_apk/src/org/tontos/launcher/I18n.java", "w", encoding="utf-8") as f:
    f.write(java_code)

print("Generated launcher_apk/src/org/tontos/launcher/I18n.java successfully.")
print("Total lines:", len(out))
