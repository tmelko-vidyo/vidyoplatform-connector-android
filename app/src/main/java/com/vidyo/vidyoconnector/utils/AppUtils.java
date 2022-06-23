package com.vidyo.vidyoconnector.utils;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.vidyo.vidyoconnector.BuildConfig;
import com.vidyo.vidyoconnector.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public class AppUtils {

    private static final String LOGS_FOLDER = "VidyoConnectorLogs";
    private static final String LOG_FILE = "VidyoConnectorLog.log";

    /**
     * Log file is create individually for every session
     *
     * @param context {@link Context}
     * @return log file path
     */
    public static String configLogFile(Context context) {
        File cacheDir = context.getCacheDir();
        File logDir = new File(cacheDir, LOGS_FOLDER);
        deleteRecursive(logDir);

        File logFile = new File(logDir, LOG_FILE);
        logFile.mkdirs();

        String[] logFiles = logDir.list();
        if (logFiles != null)
            for (String file : logFiles) Logger.i(AppUtils.class, "Cached log file: " + file);

        return logFile.getAbsolutePath();
    }

    /**
     * Expose log file URI for sharing.
     *
     * @param context {@link Context}
     * @return log file uri.
     */
    private static Uri logFileUri(Context context) {
        File cacheDir = context.getCacheDir();
        File logDir = new File(cacheDir, LOGS_FOLDER);
        File logFile = new File(logDir, LOG_FILE);

        if (!logFile.exists()) return null;

        return FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".file.provider", logFile);
    }

    private static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteRecursive(child);

        fileOrDirectory.delete();
    }

    /**
     * Send email with log file
     */
    public static void sendLogs(Context context) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("message/rfc822");
        intent.putExtra(Intent.EXTRA_SUBJECT, "Vidyo Connector Sample Logs");
        intent.putExtra(Intent.EXTRA_TEXT, "Logs attached..." + additionalInfo());

        intent.putExtra(Intent.EXTRA_STREAM, logFileUri(context));

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(Intent.createChooser(intent, "Choose sender..."));
        } catch (Exception sendReportEx) {
            sendReportEx.printStackTrace();
        }
    }

    private static String additionalInfo() {
        return "\n\nModel: " + Build.MODEL +
                "\n" + "Manufactured: " + Build.MANUFACTURER +
                "\n" + "Brand: " + Build.BRAND +
                "\n" + "Android OS version: " + Build.VERSION.RELEASE +
                "\n" + "Hardware : " + Build.HARDWARE +
                "\n" + "SDK Version : " + Build.VERSION.SDK_INT;
    }

    public static boolean isLandscape(Resources resources) {
        return resources.getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    public static <T> void dump(List<T> list) {
        for (T t : list) Logger.i("Item: %s", t.toString());
    }

    public static boolean isEmulator() {
        return (Build.MANUFACTURER.contains("Genymotion")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.toLowerCase().contains("droid4x")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.HARDWARE.equalsIgnoreCase("goldfish")
                || Build.HARDWARE.equalsIgnoreCase("vbox86")
                || Build.HARDWARE.toLowerCase().contains("nox")
                || Build.FINGERPRINT.startsWith("generic")
                || Build.PRODUCT.equalsIgnoreCase("sdk")
                || Build.PRODUCT.equalsIgnoreCase("google_sdk")
                || Build.PRODUCT.equalsIgnoreCase("sdk_x86")
                || Build.PRODUCT.equalsIgnoreCase("vbox86p")
                || Build.PRODUCT.toLowerCase().contains("nox")
                || Build.BOARD.toLowerCase().contains("nox")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")));
    }

    private static final String CERTIFICATE_RAW_NAME = "ca-certificates.crt";

    public static String writeCaCertificates(Context context) {
        try {
            InputStream caCertStream = context.getResources().openRawResource(R.raw.ca_certificates);

            File caCertDirectory = new File(context.getFilesDir() + File.separator);
            File caFile = new File(caCertDirectory, CERTIFICATE_RAW_NAME);

            FileOutputStream caCertFile = new FileOutputStream(caFile);

            byte[] buffer = new byte[1024];
            int len;

            while ((len = caCertStream.read(buffer)) != -1) {
                caCertFile.write(buffer, 0, len);
            }

            caCertStream.close();
            caCertFile.close();
            return caFile.getPath();
        } catch (Exception e) {
            return null;
        }
    }

    public static SSLContext getSslContextForCertificateFile(Context context, String fileName) {
        try {
            KeyStore keyStore = getKeyStore(context, fileName);
            SSLContext sslContext = SSLContext.getInstance("SSL");
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);
            sslContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());
            return sslContext;
        } catch (Exception e) {
            String msg = "Error during creating SslContext for certificate from assets";
            Log.e("SslUtilsAndroid", msg, e);
            throw new RuntimeException(msg);
        }
    }

    private static KeyStore getKeyStore(Context context, String fileName) {
        KeyStore keyStore = null;
        try {
            AssetManager assetManager = context.getAssets();
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            InputStream caInput = assetManager.open(fileName);
            Certificate ca;
            try {
                ca = cf.generateCertificate(caInput);
                Log.d("SslUtilsAndroid", "ca=" + ((X509Certificate) ca).getSubjectDN());
            } finally {
                caInput.close();
            }

            String keyStoreType = KeyStore.getDefaultType();
            keyStore = KeyStore.getInstance(keyStoreType);
            keyStore.load(null, null);
            keyStore.setCertificateEntry("ca", ca);
        } catch (Exception e) {
            Log.e("SslUtilsAndroid", "Error during getting keystore", e);
        }
        return keyStore;
    }
}