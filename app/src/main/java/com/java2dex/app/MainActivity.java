package com.java2dex.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQ_STORAGE = 77;

    private Theme t;
    private ListView list;
    private LinearLayout emptyBox, permOverlay;
    private TextView statTotal, statOk, statErr;
    private FrameLayout splash, splashLogoBox, fab;
    private final List<Project> projects = new ArrayList<>();
    private final List<Project> shown = new ArrayList<>();
    private Adapter adapter;
    private String query = "";
    private boolean statsAnimated = false;
    private boolean askedStorage = false;
    private boolean appliedDark;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appliedDark = Prefs.dark(this);
        t = Theme.get(this);
        getWindow().setStatusBarColor(Ui.GREEN_DEEP);
        getWindow().setNavigationBarColor(t.bg);

        extractAndroidJar();
        buildUi();
        refresh();
        runSplash();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (appliedDark != Prefs.dark(this)) { recreate(); return; }
        boolean ok = storageOk();
        if (ok) Project.java2dexRoot(this);
        if (permOverlay != null) {
            permOverlay.setVisibility((!ok && askedStorage) ? View.VISIBLE : View.GONE);
        }
        refresh();
    }

    // ---------------- storage permission ----------------

    private boolean storageOk() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void ensureStorage() {
        askedStorage = true;
        if (storageOk()) {
            Project.java2dexRoot(this);
            if (permOverlay != null) permOverlay.setVisibility(View.GONE);
            return;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(i, REQ_STORAGE);
            } catch (Exception e) {
                try {
                    startActivityForResult(new Intent(
                            Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), REQ_STORAGE);
                } catch (Exception ignored) {
                    Ui.toast(this, "Enable 'All files access' in Settings");
                }
            }
        } else {
            requestPermissions(new String[]{
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
        }
    }

    // ---------------- UI ----------------

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(t.bg);
        setContentView(root);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        // ---------- header ----------
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackground(Ui.gradient(Ui.GREEN_DEEP, Ui.GREEN, 0, this));
        header.setPadding(Ui.dp(18), Ui.dp(26), Ui.dp(18), Ui.dp(20));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        logo.setBackground(Ui.fill(0x33FFFFFF, 18, this));
        logo.setPadding(Ui.dp(8), Ui.dp(8), Ui.dp(8), Ui.dp(8));
        titleRow.addView(logo, new LinearLayout.LayoutParams(Ui.dp(46), Ui.dp(46)));

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.leftMargin = Ui.dp(12);
        titleCol.addView(Ui.text(this, "Java2Dex", 21, Color.WHITE, true));
        titleCol.addView(Ui.text(this, "v2.0 • Java → DEX power suite", 11.5f, 0xB3FFFFFF, false));
        titleRow.addView(titleCol, clp);

        titleRow.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        titleRow.addView(iconBtn("👨‍💻", v -> Dialogs.html(this, "👨‍💻 Developer", "developer.html")));
        titleRow.addView(iconBtn("ⓘ", v -> startActivity(new Intent(this, InfoActivity.class))));
        titleRow.addView(iconBtn("⚙", v -> startActivity(new Intent(this, SettingsActivity.class))));

        header.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout stats = new LinearLayout(this);
        LinearLayout.LayoutParams stl = new LinearLayout.LayoutParams(-1, -2);
        stl.topMargin = Ui.dp(18);
        statTotal = statCard(stats, "PROJECTS");
        statOk = statCard(stats, "SUCCESS");
        statErr = statCard(stats, "FAILED");
        header.addView(stats, stl);
        page.addView(header, new LinearLayout.LayoutParams(-1, -2));

        // ---------- quick actions ----------
        LinearLayout quickWrap = new LinearLayout(this);
        quickWrap.setOrientation(LinearLayout.VERTICAL);
        quickWrap.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), 0);
        page.addView(quickWrap, new LinearLayout.LayoutParams(-1, -2));

        quickWrap.addView(Ui.text(this, "QUICK ACTIONS", 10.5f, t.textSub, true));

        LinearLayout row1 = new LinearLayout(this);
        LinearLayout.LayoutParams r1p = new LinearLayout.LayoutParams(-1, -2);
        r1p.topMargin = Ui.dp(8);
        row1.addView(quickCard("➕", "New Project", "import or write code",
                v -> startActivity(new Intent(this, NewProjectActivity.class))), weight());
        LinearLayout.LayoutParams r1b = weight();
        r1b.leftMargin = Ui.dp(10);
        row1.addView(quickCard("🧠", "Code IDE", "write & edit java files",
                v -> openIdeQuick()), r1b);
        quickWrap.addView(row1, r1p);

        LinearLayout row2 = new LinearLayout(this);
        LinearLayout.LayoutParams r2p = new LinearLayout.LayoutParams(-1, -2);
        r2p.topMargin = Ui.dp(10);
        row2.addView(quickCard("🧪", "Sample", "load demo & convert",
                v -> createSampleAndOpen()), weight());
        LinearLayout.LayoutParams r2b = weight();
        r2b.leftMargin = Ui.dp(10);
        row2.addView(quickCard("📂", "Output", "see save location",
                v -> showOutputInfo()), r2b);
        quickWrap.addView(row2, r2p);

        // ---------- search ----------
        LinearLayout searchWrap = new LinearLayout(this);
        searchWrap.setOrientation(LinearLayout.VERTICAL);
        searchWrap.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), 0);
        page.addView(searchWrap, new LinearLayout.LayoutParams(-1, -2));
        EditText search = Ui.input(this, "🔍  Search projects…", t);
        searchWrap.addView(search, new LinearLayout.LayoutParams(-1, -2));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                query = s.toString();
                applyFilter();
            }
        });

        // ---------- list container (list + empty state live HERE) ----------
        FrameLayout listContainer = new FrameLayout(this);

        list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setClipToPadding(false);
        list.setPadding(Ui.dp(16), Ui.dp(12), Ui.dp(16), Ui.dp(110));
        list.setOnItemClickListener((parent, v, pos, id) -> openDetail(shown.get(pos)));
        list.setOnItemLongClickListener((parent, v, pos, id) -> {
            confirmDelete(shown.get(pos));
            return true;
        });
        listContainer.addView(list, new FrameLayout.LayoutParams(-1, -1));

        adapter = new Adapter();
        list.setAdapter(adapter);

        // empty state INSIDE the list area — never overlaps quick actions (Bug 1 fix)
        emptyBox = new LinearLayout(this);
        emptyBox.setOrientation(LinearLayout.VERTICAL);
        emptyBox.setGravity(Gravity.CENTER);

        FrameLayout eIcon = new FrameLayout(this);
        eIcon.setBackground(Ui.gradient(Ui.GREEN_LIGHT, Ui.GREEN_LIGHT, 30, this));
        TextView eEmoji = Ui.text(this, "📦", 30, Ui.GREEN_DARK, false);
        eEmoji.setGravity(Gravity.CENTER);
        eIcon.addView(eEmoji, new FrameLayout.LayoutParams(-1, -1));
        emptyBox.addView(eIcon, new LinearLayout.LayoutParams(Ui.dp(84), Ui.dp(84)));

        TextView e2 = Ui.text(this, "No projects yet", 16, t.text, true);
        e2.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams e2p = new LinearLayout.LayoutParams(-2, -2);
        e2p.topMargin = Ui.dp(14);
        emptyBox.addView(e2, e2p);
        TextView e3 = Ui.text(this,
                "Create a project or open the IDE\nto start converting", 12.5f, t.textSub, false);
        e3.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams e3p = new LinearLayout.LayoutParams(-2, -2);
        e3p.topMargin = Ui.dp(4);
        emptyBox.addView(e3, e3p);
        emptyBox.setVisibility(View.GONE);
        FrameLayout.LayoutParams emp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        listContainer.addView(emptyBox, emp);

        page.addView(listContainer, new LinearLayout.LayoutParams(-1, 0, 1f));

        // ---------- FAB ----------
        fab = new FrameLayout(this);
        fab.setBackground(Ui.ripple(this, Ui.gradient(Ui.GREEN, Ui.GREEN_DARK, 16, this)));
        fab.setElevation(Ui.dp(9));
        TextView plus = Ui.text(this, "+", 26, Color.WHITE, true);
        plus.setGravity(Gravity.CENTER);
        fab.addView(plus, new FrameLayout.LayoutParams(-1, -1));
        fab.setOnClickListener(v -> {
            startActivity(new Intent(this, NewProjectActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
        Ui.pressScale(fab, 0.92f);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                Ui.dp(56), Ui.dp(56), Gravity.BOTTOM | Gravity.END);
        flp.rightMargin = Ui.dp(20);
        flp.bottomMargin = Ui.dp(26);
        root.addView(fab, flp);

        // ---------- permission overlay ----------
        permOverlay = new LinearLayout(this);
        permOverlay.setOrientation(LinearLayout.VERTICAL);
        permOverlay.setGravity(Gravity.CENTER);
        permOverlay.setBackgroundColor(t.bg);
        permOverlay.setClickable(true);
        permOverlay.setVisibility(View.GONE);

        FrameLayout icon = new FrameLayout(this);
        icon.setBackground(Ui.gradient(Ui.GREEN, Ui.GREEN_DARK, 24, this));
        icon.setElevation(Ui.dp(6));
        TextView ic = Ui.text(this, "📁", 28, Color.WHITE, false);
        ic.setGravity(Gravity.CENTER);
        icon.addView(ic, new FrameLayout.LayoutParams(-1, -1));
        permOverlay.addView(icon, new LinearLayout.LayoutParams(Ui.dp(88), Ui.dp(88)));

        TextView pt = Ui.text(this, "Storage Permission", 19, t.text, true);
        pt.setGravity(Gravity.CENTER);
        pt.setPadding(0, Ui.dp(18), 0, 0);
        permOverlay.addView(pt, new LinearLayout.LayoutParams(-2, -2));

        TextView pd = Ui.text(this,
                "Java2Dex saves DEX files to:\n/storage/emulated/0/Java2Dex/\n\n"
                        + "Allow file access so output can be\ncreated and shared.",
                13, t.textSub, false);
        pd.setGravity(Gravity.CENTER);
        pd.setPadding(Ui.dp(32), Ui.dp(8), Ui.dp(32), 0);
        permOverlay.addView(pd, new LinearLayout.LayoutParams(-2, -2));

        TextView grant = Ui.button(this, "🔓  Grant Permission", Ui.GREEN);
        grant.setOnClickListener(v -> ensureStorage());
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-2, -2);
        glp.topMargin = Ui.dp(20);
        permOverlay.addView(grant, glp);
        root.addView(permOverlay, new FrameLayout.LayoutParams(-1, -1));

        // ---------- splash ----------
        splash = new FrameLayout(this);
        splash.setBackgroundColor(t.bg);
        splash.setClickable(true);

        LinearLayout sc = new LinearLayout(this);
        sc.setOrientation(LinearLayout.VERTICAL);
        sc.setGravity(Gravity.CENTER);

        splashLogoBox = new FrameLayout(this);
        splashLogoBox.setBackground(Ui.gradient(Ui.GREEN, Ui.GREEN_DARK, 26, this));
        splashLogoBox.setElevation(Ui.dp(10));
        ImageView slogo = new ImageView(this);
        slogo.setImageResource(R.drawable.logo);
        slogo.setPadding(Ui.dp(14), Ui.dp(14), Ui.dp(14), Ui.dp(14));
        splashLogoBox.addView(slogo, new FrameLayout.LayoutParams(-1, -1));
        sc.addView(splashLogoBox, new LinearLayout.LayoutParams(Ui.dp(96), Ui.dp(96)));

        TextView n1 = Ui.text(this, "Java2Dex", 25, t.text, true);
        n1.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams n1p = new LinearLayout.LayoutParams(-2, -2);
        n1p.topMargin = Ui.dp(18);
        sc.addView(n1, n1p);
        TextView n2 = Ui.text(this, "Compile • Convert • Smali • Mod", 12.5f, t.textSub, false);
        n2.setGravity(Gravity.CENTER);
        sc.addView(n2);
        splash.addView(sc, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        TextView foot = Ui.text(this, "v2.0 • made for modders", 11, t.textSub, false);
        foot.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(
                -2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        fp.bottomMargin = Ui.dp(40);
        splash.addView(foot, fp);
        root.addView(splash, new FrameLayout.LayoutParams(-1, -1));
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, -2, 1f);
    }

    private TextView iconBtn(String glyph, View.OnClickListener l) {
        TextView b = Ui.text(this, glyph, 15, Color.WHITE, false);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.ripple(this, Ui.fill(0x30FFFFFF, 12, this)));
        b.setPadding(Ui.dp(9), Ui.dp(7), Ui.dp(9), Ui.dp(7));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = Ui.dp(8);
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        Ui.pressScale(b, 0.9f);
        return b;
    }

    private TextView statCard(LinearLayout parent, String label) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(Ui.fill(0x26FFFFFF, 14, this));
        int pad = Ui.dp(10);
        card.setPadding(pad, pad, pad, pad);
        TextView num = Ui.text(this, "0", 20, Color.WHITE, true);
        num.setGravity(Gravity.CENTER);
        card.addView(num);
        TextView lb = Ui.text(this, label, 9, 0xB3FFFFFF, true);
        lb.setGravity(Gravity.CENTER);
        card.addView(lb);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        if (parent.getChildCount() > 0) lp.leftMargin = Ui.dp(10);
        parent.addView(card, lp);
        return num;
    }

    private LinearLayout quickCard(String emoji, String title, String sub, View.OnClickListener l) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(Ui.ripple(this, Ui.outline(t.card, t.cardStroke, 16, 1, this)));
        card.setElevation(Ui.dp(2));
        int pad = Ui.dp(12);
        card.setPadding(pad, pad + Ui.dp(4), pad, pad + Ui.dp(4));

        TextView em = Ui.text(this, emoji, 22, t.text, false);
        em.setGravity(Gravity.CENTER);
        card.addView(em);

        TextView tt = Ui.text(this, title, 13.5f, t.text, true);
        tt.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.topMargin = Ui.dp(6);
        card.addView(tt, tp);

        TextView ss = Ui.text(this, sub, 10.5f, t.textSub, false);
        ss.setGravity(Gravity.CENTER);
        card.addView(ss);

        card.setOnClickListener(l);
        Ui.pressScale(card, 0.96f);
        return card;
    }

    private void openIdeQuick() {
        Project target = projects.isEmpty() ? null : projects.get(0);
        if (target == null) {
            target = new Project();
            target.id = String.valueOf(System.currentTimeMillis());
            target.name = "Untitled";
            target.createdAt = System.currentTimeMillis();
            target.srcDir(this).mkdirs();
            writeFile(new File(target.srcDir(this), "Main.java"),
                    "public class Main {\n    public static void main(String[] args) {\n        \n    }\n}\n");
            Project.upsert(this, target);
        }
        openIde(target);
    }

    private void createSampleAndOpen() {
        Project p = new Project();
        p.id = String.valueOf(System.currentTimeMillis());
        p.name = "SampleMod_" + p.id.substring(p.id.length() - 4);
        p.createdAt = System.currentTimeMillis();
        p.srcDir(this).mkdirs();
        writeFile(new File(p.srcDir(this), "HelloMod.java"),
                "public class HelloMod {\n"
                        + "    public static String TAG = \"Java2Dex\";\n\n"
                        + "    public static String hello(String who) {\n"
                        + "        return \"Hello \" + who + \" from \" + TAG + \"!\";\n"
                        + "    }\n\n"
                        + "    public static int add(int a, int b) {\n"
                        + "        return a + b;\n"
                        + "    }\n}\n");
        Project.upsert(this, p);
        openIde(p);
        Ui.toast(this, "Sample project created ✔");
    }

    private static void writeFile(File f, String content) {
        try {
            f.getParentFile().mkdirs();
            FileOutputStream w = new FileOutputStream(f);
            w.write(content.getBytes());
            w.close();
        } catch (Exception ignored) { }
    }

    private void openIde(Project p) {
        Intent i = new Intent(this, IdeActivity.class);
        i.putExtra("id", p.id);
        startActivity(i);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void showOutputInfo() {
        new AlertDialog.Builder(this)
                .setTitle("Output folder")
                .setMessage(Project.java2dexRoot(this).getAbsolutePath()
                        + "\n\nEach project saves as:\n<project-name>/classes.dex")
                .setPositiveButton("OK", null)
                .show();
    }

    private void runSplash() {
        splashLogoBox.setScaleX(0.4f);
        splashLogoBox.setScaleY(0.4f);
        splashLogoBox.setAlpha(0f);
        splashLogoBox.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(600)
                .setInterpolator(new OvershootInterpolator(1.25f)).start();

        splash.postDelayed(() -> splash.animate().alpha(0f).setDuration(400)
                .setListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator a) {
                        splash.setVisibility(View.GONE);
                        Ui.popIn(fab, 60);
                        ensureStorage();
                    }
                }).start(), 1300);
    }

    private void extractAndroidJar() {
        File target = new File(getFilesDir(), "sys/android.jar");
        if (target.exists()) return;
        new Thread(() -> {
            try {
                InputStream in = getAssets().open("android.jar");
                target.getParentFile().mkdirs();
                OutputStream out = new FileOutputStream(target);
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.flush();
                out.close();
                in.close();
            } catch (Exception ignored) { }
        }, "jar-extract").start();
    }

    // ---------------- data ----------------

    private void refresh() {
        projects.clear();
        projects.addAll(Project.all(this));
        applyFilter();

        int ok = 0, err = 0;
        for (Project p : projects) {
            if (p.status == Project.ST_OK) ok++;
            else if (p.status == Project.ST_ERROR) err++;
        }
        if (!statsAnimated && !projects.isEmpty()) {
            statsAnimated = true;
            Ui.countUp(statTotal, projects.size());
            Ui.countUp(statOk, ok);
            Ui.countUp(statErr, err);
        } else {
            statTotal.setText(String.valueOf(projects.size()));
            statOk.setText(String.valueOf(ok));
            statErr.setText(String.valueOf(err));
        }
    }

    private void applyFilter() {
        shown.clear();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.US);
        for (Project p : projects) {
            if (q.isEmpty() || p.name.toLowerCase(Locale.US).contains(q)) shown.add(p);
        }
        if (adapter != null) adapter.notifyDataSetChanged();
        if (emptyBox != null) emptyBox.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void openDetail(Project p) {
        Intent i = new Intent(this, DetailActivity.class);
        i.putExtra("id", p.id);
        startActivity(i);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void confirmDelete(Project p) {
        new AlertDialog.Builder(this)
                .setTitle("Delete project?")
                .setMessage("\"" + p.name + "\" and its DEX output will be removed.")
                .setPositiveButton("Delete", (d, w) -> {
                    Project.delete(this, p.id);
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------------- list adapter (redesigned — Bug 2 fix) ----------------

    private class Adapter extends BaseAdapter {

        private class Holder {
            TextView icon, name, chip, sub;
        }

        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int position) { return shown.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Holder h;
            View v = convertView;
            if (v == null) {
                h = new Holder();
                v = makeItemView(h);
                v.setTag(h);
                Ui.riseIn(v, Math.min(position * 55, 400));
            } else {
                h = (Holder) v.getTag();
            }
            Project p = shown.get(position);
            h.icon.setText(p.name.length() > 0
                    ? p.name.substring(0, 1).toUpperCase(Locale.US) : "?");

            String chipTxt;
            int chipFg, chipBg;
            String sub;
            if (p.status == Project.ST_OK) {
                chipTxt = "✔ SUCCESS";
                chipFg = t.accentDark;
                chipBg = t.accentSoft;
                sub = p.dateText() + "  •  " + Ui.size(p.dexSize);
            } else if (p.status == Project.ST_ERROR) {
                chipTxt = "✖ FAILED";
                chipFg = t.danger;
                chipBg = t.dangerSoft;
                sub = p.dateText() + "  •  build failed";
            } else {
                chipTxt = "PENDING";
                chipFg = t.textSub;
                chipBg = t.chipBg;
                sub = "created " + p.createdText() + "  •  not built";
            }
            h.chip.setText(chipTxt);
            h.chip.setTextColor(chipFg);
            h.chip.setBackground(Ui.fill(chipBg, 20, MainActivity.this));
            h.sub.setText(sub);
            return v;
        }

        private View makeItemView(Holder h) {
            // outer wrapper provides spacing between cards
            LinearLayout outer = new LinearLayout(MainActivity.this);
            outer.setOrientation(LinearLayout.VERTICAL);
            outer.setPadding(0, 0, 0, Ui.dp(10));

            LinearLayout card = new LinearLayout(MainActivity.this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackground(Ui.ripple(MainActivity.this,
                    Ui.outline(t.card, t.cardStroke, 18, 1, MainActivity.this)));
            card.setElevation(Ui.dp(3));
            int pad = Ui.dp(14);
            card.setPadding(pad, pad, pad, pad);

            // letter icon
            FrameLayout iconBox = new FrameLayout(MainActivity.this);
            iconBox.setBackground(Ui.gradient(Ui.GREEN, Ui.GREEN_DARK, 14, MainActivity.this));
            h.icon = Ui.text(MainActivity.this, "", 17, Color.WHITE, true);
            h.icon.setGravity(Gravity.CENTER);
            iconBox.addView(h.icon, new FrameLayout.LayoutParams(-1, -1));
            card.addView(iconBox, new LinearLayout.LayoutParams(Ui.dp(46), Ui.dp(46)));

            // texts
            LinearLayout col = new LinearLayout(MainActivity.this);
            col.setOrientation(LinearLayout.VERTICAL);
            h.name = Ui.text(MainActivity.this, "", 15.5f, t.text, true);
            h.name.setSingleLine(true);
            col.addView(h.name);
            h.sub = Ui.text(MainActivity.this, "", 11.5f, t.textSub, false);
            h.sub.setSingleLine(true);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
            sp.topMargin = Ui.dp(3);
            col.addView(h.sub, sp);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -2, 1f);
            clp.leftMargin = Ui.dp(12);
            card.addView(col, clp);

            // chip + chevron
            LinearLayout right = new LinearLayout(MainActivity.this);
            right.setGravity(Gravity.CENTER_VERTICAL);
            h.chip = Ui.text(MainActivity.this, "", 10f, t.accentDark, true);
            h.chip.setBackground(Ui.fill(t.accentSoft, 20, MainActivity.this));
            h.chip.setPadding(Ui.dp(10), Ui.dp(4), Ui.dp(10), Ui.dp(4));
            right.addView(h.chip, new LinearLayout.LayoutParams(-2, -2));
            TextView chev = Ui.text(MainActivity.this, " ›", 17, t.textSub, true);
            right.addView(chev, new LinearLayout.LayoutParams(-2, -2));
            card.addView(right, new LinearLayout.LayoutParams(-2, -2));

            outer.addView(card, new LinearLayout.LayoutParams(-1, -2));
            return outer;
        }
    }
}
