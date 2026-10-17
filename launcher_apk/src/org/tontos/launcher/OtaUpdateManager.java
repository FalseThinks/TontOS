package org.tontos.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
        if (context == null) return "0.0.1";
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Throwable ignored) {
            return "0.0.1";
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

        String checkingMsg = "es".equalsIgnoreCase(langCode)
            ? "Comprobando actualizaciones OTA en GitHub (" + channel.getCode() + ")..."
            : "Checking for OTA updates on GitHub (" + channel.getCode() + ")...";
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
                            } else if (channel == UpdateChannel.NIGHTLY && tag.toLowerCase().contains("nightly")) {
                                targetRelease = parseRelease(obj);
                                break;
                            } else if (channel == UpdateChannel.BETA) {
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
        float density = activity.getResources().getDisplayMetrics().density;
        boolean isEs = "es".equalsIgnoreCase(langCode);

        AlertDialog.Builder builder = new AlertDialog.Builder(
            activity,
            tokens.isLight ? AlertDialog.THEME_DEVICE_DEFAULT_LIGHT : AlertDialog.THEME_DEVICE_DEFAULT_DARK
        );

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (20 * density), (int) (18 * density), (int) (20 * density), (int) (18 * density));
        root.setBackgroundColor(tokens.bg);

        TextView title = new TextView(activity);
        title.setText(isEs ? "=== NUEVA VERSIÓN DISPONIBLE ===" : "=== NEW VERSION AVAILABLE ===");
        title.setTextColor(tokens.accent);
        title.setTextSize(15);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        title.setPadding(0, 0, 0, (int) (8 * density));
        root.addView(title);

        TextView versionHeader = new TextView(activity);
        String vText = (isEs ? "Versión: " : "Version: ") + release.tagName
            + " [" + channel.getCode() + "]\n"
            + (isEs ? "Instalada: v" : "Current: v") + curVersion;
        versionHeader.setText(vText);
        versionHeader.setTextColor(tokens.textPrimary);
        versionHeader.setTextSize(13);
        versionHeader.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        versionHeader.setPadding(0, 0, 0, (int) (12 * density));
        root.addView(versionHeader);

        // Changelog scrollview
        ScrollView scroll = new ScrollView(activity);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (int) (160 * density)
        );
        scroll.setLayoutParams(scrollLp);

        TextView bodyText = new TextView(activity);
        String cleanBody = formatChangelogSummary(release.body);
        bodyText.setText(cleanBody.isEmpty() ? (isEs ? "(Sin notas de cambios)" : "(No release notes)") : cleanBody);
        bodyText.setTextColor(tokens.textMuted);
        bodyText.setTextSize(12);
        bodyText.setTypeface(Typeface.MONOSPACE);
        scroll.addView(bodyText);
        root.addView(scroll);

        // Action Buttons dock
        LinearLayout btnDock = new LinearLayout(activity);
        btnDock.setOrientation(LinearLayout.VERTICAL);
        btnDock.setPadding(0, (int) (14 * density), 0, 0);

        final String downloadTarget = (release.apkUrl != null && !release.apkUrl.isEmpty())
            ? release.apkUrl
            : release.htmlUrl;

        String actionLabel = (release.apkUrl != null)
            ? (isEs ? "[ DESCARGAR APK ]" : "[ DOWNLOAD APK ]")
            : (isEs ? "[ VER EN GITHUB ]" : "[ VIEW ON GITHUB ]");

        Button downloadBtn = new Button(activity);
        downloadBtn.setText(actionLabel);
        downloadBtn.setTextColor(tokens.isLight ? Color.WHITE : tokens.navAccent);
        downloadBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        downloadBtn.setBackground(createButtonBg(tokens.navAccent, tokens.isLight ? tokens.navAccent : tokens.navBg, (int) density));
        downloadBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openUrl(activity, downloadTarget);
            }
        });
        btnDock.addView(downloadBtn);

        root.addView(btnDock);

        builder.setView(root);
        builder.setNegativeButton(isEs ? "CERRAR" : "DISMISS", null);

        final AlertDialog dialog = builder.create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(tokens.bg));
        }
        Button pBtn = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
        if (pBtn != null) {
            pBtn.setTextColor(tokens.textMuted);
            pBtn.setTypeface(Typeface.MONOSPACE);
        }
    }

    private static void showUpToDateDialog(
        Activity activity,
        ThemeTokens tokens,
        String langCode,
        String curVersion,
        UpdateChannel channel,
        String remoteTag
    ) {
        boolean isEs = "es".equalsIgnoreCase(langCode);
        AlertDialog.Builder builder = new AlertDialog.Builder(
            activity,
            tokens.isLight ? AlertDialog.THEME_DEVICE_DEFAULT_LIGHT : AlertDialog.THEME_DEVICE_DEFAULT_DARK
        );
        builder.setTitle(isEs ? "SISTEMA ACTUALIZADO" : "SYSTEM UP TO DATE");

        String msg = isEs
            ? "TontOS está actualizado.\n\n• Canal: " + channel.getCode()
                + "\n• Versión instalada: v" + curVersion
                + "\n• Versión en GitHub: " + (remoteTag != null ? remoteTag : "v" + curVersion)
                + "\n\nNo hay nuevas actualizaciones disponibles."
            : "TontOS is up to date.\n\n• Channel: " + channel.getCode()
                + "\n• Installed: v" + curVersion
                + "\n• GitHub tag: " + (remoteTag != null ? remoteTag : "v" + curVersion)
                + "\n\nNo newer releases found.";

        builder.setMessage(msg);
        builder.setPositiveButton("OK", null);
        AlertDialog dialog = builder.create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(tokens.bg));
        }
        Button pBtn = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (pBtn != null) {
            pBtn.setTextColor(tokens.accent);
            pBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        }
    }

    private static void showErrorDialog(
        Activity activity,
        ThemeTokens tokens,
        String langCode,
        String errorDetail
    ) {
        boolean isEs = "es".equalsIgnoreCase(langCode);
        AlertDialog.Builder builder = new AlertDialog.Builder(
            activity,
            tokens.isLight ? AlertDialog.THEME_DEVICE_DEFAULT_LIGHT : AlertDialog.THEME_DEVICE_DEFAULT_DARK
        );
        builder.setTitle(isEs ? "ERROR OTA GITHUB" : "OTA UPDATE ERROR");
        String msg = isEs
            ? "No se pudo consultar el repositorio de actualizaciones:\n\n" + errorDetail
                + "\n\nVerifique su conexión a Internet o inténtelo de nuevo más tarde."
            : "Could not query GitHub release repository:\n\n" + errorDetail
                + "\n\nPlease verify network connection or try again later.";
        builder.setMessage(msg);
        builder.setPositiveButton("OK", null);
        AlertDialog dialog = builder.create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(tokens.bg));
        }
        Button pBtn = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (pBtn != null) {
            pBtn.setTextColor(tokens.accent);
            pBtn.setTypeface(Typeface.MONOSPACE);
        }
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

    private static StateListDrawable createButtonBg(int strokeColor, int bgColor, int density) {
        StateListDrawable sld = new StateListDrawable();
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(strokeColor);
        pressed.setCornerRadius(4 * density);

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(bgColor);
        normal.setStroke(Math.max(1, density), strokeColor);
        normal.setCornerRadius(4 * density);

        sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sld.addState(new int[]{}, normal);
        return sld;
    }
}
