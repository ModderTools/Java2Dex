package com.java2dex.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** On-device code IDE: files, folders, editor with highlighting, find, snippets, convert */
public class IdeActivity extends Activity {

    private static final int REQ_IMPORT = 51;

    private static final String[] KEYWORDS = {
            "abstract","assert","boolean","break","byte","case","catch","char","class","const",
            "continue","default","do","double","else","enum","extends","final","finally","float",
            "for","goto","if","implements","import","instanceof","int","interface","long","native",
            "new","package","private","protected","public","return","short","static","strictfp",
            "super","switch","synchronized","this","throw","throws","transient","try","void",
            "volatile","while","true","false","null","var","record","yield"
    };

    private Theme t;
    private Project p;
    private ListView filesList;
    private EditText editor;
    private TextView filePathLabel, metaLabel;
    private FrameLayout overlay;
    private TextView overlayStatus;
    private LinearLayout filesPanel;

    private final List<Object[]> entries = new ArrayList<>();
    private final Map<String, String> undoMap = new HashMap<>();
    private String currentRel = null;
    private final Handler hl = new Handler();
    private Runnable hlRun;
    private boolean wrapOn = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        t = Theme.get(this);
        getWindow().setStatusBarColor(Ui.GREEN_DEEP);
        String id = getIntent() != null ? getIntent().getStringExtra("id") : null;
        p = id != null ? Project.byId(this, id) : null;
        if (p == null) { finish(); return; }
        p.srcDir(this).mkdirs();
        wrapOn = Prefs.wrap(this);
        buildUi();
        refreshFiles();
        openFirstFile();
    }

    private LinearLayout.LayoutParams rowWeight() {
        return new LinearLayout.LayoutParams(0, -2, 1f);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(t.bg);
        setContentView(root);

        // header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackground(Ui.gradient(Ui.GREEN_DEEP, Ui.GREEN, 0, this));
        header.setPadding(Ui.dp(16), Ui.dp(24), Ui.dp(16), Ui.dp(14));

        LinearLayout hrow = new LinearLayout(this);
        hrow.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Ui.text(this, "←", 20, Color.WHITE, true);
        back.setBackground(Ui.ripple(this, Ui.fill(0x33FFFFFF, 12, this)));
        back.setPadding(Ui.dp(12), Ui.dp(6), Ui.dp(12), Ui.dp(6));
        back.setOnClickListener(v -> finish());
        hrow.addView(back, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout hcol = new LinearLayout(this);
        hcol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams hclp = new LinearLayout.LayoutParams(0, -2, 1f);
        hclp.leftMargin = Ui.dp(10);
        hcol.addView(Ui.text(this, "🧠  Code IDE", 17, Color.WHITE, true));
        hcol.addView(Ui.text(this, p.name, 11, 0xB3FFFFFF, false));
        hrow.addView(hcol, hclp);
        header.addView(hrow, new LinearLayout.LayoutParams(-1, -2));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        // main split
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams mainLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        mainLp.topMargin = Ui.dp(8);
        root.addView(main, mainLp);

        // files panel
        filesPanel = new LinearLayout(this);
        filesPanel.setOrientation(LinearLayout.VERTICAL);
        filesPanel.setBackground(Ui.fill(t.card, 0, this));
        main.addView(filesPanel, new LinearLayout.LayoutParams(Ui.dp(140), -1));

        TextView fh = Ui.text(this, "  FILES", 10, t.textSub, true);
        filesPanel.addView(fh, new LinearLayout.LayoutParams(-1, -2));

        filesList = new ListView(this);
        filesList.setDivider(null);
        filesList.setDividerHeight(Ui.dp(2));
        filesList.setAdapter(new FilesAdapter());
        filesList.setOnItemClickListener((parent, v, pos, id2) -> onFileTap(pos));
        filesList.setOnItemLongClickListener((parent, v, pos, id2) -> { fileOptions(pos); return true; });
        filesPanel.addView(filesList, new LinearLayout.LayoutParams(-1, 0, 1f));

        // editor panel
        LinearLayout edCol = new LinearLayout(this);
        edCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams edLp = new LinearLayout.LayoutParams(0, -1, 1f);
        edLp.leftMargin = Ui.dp(6);
        main.addView(edCol, edLp);

        filePathLabel = Ui.text(this, "no file open", 11, t.textSub, true);
        filePathLabel.setSingleLine(true);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, -2);
        fp.leftMargin = Ui.dp(4);
        edCol.addView(filePathLabel, fp);

        editor = new EditText(this);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setTextSize(Prefs.fontSize(this));
        editor.setTextColor(t.text);
        editor.setHint("Write your Java code here…");
        editor.setHintTextColor(t.textSub);
        editor.setBackgroundColor(Color.TRANSPARENT);
        editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setHorizontallyScrolling(!wrapOn);
        editor.setGravity(Gravity.TOP);
        int ep = Ui.dp(8);
        editor.setPadding(ep, ep, ep, ep);
        editor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                scheduleHighlight();
                updateMeta();
            }
        });
        editor.setOnClickListener(v -> updateMeta());
        edCol.addView(editor, new LinearLayout.LayoutParams(-1, 0, 1f));

        metaLabel = Ui.text(this, "Ln 1  •  font " + Prefs.fontSize(this), 10, t.textSub, false);
        metaLabel.setGravity(Gravity.END);
        edCol.addView(metaLabel, new LinearLayout.LayoutParams(-1, -2));

        // toolbar
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(Ui.dp(8), Ui.dp(6), Ui.dp(8), Ui.dp(8));
        hs.addView(bar, new HorizontalScrollView.LayoutParams(-2, -2));

        bar.add(toolBtn("💾 Save", v -> { saveCurrent(); Ui.toast(this, "Saved ✔"); }));
        bar.add(toolBtn("➕ File", v -> newFileDialog()));
        bar.add(toolBtn("📁 Folder", v -> newFolderDialog()));
        bar.add(toolBtn("📥 Import", v -> importFiles()));
        bar.add(toolBtn("🗑 Delete", v -> deleteCurrent()));
        bar.add(toolBtn("↩ Undo", v -> undo()));
        bar.add(toolBtn("🔍 Find", v -> findDialog()));
        bar.add(toolBtn("A+", v -> changeFont(2)));
        bar.add(toolBtn("A-", v -> changeFont(-2)));
        bar.add(toolBtn("📐 Wrap", v -> toggleWrap()));
        bar.add(toolBtn("📋 Snip", v -> snippetsDialog()));
        bar.add(toolBtn("⚡ Convert", v -> convert()));

        LinearLayout toolWrap = new LinearLayout(this);
        toolWrap.setOrientation(LinearLayout.VERTICAL);
        toolWrap.addView(hs, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams twLp = new LinearLayout.LayoutParams(-1, -2);
        root.addView(toolWrap, twLp);

        // overlay for convert
        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER);
        overlay.setBackgroundColor(0xF0FFFFFF);
        overlay.setClickable(true);
        overlay.setVisibility(View.GONE);
        ProgressBar pb = new ProgressBar(this);
        pb.setIndeterminateTintList(ColorStateList.valueOf(Ui.GREEN));
        overlay.addView(pb, new LinearLayout.LayoutParams(-2, -2));
        overlayStatus = Ui.text(this, "Working…", 14, t.text, true);
        LinearLayout.LayoutParams osp = new LinearLayout.LayoutParams(-2, -2);
        osp.topMargin = Ui.dp(14);
        overlay.addView(overlayStatus, osp);
        FrameLayout rl = new FrameLayout(this);
        setContentView(rl);
        rl.addView(root, new FrameLayout.LayoutParams(-1, -1));
        rl.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
    }

    private TextView toolBtn(String label, View.OnClickListener l) {
        TextView b = Ui.text(this, label, 12, Color.WHITE, true);
        b.setBackground(Ui.ripple(this, Ui.fill(Ui.GREEN, 10, this)));
        b.setPadding(Ui.dp(12), Ui.dp(8), Ui.dp(12), Ui.dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Ui.dp(8);
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        Ui.pressScale(b, 0.94f);
        return b;
    }

    // ---------------- files ----------------

    private void buildEntries(List<Object[]> out, File dir, String prefix) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        List<File> dirs = new ArrayList<>(), files = new ArrayList<>();
        for (File f : fs) {
            if (f.isDirectory()) dirs.add(f); else files.add(f);
        }
        Collections.sort(dirs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        Collections.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File d : dirs) {
            String rel = prefix.length() == 0 ? d.getName() : prefix + "/" + d.getName();
            out.add(new Object[]{rel + "/", Boolean.TRUE});
            buildEntries(out, d, rel);
        }
        for (File f : files) {
            if (!f.getName().toLowerCase().endsWith(".java")) continue;
            String rel = prefix.length() == 0 ? f.getName() : prefix + "/" + f.getName();
            out.add(new Object[]{rel, Boolean.FALSE});
        }
    }

    private void refreshFiles() {
        entries.clear();
        buildEntries(entries, p.srcDir(this), "");
        ((BaseAdapter) filesList.getAdapter()).notifyDataSetChanged();
    }

    private File fileFor(String rel) {
        return new File(p.srcDir(this), rel);
    }

    private void onFileTap(int pos) {
        Object[] e = entries.get(pos);
        boolean isDir = (Boolean) e[1];
        String rel = (String) e[0];
        if (isDir) {
            Ui.toast(this, "Folder: " + rel);
            return;
        }
        saveCurrent();
        openFile(rel);
    }

    private void openFirstFile() {
        for (Object[] e : entries) {
            if (!(Boolean) e[1]) { openFile((String) e[0]); return; }
        }
        newFileDialog();
    }

    private void openFile(String rel) {
        try {
            currentRel = rel;
            File f = fileFor(rel);
            byte[] data = new byte[(int) f.length()];
            FileInputStream in = new FileInputStream(f);
            int read = 0, n;
            while ((n = in.read(data, read, data.length - read)) > 0) read += n;
            in.close();
            String content = new String(data);
            undoMap.put(rel, content);
            editor.setText(content);
            editor.setTextSize(Prefs.fontSize(this));
            filePathLabel.setText("📄  " + rel);
            scheduleHighlight();
            updateMeta();
        } catch (Exception e) {
            Ui.toast(this, "Cannot open: " + rel);
        }
    }

    private void saveCurrent() {
        if (currentRel == null) return;
        try {
            File f = fileFor(currentRel);
            f.getParentFile().mkdirs();
            FileWriter w = new FileWriter(f);
            w.write(editor.getText().toString());
            w.close();
        } catch (Exception ignored) { }
    }

    private void newFileDialog() {
        final EditText input = pathInput("e.g. com/mod/MyHook.java");
        new AlertDialog.Builder(this)
                .setTitle("New Java file")
                .setMessage("Use / to create subfolders.\nClass name should match file name.")
                .setView(wrapInput(input))
                .setPositiveButton("Create", (d, w) -> {
                    String rel = input.getText().toString().trim().replace("\\", "/");
                    if (rel.length() == 0) return;
                    if (rel.contains("..")) { Ui.toast(this, "Invalid path"); return; }
                    if (!rel.toLowerCase().endsWith(".java")) rel = rel + ".java";
                    File f = fileFor(rel);
                    if (f.exists()) { Ui.toast(this, "File already exists"); return; }
                    try {
                        f.getParentFile().mkdirs();
                        String cls = rel.substring(rel.lastIndexOf('/') + 1)
                                .replace(".java", "");
                        FileWriter w2 = new FileWriter(f);
                        w2.write("public class " + cls + " {\n    \n}\n");
                        w2.close();
                    } catch (Exception ignored) { }
                    refreshFiles();
                    saveCurrent();
                    openFile(rel);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void newFolderDialog() {
        final EditText input = pathInput("e.g. com/mod/utils");
        new AlertDialog.Builder(this)
                .setTitle("New folder")
                .setView(wrapInput(input))
                .setPositiveButton("Create", (d, w) -> {
                    String rel = input.getText().toString().trim().replace("\\", "/");
                    if (rel.length() == 0 || rel.contains("..")) return;
                    fileFor(rel).mkdirs();
                    refreshFiles();
                    Ui.toast(this, "Folder created ✔");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void importFiles() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(i, "Import .java files or a .zip"), REQ_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMPORT || resultCode != RESULT_OK || data == null) return;
        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++)
                uris.add(data.getClipData().getItemAt(i).getUri());
        } else if (data.getData() != null) uris.add(data.getData());
        int count = 0;
        for (Uri u : uris) {
            try {
                String name = uriName(u);
                if (name == null) name = "import_" + System.currentTimeMillis() + ".java";
                name = name.replace("..", "_").replace("/", "_").replace("\\", "_");
                File dst = new File(p.srcDir(this), name);
                InputStream in = getContentResolver().openInputStream(u);
                OutputStream out = new FileOutputStream(dst);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.close(); in.close();
                if (name.toLowerCase().endsWith(".zip")) {
                    Ui.unzipJava(dst, p.srcDir(this));
                    dst.delete();
                }
                count++;
            } catch (Exception ignored) { }
        }
        refreshFiles();
        Ui.toast(this, count + " item(s) imported ✔");
    }

    private String uriName(Uri u) {
        try {
            Cursor c = getContentResolver().query(u, null, null, null, null);
            if (c != null) {
                try {
                    int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0 && c.moveToFirst()) {
                        String s = c.getString(idx);
                        if (s != null && s.length() > 0) return s;
                    }
                } finally { c.close(); }
            }
        } catch (Exception ignored) { }
        String seg = u.getLastPathSegment();
        if (seg == null) return null;
        int i = seg.lastIndexOf('/');
        return i >= 0 ? seg.substring(i + 1) : seg;
    }

    private void fileOptions(int pos) {
        final Object[] e = entries.get(pos);
        final String rel = (String) e[0];
        final boolean isDir = (Boolean) e[1];
        final String[] opts = isDir
                ? new String[]{"📂 Delete folder"}
                : new String[]{"📄 Open", "✏ Rename", "🗑 Delete"};
        new AlertDialog.Builder(this)
                .setTitle(rel)
                .setItems(opts, (d, w) -> {
                    if (isDir) {
                        if (w == 0) confirmDeletePath(rel, true);
                    } else {
                        if (w == 0) { saveCurrent(); openFile(rel); }
                        else if (w == 1) renameDialog(rel);
                        else if (w == 2) confirmDeletePath(rel, false);
                    }
                }).show();
    }

    private void confirmDeletePath(final String rel, final boolean dir) {
        new AlertDialog.Builder(this)
                .setTitle("Delete " + (dir ? "folder" : "file") + "?")
                .setMessage(rel)
                .setPositiveButton("Delete", (d, w) -> {
                    File f = fileFor(dir ? rel.substring(0, rel.length() - 1) : rel);
                    Project.deleteDir(f);
                    if (currentRel != null && currentRel.startsWith(dir
                            ? rel : rel.substring(0, Math.max(0, rel.lastIndexOf('/') + 1)))) {
                        currentRel = null;
                        editor.setText("");
                        filePathLabel.setText("no file open");
                    }
                    refreshFiles();
                    Ui.toast(this, "Deleted");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void renameDialog(final String rel) {
        final EditText input = pathInput(rel);
        input.setText(rel);
        new AlertDialog.Builder(this)
                .setTitle("Rename / move")
                .setMessage("Change name or path (use / for folders)")
                .setView(wrapInput(input))
                .setPositiveButton("Rename", (d, w) -> {
                    String nr = input.getText().toString().trim().replace("\\", "/");
                    if (nr.length() == 0 || nr.contains("..") || nr.equals(rel)) return;
                    File from = fileFor(rel);
                    File to = fileFor(nr);
                    to.getParentFile().mkdirs();
                    if (from.renameTo(to)) {
                        if (rel.equals(currentRel)) currentRel = nr;
                        refreshFiles();
                        Ui.toast(this, "Renamed ✔");
                    } else Ui.toast(this, "Rename failed");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteCurrent() {
        if (currentRel == null) { Ui.toast(this, "No file open"); return; }
        confirmDeletePath(currentRel, false);
    }

    // ---------------- editor tools ----------------

    private void undo() {
        if (currentRel == null) { Ui.toast(this, "No file open"); return; }
        String snap = undoMap.get(currentRel);
        if (snap == null) { Ui.toast(this, "Nothing to undo"); return; }
        String now = editor.getText().toString();
        undoMap.put(currentRel, now);
        editor.setText(snap);
        editor.setSelection(snap.length());
        Ui.toast(this, "Undo ↩ (tap again to redo)");
    }

    private void findDialog() {
        final EditText input = pathInput("Find text…");
        new AlertDialog.Builder(this)
                .setTitle("Find in code")
                .setView(wrapInput(input))
                .setPositiveButton("Find", (d, w) -> findNext(input.getText().toString()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void findNext(String q) {
        if (q == null || q.length() == 0) return;
        String text = editor.getText().toString();
        int start = editor.getSelectionEnd();
        int idx = text.indexOf(q, start);
        if (idx < 0) idx = text.indexOf(q, 0);
        if (idx < 0) { Ui.toast(this, "Not found"); return; }
        editor.requestFocus();
        editor.setSelection(idx, idx + q.length());
    }

    private void changeFont(int delta) {
        int s = Prefs.fontSize(this) + delta;
        if (s < 10) s = 10;
        if (s > 28) s = 28;
        Prefs.fontSize(this, s);
        editor.setTextSize(s);
        updateMeta();
    }

    private void toggleWrap() {
        wrapOn = !wrapOn;
        Prefs.wrap(this, wrapOn);
        editor.setHorizontallyScrolling(!wrapOn);
        Ui.toast(this, wrapOn ? "Word wrap ON" : "Word wrap OFF (scroll sideways)");
    }

    private void snippetsDialog() {
        final String[][] snips = {
                {"public class", "public class NAME {\n    \n}\n"},
                {"main method", "public static void main(String[] args) {\n    \n}\n"},
                {"Log.d", "android.util.Log.d(\"Java2Dex\", \"msg\");\n"},
                {"Toast", "android.widget.Toast.makeText(ctx, \"msg\", 0).show();\n"},
                {"for loop", "for (int i = 0; i < 10; i++) {\n    \n}\n"},
                {"if-else", "if (cond) {\n    \n} else {\n    \n}\n"},
                {"try-catch", "try {\n    \n} catch (Exception e) {\n    \n}\n"},
                {"Xposed hook", "XposedHelpers.findAndHookMethod(\"cls\", lpparam.classLoader,\n        \"method\", new XC_MethodHook() {\n    @Override\n    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {\n        \n    }\n});\n"}
        };
        String[] names = new String[snips.length];
        for (int i = 0; i < snips.length; i++) names[i] = snips[i][0];
        new AlertDialog.Builder(this)
                .setTitle("Insert snippet")
                .setItems(names, (d, w) -> {
                    int at = editor.getSelectionStart();
                    if (at < 0) at = 0;
                    editor.getText().insert(at, snips[w][1]);
                }).show();
    }

    // ---------------- convert ----------------

    private void convert() {
        saveCurrent();
        overlay.setVisibility(View.VISIBLE);
        Converter.convert(this, p, new Converter.Callback() {
            @Override public void onStep(String s) { overlayStatus.setText(s); }
            @Override public void onDone(boolean ok, String log) {
                overlay.setVisibility(View.GONE);
                try {
                    FileWriter w = new FileWriter(p.logFile(IdeActivity.this));
                    w.write(log); w.close();
                } catch (Exception ignored) { }
                if (ok) {
                    new AlertDialog.Builder(IdeActivity.this)
                            .setTitle("✔ Converted")
                            .setMessage("DEX saved to:\n" + p.publicDexFile(IdeActivity.this)
                                    .getAbsolutePath())
                            .setPositiveButton("Open Smali", (d, w) -> {
                                Intent i = new Intent(IdeActivity.this, DexViewerActivity.class);
                                i.putExtra("id", p.id);
                                startActivity(i);
                            })
                            .setNegativeButton("OK", null)
                            .show();
                } else {
                    ScrollView sc = new ScrollView(IdeActivity.this);
                    TextView tv = Ui.text(IdeActivity.this, log, 11.5f, 0xFF7F1D1D, false);
                    tv.setTypeface(Typeface.MONOSPACE);
                    int pd = Ui.dp(14);
                    tv.setPadding(pd, pd, pd, pd);
                    sc.addView(tv, new FrameLayout.LayoutParams(-1, -2));
                    new AlertDialog.Builder(IdeActivity.this)
                            .setTitle("✖ Build Failed")
                            .setView(sc)
                            .setPositiveButton("Copy", (d, w) -> Ui.copy(IdeActivity.this, "log", log))
                            .setNegativeButton("Close", null)
                            .show();
                }
            }
        });
    }

    // ---------------- highlighting ----------------

    private void scheduleHighlight() {
        if (hlRun != null) hl.removeCallbacks(hlRun);
        hlRun = this::applyHighlights;
        hl.postDelayed(hlRun, 450);
    }

    private void applyHighlights() {
        Editable e = editor.getText();
        if (e == null) return;
        int len = e.length();
        if (len == 0 || len > 30000) return;
        ForegroundColorSpan[] spans = e.getSpans(0, len, ForegroundColorSpan.class);
        for (ForegroundColorSpan s : spans) e.removeSpan(s);

        int kwColor = Prefs.dark(this) ? 0xFFC084FC : 0xFF7C3AED;
        int strColor = Prefs.dark(this) ? 0xFF34D399 : 0xFF059669;
        int comColor = Prefs.dark(this) ? 0xFF64748B : 0xFF94A3B8;
        int numColor = Prefs.dark(this) ? 0xFFFB923C : 0xFFEA580C;

        String text = e.toString();
        int i = 0;
        while (i < len) {
            char c = text.charAt(i);
            if (c == '/' && i + 1 < len && text.charAt(i + 1) == '/') {
                int end = text.indexOf('\n', i);
                if (end < 0) end = len;
                e.setSpan(new ForegroundColorSpan(comColor), i, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                i = end;
            } else if (c == '/' && i + 1 < len && text.charAt(i + 1) == '*') {
                int end = text.indexOf("*/", i + 2);
                end = end < 0 ? len : end + 2;
                e.setSpan(new ForegroundColorSpan(comColor), i, Math.min(end, len),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                i = end;
            } else if (c == '"') {
                int j = i + 1;
                while (j < len) {
                    if (text.charAt(j) == '\\') { j += 2; continue; }
                    if (text.charAt(j) == '"' || text.charAt(j) == '\n') { j++; break; }
                    j++;
                }
                e.setSpan(new ForegroundColorSpan(strColor), i, Math.min(j, len),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                i = j;
            } else if (Character.isDigit(c)) {
                int j = i;
                while (j < len && (Character.isDigit(text.charAt(j))
                        || text.charAt(j) == '.' || text.charAt(j) == 'x'
                        || text.charAt(j) == 'L' || text.charAt(j) == 'f')) j++;
                e.setSpan(new ForegroundColorSpan(numColor), i, j,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                i = j;
            } else if (Character.isJavaIdentifierStart(c)) {
                int j = i;
                while (j < len && Character.isJavaIdentifierPart(text.charAt(j))) j++;
                String word = text.substring(i, j);
                for (String k : KEYWORDS) {
                    if (k.equals(word)) {
                        e.setSpan(new ForegroundColorSpan(kwColor), i, j,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        break;
                    }
                }
                i = j;
            } else {
                i++;
            }
        }
    }

    private void updateMeta() {
        String text = editor.getText() == null ? "" : editor.getText().toString();
        int sel = editor.getSelectionEnd();
        if (sel < 0) sel = 0;
        if (sel > text.length()) sel = text.length();
        int lines = 1;
        int lastNl = -1;
        for (int i = 0; i < sel; i++) {
            if (text.charAt(i) == '\n') { lines++; lastNl = i; }
        }
        int col = sel - lastNl;
        metaLabel.setText("Ln " + lines + ", Col " + col + "  •  font "
                + Prefs.fontSize(this) + (wrapOn ? "  •  wrap" : ""));
    }

    // ---------------- helpers ----------------

    private FrameLayout wrapInput(EditText e) {
        FrameLayout f = new FrameLayout(this);
        int p = Ui.dp(20);
        f.setPadding(p, Ui.dp(8), p, 0);
        f.addView(e, new FrameLayout.LayoutParams(-1, -2));
        return f;
    }

    private EditText pathInput(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(14);
        e.setTextColor(t.text);
        e.setHintTextColor(t.textSub);
        e.setSingleLine(true);
        return e;
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveCurrent();
    }

    // ---------------- adapter ----------------

    private class FilesAdapter extends BaseAdapter {
        @Override public int getCount() { return entries.size(); }
        @Override public Object getItem(int position) { return entries.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, android.view.ViewGroup parent) {
            LinearLayout row = new LinearLayout(IdeActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int pad = Ui.dp(8);
            row.setPadding(pad, pad, pad, pad);
            Object[] e = entries.get(position);
            boolean isDir = (Boolean) e[1];
            String rel = (String) e[0];
            TextView tv = Ui.text(IdeActivity.this,
                    (isDir ? "📁 " : "📄 ") + rel, 11.5f,
                    isDir ? t.accentDark : t.text, isDir);
            tv.setSingleLine(true);
            row.addView(tv, new LinearLayout.LayoutParams(-1, -2));
            if (rel.equals(currentRel)) {
                row.setBackgroundColor(t.accentSoft);
            }
            return row;
        }
    }
}
