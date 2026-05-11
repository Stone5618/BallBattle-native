package com.ballbattle;

import android.content.Context;
import android.content.SharedPreferences;

public class SkinSystem {
    // 皮肤类型
    public static final int SKIN_TYPE_COLOR = 0;    // 颜色皮肤
    public static final int SKIN_TYPE_AURA = 1;      // 光环
    public static final int SKIN_TYPE_TRAIL = 2;     // 残影

    // 默认解锁的皮肤
    public static final Skin[] DEFAULT_SKINS = {
        new Skin("经典绿", SKIN_TYPE_COLOR, 0xFF44FF44, true, 0),
        new Skin("天空蓝", SKIN_TYPE_COLOR, 0xFF4488FF, true, 0),
        new Skin("烈焰红", SKIN_TYPE_COLOR, 0xFFFF4444, true, 0),
        new Skin("皇家金", SKIN_TYPE_COLOR, 0xFFFFD700, false, 100),
        new Skin("暗影紫", SKIN_TYPE_COLOR, 0xFFAA44FF, false, 200),
        new Skin("海洋青", SKIN_TYPE_COLOR, 0xFF44DDDD, false, 150),
        new Skin("日落橙", SKIN_TYPE_COLOR, 0xFFFF8844, false, 150),
        new Skin("樱花粉", SKIN_TYPE_COLOR, 0xFFFF88AA, false, 200),
        new Skin("薄荷绿", SKIN_TYPE_COLOR, 0xFF88FFAA, false, 250),
        new Skin("星空黑", SKIN_TYPE_COLOR, 0xFF333355, false, 300),
    };

    // 光环皮肤
    public static final Skin[] AURA_SKINS = {
        new Skin("无光环", SKIN_TYPE_AURA, 0x00000000, true, 0),
        new Skin("金色光环", SKIN_TYPE_AURA, 0x40FFD700, false, 200),
        new Skin("蓝色光环", SKIN_TYPE_AURA, 0x404488FF, false, 200),
        new Skin("红色光环", SKIN_TYPE_AURA, 0x40FF4444, false, 250),
        new Skin("彩虹光环", SKIN_TYPE_AURA, 0x40FF44FF, false, 500),
    };

    // 残影皮肤
    public static final Skin[] TRAIL_SKINS = {
        new Skin("无残影", SKIN_TYPE_TRAIL, 0x00000000, true, 0),
        new Skin("白色残影", SKIN_TYPE_TRAIL, 0x40FFFFFF, false, 150),
        new Skin("绿色残影", SKIN_TYPE_TRAIL, 0x4044FF44, false, 150),
        new Skin("金色残影", SKIN_TYPE_TRAIL, 0x40FFD700, false, 300),
    };

    public static class Skin {
        public String name;
        public int type;
        public int color;
        public boolean unlocked;
        public int cost;  // 金币价格

        public Skin(String name, int type, int color, boolean unlocked, int cost) {
            this.name = name;
            this.type = type;
            this.color = color;
            this.unlocked = unlocked;
            this.cost = cost;
        }
    }

    private SharedPreferences prefs;
    private int selectedColorIndex = 0;
    private int selectedAuraIndex = 0;
    private int selectedTrailIndex = 0;
    private int coins = 0;

    public SkinSystem(Context context) {
        prefs = context.getSharedPreferences("skin_data", Context.MODE_PRIVATE);
        load();
    }

    private void load() {
        selectedColorIndex = prefs.getInt("color_index", 0);
        selectedAuraIndex = prefs.getInt("aura_index", 0);
        selectedTrailIndex = prefs.getInt("trail_index", 0);
        coins = prefs.getInt("coins", 0);

        // 加载解锁状态
        for (int i = 0; i < DEFAULT_SKINS.length; i++) {
            boolean unlocked = prefs.getBoolean("color_unlocked_" + i, DEFAULT_SKINS[i].unlocked);
            DEFAULT_SKINS[i].unlocked = unlocked;
        }
        for (int i = 0; i < AURA_SKINS.length; i++) {
            boolean unlocked = prefs.getBoolean("aura_unlocked_" + i, AURA_SKINS[i].unlocked);
            AURA_SKINS[i].unlocked = unlocked;
        }
        for (int i = 0; i < TRAIL_SKINS.length; i++) {
            boolean unlocked = prefs.getBoolean("trail_unlocked_" + i, TRAIL_SKINS[i].unlocked);
            TRAIL_SKINS[i].unlocked = unlocked;
        }
    }

    private void save() {
        SharedPreferences.Editor editor = prefs.edit()
            .putInt("color_index", selectedColorIndex)
            .putInt("aura_index", selectedAuraIndex)
            .putInt("trail_index", selectedTrailIndex)
            .putInt("coins", coins);

        for (int i = 0; i < DEFAULT_SKINS.length; i++) {
            editor.putBoolean("color_unlocked_" + i, DEFAULT_SKINS[i].unlocked);
        }
        for (int i = 0; i < AURA_SKINS.length; i++) {
            editor.putBoolean("aura_unlocked_" + i, AURA_SKINS[i].unlocked);
        }
        for (int i = 0; i < TRAIL_SKINS.length; i++) {
            editor.putBoolean("trail_unlocked_" + i, TRAIL_SKINS[i].unlocked);
        }
        editor.apply();
    }

    public int getSelectedColor() {
        return DEFAULT_SKINS[selectedColorIndex].color;
    }

    public int getSelectedAuraColor() {
        return AURA_SKINS[selectedAuraIndex].color;
    }

    public int getSelectedTrailColor() {
        return TRAIL_SKINS[selectedTrailIndex].color;
    }

    public boolean hasAura() {
        return selectedAuraIndex > 0;
    }

    public boolean hasTrail() {
        return selectedTrailIndex > 0;
    }

    public boolean unlockSkin(int type, int index) {
        Skin[] skins = type == SKIN_TYPE_COLOR ? DEFAULT_SKINS :
                       type == SKIN_TYPE_AURA ? AURA_SKINS : TRAIL_SKINS;
        if (index < 0 || index >= skins.length) return false;
        if (skins[index].unlocked) return true;
        if (coins < skins[index].cost) return false;

        coins -= skins[index].cost;
        skins[index].unlocked = true;
        save();
        return true;
    }

    public void selectSkin(int type, int index) {
        Skin[] skins = type == SKIN_TYPE_COLOR ? DEFAULT_SKINS :
                       type == SKIN_TYPE_AURA ? AURA_SKINS : TRAIL_SKINS;
        if (index < 0 || index >= skins.length) return;
        if (!skins[index].unlocked) return;

        if (type == SKIN_TYPE_COLOR) selectedColorIndex = index;
        else if (type == SKIN_TYPE_AURA) selectedAuraIndex = index;
        else selectedTrailIndex = index;
        save();
    }

    public int getCoins() {
        return coins;
    }

    public void addCoins(int amount) {
        coins += amount;
        save();
    }

    public int getSelectedColorIndex() {
        return selectedColorIndex;
    }

    public int getSelectedAuraIndex() {
        return selectedAuraIndex;
    }

    public int getSelectedTrailIndex() {
        return selectedTrailIndex;
    }
}
