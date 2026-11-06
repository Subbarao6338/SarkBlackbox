package top.niunaijun.blackbox.core;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.utils.CloseUtils;
import top.niunaijun.blackbox.utils.FileUtils;

public class SnapshotManager {
    private static final String TAG = "SnapshotManager";

    public interface SnapshotCallback {
        void onProgress(String status, int current, int total);
        void onSuccess(String message);
        void onError(Throwable error);
    }

    public static void backupSnapshot(Context context, Uri targetZipUri, SnapshotCallback callback) {
        new Thread(() -> {
            OutputStream out = null;
            ZipOutputStream zos = null;
            try {
                if (callback != null) {
                    callback.onProgress("Preparing virtualbox snapshot...", 0, 100);
                }

                out = context.getContentResolver().openOutputStream(targetZipUri);
                if (out == null) {
                    throw new IllegalStateException("Failed to open output stream for URI: " + targetZipUri);
                }
                zos = new ZipOutputStream(new BufferedOutputStream(out));

                // 1. System configs and user metadata
                File systemDir = BEnvironment.getSystemDir();
                if (systemDir.exists()) {
                    zipDirectory(systemDir, "system", zos);
                }

                // 2. Virtual root data (apps, data directories)
                File virtualRoot = BEnvironment.getVirtualRoot();
                if (virtualRoot.exists()) {
                    File[] subFiles = virtualRoot.listFiles();
                    if (subFiles != null) {
                        for (File sub : subFiles) {
                            if ("system".equals(sub.getName()) || "cache".equals(sub.getName()) || "proc".equals(sub.getName())) {
                                continue;
                            }
                            if (sub.isDirectory()) {
                                zipDirectory(sub, "virtual_root/" + sub.getName(), zos);
                            } else {
                                zipSingleFile(sub, "virtual_root/" + sub.getName(), zos);
                            }
                        }
                    }
                }

                // 3. External storage if any
                File extVirtualRoot = BEnvironment.getExternalVirtualRoot();
                if (extVirtualRoot != null && extVirtualRoot.exists()) {
                    zipDirectory(extVirtualRoot, "external_root", zos);
                }

                zos.finish();
                zos.flush();

                if (callback != null) {
                    callback.onProgress("Backup complete", 100, 100);
                    callback.onSuccess("Virtualbox snapshot backed up successfully.");
                }
            } catch (Throwable t) {
                Log.e(TAG, "Snapshot backup error", t);
                if (callback != null) {
                    callback.onError(t);
                }
            } finally {
                CloseUtils.close(zos);
                CloseUtils.close(out);
            }
        }, "SnapshotBackup").start();
    }

    public static void restoreSnapshot(Context context, Uri sourceZipUri, SnapshotCallback callback) {
        new Thread(() -> {
            InputStream in = null;
            ZipInputStream zis = null;
            try {
                if (callback != null) {
                    callback.onProgress("Restoring snapshot...", 0, 100);
                }

                in = context.getContentResolver().openInputStream(sourceZipUri);
                if (in == null) {
                    throw new IllegalStateException("Failed to open input stream for URI: " + sourceZipUri);
                }
                zis = new ZipInputStream(new BufferedInputStream(in));

                ZipEntry entry;
                byte[] buffer = new byte[8192];
                File virtualRoot = BEnvironment.getVirtualRoot();
                File systemDir = BEnvironment.getSystemDir();
                File extVirtualRoot = BEnvironment.getExternalVirtualRoot();

                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName();
                    // Prevent path traversal
                    if (name.contains("..")) {
                        zis.closeEntry();
                        continue;
                    }

                    File targetFile = null;
                    if (name.startsWith("system/")) {
                        targetFile = new File(systemDir, name.substring("system/".length()));
                    } else if (name.startsWith("virtual_root/")) {
                        targetFile = new File(virtualRoot, name.substring("virtual_root/".length()));
                    } else if (name.startsWith("external_root/")) {
                        if (extVirtualRoot != null) {
                            targetFile = new File(extVirtualRoot, name.substring("external_root/".length()));
                        }
                    }

                    if (targetFile != null) {
                        if (entry.isDirectory()) {
                            FileUtils.mkdirs(targetFile);
                        } else {
                            File parent = targetFile.getParentFile();
                            if (parent != null) {
                                FileUtils.mkdirs(parent);
                            }
                            FileOutputStream fos = new FileOutputStream(targetFile);
                            int len;
                            while ((len = zis.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                            fos.flush();
                            fos.close();
                        }
                    }
                    zis.closeEntry();
                }

                if (callback != null) {
                    callback.onProgress("Restoration complete", 100, 100);
                    callback.onSuccess("Virtualbox snapshot restored successfully. Please restart the app.");
                }
            } catch (Throwable t) {
                Log.e(TAG, "Snapshot restore error", t);
                if (callback != null) {
                    callback.onError(t);
                }
            } finally {
                CloseUtils.close(zis);
                CloseUtils.close(in);
            }
        }, "SnapshotRestore").start();
    }

    private static void zipDirectory(File folder, String parentPath, ZipOutputStream zos) {
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File file : files) {
            String entryPath = parentPath + "/" + file.getName();
            if (file.isDirectory()) {
                zipDirectory(file, entryPath, zos);
            } else {
                zipSingleFile(file, entryPath, zos);
            }
        }
    }

    private static void zipSingleFile(File file, String entryPath, ZipOutputStream zos) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            ZipEntry zipEntry = new ZipEntry(entryPath);
            zipEntry.setTime(file.lastModified());
            zos.putNextEntry(zipEntry);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, len);
            }
            zos.closeEntry();
        } catch (Exception e) {
            Log.w(TAG, "Failed to zip file " + file.getAbsolutePath() + ": " + e.getMessage());
        } finally {
            CloseUtils.close(fis);
        }
    }
}
