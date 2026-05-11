package com.ballbattle;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 游戏主界面
 */
public class MainActivity extends Activity {

    private GameView gameView;
    private LinearLayout btnModeContainer;
    private Button btnFree;
    private Button btnSurvival;
    private Button btnBattle;
    private Button btnSkin;
    private Button btnCheckIn;
    private Button btnRank;
    private TextView tvCoins;

    private SkinSystem skinSystem;
    private CheckInSystem checkInSystem;
    private RankSystem rankSystem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 全屏模式
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // 隐藏系统UI（沉浸式模式）
        hideSystemUI();

        setContentView(R.layout.activity_main);

        // 初始化系统
        skinSystem = new SkinSystem(this);
        checkInSystem = new CheckInSystem(this);
        rankSystem = new RankSystem(this);

        gameView = findViewById(R.id.game_view);
        btnModeContainer = findViewById(R.id.btn_mode_container);
        btnFree = findViewById(R.id.btn_free);
        btnSurvival = findViewById(R.id.btn_survival);
        btnBattle = findViewById(R.id.btn_battle);
        btnSkin = findViewById(R.id.btn_skin);
        btnCheckIn = findViewById(R.id.btn_checkin);
        btnRank = findViewById(R.id.btn_rank);
        tvCoins = findViewById(R.id.tv_coins);

        // 将皮肤系统传递给引擎
        gameView.getEngine().setSkinSystem(skinSystem);

        // 将段位系统传递给GameView
        gameView.setRankSystem(rankSystem);

        // 更新金币显示
        updateCoinsDisplay();

        // 设置游戏结束监听
        gameView.setOnGameOverListener(new GameView.OnGameOverListener() {
            @Override
            public void onGameOver(int score, int killCount, float maxRadius, int eatFoodCount, float gameTime) {
                showGameOverDialog(score, killCount, maxRadius, eatFoodCount, gameTime);
            }
        });

        // 自由模式按钮
        btnFree.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gameView.getEngine().currentMode = GameEngine.GameMode.FREE;
                gameView.getEngine().setSkinSystem(skinSystem);
                gameView.startGame();
                hideButtons();
            }
        });

        // 生存模式按钮
        btnSurvival.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gameView.getEngine().currentMode = GameEngine.GameMode.SURVIVAL;
                gameView.getEngine().setSkinSystem(skinSystem);
                gameView.startGame();
                hideButtons();
            }
        });

        // 大逃杀模式按钮
        btnBattle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gameView.getEngine().currentMode = GameEngine.GameMode.BATTLE_ROYALE;
                gameView.getEngine().setSkinSystem(skinSystem);
                gameView.startGame();
                hideButtons();
            }
        });

        // 皮肤商店按钮
        btnSkin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSkinDialog();
            }
        });

        // 签到按钮
        btnCheckIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleCheckIn();
            }
        });

        // 段位按钮
        btnRank.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("段位系统")
                        .setMessage(
                                "当前段位: " + rankSystem.getRankName() + "\n" +
                                "星数: " + rankSystem.getStars() + "/" + rankSystem.getMaxStars() + "\u2605\n" +
                                "历史最高: " + rankSystem.getBestRankName() + "\n" +
                                "总场次: " + rankSystem.getTotalGames()
                        )
                        .setPositiveButton(R.string.ok, null)
                        .show();
            }
        });
    }

    /**
     * 隐藏模式选择按钮
     */
    private void hideButtons() {
        btnModeContainer.setVisibility(View.GONE);
    }

    /**
     * 显示模式选择按钮
     */
    private void showButtons() {
        btnModeContainer.setVisibility(View.VISIBLE);
    }

    /**
     * 隐藏系统UI实现沉浸式全屏
     */
    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);
    }

    /**
     * 更新金币显示
     */
    private void updateCoinsDisplay() {
        if (tvCoins != null) {
            tvCoins.setText("金币: " + skinSystem.getCoins());
        }
    }

    /**
     * 处理签到逻辑
     */
    private void handleCheckIn() {
        if (!checkInSystem.hasCheckedInToday()) {
            checkInSystem.checkIn();
            int reward = checkInSystem.getTodayReward();
            skinSystem.addCoins(reward);
            updateCoinsDisplay();

            new AlertDialog.Builder(this)
                    .setTitle(R.string.checkin_success)
                    .setMessage("第" + checkInSystem.getCurrentDay() + "天\n获得: " + checkInSystem.getTodayRewardName())
                    .setPositiveButton(R.string.ok, null)
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.already_checked_in)
                    .setMessage("明天再来哦~\n当前连续签到: " + checkInSystem.getConsecutiveDays() + "天")
                    .setPositiveButton(R.string.ok, null)
                    .show();
        }
    }

    /**
     * 显示皮肤商店对话框
     */
    private void showSkinDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.skin_title) + "  (金币: " + skinSystem.getCoins() + ")");

        // 创建自定义布局
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        // 颜色皮肤区域
        TextView colorTitle = new TextView(this);
        colorTitle.setText(getString(R.string.color_skin));
        colorTitle.setTextSize(18f);
        colorTitle.setTextColor(Color.WHITE);
        colorTitle.setPadding(0, 10, 0, 5);
        layout.addView(colorTitle);

        addSkinButtons(layout, SkinSystem.SKIN_TYPE_COLOR, SkinSystem.DEFAULT_SKINS,
                skinSystem.getSelectedColorIndex());

        // 光环皮肤区域
        TextView auraTitle = new TextView(this);
        auraTitle.setText(getString(R.string.aura_skin));
        auraTitle.setTextSize(18f);
        auraTitle.setTextColor(Color.WHITE);
        auraTitle.setPadding(0, 20, 0, 5);
        layout.addView(auraTitle);

        addSkinButtons(layout, SkinSystem.SKIN_TYPE_AURA, SkinSystem.AURA_SKINS,
                skinSystem.getSelectedAuraIndex());

        // 残影皮肤区域
        TextView trailTitle = new TextView(this);
        trailTitle.setText(getString(R.string.trail_skin));
        trailTitle.setTextSize(18f);
        trailTitle.setTextColor(Color.WHITE);
        trailTitle.setPadding(0, 20, 0, 5);
        layout.addView(trailTitle);

        addSkinButtons(layout, SkinSystem.SKIN_TYPE_TRAIL, SkinSystem.TRAIL_SKINS,
                skinSystem.getSelectedTrailIndex());

        // 使用 ScrollView 包裹
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.addView(layout);

        builder.setView(scrollView);
        builder.setPositiveButton(R.string.ok, null);
        builder.show();
    }

    /**
     * 添加皮肤按钮到布局
     */
    private void addSkinButtons(LinearLayout parentLayout, final int skinType,
                                final SkinSystem.Skin[] skins, int selectedIndex) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        for (int i = 0; i < skins.length; i++) {
            final int index = i;
            final SkinSystem.Skin skin = skins[i];

            LinearLayout itemLayout = new LinearLayout(this);
            itemLayout.setOrientation(LinearLayout.VERTICAL);
            itemLayout.setGravity(Gravity.CENTER);
            itemLayout.setPadding(8, 8, 8, 8);

            // 颜色预览圆形
            View colorPreview = new View(this);
            int previewSize = 50;
            LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(previewSize, previewSize);
            colorPreview.setLayoutParams(previewParams);

            // 设置预览颜色
            if (skinType == SkinSystem.SKIN_TYPE_COLOR) {
                colorPreview.setBackgroundColor(skin.color);
            } else if (skinType == SkinSystem.SKIN_TYPE_AURA) {
                if (i == 0) {
                    colorPreview.setBackgroundColor(Color.TRANSPARENT);
                    colorPreview.setBackgroundResource(android.R.drawable.btn_default);
                } else {
                    colorPreview.setBackgroundColor(skin.color | 0xFF000000);
                }
            } else {
                if (i == 0) {
                    colorPreview.setBackgroundColor(Color.TRANSPARENT);
                    colorPreview.setBackgroundResource(android.R.drawable.btn_default);
                } else {
                    colorPreview.setBackgroundColor(skin.color | 0xFF000000);
                }
            }

            itemLayout.addView(colorPreview);

            // 皮肤名称
            TextView nameText = new TextView(this);
            nameText.setText(skin.name);
            nameText.setTextSize(10f);
            nameText.setTextColor(Color.WHITE);
            nameText.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            nameText.setLayoutParams(nameParams);
            itemLayout.addView(nameText);

            // 状态文字
            TextView statusText = new TextView(this);
            statusText.setTextSize(9f);
            statusText.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            statusText.setLayoutParams(statusParams);

            if (i == selectedIndex) {
                statusText.setText("[" + getString(R.string.equipped) + "]");
                statusText.setTextColor(Color.GREEN);
            } else if (skin.unlocked) {
                statusText.setText(getString(R.string.unlock));
                statusText.setTextColor(Color.GRAY);
            } else {
                statusText.setText(skin.cost + "金币");
                statusText.setTextColor(Color.YELLOW);
            }
            itemLayout.addView(statusText);

            // 点击事件
            itemLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (skin.unlocked) {
                        // 已解锁，直接装备
                        skinSystem.selectSkin(skinType, index);
                        // 刷新对话框
                        showSkinDialog();
                    } else {
                        // 未解锁，尝试购买
                        if (skinSystem.unlockSkin(skinType, index)) {
                            skinSystem.selectSkin(skinType, index);
                            updateCoinsDisplay();
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle(R.string.unlock_success)
                                    .setMessage("已解锁并装备: " + skin.name)
                                    .setPositiveButton(R.string.ok, null)
                                    .show();
                        } else {
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle(R.string.insufficient_coins)
                                    .setMessage("解锁 " + skin.name + " 需要 " + skin.cost + " 金币\n当前金币: " + skinSystem.getCoins())
                                    .setPositiveButton(R.string.ok, null)
                                    .show();
                        }
                    }
                }
            });

            row.addView(itemLayout);
        }

        parentLayout.addView(row);
    }

    /**
     * 显示游戏结束对话框
     */
    private void showGameOverDialog(int score, int killCount, float maxRadius, int eatFoodCount, float gameTime) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || isDestroyed()) return;

                int minutes = (int)(gameTime / 60);
                int seconds = (int)(gameTime % 60);

                // 计算段位星数变化
                int playerRank = gameView.getEngine().getPlayerRank();
                int totalPlayers = gameView.getEngine().getAliveAIBalls().size() + 1;
                int starChange = rankSystem.calculateStars(playerRank, totalPlayers);

                // 记录更新前的段位
                String oldRankName = rankSystem.getRankName();

                // 更新段位
                rankSystem.updateRank(starChange);

                String newRankName = rankSystem.getRankName();
                String rankChangeText = "";
                if (!oldRankName.equals(newRankName)) {
                    rankChangeText = "\n段位变化: " + oldRankName + " -> " + newRankName;
                }

                String rankInfo = "\n段位: " + newRankName + " (" + rankSystem.getStars() + "/" + rankSystem.getMaxStars() + "\u2605)" +
                        "\n星数变化: " + (starChange > 0 ? "+" : "") + starChange +
                        rankChangeText;

                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.game_over)
                        .setMessage(
                                "得分: " + score + "\n" +
                                "击杀: " + killCount + "\n" +
                                "最大体积: " + (int)maxRadius + "\n" +
                                "吃食物: " + eatFoodCount + "\n" +
                                "存活时间: " + minutes + "分" + seconds + "秒" +
                                rankInfo
                        )
                        .setPositiveButton(R.string.restart, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                gameView.getEngine().setSkinSystem(skinSystem);
                                gameView.startGame();
                            }
                        })
                        .setNegativeButton(R.string.back_to_menu, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                showButtons();
                                updateCoinsDisplay();
                            }
                        })
                        .setCancelable(false)
                        .show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        updateCoinsDisplay();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
