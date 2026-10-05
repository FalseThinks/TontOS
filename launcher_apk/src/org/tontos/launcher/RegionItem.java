package org.tontos.launcher;

import java.util.Locale;

/**
 * Immutable value object representing a distinct geographical country/region for formatting.
 */
public final class RegionItem {
    private final String code;        // ISO 3166-1 alpha-2 (e.g., "US", "DE", "PL")
    private final String displayName; // Localized display name (e.g., "Germany", "Deutschland")
    private final Locale locale;

    public RegionItem(String code, String displayName, Locale locale) {
        this.code = code != null ? code.toUpperCase(Locale.ROOT) : "";
        this.displayName = displayName != null ? displayName : this.code;
        this.locale = locale != null ? locale : Locale.getDefault();
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Locale getLocale() {
        return locale;
    }

    @Override
    public String toString() {
        return displayName + " [" + code + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegionItem that = (RegionItem) o;
        return code.equalsIgnoreCase(that.code);
    }

    @Override
    public int hashCode() {
        return code.toUpperCase(Locale.ROOT).hashCode();
    }
}
