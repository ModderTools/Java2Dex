package com.java2dex.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;

public class SettingsActivity extends Activity {

    private Theme t;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        t = Theme.get(this);
        getWindow().setStatusBarColor(Ui.GREEN_DEEP);
        buildUi();
    }

    private LinearLayout.LayoutParams w() {
        return new LinearLayout.LayoutParams(0, -2, 1f);
    }

    private LinearLayout section(String title) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.outline(t.card, t.cardStroke, 16, 1, this));
        card.setElevation(Ui.dp(2));
        int pd = Ui.dp(16);
        card.setPadding(pd, pd, pd, pd);
        card.addView(Ui.text(this, title, 12, t.textSub, true));
        return card;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(t.bg);
        setContentView(scroll);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(col, new ScrollView.LayoutParams(-1, -2));

        col.addView(Ui.header(this, "⚙  Settings", "Customize Java2Dex", true));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(16), Ui.dp(16), Ui.dp(16), Ui.dp(30));
        col.addView(body, new LinearLayout.LayoutParams(-1, -2));

        // ---- appearance ----
        LinearLayout ap = section("APPEARANCE");
        LinearLayout darkRow = new LinearLayout(this);
        darkRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout darkCol = new LinearLayout(this);
        darkCol.setOrientation(LinearLayout.VERTICAL);
        darkCol.addView(Ui.text(this, "🌙  Dark mode", 14.5f, t.text, true));
        darkCol.addView(Ui.text(this, "Green-on-dark theme", 11, t.textSub, false));
        darkRow.addView(darkCol, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch darkSw = new Switch(this);
        darkSw.setChecked(Prefs.dark(this));
        darkSw.setOnCheckedChangeListener((b, v) -> {
            Prefs.dark(this, v);
            recreate();
        });
        darkRow.addView(darkSw, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams drp = new LinearLayout.LayoutParams(-1, -2);
        drp.topMargin = Ui.dp(12);
        ap.addView(darkRow, drp);
        body.addView(ap, margin());

        // ---- editor ----
        LinearLayout ed = section("CODE EDITOR");
        LinearLayout fontRow = new LinearLayout(this);
        fontRow.setGravity(Gravity.CENTER_VERTICAL);
        fontRow.addView(Ui.text(this, "🔤  Font size", 14.5f, t.text, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        TextView minus = Ui.button(this, "  A-  ", t.textSub);
        TextView val = Ui.text(this, String.valueOf(Prefs.fontSize(this)), 14, t.text, true);
        val.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-2, -2);
        vlp.leftMargin = Ui.dp(8);
        vlp.rightMargin = Ui.dp(8);
        TextView plus = Ui.button(this, "  A+  ", Ui.GREEN);
        fontRow.addView(minus);
        fontRow.addView(val, vlp);
        fontRow.addView(plus);
        minus.setOnClickListener(v -> setFont(Prefs.fontSize(this) - 2, val));
        plus.setOnClickListener(v -> setFont(Prefs.fontSize(this) + 2, val));
        LinearLayout.LayoutParams frp = new LinearLayout.LayoutParams(-1, -2);
        frp.topMargin = Ui.dp(12);
        ed.addView(fontRow, frp);

        LinearLayout wrapRow = new LinearLayout(this);
        wrapRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout wrapCol = new LinearLayout(this);
        wrapCol.setOrientation(LinearLayout.VERTICAL);
        wrapCol.addView(Ui.text(this, "📐  Word wrap", 14.5f, t.text, true));
        wrapCol.addView(Ui.text(this, "Wrap long lines in the editor", 11, t.textSub, false));
        wrapRow.addView(wrapCol, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch wrapSw = new Switch(this);
        wrapSw.setChecked(Prefs.wrap(this));
        wrapSw.setOnCheckedChangeListener((b, v) -> Prefs.wrap(this, v));
        wrapRow.addView(wrapSw, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams wrp = new LinearLayout.LayoutParams(-1, -2);
        wrp.topMargin = Ui.dp(12);
        ed.addView(wrapRow, wrp);
        body.addView(ed, margin());

        // ---- build ----
        LinearLayout bd = section("BUILD");
        LinearLayout apiRow = new LinearLayout(this);
        apiRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout apiCol = new LinearLayout(this);
        apiCol.setOrientation(LinearLayout.VERTICAL);
        apiCol.addView(Ui.text(this, "🎯  Minimum Android API", 14.5f, t.text, true));
        apiCol.addView(Ui.text(this, "Lower = runs on older devices (more desugaring)", 11, t.textSub, false));
        apiRow.addView(apiCol, new LinearLayout.LayoutParams(0, -2, 1f));
        final TextView apiVal = Ui.button(this, "API " + Prefs.minApi(this), Ui.GREEN);
        apiVal.setOnClickListener(v -> {
            final int[] levels = {21, 24, 26, 28, 30};
            String[] labels = new String[levels.length];
            for (int i = 0; i < levels.length; i++) labels[i] = "API " + levels[i] + androidName(levels[i]);
            Ui.dialog(this).setTitle("Minimum API level")
                    .setItems(labels, (d, which) -> {
                        Prefs.minApi(this, levels[which]);
                        apiVal.setText("API " + levels[which]);
                    }).show();
        });
        apiRow.addView(apiVal, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams arp = new LinearLayout.LayoutParams(-1, -2);
        arp.topMargin = Ui.dp(12);
        bd.addView(apiRow, arp);

        LinearLayout bundleRow = new LinearLayout(this);
        bundleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout bundleCol = new LinearLayout(this);
        bundleCol.setOrientation(LinearLayout.VERTICAL);
        bundleCol.addView(Ui.text(this, "📚  Bundle library jars", 14.5f, t.text, true));
        bundleCol.addView(Ui.text(this, "Merge libs into the DEX (off = compile-only)", 11, t.textSub, false));
        bundleRow.addView(bundleCol, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch bundleSw = new Switch(this);
        bundleSw.setChecked(Prefs.bundleLibs(this));
        bundleSw.setOnCheckedChangeListener((b, v) -> Prefs.bundleLibs(this, v));
        bundleRow.addView(bundleSw, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams brp2 = new LinearLayout.LayoutParams(-1, -2);
        brp2.topMargin = Ui.dp(14);
        bd.addView(bundleRow, brp2);
        body.addView(bd, margin());

        // ---- output ----
        LinearLayout out = section("OUTPUT FOLDER");
        TextView cur = Ui.text(this, "Current: "
                + Project.java2dexRoot(this).getAbsolutePath(), 11.5f, t.textSub, false);
        cur.setTypeface(Typeface.MONOSPACE);
        out.addView(cur);

        final EditText folderInput = new EditText(this);
        folderInput.setHint("/storage/emulated/0/MyFolder");
        folderInput.setTextSize(13);
        folderInput.setTextColor(t.text);
        folderInput.setHintTextColor(t.textSub);
        folderInput.setText(Prefs.folder(this) == null ? "" : Prefs.folder(this));
        folderInput.setBackground(Ui.outline(t.inputBg, t.cardStroke, 10, 1.2f, this));
        int fpad = Ui.dp(12);
        folderInput.setPadding(fpad, fpad, fpad, fpad);
        folderInput.setSingleLine(true);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, -2);
        flp.topMargin = Ui.dp(10);
        out.addView(folderInput, flp);

        LinearLayout fbRow = new LinearLayout(this);
        TextView save = Ui.button(this, "💾 Save Path", Ui.GREEN);
        fbRow.addView(save, w());
        TextView def = Ui.button(this, "Default", t.textSub);
        LinearLayout.LayoutParams dlp = w();
        dlp.leftMargin = Ui.dp(10);
        fbRow.addView(def, dlp);
        LinearLayout.LayoutParams fbrp = new LinearLayout.LayoutParams(-1, -2);
        fbrp.topMargin = Ui.dp(10);
        out.addView(fbRow, fbrp);

        save.setOnClickListener(v -> {
            String s = folderInput.getText().toString().trim();
            if (s.length() == 0) { Ui.toast(this, "Type a path or use Default"); return; }
            if (!s.startsWith("/") || s.equals("/") || s.equals("/storage")
                    || s.equals("/storage/emulated") || s.equals("/storage/emulated/0")
                    || s.replace("//", "/").matches("/storage/emulated/0/?")) {
                Ui.toast(this, "Pick a sub-folder, e.g. /storage/emulated/0/MyDex");
                return;
            }
            File f = new File(s);
            if (!f.exists() && !f.mkdirs()) { Ui.toast(this, "Cannot create folder — check storage permission"); return; }
            Prefs.folder(this, s);
            cur.setText("Current: " + Project.java2dexRoot(this).getAbsolutePath());
            Ui.toast(this, "Output folder updated ✔");
        });
        def.setOnClickListener(v -> {
            Prefs.folder(this, null);
            folderInput.setText("");
            cur.setText("Current: " + Project.java2dexRoot(this).getAbsolutePath());
            Ui.toast(this, "Using default Java2Dex folder");
        });

        TextView reset = Ui.button(this, "♻  Clean DEX Outputs", Ui.RED);
        LinearLayout.LayoutParams rsrp = new LinearLayout.LayoutParams(-1, -2);
        rsrp.topMargin = Ui.dp(10);
        out.addView(reset, rsrp);
        reset.setOnClickListener(v -> confirmReset());
        body.addView(out, margin());

        // ---- data ----
        LinearLayout dt = section("DATA");
        TextView cache = Ui.button(this, "🧹  Clear Build Cache", t.textSub);
        LinearLayout.LayoutParams cacheLp = new LinearLayout.LayoutParams(-1, -2);
        cacheLp.topMargin = Ui.dp(12);
        dt.addView(cache, cacheLp);
        cache.setOnClickListener(v -> clearBuildCache());
        TextView wipe = Ui.button(this, "🗑  Delete All Projects", Ui.RED);
        LinearLayout.LayoutParams wipeLp = new LinearLayout.LayoutParams(-1, -2);
        wipeLp.topMargin = Ui.dp(10);
        dt.addView(wipe, wipeLp);
        wipe.setOnClickListener(v -> confirmWipe());
        body.addView(dt, margin());

        // ---- about ----
        LinearLayout ab = section("ABOUT");
        TextView infoBtn = Ui.button(this, "ⓘ  App Info", Ui.GREEN);
        ab.addView(infoBtn, new LinearLayout.LayoutParams(-1, -2));
        infoBtn.setOnClickListener(v ->
                startActivity(new android.content.Intent(this, InfoActivity.class)));
        body.addView(ab, margin());
    }

    private LinearLayout.LayoutParams margin() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(14);
        return lp;
    }

    private void setFont(int s, TextView val) {
        if (s < 10) s = 10;
        if (s > 28) s = 28;
        Prefs.fontSize(this, s);
        val.setText(String.valueOf(s));
    }

    private void confirmReset() {
        Ui.dialog(this)
                .setTitle("Clean DEX outputs?")
                .setMessage("Deletes the classes*.dex and smali files Java2Dex created for your projects in:\n\n"
                        + Project.java2dexRootNoCreate(this).getAbsolutePath()
                        + "\n\nOther files in that folder are never touched. Your sources stay safe — "
                        + "just re-convert.")
                .setPositiveButton("Clean", (d, w) -> {
                    int n = Project.cleanOutputs(this);
                    Ui.toast(this, n > 0 ? "Outputs cleaned ✔" : "Nothing to clean");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static String androidName(int api) {
        switch (api) {
            case 21: return "  (5.0)";
            case 24: return "  (7.0)";
            case 26: return "  (8.0)";
            case 28: return "  (9)";
            case 30: return "  (11)";
            default: return "";
        }
    }

    private void clearBuildCache() {
        long freed = 0;
        for (Project p : Project.all(this)) {
            freed += size(p.classesDir(this)) + size(p.dexTmpDir(this));
            Project.deleteDir(p.classesDir(this));
            Project.deleteDir(p.dexTmpDir(this));
        }
        Ui.toast(this, "Cache cleared — " + Ui.size(freed) + " freed");
    }

    private static long size(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long n = 0;
        File[] fs = f.listFiles();
        if (fs != null) for (File x : fs) n += size(x);
        return n;
    }

    private void confirmWipe() {
        Ui.dialog(this)
                .setTitle("Delete all projects?")
                .setMessage("All project sources, logs and list data will be erased. Output DEX files stay in the Java2Dex folder.")
                .setPositiveButton("Delete All", (d, w) -> {
                    for (Project p : Project.all(this)) Project.delete(this, p.id);
                    Project.clearAll(this);
                    Ui.toast(this, "All projects deleted");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
