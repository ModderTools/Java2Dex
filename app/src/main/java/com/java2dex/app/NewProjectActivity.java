package com.java2dex.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class NewProjectActivity extends Activity {

    private static final int REQ_SRC = 41;
    private static final int REQ_LIBS = 42;

    private static final String SAMPLE_JAVA =
            "public class HelloMod {\n"
            + "    public static String TAG = \"Java2Dex\";\n\n"
            + "    public static String hello(String who) {\n"
            + "        return \"Hello \" + who + \" from \" + TAG + \"!\";\n"
            + "    }\n\n"
            + "    public static int add(int a, int b) {\n"
            + "        return a + b;\n"
            + "    }\n"
            + "}\n";

    private EditText nameInput;
    private TextView srcSub, libSub, statusText;
    private ScrollView formScroll;
    private LinearLayout progressBox, srcCard;
    private FrameLayout resultBox;
    private Project currentProject;
    private final List<Uri> sources = new ArrayList<>();
    private final List<Uri> libs = new ArrayList<>();
    private boolean useSample = false;
    private String lastError = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Ui.GREEN_DEEP);
        buildUi();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.GREEN_BG);
        setContentView(root);

        FrameLayout stage = new FrameLayout(this);
        root.addView(stage, new FrameLayout.LayoutParams(-1, -1));

        // ================= FORM =================
        formScroll = new ScrollView(this);
        formScroll.setFillViewport(true);
        stage.addView(formScroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        formScroll.addView(col, new ScrollView.LayoutParams(-1, -2));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackground(Ui.gradient(Ui.GREEN_DEEP, Ui.GREEN, 0, this));
        header.setPadding(Ui.dp(this, 18), Ui.dp(this, 26), Ui.dp(this, 18), Ui.dp(this, 22));

        TextView back = Ui.text(this, "←  Back", 15, Color.WHITE, true);
        back.setBackground(Ui.ripple(this, Ui.fill(0x33FFFFFF, 12, this)));
        back.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(-2, -2));

        TextView h1 = Ui.text(this, "New Project", 22, Color.WHITE, true);
        LinearLayout.LayoutParams h1p = new LinearLayout.LayoutParams(-2, -2);
        h1p.topMargin = Ui.dp(this, 14);
        header.addView(h1, h1p);
        header.addView(Ui.text(this, "Compile Java sources into a classes.dex", 12, 0xB3FFFFFF, false));
        col.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 28));
        col.addView(body, new LinearLayout.LayoutParams(-1, -2));

        body.addView(Ui.text(this, "PROJECT NAME", 11, Ui.TEXT_SUB, true));
        nameInput = Ui.input(this, "e.g. MyModPatch");
        LinearLayout.LayoutParams nip = new LinearLayout.LayoutParams(-1, -2);
        nip.topMargin = Ui.dp(this, 7);
        body.addView(nameInput, nip);

        // source picker card
        srcCard = new LinearLayout(this);
        srcCard.setOrientation(LinearLayout.VERTICAL);
        srcCard.setBackground(Ui.outline(Color.WHITE, Ui.STROKE, 14, 1.2f, this));
        int pd = Ui.dp(this, 14);
        srcCard.setPadding(pd, pd, pd, pd);
        srcCard.addView(Ui.text(this, "📄  Java sources", 14.5f, Ui.TEXT, true));
        srcSub = Ui.text(this, "Tap to choose .java files or a .zip archive", 11.5f, Ui.TEXT_SUB, false);
        LinearLayout.LayoutParams ssp = new LinearLayout.LayoutParams(-2, -2);
        ssp.topMargin = Ui.dp(this, 3);
        srcCard.addView(srcSub, ssp);
        srcCard.setOnClickListener(v -> pick(REQ_SRC));
        LinearLayout.LayoutParams scp = new LinearLayout.LayoutParams(-1, -2);
        scp.topMargin = Ui.dp(this, 16);
        body.addView(srcCard, scp);

        // libs picker card
        LinearLayout libCard = new LinearLayout(this);
        libCard.setOrientation(LinearLayout.VERTICAL);
        libCard.setBackground(Ui.outline(Color.WHITE, Ui.STROKE, 14, 1.2f, this));
        libCard.setPadding(pd, pd, pd, pd);
        libCard.addView(Ui.text(this, "📚  Library jars (optional)", 14.5f, Ui.TEXT, true));
        libSub = Ui.text(this, "Tap to add .jar / .zip files to the classpath", 11.5f, Ui.TEXT_SUB, false);
        LinearLayout.LayoutParams lsp = new LinearLayout.LayoutParams(-2, -2);
        lsp.topMargin = Ui.dp(this, 3);
        libCard.addView(libSub, lsp);
        libCard.setOnClickListener(v -> pick(REQ_LIBS));
        LinearLayout.LayoutParams lcp = new LinearLayout.LayoutParams(-1, -2);
        lcp.topMargin = Ui.dp(this, 10);
        body.addView(libCard, lcp);

        TextView sample = Ui.text(this, "✨  Or load a sample project to try", 13, Ui.GREEN_DARK, true);
        sample.setPadding(0, Ui.dp(this, 14), 0, 0);
        sample.setOnClickListener(v -> loadSample());
        body.addView(sample, new LinearLayout.LayoutParams(-2, -2));

        TextView convertBtn = Ui.button(this, "⚡  CONVERT TO DEX", Ui.GREEN);
        convertBtn.setOnClickListener(v -> startConvert());
        LinearLayout.LayoutParams cbp = new LinearLayout.LayoutParams(-1, -2);
        cbp.topMargin = Ui.dp(this, 22);
        body.addView(convertBtn, cbp);

        // ================= PROGRESS =================
        progressBox = new LinearLayout(this);
        progressBox.setOrientation(LinearLayout.VERTICAL);
        progressBox.setGravity(Gravity.CENTER);
        progressBox.setBackgroundColor(Color.WHITE);
        progressBox.setVisibility(View.GONE);
        progressBox.setClickable(true);

        ProgressBar pb = new ProgressBar(this);
        pb.setIndeterminateTintList(ColorStateList.valueOf(Ui.GREEN));
        progressBox.addView(pb, new LinearLayout.LayoutParams(-2, -2));

        statusText = Ui.text(this, "Preparing…", 14.5f, Ui.TEXT, true);
        statusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(-2, -2);
        stp.topMargin = Ui.dp(this, 16);
        progressBox.addView(statusText, stp);

        TextView hint = Ui.text(this, "Large projects may take a while", 12, Ui.TEXT_SUB, false);
        hint.setGravity(Gravity.CENTER);
        progressBox.addView(hint);

        stage.addView(progressBox, new FrameLayout.LayoutParams(-1, -1));

        // ================= RESULT =================
        resultBox = new FrameLayout(this);
        resultBox.setVisibility(View.GONE);
        stage.addView(resultBox, new FrameLayout.LayoutParams(-1, -1));
    }

    // ---------------- pickers ----------------

    private void pick(int req) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(i,
                req == REQ_SRC ? "Select .java / .zip" : "Select library jars"), req);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                uris.add(data.getClipData().getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (uris.isEmpty()) return;
        if (requestCode == REQ_SRC) {
            sources.addAll(uris);
            useSample = false;
            srcSub.setText(describe(sources) + " — tap to change");
        } else {
            libs.addAll(uris);
            libSub.setText(describe(libs) + " — tap to change");
        }
    }

    private void loadSample() {
        useSample = true;
        srcSub.setText("Sample project loaded (HelloMod.java)");
        Ui.toast(this, "Sample loaded — enter a name and convert!");
    }

    // ---------------- convert flow ----------------

    private void startConvert() {
        String name = nameInput.getText().toString().trim();
        if (name.length() == 0) {
            Ui.shake(nameInput);
            Ui.toast(this, "Enter a project name");
            return;
        }
        if (sources.isEmpty() && !useSample) {
            Ui.shake(srcCard);
            Ui.toast(this, "Select .java files or a .zip");
            return;
        }
        Project p = new Project();
        p.id = String.valueOf(System.currentTimeMillis());
        p.name = name;
        p.createdAt = System.currentTimeMillis();
        Project.upsert(this, p);
        currentProject = p;

        showProgress();
        new Thread(() -> {
            final boolean staged = stageFiles(p);
            runOnUiThread(() -> {
                if (!staged) {
                    showError(lastError);
                    return;
                }
                runConvert(p);
            });
        }).start();
    }

    private void runConvert(final Project p) {
        showProgress();
        Converter.convert(this, p, new Converter.Callback() {
            @Override public void onStep(String s) {
                statusText.setText(s);
            }
            @Override public void onDone(boolean ok, String log) {
                saveLog(p, log);
                if (ok) showSuccess(p);
                else showError(log);
            }
        });
    }

    private boolean stageFiles(Project p) {
        try {
            File src = p.srcDir(this);
            src.mkdirs();
            if (useSample) {
                FileWriter w = new FileWriter(new File(src, "HelloMod.java"));
                w.write(SAMPLE_JAVA);
                w.close();
            }
            int i = 0;
            for (Uri u : sources) {
                String name = displayName(u);
                if (name == null || name.length() == 0) name = "file_" + (i++);
                name = sanitize(name);
                File dst = new File(src, name);
                copyStream(getContentResolver().openInputStream(u), new FileOutputStream(dst));
                if (name.toLowerCase().endsWith(".zip")) {
                    unzip(dst, src);
                    dst.delete();
                }
            }
            if (!libs.isEmpty()) {
                File libDir = p.libsDir(this);
                libDir.mkdirs();
                int j = 0;
                for (Uri u : libs) {
                    String name = displayName(u);
                    if (name == null || name.length() == 0) name = "lib_" + (j++) + ".jar";
                    copyStream(getContentResolver().openInputStream(u),
                            new FileOutputStream(new File(libDir, sanitize(name))));
                }
            }
            return true;
        } catch (Exception e) {
            lastError = "[Java2Dex] Could not import files: " + e.getMessage();
            return false;
        }
    }

    // ---------------- helpers ----------------

    private static String describe(List<Uri> list) {
        if (list.isEmpty()) return "Nothing";
        return list.size() + (list.size() == 1 ? " file selected" : " files selected");
    }

    private static String sanitize(String n) {
        String s = n == null ? "" : n.replace("..", "_").replace("/", "_").replace("\\", "_");
        return s.length() == 0 ? "file" : s;
    }

    private String displayName(Uri u) {
        try {
            Cursor c = getContentResolver().query(u, null, null, null, null);
            if (c != null) {
                try {
                    int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0 && c.moveToFirst()) {
                        String s = c.getString(idx);
                        if (s != null && s.length() > 0) return s;
                    }
                } finally {
                    c.close();
                }
            }
        } catch (Exception ignored) {}
        String seg = u.getLastPathSegment();
        if (seg == null) return null;
        int i = seg.lastIndexOf('/');
        return i >= 0 ? seg.substring(i + 1) : seg;
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.flush();
        out.close();
        in.close();
    }

    private static void unzip(File zip, File destDir) throws IOException {
        java.util.zip.ZipInputStream zis =
                new java.util.zip.ZipInputStream(new FileInputStream(zip));
        try {
            java.util.zip.ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String name = e.getName().replace("\\", "/");
                if (name.contains("..")) continue;
                while (name.startsWith("/")) name = name.substring(1);
                if (!name.toLowerCase().endsWith(".java")) continue;
                File out = new File(destDir, name);
                File parent = out.getParentFile();
                if (parent != null) parent.mkdirs();
                FileOutputStream fo = new FileOutputStream(out);
                byte[] buf = new byte[8192];
                int n;
                while ((n = zis.read(buf)) > 0) fo.write(buf, 0, n);
                fo.close();
            }
        } finally {
            zis.close();
        }
    }

    private void saveLog(final Project p, final String log) {
        new Thread(() -> {
            try {
                File f = p.logFile(this);
                f.getParentFile().mkdirs();
                FileWriter w = new FileWriter(f);
                w.write(log);
                w.close();
            } catch (Exception ignored) {}
        }).start();
    }

    // ---------------- states ----------------

    private void showProgress() {
        formScroll.setVisibility(View.GONE);
        resultBox.setVisibility(View.GONE);
        resultBox.removeAllViews();
        progressBox.setVisibility(View.VISIBLE);
    }

    private void resetForm() {
        resultBox.setVisibility(View.GONE);
        resultBox.removeAllViews();
        formScroll.setVisibility(View.VISIBLE);
    }

    private LinearLayout.LayoutParams rowWeight() {
        return new LinearLayout.LayoutParams(0, -2, 1f);
    }

    private void showSuccess(final Project p) {
        progressBox.setVisibility(View.GONE);
        resultBox.removeAllViews();
        resultBox.setVisibility(View.VISIBLE);

        ScrollView sc = new ScrollView(this);
        resultBox.addView(sc, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(Ui.dp(this, 22), Ui.dp(this, 40), Ui.dp(this, 22), Ui.dp(this, 28));
        sc.addView(col, new ScrollView.LayoutParams(-1, -2));

        Ui.SuccessView sv = new Ui.SuccessView(this);
        col.addView(sv, new LinearLayout.LayoutParams(Ui.dp(this, 110), Ui.dp(this, 110)));
        sv.start();

        TextView t1 = Ui.text(this, "Conversion Successful!", 19, Ui.TEXT, true);
        t1.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams t1p = new LinearLayout.LayoutParams(-2, -2);
        t1p.topMargin = Ui.dp(this, 18);
        col.addView(t1, t1p);

        TextView t2 = Ui.text(this, "classes.dex saved to /storage/emulated/0/Java2Dex/", 13, Ui.TEXT_SUB, false);
        t2.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams t2p = new LinearLayout.LayoutParams(-2, -2);
        t2p.topMargin = Ui.dp(this, 4);
        col.addView(t2, t2p);

        LinearLayout pathCard = new LinearLayout(this);
        pathCard.setOrientation(LinearLayout.VERTICAL);
        pathCard.setBackground(Ui.outline(Ui.GREEN_LIGHT, Ui.GREEN, 12, 1, this));
        int pd = Ui.dp(this, 12);
        pathCard.setPadding(pd, pd, pd, pd);
        TextView pt = Ui.text(this, p.publicDexFile(this).getAbsolutePath(), 12, Ui.GREEN_DEEP, false);
        pt.setTypeface(Typeface.MONOSPACE);
        pt.setTextIsSelectable(true);
        pathCard.addView(pt);
        LinearLayout.LayoutParams pcp = new LinearLayout.LayoutParams(-1, -2);
        pcp.topMargin = Ui.dp(this, 18);
        col.addView(pathCard, pcp);

        LinearLayout row1 = new LinearLayout(this);
        TextView copyBtn = Ui.button(this, "📋  Copy Path", Ui.GREEN_DARK);
        row1.addView(copyBtn, rowWeight());
        if (Build.VERSION.SDK_INT >= 29) {
            TextView ex = Ui.button(this, "⬇  Export", Ui.GREEN);
            LinearLayout.LayoutParams elp = rowWeight();
            elp.leftMargin = Ui.dp(this, 10);
            row1.addView(ex, elp);
            ex.setOnClickListener(v -> exportToDownloads(p));
        }
        LinearLayout.LayoutParams r1p = new LinearLayout.LayoutParams(-1, -2);
        r1p.topMargin = Ui.dp(this, 20);
        col.addView(row1, r1p);

        LinearLayout row2 = new LinearLayout(this);
        TextView shareBtn = Ui.button(this, "↗  Share", Ui.TEXT_SUB);
        row2.addView(shareBtn, rowWeight());
        TextView doneBtn = Ui.button(this, "Done", Ui.GREEN);
        LinearLayout.LayoutParams dlp = rowWeight();
        dlp.leftMargin = Ui.dp(this, 10);
        row2.addView(doneBtn, dlp);
        LinearLayout.LayoutParams r2p = new LinearLayout.LayoutParams(-1, -2);
        r2p.topMargin = Ui.dp(this, 10);
        col.addView(row2, r2p);

        copyBtn.setOnClickListener(v ->
                Ui.copy(this, "dex-path", p.publicDexFile(this).getAbsolutePath()));
        shareBtn.setOnClickListener(v -> {
    File f = p.publicDexFile(this);
    if (f.exists()) Ui.shareFile(this, f, p.safeName() + "_classes.dex");
    else Ui.toast(this, "File missing — try again");
});
        doneBtn.setOnClickListener(v -> finish());
    }

    private void showError(String log) {
        progressBox.setVisibility(View.GONE);
        resultBox.removeAllViews();
        resultBox.setVisibility(View.VISIBLE);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        resultBox.addView(col, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackground(Ui.gradient(0xFFB91C1C, Ui.RED, 0, this));
        header.setPadding(Ui.dp(this, 18), Ui.dp(this, 26), Ui.dp(this, 18), Ui.dp(this, 20));
        TextView back = Ui.text(this, "←  Back", 15, Color.WHITE, true);
        back.setBackground(Ui.ripple(this, Ui.fill(0x33FFFFFF, 12, this)));
        back.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
        back.setOnClickListener(v -> resetForm());
        header.addView(back, new LinearLayout.LayoutParams(-2, -2));
        TextView h1 = Ui.text(this, "✖  Build Failed", 20, Color.WHITE, true);
        LinearLayout.LayoutParams h1p = new LinearLayout.LayoutParams(-2, -2);
        h1p.topMargin = Ui.dp(this, 12);
        header.addView(h1, h1p);
        header.addView(Ui.text(this,
                "See the error log below — tap Copy Error to grab it", 12, 0xCCFFFFFF, false));
        col.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 16));
        col.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        ScrollView logScroll = new ScrollView(this);
        logScroll.setBackground(Ui.fill(0xFFF8FAFC, 12, this));
        TextView logTv = Ui.text(this, log, 11.5f, 0xFF7F1D1D, false);
        logTv.setTypeface(Typeface.MONOSPACE);
        logTv.setTextIsSelectable(true);
        int pd = Ui.dp(this, 12);
        logTv.setPadding(pd, pd, pd, pd);
        logScroll.addView(logTv, new ScrollView.LayoutParams(-1, -2));
        body.addView(logScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout row = new LinearLayout(this);
        TextView copyBtn = Ui.button(this, "📋  Copy Error", Ui.RED);
        row.addView(copyBtn, rowWeight());
        TextView retryBtn = Ui.button(this, "↺  Retry", Ui.GREEN_DARK);
        LinearLayout.LayoutParams rlp = rowWeight();
        rlp.leftMargin = Ui.dp(this, 10);
        row.addView(retryBtn, rlp);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = Ui.dp(this, 12);
        body.addView(row, rp);

        copyBtn.setOnClickListener(v -> Ui.copy(this, "build-error", log));
        retryBtn.setOnClickListener(v -> {
            if (currentProject != null) runConvert(currentProject);
        });
        Ui.shake(body);
    }

    private void exportToDownloads(final Project p) {
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
            Uri uri = getContentResolver()
                    .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) throw new IOException("insert failed");
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
