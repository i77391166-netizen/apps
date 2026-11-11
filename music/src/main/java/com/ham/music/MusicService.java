package com.ham.music;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import org.json.JSONArray;
import org.json.JSONObject;

public class MusicService extends Service {

    public static final String A_PREV = "com.ham.music.PREV";
    public static final String A_TOGGLE = "com.ham.music.TOGGLE";
    public static final String A_NEXT = "com.ham.music.NEXT";
    public static final String A_STOP = "com.ham.music.STOP";
    public static final String A_Q = "com.ham.music.QUEUE";

    private static final String CH = "playback";
    private static final int NID = 41;

    public static MusicService I;

    private MediaPlayer mp;
    private MediaSession session;
    private AudioManager am;
    private Object focusReq;
    private JSONArray queue = new JSONArray();
    private int idx = -1;
    private int repeat = 0;
    private boolean playing = false;
    private boolean wantPlay = true;
    private boolean noisyOn = false;

    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            pause();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        I = this;
        am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        session = new MediaSession(this, "hammusic");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                resume();
            }

            @Override
            public void onPause() {
                pause();
            }

            @Override
            public void onSkipToNext() {
                next();
            }

            @Override
            public void onSkipToPrevious() {
                prev();
            }

            @Override
            public void onSeekTo(long p) {
                seek((int) p);
            }

            @Override
            public void onStop() {
                stopAll();
            }
        });
        session.setActive(true);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CH, "Плеер", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        try {
            registerReceiver(noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
            noisyOn = true;
        } catch (Throwable t) {
        }
    }

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public int onStartCommand(Intent in, int flags, int startId) {
        String a = in == null ? null : in.getAction();
        if (a == null) a = A_TOGGLE;
        try {
            if (A_Q.equals(a)) {
                String q = in.getStringExtra("q");
                queue = new JSONArray(q == null ? "[]" : q);
                idx = in.getIntExtra("i", 0);
                repeat = in.getIntExtra("r", 0);
                startFg();
                open(true);
            } else if (A_TOGGLE.equals(a)) {
                doToggle();
            } else if (A_NEXT.equals(a)) {
                next();
            } else if (A_PREV.equals(a)) {
                prev();
            } else if (A_STOP.equals(a)) {
                stopAll();
            }
        } catch (Throwable t) {
        }
        return START_NOT_STICKY;
    }

    private JSONObject cur() {
        try {
            if (idx < 0 || idx >= queue.length()) return null;
            return queue.getJSONObject(idx);
        } catch (Throwable t) {
            return null;
        }
    }

    private int dur() {
        try {
            return mp != null ? mp.getDuration() : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    private int pos() {
        try {
            return mp != null ? mp.getCurrentPosition() : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    private void open(boolean autoplay) {
        JSONObject t = cur();
        if (t == null) {
            stopAll();
            return;
        }
        wantPlay = autoplay;
        if (mp != null) {
            try {
                mp.reset();
            } catch (Throwable e) {
            }
        } else {
            mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer m) {
                    onEnded();
                }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer m, int what, int extra) {
                    playing = false;
                    updateSession();
                    push();
                    return true;
                }
            });
            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer m) {
                    if (wantPlay) {
                        try {
                            m.start();
                            playing = true;
                        } catch (Throwable e) {
                            playing = false;
                        }
                    } else {
                        playing = false;
                    }
                    updateMeta();
                    updateSession();
                    startFg();
                    push();
                }
            });
        }
        try {
            mp.setDataSource(this, Uri.parse(t.optString("u")));
            mp.prepareAsync();
        } catch (Throwable e) {
            playing = false;
        }
        if (autoplay) focus(true);
        updateMeta();
        updateSession();
        startFg();
        push();
    }

    private void onEnded() {
        if (repeat == 1) {
            try {
                mp.seekTo(0);
                mp.start();
                playing = true;
            } catch (Throwable e) {
            }
            updateSession();
            startFg();
            push();
            return;
        }
        if (idx + 1 < queue.length()) {
            idx++;
            open(true);
        } else if (repeat == 2 && queue.length() > 0) {
            idx = 0;
            open(true);
        } else {
            playing = false;
            try {
                if (mp != null) {
                    mp.seekTo(0);
                }
            } catch (Throwable e) {
            }
            updateSession();
            startFg();
            push();
        }
    }

    public void doToggle() {
        if (playing) pause();
        else resume();
    }

    public void resume() {
        if (mp == null) {
            if (queue.length() > 0) {
                if (idx < 0) idx = 0;
                open(true);
            }
            return;
        }
        focus(true);
        try {
            mp.start();
            playing = true;
        } catch (Throwable e) {
        }
        updateSession();
        startFg();
        push();
    }

    public void pause() {
        try {
            if (mp != null) mp.pause();
        } catch (Throwable e) {
        }
        playing = false;
        updateSession();
        startFg();
        push();
    }

    public void next() {
        if (queue.length() == 0) return;
        idx = (idx + 1) % queue.length();
        open(true);
    }

    public void prev() {
        if (queue.length() == 0) return;
        if (pos() > 4000) {
            seek(0);
            return;
        }
        idx = (idx - 1 + queue.length()) % queue.length();
        open(true);
    }

    public void seek(int ms) {
        try {
            if (mp != null) mp.seekTo(Math.max(0, ms));
        } catch (Throwable e) {
        }
        updateSession();
        push();
    }

    public void stopAll() {
        playing = false;
        try {
            if (mp != null) {
                mp.stop();
                mp.release();
            }
        } catch (Throwable e) {
        }
        mp = null;
        queue = new JSONArray();
        idx = -1;
        focus(false);
        try {
            if (session != null) session.setActive(false);
        } catch (Throwable t) {
        }
        try {
            stopForeground(true);
        } catch (Throwable t) {
        }
        stopSelf();
        push();
    }

    private void focus(boolean on) {
        if (am == null) return;
        try {
            if (on) {
                if (Build.VERSION.SDK_INT >= 26) {
                    AudioFocusRequest r = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                            .setAudioAttributes(new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .build())
                            .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                                @Override
                                public void onAudioFocusChange(int f) {
                                    if (f == AudioManager.AUDIOFOCUS_LOSS || f == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                                        pause();
                                    } else if (f == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                                        try {
                                            if (mp != null) mp.setVolume(0.25f, 0.25f);
                                        } catch (Throwable t) {
                                        }
                                    } else if (f == AudioManager.AUDIOFOCUS_GAIN) {
                                        try {
                                            if (mp != null) mp.setVolume(1f, 1f);
                                        } catch (Throwable t) {
                                        }
                                    }
                                }
                            })
                            .build();
                    focusReq = r;
                    am.requestAudioFocus(r);
                } else {
                    am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                }
            } else {
                if (Build.VERSION.SDK_INT >= 26 && focusReq instanceof AudioFocusRequest) {
                    am.abandonAudioFocusRequest((AudioFocusRequest) focusReq);
                } else {
                    am.abandonAudioFocus(null);
                }
            }
        } catch (Throwable t) {
        }
    }

    private void updateMeta() {
        JSONObject t = cur();
        if (t == null || session == null) return;
        try {
            MediaMetadata.Builder b = new MediaMetadata.Builder();
            b.putString(MediaMetadata.METADATA_KEY_TITLE, t.optString("t"));
            b.putString(MediaMetadata.METADATA_KEY_ARTIST, t.optString("a"));
            b.putString(MediaMetadata.METADATA_KEY_ALBUM, t.optString("al"));
            b.putLong(MediaMetadata.METADATA_KEY_DURATION, dur());
            session.setMetadata(b.build());
        } catch (Throwable e) {
        }
    }

    private void updateSession() {
        if (session == null) return;
        try {
            PlaybackState.Builder b = new PlaybackState.Builder();
            b.setActions(PlaybackState.ACTION_PLAY
                    | PlaybackState.ACTION_PAUSE
                    | PlaybackState.ACTION_PLAY_PAUSE
                    | PlaybackState.ACTION_SKIP_TO_NEXT
                    | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                    | PlaybackState.ACTION_SEEK_TO
                    | PlaybackState.ACTION_STOP);
            b.setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, pos(), 1f);
            session.setPlaybackState(b.build());
        } catch (Throwable e) {
        }
    }

    private PendingIntent pi(String action) {
        Intent i = new Intent(this, MusicService.class);
        i.setAction(action);
        return PendingIntent.getService(this, action.hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification buildNotif() {
        JSONObject t = cur();
        String title = t == null ? "Музыка" : t.optString("t");
        String sub = t == null ? "Ничего не играет" : t.optString("a");
        if (sub.length() == 0) sub = "Неизвестный исполнитель";

        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent cpi = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH)
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_note)
                .setContentTitle(title)
                .setContentText(sub)
                .setContentIntent(cpi)
                .setOngoing(playing)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(android.R.drawable.ic_media_previous, "Назад", pi(A_PREV))
                .addAction(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        playing ? "Пауза" : "Играть", pi(A_TOGGLE))
                .addAction(android.R.drawable.ic_media_next, "Дальше", pi(A_NEXT));

        if (session != null) {
            try {
                Notification.MediaStyle st = new Notification.MediaStyle()
                        .setMediaSession(session.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2);
                b.setStyle(st);
            } catch (Throwable e) {
            }
        }
        return b.build();
    }

    private void startFg() {
        try {
            Notification n = buildNotif();
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NID, n);
            }
        } catch (Throwable t) {
        }
    }

    private void push() {
        MainActivity.post("window.mSync&&window.mSync()");
    }

    @Override
    public void onDestroy() {
        I = null;
        try {
            if (noisyOn) unregisterReceiver(noisy);
        } catch (Throwable t) {
        }
        try {
            if (mp != null) mp.release();
        } catch (Throwable t) {
        }
        mp = null;
        try {
            if (session != null) {
                session.setActive(false);
                session.release();
            }
        } catch (Throwable t) {
        }
        super.onDestroy();
    }

    public static String stateJson() {
        JSONObject o = new JSONObject();
        try {
            MusicService s = I;
            if (s == null) {
                o.put("run", false);
                return o.toString();
            }
            o.put("run", true);
            o.put("idx", s.idx);
            o.put("len", s.queue.length());
            o.put("playing", s.playing);
            o.put("pos", s.pos());
            o.put("dur", s.dur());
            o.put("repeat", s.repeat);
            JSONObject t = s.cur();
            if (t != null) o.put("track", t);
        } catch (Throwable t) {
        }
        return o.toString();
    }

    public static void setQueue(Context c, String q, int i, int r) {
        try {
            Intent in = new Intent(c, MusicService.class);
            in.setAction(A_Q);
            in.putExtra("q", q);
            in.putExtra("i", i);
            in.putExtra("r", r);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(in);
            else c.startService(in);
        } catch (Throwable t) {
        }
    }

    public static void cmd(String a) {
        MusicService s = I;
        if (s == null) return;
        if (A_TOGGLE.equals(a)) s.doToggle();
        else if (A_NEXT.equals(a)) s.next();
        else if (A_PREV.equals(a)) s.prev();
        else if (A_STOP.equals(a)) s.stopAll();
    }

    public static void seekTo(int ms) {
        MusicService s = I;
        if (s != null) s.seek(ms);
    }

    public static void setRepeat(int m) {
        MusicService s = I;
        if (s != null) s.repeat = m;
    }
}
