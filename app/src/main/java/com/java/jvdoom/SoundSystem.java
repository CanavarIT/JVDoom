package com.java.jvdoom;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.util.HashMap;
import java.util.Map;

/** Small software mixer: Doom DS* sound lumps -> one stereo AudioTrack with distance attenuation and panning. */
public final class SoundSystem implements Game.Sfx {
    static final int RATE = 22050, MAXV = 12;
    final Wad wad;
    final Map<String, short[]> cache = new HashMap<>();
    final short[][] vData = new short[MAXV][];
    final int[] vPos = new int[MAXV];
    final float[] vL = new float[MAXV], vR = new float[MAXV];
    final String[] vName = new String[MAXV];
    volatile float lx, ly, la;
    volatile boolean running;
    volatile boolean muted;
    volatile float volume = 1f;          // 0..1 master SFX volume (Doom's "SFX Volume" slider)
    Thread thread;

    public SoundSystem(Wad w) { wad = w; }

    short[] load(String name) {
        if (cache.containsKey(name)) return cache.get(name);
        short[] out = null;
        byte[] d = wad.bytes(name);
        if (d != null && d.length > 8 + 32 && Wad.u16(d, 0) == 3) {
            int rate = Wad.u16(d, 2), n = Wad.i32(d, 4);
            if (n > d.length - 8) n = d.length - 8;
            int start = 8 + 16, end = 8 + n - 16;
            if (end > start && rate > 0) {
                int len = end - start;
                int outLen = (int) ((long) len * RATE / rate);
                out = new short[outLen];
                for (int i = 0; i < outLen; i++) {
                    float p = (float) i * rate / RATE;
                    int i0 = (int) p; float f = p - i0;
                    int a = (d[start + Math.min(i0, len - 1)] & 255) - 128;
                    int b = (d[start + Math.min(i0 + 1, len - 1)] & 255) - 128;
                    out[i] = (short) ((a + (b - a) * f) * 200f);
                }
            }
        }
        cache.put(name, out);
        return out;
    }

    @Override public void listener(float x, float y, float a) { lx = x; ly = y; la = a; }

    @Override public void play(String lump, float x, float y) {
        if (muted || !running) return;
        short[] s;
        synchronized (cache) { s = load(lump); }
        if (s == null) return;
        float dx = x - lx, dy = y - ly;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float vol = (dist < 200f ? 1f : Math.max(0f, 1f - (dist - 200f) / 1100f)) * volume;
        if (vol < 0.03f) return;
        float pan = 0f;
        if (dist > 8f) pan = (float) Math.sin(Math.atan2(dy, dx) - la);   // +1 = left
        float l = vol * (0.72f + 0.28f * pan), r = vol * (0.72f - 0.28f * pan);
        synchronized (vData) {
            int slot = -1;
            for (int i = 0; i < MAXV; i++) if (vData[i] == null) { slot = i; break; }
            if (slot < 0) {
                int oldest = 0, best = -1;
                for (int i = 0; i < MAXV; i++) if (vPos[i] > best) { best = vPos[i]; oldest = i; }
                slot = oldest;
            }
            vData[slot] = s; vPos[slot] = 0; vL[slot] = l; vR[slot] = r; vName[slot] = lump;
        }
    }

    public void start() {
        if (running) return;
        running = true;
        thread = new Thread(new Runnable() { public void run() { loop(); } }, "doom-audio");
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    public void stop() {
        running = false;
        if (thread != null) { try { thread.join(500); } catch (InterruptedException ignored) { } thread = null; }
        synchronized (vData) { for (int i = 0; i < MAXV; i++) vData[i] = null; }
    }

    @SuppressWarnings("deprecation")
    void loop() {
        int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) min = 4096;
        AudioTrack track;
        try {
            track = new AudioTrack(AudioManager.STREAM_MUSIC, RATE, AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(min, 4096), AudioTrack.MODE_STREAM);
            track.play();
        } catch (Throwable t) { running = false; return; }
        final int FR = 512;
        short[] buf = new short[FR * 2];
        int[] acc = new int[FR * 2];
        while (running) {
            java.util.Arrays.fill(acc, 0);
            synchronized (vData) {
                for (int v = 0; v < MAXV; v++) {
                    short[] d = vData[v];
                    if (d == null) continue;
                    int p = vPos[v];
                    int n = Math.min(FR, d.length - p);
                    float l = vL[v], r = vR[v];
                    for (int i = 0; i < n; i++) {
                        int smp = d[p + i];
                        acc[i * 2] += (int) (smp * l);
                        acc[i * 2 + 1] += (int) (smp * r);
                    }
                    vPos[v] = p + n;
                    if (vPos[v] >= d.length) vData[v] = null;
                }
            }
            for (int i = 0; i < acc.length; i++) {
                int x = acc[i];
                buf[i] = (short) (x > 32767 ? 32767 : (x < -32768 ? -32768 : x));
            }
            track.write(buf, 0, buf.length);
        }
        try { track.stop(); } catch (Throwable ignored) { }
        track.release();
    }
}
