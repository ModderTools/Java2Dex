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

    private ListView list;
    private LinearLayout emptyBox;
    private LinearLayout permOverlay;
    private TextView statTotal, statOk, statErr;
    private FrameLayout splash, splashLogo, fab;
    private final List<Project> projects = new ArrayList<>();
    private final List<Project> shown = new ArrayList<>();
    private Adapter adapter;
    private String query = "";
    private boolean statsAnimated = false;
    private boolean askedStorage = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Ui.GREEN_DEEP);
        getWindow().setNavigationBarColor(Color.WHITE);

        extractAndroidJar();
        buildUi();
        refresh();
        runSplash();
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean ok = storageOk();
        if (ok) Project.java2dexRoot(this);
        if (permOverlay != null) {
            permOverlay.setVisibility((!ok && askedStorage) ? View.VISIBLE : View.GONE);
        }
        refresh();
    }

    // ---------------- storage permission ----------------

    private boolean storageOk() {
        if (Build.VERSION.SDK_INT >= 30) {
            return Environment.isExternalStorageManager();
        }
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

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        setContentView(root);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        // ---------- header ----------
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackground(Ui.gradient(Ui.GREEN_DEEP, Ui.GREEN, 0, this));
        header.setPadding(Ui.dp(this, 20), Ui.dp(this, 30), Ui.dp(this, 20), Ui.dp(this, 22));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView logo = Ui.text(this, "J2D", 15, Color.WHITE, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(Ui.fill(0x33FFFFFF, 16, this));
        logo.setPadding(Ui.dp(this, 11), Ui.dp(this, 8), Ui.dp(this, 11), Ui.dp(this, 8));
        titleRow.addView(logo, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.leftMargin = Ui.dp(this, 12);
        titleCol.addView(Ui.text(this, "Java2Dex", 21, Color.WHITE, true));
        titleCol.addView(Ui.text(this, "Java → DEX converter for modders", 12, 0xB3FFFFFF, false));
        titleRow.addView(titleCol, clp);

        titleRow.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        TextView aboutBtn = Ui.text(this, "ⓘ", 18, Color.WHITE, true);
        aboutBtn.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        aboutBtn.setOnClickListener(v -> showAbout());
        titleRow.addView(aboutBtn, new LinearLayout.LayoutParams(-2, -2));

        header.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout stats = new LinearLayout(this);
        LinearLayout.LayoutParams stl = new LinearLayout.LayoutParams(-1, -2);
        stl.topMargin = Ui.dp(this, 18);
        statTotal = statCard(stats, "PROJECTS");
        statOk = statCard(stats, "SUCCESS");
        statErr = statCard(stats, "FAILED");
        header.addView(stats, stl);

        page.addView(header, new LinearLayout.LayoutParams(-1, -2));

        // ---------- search ----------
        LinearLayout searchWrap = new LinearLayout(this);
        searchWrap.setOrientation(LinearLayout.VERTICAL);
        searchWrap.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), 0);
        page.addView(searchWrap, new LinearLayout.LayoutParams(-1, -2));

        EditText search = Ui.input(this, "🔍  Search projects…");
        searchWrap.addView(search, new LinearLayout.LayoutParams(-1, -2));
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString();
                applyFilter();
            }
        });

        // ---------- list ----------
        list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setClipToPadding(false);
        list.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 100));
        list.setOnItemClickListener((parent, v, pos, id) -> openDetail(shown.get(pos)));
        list.setOnItemLongClickListener((parent, v, pos, id) -> {
            confirmDelete(shown.get(pos));
            return true;
        });
        page.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));

        adapter = new Adapter();
        list.setAdapter(adapter);

        // ---------- empty state ----------
        emptyBox = new LinearLayout(this);
        emptyBox.setOrientation(LinearLayout.VERTICAL);
        emptyBox.setGravity(Gravity.CENTER);
        TextView e1 = Ui.text(this, "📦", 46, Ui.TEXT, false);
        e1.setGravity(Gravity.CENTER);
        emptyBox.addView(e1);
        TextView e2 = Ui.text(this, "No projects yet", 17, Ui.TEXT, true);
        e2.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams e2p = new LinearLayout.LayoutParams(-2, -2);
        e2p.topMargin = Ui.dp(this, 10);
        emptyBox.addView(e2, e2p);
        TextView e3 = Ui.text(this, "Tap the + button to create your first\nJava → DEX project", 13, Ui.TEXT_SUB, false);
        e3.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams e3p = new LinearLayout.LayoutParams(-2, -2);
        e3p.topMargin = Ui.dp(this, 4);
        emptyBox.addView(e3, e3p);
        emptyBox.setVisibility(View.GONE);
        FrameLayout.LayoutParams emp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        emp.bottomMargin = Ui.dp(this, 60);
        root.addView(emptyBox, emp);

        // ---------- FAB ----------
        fab = new FrameLayout(this);
        fab.setBackground(Ui.ripple(this, Ui.gradient(Ui.GREEN, Ui.GREEN_DARK, 30, this)));
        fab.setElevation(Ui.dp(this, 6));
        TextView plus = Ui.text(this, "+", 26, Color.WHITE, true);
        plus.setGravity(Gravity.CENTER);
        fab.addView(plus, new FrameLayout.LayoutParams(-1, -1));
        fab.setOnClickListener(v -> {
            startActivity(new Intent(this, NewProjectActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
        Ui.pressScale(fab, 0.9f);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                Ui.dp(this, 58), Ui.dp(this, 58), Gravity.BOTTOM | Gravity.END);
        flp.rightMargin = Ui.dp(this, 20);
        flp.bottomMargin = Ui.dp(this, 24);
        root.addView(fab, flp);

        // ---------- permission overlay ----------
        permOverlay = new LinearLayout(this);
        permOverlay.setOrientation(LinearLayout.VERTICAL);
        permOverlay.setGravity(Gravity.CENTER);
        permOverlay.setBackgroundColor(Color.WHITE);
        permOverlay.setClickable(true);
        permOverlay.setVisibility(View.GONE);

        FrameLayout icon = new FrameLayout(this);
        icon.setBackground(Ui.gradient(Ui.GREEN, Ui.GREEN_DARK, 34, this));
        icon.setElevation(Ui.dp(this, 6));
        TextView ic = Ui.text(this, "📁", 30, Color.WHITE, false);
        ic.setGravity(Gravity.CENTER);
        icon.addView(ic, new FrameLayout.LayoutParams(-1, -1));
        permOverlay.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 92), Ui.dp(this, 92)));

        TextView pt = Ui.text(this, "Storage Permission", 20, Ui.TEXT, true);
        pt.setGravity(Gravity.CENTER);
        pt.setPadding(0, Ui.dp(this, 18), 0, 0);
        permOverlay.addView(pt, new LinearLayout.LayoutParams(-2, -2));

        TextView pd = Ui.text(this,
                "Java2Dex saves your DEX files to:\n/storage/emulated/0/Java2Dex/\n\n"
                        + "Please allow file access so the output\nfolder can be created and shared.",
                13, Ui.TEXT_SUB, false);
        pd.setGravity(Gravity.CENTER);
        pd.setPadding(Ui.dp(this, 32), Ui.dp(this, 8), Ui.dp(this, 32), 0);
        permOverlay.addView(pd, new LinearLayout.LayoutParams(-2, -2));

        TextView grant = Ui.button(this, "🔓  Grant Permission", Ui.GREEN);
        grant.setOnClickListener(v -> ensureStorage());
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-2, -2);
        glp.topMargin = Ui.dp(this, 22);
        permOverlay.addView(grant, glp);

        root.addView(permOverlay, new FrameLayout.LayoutParams(-1, -1));

        // ---------- splash ----------
        splash = new FrameLayout(this);
        splash.setBackgroundColor(Color.WHITE);
        splash.setClickable(true);

        LinearLayout sc = new LinearLayout(this);
        sc.setOrientation(LinearLayout.VERTICAL);
        sc.setGravity(Gravity.CENTER);

        splashLogo = new FrameLayout(this);
        splashLogo.setBackground(Ui.gradient(Ui.GREEN, Ui.GREEN_DARK, 30, this));
        splashLogo.setElevation(Ui.dp(this, 10));
        TextView logoTxt = Ui.text(this, "J2D", 26, Color.WHITE, true);
        logoTxt.setGravity(Gravity.CENTER);
        splashLogo.addView(logoTxt, new FrameLayout.LayoutParams(-1, -1));
        sc.addView(splashLogo, new LinearLayout.LayoutParams(Ui.dp(this, 96), Ui.dp(this, 96)));

        TextView n1 = Ui.text(this, "Java2Dex", 26, Ui.TEXT, true);
        n1.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams n1p = new LinearLayout.LayoutParams(-2, -2);
        n1p.topMargin = Ui.dp(this, 18);
        sc.addView(n1, n1p);
        TextView n2 = Ui.text(this, "Convert • Save • Mod", 13, Ui.TEXT_SUB, false);
        n2.setGravity(Gravity.CENTER);
        sc.addView(n2);
        splash.addView(sc, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        TextView foot = Ui.text(this, "v1.0 • made for modders", 11, 0xFFCBD5E1, false);
        foot.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(
                -2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        fp.bottomMargin = Ui.dp(this, 40);
        splash.addView(foot, fp);

        root.addView(splash, new FrameLayout.LayoutParams(-1, -1));
    }

    private TextView statCard(LinearLayout parent, String label) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(Ui.fill(0x26FFFFFF, 14, this));
        int pad = Ui.dp(this, 10);
        card.setPadding(pad, pad, pad, pad);
        TextView num = Ui.text(this, "0", 20, Color.WHITE, true);
        num.setGravity(Gravity.CENTER);
        card.addView(num);
        TextView lb = Ui.text(this, label, 9, 0xB3FFFFFF, true);
        lb.setGravity(Gravity.CENTER);
        card.addView(lb);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        if (parent.getChildCount() > 0) lp.leftMargin = Ui.dp(this, 10);
        parent.addView(card, lp);
        return num;
    }

    private void runSplash() {
        splashLogo.setScaleX(0.4f);
        splashLogo.setScaleY(0.4f);
        splashLogo.setAlpha(0f);
        splashLogo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(600)
                .setInterpolator(new OvershootInterpolator(1.25f)).start();

        splash.postDelayed(() -> splash.animate().alpha(0f).setDuration(400)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator a) {
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

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("Java2Dex v1.0")
                .setMessage("On-device Java → DEX converter for modders.\n\n"
                        + "• Compiler: Eclipse ECJ\n"
                        + "• DEXer: Google D8\n\n"
                        + "Output folder:\n"
                        + Project.java2dexRoot(this).getAbsolutePath()
                        + "\n\nBuilt with ❤ for the modding community.")
                .setPositiveButton("Nice", null)
                .show();
    }

    // ---------------- list adapter ----------------

    private class Adapter extends BaseAdapter {

        private class Holder {
            TextView name, chip, sub;
        }

        @Override
        public int getCount() { return shown.size(); }

        @Override
        public Object getItem(int position) { return shown.get(position); }

        @Override
        public long getItemId(int position) { return position; }

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
            h.name.setText(p.name);
            if (p.status == Project.ST_OK) {
                h.chip.setText("✔ SUCCESS");
                h.chip.setTextColor(Ui.GREEN_DARK);
                h.chip.setBackground(Ui.fill(Ui.GREEN_LIGHT, 20, MainActivity.this));
                h.sub.setText(p.dateText() + "  •  " + Ui.size(p.dexSize));
            } else if (p.status == Project.ST_ERROR) {
                h.chip.setText("✖ FAILED");
                h.chip.setTextColor(Ui.RED);
                h.chip.setBackground(Ui.fill(Ui.RED_LIGHT, 20, MainActivity.this));
                h.sub.setText(p.dateText() + "  •  build failed");
            } else {
                h.chip.setText("PENDING");
                h.chip.setTextColor(Ui.TEXT_SUB);
                h.chip.setBackground(Ui.fill(0xFFF1F5F9, 20, MainActivity.this));
                h.sub.setText("created " + p.createdText() + "  •  not built yet");
            }
            return v;
        }

        private View makeItemView(Holder h) {
            FrameLayout wrap = new FrameLayout(MainActivity.this);

            LinearLayout card = new LinearLayout(MainActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(Ui.ripple(MainActivity.this,
                    Ui.outline(Color.WHITE, Ui.STROKE, 16, 1, MainActivity.this)));
            card.setElevation(Ui.dp(MainActivity.this, 2));
            int pad = Ui.dp(MainActivity.this, 14);
            card.setPadding(pad, pad, pad, pad);

            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);

            h.name = Ui.text(MainActivity.this, "", 15.5f, Ui.TEXT, true);
            h.name.setSingleLine(true);
            row.addView(h.name, new LinearLayout.LayoutParams(0, -2, 1f));

            h.chip = Ui.text(MainActivity.this, "", 10.5f, Ui.GREEN_DARK, true);
            h.chip.setBackground(Ui.fill(Ui.GREEN_LIGHT, 20, MainActivity.this));
            h.chip.setPadding(Ui.dp(MainActivity.this, 10), Ui.dp(MainActivity.this, 3),
                    Ui.dp(MainActivity.this, 10), Ui.dp(MainActivity.this, 3));
            row.addView(h.chip, new LinearLayout.LayoutParams(-2, -2));

            card.addView(row, new LinearLayout.LayoutParams(-1, -2));

            h.sub = Ui.text(MainActivity.this, "", 12, Ui.TEXT_SUB, false);
            h.sub.setSingleLine(true);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
            sp.topMargin = Ui.dp(MainActivity.this, 5);
            card.addView(h.sub, sp);

            wrap.addView(card, new FrameLayout.LayoutParams(-1, -2));
            return wrap;
        }
    }
}
