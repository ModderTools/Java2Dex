package com.java2dex.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;

public class DetailActivity extends Activity {

    private Theme t;
    private Project p;
    private LinearLayout infoCol;
    private LinearLayout progressOverlay;
    private TextView statusText2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        t = Theme.get(this);
        getWindow().setStatusBarColor(Ui.GREEN_DEEP);

        String id = getIntent() != null ? getIntent().getStringExtra("id") : null;
        p = id != null ? Project.byId(this, id) : null;
        if (p == null) { finish(); return; }

        buildUi();
        refreshInfo();
    }

    private LinearLayout.LayoutParams w() {
        return new LinearLayout.LayoutParams(0, -2, 1f);
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(t.bg);
        setContentView(root);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackground(Ui.gradient(Ui.GREEN_DEEP, Ui.GREEN, 0, this));
        header.setPadding(Ui.dp(18), Ui.dp(26), Ui.dp(18), Ui.dp(22));

        TextView back = Ui.text(this, "←  Back", 15, Color.WHITE, true);
        back.setBackground(Ui.ripple(this, Ui.fill(0x33FFFFFF, 12, this)));
        back.setPadding(Ui.dp(14), Ui.dp(8), Ui.dp(14), Ui.dp(8));
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(-2, -2));

        TextView h1 = Ui.text(this, p.name, 22, Color.WHITE, true);
        h1.setSingleLine(true);
        LinearLayout.LayoutParams h1p = new LinearLayout.LayoutParams(-2, -2);
        h1p.topMargin = Ui.dp(14);
        header.addView(h1, h1p);
        header.addView(Ui.text(this, "Project details", 12, 0xB3FFFFFF, false));
        page.addView(header, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(16), Ui.dp(16), Ui.dp(16), Ui.dp(28));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));

        LinearLayout infoCard = new LinearLayout(this);
        infoCard.setOrientation(LinearLayout.VERTICAL);
        infoCard.setBackground(Ui.outline(t.card, t.cardStroke, 16, 1, this));
        infoCard.setElevation(Ui.dp(2));
        int pd = Ui.dp(16);
        infoCard.setPadding(pd, pd, pd, pd);
        body.addView(infoCard, new LinearLayout.LayoutParams(-1, -2));

        infoCol = new LinearLayout(this);
        infoCol.setOrientation(LinearLayout.VERTICAL);
        infoCard.addView(infoCol, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout g1 = new LinearLayout(this);
        TextView recon = Ui.button(this, "🔄  Re-convert", Ui.GREEN);
        g1.addView(recon, w());
        TextView smali = Ui.button(this, "🧬  Smali", Ui.GREEN_DARK);
        LinearLayout.LayoutParams s2 = w();
        s2.leftMargin = Ui.dp(10);
        g1.addView(smali, s2);
        LinearLayout.LayoutParams g1p = new LinearLayout.LayoutParams(-1, -2);
        g1p.topMargin = Ui.dp(16);
        body.addView(g1, g1p);

        LinearLayout g2 = new LinearLayout(this);
        TextView ex = Ui.button(this, "⬇  Export", t.textSub);
        g2.addView(ex, w());
        TextView sh = Ui.button(this, "↗  Share", t.textSub);
        LinearLayout.LayoutParams sh2 = w();
        sh2.leftMargin = Ui.dp(10);
        g2.addView(sh, sh2);
        LinearLayout.LayoutParams g2p = new LinearLayout.LayoutParams(-1, -2);
        g2p.topMargin = Ui.dp(10);
        body.addView(g2, g2p);

        LinearLayout g3 = new LinearLayout(this);
        TextView cp = Ui.button(this, "📋  Path", t.textSub);
        g3.addView(cp, w());
        TextView lg = Ui.button(this, "📄  Log", t.textSub);
        LinearLayout.LayoutParams lg2 = w();
        lg2.leftMargin = Ui.dp(10);
        g3.addView(lg, lg2);
        LinearLayout.LayoutParams g3p = new LinearLayout.LayoutParams(-1, -2);
        g3p.topMargin = Ui.dp(10);
        body.addView(g3, g3p);

        TextView del = Ui.button(this, "🗑  Delete Project", Ui.RED);
        LinearLayout.LayoutParams delp = new LinearLayout.LayoutParams(-1, -2);
        delp.topMargin = Ui.dp(10);
        body.addView(del, delp);

        recon.setOnClickListener(v -> reconvert());
        smali.setOnClickListener(v -> {
            Intent i = new Intent(this, DexViewerActivity.class);
            i.putExtra("id", p.id);
            startActivity(i);
        });
        ex.setOnClickListener(v -> exportToDownloads());
        sh.setOnClickListener(v -> {
            File f = p.publicDexFile(this);
            if (f.exists()) Ui.shareFile(this, f, p.safeName() + "_classes.dex");
            else Ui.toast(this, "No DEX yet — re-convert first");
        });
        cp.setOnClickListener(v ->
                Ui.copy(this, "dex-path", p.publicDexFile(this).getAbsolutePath()));
        lg.setOnClickListener(v -> showLog(null));
        del.setOnClickListener(v -> confirmDelete());

        progressOverlay = new LinearLayout(this);
        progressOverlay.setOrientation(LinearLayout.VERTICAL);
        progressOverlay.setGravity(Gravity.CENTER);
        progressOverlay.setBackgroundColor(0xF0FFFFFF);
        progressOverlay.setClickable(true);
        ProgressBar pb = new ProgressBar(this);
        pb.setIndeterminateTintList(ColorStateList.valueOf(Ui.GREEN));
        progressOverlay.addView(pb, new LinearLayout.LayoutParams(-2, -2));
        statusText2 = Ui.text(this, "Working…", 14, t.text, true);
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(-2, -2);
        stp.topMargin = Ui.dp(14);
        progressOverlay.addView(statusText2, stp);
        progressOverlay.setVisibility(View.GONE);
        root.addView(progressOverlay, new FrameLayout.LayoutParams(-1, -1));
    }

    private void refreshInfo() {
        infoCol.removeAllViews();

        LinearLayout stat = new LinearLayout(this);
        stat.setGravity(Gravity.CENTER_VERTICAL);
        stat.addView(Ui.text(this, "STATUS", 10.5f, t.textSub, true));
        stat.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        int st = p.status;
        TextView chip = Ui.text(this,
                st == Project.ST_OK ? "✔ SUCCESS" : st == Project.ST_ERROR ? "✖ FAILED" : "PENDING",
                11,
                st == Project.ST_OK ? t.accentDark : st == Project.ST_ERROR ? t.danger : t.textSub,
                true);
        chip.setBackground(Ui.fill(
                st == Project.ST_OK ? t.accentSoft : st == Project.ST_ERROR ? t.dangerSoft : t.chipBg,
                20, this));
        chip.setPadding(Ui.dp(10), Ui.dp(3), Ui.dp(10), Ui.dp(3));
        stat.addView(chip);
        infoCol.addView(stat, new LinearLayout.LayoutParams(-1, -2));

        addRow("Created", p.createdText());
        addRow("Last build", p.dateText());
        addRow("Source files", countJava(p.srcDir(this)) + " .java");
        addRow("DEX size", st == Project.ST_OK ? Ui.size(p.dexSize) : "—");
        addRow("DEX file", p.publicDexFile(this).getAbsolutePath());
    }

    private void addRow(String k, String v) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.addView(Ui.text(this, k, 10.5f, t.textSub, true));
        TextView val = Ui.text(this, v, 13, t.text, false);
        val.setTextIsSelectable(true);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-2, -2);
        vp.topMargin = Ui.dp(2);
        r.addView(val, vp);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = Ui.dp(12);
        infoCol.addView(r, rp);
    }

    private int countJava(File dir) {
        int n = 0;
        File[] fs = dir.listFiles();
        if (fs != null) {
            for (File f : fs) {
                if (f.isDirectory()) n += countJava(f);
                else if (f.getName().toLowerCase().endsWith(".java")) n++;
            }
        }
        return n;
    }

    private void reconvert() {
        progressOverlay.setVisibility(View.VISIBLE);
        Converter.convert(this, p, new Converter.Callback() {
            @Override public void onStep(String s) { statusText2.setText(s); }
            @Override public void onDone(boolean ok, String log) {
                saveLog(log);
                progressOverlay.setVisibility(View.GONE);
                if (ok) Ui.toast(DetailActivity.this, "Rebuilt successfully ✔");
                else { Ui.toast(DetailActivity.this, "Build failed — see log"); showLog(log); }
                refreshInfo();
            }
        });
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Delete project?")
                .setMessage("Removes sources, logs and DEX output for \"" + p.name + "\".")
                .setPositiveButton("Delete", (d, w2) -> {
                    Project.delete(this, p.id);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showLog(String text) {
        final String content = text != null && text.length() > 0 ? text : readLog();
        ScrollView sc = new ScrollView(this);
        TextView tv = Ui.text(this, content, 11.5f, t.text, false);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextIsSelectable(true);
        int pd = Ui.dp(14);
        tv.setPadding(pd, pd, pd, pd);
        sc.addView(tv, new FrameLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this)
                .setTitle("Build log")
                .setView(sc)
                .setPositiveButton("Copy", (d, w2) -> Ui.copy(this, "log", content))
                .setNegativeButton("Close", null)
                .show();
    }

    private String readLog() {
        try {
            FileInputStream in = new FileInputStream(p.logFile(this));
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return bo.toString();
        } catch (Exception e) {
            return "No log available.";
        }
    }

    private void saveLog(String log) {
        try {
            File f = p.logFile(this);
            f.getParentFile().mkdirs();
            FileWriter w = new FileWriter(f);
            w.write(log); w.close();
        } catch (Exception ignored) { }
    }

    private void exportToDownloads() {
        if (Build.VERSION.SDK_INT < 29) {
            Ui.toast(this, "Requires Android 10+");
            return;
        }
        try {
            File dex = p.publicDexFile(this);
            if (!dex.exists()) { Ui.toast(this, "No DEX — convert first"); return; }
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, p.safeName() + "_classes.dex");
            cv.put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream");
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/Java2Dex");
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) throw new java.io.IOException("insert failed");
            InputStream in = new FileInputStream(dex);
            OutputStream out = getContentResolver().openOutputStream(uri);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush(); out.close(); in.close();
            Ui.toast(this, "Exported to Downloads/Java2Dex ✔");
        } catch (Exception e) {
            Ui.toast(this, "Export failed: " + e.getMessage());
        }
    }
}
