package com.ballbattle;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.media.ToneGenerator;
import android.os.Handler;

/**
 * 音效管理器 - 使用 ToneGenerator 生成简单音效
 */
public class SoundManager {
    private SoundPool soundPool;
    private Context context;
    private boolean soundEnabled = true;
    private boolean bgmEnabled = true;

    // 音效ID常量
    public static final int SOUND_EAT_FOOD = 1;
    public static final int SOUND_EAT_BALL = 2;
    public static final int SOUND_SPLIT = 3;
    public static final int SOUND_SPIT = 4;
    public static final int SOUND_DEATH = 5;
    public static final int SOUND_HIT_VIRUS = 6;
    public static final int SOUND_MERGE = 7;
    public static final int SOUND_BUTTON = 8;

    private Handler handler = new Handler();

    public SoundManager(Context context) {
        this.context = context;

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(8)
                .setAudioAttributes(attrs)
                .build();
    }

    /**
     * 播放指定音效
     */
    public void play(int soundId) {
        if (!soundEnabled) return;
        try {
            ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 50);

            switch (soundId) {
                case SOUND_EAT_FOOD:
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP, 80);
                    break;
                case SOUND_EAT_BALL:
                    tone.startTone(ToneGenerator.TONE_PROP_ACK, 150);
                    break;
                case SOUND_SPLIT:
                    tone.startTone(ToneGenerator.TONE_PROP_PROMPT, 120);
                    break;
                case SOUND_SPIT:
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 60);
                    break;
                case SOUND_DEATH:
                    tone.startTone(ToneGenerator.TONE_PROP_NACK, 300);
                    break;
                case SOUND_HIT_VIRUS:
                    tone.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 200);
                    break;
                case SOUND_MERGE:
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP, 100);
                    break;
                case SOUND_BUTTON:
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP, 50);
                    break;
            }

            // 延迟释放 ToneGenerator 资源
            handler.postDelayed(() -> {
                try {
                    tone.release();
                } catch (Exception e) {
                    // 忽略释放异常
                }
            }, 500);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setSoundEnabled(boolean enabled) {
        this.soundEnabled = enabled;
    }

    public void setBgmEnabled(boolean enabled) {
        this.bgmEnabled = enabled;
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public boolean isBgmEnabled() {
        return bgmEnabled;
    }

    public void release() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
    }
}
