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
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class DetailActivity extends Activity {

    private Project p;
    private LinearLayout infoCol;
    private LinearLayout progressOverlay;
    private TextView statusText2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Ui.GREEN_DEEP);

        String id = getIntent() != null ? getIntent().getStringExtra("id") : null;
        p = id != null ? Project.byId(this, id) : null;
        if (p == null) { finish(); return; }

        buildUi();
        refreshInfo();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.GREEN_BG);
        setContentView(root);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        // header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackground(Ui.gradient(Ui.GREEN_DEEP, Ui.GREEN, 0, this));
        header.setPadding(Ui.dp(this, 18), Ui.dp(this, 26), Ui.dp(this, 18), Ui.dp(this, 22));

        TextView back = Ui.text(this, "←  Back", 15, Color.WHITE, true);
        back.setBackground(Ui.ripple(this, Ui.fill(0x33FFFFFF, 12, this)));
        back.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(-2, -2));

        TextView h1 = Ui.text(this, p.name, 22, Color.WHITE, true);
        LinearLayout.LayoutParams h1p = new LinearLayout.LayoutParams(-2, -2);
        h1p.topMargin = Ui.dp(this, 14);
        h1.setSingleLine(true);
        header.addView(h1, h1p);
        header.addView(Ui.text(this, "Project details", 12, 0xB3FFFFFF, false));
        page.addView(header, new LinearLayout.LayoutParams(-1, -2));

        // body
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 28));
        page.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout infoCard = new LinearLayout(this);
        infoCard.setOrientation(LinearLayout.VERTICAL);
        infoCard.setBackground(Ui.outline(Color.WHITE, Ui.STROKE, 16, 1, this));
        infoCard.setElevation(Ui.dp(this, 2));
        int pd = Ui.dp(this, 16);
        infoCard.setPadding(pd, pd, pd, pd);
        body.addView(infoCard, new LinearLayout.LayoutParams(-1, -2));

        infoCol = new LinearLayout(this);
        infoCol.setOrientation(LinearLayout.VERTICAL);
        infoCard.addView(infoCol, new LinearLayout.LayoutParams(-1, -2));

        // actions
        LinearLayout g1 = new LinearLayout(this);
        TextView recon = Ui.button(this, "🔄  Re-convert", Ui.GREEN);
        g1.addView(recon, w());
        TextView ex = Ui.button(this, "⬇  Export", Ui.GREEN_DARK);
        LinearLayout.LayoutParams e2 = w();
        e2.leftMargin = Ui.dp(this, 10);
        g1.addView(ex, e2);
        LinearLayout.LayoutParams g1p = new LinearLayout.LayoutParams(-1, -2);
        g1p.topMargin = Ui.dp(this, 16);
        body.addView(g1, g1p);

        LinearLayout g2 = new LinearLayout(this);
        TextView cp = Ui.button(this, "📋  Copy Path", Ui.TEXT_SUB);
        g2.addView(cp, w());
        TextView sh = Ui.button(this, "↗  Share", Ui.TEXT_SUB);
        LinearLayout.LayoutParams s2 = w();
        s2.leftMargin = Ui.dp(this, 10);
        g2.addView(sh, s2);
        LinearLayout.LayoutParams g2p = new LinearLayout.LayoutParams(-1, -2);
        g2p.topMargin = Ui.dp(this, 10);
        body.addView(g2, g2p);

        LinearLayout g3 = new LinearLayout(this);
        TextView lg = Ui.button(this, "📄  View Log", Ui.TEXT_SUB);
        g3.addView(lg, w());
        TextView del = Ui.button(this, "🗑  Delete", Ui.RED);
        LinearLayout.LayoutParams d2 = w();
        d2.leftMargin = Ui.dp(this, 10);
        g3.addView(del, d2);
        LinearLayout.LayoutParams g3p = new LinearLayout.LayoutParams(-1, -2);
        g3p.topMargin = Ui.dp(this, 10);
        body.addView(g3, g3p);

        recon.setOnClickListener(v -> reconvert());
        ex.setOnClickListener(v -> exportToDownloads());
        cp.setOnClickListener(v ->
                Ui.copy(this, "dex-path", p.publicDexFile(this).getAbsolutePath()));
        sh.setOnClickListener(v -> {
    File f = p.publicDexFile(this);
    if (f.exists()) Ui.shareFile(this, f, p.safeName() + "_classes.dex");
    else Ui.toast(this, "No DEX yet — re-convert first");
});
        lg.setOnClickListener(v -> showLog(null));
        del.setOnClickListener(v -> confirmDelete());

        // progress overlay
        progressOverlay = new LinearLayout(this);
        progressOverlay.setOrientation(LinearLayout.VERTICAL);
        progressOverlay.setGravity(Gravity.CENTER);
        progressOverlay.setBackgroundColor(0xF0FFFFFF);
        progressOverlay.setClickable(true);
        ProgressBar pb = new ProgressBar(this);
        pb.setIndeterminateTintList(ColorStateList.valueOf(Ui.GREEN));
        progressOverlay.addView(pb, new LinearLayout.LayoutParams(-2, -2));
        statusText2 = Ui.text(this, "Working…", 14, Ui.TEXT, true);
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(-2, -2);
        stp.topMargin = Ui.dp(this, 14);
        progressOverlay.addView(statusText2, stp);
        progressOverlay.setVisibility(View.GONE);
        root.addView(progressOverlay, new FrameLayout.LayoutParams(-1, -1));
    }

    private LinearLayout.LayoutParams w() {
        return new LinearLayout.LayoutParams(0, -2, 1f);
    }

    private void refreshInfo() {
        infoCol.removeAllViews();

        LinearLayout stat = new LinearLayout(this);
        stat.setGravity(Gravity.CENTER_VERTICAL);
        stat.addView(Ui.text(this, "STATUS", 10.5f, Ui.TEXT_SUB, true));
        stat.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        int st = p.status;
        TextView chip = Ui.text(this,
                st == Project.ST_OK ? "✔ SUCCESS" : st == Project.ST_ERROR ? "✖ FAILED" : "PENDING",
                11,
                st == Project.ST_OK ? Ui.GREEN_DARK : st == Project.ST_ERROR ? Ui.RED : Ui.TEXT_SUB,
                true);
        chip.setBackground(Ui.fill(
                st == Project.ST_OK ? Ui.GREEN_LIGHT : st == Project.ST_ERROR ? Ui.RED_LIGHT : 0xFFF1F5F9,
                20, this));
        chip.setPadding(Ui.dp(this, 10), Ui.dp(this, 3), Ui.dp(this, 10), Ui.dp(this, 3));
        stat.addView(chip);
        infoCol.addView(stat, new LinearLayout.LayoutParams(-1, -2));

        addRow("Created", p.createdText());
        addRow("Last build", p.dateText());
        addRow("Source files", countJava(p.srcDir(this)) + " .java");
        addRow("DEX size", st == Project.ST_OK ? Ui.size(p.dexSize) : "—");
        addRow("DEX file", p.publicDexFile(this).getAbsolutePath());
        addRow("Log", p.logFile(this).exists()
                ? p.logFile(this).getAbsolutePath() : "No log yet");
    }

    private void addRow(String k, String v) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.addView(Ui.text(this, k, 10.5f, Ui.TEXT_SUB, true));
        TextView val = Ui.text(this, v, 13, Ui.TEXT, false);
        val.setTextIsSelectable(true);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-2, -2);
        vp.topMargin = Ui.dp(this, 2);
        r.addView(val, vp);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = Ui.dp(this, 12);
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
            @Override public void onStep(String s) {
                statusText2.setText(s);
            }
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
                .setMessage("This removes sources, logs and the JAVA2DEX output for \""
                        + p.name + "\".")
                .setPositiveButton("Delete", (d, w) -> {
                    Project.delete(this, p.id);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showLog(String text) {
        final String content = text != null && text.length() > 0 ? text : readLog();
        ScrollView sc = new ScrollView(this);
        TextView tv = Ui.text(this, content, 11.5f, 0xFF334155, false);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextIsSelectable(true);
        int pd = Ui.dp(this, 14);
        tv.setPadding(pd, pd, pd, pd);
        sc.addView(tv, new FrameLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this)
                .setTitle("Build log")
                .setView(sc)
                .setPositiveButton("Copy", (d, w) -> Ui.copy(this, "log", content))
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
            w.write(log);
            w.close();
        } catch (Exception ignored) {}
    }

    private void exportToDownloads() {
        if (Build.VERSION.SDK_INT < 29) {
            Ui.toast(this, "Requires Android 10+");
            return;
        }
        try {
            File dex = p.publicDexFile(this);
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
            out.flush();
            out.close();
            in.close();
            Ui.toast(this, "Exported to Downloads/Java2Dex ✔");
        } catch (Exception e) {
            Ui.toast(this, "Export failed: " + e.getMessage());
        }
    }

}
