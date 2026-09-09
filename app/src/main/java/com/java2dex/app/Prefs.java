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
}
