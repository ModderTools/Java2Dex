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
import android.text.InputFilter;
import android.text.Spannable;
import android.text.Spanned;
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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** AIDE-style code IDE: ☰ full-screen explorer, code sheet with line numbers, symbol bar */
public class IdeActivity extends Activity {

    private static final int REQ_IMPORT = 51;
    private static final int MAX_HIGHLIGHT = 120000;
    private static final int MAX_OPEN_BYTES = 2 * 1024 * 1024;

    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
            "abstract","assert","boolean","break","byte","case","catch","char","class","const",
            "continue","default","do","double","else","enum","extends","final","finally","float",
            "for","goto","if","implements","import","instanceof","int","interface","long","native",
            "new","package","private","protected","public","return","short","static","strictfp",
            "super","switch","synchronized","this","throw","throws","transient","try","void",
            "volatile","while","true","false","null","var","record","yield"));

    private static class Hist {
        final ArrayList<String> undo = new ArrayList<>();
        final ArrayList<String> redo = new ArrayList<>();
        String last;
    }

    private Theme t;
    private Project p;
    private ListView filesList;
    private CodeEditText editor;
    private TextView fileTitle, metaLabel, explorerSub;
    private LinearLayout explorer, overlay;
    private TextView overlayStatus;

    private final List<Object[]> entries = new ArrayList<>();
    private final Map<String, Hist> hist = new HashMap<>();
    private String currentRel = null;
    private String selectedDir = "";          // folder chosen in the explorer ("" = root)
    private final Handler hl = new Handler();
    private Runnable hlRun, snapRun, saveRun;
    private boolean wrapOn = true;
    private boolean programmatic = false;     // true while we set editor text ourselves
    private boolean dirty = false;
    private String lastFind = "", lastRepl = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        t = Theme.get(this);
        getWindow().setStatusBarColor(Ui.GREEN_DEEP);
        getWindow().setNavigationBarColor(0xFF0B1F12);
        String id = getIntent() != null ? getIntent().getStringExtra("id") : null;
        p = id != null ? Project.byId(this, id) : null;
        if (p == null) { finish(); return; }
        p.srcDir(this).mkdirs();
        ensureDefaultFile();
        wrapOn = Prefs.wrap(this);
        buildUi();
        refreshFiles();
        openFirstFile();
    }

    private void ensureDefaultFile() {
        List<File> found = new ArrayList<>();
        findJava(p.srcDir(this), found);
        if (found.isEmpty()) {
            try {
                Ui.writeText(new File(p.srcDir(this), "Main.java"),
                        "public class Main {\n    public static void main(String[] args) {\n        \n    }\n}\n");
            } catch (Throwable ignored) { }
        }
    }

    private void findJava(File dir, List<File> out) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) findJava(f, out);
            else if (f.getName().toLowerCase().endsWith(".java")) out.add(f);
        }
    }

    // ================================================================ UI

    private void buildUi() {
        boolean dark = Prefs.dark(this);
        FrameLayout base = new FrameLayout(this);
        base.setBackgroundColor(t.bg);
        setContentView(base);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        base.addView(root, new FrameLayout.LayoutParams(-1, -1));

        // ---------- header ----------
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackground(Ui.gradient(Ui.GREEN_DEEP, Ui.GREEN, 0, this));
        header.setPadding(Ui.dp(12), Ui.dp(20), Ui.dp(12), Ui.dp(12));

        LinearLayout hrow = new LinearLayout(this);
        hrow.setGravity(Gravity.CENTER_VERTICAL);

        TextView menu = Ui.text(this, "☰", 20, Color.WHITE, true);
        menu.setGravity(Gravity.CENTER);
        menu.setBackground(Ui.ripple(this, Ui.fill(0x33FFFFFF, 12, this)));
        menu.setPadding(Ui.dp(14), Ui.dp(7), Ui.dp(14), Ui.dp(7));
        menu.setOnClickListener(v -> toggleExplorer());
        hrow.addView(menu, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout hcol = new LinearLayout(this);
        hcol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams hclp = new LinearLayout.LayoutParams(0, -2, 1f);
        hclp.leftMargin = Ui.dp(12);
        fileTitle = Ui.text(this, "Java2Dex IDE", 16.5f, Color.WHITE, true);
        fileTitle.setSingleLine(true);
        hcol.addView(fileTitle);
        hcol.addView(Ui.text(this, p.name + "  •  tap ☰ for files", 10.5f, 0xB3FFFFFF, false));
        hrow.addView(hcol, hclp);

        hrow.addView(headBtn("💾", v -> {
            if (saveCurrent()) Ui.toast(this, "Saved ✔");
        }));
        // prominent Convert / Run button: solid white pill with a green play icon
        TextView play = Ui.text(this, "▶", 19, Ui.GREEN_DEEP, true);
        play.setGravity(Gravity.CENTER);
        play.setBackground(Ui.ripple(this, Ui.fill(Color.WHITE, 22, this)));
        play.setPadding(Ui.dp(16), Ui.dp(7), Ui.dp(16), Ui.dp(7));
        play.setElevation(Ui.dp(3));
        play.setContentDescription("Convert to DEX");
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-2, -2);
        plp.leftMargin = Ui.dp(10);
        play.setLayoutParams(plp);
        play.setOnClickListener(v -> convert());
        Ui.pressScale(play, 0.9f);
        hrow.addView(play);

        header.addView(hrow, new LinearLayout.LayoutParams(-1, -2));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        // ---------- code sheet ----------
        FrameLayout sheet = new FrameLayout(this);
        sheet.setBackground(Ui.outline(dark ? t.card : Color.WHITE, t.cardStroke, 16, 1, this));
        sheet.setElevation(Ui.dp(3));
        sheet.setClipToOutline(true);

        editor = new CodeEditText(this, t.textSub,
                dark ? 0xFF0F1A2E : 0xFFF1F5F9, t.cardStroke);
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
        editor.setCodePadding(Ui.dp(12), Ui.dp(12), Ui.dp(12));
        editor.setFilters(new InputFilter[]{ indentFilter });
        editor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                if (!programmatic) {
                    setDirty(true);
                    scheduleSnapshot();
                    scheduleAutosave();
                }
                scheduleHighlight();
                updateMeta();
            }
        });
        sheet.addView(editor, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout.LayoutParams sheetLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        sheetLp.setMargins(Ui.dp(10), Ui.dp(10), Ui.dp(10), Ui.dp(6));
        root.addView(sheet, sheetLp);

        // ---------- meta strip ----------
        metaLabel = Ui.text(this, "Ln 1, Col 1", 10, t.textSub, false);
        metaLabel.setSingleLine(true);
        metaLabel.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        metaLabel.setBackground(Ui.fill(t.chipBg, 8, this));
        metaLabel.setPadding(Ui.dp(12), Ui.dp(5), Ui.dp(12), Ui.dp(5));
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(-1, -2);
        metaLp.setMargins(Ui.dp(10), 0, Ui.dp(10), Ui.dp(6));
        root.addView(metaLabel, metaLp);

        // ---------- symbol bar + toolbar ----------
        LinearLayout toolStrip = new LinearLayout(this);
        toolStrip.setOrientation(LinearLayout.VERTICAL);
        toolStrip.setBackgroundColor(0xFF0B1F12);

        HorizontalScrollView sym = new HorizontalScrollView(this);
        sym.setHorizontalScrollBarEnabled(false);
        LinearLayout symBar = new LinearLayout(this);
        symBar.setPadding(Ui.dp(8), Ui.dp(7), Ui.dp(8), Ui.dp(2));
        sym.addView(symBar, new HorizontalScrollView.LayoutParams(-2, -2));
        symBar.addView(symBtn("⇥", "    ", 0));
        symBar.addView(symBtn("{}", "{}", 1));
        symBar.addView(symBtn("()", "()", 1));
        symBar.addView(symBtn("[]", "[]", 1));
        symBar.addView(symBtn("\"\"", "\"\"", 1));
        symBar.addView(symBtn(";", ";", 0));
        String[] singles = {"{", "}", "(", ")", "[", "]", "=", ".", ",", "<", ">", "!", "&", "|",
                "+", "-", "*", "/", "_", ":", "?", "'", "\\", "@", "#", "%"};
        for (String s : singles) symBar.addView(symBtn(s, s, 0));
        toolStrip.addView(sym, new LinearLayout.LayoutParams(-1, -2));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout bar = new LinearLayout(this);
        bar.setPadding(Ui.dp(8), Ui.dp(5), Ui.dp(8), Ui.dp(9));
        hs.addView(bar, new HorizontalScrollView.LayoutParams(-2, -2));
        bar.addView(toolBtn("↩ Undo", v -> undo()));
        bar.addView(toolBtn("↪ Redo", v -> redo()));
        bar.addView(toolBtn("🔍 Find", v -> findDialog()));
        bar.addView(toolBtn("# Line", v -> gotoDialog()));
        bar.addView(toolBtn("📋 Snip", v -> snippetsDialog()));
        bar.addView(toolBtn("A+", v -> changeFont(2)));
        bar.addView(toolBtn("A-", v -> changeFont(-2)));
        bar.addView(toolBtn("📐 Wrap", v -> toggleWrap()));
        bar.addView(toolBtn("⚡ Convert", v -> convert()));
        toolStrip.addView(hs, new LinearLayout.LayoutParams(-1, -2));
        root.addView(toolStrip, new LinearLayout.LayoutParams(-1, -2));

        // ---------- explorer overlay ----------
        explorer = new LinearLayout(this);
        explorer.setOrientation(LinearLayout.VERTICAL);
        explorer.setBackgroundColor(t.bg);
        explorer.setClickable(true);
        explorer.setVisibility(View.GONE);

        LinearLayout exHeader = new LinearLayout(this);
        exHeader.setOrientation(LinearLayout.VERTICAL);
        exHeader.setBackground(Ui.gradient(Ui.GREEN_DEEP, Ui.GREEN, 0, this));
        exHeader.setPadding(Ui.dp(16), Ui.dp(22), Ui.dp(16), Ui.dp(14));

        LinearLayout exRow = new LinearLayout(this);
        exRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView close = Ui.text(this, "✕", 16, Color.WHITE, true);
        close.setGravity(Gravity.CENTER);
        close.setBackground(Ui.ripple(this, Ui.fill(0x33FFFFFF, 12, this)));
        close.setPadding(Ui.dp(14), Ui.dp(7), Ui.dp(14), Ui.dp(7));
        close.setOnClickListener(v -> toggleExplorer());
        exRow.addView(close, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout exCol = new LinearLayout(this);
        exCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams exClp = new LinearLayout.LayoutParams(0, -2, 1f);
        exClp.leftMargin = Ui.dp(12);
        exCol.addView(Ui.text(this, "FILES", 16, Color.WHITE, true));
        explorerSub = Ui.text(this, p.name, 10.5f, 0xB3FFFFFF, false);
        exCol.addView(explorerSub);
        exRow.addView(exCol, exClp);
        exHeader.addView(exRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout exActions = new LinearLayout(this);
        exActions.setPadding(0, Ui.dp(12), 0, 0);
        exActions.addView(exBtn("＋ File", v -> newFileDialog()));
        exActions.addView(exBtn("📁 Folder", v -> newFolderDialog()));
        exActions.addView(exBtn("📥 Import", v -> importFiles()));
        exActions.addView(exBtn("🗑 Delete", v -> deleteCurrent()));
        exHeader.addView(exActions, new LinearLayout.LayoutParams(-1, -2));
        explorer.addView(exHeader, new LinearLayout.LayoutParams(-1, -2));

        filesList = new ListView(this);
        filesList.setDivider(null);
        filesList.setDividerHeight(Ui.dp(8));
        filesList.setPadding(Ui.dp(14), Ui.dp(10), Ui.dp(14), Ui.dp(20));
        filesList.setClipToPadding(false);
        filesList.setAdapter(new FilesAdapter());
        filesList.setOnItemClickListener((parent, v, pos, id2) -> onFileTap(pos));
        filesList.setOnItemLongClickListener((parent, v, pos, id2) -> {
            fileOptions(pos);
            return true;
        });
        explorer.addView(filesList, new LinearLayout.LayoutParams(-1, 0, 1f));
        base.addView(explorer, new FrameLayout.LayoutParams(-1, -1));

        // ---------- convert overlay ----------
        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER);
        overlay.setBackgroundColor(dark ? 0xF0141E31 : 0xF0FFFFFF);
        overlay.setClickable(true);
        overlay.setVisibility(View.GONE);
        ProgressBar pb = new ProgressBar(this);
        pb.setIndeterminateTintList(ColorStateList.valueOf(Ui.GREEN));
        overlay.addView(pb, new LinearLayout.LayoutParams(-2, -2));
        overlayStatus = Ui.text(this, "Working…", 14, t.text, true);
        LinearLayout.LayoutParams osp = new LinearLayout.LayoutParams(-2, -2);
        osp.topMargin = Ui.dp(14);
        overlay.addView(overlayStatus, osp);
        base.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
    }

    private TextView headBtn(String glyph, View.OnClickListener l) {
        TextView b = Ui.text(this, glyph, 17, Color.WHITE, false);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.ripple(this, Ui.fill(0x33FFFFFF, 12, this)));
        b.setPadding(Ui.dp(13), Ui.dp(7), Ui.dp(13), Ui.dp(7));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = Ui.dp(8);
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        Ui.pressScale(b, 0.92f);
        return b;
    }

    private TextView symBtn(String label, final String insert, final int caretBack) {
        TextView b = Ui.text(this, label, 14, Color.WHITE, true);
        b.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.ripple(this, Ui.fill(0x1AFFFFFF, 8, this)));
        b.setPadding(Ui.dp(12), Ui.dp(6), Ui.dp(12), Ui.dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Ui.dp(6);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> insertAtCaret(insert, caretBack));
        Ui.pressScale(b, 0.9f);
        return b;
    }

    private TextView toolBtn(String label, View.OnClickListener l) {
        TextView b = Ui.text(this, label, 12, Color.WHITE, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.ripple(this, Ui.fill(0x26FFFFFF, 10, this)));
        b.setPadding(Ui.dp(13), Ui.dp(8), Ui.dp(13), Ui.dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Ui.dp(8);
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        Ui.pressScale(b, 0.94f);
        return b;
    }

    private TextView exBtn(String label, View.OnClickListener l) {
        TextView b = Ui.text(this, label, 12, Color.WHITE, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.ripple(this, Ui.fill(Ui.GREEN, 12, this)));
        b.setPadding(Ui.dp(8), Ui.dp(10), Ui.dp(8), Ui.dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.rightMargin = Ui.dp(8);
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        Ui.pressScale(b, 0.94f);
        return b;
    }

    private void toggleExplorer() {
        if (explorer.getVisibility() == View.VISIBLE) {
            explorer.animate().alpha(0f).translationX(Ui.dp(60)).setDuration(180).start();
            explorer.postDelayed(() -> explorer.setVisibility(View.GONE), 180);
        } else {
            refreshFiles();
            explorer.setAlpha(0f);
            explorer.setTranslationX(Ui.dp(60));
            explorer.setVisibility(View.VISIBLE);
            explorer.animate().alpha(1f).translationX(0).setDuration(220).start();
        }
    }

    @Override
    public void onBackPressed() {
        if (explorer != null && explorer.getVisibility() == View.VISIBLE) {
            toggleExplorer();
            return;
        }
        saveCurrent();
        super.onBackPressed();
    }

    // ================================================================ files

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
        int files = 0;
        for (Object[] e : entries) if (!(Boolean) e[1]) files++;
        explorerSub.setText(p.name + "  •  " + files + " file" + (files == 1 ? "" : "s")
                + (selectedDir.length() > 0 ? "  •  in " + selectedDir : ""));
        if (filesList != null && filesList.getAdapter() != null) {
            ((BaseAdapter) filesList.getAdapter()).notifyDataSetChanged();
        }
    }

    private File fileFor(String rel) {
        return new File(p.srcDir(this), rel);
    }

    /** reject traversal, absolute paths and empty segments */
    private String cleanRel(String raw) {
        if (raw == null) return null;
        String r = raw.trim().replace("\\", "/");
        while (r.startsWith("/")) r = r.substring(1);
        while (r.endsWith("/")) r = r.substring(0, r.length() - 1);
        if (r.length() == 0) return null;
        for (String seg : r.split("/")) {
            if (seg.length() == 0 || seg.equals(".") || seg.equals("..")) return null;
        }
        return r;
    }

    private void onFileTap(int pos) {
        Object[] e = entries.get(pos);
        boolean isDir = (Boolean) e[1];
        String rel = (String) e[0];
        if (isDir) {
            String d = rel.substring(0, rel.length() - 1);
            selectedDir = d.equals(selectedDir) ? "" : d;   // tap again to deselect
            refreshFiles();
            return;
        }
        saveCurrent();
        openFile(rel);
        toggleExplorer();
    }

    private void openFirstFile() {
        for (Object[] e : entries) {
            if (!(Boolean) e[1]) { openFile((String) e[0]); return; }
        }
        refreshFiles();
        for (Object[] e : entries) {
            if (!(Boolean) e[1]) { openFile((String) e[0]); return; }
        }
    }

    private void setEditorText(String content) {
        programmatic = true;
        try {
            editor.setText(content);
        } finally {
            programmatic = false;
        }
    }

    private void openFile(String rel) {
        try {
            File f = fileFor(rel);
            if (f.length() > MAX_OPEN_BYTES) {
                Ui.toast(this, "File too large to edit on device (> 2 MB)");
                return;
            }
            String content = Ui.readText(f);
            currentRel = rel;
            setEditorText(content);
            Hist h = hist.get(rel);
            if (h == null) { h = new Hist(); hist.put(rel, h); }
            h.last = content;
            editor.setTextSize(Prefs.fontSize(this));
            setDirty(false);
            fileTitle.setText(rel.substring(rel.lastIndexOf('/') + 1));
            scheduleHighlight();
            updateMeta();
        } catch (Throwable e) {
            Ui.toast(this, "Cannot open: " + rel);
        }
    }

    /** returns true on success; silent on failure unless it is a manual save */
    private boolean saveCurrent() {
        if (currentRel == null) return true;
        try {
            Ui.writeText(fileFor(currentRel), editor.getText().toString());
            setDirty(false);
            return true;
        } catch (Throwable e) {
            Ui.toast(this, "Save failed: " + e.getMessage());
            return false;
        }
    }

    private void setDirty(boolean d) {
        dirty = d;
        if (fileTitle != null && currentRel != null) {
            String name = currentRel.substring(currentRel.lastIndexOf('/') + 1);
            fileTitle.setText(d ? name + "  ●" : name);
        }
    }

    // ================================================================ file ops

    private void newFileDialog() {
        final EditText input = pathInput("e.g. com/mod/MyHook.java");
        if (selectedDir.length() > 0) input.setText(selectedDir + "/");
        input.setSelection(input.getText().length());
        Ui.dialog(this)
                .setTitle("New Java file")
                .setMessage("Use / to create subfolders. A package line is added for you.")
                .setView(wrapInput(input))
                .setPositiveButton("Create", (d, w) -> {
                    String rel = cleanRel(input.getText().toString());
                    if (rel == null) { Ui.toast(this, "Invalid path"); return; }
                    if (!rel.toLowerCase().endsWith(".java")) rel = rel + ".java";
                    String file = rel.substring(rel.lastIndexOf('/') + 1);
                    String cls = file.substring(0, file.length() - 5);
                    if (!isIdentifier(cls)) { Ui.toast(this, "\"" + cls + "\" is not a valid class name"); return; }
                    File f = fileFor(rel);
                    if (f.exists()) { Ui.toast(this, "File already exists"); return; }
                    String pkg = rel.contains("/")
                            ? rel.substring(0, rel.lastIndexOf('/')).replace('/', '.') : "";
                    try {
                        Ui.writeText(f, (pkg.length() > 0 ? "package " + pkg + ";\n\n" : "")
                                + "public class " + cls + " {\n    \n}\n");
                    } catch (Throwable e) {
                        Ui.toast(this, "Cannot create file: " + e.getMessage());
                        return;
                    }
                    refreshFiles();
                    saveCurrent();
                    openFile(rel);
                    toggleExplorer();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static boolean isIdentifier(String s) {
        if (s == null || s.length() == 0 || !Character.isJavaIdentifierStart(s.charAt(0))) return false;
        for (int i = 1; i < s.length(); i++) if (!Character.isJavaIdentifierPart(s.charAt(i))) return false;
        return !KEYWORDS.contains(s);
    }

    private void newFolderDialog() {
        final EditText input = pathInput("e.g. com/mod/utils");
        if (selectedDir.length() > 0) input.setText(selectedDir + "/");
        input.setSelection(input.getText().length());
        Ui.dialog(this)
                .setTitle("New folder")
                .setView(wrapInput(input))
                .setPositiveButton("Create", (d, w) -> {
                    String rel = cleanRel(input.getText().toString());
                    if (rel == null) { Ui.toast(this, "Invalid path"); return; }
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
        startActivityForResult(Intent.createChooser(i, "Import .java, .zip or library .jar"), REQ_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMPORT || resultCode != RESULT_OK || data == null) return;
        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                uris.add(data.getClipData().getItemAt(i).getUri());
            }
        } else if (data.getData() != null) uris.add(data.getData());

        int src = 0, libs = 0, skipped = 0;
        for (Uri u : uris) {
            try {
                String name = uriName(u);
                if (name == null) name = "import_" + System.currentTimeMillis() + ".java";
                name = name.replace("..", "_").replace("/", "_").replace("\\", "_");
                String low = name.toLowerCase();
                InputStream in = getContentResolver().openInputStream(u);
                if (in == null) { skipped++; continue; }
                if (low.endsWith(".jar")) {
                    // library → classpath, not source
                    Ui.copyStream(in, new FileOutputStream(prep(new File(p.libsDir(this), name))));
                    libs++;
                } else if (low.endsWith(".zip")) {
                    File tmp = new File(getCacheDir(), "import_" + System.currentTimeMillis() + ".zip");
                    Ui.copyStream(in, new FileOutputStream(tmp));
                    try { Ui.unzipJava(tmp, p.srcDir(this)); } finally { tmp.delete(); }
                    src++;
                } else if (low.endsWith(".java")) {
                    File dst = new File(selectedDir.length() > 0 ? fileFor(selectedDir) : p.srcDir(this), name);
                    Ui.copyStream(in, new FileOutputStream(prep(dst)));
                    src++;
                } else {
                    in.close();
                    skipped++;
                }
            } catch (Throwable ignored) { skipped++; }
        }
        refreshFiles();
        Ui.toast(this, src + " source(s), " + libs + " library jar(s) imported"
                + (skipped > 0 ? ", " + skipped + " skipped" : ""));
    }

    private static File prep(File f) {
        File parent = f.getParentFile();
        if (parent != null) parent.mkdirs();
        return f;
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
        } catch (Throwable ignored) { }
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
                ? new String[]{"New file here", "Rename / move", "Delete folder"}
                : new String[]{"Open", "Rename / move", "Duplicate", "Delete"};
        Ui.dialog(this)
                .setTitle(rel)
                .setItems(opts, (d, w) -> {
                    if (isDir) {
                        String dn = rel.substring(0, rel.length() - 1);
                        if (w == 0) { selectedDir = dn; newFileDialog(); }
                        else if (w == 1) renameDialog(dn, true);
                        else confirmDeletePath(rel, true);
                    } else {
                        if (w == 0) { saveCurrent(); openFile(rel); toggleExplorer(); }
                        else if (w == 1) renameDialog(rel, false);
                        else if (w == 2) duplicate(rel);
                        else confirmDeletePath(rel, false);
                    }
                }).show();
    }

    private void duplicate(String rel) {
        saveCurrent();
        String base = rel.substring(0, rel.length() - 5);
        String cand = base + "Copy.java";
        int n = 2;
        while (fileFor(cand).exists()) cand = base + "Copy" + (n++) + ".java";
        try {
            Ui.copyFile(fileFor(rel), fileFor(cand));
            refreshFiles();
            Ui.toast(this, "Duplicated — rename the class inside!");
        } catch (Throwable e) {
            Ui.toast(this, "Duplicate failed: " + e.getMessage());
        }
    }

    private void confirmDeletePath(final String rel, final boolean dir) {
        Ui.dialog(this)
                .setTitle("Delete " + (dir ? "folder" : "file") + "?")
                .setMessage(rel)
                .setPositiveButton("Delete", (d, w) -> {
                    String clean = dir ? rel.substring(0, rel.length() - 1) : rel;
                    // never write the open file back after deleting it
                    boolean hit = currentRel != null
                            && (dir ? (currentRel.equals(clean) || currentRel.startsWith(rel))
                                    : currentRel.equals(rel));
                    if (hit) { currentRel = null; }
                    Project.deleteDir(fileFor(clean));
                    hist.remove(rel);
                    if (hit) {
                        setEditorText("");
                        setDirty(false);
                        fileTitle.setText("Java2Dex IDE");
                    }
                    if (dir && selectedDir.equals(clean)) selectedDir = "";
                    refreshFiles();
                    Ui.toast(this, "Deleted");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void renameDialog(final String rel, final boolean dir) {
        final EditText input = pathInput(rel);
        input.setText(rel);
        input.setSelection(rel.length());
        Ui.dialog(this)
                .setTitle("Rename / move")
                .setMessage("Change name or path (use / for folders)")
                .setView(wrapInput(input))
                .setPositiveButton("Rename", (d, w) -> {
                    String nr = cleanRel(input.getText().toString());
                    if (nr == null) { Ui.toast(this, "Invalid path"); return; }
                    if (!dir && !nr.toLowerCase().endsWith(".java")) nr = nr + ".java";
                    if (nr.equals(rel)) return;
                    File from = fileFor(rel);
                    File to = fileFor(nr);
                    if (to.exists()) { Ui.toast(this, "Target already exists"); return; }
                    saveCurrent();
                    prep(to);
                    if (from.renameTo(to)) {
                        if (currentRel != null) {
                            if (!dir && currentRel.equals(rel)) currentRel = nr;
                            else if (dir && currentRel.startsWith(rel + "/"))
                                currentRel = nr + currentRel.substring(rel.length());
                            if (currentRel != null)
                                fileTitle.setText(currentRel.substring(currentRel.lastIndexOf('/') + 1));
                        }
                        hist.clear();
                        if (currentRel != null) {
                            Hist h = new Hist(); h.last = editor.getText().toString(); hist.put(currentRel, h);
                        }
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

    // ================================================================ editor tools

    private final InputFilter indentFilter = new InputFilter() {
        @Override
        public CharSequence filter(CharSequence src, int s, int e, Spanned dest, int ds, int de) {
            if (programmatic || e - s != 1 || src.charAt(s) != '\n') return null;
            int ls = ds;
            while (ls > 0 && dest.charAt(ls - 1) != '\n') ls--;
            int k = ls;
            while (k < ds && (dest.charAt(k) == ' ' || dest.charAt(k) == '\t')) k++;
            StringBuilder sb = new StringBuilder("\n");
            sb.append(dest, ls, k);
            int q = ds - 1;
            while (q >= ls && dest.charAt(q) == ' ') q--;
            if (q >= ls && dest.charAt(q) == '{') sb.append("    ");
            return sb;
        }
    };

    private void insertAtCaret(String text, int caretBack) {
        int a = Math.max(0, editor.getSelectionStart());
        int b = Math.max(0, editor.getSelectionEnd());
        int lo = Math.min(a, b), hi = Math.max(a, b);
        Editable ed = editor.getText();
        if (caretBack > 0 && hi > lo) {
            // wrap the selection:  { selection }
            String sel = ed.subSequence(lo, hi).toString();
            String open = text.substring(0, text.length() - caretBack);
            String close = text.substring(text.length() - caretBack);
            ed.replace(lo, hi, open + sel + close);
            editor.setSelection(lo + open.length() + sel.length() + close.length());
            return;
        }
        ed.replace(lo, hi, text);
        editor.setSelection(lo + text.length() - caretBack);
    }

    // ---- undo / redo (real history, not a single snapshot) ----

    private void scheduleSnapshot() {
        if (snapRun != null) hl.removeCallbacks(snapRun);
        snapRun = new Runnable() { @Override public void run() { snapshot(); } };
        hl.postDelayed(snapRun, 700);
    }

    private void snapshot() {
        if (currentRel == null) return;
        Hist h = hist.get(currentRel);
        if (h == null) return;
        String now = editor.getText().toString();
        if (h.last != null && !h.last.equals(now)) {
            h.undo.add(h.last);
            if (h.undo.size() > 60) h.undo.remove(0);
            h.redo.clear();
        }
        h.last = now;
    }

    private void undo() {
        if (currentRel == null) { Ui.toast(this, "No file open"); return; }
        snapshot();
        Hist h = hist.get(currentRel);
        if (h == null || h.undo.isEmpty()) { Ui.toast(this, "Nothing to undo"); return; }
        h.redo.add(editor.getText().toString());
        String prev = h.undo.remove(h.undo.size() - 1);
        applyHistory(h, prev);
    }

    private void redo() {
        if (currentRel == null) { Ui.toast(this, "No file open"); return; }
        snapshot();
        Hist h = hist.get(currentRel);
        if (h == null || h.redo.isEmpty()) { Ui.toast(this, "Nothing to redo"); return; }
        h.undo.add(editor.getText().toString());
        String next = h.redo.remove(h.redo.size() - 1);
        applyHistory(h, next);
    }

    private void applyHistory(Hist h, String text) {
        int caret = Math.min(editor.getSelectionStart() < 0 ? 0 : editor.getSelectionStart(), text.length());
        setEditorText(text);
        h.last = text;
        editor.setSelection(caret);
        setDirty(true);
        scheduleAutosave();
    }

    // ---- autosave ----

    private void scheduleAutosave() {
        if (saveRun != null) hl.removeCallbacks(saveRun);
        saveRun = new Runnable() {
            @Override public void run() { if (dirty) saveQuiet(); }
        };
        hl.postDelayed(saveRun, 1500);
    }

    private void saveQuiet() {
        if (currentRel == null) return;
        try {
            Ui.writeText(fileFor(currentRel), editor.getText().toString());
            setDirty(false);
        } catch (Throwable ignored) { }
    }

    // ---- find / replace / goto ----

    private void findDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(20), Ui.dp(8), Ui.dp(20), 0);
        final EditText find = pathInput("Find…");
        final EditText repl = pathInput("Replace with…");
        String sel = selectedText();
        find.setText(sel.length() > 0 && sel.indexOf('\n') < 0 ? sel : lastFind);
        repl.setText(lastRepl);
        find.setSelection(find.getText().length());
        box.addView(find, new LinearLayout.LayoutParams(-1, -2));
        box.addView(repl, new LinearLayout.LayoutParams(-1, -2));

        final AlertDialog d = Ui.dialog(this)
                .setTitle("Find & replace")
                .setView(box)
                .setPositiveButton("Next", null)
                .setNeutralButton("Replace", null)
                .setNegativeButton("All", null)
                .create();
        d.show();
        // keep the dialog open between actions
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            lastFind = find.getText().toString();
            findNext(lastFind);
        });
        d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            lastFind = find.getText().toString();
            lastRepl = repl.getText().toString();
            replaceOne(lastFind, lastRepl);
        });
        d.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
            lastFind = find.getText().toString();
            lastRepl = repl.getText().toString();
            replaceAll(lastFind, lastRepl);
            d.dismiss();
        });
    }

    private String selectedText() {
        int a = editor.getSelectionStart(), b = editor.getSelectionEnd();
        if (a < 0 || b < 0 || a == b) return "";
        return editor.getText().subSequence(Math.min(a, b), Math.max(a, b)).toString();
    }

    private void findNext(String q) {
        if (q == null || q.length() == 0) return;
        String text = editor.getText().toString();
        int start = Math.max(editor.getSelectionStart(), editor.getSelectionEnd());
        int idx = text.indexOf(q, Math.max(0, start));
        boolean wrapped = false;
        if (idx < 0) { idx = text.indexOf(q, 0); wrapped = true; }
        if (idx < 0) { Ui.toast(this, "Not found"); return; }
        if (wrapped) Ui.toast(this, "Wrapped to top");
        editor.requestFocus();
        editor.setSelection(idx, idx + q.length());
    }

    private void replaceOne(String q, String r) {
        if (q.length() == 0) return;
        if (selectedText().equals(q)) {
            int a = Math.min(editor.getSelectionStart(), editor.getSelectionEnd());
            editor.getText().replace(a, a + q.length(), r);
            editor.setSelection(a + r.length());
        }
        findNext(q);
    }

    private void replaceAll(String q, String r) {
        if (q.length() == 0) return;
        String text = editor.getText().toString();
        int count = 0, from = 0;
        while ((from = text.indexOf(q, from)) >= 0) { count++; from += q.length(); }
        if (count == 0) { Ui.toast(this, "Not found"); return; }
        snapshot();
        int caret = Math.min(Math.max(0, editor.getSelectionStart()), text.length());
        editor.setText(text.replace(q, r));
        editor.setSelection(Math.min(caret, editor.getText().length()));
        Ui.toast(this, count + " replaced");
    }

    private void gotoDialog() {
        final EditText in = pathInput("Line number");
        in.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        Ui.dialog(this)
                .setTitle("Go to line")
                .setView(wrapInput(in))
                .setPositiveButton("Go", (d, w) -> {
                    try { gotoLine(Integer.parseInt(in.getText().toString().trim())); }
                    catch (Exception e) { Ui.toast(this, "Enter a line number"); }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void gotoLine(int line) {
        String text = editor.getText().toString();
        int off = 0, cur = 1;
        while (cur < line) {
            int nl = text.indexOf('\n', off);
            if (nl < 0) break;
            off = nl + 1;
            cur++;
        }
        editor.requestFocus();
        editor.setSelection(Math.min(off, text.length()));
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
        Ui.toast(this, wrapOn ? "Word wrap ON" : "Word wrap OFF");
        updateMeta();
    }

    private void snippetsDialog() {
        final String[][] snips = {
                {"public class", "public class NAME {\n    \n}\n"},
                {"main method", "public static void main(String[] args) {\n    \n}\n"},
                {"Log.d", "android.util.Log.d(\"Java2Dex\", \"msg\");\n"},
                {"Toast", "android.widget.Toast.makeText(ctx, \"msg\", android.widget.Toast.LENGTH_SHORT).show();\n"},
                {"for loop", "for (int i = 0; i < 10; i++) {\n    \n}\n"},
                {"for-each", "for (Object o : list) {\n    \n}\n"},
                {"if-else", "if (cond) {\n    \n} else {\n    \n}\n"},
                {"try-catch", "try {\n    \n} catch (Throwable e) {\n    \n}\n"},
                {"Runnable / UI thread", "new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {\n    @Override\n    public void run() {\n        \n    }\n});\n"},
                {"Reflection call", "java.lang.reflect.Method m = cls.getDeclaredMethod(\"name\");\nm.setAccessible(true);\nObject r = m.invoke(obj);\n"},
                {"Xposed hook", "XposedHelpers.findAndHookMethod(\"cls\", lpparam.classLoader,\n        \"method\", new XC_MethodHook() {\n    @Override\n    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {\n        \n    }\n});\n"}
        };
        String[] names = new String[snips.length];
        for (int i = 0; i < snips.length; i++) names[i] = snips[i][0];
        Ui.dialog(this)
                .setTitle("Insert snippet")
                .setItems(names, (d, w) -> insertAtCaret(snips[w][1], 0))
                .show();
    }

    // ================================================================ convert

    private void convert() {
        if (Converter.isRunning()) { Ui.toast(this, "A conversion is already running"); return; }
        if (!saveCurrent()) return;
        overlay.setVisibility(View.VISIBLE);
        Converter.convert(this, p, new Converter.Callback() {
            @Override public void onStep(String s) { overlayStatus.setText(s); }
            @Override public void onDone(boolean ok, String log) {
                overlay.setVisibility(View.GONE);
                if (!Ui.alive(IdeActivity.this)) return;
                Project fresh = Project.byId(IdeActivity.this, p.id);
                if (fresh != null) p = fresh;
                if (ok) showSuccess(); else showBuildFailed(log);
            }
        });
    }

    private void showSuccess() {
        List<File> dex = p.publicDexFiles(this);
        StringBuilder sb = new StringBuilder("Saved to:\n").append(p.publicDexDir(this).getAbsolutePath());
        sb.append("\n\n");
        for (File f : dex) sb.append(f.getName()).append("  •  ").append(Ui.size(f.length())).append('\n');
        Ui.dialog(this)
                .setTitle("✔ Converted")
                .setMessage(sb.toString().trim())
                .setPositiveButton("Smali", (d, w) -> {
                    Intent i = new Intent(this, DexViewerActivity.class);
                    i.putExtra("id", p.id);
                    startActivity(i);
                })
                .setNeutralButton("Share", (d, w) ->
                        Ui.shareFile(this, p.publicDexFile(this), p.safeName() + "_classes.dex"))
                .setNegativeButton("OK", null)
                .show();
    }

    private static class BuildError {
        String rel; int line; String msg;
    }

    private static final Pattern ERR = Pattern.compile("ERROR in (.+?\\.java) \\(at line (\\d+)\\)");

    private List<BuildError> parseErrors(String log) {
        List<BuildError> out = new ArrayList<>();
        String[] lines = log.split("\n");
        String base = p.srcDir(this).getAbsolutePath() + "/";
        for (int i = 0; i < lines.length; i++) {
            Matcher m = ERR.matcher(lines[i]);
            if (!m.find()) continue;
            BuildError e = new BuildError();
            String path = m.group(1);
            e.rel = path.startsWith(base) ? path.substring(base.length()) : null;
            try { e.line = Integer.parseInt(m.group(2)); } catch (Exception ex) { e.line = 1; }
            // ECJ prints: source line, caret line, then the message
            e.msg = (i + 3 < lines.length) ? lines[i + 3].trim() : "";
            if (e.msg.length() == 0 && i + 1 < lines.length) e.msg = lines[i + 1].trim();
            out.add(e);
        }
        return out;
    }

    private void showBuildFailed(final String log) {
        final List<BuildError> errs = parseErrors(log);
        if (errs.isEmpty()) {
            showRawLog(log);
            return;
        }
        String[] items = new String[errs.size()];
        for (int i = 0; i < errs.size(); i++) {
            BuildError e = errs.get(i);
            String name = e.rel == null ? "?" : e.rel.substring(e.rel.lastIndexOf('/') + 1);
            items[i] = name + ":" + e.line + "  —  " + e.msg;
        }
        Ui.dialog(this)
                .setTitle("✖ " + errs.size() + " error" + (errs.size() == 1 ? "" : "s") + " — tap to jump")
                .setItems(items, (d, w) -> {
                    BuildError e = errs.get(w);
                    if (e.rel != null && fileFor(e.rel).exists()) {
                        saveCurrent();
                        openFile(e.rel);
                        gotoLine(e.line);
                    }
                })
                .setPositiveButton("Full log", (d, w) -> showRawLog(log))
                .setNegativeButton("Close", null)
                .show();
    }

    private void showRawLog(final String log) {
        ScrollView sc = new ScrollView(this);
        TextView tv = Ui.text(this, log, 11.5f, t.danger, false);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextIsSelectable(true);
        int pd = Ui.dp(14);
        tv.setPadding(pd, pd, pd, pd);
        sc.addView(tv, new FrameLayout.LayoutParams(-1, -2));
        Ui.dialog(this)
                .setTitle("✖ Build Failed")
                .setView(sc)
                .setPositiveButton("Copy", (d, w) -> Ui.copy(this, "log", log))
                .setNegativeButton("Close", null)
                .show();
    }

    // ================================================================ highlighting

    private void scheduleHighlight() {
        if (hlRun != null) hl.removeCallbacks(hlRun);
        hlRun = new Runnable() {
            @Override public void run() { applyHighlights(); }
        };
        hl.postDelayed(hlRun, 450);
    }

    private void applyHighlights() {
        Editable e = editor.getText();
        if (e == null) return;
        int len = e.length();
        ForegroundColorSpan[] spans = e.getSpans(0, len, ForegroundColorSpan.class);
        for (ForegroundColorSpan s : spans) e.removeSpan(s);
        if (len == 0 || len > MAX_HIGHLIGHT) return;

        boolean dk = Prefs.dark(this);
        int kwColor = dk ? 0xFFC084FC : 0xFF7C3AED;
        int strColor = dk ? 0xFF34D399 : 0xFF059669;
        int comColor = dk ? 0xFF64748B : 0xFF94A3B8;
        int numColor = dk ? 0xFFFB923C : 0xFFEA580C;
        int annColor = dk ? 0xFFFBBF24 : 0xFFB45309;
        int typColor = dk ? 0xFF60A5FA : 0xFF2563EB;

        String text = e.toString();
        int i = 0;
        while (i < len) {
            char c = text.charAt(i);
            if (c == '/' && i + 1 < len && text.charAt(i + 1) == '/') {
                int end = text.indexOf('\n', i);
                if (end < 0) end = len;
                span(e, comColor, i, end);
                i = end;
            } else if (c == '/' && i + 1 < len && text.charAt(i + 1) == '*') {
                int end = text.indexOf("*/", i + 2);
                end = end < 0 ? len : end + 2;
                span(e, comColor, i, end);
                i = end;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < len) {
                    char d = text.charAt(j);
                    if (d == '\\') { j += 2; continue; }
                    if (d == c || d == '\n') { j++; break; }
                    j++;
                }
                span(e, strColor, i, Math.min(j, len));
                i = Math.min(j, len);
            } else if (c == '@' && i + 1 < len && Character.isJavaIdentifierStart(text.charAt(i + 1))) {
                int j = i + 1;
                while (j < len && Character.isJavaIdentifierPart(text.charAt(j))) j++;
                span(e, annColor, i, j);
                i = j;
            } else if (Character.isDigit(c)) {
                int j = i;
                while (j < len && (Character.isLetterOrDigit(text.charAt(j)) || text.charAt(j) == '.'
                        || text.charAt(j) == '_')) j++;
                span(e, numColor, i, j);
                i = j;
            } else if (Character.isJavaIdentifierStart(c)) {
                int j = i;
                while (j < len && Character.isJavaIdentifierPart(text.charAt(j))) j++;
                String word = text.substring(i, j);
                if (KEYWORDS.contains(word)) span(e, kwColor, i, j);
                else if (Character.isUpperCase(c) && j - i > 1 && !isAllCaps(word)) span(e, typColor, i, j);
                i = j;
            } else {
                i++;
            }
        }
    }

    private static boolean isAllCaps(String w) {
        for (int i = 0; i < w.length(); i++) {
            char c = w.charAt(i);
            if (Character.isLowerCase(c)) return false;
        }
        return true;   // CONSTANT_NAMES are not types
    }

    private static void span(Editable e, int color, int a, int b) {
        if (b > a) e.setSpan(new ForegroundColorSpan(color), a, b, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void updateMeta() {
        if (editor == null || metaLabel == null) return;
        int sel = editor.getSelectionEnd();
        Editable ed = editor.getText();
        if (sel < 0) sel = 0;
        if (sel > ed.length()) sel = ed.length();
        int lines = 1, lastNl = -1, total = 1;
        for (int i = 0, n = ed.length(); i < n; i++) {
            if (ed.charAt(i) == '\n') {
                total++;
                if (i < sel) { lines++; lastNl = i; }
            }
        }
        int col = sel - lastNl;
        metaLabel.setText("Ln " + lines + ", Col " + col + "  •  " + total + " lines  •  font "
                + Prefs.fontSize(this) + (wrapOn ? " • wrap" : "")
                + (currentRel != null ? "  •  " + currentRel : ""));
    }

    // ================================================================ helpers

    private FrameLayout wrapInput(EditText e) {
        FrameLayout f = new FrameLayout(this);
        int pad = Ui.dp(20);
        f.setPadding(pad, Ui.dp(8), pad, 0);
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
        snapshot();
        saveCurrent();
    }

    @Override
    protected void onDestroy() {
        hl.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ================================================================ adapter

    private class FilesAdapter extends BaseAdapter {
        @Override public int getCount() { return entries.size(); }
        @Override public Object getItem(int position) { return entries.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, android.view.ViewGroup parent) {
            LinearLayout card;
            TextView tv;
            if (convertView == null) {
                card = new LinearLayout(IdeActivity.this);
                card.setGravity(Gravity.CENTER_VERTICAL);
                tv = Ui.text(IdeActivity.this, "", 12.5f, t.text, false);
                tv.setSingleLine(true);
                tv.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                card.addView(tv, new LinearLayout.LayoutParams(-1, -2));
                card.setTag(tv);
            } else {
                card = (LinearLayout) convertView;
                tv = (TextView) card.getTag();
            }
            Object[] e = entries.get(position);
            boolean isDir = (Boolean) e[1];
            String rel = (String) e[0];
            String path = isDir ? rel.substring(0, rel.length() - 1) : rel;
            int depth = 0;
            for (int i = 0; i < path.length(); i++) if (path.charAt(i) == '/') depth++;
            String name = path.substring(path.lastIndexOf('/') + 1);

            int pad = Ui.dp(12);
            card.setPadding(pad + depth * Ui.dp(16), pad, pad, pad);
            tv.setText((isDir ? "📁 " : "📄 ") + name);
            tv.setTextColor(isDir ? t.accentDark : t.text);
            tv.setTypeface(isDir ? Typeface.create("sans-serif-medium", Typeface.BOLD) : Typeface.DEFAULT);

            boolean active = !isDir && rel.equals(currentRel);
            boolean chosenDir = isDir && path.equals(selectedDir);
            if (active || chosenDir) {
                card.setBackground(Ui.ripple(IdeActivity.this,
                        Ui.outline(t.accentSoft, t.accent, 12, 1.4f, IdeActivity.this)));
            } else {
                card.setBackground(Ui.ripple(IdeActivity.this,
                        Ui.outline(t.card, t.cardStroke, 12, 1, IdeActivity.this)));
            }
            return card;
        }
    }
}
