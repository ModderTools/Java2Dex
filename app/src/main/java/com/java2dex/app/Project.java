package com.java2dex.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Project model + storage (SharedPreferences JSON) + path helpers */
public class Project {

    public static final int ST_NONE = 0, ST_OK = 1, ST_ERROR = 2;

    public String id;
    public String name;
    public long createdAt;
    public long builtAt;
    public int status = ST_NONE;
    public long dexSize;

    // ---------------- storage ----------------

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

    /** newest first */
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

    public static void delete(Context c, String id) {
        Project p = byId(c, id);
        if (p != null) {
            deleteDir(p.dir(c));
            deleteDir(p.publicDexDir(c));
        }
        List<Project> list = allInOrder(c);
        List<Project> keep = new ArrayList<>();
        for (Project x : list) if (!x.id.equals(id)) keep.add(x);
        saveAll(c, keep);
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

    // ---------------- paths ----------------

    public File dir(Context c)        { return new File(new File(c.getFilesDir(), "projects"), id); }
    public File srcDir(Context c)     { return new File(dir(c), "src"); }
    public File libsDir(Context c)    { return new File(dir(c), "libs"); }
    public File classesDir(Context c) { return new File(dir(c), "classes"); }
    public File dexTmpDir(Context c)  { return new File(dir(c), "dexout"); }
    public File logFile(Context c)    { return new File(dir(c), "build.log.txt"); }

    /** JAVA2DEX/<project>/classes.dex  — the user-facing output */
    public File publicDexDir(Context c)  { return new File(java2dexRoot(c), safeName()); }
    public File publicDexFile(Context c) { return new File(publicDexDir(c), "classes.dex"); }

    public String safeName() {
        String s = name == null ? "" : name.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        return s.length() == 0 ? "project" : s;
    }

    /** auto-generated on first launch (internal storage) */
    public static File java2dexRoot(Context c) {
        File f = new File(c.getFilesDir(), "JAVA2DEX");
        if (!f.exists()) f.mkdirs();
        return f;
    }

    // ---------------- text ----------------

    public String createdText() { return fmt(createdAt); }

    public String dateText() { return builtAt <= 0 ? "Never built" : fmt(builtAt); }

    private static String fmt(long t) {
        return new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(new Date(t));
    }
}
