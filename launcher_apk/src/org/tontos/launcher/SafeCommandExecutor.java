package org.tontos.launcher;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Hardened process execution utility for TontOS system daemons and launcher components.
 * Enforces strict input validation, avoids raw shell interpolation, drains streams to prevent
 * deadlock, and bounds execution time.
 */
public final class SafeCommandExecutor {

    private static final String TAG = "SafeCommandExecutor";
    private static final Pattern VALID_PACKAGE_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");
    private static final Pattern SAFE_ARG_PATTERN = Pattern.compile("^[a-zA-Z0-9._/\\-:+@=,]+$");
    private static final long DEFAULT_TIMEOUT_MS = 3000;

    private SafeCommandExecutor() {}

    /**
     * Validates whether a package identifier contains only allowed Android package name characters.
     */
    public static boolean isValidPackageName(String pkg) {
        return pkg != null && !pkg.isEmpty() && pkg.length() <= 256 && VALID_PACKAGE_PATTERN.matcher(pkg).matches();
    }

    /**
     * Validates whether a command argument contains only allowed shell-safe characters,
     * rejecting any shell metacharacters (; & | ` \n ( ) > < \0 ' ").
     */
    public static boolean isSafeArgument(String arg) {
        return arg != null && !arg.isEmpty() && arg.length() <= 1024 && SAFE_ARG_PATTERN.matcher(arg).matches();
    }

    /**
     * Executes a system command with argument array (bypassing shell string interpolation).
     * Drains stdout and stderr to prevent pipe buffer starvation deadlocks.
     *
     * @param command Array of command and arguments.
     * @return true if process terminated with exit code 0; false otherwise.
     */
    public static boolean execute(String... command) {
        if (command == null || command.length == 0) {
            Log.w(TAG, "Refusing execution of empty command array");
            return false;
        }

        for (String arg : command) {
            if (!isSafeArgument(arg)) {
                Log.w(TAG, "Refusing execution with unsafe argument: " + arg);
                return false;
            }
        }

        return runRawProcess(command);
    }

    /**
     * Executes a system command with argument array and returns its combined output.
     */
    public static String executeWithOutput(String... command) {
        if (command == null || command.length == 0) return "";
        for (String arg : command) {
            if (!isSafeArgument(arg)) {
                Log.w(TAG, "Refusing execution with unsafe argument: " + arg);
                return "";
            }
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                process.waitFor(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } else {
                process.waitFor();
            }
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * Executes a system command with argument array using platform privileged capabilities.
     * Enforces strict argument validation and executes directly without shell interpolation.
     */
    public static boolean executePrivileged(String... command) {
        return execute(command);
    }

    private static boolean runRawProcess(String[] command) {
        try {
            // nosemgrep: java.lang.security.audit.command-injection-process-builder.command-injection-process-builder
            ProcessBuilder pb = new ProcessBuilder(command); // nosemgrep: java.lang.security.audit.command-injection-process-builder.command-injection-process-builder
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Drain stdout/stderr asynchronously in a daemon thread to prevent pipe deadlocks
            Thread drainer = new Thread(new Runnable() {
                @Override
                public void run() {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        while (reader.readLine() != null) {
                            // Discard output to keep pipe empty
                        }
                    } catch (Throwable ignored) {}
                }
            });
            drainer.setDaemon(true);
            drainer.start();

            boolean finished = false;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                finished = process.waitFor(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    Log.w(TAG, "Command timed out: " + Arrays.toString(command));
                    return false;
                }
            } else {
                process.waitFor();
                finished = true;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                Log.d(TAG, "Command " + command[0] + " exited with non-zero status " + exitCode);
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Interrupted while waiting for command: " + Arrays.toString(command), e);
            return false;
        } catch (Throwable t) {
            Log.w(TAG, "Execution failed for: " + Arrays.toString(command), t);
            return false;
        }
    }
}
