package org.tontos.launcher;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Handles cryptographic backup creation, destination path selection, and restore integrity checks.
 */
public final class BackupRestoreManager {

    public static final int REQUEST_CODE_SAF_CREATE = 9001;
    public static final int REQUEST_CODE_SAF_RESTORE = 9002;

    private static String sPendingBackupPayload = null;

    private BackupRestoreManager() {}

    public static String sha256Hex(String input) {
        if (input == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static final String DEFAULT_BACKUP_SECRET = "TontOS_Secure_Backup_v1_Secret";

    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    public static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() % 2 != 0) return new byte[0];
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    public static byte[] deriveKey(String password, byte[] salt) throws Exception {
        if (password == null || password.trim().isEmpty()) {
            password = DEFAULT_BACKUP_SECRET;
        }
        javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, 10000, 256);
        javax.crypto.SecretKeyFactory factory;
        try {
            factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        } catch (Exception e) {
            factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        }
        return factory.generateSecret(spec).getEncoded();
    }

    public static byte[] encryptAesGcm(byte[] plaintext, byte[] keyBytes, byte[] iv) throws Exception {
        javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
        javax.crypto.spec.GCMParameterSpec gcmSpec = new javax.crypto.spec.GCMParameterSpec(128, iv);
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
        return cipher.doFinal(plaintext);
    }

    public static byte[] decryptAesGcm(byte[] ciphertext, byte[] keyBytes, byte[] iv) throws Exception {
        javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
        javax.crypto.spec.GCMParameterSpec gcmSpec = new javax.crypto.spec.GCMParameterSpec(128, iv);
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec, gcmSpec);
        return cipher.doFinal(ciphertext);
    }

    public static String decryptPayload(JSONObject envelope, String password) throws Exception {
        if (!envelope.optBoolean("encrypted", false)) {
            return envelope.getString("payload");
        }
        String saltHex = envelope.getString("salt");
        String ivHex = envelope.getString("iv");
        String cipherHex = envelope.getString("ciphertext");

        byte[] salt = hexToBytes(saltHex);
        byte[] iv = hexToBytes(ivHex);
        byte[] cipherBytes = hexToBytes(cipherHex);

        byte[] keyBytes = deriveKey(password, salt);
        byte[] plainBytes = decryptAesGcm(cipherBytes, keyBytes, iv);
        return new String(plainBytes, StandardCharsets.UTF_8);
    }

    public static class CategoryDef {
        public final String id;
        public final String nameKey;
        public final String descKey;

        public CategoryDef(String id, String nameKey, String descKey) {
            this.id = id;
            this.nameKey = nameKey;
            this.descKey = descKey;
        }

        public String getName(String langCode) {
            return I18n.get(nameKey, langCode);
        }

        public String getDesc(String langCode) {
            return I18n.get(descKey, langCode);
        }
    }

    public static final List<CategoryDef> ALL_CATEGORIES = Collections.unmodifiableList(java.util.Arrays.asList(
        new CategoryDef("display", I18n.KEY_BACKUP_CAT_DISPLAY_NAME, I18n.KEY_BACKUP_CAT_DISPLAY_DESC),
        new CategoryDef("detox", I18n.KEY_BACKUP_CAT_DETOX_NAME, I18n.KEY_BACKUP_CAT_DETOX_DESC),
        new CategoryDef("launcher", I18n.KEY_BACKUP_CAT_LAUNCHER_NAME, I18n.KEY_BACKUP_CAT_LAUNCHER_DESC),
        new CategoryDef("security", I18n.KEY_BACKUP_CAT_SECURITY_NAME, I18n.KEY_BACKUP_CAT_SECURITY_DESC),
        new CategoryDef("system", I18n.KEY_BACKUP_CAT_SYSTEM_NAME, I18n.KEY_BACKUP_CAT_SYSTEM_DESC)
    ));

    public static Set<String> getAllCategoryIds() {
        Set<String> set = new HashSet<>();
        for (CategoryDef cat : ALL_CATEGORIES) {
            set.add(cat.id);
        }
        return set;
    }

    public static String getCategoryForKey(String key) {
        if (key == null) return null;
        if (key.equals("selected_theme") || key.equals("system_language") || key.equals("system_region")
                || key.equals("pref_font_scale") || key.equals("pref_clock_24h") || key.equals("pref_grayscale_enabled")) {
            return "display";
        }
        if (key.equals("pref_friction_seconds") || key.equals("pref_daily_budget_mins")
                || key.equals("pref_home_app_limit") || key.equals("screen_time_today_secs")
                || key.equals(MainActivity.KEY_SCREEN_TIME_SECS)) {
            return "detox";
        }
        if (key.equals("pinned_apps") || key.equals("pinned_apps_order") || key.equals("shade_tiles")) {
            return "launcher";
        }
        if (key.equals("pref_blocked_network_apps") || key.equals("pref_privacy_restricted_bg_apps")
                || key.startsWith("pref_privacy_")) {
            return "security";
        }
        if (key.equals("bg_policy") || key.equals("bg_allowed_apps") || key.equals("pref_ultra_battery_saver")
                || key.startsWith("pre_ultra_") || key.equals("pref_notification_delivery_mode")
                || key.startsWith("pref_glyph_") || key.startsWith("pref_eq_") || key.equals("setup_completed")) {
            return "system";
        }
        return null;
    }

    public static String generateBackupEnvelope(SharedPreferences prefs) throws Exception {
        return generateBackupEnvelope(prefs, getAllCategoryIds(), null);
    }

    public static String generateBackupEnvelope(SharedPreferences prefs, Set<String> selectedCategories) throws Exception {
        return generateBackupEnvelope(prefs, selectedCategories, null);
    }

    public static String generateBackupEnvelope(SharedPreferences prefs, Set<String> selectedCategories, String password) throws Exception {
        if (selectedCategories == null || selectedCategories.isEmpty()) {
            selectedCategories = getAllCategoryIds();
        }
        JSONObject payload = new JSONObject();
        Map<String, ?> all = prefs.getAll();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            String key = entry.getKey();
            if (key == null) continue;
            // Exclude temporary scroll positions and security credentials (PIN/pattern/salt)
            if (key.startsWith("pref_last_settings_scroll") || key.startsWith("pref_lock_") || key.equals("lock_salt") || key.contains("hash")) continue;
            if (!isAllowedPreferenceKey(key)) continue;

            String cat = getCategoryForKey(key);
            if (cat == null || !selectedCategories.contains(cat)) {
                continue;
            }

            Object val = entry.getValue();
            if (val instanceof Set || val instanceof java.util.Collection) {
                org.json.JSONArray arr = new org.json.JSONArray();
                for (Object item : (java.util.Collection<?>) val) {
                    if (item != null) arr.put(item.toString());
                }
                payload.put(key, arr);
            } else {
                payload.put(key, val);
            }
        }

        String payloadStr = payload.toString();
        String checksum = sha256Hex(payloadStr);

        byte[] salt = new byte[16];
        new java.security.SecureRandom().nextBytes(salt);
        byte[] iv = new byte[12];
        new java.security.SecureRandom().nextBytes(iv);

        byte[] keyBytes = deriveKey(password, salt);
        byte[] cipherBytes = encryptAesGcm(payloadStr.getBytes(StandardCharsets.UTF_8), keyBytes, iv);

        boolean hasUserPassword = (password != null && !password.trim().isEmpty());

        JSONObject envelope = new JSONObject();
        envelope.put("version", 2);
        envelope.put("timestamp", System.currentTimeMillis());
        envelope.put("date_iso", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date()));
        envelope.put("encrypted", true);
        envelope.put("cipher", "AES-256-GCM");
        envelope.put("kdf", "PBKDF2WithHmacSHA256");
        envelope.put("has_user_password", hasUserPassword);
        envelope.put("salt", bytesToHex(salt));
        envelope.put("iv", bytesToHex(iv));
        envelope.put("sha256", checksum);
        envelope.put("device_model", Build.MANUFACTURER + " " + Build.MODEL);
        envelope.put("tontos_version", MainActivity.OS_VERSION_STRING);
        envelope.put("keys_count", payload.length());
        org.json.JSONArray catsArr = new org.json.JSONArray();
        for (String c : selectedCategories) {
            catsArr.put(c);
        }
        envelope.put("categories", catsArr);
        envelope.put("ciphertext", bytesToHex(cipherBytes));

        return envelope.toString(2);
    }

    /**
     * Shows a detailed AMOLED dialog for creating a backup with custom name and destination selection.
     */
    public static void showCreateBackupDialog(
        final Activity activity,
        final ThemeTokens tokens,
        final String langCode,
        final SharedPreferences prefs
    ) {
        if (activity == null || activity.isFinishing()) return;

        final AmoledDialog dlg = new AmoledDialog(activity, tokens, I18n.get(I18n.KEY_BACKUP_CREATE_TITLE, langCode));
        final float density = dlg.getDensity();
        final float fontScale = dlg.getFontScale();
        final ThemeTokens themeTokens = dlg.getTokens();

        LinearLayout scrollContent = dlg.getContentContainer();

        // Summary Info
        TextView info = new TextView(activity);
        int keyCount = prefs.getAll().size();
        info.setText(I18n.format(I18n.KEY_BACKUP_CREATE_SUMMARY_FMT, langCode, keyCount, "SHA-256"));
        info.setTextColor(tokens.textMuted);
        info.setTextSize(12 * fontScale);
        info.setTypeface(Typeface.MONOSPACE);
        info.setPadding(0, 0, 0, (int) (14 * density));
        scrollContent.addView(info);

        // File Name Label
        TextView nameLabel = new TextView(activity);
        nameLabel.setText(I18n.get(I18n.KEY_BACKUP_FILE_NAME_LABEL, langCode));
        nameLabel.setTextColor(tokens.textPrimary);
        nameLabel.setTextSize(13 * fontScale);
        nameLabel.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        nameLabel.setPadding(0, 0, 0, (int) (4 * density));
        scrollContent.addView(nameLabel);

        // File Name Input
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date());
        final EditText nameInput = new EditText(activity);
        nameInput.setText("tontos_backup_" + timeStamp + ".tontbak");
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT);
        nameInput.setTextColor(tokens.textPrimary);
        nameInput.setTextSize(13 * fontScale);
        nameInput.setTypeface(Typeface.MONOSPACE);
        nameInput.setSingleLine(true);
        nameInput.setPadding((int) (12 * density), (int) (10 * density), (int) (12 * density), (int) (10 * density));

        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(tokens.controlBgNormal);
        inputBg.setStroke((int) (1 * density + 0.5f), tokens.border);
        inputBg.setCornerRadius((int) (4 * density));
        nameInput.setBackground(inputBg);
        scrollContent.addView(nameInput);

        // Destination Label
        TextView destLabel = new TextView(activity);
        destLabel.setText(I18n.get(I18n.KEY_BACKUP_DEST_LABEL, langCode));
        destLabel.setTextColor(tokens.textPrimary);
        destLabel.setTextSize(13 * fontScale);
        destLabel.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        destLabel.setPadding(0, (int) (14 * density), 0, (int) (6 * density));
        scrollContent.addView(destLabel);

        // Selectable Categories for Export
        TextView catLabel = new TextView(activity);
        catLabel.setText(I18n.get(I18n.KEY_BACKUP_CAT_EXPORT_LABEL, langCode));
        catLabel.setTextColor(tokens.textPrimary);
        catLabel.setTextSize(13 * fontScale);
        catLabel.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        catLabel.setPadding(0, (int) (14 * density), 0, (int) (6 * density));
        scrollContent.addView(catLabel);

        final Set<String> selectedCategories = new HashSet<>(getAllCategoryIds());
        final LinearLayout catContainer = new LinearLayout(activity);
        catContainer.setOrientation(LinearLayout.VERTICAL);

        // "Todo" toggle row
        final TextView selectAllExportRow = new TextView(activity);
        selectAllExportRow.setText("[X] " + I18n.get(I18n.KEY_BACKUP_CAT_ALL, langCode));
        selectAllExportRow.setTextColor(tokens.accent);
        selectAllExportRow.setTextSize(12 * fontScale);
        selectAllExportRow.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        selectAllExportRow.setPadding((int) (8 * density), (int) (6 * density), (int) (8 * density), (int) (6 * density));
        selectAllExportRow.setClickable(true);
        selectAllExportRow.setFocusable(true);
        catContainer.addView(selectAllExportRow);

        final List<TextView> exportCatRows = new ArrayList<>();
        for (final CategoryDef def : ALL_CATEGORIES) {
            final TextView catRow = new TextView(activity);
            catRow.setText("[X] " + def.getName(langCode) + "\n    " + def.getDesc(langCode));
            catRow.setTextColor(tokens.accent);
            catRow.setTextSize(12 * fontScale);
            catRow.setTypeface(Typeface.MONOSPACE);
            catRow.setPadding((int) (8 * density), (int) (6 * density), (int) (8 * density), (int) (6 * density));
            catRow.setClickable(true);
            catRow.setFocusable(true);

            catRow.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (selectedCategories.contains(def.id)) {
                        if (selectedCategories.size() <= 1) {
                            Toast.makeText(activity, I18n.get(I18n.KEY_BACKUP_CAT_SELECT_AT_LEAST_ONE, langCode), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        selectedCategories.remove(def.id);
                        catRow.setText("[ ] " + def.getName(langCode) + "\n    " + def.getDesc(langCode));
                        catRow.setTextColor(tokens.textMuted);
                    } else {
                        selectedCategories.add(def.id);
                        catRow.setText("[X] " + def.getName(langCode) + "\n    " + def.getDesc(langCode));
                        catRow.setTextColor(tokens.accent);
                    }
                    if (selectedCategories.size() == ALL_CATEGORIES.size()) {
                        selectAllExportRow.setText("[X] " + I18n.get(I18n.KEY_BACKUP_CAT_ALL, langCode));
                        selectAllExportRow.setTextColor(tokens.accent);
                    } else {
                        selectAllExportRow.setText("[ ] " + I18n.get(I18n.KEY_BACKUP_CAT_ALL, langCode));
                        selectAllExportRow.setTextColor(tokens.textMuted);
                    }
                }
            });
            exportCatRows.add(catRow);
            catContainer.addView(catRow);
        }

        selectAllExportRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedCategories.size() == ALL_CATEGORIES.size()) {
                    selectedCategories.clear();
                    selectAllExportRow.setText("[ ] " + I18n.get(I18n.KEY_BACKUP_CAT_ALL, langCode));
                    selectAllExportRow.setTextColor(tokens.textMuted);
                    for (int i = 0; i < ALL_CATEGORIES.size(); i++) {
                        CategoryDef d = ALL_CATEGORIES.get(i);
                        TextView r = exportCatRows.get(i);
                        r.setText("[ ] " + d.getName(langCode) + "\n    " + d.getDesc(langCode));
                        r.setTextColor(tokens.textMuted);
                    }
                } else {
                    selectedCategories.clear();
                    selectedCategories.addAll(getAllCategoryIds());
                    selectAllExportRow.setText("[X] " + I18n.get(I18n.KEY_BACKUP_CAT_ALL, langCode));
                    selectAllExportRow.setTextColor(tokens.accent);
                    for (int i = 0; i < ALL_CATEGORIES.size(); i++) {
                        CategoryDef d = ALL_CATEGORIES.get(i);
                        TextView r = exportCatRows.get(i);
                        r.setText("[X] " + d.getName(langCode) + "\n    " + d.getDesc(langCode));
                        r.setTextColor(tokens.accent);
                    }
                }
            }
        });
        scrollContent.addView(catContainer);

        // Encryption Password Label
        TextView pwdLabel = new TextView(activity);
        pwdLabel.setText(I18n.get(I18n.KEY_BACKUP_PWD_LABEL, langCode));
        pwdLabel.setTextColor(tokens.textPrimary);
        pwdLabel.setTextSize(13 * fontScale);
        pwdLabel.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        pwdLabel.setPadding(0, (int) (14 * density), 0, (int) (4 * density));
        scrollContent.addView(pwdLabel);

        final EditText pwdInput = new EditText(activity);
        pwdInput.setHint(I18n.get(I18n.KEY_BACKUP_PWD_HINT, langCode));
        pwdInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pwdInput.setTextColor(tokens.textPrimary);
        pwdInput.setHintTextColor(tokens.textMuted);
        pwdInput.setTextSize(13 * fontScale);
        pwdInput.setTypeface(Typeface.MONOSPACE);
        pwdInput.setSingleLine(true);
        pwdInput.setPadding((int) (12 * density), (int) (10 * density), (int) (12 * density), (int) (10 * density));
        GradientDrawable pwdBg = new GradientDrawable();
        pwdBg.setColor(tokens.controlBgNormal);
        pwdBg.setStroke((int) (1 * density + 0.5f), tokens.border);
        pwdBg.setCornerRadius((int) (4 * density));
        pwdInput.setBackground(pwdBg);
        scrollContent.addView(pwdInput);

        // 3 Destination Options (Download, App Internal, SAF System Picker)
        final int[] selectedDest = new int[]{0}; // 0 = Downloads, 1 = App Internal, 2 = SAF Picker
        final LinearLayout optionsContainer = new LinearLayout(activity);
        optionsContainer.setOrientation(LinearLayout.VERTICAL);

        final List<TextView> optionViews = new ArrayList<>();
        final String destDownload = I18n.get(I18n.KEY_BACKUP_DEST_DOWNLOADS, langCode);
        final String destApp = I18n.get(I18n.KEY_BACKUP_DEST_APP, langCode);
        final String destSaf = I18n.get(I18n.KEY_BACKUP_DEST_SAF, langCode);
        final String[] destPrefixes = new String[]{destDownload, destApp, destSaf};
        final String[] destTitles = new String[]{
            "[X] " + destDownload,
            "[ ] " + destApp,
            "[ ] " + destSaf
        };

        for (int i = 0; i < 3; i++) {
            final int index = i;
            final TextView row = new TextView(activity);
            row.setText(destTitles[i]);
            row.setTextColor(index == 0 ? tokens.accent : tokens.textMuted);
            row.setTextSize(12 * fontScale);
            row.setTypeface(Typeface.MONOSPACE, index == 0 ? Typeface.BOLD : Typeface.NORMAL);
            row.setPadding((int) (10 * density), (int) (8 * density), (int) (10 * density), (int) (8 * density));
            row.setClickable(true);
            row.setFocusable(true);

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedDest[0] = index;
                    for (int j = 0; j < optionViews.size(); j++) {
                        TextView tv = optionViews.get(j);
                        if (j == index) {
                            tv.setText("[X] " + destPrefixes[j]);
                            tv.setTextColor(tokens.accent);
                            tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                        } else {
                            tv.setText("[ ] " + destPrefixes[j]);
                            tv.setTextColor(tokens.textMuted);
                            tv.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
                        }
                    }
                }
            });
            optionViews.add(row);
            optionsContainer.addView(row);
        }
        scrollContent.addView(optionsContainer);

        // Cancel Button
        dlg.addActionButton(
            I18n.get(I18n.KEY_CANCEL, langCode),
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

        // Save Button
        dlg.addActionButton(
            I18n.get(I18n.KEY_BACKUP_SAVE_BTN, langCode),
            themeTokens.accent,
            themeTokens.accent,
            false,
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String fileName = nameInput.getText().toString().trim();
                    if (fileName.isEmpty()) {
                        Toast.makeText(activity, I18n.get(I18n.KEY_BACKUP_ERR_EMPTY_NAME, langCode), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // Enforce a safe single filename component to prevent path traversal/injection.
                    if (fileName.contains("..")
                            || fileName.contains("/")
                            || fileName.contains("\\")
                            || fileName.startsWith(".")
                            || !fileName.matches("[A-Za-z0-9 _.-]+")) {
                        Toast.makeText(activity, I18n.get(I18n.KEY_BACKUP_ERR_INVALID_NAME, langCode), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!fileName.endsWith(".tontbak") && !fileName.endsWith(".json")) {
                        fileName += ".tontbak";
                    }

                    try {
                        String userPassword = pwdInput.getText().toString().trim();
                        if (userPassword.isEmpty()) userPassword = null;
                        String envelopeJson = generateBackupEnvelope(prefs, selectedCategories, userPassword);

                        if (selectedDest[0] == 2) {
                            // SAF Picker
                            sPendingBackupPayload = envelopeJson;
                            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                            intent.setType("application/octet-stream");
                            intent.putExtra(Intent.EXTRA_TITLE, fileName);
                            activity.startActivityForResult(intent, REQUEST_CODE_SAF_CREATE);
                            dlg.dismiss();
                            return;
                        }

                        File targetDir;
                        if (selectedDest[0] == 0) {
                            File dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                            targetDir = new File(dl, "TontOS_Backups");
                        } else {
                            File base = activity.getExternalFilesDir(null);
                            if (base == null) base = activity.getFilesDir();
                            targetDir = new File(base, "backup");
                        }

                        if (!targetDir.exists()) targetDir.mkdirs();
                        File targetFile = new File(targetDir, fileName);

                        FileWriter writer = new FileWriter(targetFile);
                        writer.write(envelopeJson);
                        writer.flush();
                        writer.close();

                        dlg.dismiss();
                        showBackupSuccessDialog(activity, tokens, langCode, targetFile);

                    } catch (Exception e) {
                        Toast.makeText(activity, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
            }
        );

        dlg.show();
    }

    public static void handleSafCreateResult(
        Activity activity,
        Uri uri,
        ThemeTokens tokens,
        String langCode
    ) {
        if (uri == null || sPendingBackupPayload == null) return;
        try {
            OutputStream os = activity.getContentResolver().openOutputStream(uri);
            if (os != null) {
                os.write(sPendingBackupPayload.getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.close();
                Toast.makeText(activity, I18n.get(I18n.KEY_BACKUP_SAF_SUCCESS, langCode), Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(activity, "SAF Write Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            sPendingBackupPayload = null;
        }
    }

    private static void showBackupSuccessDialog(
        final Activity activity,
        final ThemeTokens tokens,
        final String langCode,
        final File targetFile
    ) {
        long bytes = targetFile.length();
        String details =
            I18n.get(I18n.KEY_BACKUP_DETAILS_FILE, langCode) + targetFile.getName() + "\n" +
            I18n.get(I18n.KEY_BACKUP_DETAILS_PATH, langCode) + targetFile.getParent() + "\n" +
            I18n.get(I18n.KEY_BACKUP_DETAILS_SIZE, langCode) + bytes + " bytes\n" +
            I18n.get(I18n.KEY_BACKUP_DETAILS_STATUS, langCode) + I18n.get(I18n.KEY_BACKUP_STATUS_VERIFIED, langCode);

        BooleanSelectorDialog.show(
            activity,
            tokens,
            I18n.get(I18n.KEY_BACKUP_SUCCESS_TITLE, langCode),
            details,
            I18n.get(I18n.KEY_DONE, langCode),
            null,
            null
        );
    }

    /**
     * Shows a detailed dialog to list and restore existing backups.
     */
    public static void showRestoreBackupDialog(
        final Activity activity,
        final ThemeTokens tokens,
        final String langCode,
        final SharedPreferences prefs,
        final Runnable onRestored
    ) {
        if (activity == null || activity.isFinishing()) return;

        final AmoledDialog dlg = new AmoledDialog(activity, tokens, I18n.get(I18n.KEY_RESTORE_TITLE, langCode));
        final float density = dlg.getDensity();
        final float fontScale = dlg.getFontScale();
        final ThemeTokens themeTokens = dlg.getTokens();

        // Find existing backups
        List<File> backupFiles = findAvailableBackups(activity);
        LinearLayout listContainer = dlg.getContentContainer();

        if (backupFiles.isEmpty()) {
            TextView none = new TextView(activity);
            none.setText(I18n.get(I18n.KEY_RESTORE_NO_BACKUPS, langCode));
            none.setTextColor(themeTokens.textMuted);
            none.setTextSize(13 * fontScale);
            none.setTypeface(Typeface.MONOSPACE);
            none.setPadding(0, (int) (16 * density), 0, (int) (16 * density));
            listContainer.addView(none);
        } else {
            for (final File f : backupFiles) {
                TextView item = new TextView(activity);
                item.setText("> " + f.getName() + " (" + (f.length() / 1024 + 1) + " KB)");
                item.setTextColor(themeTokens.textPrimary);
                item.setTextSize(13 * fontScale);
                item.setTypeface(Typeface.MONOSPACE);
                item.setMinHeight((int) (48 * density));
                item.setGravity(Gravity.CENTER_VERTICAL);
                item.setPadding((int) (12 * density), (int) (10 * density), (int) (12 * density), (int) (10 * density));
                item.setClickable(true);
                item.setFocusable(true);

                GradientDrawable itemBg = new GradientDrawable();
                itemBg.setColor(themeTokens.controlBgNormal);
                itemBg.setStroke((int) (1 * density + 0.5f), themeTokens.border);
                itemBg.setCornerRadius((int) (4 * density));
                item.setBackground(itemBg);

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, 0, (int) (8 * density));
                item.setLayoutParams(lp);

                item.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dlg.dismiss();
                        confirmRestoreFile(activity, themeTokens, langCode, f, prefs, onRestored);
                    }
                });
                listContainer.addView(item);
            }
        }

        // Action dock: SAF picker button + Cancel
        dlg.addActionButton(
            I18n.get(I18n.KEY_RESTORE_SAF_BTN, langCode),
            themeTokens.textPrimary,
            themeTokens.border,
            false,
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dlg.dismiss();
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    activity.startActivityForResult(intent, REQUEST_CODE_SAF_RESTORE);
                }
            }
        );

        dlg.addActionButton(
            I18n.get(I18n.KEY_RESTORE_CLOSE_BTN, langCode),
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

        dlg.show();
    }

    private static List<File> findAvailableBackups(Context context) {
        List<File> list = new ArrayList<>();
        // 1. Check Download/TontOS_Backups and Download/
        File dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (dl != null && dl.exists()) {
            scanDir(new File(dl, "TontOS_Backups"), list);
            scanDir(dl, list);
        }
        // 2. Check App backup folder
        File base = context.getExternalFilesDir(null);
        if (base == null) base = context.getFilesDir();
        if (base != null) {
            scanDir(new File(base, "backup"), list);
        }

        // Sort descending by last modified
        Collections.sort(list, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                return Long.compare(o2.lastModified(), o1.lastModified());
            }
        });
        return list;
    }

    private static void scanDir(File dir, List<File> list) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isFile() && (f.getName().endsWith(".tontbak") || f.getName().endsWith(".json"))) {
                if (!list.contains(f)) list.add(f);
            }
        }
    }

    private static void confirmRestoreFile(
        final Activity activity,
        final ThemeTokens tokens,
        final String langCode,
        final File file,
        final SharedPreferences prefs,
        final Runnable onRestored
    ) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = br.readLine()) != null) sb.append(l);
            br.close();

            validateAndRestore(activity, tokens, langCode, sb.toString(), prefs, onRestored);
        } catch (Exception e) {
            Toast.makeText(activity, "Read Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public static void handleSafRestoreResult(
        Activity activity,
        Uri uri,
        ThemeTokens tokens,
        String langCode,
        SharedPreferences prefs,
        Runnable onRestored
    ) {
        if (uri == null) return;
        try {
            InputStream is = activity.getContentResolver().openInputStream(uri);
            if (is == null) return;
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = br.readLine()) != null) sb.append(l);
            br.close();

            validateAndRestore(activity, tokens, langCode, sb.toString(), prefs, onRestored);
        } catch (Exception e) {
            Toast.makeText(activity, "SAF Read Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private static void validateAndRestore(
        final Activity activity,
        final ThemeTokens tokens,
        final String langCode,
        final String rawJson,
        final SharedPreferences prefs,
        final Runnable onRestored
    ) throws Exception {
        final JSONObject envelope = new JSONObject(rawJson);
        final boolean isEncrypted = envelope.optBoolean("encrypted", false);

        if (!isEncrypted && !envelope.has("payload")) {
            Toast.makeText(activity, I18n.get(I18n.KEY_RESTORE_ERR_INVALID, langCode), Toast.LENGTH_LONG).show();
            return;
        }
        if (isEncrypted && !envelope.has("ciphertext")) {
            Toast.makeText(activity, I18n.get(I18n.KEY_RESTORE_ERR_INVALID, langCode), Toast.LENGTH_LONG).show();
            return;
        }

        boolean hasUserPassword = envelope.optBoolean("has_user_password", false);
        if (isEncrypted && hasUserPassword) {
            promptRestorePassword(activity, tokens, langCode, envelope, prefs, onRestored);
        } else {
            try {
                String payload = decryptPayload(envelope, null);
                showRestoreCategorySelectionDialog(activity, tokens, langCode, envelope, payload, prefs, onRestored);
            } catch (Exception e) {
                Toast.makeText(activity, I18n.get(I18n.KEY_BACKUP_PWD_WRONG_ERR, langCode), Toast.LENGTH_LONG).show();
            }
        }
    }

    private static void promptRestorePassword(
        final Activity activity,
        final ThemeTokens tokens,
        final String langCode,
        final JSONObject envelope,
        final SharedPreferences prefs,
        final Runnable onRestored
    ) {
        final AmoledDialog pwdDlg = new AmoledDialog(activity, tokens, I18n.get(I18n.KEY_BACKUP_PWD_PROMPT_TITLE, langCode));
        final float density = pwdDlg.getDensity();
        final float fontScale = pwdDlg.getFontScale();
        final ThemeTokens themeTokens = pwdDlg.getTokens();

        LinearLayout content = pwdDlg.getContentContainer();

        TextView label = new TextView(activity);
        label.setText(I18n.get(I18n.KEY_BACKUP_PWD_PROMPT_DESC, langCode));
        label.setTextColor(tokens.textPrimary);
        label.setTextSize(13 * fontScale);
        label.setTypeface(Typeface.MONOSPACE);
        label.setPadding(0, 0, 0, (int) (8 * density));
        content.addView(label);

        final EditText input = new EditText(activity);
        input.setHint(I18n.get(I18n.KEY_BACKUP_PWD_PROMPT_HINT, langCode));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setTextColor(tokens.textPrimary);
        input.setHintTextColor(tokens.textMuted);
        input.setTextSize(13 * fontScale);
        input.setTypeface(Typeface.MONOSPACE);
        input.setSingleLine(true);
        input.setPadding((int) (12 * density), (int) (10 * density), (int) (12 * density), (int) (10 * density));
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(tokens.controlBgNormal);
        inputBg.setStroke((int) (1 * density + 0.5f), tokens.border);
        inputBg.setCornerRadius((int) (4 * density));
        input.setBackground(inputBg);
        content.addView(input);

        pwdDlg.addActionButton(
            I18n.get(I18n.KEY_CANCEL, langCode),
            themeTokens.textMuted,
            themeTokens.border,
            false,
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pwdDlg.dismiss();
                }
            }
        );

        pwdDlg.addActionButton(
            I18n.get(I18n.KEY_BACKUP_PWD_DECRYPT_BTN, langCode),
            themeTokens.accent,
            themeTokens.accent,
            false,
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String pwd = input.getText().toString();
                    if (pwd.isEmpty()) {
                        Toast.makeText(activity, I18n.get(I18n.KEY_BACKUP_PWD_EMPTY_ERR, langCode), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        String payload = decryptPayload(envelope, pwd);
                        pwdDlg.dismiss();
                        showRestoreCategorySelectionDialog(activity, tokens, langCode, envelope, payload, prefs, onRestored);
                    } catch (Exception e) {
                        Toast.makeText(activity, I18n.get(I18n.KEY_BACKUP_PWD_WRONG_ERR, langCode), Toast.LENGTH_LONG).show();
                    }
                }
            }
        );

        pwdDlg.show();
    }

    private static void showRestoreCategorySelectionDialog(
        final Activity activity,
        final ThemeTokens tokens,
        final String langCode,
        final JSONObject envelope,
        final String payload,
        final SharedPreferences prefs,
        final Runnable onRestored
    ) throws Exception {
        String recordedHash = envelope.optString("sha256", "");
        String actualHash = sha256Hex(payload);

        if (!actualHash.equalsIgnoreCase(recordedHash)) {
            Toast.makeText(activity, I18n.get(I18n.KEY_RESTORE_ERR_CHECKSUM, langCode), Toast.LENGTH_LONG).show();
            return;
        }

        final JSONObject payloadObj = new JSONObject(payload);

        // Detect available categories in the payload
        final Set<String> availableCategoriesInBackup = new HashSet<>();
        java.util.Iterator<String> pKeys = payloadObj.keys();
        while (pKeys.hasNext()) {
            String pk = pKeys.next();
            String cat = getCategoryForKey(pk);
            if (cat != null) {
                availableCategoriesInBackup.add(cat);
            }
        }
        if (envelope.has("categories")) {
            org.json.JSONArray catArr = envelope.optJSONArray("categories");
            if (catArr != null) {
                for (int i = 0; i < catArr.length(); i++) {
                    String c = catArr.optString(i);
                    if (c != null && !c.isEmpty()) availableCategoriesInBackup.add(c);
                }
            }
        }

        final AmoledDialog dlg = new AmoledDialog(activity, tokens, I18n.get(I18n.KEY_RESTORE_CONFIRM_TITLE, langCode));
        final float density = dlg.getDensity();
        final float fontScale = dlg.getFontScale();
        final ThemeTokens themeTokens = dlg.getTokens();

        LinearLayout scrollContent = dlg.getContentContainer();

        String dateStr = envelope.optString("date_iso", "Unknown");
        String devStr = envelope.optString("device_model", "TontOS Device");
        int count = payloadObj.length();

        String cipherInfo = envelope.optBoolean("encrypted", false) ? ("\n🔒 " + envelope.optString("cipher", "AES-256-GCM")) : "";

        TextView info = new TextView(activity);
        info.setText(I18n.format(I18n.KEY_RESTORE_CONFIRM_MSG_FMT, langCode, count, dateStr, devStr)
                + "\n✔ SHA-256: " + (recordedHash.length() > 16 ? recordedHash.substring(0, 16) : recordedHash) + "..."
                + cipherInfo);
        info.setTextColor(tokens.textMuted);
        info.setTextSize(12 * fontScale);
        info.setTypeface(Typeface.MONOSPACE);
        info.setPadding(0, 0, 0, (int) (12 * density));
        scrollContent.addView(info);

        TextView selectLabel = new TextView(activity);
        selectLabel.setText(I18n.get(I18n.KEY_BACKUP_CAT_IMPORT_LABEL, langCode));
        selectLabel.setTextColor(tokens.textPrimary);
        selectLabel.setTextSize(13 * fontScale);
        selectLabel.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        selectLabel.setPadding(0, (int) (4 * density), 0, (int) (6 * density));
        scrollContent.addView(selectLabel);

        final Set<String> selectedRestoreCategories = new HashSet<>(availableCategoriesInBackup);
        final LinearLayout catContainer = new LinearLayout(activity);
        catContainer.setOrientation(LinearLayout.VERTICAL);

        // "Todo" toggle row for restore
        final TextView selectAllRestoreRow = new TextView(activity);
        selectAllRestoreRow.setText("[X] " + I18n.get(I18n.KEY_BACKUP_CAT_ALL_AVAILABLE, langCode));
        selectAllRestoreRow.setTextColor(tokens.accent);
        selectAllRestoreRow.setTextSize(12 * fontScale);
        selectAllRestoreRow.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        selectAllRestoreRow.setPadding((int) (8 * density), (int) (6 * density), (int) (8 * density), (int) (6 * density));
        selectAllRestoreRow.setClickable(true);
        selectAllRestoreRow.setFocusable(true);
        catContainer.addView(selectAllRestoreRow);

        final List<TextView> availableCatRows = new ArrayList<>();
        final List<CategoryDef> availableDefs = new ArrayList<>();

        for (final CategoryDef def : ALL_CATEGORIES) {
            final boolean isAvailable = availableCategoriesInBackup.contains(def.id);
            final TextView catRow = new TextView(activity);
            if (isAvailable) {
                catRow.setText("[X] " + def.getName(langCode) + "\n    " + def.getDesc(langCode));
                catRow.setTextColor(tokens.accent);
                catRow.setClickable(true);
                catRow.setFocusable(true);
                catRow.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (selectedRestoreCategories.contains(def.id)) {
                            if (selectedRestoreCategories.size() <= 1) {
                                Toast.makeText(activity, I18n.get(I18n.KEY_BACKUP_CAT_SELECT_AT_LEAST_ONE, langCode), Toast.LENGTH_SHORT).show();
                                return;
                            }
                            selectedRestoreCategories.remove(def.id);
                            catRow.setText("[ ] " + def.getName(langCode) + "\n    " + def.getDesc(langCode));
                            catRow.setTextColor(tokens.textMuted);
                        } else {
                            selectedRestoreCategories.add(def.id);
                            catRow.setText("[X] " + def.getName(langCode) + "\n    " + def.getDesc(langCode));
                            catRow.setTextColor(tokens.accent);
                        }
                        if (selectedRestoreCategories.size() == availableCategoriesInBackup.size()) {
                            selectAllRestoreRow.setText("[X] " + I18n.get(I18n.KEY_BACKUP_CAT_ALL_AVAILABLE, langCode));
                            selectAllRestoreRow.setTextColor(tokens.accent);
                        } else {
                            selectAllRestoreRow.setText("[ ] " + I18n.get(I18n.KEY_BACKUP_CAT_ALL_AVAILABLE, langCode));
                            selectAllRestoreRow.setTextColor(tokens.textMuted);
                        }
                    }
                });
                availableCatRows.add(catRow);
                availableDefs.add(def);
            } else {
                String notAvailableStr = I18n.get(I18n.KEY_BACKUP_CAT_NOT_AVAILABLE, langCode);
                catRow.setText("[-] " + def.getName(langCode) + " " + notAvailableStr);
                catRow.setTextColor(tokens.textMuted);
                catRow.setAlpha(0.5f);
            }
            catRow.setTextSize(12 * fontScale);
            catRow.setTypeface(Typeface.MONOSPACE);
            catRow.setPadding((int) (8 * density), (int) (6 * density), (int) (8 * density), (int) (6 * density));
            catContainer.addView(catRow);
        }

        selectAllRestoreRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedRestoreCategories.size() == availableCategoriesInBackup.size()) {
                    selectedRestoreCategories.clear();
                    selectAllRestoreRow.setText("[ ] " + I18n.get(I18n.KEY_BACKUP_CAT_ALL_AVAILABLE, langCode));
                    selectAllRestoreRow.setTextColor(tokens.textMuted);
                    for (int i = 0; i < availableDefs.size(); i++) {
                        CategoryDef d = availableDefs.get(i);
                        TextView r = availableCatRows.get(i);
                        r.setText("[ ] " + d.getName(langCode) + "\n    " + d.getDesc(langCode));
                        r.setTextColor(tokens.textMuted);
                    }
                } else {
                    selectedRestoreCategories.clear();
                    selectedRestoreCategories.addAll(availableCategoriesInBackup);
                    selectAllRestoreRow.setText("[X] " + I18n.get(I18n.KEY_BACKUP_CAT_ALL_AVAILABLE, langCode));
                    selectAllRestoreRow.setTextColor(tokens.accent);
                    for (int i = 0; i < availableDefs.size(); i++) {
                        CategoryDef d = availableDefs.get(i);
                        TextView r = availableCatRows.get(i);
                        r.setText("[X] " + d.getName(langCode) + "\n    " + d.getDesc(langCode));
                        r.setTextColor(tokens.accent);
                    }
                }
            }
        });

        scrollContent.addView(catContainer);

        // Cancel Button
        dlg.addActionButton(
            I18n.get(I18n.KEY_CANCEL, langCode),
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

        // Restore Button
        dlg.addActionButton(
            I18n.get(I18n.KEY_RESTORE_NOW_BTN, langCode),
            themeTokens.accent,
            themeTokens.accent,
            false,
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (selectedRestoreCategories.isEmpty()) {
                        Toast.makeText(activity, I18n.get(I18n.KEY_BACKUP_CAT_SELECT_AT_LEAST_ONE, langCode), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    dlg.dismiss();
                    applyRestoredPreferences(prefs, payloadObj, selectedRestoreCategories);
                    Toast.makeText(activity, I18n.get(I18n.KEY_RESTORE_SUCCESS, langCode), Toast.LENGTH_SHORT).show();
                    if (onRestored != null) onRestored.run();
                }
            }
        );

        dlg.show();
    }

    public static void applyRestoredPreferences(SharedPreferences prefs, JSONObject payload) {
        applyRestoredPreferences(prefs, payload, getAllCategoryIds());
    }

    public static void applyRestoredPreferences(SharedPreferences prefs, JSONObject payload, Set<String> allowedCategories) {
        if (allowedCategories == null || allowedCategories.isEmpty()) {
            allowedCategories = getAllCategoryIds();
        }
        SharedPreferences.Editor editor = prefs.edit();
        java.util.Iterator<String> keys = payload.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            // Validate key against strict allowlist (blocks credentials, hashes, or arbitrary injection)
            if (!isAllowedPreferenceKey(key)) continue;

            String cat = getCategoryForKey(key);
            if (cat == null || !allowedCategories.contains(cat)) {
                continue;
            }

            Object val = payload.opt(key);
            if (val instanceof Boolean) {
                editor.putBoolean(key, (Boolean) val);
            } else if (MainActivity.KEY_SCREEN_TIME_SECS.equals(key) || "screen_time_today_secs".equals(key)) {
                if (val instanceof Number) {
                    editor.putLong(key, ((Number) val).longValue());
                }
            } else if (val instanceof Integer) {
                editor.putInt(key, (Integer) val);
            } else if (val instanceof Long) {
                editor.putLong(key, (Long) val);
            } else if (val instanceof Double) {
                editor.putFloat(key, ((Double) val).floatValue());
            } else if (val instanceof String) {
                String str = (String) val;
                if (isStringSetKey(key) || (str.startsWith("[") && str.endsWith("]"))) {
                    Set<String> set = parseSafeStringSet(str);
                    editor.putStringSet(key, set);
                } else {
                    editor.putString(key, str);
                }
            } else if (val instanceof org.json.JSONArray) {
                org.json.JSONArray arr = (org.json.JSONArray) val;
                Set<String> set = new HashSet<>();
                for (int i = 0; i < arr.length(); i++) {
                    set.add(arr.optString(i));
                }
                editor.putStringSet(key, set);
            }
        }
        editor.apply();
    }

    private static boolean isAllowedPreferenceKey(String key) {
        if (key == null) return false;
        if (key.startsWith("pref_lock_") || key.equals("lock_salt") || key.contains("hash")) return false;
        return key.equals("selected_theme")
                || key.equals("system_language")
                || key.equals("system_region")
                || key.equals("pref_font_scale")
                || key.equals("pref_clock_24h")
                || key.equals("pref_friction_seconds")
                || key.equals("pref_home_app_limit")
                || key.equals("pref_daily_budget_mins")
                || key.equals("pref_grayscale_enabled")
                || key.equals("bg_policy")
                || key.equals("bg_allowed_apps")
                || key.equals("pinned_apps")
                || key.equals("pinned_apps_order")
                || key.equals("shade_tiles")
                || key.equals("pref_blocked_network_apps")
                || key.equals("pref_privacy_restricted_bg_apps")
                || key.equals("pref_notification_delivery_mode")
                || key.equals("pref_ultra_battery_saver")
                || key.equals("setup_completed")
                || key.equals("screen_time_today_secs")
                || key.startsWith("pref_privacy_")
                || key.startsWith("pref_glyph_")
                || key.startsWith("pre_ultra_")
                || key.startsWith("pref_eq_");
    }

    private static boolean isStringSetKey(String key) {
        return "bg_allowed_apps".equals(key)
                || "pinned_apps".equals(key)
                || "shade_tiles".equals(key)
                || "pref_blocked_network_apps".equals(key)
                || "pref_privacy_restricted_bg_apps".equals(key)
                || (key != null && key.startsWith("pref_privacy_blocked_perms_"));
    }

    private static Set<String> parseSafeStringSet(String raw) {
        Set<String> set = new HashSet<>();
        if (raw == null || raw.trim().isEmpty()) return set;
        String trimmed = raw.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        for (String p : trimmed.split(",")) {
            String item = p.trim();
            if (!item.isEmpty()) set.add(item);
        }
        return set;
    }
}
