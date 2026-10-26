package org.tontos.launcher;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Handles OTA update discovery against GitHub Releases.
 * Queries GitHub API asynchronously, compares semver tags, and displays monospace update dialogs.
 */
public final class OtaUpdateManager {

    private static final String GITHUB_API_RELEASES_URL = "https://api.github.com/repos/FalseThinks/TontOS/releases";
    private static final String GITHUB_API_LATEST_URL = "https://api.github.com/repos/FalseThinks/TontOS/releases/latest";
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 10000;
    private static final int MAX_RESPONSE_BYTES = 64 * 1024; // 64 KB ceiling

    public static class ReleaseInfo {
        public final String tagName;
        public final String title;
        public final String body;
        public final String htmlUrl;
        public final String apkUrl;
        public final long apkSize;
        public final boolean isPrerelease;

        public ReleaseInfo(String tagName, String title, String body, String htmlUrl, String apkUrl, long apkSize, boolean isPrerelease) {
            this.tagName = tagName;
            this.title = title;
            this.body = body;
            this.htmlUrl = htmlUrl;
            this.apkUrl = apkUrl;
            this.apkSize = apkSize;
            this.isPrerelease = isPrerelease;
        }
    }

    private OtaUpdateManager() {}

    public static String getInstalledVersion(Context context) {
        if (context == null) return "0.0.4";
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Throwable ignored) {
            return "0.0.4";
        }
    }

    /**
     * Compares semantic versions (e.g. "0.0.2", "v0.0.1", "0.0.1-alpha").
     * Returns > 0 if v1 > v2, < 0 if v1 < v2, and 0 if equal.
     */
    public static int compareVersions(String v1, String v2) {
        if (v1 == null && v2 == null) return 0;
        if (v1 == null) return -1;
        if (v2 == null) return 1;

        String clean1 = v1.trim();
        if (clean1.startsWith("v") || clean1.startsWith("V")) clean1 = clean1.substring(1);
        String clean2 = v2.trim();
        if (clean2.startsWith("v") || clean2.startsWith("V")) clean2 = clean2.substring(1);

        // Strip pre-release suffix for core number comparison
        String[] parts1 = clean1.split("-")[0].split("\\.");
        String[] parts2 = clean2.split("-")[0].split("\\.");

        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int num1 = 0;
            if (i < parts1.length) {
                try { num1 = Integer.parseInt(parts1[i]); } catch (Throwable ignored) {}
            }
            int num2 = 0;
            if (i < parts2.length) {
                try { num2 = Integer.parseInt(parts2[i]); } catch (Throwable ignored) {}
            }
            if (num1 != num2) {
                return Integer.compare(num1, num2);
            }
        }
        return 0;
    }

    /**
     * Queries GitHub asynchronously and triggers UI callback on main thread.
     */
    public static void checkForUpdates(
        final Activity activity,
        final ThemeTokens tokens,
        final UpdateChannel channel,
        final String langCode
    ) {
        if (activity == null || activity.isFinishing()) return;

        final Handler mainHandler = new Handler(Looper.getMainLooper());
        final String curVersion = getInstalledVersion(activity);

        String checkingMsg = I18n.format(I18n.KEY_OTA_CHECKING_FMT, langCode, channel.getCode());
        Toast.makeText(activity, checkingMsg, Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String targetUrl = (channel == UpdateChannel.RELEASE)
                        ? GITHUB_API_LATEST_URL
                        : GITHUB_API_RELEASES_URL;

                    URL url = new URL(targetUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                    conn.setReadTimeout(READ_TIMEOUT_MS);
                    conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                    conn.setRequestProperty("User-Agent", "TontOS-OTA/" + curVersion);

                    int responseCode = conn.getResponseCode();
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        final String errMsg = "HTTP " + responseCode + ": " + conn.getResponseMessage();
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (activity.isFinishing()) return;
                                showErrorDialog(activity, tokens, langCode, errMsg);
                            }
                        });
                        return;
                    }

                    InputStream in = conn.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    char[] buffer = new char[1024];
                    int read;
                    while ((read = reader.read(buffer)) != -1) {
                        sb.append(buffer, 0, read);
                        if (sb.length() > MAX_RESPONSE_BYTES) break;
                    }
                    reader.close();
                    conn.disconnect();

                    String jsonStr = sb.toString().trim();
                    ReleaseInfo targetRelease = null;

                    if (jsonStr.startsWith("[")) {
                        JSONArray array = new JSONArray(jsonStr);
                        for (int i = 0; i < array.length(); i++) {
                            JSONObject obj = array.optJSONObject(i);
                            if (obj == null) continue;
                            boolean prerelease = obj.optBoolean("prerelease", false);
                            String tag = obj.optString("tag_name", "");

                            if (channel == UpdateChannel.RELEASE && !prerelease) {
                                targetRelease = parseRelease(obj);
                                break;
                            } else if (channel == UpdateChannel.BETA && (prerelease || tag.toLowerCase().contains("beta") || tag.toLowerCase().contains("alpha"))) {
                                targetRelease = parseRelease(obj);
                                break;
                            }

                        }
                        if (targetRelease == null && array.length() > 0) {
                            targetRelease = parseRelease(array.optJSONObject(0));
                        }
                    } else if (jsonStr.startsWith("{")) {
                        JSONObject obj = new JSONObject(jsonStr);
                        targetRelease = parseRelease(obj);
                    }

                    final ReleaseInfo finalRelease = targetRelease;
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (activity.isFinishing()) return;
                            if (finalRelease == null) {
                                showUpToDateDialog(activity, tokens, langCode, curVersion, channel, null);
                            } else {
                                int cmp = compareVersions(finalRelease.tagName, curVersion);
                                if (cmp > 0) {
                                    showNewVersionDialog(activity, tokens, langCode, curVersion, finalRelease, channel);
                                } else {
                                    showUpToDateDialog(activity, tokens, langCode, curVersion, channel, finalRelease.tagName);
                                }
                            }
                        }
                    });

                } catch (final Throwable t) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (activity.isFinishing()) return;
                            showErrorDialog(activity, tokens, langCode, t.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private static ReleaseInfo parseRelease(JSONObject obj) {
        if (obj == null) return null;
        String tagName = obj.optString("tag_name", "");
        String title = obj.optString("name", tagName);
        String body = obj.optString("body", "");
        String htmlUrl = obj.optString("html_url", "");
        boolean prerelease = obj.optBoolean("prerelease", false);

        String apkUrl = null;
        long apkSize = 0;
        JSONArray assets = obj.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset != null) {
                    String name = asset.optString("name", "");
                    if (name.endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url", null);
                        apkSize = asset.optLong("size", 0);
                        break;
                    }
                }
            }
        }
        return new ReleaseInfo(tagName, title, body, htmlUrl, apkUrl, apkSize, prerelease);
    }

    private static void showNewVersionDialog(
        final Activity activity,
        final ThemeTokens tokens,
        final String langCode,
        final String curVersion,
        final ReleaseInfo release,
        final UpdateChannel channel
    ) {
        if (activity == null || activity.isFinishing()) return;

        final AmoledDialog dlg = new AmoledDialog(activity, tokens, I18n.get(I18n.KEY_OTA_NEW_VERSION_TITLE, langCode));
        final float density = dlg.getDensity();
        final float fontScale = dlg.getFontScale();
        final ThemeTokens themeTokens = dlg.getTokens();

        TextView versionHeader = new TextView(activity);
        String vText = I18n.get(I18n.KEY_OTA_VERSION_LABEL, langCode) + release.tagName
            + " [" + channel.getCode() + "]\n"
            + I18n.get(I18n.KEY_OTA_CURRENT_LABEL, langCode) + curVersion;
        versionHeader.setText(vText);
        versionHeader.setTextColor(themeTokens.textPrimary);
        versionHeader.setTextSize(13 * fontScale);
        versionHeader.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        versionHeader.setPadding(0, 0, 0, (int) (12 * density));
        dlg.getContentContainer().addView(versionHeader);

        TextView bodyText = new TextView(activity);
        String cleanBody = formatChangelogSummary(release.body);
        bodyText.setText(cleanBody.isEmpty() ? I18n.get(I18n.KEY_OTA_NO_NOTES, langCode) : cleanBody);
        bodyText.setTextColor(themeTokens.textMuted);
        bodyText.setTextSize(12 * fontScale);
        bodyText.setTypeface(Typeface.MONOSPACE);
        bodyText.setLineSpacing(0, 1.3f);
        dlg.getContentContainer().addView(bodyText);

        final String downloadTarget = (release.apkUrl != null && !release.apkUrl.isEmpty())
            ? release.apkUrl
            : release.htmlUrl;

        String actionLabel = (release.apkUrl != null)
            ? I18n.get(I18n.KEY_OTA_DOWNLOAD_APK, langCode)
            : I18n.get(I18n.KEY_OTA_VIEW_GITHUB, langCode);

        // Dismiss Button
        dlg.addActionButton(
            I18n.get(I18n.KEY_OTA_DISMISS, langCode),
            themeTokens.textMuted,
            themeTokens.border,
            false,
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dlg.dismiss();
                }
            }
        );

        // Download / Action Button
        dlg.addActionButton(
            actionLabel,
            themeTokens.accent,
            themeTokens.accent,
            false,
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openUrl(activity, downloadTarget);
                }
            }
        );

        dlg.show();
    }

    private static void showUpToDateDialog(
        Activity activity,
        ThemeTokens tokens,
        String langCode,
        String curVersion,
        UpdateChannel channel,
        String remoteTag
    ) {
        String msg = I18n.format(
            I18n.KEY_OTA_UP_TO_DATE_MSG_FMT,
            langCode,
            channel.getCode(),
            curVersion,
            (remoteTag != null ? remoteTag : "v" + curVersion)
        );
        BooleanSelectorDialog.show(
            activity,
            tokens,
            I18n.get(I18n.KEY_OTA_UP_TO_DATE_TITLE, langCode),
            msg,
            I18n.get(I18n.KEY_DONE, langCode),
            null,
            null
        );
    }

    private static void showErrorDialog(
        Activity activity,
        ThemeTokens tokens,
        String langCode,
        String errorDetail
    ) {
        String msg = I18n.format(I18n.KEY_OTA_ERROR_MSG_FMT, langCode, errorDetail);
        BooleanSelectorDialog.show(
            activity,
            tokens,
            I18n.get(I18n.KEY_OTA_ERROR_TITLE, langCode),
            msg,
            I18n.get(I18n.KEY_DONE, langCode),
            null,
            null
        );
    }

    private static String formatChangelogSummary(String raw) {
        if (raw == null) return "";
        String[] lines = raw.split("\n");
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) continue;
            if (trimmed.isEmpty()) continue;
            sb.append(trimmed).append("\n");
            count++;
            if (count >= 12) {
                sb.append("... (ver detalles completos en GitHub)\n");
                break;
            }
        }
        return sb.toString().trim();
    }

    private static void openUrl(Context context, String url) {
        if (context == null || url == null || url.isEmpty()) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable t) {
            Toast.makeText(context, "URL: " + url, Toast.LENGTH_LONG).show();
        }
    }
}
