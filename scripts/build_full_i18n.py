import json, os

with open("/tmp/base_langs.json") as f:
    data = json.load(f)

NEW_KEYS = {
    "KEY_NFC_RADIO": {
        "en": "NFC (Near Field Communication)",
        "es": "NFC (Comunicación de campo cercano)",
        "de": "NFC (Nahfeldkommunikation)",
        "pl": "NFC (Komunikacja zbliżeniowa)",
        "fr": "NFC (Communication en champ proche)",
        "it": "NFC (Comunicazione a corto raggio)",
        "pt": "NFC (Comunicação por proximidade)",
        "ru": "NFC (Бесконтактная связь)",
        "uk": "NFC (Безконтактний зв'язок)",
        "zh": "NFC（近场通信）",
        "ja": "NFC（近距離無線通信）",
        "nl": "NFC (Draadloze communicatie)"
    },
    "KEY_SCREEN_TIME_RESET_OK": {
        "en": "OK: Screen time reset to 0m",
        "es": "OK: Tiempo en pantalla restablecido a 0m",
        "de": "OK: Bildschirmzeit auf 0m zurückgesetzt",
        "pl": "OK: Czas ekranowy zresetowany do 0m",
        "fr": "OK : Temps d'écran réinitialisé à 0m",
        "it": "OK: Tempo di utilizzo azzerato a 0m",
        "pt": "OK: Tempo de ecrã reposto a 0m",
        "ru": "OK: Экранное время сброшено на 0м",
        "uk": "OK: Екранний час скинуто на 0хв",
        "zh": "OK：屏幕时间已重置为0分钟",
        "ja": "OK: スクリーンタイムを0分にリセットしました",
        "nl": "OK: Schermtijd gereset naar 0m"
    },
    "KEY_SCREEN_TIME_RESET_TITLE": {
        "en": "SCREEN TIME RESET",
        "es": "TIEMPO EN PANTALLA",
        "de": "BILDSCHIRMZEIT",
        "pl": "CZAS EKRANOWY",
        "fr": "TEMPS D'ÉCRAN",
        "it": "TEMPO DI UTILIZZO",
        "pt": "TEMPO DE ECRÃ",
        "ru": "ЭКРАННОЕ ВРЕМЯ",
        "uk": "ЕКРАННИЙ ЧАС",
        "zh": "屏幕使用时间",
        "ja": "スクリーンタイム",
        "nl": "SCHERMTIJD"
    },
    "KEY_FONT_XS": {
        "en": "Extra Small (70%)",
        "es": "Muy pequeña (70%)",
        "de": "Sehr klein (70%)",
        "pl": "Bardzo mała (70%)",
        "fr": "Très petite (70%)",
        "it": "Molto piccolo (70%)",
        "pt": "Muito pequeno (70%)",
        "ru": "Очень мелкий (70%)",
        "uk": "Дуже дрібний (70%)",
        "zh": "极小 (70%)",
        "ja": "極小 (70%)",
        "nl": "Zeer klein (70%)"
    },
    "KEY_FONT_XXL": {
        "en": "Extra Extra Large (150%)",
        "es": "Gigante (150%)",
        "de": "Sehr groß (150%)",
        "pl": "Bardzo duża (150%)",
        "fr": "Très grande (150%)",
        "it": "Molto grande (150%)",
        "pt": "Muito grande (150%)",
        "ru": "Очень крупный (150%)",
        "uk": "Дуже великий (150%)",
        "zh": "极大 (150%)",
        "ja": "特大 (150%)",
        "nl": "Zeer groot (150%)"
    },
    "KEY_FONT_MAX": {
        "en": "Maximum (175%)",
        "es": "Máxima (175%)",
        "de": "Maximal (175%)",
        "pl": "Maksymalna (175%)",
        "fr": "Maximale (175%)",
        "it": "Massimo (175%)",
        "pt": "Máximo (175%)",
        "ru": "Максимальный (175%)",
        "uk": "Максимальний (175%)",
        "zh": "最大 (175%)",
        "ja": "最大 (175%)",
        "nl": "Maximaal (175%)"
    },
    "KEY_BG_POLICY": {
        "en": "Background Apps Policy",
        "es": "Política en segundo plano",
        "de": "Hintergrund-Richtlinie",
        "pl": "Zasady pracy w tle",
        "fr": "Politique d'arrière-plan",
        "it": "Criteri processi in background",
        "pt": "Política de segundo plano",
        "ru": "Политика работы в фоне",
        "uk": "Політика роботи у фоні",
        "zh": "后台运行策略",
        "ja": "バックグラウンド実行ポリシー",
        "nl": "Achtergrondbeleid"
    },
    "KEY_BG_STRICT": {
        "en": "Strict (All blocked)",
        "es": "Estricta (Todas bloqueadas)",
        "de": "Strikt (Alle blockiert)",
        "pl": "Rygorystyczne (Wszystkie zablokowane)",
        "fr": "Stricte (Toutes bloquées)",
        "it": "Severo (Tutte bloccate)",
        "pt": "Estrita (Todas bloqueadas)",
        "ru": "Строгая (Все заблокированы)",
        "uk": "Сувора (Всі заблоковані)",
        "zh": "严格模式（全部禁止后台）",
        "ja": "厳格モード（すべて禁止）",
        "nl": "Strikt (Alles geblokkeerd)"
    },
    "KEY_BG_WHITELIST": {
        "en": "Whitelist (Default: 0 authorized)",
        "es": "Lista blanca (Por defecto: 0 autorizadas)",
        "de": "Whitelist (Standard: 0 autorisiert)",
        "pl": "Biała lista (Domyślnie: 0 autoryzowanych)",
        "fr": "Liste blanche (Par défaut : 0 autorisée)",
        "it": "Whitelist (Predefinito: 0 autorizzate)",
        "pt": "Lista branca (Padrão: 0 autorizadas)",
        "ru": "Белый список (По умолч.: 0 разрешено)",
        "uk": "Білий список (За замовч.: 0 дозволено)",
        "zh": "白名单模式（默认：0个应用允许）",
        "ja": "ホワイトリスト（デフォルト: 0件許可）",
        "nl": "Witte lijst (Standaard: 0 geautoriseerd)"
    },
    "KEY_BG_ALLOWED_ALL": {
        "en": "Unrestricted (All allowed)",
        "es": "Sin restricciones (Todas permitidas)",
        "de": "Uneingeschränkt (Alle erlaubt)",
        "pl": "Bez ograniczeń (Wszystkie dozwolone)",
        "fr": "Sans restriction (Toutes autorisées)",
        "it": "Senza restrizioni (Tutte permesse)",
        "pt": "Sem restrições (Todas permitidas)",
        "ru": "Без ограничений (Все разрешены)",
        "uk": "Без обмежень (Всі дозволені)",
        "zh": "无限制模式（全部允许）",
        "ja": "無制限（すべて許可）",
        "nl": "Onbeperkt (Alles toegestaan)"
    },
    "KEY_BG_APPS_CONFIG": {
        "en": "Background App Permissions",
        "es": "Permisos de apps en segundo plano",
        "de": "Hintergrundberechtigungen für Apps",
        "pl": "Uprawnienia aplikacji w tle",
        "fr": "Autorisations d'arrière-plan des applications",
        "it": "Permessi app in background",
        "pt": "Permissões de apps em segundo plano",
        "ru": "Разрешения приложений в фоне",
        "uk": "Дозволи програм у фоні",
        "zh": "应用后台运行权限",
        "ja": "アプリのバックグラウンド権限",
        "nl": "Achtergrondmachtigingen van apps"
    },
    "KEY_BG_BLOCKED_DEFAULT": {
        "en": "BLOCKED (Default)",
        "es": "BLOQUEADA (Por defecto)",
        "de": "BLOCKIERT (Standard)",
        "pl": "ZABLOKOWANA (Domyślnie)",
        "fr": "BLOQUÉE (Par défaut)",
        "it": "BLOCCATA (Predefinito)",
        "pt": "BLOQUEADA (Padrão)",
        "ru": "ЗАБЛОКИРОВАНО (По умолч.)",
        "uk": "ЗАБЛОКОВАНО (За замовч.)",
        "zh": "已禁止（默认）",
        "ja": "禁止中（デフォルト）",
        "nl": "GEBLOKKEERD (Standaard)"
    },
    "KEY_BG_ALLOWED": {
        "en": "ALLOWED",
        "es": "PERMITIDA",
        "de": "ERLAUBT",
        "pl": "DOZWOLONA",
        "fr": "AUTORISÉE",
        "it": "PERMESSA",
        "pt": "PERMITIDA",
        "ru": "РАЗРЕШЕНО",
        "uk": "ДОЗВОЛЕНО",
        "zh": "已允许",
        "ja": "許可",
        "nl": "TOEGESTAAN"
    },
    "KEY_BG_RESTRICTED": {
        "en": "RESTRICTED",
        "es": "RESTRINGIDA",
        "de": "EINGESCHRÄNKT",
        "pl": "OGRANICZONA",
        "fr": "RESTREINTE",
        "it": "LIMITATA",
        "pt": "RESTRITA",
        "ru": "ОГРАНИЧЕНО",
        "uk": "ОБМЕЖЕНО",
        "zh": "已限制",
        "ja": "制限付き",
        "nl": "BEPERKT"
    },
    "KEY_THEME_SELECT_TITLE": {
        "en": "SELECT THEME PRESET",
        "es": "SELECCIONAR TEMA",
        "de": "THEMEN-VOREINSTELLUNG WÄHLEN",
        "pl": "WYBIERZ MOTYW",
        "fr": "CHOISIR LE THÈME",
        "it": "SELEZIONA TEMA",
        "pt": "SELECIONAR TEMA",
        "ru": "ВЫБЕРИТЕ ТЕМУ",
        "uk": "ВИБЕРІТЬ ТЕМУ",
        "zh": "选择主题预设",
        "ja": "テーマプリセットを選択",
        "nl": "SELECTEER THEMA"
    },
    "KEY_THEME_AMOLED_DESC": {
        "en": "Emerald on pure black (photopic efficiency peak)",
        "es": "Esmeralda sobre negro puro (máxima eficiencia fotópica)",
        "de": "Smaragd auf echtem Schwarz (höchste Effizienz)",
        "pl": "Szmaragd na czystej czerni (szczytowa wydajność)",
        "fr": "Émeraude sur noir pur (efficacité photopique maximale)",
        "it": "Smeraldo su nero puro (massima efficienza fotopica)",
        "pt": "Esmeralda em preto puro (pico de eficiência fotópica)",
        "ru": "Изумруд на глубоком черном (пик фотопической эффективности)",
        "uk": "Смарагд на глибокому чорному (пік фотопічної ефективності)",
        "zh": "纯黑背景上的翠绿色（最高视觉效率）",
        "ja": "純黒に映えるエメラルド（最高の視覚効率）",
        "nl": "Smaragd op puur zwart (piek van fotopische efficiëntie)"
    },
    "KEY_THEME_AMBER_DESC": {
        "en": "0% blue light, ideal for night reading",
        "es": "0% luz azul, ideal para lectura nocturna",
        "de": "0% Blaulicht, ideal zum nächtlichen Lesen",
        "pl": "0% światła niebieskiego, idealny do czytania w nocy",
        "fr": "0% lumière bleue, idéal pour la lecture nocturne",
        "it": "0% luce blu, ideale per la lettura notturna",
        "pt": "0% de luz azul, ideal para leitura noturna",
        "ru": "0% синего света, идеально для чтения ночью",
        "uk": "0% синього світла, ідеально для нічного читання",
        "zh": "0%蓝光，夜间阅读的理想选择",
        "ja": "ブルーライト0%、夜間読書に最適",
        "nl": "0% blauw licht, ideaal voor 's nachts lezen"
    },
    "KEY_THEME_E_INK_DESC": {
        "en": "Daylight high-contrast monochrome",
        "es": "Monocromo de alto contraste para luz diurna",
        "de": "Tageslicht-Monochrom mit hohem Kontrast",
        "pl": "Wysokokontrastowy monochrom na światło dzienne",
        "fr": "Monochrome à fort contraste pour lumière du jour",
        "it": "Monocromatico ad alto contrasto per la luce del giorno",
        "pt": "Monocromático de alto contraste para a luz do dia",
        "ru": "Дневной высококонтрастный монохром",
        "uk": "Денний висококонтрастний монохром",
        "zh": "日光高对比度黑白显示",
        "ja": "昼間の高コントラストモノクロ",
        "nl": "Hoog contrast monochroom voor daglicht"
    },
    "KEY_THEME_SLATE_DESC": {
        "en": "Deep midnight slate with ice blue accents",
        "es": "Pizarra medianoche con acentos azul hielo",
        "de": "Tiefes Mitternachtsschiefer mit eisblauen Akzenten",
        "pl": "Głęboki łupek z lodowatymi akcentami",
        "fr": "Ardoise nuit profonde avec accents bleu glacier",
        "it": "Ardesia notte profonda con accenti blu ghiaccio",
        "pt": "Ardósia meia-noite profunda com detalhes azul gelo",
        "ru": "Глубокий полуночный сланец с ледяными акцентами",
        "uk": "Глибокий опівнічний сланець із крижаними акцентами",
        "zh": "深邃午夜暗岩配冰蓝点缀",
        "ja": "ディープミッドナイトスレートとアイスブルーアクセント",
        "nl": "Diep middernachtleisteen met ijsblauwe accenten"
    },
    "KEY_NO_APPS_MATCH_FMT": {
        "en": "No applications matching \"%s\"",
        "es": "No hay aplicaciones que coincidan con \"%s\"",
        "de": "Keine Anwendungen passend zu \"%s\"",
        "pl": "Brak aplikacji pasujących do \"%s\"",
        "fr": "Aucune application correspondant à \"%s\"",
        "it": "Nessuna applicazione corrispondente a \"%s\"",
        "pt": "Nenhum aplicativo correspondente a \"%s\"",
        "ru": "Нет приложений, соответствующих \"%s\"",
        "uk": "Немає програм, що відповідають \"%s\"",
        "zh": "没有匹配 \"%s\" 的应用",
        "ja": "\"%s\" に一致するアプリはありません",
        "nl": "Geen applicaties gevonden die overeenkomen met \"%s\""
    },
    "KEY_NO_APPS_PINNED": {
        "en": "No apps pinned (All in drawer)",
        "es": "Sin apps fijadas (Todas en el cajón)",
        "de": "Keine Apps angeheftet (Alle im Drawer)",
        "pl": "Brak przypiętych aplikacji (Wszystkie w menu)",
        "fr": "Aucune application épinglée (Toutes dans le tiroir)",
        "it": "Nessuna app bloccata (Tutte nel drawer)",
        "pt": "Nenhuma app fixada (Todas na gaveta)",
        "ru": "Нет закрепленных приложений (Все в меню)",
        "uk": "Немає закріплених програм (Всі в меню)",
        "zh": "未固定应用（全部在抽屉中）",
        "ja": "固定されたアプリはありません（すべてドロワー内）",
        "nl": "Geen apps vastgemaakt (Alles in lade)"
    },
    "KEY_ALL_APPS_PINNED": {
        "en": "All installed apps are pinned",
        "es": "Todas las apps instaladas están fijadas",
        "de": "Alle installierten Apps sind angeheftet",
        "pl": "Wszystkie zainstalowane aplikacje są przypięte",
        "fr": "Toutes les applications installées sont épinglées",
        "it": "Tutte le app installate sono bloccate",
        "pt": "Todas as apps instaladas estão fixadas",
        "ru": "Все установленные приложения закреплены",
        "uk": "Всі встановлені програми закріплені",
        "zh": "已安装的所有应用均已固定",
        "ja": "インストール済みのすべてのアプリが固定されています",
        "nl": "Alle geïnstalleerde apps zijn vastgemaakt"
    },
    "KEY_SELECT_OPTION": {
        "en": "SELECT OPTION",
        "es": "SELECCIONAR OPCIÓN",
        "de": "OPTION WÄHLEN",
        "pl": "WYBIERZ OPCJĘ",
        "fr": "CHOISIR UNE OPTION",
        "it": "SELEZIONA OPZIONE",
        "pt": "SELECIONAR OPÇÃO",
        "ru": "ВЫБЕРИТЕ ВАРИАНТ",
        "uk": "ВИБЕРІТЬ ВАРІАНТ",
        "zh": "选择选项",
        "ja": "オプションを選択",
        "nl": "SELECTEER OPTIE"
    },
    "KEY_NOT_SUPPORTED": {
        "en": "Not supported on this device",
        "es": "No compatible con este dispositivo",
        "de": "Nicht unterstützt auf diesem Gerät",
        "pl": "Brak wsparcia na tym urządzeniu",
        "fr": "Non pris en charge sur cet appareil",
        "it": "Non supportato su questo dispositivo",
        "pt": "Não suportado neste dispositivo",
        "ru": "Не поддерживается на этом устройстве",
        "uk": "Не підтримується на цьому пристрої",
        "zh": "当前设备不支持",
        "ja": "このデバイスではサポートされていません",
        "nl": "Niet ondersteund op dit apparaat"
    },
    "KEY_GITHUB_OTA": {
        "en": "GitHub OTA Updates",
        "es": "Actualizaciones GitHub OTA",
        "de": "GitHub OTA-Updates",
        "pl": "Aktualizacje GitHub OTA",
        "fr": "Mises à jour GitHub OTA",
        "it": "Aggiornamenti GitHub OTA",
        "pt": "Atualizações GitHub OTA",
        "ru": "Обновления GitHub OTA",
        "uk": "Оновлення GitHub OTA",
        "zh": "GitHub OTA 系统更新",
        "ja": "GitHub OTA システム更新",
        "nl": "GitHub OTA-updates"
    },
    "KEY_CHECKING_OTA": {
        "en": "Checking OTA for",
        "es": "Comprobando OTA para",
        "de": "Prüfe OTA für",
        "pl": "Sprawdzanie OTA dla",
        "fr": "Vérification OTA pour",
        "it": "Controllo OTA per",
        "pt": "A verificar OTA para",
        "ru": "Проверка OTA для",
        "uk": "Перевірка OTA для",
        "zh": "正在检查 OTA 更新：",
        "ja": "OTA を確認中: ",
        "nl": "OTA controleren voor"
    },
    "KEY_BACKUP_SAVED": {
        "en": "Backup saved to:",
        "es": "Copia guardada en:",
        "de": "Sicherung gespeichert in:",
        "pl": "Kopia zapisana w:",
        "fr": "Sauvegarde enregistrée dans :",
        "it": "Backup salvato in:",
        "pt": "Cópia guardada em:",
        "ru": "Копия сохранена в:",
        "uk": "Копію збережено в:",
        "zh": "备份已保存至：",
        "ja": "バックアップ保存先: ",
        "nl": "Back-up opgeslagen in:"
    },
    "KEY_BACKUP_RESTORED": {
        "en": "Backup restored successfully!",
        "es": "¡Copia restaurada con éxito!",
        "de": "Sicherung erfolgreich wiederhergestellt!",
        "pl": "Kopia przywrócona pomyślnie!",
        "fr": "Sauvegarde restaurée avec succès !",
        "it": "Backup ripristinato con successo!",
        "pt": "Cópia restaurada com sucesso!",
        "ru": "Резервная копия успешно восстановлена!",
        "uk": "Резервну копію успішно відновлено!",
        "zh": "备份恢复成功！",
        "ja": "バックアップが正常に復元されました！",
        "nl": "Back-up succesvol hersteld!"
    },
    "KEY_NO_BACKUP_FOUND": {
        "en": "No backup file found",
        "es": "No se encontró archivo de copia",
        "de": "Keine Sicherungsdatei gefunden",
        "pl": "Nie znaleziono pliku kopii zapasowej",
        "fr": "Aucun fichier de sauvegarde trouvé",
        "it": "Nessun file di backup trovato",
        "pt": "Nenhum ficheiro de cópia encontrado",
        "ru": "Файл резервной копии не найден",
        "uk": "Файл резервної копії не знайдено",
        "zh": "未找到备份文件",
        "ja": "バックアップファイルが見つかりません",
        "nl": "Geen back-upbestand gevonden"
    },
    "KEY_APP_BROWSER": {
        "en": "FOSS Browser",
        "es": "Navegador FOSS",
        "de": "FOSS Browser",
        "pl": "Przeglądarka FOSS",
        "fr": "Navigateur FOSS",
        "it": "Browser FOSS",
        "pt": "Navegador FOSS",
        "ru": "Браузер FOSS",
        "uk": "Браузер FOSS",
        "zh": "FOSS 极简浏览器",
        "ja": "FOSS ブラウザ",
        "nl": "FOSS Browser"
    }
}

# Merge into data
for k, trans in NEW_KEYS.items():
    for lang, val in trans.items():
        if lang in data:
            data[lang][k] = val

# Also ensure KEY_CANCEL in es is "[ CANCELAR ]"
data["es"]["KEY_CANCEL"] = "[ CANCELAR ]"
data["es"]["KEY_ACTION_RUN"] = "EJECUTAR"
data["es"]["KEY_ACTION_RESTORE"] = "RESTAURAR"
data["es"]["KEY_CONFIRM_RUN_TITLE"] = "ASISTENTE DE CONFIGURACIÓN"
data["es"]["KEY_CONFIRM_WIZARD_MSG"] = "¿Volver a ejecutar el asistente inicial?"

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
out.append(f"    // ----------------------------------------------------")
out.append(f"    // String Constant Keys ({len(keys_ordered)} verified UI keys)")
out.append(f"    // ----------------------------------------------------")

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

for lang in LANGS:
    name = LANG_NAMES[lang]
    out.append(f"        // ==========================================")
    out.append(f"        // --- {name} ---")
    out.append(f"        // ==========================================")
    out.append(f"        Map<String, String> {lang} = new HashMap<>();")
    for k in keys_ordered:
        val = data[lang].get(k, data["en"].get(k, ""))
        # Escape string for java
        val_escaped = val.replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n')
        out.append(f'        {lang}.put({k}, "{val_escaped}");')
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
out.append("     * Updates Android runtime Context Resources, default Locale, and fontScale.")
out.append("     */")
out.append("    public static void applyLocale(Context context, String languageCode, String regionCode) {")
out.append("        applyLocaleAndScale(context, languageCode, regionCode, 1.0f);")
out.append("    }")
out.append("")
out.append("    public static void applyLocaleAndScale(Context context, String languageCode, String regionCode, float fontScale) {")
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
out.append("            if (fontScale > 0.1f) {")
out.append("                config.fontScale = fontScale;")
out.append("            }")
out.append("            res.updateConfiguration(config, res.getDisplayMetrics());")
out.append("        }")
out.append("    }")
out.append("}")
out.append("")

java_code = "\n".join(out)
target_path = "/home/paproka/Desktop/TontoOS/launcher_apk/src/org/tontos/launcher/I18n.java"
with open(target_path, "w") as f:
    f.write(java_code)

print(f"Generated {len(keys_ordered)} keys across {len(LANGS)} languages in {target_path}")
