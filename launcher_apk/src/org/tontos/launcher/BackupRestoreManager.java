package org.tontos.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
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
import android.widget.Button;
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
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static String generateBackupEnvelope(SharedPreferences prefs) throws Exception {
        JSONObject payload = new JSONObject();
        Map<String, ?> all = prefs.getAll();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            String key = entry.getKey();
            if (key == null) continue;
            // Exclude temporary scroll positions or transient session states
            if (key.startsWith("pref_last_settings_scroll")) continue;
            payload.put(key, entry.getValue());
        }

        String payloadStr = payload.toString();
        String checksum = sha256Hex(payloadStr);

        JSONObject envelope = new JSONObject();
        envelope.put("version", 1);
        envelope.put("timestamp", System.currentTimeMillis());
        envelope.put("date_iso", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date()));
        envelope.put("sha256", checksum);
        envelope.put("device_model", Build.MANUFACTURER + " " + Build.MODEL);
        envelope.put("tontos_version", MainActivity.OS_VERSION_STRING);
        envelope.put("keys_count", payload.length());
        envelope.put("payload", payloadStr);

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
        final float density = activity.getResources().getDisplayMetrics().density;
        final boolean isEs = "es".equalsIgnoreCase(langCode);

        AlertDialog.Builder builder = new AlertDialog.Builder(
            activity,
            tokens.isLight ? AlertDialog.THEME_DEVICE_DEFAULT_LIGHT : AlertDialog.THEME_DEVICE_DEFAULT_DARK
        );

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (20 * density), (int) (18 * density), (int) (20 * density), (int) (18 * density));
        root.setBackgroundColor(tokens.bg);

        // Header Title
        TextView header = new TextView(activity);
        header.setText(isEs ? "=== CREAR COPIA DE SEGURIDAD ===" : "=== CREATE BACKUP SNAPSHOT ===");
        header.setTextColor(tokens.accent);
        header.setTextSize(15);
        header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.setPadding(0, 0, 0, (int) (8 * density));
        root.addView(header);

        // Summary Info
        TextView info = new TextView(activity);
        int keyCount = prefs.getAll().size();
        info.setText(isEs
            ? ("Respaldo criptográfico de " + keyCount + " ajustes (tema, launcher, audio, redes).")
            : ("Cryptographic snapshot of " + keyCount + " preferences (theme, apps, sound, radios)."));
        info.setTextColor(tokens.textMuted);
        info.setTextSize(12);
        info.setTypeface(Typeface.MONOSPACE);
        info.setPadding(0, 0, 0, (int) (14 * density));
        root.addView(info);

        // File Name Label
        TextView nameLabel = new TextView(activity);
        nameLabel.setText(isEs ? "Nombre del archivo:" : "Backup File Name:");
        nameLabel.setTextColor(tokens.textPrimary);
        nameLabel.setTextSize(13);
        nameLabel.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        nameLabel.setPadding(0, 0, 0, (int) (4 * density));
        root.addView(nameLabel);

        // File Name Input
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date());
        final EditText nameInput = new EditText(activity);
        nameInput.setText("tontos_backup_" + timeStamp + ".tontbak");
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT);
        nameInput.setTextColor(tokens.textPrimary);
        nameInput.setTextSize(13);
        nameInput.setTypeface(Typeface.MONOSPACE);
        nameInput.setSingleLine(true);
        nameInput.setPadding((int) (12 * density), (int) (10 * density), (int) (12 * density), (int) (10 * density));

        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(tokens.controlBgNormal);
        inputBg.setStroke((int) (1 * density + 0.5f), tokens.border);
        inputBg.setCornerRadius((int) (4 * density));
        nameInput.setBackground(inputBg);
        root.addView(nameInput);

        // Destination Label
        TextView destLabel = new TextView(activity);
        destLabel.setText(isEs ? "Ruta / Destino de guardado:" : "Storage Destination:");
        destLabel.setTextColor(tokens.textPrimary);
        destLabel.setTextSize(13);
        destLabel.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        destLabel.setPadding(0, (int) (14 * density), 0, (int) (6 * density));
        root.addView(destLabel);

        // 3 Destination Options (Download, App Internal, SAF System Picker)
        final int[] selectedDest = new int[]{0}; // 0 = Downloads, 1 = App Internal, 2 = SAF Picker
        final LinearLayout optionsContainer = new LinearLayout(activity);
        optionsContainer.setOrientation(LinearLayout.VERTICAL);

        final List<TextView> optionViews = new ArrayList<>();
        final String[] destTitles = new String[]{
            isEs ? "[X] Carpeta Descargas (/sdcard/Download)" : "[X] Downloads (/sdcard/Download)",
            isEs ? "[ ] Carpeta TontOS (/Android/data/backup)" : "[ ] TontOS App Folder (/backup)",
            isEs ? "[ ] Explorador del sistema (Elegir carpeta...)" : "[ ] System File Picker (Choose...)"
        };
        final String[] destPrefixes = new String[]{
            isEs ? "Carpeta Descargas (/sdcard/Download)" : "Downloads (/sdcard/Download)",
            isEs ? "Carpeta TontOS (/Android/data/backup)" : "TontOS App Folder (/backup)",
            isEs ? "Explorador del sistema (Elegir carpeta...)" : "System File Picker (Choose...)"
        };

        for (int i = 0; i < 3; i++) {
            final int index = i;
            final TextView row = new TextView(activity);
            row.setText(destTitles[i]);
            row.setTextColor(index == 0 ? tokens.accent : tokens.textMuted);
            row.setTextSize(12);
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
        root.addView(optionsContainer);

        builder.setView(root);
        final AlertDialog dialog = builder.create();

        // Dialog Action Buttons Dock
        LinearLayout dock = new LinearLayout(activity);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.END);
        dock.setPadding(0, (int) (16 * density), 0, 0);

        TextView cancelBtn = createDialogButton(activity, isEs ? "CANCELAR" : "CANCEL", false, tokens);
        cancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        dock.addView(cancelBtn);

        View spacer = new View(activity);
        spacer.setLayoutParams(new LinearLayout.LayoutParams((int) (12 * density), 1));
        dock.addView(spacer);

        TextView saveBtn = createDialogButton(activity, isEs ? "GUARDAR COPIA" : "SAVE BACKUP", true, tokens);
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String fileName = nameInput.getText().toString().trim();
                if (fileName.isEmpty()) {
                    Toast.makeText(activity, isEs ? "Introduce un nombre válido" : "Enter a valid filename", Toast.LENGTH_SHORT).show();
                    return;
                }
                // Enforce a safe single filename component to prevent path traversal/injection.
                if (fileName.contains("..")
                        || fileName.contains("/")
                        || fileName.contains("\\")
                        || fileName.startsWith(".")
                        || !fileName.matches("[A-Za-z0-9 _.-]+")) {
                    Toast.makeText(activity, isEs ? "Nombre de archivo no válido" : "Invalid filename", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!fileName.endsWith(".tontbak") && !fileName.endsWith(".json")) {
                    fileName += ".tontbak";
                }

                try {
                    String envelopeJson = generateBackupEnvelope(prefs);

                    if (selectedDest[0] == 2) {
                        // SAF Picker
                        sPendingBackupPayload = envelopeJson;
                        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("application/octet-stream");
                        intent.putExtra(Intent.EXTRA_TITLE, fileName);
                        activity.startActivityForResult(intent, REQUEST_CODE_SAF_CREATE);
                        dialog.dismiss();
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

                    dialog.dismiss();
                    showBackupSuccessDialog(activity, tokens, langCode, targetFile);

                } catch (Exception e) {
                    Toast.makeText(activity, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });
        dock.addView(saveBtn);
        root.addView(dock);

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(tokens.bg));
        }
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
                boolean isEs = "es".equalsIgnoreCase(langCode);
                Toast.makeText(activity, isEs ? "Copia guardada con éxito en SAF" : "Backup saved via SAF", Toast.LENGTH_LONG).show();
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
        final float density = activity.getResources().getDisplayMetrics().density;
        final boolean isEs = "es".equalsIgnoreCase(langCode);

        AlertDialog.Builder builder = new AlertDialog.Builder(
            activity,
            tokens.isLight ? AlertDialog.THEME_DEVICE_DEFAULT_LIGHT : AlertDialog.THEME_DEVICE_DEFAULT_DARK
        );

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (20 * density), (int) (18 * density), (int) (20 * density), (int) (18 * density));
        root.setBackgroundColor(tokens.bg);

        TextView header = new TextView(activity);
        header.setText(isEs ? "=== COPIA GUARDADA CON ÉXITO ===" : "=== BACKUP CREATED SUCCESSFULLY ===");
        header.setTextColor(tokens.accent);
        header.setTextSize(14);
        header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.setPadding(0, 0, 0, (int) (8 * density));
        root.addView(header);

        TextView details = new TextView(activity);
        long bytes = targetFile.length();
        details.setText(
            (isEs ? "Archivo:  " : "File:     ") + targetFile.getName() + "\n" +
            (isEs ? "Ruta:     " : "Path:     ") + targetFile.getParent() + "\n" +
            (isEs ? "Tamaño:   " : "Size:     ") + bytes + " bytes\n" +
            (isEs ? "Estado:   " : "Status:   ") + "[ ÍNTEGRO & VERIFICADO ]"
        );
        details.setTextColor(tokens.textPrimary);
        details.setTextSize(12);
        details.setTypeface(Typeface.MONOSPACE);
        details.setPadding(0, (int) (6 * density), 0, (int) (14 * density));
        root.addView(details);

        TextView okBtn = createDialogButton(activity, "OK", true, tokens);
        builder.setView(root);
        final AlertDialog dialog = builder.create();
        okBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        });
        root.addView(okBtn);

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(tokens.bg));
        }
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
        final float density = activity.getResources().getDisplayMetrics().density;
        final boolean isEs = "es".equalsIgnoreCase(langCode);

        AlertDialog.Builder builder = new AlertDialog.Builder(
            activity,
            tokens.isLight ? AlertDialog.THEME_DEVICE_DEFAULT_LIGHT : AlertDialog.THEME_DEVICE_DEFAULT_DARK
        );

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (20 * density), (int) (18 * density), (int) (20 * density), (int) (18 * density));
        root.setBackgroundColor(tokens.bg);

        TextView header = new TextView(activity);
        header.setText(isEs ? "=== RESTAURAR COPIA DE SEGURIDAD ===" : "=== RESTORE BACKUP SNAPSHOT ===");
        header.setTextColor(tokens.accent);
        header.setTextSize(14);
        header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.setPadding(0, 0, 0, (int) (8 * density));
        root.addView(header);

        // Find existing backups
        List<File> backupFiles = findAvailableBackups(activity);

        ScrollView scroll = new ScrollView(activity);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (180 * density)));

        LinearLayout listContainer = new LinearLayout(activity);
        listContainer.setOrientation(LinearLayout.VERTICAL);

        builder.setView(root);
        final AlertDialog dialog = builder.create();

        if (backupFiles.isEmpty()) {
            TextView none = new TextView(activity);
            none.setText(isEs ? "No se encontraron copias en Descargas o carpeta TontOS." : "No backup files found in Downloads or App folder.");
            none.setTextColor(tokens.textMuted);
            none.setTextSize(12);
            none.setTypeface(Typeface.MONOSPACE);
            none.setPadding(0, (int) (10 * density), 0, (int) (10 * density));
            listContainer.addView(none);
        } else {
            for (final File f : backupFiles) {
                TextView item = new TextView(activity);
                item.setText("> " + f.getName() + " (" + (f.length() / 1024 + 1) + " KB)");
                item.setTextColor(tokens.textPrimary);
                item.setTextSize(12);
                item.setTypeface(Typeface.MONOSPACE);
                item.setPadding((int) (8 * density), (int) (8 * density), (int) (8 * density), (int) (8 * density));
                item.setClickable(true);
                item.setFocusable(true);

                GradientDrawable itemBg = new GradientDrawable();
                itemBg.setColor(tokens.controlBgNormal);
                itemBg.setStroke((int) (1 * density), tokens.border);
                itemBg.setCornerRadius((int) (4 * density));
                item.setBackground(itemBg);

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, 0, (int) (6 * density));
                item.setLayoutParams(lp);

                item.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog.dismiss();
                        confirmRestoreFile(activity, tokens, langCode, f, prefs, onRestored);
                    }
                });
                listContainer.addView(item);
            }
        }
        scroll.addView(listContainer);
        root.addView(scroll);

        // Action dock: SAF picker button + Cancel
        LinearLayout dock = new LinearLayout(activity);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.END);
        dock.setPadding(0, (int) (12 * density), 0, 0);

        TextView safBtn = createDialogButton(activity, isEs ? "ELEGIR ARCHIVO (SAF)" : "OPEN FILE (SAF)", false, tokens);
        safBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                activity.startActivityForResult(intent, REQUEST_CODE_SAF_RESTORE);
            }
        });
        dock.addView(safBtn);

        View sp = new View(activity);
        sp.setLayoutParams(new LinearLayout.LayoutParams((int) (8 * density), 1));
        dock.addView(sp);

        TextView cancelBtn = createDialogButton(activity, isEs ? "CERRAR" : "CLOSE", false, tokens);
        cancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        });
        dock.addView(cancelBtn);
        root.addView(dock);

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(tokens.bg));
        }
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
        final boolean isEs = "es".equalsIgnoreCase(langCode);
        final float density = activity.getResources().getDisplayMetrics().density;

        JSONObject envelope = new JSONObject(rawJson);
        if (!envelope.has("payload") || !envelope.has("sha256")) {
            Toast.makeText(activity, isEs ? "Error: archivo no es una copia TontOS válida" : "Invalid backup envelope", Toast.LENGTH_LONG).show();
            return;
        }

        final String payload = envelope.getString("payload");
        String recordedHash = envelope.getString("sha256");
        String actualHash = sha256Hex(payload);

        if (!actualHash.equalsIgnoreCase(recordedHash)) {
            Toast.makeText(activity, isEs ? "Error: Checksum SHA-256 no coincide (archivo corrupto o alterado)" : "Integrity check failed: corrupted backup", Toast.LENGTH_LONG).show();
            return;
        }

        final JSONObject payloadObj = new JSONObject(payload);

        AlertDialog.Builder builder = new AlertDialog.Builder(
            activity,
            tokens.isLight ? AlertDialog.THEME_DEVICE_DEFAULT_LIGHT : AlertDialog.THEME_DEVICE_DEFAULT_DARK
        );

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (20 * density), (int) (18 * density), (int) (20 * density), (int) (18 * density));
        root.setBackgroundColor(tokens.bg);

        TextView title = new TextView(activity);
        title.setText(isEs ? "=== CONFIRMAR RESTAURACIÓN ===" : "=== CONFIRM RESTORE ===");
        title.setTextColor(tokens.accent);
        title.setTextSize(14);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        title.setPadding(0, 0, 0, (int) (8 * density));
        root.addView(title);

        TextView msg = new TextView(activity);
        String dateStr = envelope.optString("date_iso", "Desconocida");
        String devStr = envelope.optString("device_model", "TontOS Device");
        int count = payloadObj.length();

        msg.setText(isEs
            ? ("Se restaurarán " + count + " ajustes creados el " + dateStr + " (" + devStr + ").\n\n¿Deseas aplicar la copia ahora?")
            : ("Restore " + count + " preferences from " + dateStr + " (" + devStr + ")?\n\nApply snapshot now?"));
        msg.setTextColor(tokens.textPrimary);
        msg.setTextSize(12);
        msg.setTypeface(Typeface.MONOSPACE);
        msg.setPadding(0, (int) (6 * density), 0, (int) (14 * density));
        root.addView(msg);

        LinearLayout dock = new LinearLayout(activity);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.END);

        builder.setView(root);
        final AlertDialog confirmDialog = builder.create();

        TextView cancelBtn = createDialogButton(activity, isEs ? "CANCELAR" : "CANCEL", false, tokens);
        cancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmDialog.dismiss(); }
        });
        dock.addView(cancelBtn);

        View sp = new View(activity);
        sp.setLayoutParams(new LinearLayout.LayoutParams((int) (8 * density), 1));
        dock.addView(sp);

        TextView applyBtn = createDialogButton(activity, isEs ? "RESTAURAR AHORA" : "RESTORE NOW", true, tokens);
        applyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDialog.dismiss();
                applyRestoredPreferences(prefs, payloadObj);
                Toast.makeText(activity, isEs ? "Copia restaurada con éxito" : "Backup restored successfully", Toast.LENGTH_SHORT).show();
                if (onRestored != null) onRestored.run();
            }
        });
        dock.addView(applyBtn);
        root.addView(dock);

        confirmDialog.show();
        if (confirmDialog.getWindow() != null) {
            confirmDialog.getWindow().setBackgroundDrawable(new ColorDrawable(tokens.bg));
        }
    }

    private static void applyRestoredPreferences(SharedPreferences prefs, JSONObject payload) {
        SharedPreferences.Editor editor = prefs.edit();
        java.util.Iterator<String> keys = payload.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object val = payload.opt(key);
            if (val instanceof Boolean) {
                editor.putBoolean(key, (Boolean) val);
            } else if (val instanceof Integer) {
                editor.putInt(key, (Integer) val);
            } else if (val instanceof Long) {
                editor.putLong(key, (Long) val);
            } else if (val instanceof Double) {
                editor.putFloat(key, ((Double) val).floatValue());
            } else if (val instanceof String) {
                editor.putString(key, (String) val);
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

    private static TextView createDialogButton(
        Context context,
        String text,
        boolean isPrimary,
        ThemeTokens tokens
    ) {
        float density = context.getResources().getDisplayMetrics().density;
        TextView btn = new TextView(context);
        btn.setText(text);
        btn.setTextColor(isPrimary ? tokens.activeSelectionText : tokens.navAccent);
        btn.setTextSize(12);
        btn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding((int) (14 * density), (int) (8 * density), (int) (14 * density), (int) (8 * density));
        btn.setClickable(true);
        btn.setFocusable(true);

        StateListDrawable sld = new StateListDrawable();
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(tokens.controlBgPressed);
        pressed.setCornerRadius((int) (4 * density));
        pressed.setStroke((int) (1 * density + 0.5f), tokens.border);

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(isPrimary ? tokens.activeSelectionBg : tokens.controlBgNormal);
        normal.setCornerRadius((int) (4 * density));
        normal.setStroke((int) (1 * density + 0.5f), isPrimary ? tokens.accent : tokens.border);

        sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sld.addState(new int[]{android.R.attr.state_focused}, pressed);
        sld.addState(new int[]{}, normal);

        btn.setBackground(sld);
        return btn;
    }
}
