package com.ballbattle;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

/**
 * 游戏主界面
 */
public class MainActivity extends Activity {

    private GameView gameView;
    private Button btnStart;

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

        gameView = findViewById(R.id.game_view);
        btnStart = findViewById(R.id.btn_start);

        // 设置游戏结束监听
        gameView.setOnGameOverListener(new GameView.OnGameOverListener() {
            @Override
            public void onGameOver(int score, int killCount, float maxRadius, int eatFoodCount, float gameTime) {
                showGameOverDialog(score, killCount, maxRadius, eatFoodCount, gameTime);
            }
        });

        // 开始游戏按钮
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnStart.setVisibility(View.GONE);
                gameView.startGame();
            }
        });
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
     * 显示游戏结束对话框
     */
    private void showGameOverDialog(int score, int killCount, float maxRadius, int eatFoodCount, float gameTime) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || isDestroyed()) return;

                int minutes = (int)(gameTime / 60);
                int seconds = (int)(gameTime % 60);

                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.game_over)
                        .setMessage(
                                "得分: " + score + "\n" +
                                "击杀: " + killCount + "\n" +
                                "最大体积: " + (int)maxRadius + "\n" +
                                "吃食物: " + eatFoodCount + "\n" +
                                "存活时间: " + minutes + "分" + seconds + "秒"
                        )
                        .setPositiveButton(R.string.restart, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                gameView.startGame();
                            }
                        })
                        .setNegativeButton("返回菜单", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                findViewById(R.id.btn_start).setVisibility(View.VISIBLE);
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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
