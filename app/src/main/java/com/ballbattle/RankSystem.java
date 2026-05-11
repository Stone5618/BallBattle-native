package com.ballbattle;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 段位系统 - 管理玩家段位、星数和升降段逻辑
 */
public class RankSystem {
    // 段位定义
    public static final String[] RANK_NAMES = {
        "青铜III", "青铜II", "青铜I",
        "白银III", "白银II", "白银I",
        "黄金III", "黄金II", "黄金I",
        "白金III", "白金II", "白金I",
        "钻石III", "钻石II", "钻石I",
        "大师III", "大师II", "大师I",
        "王者III", "王者II", "王者I",
        "超神"
    };

    // 每个段位需要的星数
    public static final int[] RANK_STARS = {
        3, 3, 3,  // 青铜
        4, 4, 4,  // 白银
        4, 4, 4,  // 黄金
        5, 5, 5,  // 白金
        5, 5, 5,  // 钻石
        6, 6, 6,  // 大师
        7, 7, 7,  // 王者
        999       // 超神（无限）
    };

    // 段位颜色
    public static final int[] RANK_COLORS = {
        0xFFCD7F32, 0xFFCD7F32, 0xFFCD7F32,  // 青铜
        0xFFC0C0C0, 0xFFC0C0C0, 0xFFC0C0C0,  // 白银
        0xFFFFD700, 0xFFFFD700, 0xFFFFD700,  // 黄金
        0xFFE5E4E2, 0xFFE5E4E2, 0xFFE5E4E2,  // 白金
        0xFF00BFFF, 0xFF00BFFF, 0xFF00BFFF,  // 钻石
        0xFFFF4500, 0xFFFF4500, 0xFFFF4500,  // 大师
        0xFFFF1493, 0xFFFF1493, 0xFFFF1493,  // 王者
        0xFFFF0000  // 超神
    };

    private SharedPreferences prefs;
    private int rankIndex = 0;  // 当前段位索引
    private int stars = 0;      // 当前星数
    private int totalGames = 0;
    private int bestRank = 0;   // 历史最高段位（索引越大越好）

    public RankSystem(Context context) {
        prefs = context.getSharedPreferences("rank_data", Context.MODE_PRIVATE);
        load();
    }

    private void load() {
        rankIndex = prefs.getInt("rank_index", 0);
        stars = prefs.getInt("stars", 0);
        totalGames = prefs.getInt("total_games", 0);
        bestRank = prefs.getInt("best_rank", 0);
    }

    private void save() {
        prefs.edit()
            .putInt("rank_index", rankIndex)
            .putInt("stars", stars)
            .putInt("total_games", totalGames)
            .putInt("best_rank", bestRank)
            .apply();
    }

    /**
     * 根据排名计算星数变化
     * @param playerRank 玩家排名（1开始）
     * @param totalPlayers 总玩家数
     * @return 星数变化
     */
    public int calculateStars(int playerRank, int totalPlayers) {
        if (playerRank <= 0) return -1;
        if (playerRank == 1) return 3;
        if (playerRank <= 3) return 2;
        if (playerRank <= Math.ceil(totalPlayers * 0.3f)) return 1;
        if (playerRank <= Math.ceil(totalPlayers * 0.5f)) return 0;
        return -1;
    }

    /**
     * 更新段位
     * @param starChange 星数变化
     */
    public void updateRank(int starChange) {
        totalGames++;
        stars += starChange;

        // 升段
        while (stars >= RANK_STARS[rankIndex] && rankIndex < RANK_NAMES.length - 1) {
            stars -= RANK_STARS[rankIndex];
            rankIndex++;
        }

        // 掉段（钻石以下有保护）
        if (stars < 0) {
            if (rankIndex > 0) {
                if (rankIndex <= 11) {  // 钻石I及以下有保护
                    // 检查是否是第一次掉段
                    boolean hasProtection = prefs.getBoolean("drop_protect", true);
                    if (hasProtection) {
                        stars = 0;
                        prefs.edit().putBoolean("drop_protect", false).apply();
                        save();
                        return;
                    }
                }
                rankIndex--;
                stars += RANK_STARS[rankIndex] - 1;
                if (stars < 0) stars = 0;
                // 重置保护
                prefs.edit().putBoolean("drop_protect", true).apply();
            } else {
                stars = 0;
            }
        }

        // 更新最高段位
        if (rankIndex > bestRank) {
            bestRank = rankIndex;
        }

        save();
    }

    public String getRankName() {
        return RANK_NAMES[rankIndex];
    }

    public int getRankColor() {
        return RANK_COLORS[rankIndex];
    }

    public int getStars() {
        return stars;
    }

    public int getMaxStars() {
        return RANK_STARS[rankIndex];
    }

    public int getRankIndex() {
        return rankIndex;
    }

    public int getTotalGames() {
        return totalGames;
    }

    public String getBestRankName() {
        return RANK_NAMES[bestRank];
    }
}
