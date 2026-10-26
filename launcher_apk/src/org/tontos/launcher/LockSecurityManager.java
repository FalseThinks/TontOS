package org.tontos.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.biometrics.BiometricManager;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;

/**
 * Lock Screen Security & Biometric Manager for TontOS.
 * Provides SHA-256 salted credential verification for Pattern and PIN,
 * and integrates native fingerprint hardware authentication and enrollment.
 */
public final class LockSecurityManager {

    private static final String TAG = "LockSecurityManager";
    public static final String PREFS_NAME = "tontos_prefs";

    public static final String KEY_SECURITY_TYPE = "pref_lock_security_type";
    public static final String KEY_PATTERN_HASH = "pref_lock_pattern_hash";
    public static final String KEY_PIN_HASH = "pref_lock_pin_hash";
    public static final String KEY_SALT = "pref_lock_security_salt";
    public static final String KEY_BIOMETRIC_ENABLED = "pref_lock_biometric_enabled";

    public enum SecurityType {
        NONE("NONE"),
        PATTERN("PATTERN"),
        PIN("PIN");

        private final String id;

        SecurityType(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public static SecurityType fromId(String id) {
            if (id == null) return NONE;
            for (SecurityType type : values()) {
                if (type.id.equalsIgnoreCase(id) || type.name().equalsIgnoreCase(id)) {
                    return type;
                }
            }
            return NONE;
        }
    }

    public interface FingerprintAuthCallback {
        void onAuthenticated();
        void onFailed();
        void onError(String message);
    }

    private LockSecurityManager() {}

    private static SharedPreferences getPrefs(Context context) {
        if (context == null) return null;
        return MainActivity.getSafeSharedPreferences(context, PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static SecurityType getSecurityType(Context context) {
        SharedPreferences prefs = getPrefs(context);
        if (prefs == null) return SecurityType.NONE;
        String id = prefs.getString(KEY_SECURITY_TYPE, SecurityType.NONE.getId());
        return SecurityType.fromId(id);
    }

    public static void setSecurityType(Context context, SecurityType type) {
        if (type == null) return;
        SharedPreferences prefs = getPrefs(context);
        if (prefs != null) prefs.edit().putString(KEY_SECURITY_TYPE, type.getId()).apply();
    }

    public static boolean hasSecurity(Context context) {
        return getSecurityType(context) != SecurityType.NONE;
    }

    public static String getOrCreateSalt(Context context) {
        SharedPreferences prefs = getPrefs(context);
        if (prefs == null) return "fallback_salt_0";
        String salt = prefs.getString(KEY_SALT, null);
        if (salt == null || salt.trim().isEmpty()) {
            byte[] saltBytes = new byte[16];
            new SecureRandom().nextBytes(saltBytes);
            salt = Base64.encodeToString(saltBytes, Base64.NO_WRAP);
            prefs.edit().putString(KEY_SALT, salt).apply();
        }
        return salt;
    }

    private static final int PBKDF2_ITERATIONS = 100_000;
    private static final int PBKDF2_KEY_LENGTH = 256;

    public static String hashCredential(String credential, String salt) {
        if (credential == null) credential = "";
        if (salt == null) salt = "";
        try {
            javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
                credential.toCharArray(),
                salt.getBytes(StandardCharsets.UTF_8),
                PBKDF2_ITERATIONS,
                PBKDF2_KEY_LENGTH
            );
            javax.crypto.SecretKeyFactory skf = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = skf.generateSecret(spec).getEncoded();
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return "pbkdf2$" + sb.toString();
        } catch (Exception e) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] data = (salt + ":" + credential).getBytes(StandardCharsets.UTF_8);
                for (int i = 0; i < 50_000; i++) {
                    data = md.digest(data);
                }
                StringBuilder sb = new StringBuilder(data.length * 2);
                for (byte b : data) {
                    sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                    sb.append(Character.forDigit(b & 0xF, 16));
                }
                return "sha256$" + sb.toString();
            } catch (Exception ex) {
                Log.e(TAG, "Cryptographic failure in credential hashing", ex);
                return "";
            }
        }
    }

    private static boolean verifyCredentialMatch(String credential, String salt, String savedHash) {
        if (savedHash == null || savedHash.isEmpty()) return false;
        if (savedHash.startsWith("pbkdf2$") || savedHash.startsWith("sha256$")) {
            String computed = hashCredential(credential, salt);
            return MessageDigest.isEqual(savedHash.getBytes(StandardCharsets.UTF_8), computed.getBytes(StandardCharsets.UTF_8));
        }
        // Legacy single-round SHA-256 migration
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] digest = md.digest(credential.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return MessageDigest.isEqual(savedHash.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    public static String patternToString(List<Integer> pattern) {
        if (pattern == null || pattern.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pattern.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(pattern.get(i));
        }
        return sb.toString();
    }

    public static boolean setPattern(Context context, List<Integer> pattern) {
        if (context == null || pattern == null || pattern.size() < 4) return false;
        String raw = patternToString(pattern);
        String salt = getOrCreateSalt(context);
        String hash = hashCredential(raw, salt);
        SharedPreferences prefs = getPrefs(context);
        if (prefs == null) return false;
        prefs.edit()
            .putString(KEY_PATTERN_HASH, hash)
            .putString(KEY_SECURITY_TYPE, SecurityType.PATTERN.getId())
            .apply();
        return true;
    }

    public static boolean verifyPattern(Context context, List<Integer> pattern) {
        if (context == null || pattern == null || pattern.isEmpty()) return false;
        SharedPreferences prefs = getPrefs(context);
        if (prefs == null) return false;
        String savedHash = prefs.getString(KEY_PATTERN_HASH, null);
        if (savedHash == null) return false;
        String raw = patternToString(pattern);
        String salt = getOrCreateSalt(context);
        boolean matched = verifyCredentialMatch(raw, salt, savedHash);
        if (matched && !savedHash.startsWith("pbkdf2$")) {
            setPattern(context, pattern);
        }
        return matched;
    }

    public static boolean setPin(Context context, String pin) {
        if (context == null || pin == null || pin.length() < 4) return false;
        String salt = getOrCreateSalt(context);
        String hash = hashCredential(pin, salt);
        SharedPreferences prefs = getPrefs(context);
        if (prefs == null) return false;
        prefs.edit()
            .putString(KEY_PIN_HASH, hash)
            .putString(KEY_SECURITY_TYPE, SecurityType.PIN.getId())
            .apply();
        return true;
    }

    public static boolean verifyPin(Context context, String pin) {
        if (context == null || pin == null || pin.isEmpty()) return false;
        SharedPreferences prefs = getPrefs(context);
        if (prefs == null) return false;
        String savedHash = prefs.getString(KEY_PIN_HASH, null);
        if (savedHash == null) return false;
        String salt = getOrCreateSalt(context);
        boolean matched = verifyCredentialMatch(pin, salt, savedHash);
        if (matched && !savedHash.startsWith("pbkdf2$")) {
            setPin(context, pin);
        }
        return matched;
    }

    public static boolean hasPin(Context context) {
        SharedPreferences prefs = getPrefs(context);
        return prefs != null && prefs.getString(KEY_PIN_HASH, null) != null;
    }

    public static boolean hasPattern(Context context) {
        SharedPreferences prefs = getPrefs(context);
        return prefs != null && prefs.getString(KEY_PATTERN_HASH, null) != null;
    }

    public static void clearSecurity(Context context) {
        SharedPreferences prefs = getPrefs(context);
        if (prefs != null) {
            prefs.edit()
                .remove(KEY_PATTERN_HASH)
                .remove(KEY_PIN_HASH)
                .putString(KEY_SECURITY_TYPE, SecurityType.NONE.getId())
                .apply();
        }
    }

    public static void clearCredentials(Context context) {
        clearSecurity(context);
    }

    // --- Biometric & Fingerprint Integration ---

    public static boolean isFingerprintHardwareAvailable(Context context) {
        if (context == null) return false;
        try {
            FingerprintManager fpm = (FingerprintManager) context.getSystemService(Context.FINGERPRINT_SERVICE);
            if (fpm != null && fpm.isHardwareDetected()) {
                return true;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                BiometricManager bm = (BiometricManager) context.getSystemService(Context.BIOMETRIC_SERVICE);
                if (bm != null) {
                    int can = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.BIOMETRIC_STRONG);
                    return can != BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE && can != BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Fingerprint hardware check error: " + t.getMessage());
        }
        return false;
    }

    public static boolean hasEnrolledFingerprints(Context context) {
        if (context == null) return false;
        try {
            FingerprintManager fpm = (FingerprintManager) context.getSystemService(Context.FINGERPRINT_SERVICE);
            if (fpm != null && fpm.isHardwareDetected() && fpm.hasEnrolledFingerprints()) {
                return true;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                BiometricManager bm = (BiometricManager) context.getSystemService(Context.BIOMETRIC_SERVICE);
                if (bm != null) {
                    int result = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.BIOMETRIC_STRONG);
                    return result == BiometricManager.BIOMETRIC_SUCCESS;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Enrolled fingerprints check error: " + t.getMessage());
        }
        return false;
    }

    public static boolean isFingerprintEnabled(Context context) {
        SharedPreferences prefs = getPrefs(context);
        return prefs != null && prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true);
    }

    public static void setFingerprintEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = getPrefs(context);
        if (prefs != null) prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply();
    }

    public static void launchFingerprintEnrollment(Activity activity) {
        if (activity == null) return;
        try {
            Intent intent = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                intent = new Intent(Settings.ACTION_BIOMETRIC_ENROLL);
                intent.putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                    BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.BIOMETRIC_WEAK);
            }
            if (intent == null || activity.getPackageManager().resolveActivity(intent, 0) == null) {
                intent = new Intent(Settings.ACTION_FINGERPRINT_ENROLL);
            }
            if (activity.getPackageManager().resolveActivity(intent, 0) == null) {
                intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
            }
            activity.startActivity(intent);
        } catch (Throwable t) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
            } catch (Throwable ignored) {}
        }
    }

    public static void launchAospLockSettings(Activity activity) {
        if (activity == null) return;
        try {
            Intent intent = new Intent(android.app.admin.DevicePolicyManager.ACTION_SET_NEW_PASSWORD);
            if (activity.getPackageManager().resolveActivity(intent, 0) == null) {
                intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
            }
            activity.startActivity(intent);
        } catch (Throwable t) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
            } catch (Throwable ignored) {}
        }
    }

    public static CancellationSignal startFingerprintListener(Context context, final FingerprintAuthCallback callback) {
        if (context == null || callback == null) return null;
        if (!isFingerprintEnabled(context) || !hasEnrolledFingerprints(context)) return null;

        try {
            FingerprintManager fpm = (FingerprintManager) context.getSystemService(Context.FINGERPRINT_SERVICE);
            if (fpm == null || !fpm.isHardwareDetected() || !fpm.hasEnrolledFingerprints()) return null;

            final CancellationSignal signal = new CancellationSignal();
            fpm.authenticate(null, signal, 0, new FingerprintManager.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
                    callback.onAuthenticated();
                }

                @Override
                public void onAuthenticationFailed() {
                    callback.onFailed();
                }

                @Override
                public void onAuthenticationError(int errorCode, CharSequence errString) {
                    if (errorCode != FingerprintManager.FINGERPRINT_ERROR_CANCELED) {
                        callback.onError(errString != null ? errString.toString() : "Error " + errorCode);
                    }
                }
            }, null);
            return signal;
        } catch (Throwable t) {
            Log.w(TAG, "startFingerprintListener failed: " + t.getMessage());
            return null;
        }
    }

    // --- Haptic Feedback ---

    public static void vibrateSuccess(Context context) {
        if (context == null) return;
        try {
            Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(40);
                }
            }
        } catch (Throwable ignored) {}
    }

    public static void vibrateError(Context context) {
        if (context == null) return;
        try {
            Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                long[] pattern = {0, 60, 60, 60};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, -1));
                } else {
                    v.vibrate(pattern, -1);
                }
            }
        } catch (Throwable ignored) {}
    }
}
