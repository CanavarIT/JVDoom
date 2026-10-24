package com.java.jvdoom;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    static final int REQ_PICK = 42;
    static final String[] NAMES = {"doom1.wad", "doom.wad", "doom2.wad", "freedoom1.wad", "freedoom2.wad", "doomu.wad", "plutonia.wad", "tnt.wad"};

    FrameLayout root;
    TextView status;
    GameView view;
    SoundSystem sound;
    volatile boolean loading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(18);
        status.setGravity(Gravity.CENTER);
        status.setPadding(48, 48, 48, 48);
        root.addView(status, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        hideSystemUi();
        loadWadAsync(null);
    }

    void hideSystemUi() {
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (c != null) {
            c.hide(WindowInsetsCompat.Type.systemBars());
            c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    // ------------------------------------------------------------------ WAD discovery
    /** Returns bytes of the first usable WAD: external app dir, internal dir, assets. */
    byte[] findWad() throws IOException {
        File[] dirs = {getExternalFilesDir(null), getFilesDir()};
        for (File dir : dirs) {
            if (dir == null || !dir.isDirectory()) continue;
            for (String n : NAMES) {
                File f = ci(dir, n);
                if (f != null) return read(new java.io.FileInputStream(f));
            }
            File[] all = dir.listFiles();
            if (all != null) {
                Arrays.sort(all);
                for (File f : all) if (f.getName().toLowerCase().endsWith(".wad")) return read(new java.io.FileInputStream(f));
            }
        }
        AssetManager am = getAssets();
        String[] assets = am.list("");
        if (assets != null) {
            for (String n : NAMES) for (String a : assets) if (a.equalsIgnoreCase(n)) return read(am.open(a));
            for (String a : assets) if (a.toLowerCase().endsWith(".wad")) return read(am.open(a));
        }
        return null;
    }

    static File ci(File dir, String name) {
        File[] l = dir.listFiles();
        if (l == null) return null;
        for (File f : l) if (f.getName().equalsIgnoreCase(name) && f.isFile()) return f;
        return null;
    }

    static byte[] read(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream(1 << 22);
        byte[] b = new byte[1 << 16];
        int r;
        while ((r = in.read(b)) > 0) bo.write(b, 0, r);
        in.close();
        return bo.toByteArray();
    }

    void loadWadAsync(final byte[] given) {
        if (loading) return;
        loading = true;
        status.setText("Loading WAD…");
        new Thread(new Runnable() {
            public void run() {
                try {
                    byte[] data = given != null ? given : findWad();
                    if (data == null) { fail(null, true); return; }
                    Wad wad = new Wad(data);
                    if (wad.maps().isEmpty()) throw new IOException("This WAD contains no maps (doom1.wad / doom.wad / doom2.wad required)");
                    Gfx gfx = new Gfx(wad);
                    final Wad fw = wad; final Gfx fg = gfx;
                    runOnUiThread(new Runnable() { public void run() { startGame(fw, fg); } });
                } catch (final Throwable t) {
                    fail(t.getMessage() != null ? t.getMessage() : t.toString(), true);
                }
            }
        }, "wad-loader").start();
    }

    void fail(final String msg, final boolean offerPick) {
        runOnUiThread(new Runnable() { public void run() {
            loading = false;
            String help = "Put doom1.wad (or doom.wad / doom2.wad) here:\n\n"
                    + "  app/src/main/assets/doom1.wad  (before building)\n\nor on the phone, in the folder:\n\n"
                    + (getExternalFilesDir(null) != null ? getExternalFilesDir(null).getAbsolutePath() : "Android/data/com.java.jvdoom/files")
                    + "\n\nor pick the file manually.";
            status.setText((msg != null ? "Error: " + msg + "\n\n" : "WAD not found.\n\n") + help);
            new AlertDialog.Builder(MainActivity.this).setTitle("WAD file required")
                    .setMessage((msg != null ? msg + "\n\n" : "") + help)
                    .setPositiveButton("Choose file…", new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface d, int w) { pick(); } })
                    .setNegativeButton("Close", null).show();
        } });
    }

    void pick() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_PICK);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_PICK || res != Activity.RESULT_OK || data == null || data.getData() == null) return;
        final Uri u = data.getData();
        loading = true;
        status.setText("Copying WAD…");
        new Thread(new Runnable() {
            public void run() {
                try {
                    InputStream in = getContentResolver().openInputStream(u);
                    byte[] bytes = read(in);
                    // keep a copy so next start is automatic
                    File out = new File(getFilesDir(), "doom1.wad");
                    OutputStream os = new FileOutputStream(out);
                    os.write(bytes); os.close();
                    loading = false;
                    loadWadAsync(bytes);
                } catch (Throwable t) { fail(String.valueOf(t.getMessage()), true); }
            }
        }).start();
    }

    // ------------------------------------------------------------------ game
    void startGame(Wad wad, Gfx gfx) {
        sound = new SoundSystem(wad);
        Renderer tmp = new Renderer(gfx, 320, 200);
        Game game = new Game(wad, gfx, tmp);
        game.sfx = sound;
        String first = wad.maps().get(0);
        game.startMap(first, false);          // preloaded; the title screen is shown until "New Game"
        view = new GameView(this, game, gfx, wad, sound);
        root.removeAllViews();
        root.addView(view, new FrameLayout.LayoutParams(-1, -1));
        sound.start();
        loading = false;
    }

    @Override protected void onPause() {
        super.onPause();
        if (view != null) view.onPauseActivity();
    }

    @Override protected void onResume() {
        super.onResume();
        if (view != null) view.onResumeActivity();
        hideSystemUi();
    }

    @Override public void onBackPressed() {
        if (view != null && view.onBack()) return;
        super.onBackPressed();
    }
}
