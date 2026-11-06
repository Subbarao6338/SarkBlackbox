package top.niunaijun.blackbox.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import top.niunaijun.blackbox.BlackBoxCore;

public class DnsConfigManager {
    public static final String PREF_NAME = "sark_dns_config";
    public static final String KEY_DNS_MODE = "dns_mode";
    public static final String KEY_CUSTOM_DNS = "custom_dns";

    public static final String MODE_SYSTEM = "system";
    public static final String MODE_CLOUDFLARE = "cloudflare";
    public static final String MODE_GOOGLE = "google";
    public static final String MODE_ADGUARD = "adguard";
    public static final String MODE_CUSTOM = "custom";

    public static final String HOST_CLOUDFLARE = "one.one.one.one";
    public static final String HOST_GOOGLE = "dns.google";
    public static final String HOST_ADGUARD = "dns.adguard.com";

    private static SharedPreferences getPrefs() {
        Context context = BlackBoxCore.getContext();
        if (context == null) return null;
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static String getDnsMode() {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) return MODE_SYSTEM;
        return prefs.getString(KEY_DNS_MODE, MODE_SYSTEM);
    }

    public static void setDnsMode(String mode) {
        SharedPreferences prefs = getPrefs();
        if (prefs != null) {
            prefs.edit().putString(KEY_DNS_MODE, mode).apply();
        }
    }

    public static String getCustomDns() {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) return "";
        return prefs.getString(KEY_CUSTOM_DNS, "");
    }

    public static void setCustomDns(String customDns) {
        SharedPreferences prefs = getPrefs();
        if (prefs != null) {
            prefs.edit().putString(KEY_CUSTOM_DNS, customDns).apply();
        }
    }

    public static boolean isPrivateDnsActive() {
        String mode = getDnsMode();
        return !MODE_SYSTEM.equals(mode);
    }

    public static String getPrivateDnsServerName() {
        String mode = getDnsMode();
        switch (mode) {
            case MODE_CLOUDFLARE:
                return HOST_CLOUDFLARE;
            case MODE_GOOGLE:
                return HOST_GOOGLE;
            case MODE_ADGUARD:
                return HOST_ADGUARD;
            case MODE_CUSTOM:
                String custom = getCustomDns();
                if (!TextUtils.isEmpty(custom)) {
                    if (custom.startsWith("https://")) {
                        custom = custom.substring(8);
                    } else if (custom.startsWith("http://")) {
                        custom = custom.substring(7);
                    }
                    int slashIdx = custom.indexOf('/');
                    if (slashIdx != -1) {
                        custom = custom.substring(0, slashIdx);
                    }
                    return custom;
                }
                return null;
            case MODE_SYSTEM:
            default:
                return null;
        }
    }

    public static List<InetAddress> getActiveDnsServers() {
        String mode = getDnsMode();
        List<String> ips = new ArrayList<>();
        switch (mode) {
            case MODE_CLOUDFLARE:
                ips.add("1.1.1.1");
                ips.add("1.0.0.1");
                break;
            case MODE_GOOGLE:
                ips.add("8.8.8.8");
                ips.add("8.8.4.4");
                break;
            case MODE_ADGUARD:
                ips.add("94.140.14.14");
                ips.add("94.140.15.15");
                break;
            case MODE_CUSTOM:
                String custom = getCustomDns();
                if (!TextUtils.isEmpty(custom)) {
                    String[] parts = custom.split("[,; ]+");
                    for (String part : parts) {
                        String clean = part.trim();
                        if (!clean.isEmpty()) {
                            ips.add(clean);
                        }
                    }
                }
                break;
            case MODE_SYSTEM:
            default:
                break;
        }

        if (ips.isEmpty()) {
            return Collections.emptyList();
        }

        List<InetAddress> addresses = new ArrayList<>();
        for (String ip : ips) {
            try {
                addresses.add(InetAddress.getByName(ip));
            } catch (Exception ignored) {
            }
        }
        return addresses;
    }
}
