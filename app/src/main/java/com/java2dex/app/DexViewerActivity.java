package com.java2dex.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

/** DEX Explorer — browse classes, view real smali, strings, export. Crash-proof. */
public class DexViewerActivity extends Activity {

    private Theme t;
    private Project p;
    private SmaliGen.DexFile dex;
    private List<String> classes = new ArrayList<>();
    private List<String> strings = new ArrayList<>();
    private List<String> shown = new ArrayList<>();
    private boolean showStrings = false;

    private ListView listView;
    private TextView infoText;
    private EditText search;
    private LinearLayout loadingBox;
    private TextView tab1, tab2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        t = Theme.get(this);
        getWindow().setStatusBarColor(Ui.GREEN_DEEP);
        String id = getIntent() != null ? getIntent().getStringExtra("id") : null;
        p = id != null ? Project.byId(this, id) : null;
        if (p == null) { finish(); return; }
        buildUi();
        load();
    }

    private LinearLayout.LayoutParams rowWeight() {
        return new LinearLayout.LayoutParams(0, -2, 1f);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(t.bg);
        setContentView(root);

        // ---- header ----
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackground(Ui.gradient(Ui.GREEN_DEEP, Ui.GREEN, 0, this));
        header.setPadding(Ui.dp(16), Ui.dp(24), Ui.dp(16), Ui.dp(16));

        LinearLayout hr = new LinearLayout(this);
        hr.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Ui.text(this, "←", 20, Color.WHITE, true);
        back.setBackground(Ui.ripple(this, Ui.fill(0x33FFFFFF, 12, this)));
        back.setPadding(Ui.dp(12), Ui.dp(6), Ui.dp(12), Ui.dp(6));
        back.setOnClickListener(v -> finish());
        hr.addView(back, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout hc = new LinearLayout(this);
        hc.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams hclp = new LinearLayout.LayoutParams(0, -2, 1f);
        hclp.leftMargin = Ui.dp(10);
        hc.addView(Ui.text(this, "DEX Explorer", 17, Color.WHITE, true));
        hc.addView(Ui.text(this, p.name, 11, 0xB3FFFFFF, false));
        hr.addView(hc, hclp);
        header.addView(hr, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView saveAll = miniBtn("💾 Save All Smali");
        saveAll.setOnClickListener(v -> saveAllSmali());
        btnRow.addView(saveAll, new LinearLayout.LayoutParams(-2, -2));
        TextView reload = miniBtn("↺ Reload");
        reload.setOnClickListener(v -> {
            loadingBox.setVisibility(View.VISIBLE);
            load();
        });
        btnRow.addView(reload, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams brp = new LinearLayout.LayoutParams(-1, -2);
        brp.topMargin = Ui.dp(12);
        header.addView(btnRow, brp);

        infoText = Ui.text(this, "loading…", 11, 0xB3FFFFFF, false);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-1, -2);
        ilp.topMargin = Ui.dp(8);
        header.addView(infoText, ilp);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        // ---- body ----
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, 0, 1f);
        blp.topMargin = Ui.dp(10);
        root.addView(body, blp);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setPadding(Ui.dp(16), 0, Ui.dp(16), Ui.dp(8));
        tab1 = Ui.button(this, "🏫 Classes", Ui.GREEN);
        tab1.setOnClickListener(v -> { showStrings = false; applyTabs(); filter(); });
        tabs.addView(tab1, rowWeight());
        tab2 = Ui.button(this, "🔤 Strings", t.textSub);
        LinearLayout.LayoutParams t2p = rowWeight();
        t2p.leftMargin = Ui.dp(10);
        tab2.setLayoutParams(t2p);
        tabs.addView(tab2);
        tab2.setOnClickListener(v -> { showStrings = true; applyTabs(); filter(); });
        body.addView(tabs, new LinearLayout.LayoutParams(-1, -2));

        search = Ui.input(this, "🔍 Filter…", t);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.setMargins(Ui.dp(16), 0, Ui.dp(16), Ui.dp(8));
        search.setLayoutParams(sp);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { filter(); }
        });
        body.addView(search);

        listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(Ui.dp(8));
        listView.setPadding(Ui.dp(16), 0, Ui.dp(16), Ui.dp(20));
        listView.setClipToPadding(false);
        listView.setAdapter(new ListAdapter());
        listView.setOnItemClickListener((parent, v, pos, id2) -> onItemTap(shown.get(pos)));
        body.addView(listView, new LinearLayout.LayoutParams(-1, 0, 1f));

        // ---- loading overlay ----
        loadingBox = new LinearLayout(this);
        loadingBox.setOrientation(LinearLayout.VERTICAL);
        loadingBox.setGravity(Gravity.CENTER);
        loadingBox.setBackgroundColor(t.bg);
        ProgressBar pb = new ProgressBar(this);
        pb.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));
        loadingBox.addView(pb, new LinearLayout.LayoutParams(-2, -2));
        loadingBox.addView(Ui.text(this, "Parsing DEX…", 13, t.text, true));
        root.addView(loadingBox, new FrameLayout.LayoutParams(-1, -1));
    }

    private TextView miniBtn(String label) {
        TextView b = Ui.text(this, label, 12, Color.WHITE, true);
        b.setBackground(Ui.ripple(this, Ui.fill(0x40FFFFFF, 10, this)));
        b.setPadding(Ui.dp(12), Ui.dp(8), Ui.dp(12), Ui.dp(8));
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) b.getLayoutParams();
        lp.rightMargin = Ui.dp(8);
        return b;
    }

    private void applyTabs() {
        tab1.setBackground(Ui.ripple(this,
                Ui.fill(showStrings ? t.textSub : Ui.GREEN, 14, this)));
        tab2.setBackground(Ui.ripple(this,
                Ui.fill(showStrings ? Ui.GREEN : t.textSub, 14, this)));
    }

    private void load() {
        new Thread(() -> {
            try {
                File f = p.publicDexFile(this);
                if (!f.exists()) f = p.internalDexFile(this);
                final File dexFile = f;
                if (dexFile == null || !dexFile.exists()) {
                    throw new Exception("classes.dex not found — convert the project first");
                }
                SmaliGen.DexFile parsed = SmaliGen.open(dexFile);
                List<String> cl = SmaliGen.classNames(parsed);
                List<String> st = new ArrayList<>(SmaliGen.stringList(parsed));
                runOnUiThread(() -> {
                    dex = parsed;
                    classes = cl;
                    strings = st;
                    loadingBox.setVisibility(View.GONE);
                    infoText.setText("DEX v" + dex.version + "  •  " + Ui.size(dexFile.length())
                            + "  •  " + classes.size() + " classes  •  "
                            + strings.size() + " strings");
                    applyTabs();
                    filter();
                });
            } catch (Throwable e) {
                runOnUiThread(() -> {
                    loadingBox.setVisibility(View.GONE);
                    new AlertDialog.Builder(this)
                            .setTitle("No DEX found")
                            .setMessage("Convert the project first, then open the DEX Explorer.\n\n"
                                    + e.getMessage())
                            .setPositiveButton("OK", (d, w) -> finish())
                            .show();
                });
            }
        }, "dex-load").start();
    }

    private void filter() {
        shown.clear();
        String q = search.getText().toString().trim().toLowerCase();
        List<String> src = showStrings ? strings : classes;
        for (String s : src) {
            if (q.isEmpty() || s.toLowerCase().contains(q)) shown.add(s);
        }
        ((BaseAdapter) listView.getAdapter()).notifyDataSetChanged();
    }

    private void onItemTap(String item) {
        if (showStrings) {
            Ui.copy(this, "string", item);
        } else {
            classDialog(item);
        }
    }

    /** class options: view / copy / save / share smali */
    private void classDialog(final String name) {
        new AlertDialog.Builder(this)
                .setTitle(name)
                .setItems(new String[]{"📜 View Smali", "📋 Copy Smali",
                        "💾 Save .smali", "↗ Share Smali"},
                        (d, w) -> {
                            if (w == 0) showSmali(name);
                            else if (w == 1) smaliBg(name, (code) ->
                                    Ui.copy(this, "smali", code));
                            else if (w == 2) smaliBg(name, (code) -> saveOne(name, code));
                            else smaliBg(name, (code) ->
                                    Ui.shareText(this, name + ".smali", code));
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private interface SmaliReady { void ready(String code); }

    /** generates smali on background thread — catches Throwable, never crashes */
    private void smaliBg(final String name, final SmaliReady action) {
        new Thread(() -> {
            String code;
            try {
                SmaliGen.DexClass c = SmaliGen.findClass(dex, name);
                code = (c == null) ? "# class not found" : SmaliGen.renderClass(dex, c);
            } catch (Throwable e) {
                code = "# Failed to generate smali: " + e.getMessage();
            }
            final String out = code;
            runOnUiThread(() -> action.ready(out));
        }, "smali-gen").start();
    }

    private void showSmali(final String name) {
        smaliBg(name, code -> {
            ScrollView sc = new ScrollView(this);
            TextView tv = Ui.text(this, code, 11, t.text, false);
            tv.setTypeface(Typeface.MONOSPACE);
            tv.setTextIsSelectable(true);
            int pd = Ui.dp(14);
            tv.setPadding(pd, pd, pd, pd);
            sc.addView(tv, new FrameLayout.LayoutParams(-1, -2));
            new AlertDialog.Builder(this)
                    .setTitle(name)
                    .setView(sc)
                    .setPositiveButton("📋 Copy", (d, w) -> Ui.copy(this, "smali", code))
                    .setNeutralButton("↗ Share", (d, w) ->
                            Ui.shareText(this, name + ".smali", code))
                    .setNegativeButton("Close", null)
                    .show();
        });
    }

    private void saveOne(String name, String code) {
        try {
            File dir = p.smaliDir(this);
            File out = new File(dir, name.replace('.', '/') + ".smali");
            out.getParentFile().mkdirs();
            FileWriter w = new FileWriter(out);
            w.write(code);
            w.close();
            Ui.toast(this, "Saved: " + out.getAbsolutePath());
        } catch (Throwable e) {
            Ui.toast(this, "Save failed: " + e.getMessage());
        }
    }

    private void saveAllSmali() {
        if (dex == null) { Ui.toast(this, "Load a dex first"); return; }
        new Thread(() -> {
            int count = 0;
            try {
                File dir = p.smaliDir(this);
                dir.mkdirs();
                for (String name : classes) {
                    try {
                        SmaliGen.DexClass c = SmaliGen.findClass(dex, name);
                        if (c == null) continue;
                        File out = new File(dir, name.replace('.', '/') + ".smali");
                        out.getParentFile().mkdirs();
                        FileWriter w = new FileWriter(out);
                        w.write(SmaliGen.renderClass(dex, c));
                        w.close();
                        count++;
                    } catch (Throwable ignored) { }
                }
                final int n = count;
                runOnUiThread(() -> Ui.toast(this,
                        n + " smali files saved to\n" + p.smaliDir(this).getAbsolutePath()));
            } catch (Throwable e) {
                runOnUiThread(() -> Ui.toast(this, "Save failed: " + e.getMessage()));
            }
        }, "smali-save").start();
    }

    private class ListAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int position) { return shown.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, android.view.ViewGroup parent) {
            LinearLayout card = new LinearLayout(DexViewerActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(Ui.ripple(DexViewerActivity.this,
                    Ui.outline(t.card, t.cardStroke, 14, 1, DexViewerActivity.this)));
            card.setElevation(Ui.dp(2));
            int pd = Ui.dp(12);
            card.setPadding(pd, pd, pd, pd);
            String s = shown.get(position);
            TextView tv = Ui.text(DexViewerActivity.this,
                    (showStrings ? "🔤 " : "🏫 ") + s, 12.5f,
                    showStrings ? t.textSub : t.text, !showStrings);
            tv.setTypeface(showStrings ? Typeface.MONOSPACE : Typeface.DEFAULT);
            tv.setSingleLine(true);
            card.addView(tv);
            return card;
        }
    }
}
