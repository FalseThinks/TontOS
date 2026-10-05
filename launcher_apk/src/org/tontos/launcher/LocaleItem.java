package org.tontos.launcher;

import java.util.Locale;
import java.util.Objects;

/**
 * Clean data model representing a supported device locale in TontOS.
 */
public final class LocaleItem {
    public static final LocaleItem DEFAULT = new LocaleItem("en-US", "English (United States)", "English", Locale.US);

    private final String tag;
    private final String displayName;
    private final String nativeName;
    private final Locale locale;

    public LocaleItem(String tag, String displayName, String nativeName, Locale locale) {
        this.tag = Objects.requireNonNull(tag, "tag cannot be null");
        this.displayName = Objects.requireNonNull(displayName, "displayName cannot be null");
        this.nativeName = nativeName != null && !nativeName.isEmpty() ? nativeName : displayName;
        this.locale = locale != null ? locale : Locale.forLanguageTag(tag);
    }

    public String getTag() {
        return tag;
    }

    /**
     * Legacy Android locale code format with underscore (e.g. "en_US").
     */
    public String getCode() {
        return tag.replace('-', '_');
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getNativeName() {
        return nativeName;
    }

    public Locale getLocale() {
        return locale;
    }

    public String getLanguage() {
        return locale.getLanguage();
    }

    public String getCountry() {
        return locale.getCountry();
    }

    @Override
    public String toString() {
        return displayName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocaleItem that = (LocaleItem) o;
        return tag.equalsIgnoreCase(that.tag);
    }

    @Override
    public int hashCode() {
        return tag.toLowerCase(Locale.ROOT).hashCode();
    }
}
