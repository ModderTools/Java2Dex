package com.java2dex.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Project {

    public static final int ST_NONE = 0, ST_OK = 1, ST_ERROR = 2;

    public String id;
    public String name;
    public long createdAt;
    public long builtAt;
    public int status = ST_NONE;
    public long dexSize;

    private static final Object LOCK = new Object();

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("j2d_projects", Context.MODE_PRIVATE);
    }

    private static List<Project> allInOrder(Context c) {
        List<Project> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString("list", "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(fromJson(arr.getJSONObject(i)));
        } catch (Exception ignored) {}
        return out;
    }

    public static List<Project> all(Context c) {
        List<Project> out = allInOrder(c);
        Collections.reverse(out);
        return out;
    }

    public static Project byId(Context c, String id) {
        for (Project p : allInOrder(c)) if (p.id.equals(id)) return p;
        return null;
    }

    public static void upsert(Context c, Project p) {
        synchronized (LOCK) {
            List<Project> list = allInOrder(c);
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).id.equals(p.id)) {
                    list.set(i, p);
                    saveAll(c, list);
                    return;
                }
            }
            list.add(p);
            saveAll(c, list);
        }
    }

    public static void delete(Context c, String id) {
        synchronized (LOCK) {
            Project p = byId(c, id);
            if (p != null) {
                deleteDir(p.dir(c));
                File pub = p.publicDexDirNoCreate(c);
                if (pub.exists() && !isSharedWithOthers(c, p)) deleteDir(pub);
            }
            List<Project> list = allInOrder(c);
            List<Project> keep = new ArrayList<>();
            for (Project x : list) if (!x.id.equals(id)) keep.add(x);
            saveAll(c, keep);
        }
    }

    /** true when another project writes into the same output folder name */
    private static boolean isSharedWithOthers(Context c, Project me) {
        for (Project x : allInOrder(c)) {
            if (!x.id.equals(me.id) && x.safeName().equals(me.safeName())) return true;
        }
        return false;
    }

    /**
     * Removes only DEX + smali output of every project (never the folder itself,
     * so a custom output folder like /storage/emulated/0 can never be wiped).
     */
    public static int cleanOutputs(Context c) {
        int n = 0;
        for (Project p : allInOrder(c)) {
            File pub = p.publicDexDirNoCreate(c);
            if (!pub.exists()) continue;
            File[] fs = pub.listFiles();
            if (fs != null) for (File f : fs) {
                String nm = f.getName();
                if (nm.equals("smali") || (nm.startsWith("classes") && nm.endsWith(".dex"))) {
                    deleteDir(f);
                    n++;
                }
            }
            String[] left = pub.list();
            if (left != null && left.length == 0) pub.delete();
            p.status = ST_NONE;
            p.dexSize = 0;
            upsert(c, p);
        }
        return n;
    }

    /** "Name", "Name (2)", "Name (3)" … so two projects never share an output folder */
    public static String uniqueName(Context c, String wanted) {
        String base = wanted == null ? "" : wanted.trim();
        if (base.length() == 0) base = "project";
        List<Project> all = allInOrder(c);
        String cand = base;
        int i = 2;
        while (true) {
            boolean clash = false;
            Project probe = new Project();
            probe.name = cand;
            for (Project x : all) {
                if (x.safeName().equalsIgnoreCase(probe.safeName())) { clash = true; break; }
            }
            if (!clash) return cand;
            cand = base + " (" + i++ + ")";
        }
    }

    public static void clearAll(Context c) {
        synchronized (LOCK) { prefs(c).edit().clear().apply(); }
    }

    private static void saveAll(Context c, List<Project> list) {
        try {
            JSONArray arr = new JSONArray();
            for (Project p : list) arr.put(toJson(p));
            prefs(c).edit().putString("list", arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private static JSONObject toJson(Project p) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", p.id);
        o.put("name", p.name);
        o.put("createdAt", p.createdAt);
        o.put("builtAt", p.builtAt);
        o.put("status", p.status);
        o.put("dexSize", p.dexSize);
        return o;
    }

    private static Project fromJson(JSONObject o) {
        Project p = new Project();
        p.id = o.optString("id");
        p.name = o.optString("name");
        p.createdAt = o.optLong("createdAt");
        p.builtAt = o.optLong("builtAt");
        p.status = o.optInt("status");
        p.dexSize = o.optLong("dexSize");
        return p;
    }

    static void deleteDir(File f) {
        if (f == null || !f.exists()) return;
        File[] fs = f.listFiles();
        if (fs != null) for (File x : fs) deleteDir(x);
        f.delete();
    }

    public File dir(Context c)        { return new File(new File(c.getFilesDir(), "projects"), id); }
    public File srcDir(Context c)     { return new File(dir(c), "src"); }
    public File libsDir(Context c)    { return new File(dir(c), "libs"); }
    public File classesDir(Context c) { return new File(dir(c), "classes"); }
    public File dexTmpDir(Context c)  { return new File(dir(c), "dexout"); }
    public File logFile(Context c)    { return new File(dir(c), "build.log.txt"); }
    public File internalDexFile(Context c) { return new File(dexTmpDir(c), "classes.dex"); }

    public File publicDexDir(Context c) {
        File f = new File(java2dexRoot(c), safeName());
        if (!f.exists()) f.mkdirs();
        return f;
    }

    public File publicDexDirNoCreate(Context c) { return new File(java2dexRootNoCreate(c), safeName()); }

    public File publicDexFile(Context c) { return new File(publicDexDir(c), "classes.dex"); }

    /** every classes*.dex produced by the last build (multi-dex aware), sorted */
    public List<File> publicDexFiles(Context c) {
        List<File> out = new ArrayList<>();
        File[] fs = publicDexDirNoCreate(c).listFiles();
        if (fs != null) for (File f : fs) {
            String n = f.getName();
            if (f.isFile() && n.startsWith("classes") && n.endsWith(".dex")) out.add(f);
        }
        Collections.sort(out, (a, b) -> dexOrder(a.getName()) - dexOrder(b.getName()));
        return out;
    }

    private static int dexOrder(String n) {
        String mid = n.substring("classes".length(), n.length() - ".dex".length());
        if (mid.length() == 0) return 1;
        try { return Integer.parseInt(mid); } catch (Exception e) { return 9999; }
    }

    public int libCount(Context c) {
        File[] fs = libsDir(c).listFiles();
        return fs == null ? 0 : fs.length;
    }
    public File smaliDir(Context c)      { return new File(publicDexDir(c), "smali"); }

    public String safeName() {
        String s = name == null ? "" : name.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        return s.length() == 0 ? "project" : s;
    }

    /** custom folder if chosen in Settings, else /storage/emulated/0/Java2Dex */
    public static File java2dexRoot(Context c) {
        File f = java2dexRootNoCreate(c);
        if (!f.exists()) f.mkdirs();
        return f;
    }

    public static File java2dexRootNoCreate(Context c) {
        String custom = Prefs.folder(c);
        if (custom != null && custom.trim().length() > 0) return new File(custom.trim());
        return new File(Environment.getExternalStorageDirectory(), "Java2Dex");
    }

    public String createdText() { return fmt(createdAt); }

    public String dateText() { return builtAt <= 0 ? "Never built" : fmt(builtAt); }

    public static String fmtTime(long t) { return fmt(t); }

    private static String fmt(long t) {
        return new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(new Date(t));
    }
}
