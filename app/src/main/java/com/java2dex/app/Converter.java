package com.java2dex.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;

import org.eclipse.jdt.core.compiler.batch.BatchCompiler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Java → .class (ECJ) → classes.dex (D8) — fully on-device */
public class Converter {

    public interface Callback {
        void onStep(String step);
        void onDone(boolean success, String log);
    }

    public static void convert(final Context ctx, final Project p, final Callback cb) {
        final Context app = ctx.getApplicationContext();
        final Handler h = new Handler(Looper.getMainLooper());
        final StringBuilder log = new StringBuilder();

        new Thread(() -> {
            boolean ok;
            try {
                ok = run(app, p, log, cb, h);
            } catch (Throwable t) {
                log.append("\n[Java2Dex] Unexpected error: ")
                   .append(t.getClass().getSimpleName()).append(": ")
                   .append(t.getMessage()).append("\n");
                ok = false;
            }
            final boolean fOk = ok;
            final String fLog = log.toString();

            p.status = fOk ? Project.ST_OK : Project.ST_ERROR;
            p.builtAt = System.currentTimeMillis();
            if (fOk) p.dexSize = p.publicDexFile(app).length();
            Project.upsert(app, p);

            h.post(() -> cb.onDone(fOk, fLog));
        }, "j2d-convert").start();
    }

    private static boolean run(Context app, Project p, StringBuilder log,
                               Callback cb, Handler h) throws Exception {
        File classes = p.classesDir(app);
        File dexOut = p.dexTmpDir(app);
        Project.deleteDir(classes);
        Project.deleteDir(dexOut);
        classes.mkdirs();
        dexOut.mkdirs();

        log.append("[Java2Dex] Project: ").append(p.name).append("\n");

        // 1. collect sources
        h.post(() -> cb.onStep("Collecting source files…"));
        List<File> sources = new ArrayList<>();
        collect(p.srcDir(app), sources);
        log.append("[Java2Dex] Source files found: ").append(sources.size()).append("\n");
        if (sources.isEmpty()) {
            log.append("[Java2Dex] ERROR: No .java files found in project sources.\n");
            return false;
        }

        // 2. classpath
        StringBuilder cp = new StringBuilder();
        File sysJar = sysAndroidJar(app);
        if (sysJar != null) {
            cp.append(sysJar.getAbsolutePath());
            log.append("[Java2Dex] Using android.jar for compilation.\n");
        } else {
            log.append("[Java2Dex] WARNING: android.jar missing — Android APIs may not resolve.\n");
        }
        File libs = p.libsDir(app);
        File[] jars = libs.listFiles();
        if (jars != null) {
            for (File j : jars) {
                String n = j.getName().toLowerCase();
                if (n.endsWith(".jar") || n.endsWith(".zip")) {
                    if (cp.length() > 0) cp.append(":");
                    cp.append(j.getAbsolutePath());
                    log.append("[Java2Dex] Library: ").append(j.getName()).append("\n");
                }
            }
        }
        if (cp.length() == 0) cp.append(".");

        // 3. ECJ compile : .java → .class
        h.post(() -> cb.onStep("Compiling Java (" + sources.size() + " files)…"));
        log.append("\n[ECJ] Compiling with Eclipse Compiler for Java…\n");
        StringWriter outW = new StringWriter();
        StringWriter errW = new StringWriter();
        PrintWriter out = new PrintWriter(outW, true);
        PrintWriter err = new PrintWriter(errW, true);

        List<String> args = new ArrayList<>();
        args.add("-1.8");
        args.add("-g");
        args.add("-encoding");
        args.add("UTF-8");
        args.add("-proc:none");
        args.add("-cp");
        args.add(cp.toString());
        args.add("-d");
        args.add(classes.getAbsolutePath());
        for (File s : sources) args.add(s.getAbsolutePath());

        boolean ok = BatchCompiler.compile(args.toArray(new String[0]), out, err, null);
        out.flush();
        err.flush();
        if (outW.toString().length() > 0) log.append(outW).append("\n");
        if (errW.toString().length() > 0) log.append(errW).append("\n");

        if (!ok) {
            log.append("\n[Java2Dex] Compilation FAILED. Fix the errors above and retry.\n");
            return false;
        }
        log.append("[ECJ] Compiled successfully.\n");

        // 4. D8 dex : .class → classes.dex
        h.post(() -> cb.onStep("Converting to DEX (D8)…"));
        log.append("\n[D8] Converting .class files to classes.dex…\n");
        List<File> classFiles = new ArrayList<>();
        collect(classes, classFiles);
        List<Path> prog = new ArrayList<>();
        for (File f : classFiles) if (f.getName().endsWith(".class")) prog.add(f.toPath());
        if (prog.isEmpty()) {
            log.append("[D8] ERROR: no .class files produced by compiler.\n");
            return false;
        }
        D8Command.Builder db = D8Command.builder();
        db.addProgramFiles(prog.toArray(new Path[0]));
        if (sysJar != null) db.addLibraryFiles(sysJar.toPath());
        db.setOutput(dexOut.toPath(), OutputMode.DexIndexed);
        D8.run(db.build());
        log.append("[D8] DEX created.\n");

        // 5. save into JAVA2DEX folder
        h.post(() -> cb.onStep("Saving to JAVA2DEX folder…"));
        File dexFile = new File(dexOut, "classes.dex");
        if (!dexFile.exists()) {
            log.append("[Java2Dex] ERROR: classes.dex not found after dexing.\n");
            return false;
        }
        File pub = p.publicDexDir(app);
        Project.deleteDir(pub);
        pub.mkdirs();
        copy(dexFile, new File(pub, "classes.dex"));
        log.append("\n[Java2Dex] SUCCESS\n");
        log.append("[Java2Dex] Saved: ")
           .append(new File(pub, "classes.dex").getAbsolutePath()).append("\n");
        log.append("[Java2Dex] Size: ")
           .append(new File(pub, "classes.dex").length()).append(" bytes\n");
        return true;
    }

    /** android.jar bundled by CI into assets → extracted once to internal storage */
    public static File sysAndroidJar(Context app) {
        File f = new File(app.getFilesDir(), "sys/android.jar");
        return f.exists() ? f : null;
    }

    private static void collect(File dir, List<File> out) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) collect(f, out);
            else out.add(f);
        }
    }

    private static void copy(File src, File dst) throws IOException {
        FileInputStream in = new FileInputStream(src);
        FileOutputStream out = new FileOutputStream(dst);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.flush();
        out.close();
        in.close();
    }
}
