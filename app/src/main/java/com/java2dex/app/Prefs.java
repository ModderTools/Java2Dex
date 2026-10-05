package com.java2dex.app;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences("j2d_prefs", Context.MODE_PRIVATE);
    }

    public static boolean dark(Context c) { return p(c).getBoolean("dark", false); }
    public static void dark(Context c, boolean v) { p(c).edit().putBoolean("dark", v).apply(); }

    public static String folder(Context c) { return p(c).getString("folder", null); }
    public static void folder(Context c, String v) { p(c).edit().putString("folder", v).apply(); }

    public static int fontSize(Context c) { return p(c).getInt("fontSize", 14); }
    public static void fontSize(Context c, int v) { p(c).edit().putInt("fontSize", v).apply(); }

    public static boolean wrap(Context c) { return p(c).getBoolean("wrap", true); }
    public static void wrap(Context c, boolean v) { p(c).edit().putBoolean("wrap", v).apply(); }

    /** D8 minimum API level — lower = more desugaring = runs on older Android */
    public static int minApi(Context c) { return p(c).getInt("minApi", 21); }
    public static void minApi(Context c, int v) { p(c).edit().putInt("minApi", v).apply(); }

    /** merge library jars into the produced DEX instead of using them as compile-only */
    public static boolean bundleLibs(Context c) { return p(c).getBoolean("bundleLibs", false); }
    public static void bundleLibs(Context c, boolean v) { p(c).edit().putBoolean("bundleLibs", v).apply(); }

    /** timestamp of the install the bundled android.jar was extracted for */
    public static long jarStamp(Context c) { return p(c).getLong("jarStamp", 0L); }
    public static void jarStamp(Context c, long v) { p(c).edit().putLong("jarStamp", v).apply(); }
}
