package com.java.jvdoom;

import android.content.Context;
import android.media.MediaPlayer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Doom music. The WAD stores songs in the MUS format; we convert them to a standard MIDI file
 * and let Android's built-in MIDI synthesizer (MediaPlayer) play it. All work happens on a
 * single background thread so the game never stalls while a song is being prepared.
 */
public final class MusicPlayer {
    private final Context ctx;
    private final Wad wad;
    private final ExecutorService ex = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override public Thread newThread(Runnable r) { Thread t = new Thread(r, "jvdoom-music"); t.setDaemon(true); return t; }
    });

    private MediaPlayer mp;
    private String wanted;          // lump asked for last (UI thread)
    private boolean wantedLoop;
    private volatile float volume = 0.7f;
    private volatile boolean paused;

    public MusicPlayer(Context c, Wad w) { ctx = c.getApplicationContext(); wad = w; }

    // ------------------------------------------------------------------ control (called from any thread)
    /** Plays the given music lump; does nothing when it is already the current song. */
    public void request(final String lump, final boolean loop) {
        if (lump == null || lump.equals(wanted)) return;
        wanted = lump; wantedLoop = loop;
        ex.execute(new Runnable() { public void run() { start(lump, loop); } });
    }

    public void setVolume(final float v) {
        volume = Math.max(0f, Math.min(1f, v));
        ex.execute(new Runnable() { public void run() { applyVolume(); } });
    }

    public void pause() {
        paused = true;
        ex.execute(new Runnable() { public void run() { try { if (mp != null && mp.isPlaying()) mp.pause(); } catch (Throwable ignored) { } } });
    }

    public void resume() {
        paused = false;
        ex.execute(new Runnable() { public void run() { applyVolume(); } });
    }

    // ------------------------------------------------------------------ worker thread
    private void applyVolume() {
        try {
            if (volume <= 0f || paused) {
                if (mp != null && mp.isPlaying()) mp.pause();
                return;
            }
            if (mp == null) { if (wanted != null) start(wanted, wantedLoop); return; }
            mp.setVolume(volume, volume);
            if (!mp.isPlaying()) mp.start();
        } catch (Throwable ignored) { }
    }

    private void release() {
        try { if (mp != null) { mp.stop(); mp.release(); } } catch (Throwable ignored) { }
        mp = null;
    }

    private void start(String lump, boolean loop) {
        release();
        try {
            if (!wad.has(lump)) return;
            byte[] midi = musToMidi(wad.bytes(lump));
            if (midi == null) return;
            File f = new File(ctx.getCacheDir(), "jvmus_" + lump + ".mid");
            FileOutputStream o = new FileOutputStream(f);
            try { o.write(midi); } finally { o.close(); }
            final MediaPlayer p = new MediaPlayer();
            p.setDataSource(f.getAbsolutePath());
            p.setLooping(loop);
            p.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override public boolean onError(MediaPlayer m, int what, int extra) { return true; }
            });
            p.prepare();
            p.setVolume(volume, volume);
            mp = p;
            if (volume > 0f && !paused) p.start();
        } catch (Throwable t) {
            release();
        }
    }

    // ------------------------------------------------------------------ MUS -> MIDI
    private static final int[] CTRL = {-1, 0, 1, 7, 10, 11, 91, 93, 64, 67};   // MUS controller -> MIDI controller (0 = program change)

    /** Converts a Doom MUS lump into a format-0 Standard MIDI File (140 ticks per second). Returns null if invalid. */
    static byte[] musToMidi(byte[] mus) {
        if (mus == null || mus.length < 16 || mus[0] != 'M' || mus[1] != 'U' || mus[2] != 'S' || mus[3] != 0x1A) return null;
        int scoreLen = (mus[4] & 255) | (mus[5] & 255) << 8;
        int pos = (mus[6] & 255) | (mus[7] & 255) << 8;
        int end = Math.min(mus.length, pos + scoreLen);

        ByteArrayOutputStream tr = new ByteArrayOutputStream();
        int[] vel = new int[16];
        for (int i = 0; i < 16; i++) vel[i] = 127;
        // tempo: 1,000,000 microseconds per quarter note with 140 ticks per quarter = 140 ticks per second
        tr.write(0); tr.write(0xFF); tr.write(0x51); tr.write(3); tr.write(0x0F); tr.write(0x42); tr.write(0x40);

        int delta = 0;
        boolean done = false;
        while (!done && pos < end) {
            int ev = mus[pos++] & 255;
            boolean last = (ev & 0x80) != 0;
            int type = (ev >> 4) & 7;
            int ch = ev & 15;
            int mch = ch == 15 ? 9 : (ch >= 9 ? ch + 1 : ch);
            switch (type) {
                case 0: {                                           // release note
                    if (pos >= end) { done = true; break; }
                    int note = mus[pos++] & 127;
                    vlq(tr, delta); delta = 0;
                    tr.write(0x80 | mch); tr.write(note); tr.write(0);
                    break;
                }
                case 1: {                                           // play note
                    if (pos >= end) { done = true; break; }
                    int b = mus[pos++] & 255;
                    int note = b & 127;
                    if ((b & 128) != 0 && pos < end) vel[ch] = mus[pos++] & 127;
                    vlq(tr, delta); delta = 0;
                    tr.write(0x90 | mch); tr.write(note); tr.write(vel[ch]);
                    break;
                }
                case 2: {                                           // pitch bend
                    if (pos >= end) { done = true; break; }
                    int bend = (mus[pos++] & 255) * 64;
                    vlq(tr, delta); delta = 0;
                    tr.write(0xE0 | mch); tr.write(bend & 127); tr.write((bend >> 7) & 127);
                    break;
                }
                case 3: {                                           // system event
                    if (pos >= end) { done = true; break; }
                    int c = mus[pos++] & 127, cc = -1;
                    if (c == 10) cc = 120; else if (c == 11) cc = 123; else if (c == 12) cc = 126; else if (c == 13) cc = 127; else if (c == 14) cc = 121;
                    if (cc >= 0) { vlq(tr, delta); delta = 0; tr.write(0xB0 | mch); tr.write(cc); tr.write(0); }
                    break;
                }
                case 4: {                                           // controller / instrument
                    if (pos + 1 >= end) { done = true; break; }
                    int c = mus[pos++] & 127, v = mus[pos++] & 127;
                    if (c == 0) { vlq(tr, delta); delta = 0; tr.write(0xC0 | mch); tr.write(v); }
                    else if (c < CTRL.length) { vlq(tr, delta); delta = 0; tr.write(0xB0 | mch); tr.write(CTRL[c]); tr.write(v); }
                    break;
                }
                case 6: done = true; break;                         // end of score
                default: break;                                     // 5 = end of measure, 7 = unused
            }
            if (done) break;
            if (last) {                                             // a delay (in 1/140 s) follows
                int d = 0, b;
                do {
                    if (pos >= end) break;
                    b = mus[pos++] & 255;
                    d = d * 128 + (b & 127);
                } while ((b & 128) != 0);
                delta += d;
            }
        }
        vlq(tr, delta);
        tr.write(0xFF); tr.write(0x2F); tr.write(0);

        byte[] track = tr.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] head = {'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0, (byte) 140};
        out.write(head, 0, head.length);
        out.write('M'); out.write('T'); out.write('r'); out.write('k');
        out.write(track.length >>> 24); out.write(track.length >>> 16); out.write(track.length >>> 8); out.write(track.length);
        out.write(track, 0, track.length);
        return out.toByteArray();
    }

    private static void vlq(ByteArrayOutputStream o, int v) {
        int buf = v & 0x7F;
        while ((v >>>= 7) > 0) { buf <<= 8; buf |= 0x80 | (v & 0x7F); }
        while (true) {
            o.write(buf & 0xFF);
            if ((buf & 0x80) != 0) buf >>>= 8; else break;
        }
    }
}
