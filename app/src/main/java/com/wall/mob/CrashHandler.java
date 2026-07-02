package com.wall.mob;

import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Global exception handler that catches all uncaught exceptions,
 * saves crash logs, and launches CrashLogActivity automatically.
 */
public class CrashHandler implements Thread.UncaughtExceptionHandler {
    
    private static final String TAG = "CrashHandler";
    private static final String CRASH_LOG_DIR = "crash_logs";
    
    private final Context context;
    private final Thread.UncaughtExceptionHandler defaultHandler;
    
    public CrashHandler(Context context) {
        this.context = context.getApplicationContext();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }
    
    /**
     * Initialize the crash handler as the default exception handler
     */
    public static void initialize(Context context) {
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(context));
        Log.d(TAG, "CrashHandler initialized");
    }
    
    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        try {
            // Save crash log
            String crashLog = saveCrashLog(throwable);
            
            // Launch CrashLogActivity
            launchCrashLogActivity(crashLog);
            
            // Give some time for the activity to start
            Thread.sleep(1000);
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling crash", e);
        } finally {
            // Call default handler or kill process
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            } else {
                Process.killProcess(Process.myPid());
                System.exit(10);
            }
        }
    }
    
    /**
     * Save crash log to file and return the log content
     */
    private String saveCrashLog(Throwable throwable) {
        try {
            // Create crash log directory
            File crashDir = new File(context.getFilesDir(), CRASH_LOG_DIR);
            if (!crashDir.exists()) {
                crashDir.mkdirs();
            }
            
            // Generate crash log content
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new Date());
            
            StringBuilder logBuilder = new StringBuilder();
            logBuilder.append("=== CRASH REPORT ===\n");
            logBuilder.append("Time: ").append(timestamp).append("\n");
            logBuilder.append("App Version: ").append(BuildConfig.VERSION_NAME)
                    .append(" (").append(BuildConfig.VERSION_CODE).append(")\n");
            logBuilder.append("Android Version: ").append(android.os.Build.VERSION.RELEASE)
                    .append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n");
            logBuilder.append("Device: ").append(android.os.Build.MANUFACTURER)
                    .append(" ").append(android.os.Build.MODEL).append("\n");
            logBuilder.append("\n=== STACK TRACE ===\n");
            
            // Get stack trace
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            throwable.printStackTrace(pw);
            logBuilder.append(sw.toString());
            
            // Get cause if exists
            Throwable cause = throwable.getCause();
            if (cause != null) {
                logBuilder.append("\n=== CAUSED BY ===\n");
                StringWriter causeSw = new StringWriter();
                PrintWriter causePw = new PrintWriter(causeSw);
                cause.printStackTrace(causePw);
                logBuilder.append(causeSw.toString());
            }
            
            String logContent = logBuilder.toString();
            
            // Save to file
            String filename = "crash_" + System.currentTimeMillis() + ".log";
            File logFile = new File(crashDir, filename);
            
            FileWriter writer = new FileWriter(logFile);
            writer.write(logContent);
            writer.flush();
            writer.close();
            
            Log.d(TAG, "Crash log saved to: " + logFile.getAbsolutePath());
            
            // Keep only last 10 crash logs
            cleanOldLogs(crashDir);
            
            return logContent;
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving crash log", e);
            return getStackTraceString(throwable);
        }
    }
    
    /**
     * Launch CrashLogActivity to display the crash
     */
    private void launchCrashLogActivity(String crashLog) {
        try {
            Intent intent = new Intent(context, CrashLogActivity.class);
            intent.putExtra("crash_log", crashLog);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error launching CrashLogActivity", e);
        }
    }
    
    /**
     * Clean old crash logs, keep only last 10
     */
    private void cleanOldLogs(File crashDir) {
        try {
            File[] files = crashDir.listFiles();
            if (files != null && files.length > 10) {
                // Sort by last modified
                java.util.Arrays.sort(files, (f1, f2) -> 
                    Long.compare(f1.lastModified(), f2.lastModified()));
                
                // Delete oldest files
                for (int i = 0; i < files.length - 10; i++) {
                    files[i].delete();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning old logs", e);
        }
    }
    
    /**
     * Get stack trace as string
     */
    private String getStackTraceString(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
    
    /**
     * Get crash logs directory
     */
    public static File getCrashLogsDir(Context context) {
        return new File(context.getFilesDir(), CRASH_LOG_DIR);
    }
}
