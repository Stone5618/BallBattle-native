package com.ballbattle;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 游戏结束对话框
 * 纯代码构建的自定义游戏结束弹窗，无需额外布局文件
 */
public class GameOverDialog extends AlertDialog {

    private int finalScore;

    public interface OnRestartListener {
        void onRestart();
    }

    private OnRestartListener restartListener;

    public GameOverDialog(Context context, int score, OnRestartListener listener) {
        super(context);
        this.finalScore = score;
        this.restartListener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 设置对话框属性
        setCancelable(false);
        setTitle(R.string.game_over);

        // 纯代码创建自定义视图
        Context ctx = getContext();
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(40, 30, 40, 20);

        // "你的得分" 标签
        TextView labelView = new TextView(ctx);
        labelView.setText(R.string.your_score);
        labelView.setTextSize(18);
        labelView.setTextColor(0xFFAAAAAA);
        labelView.setGravity(Gravity.CENTER);
        layout.addView(labelView);

        // 分数显示
        TextView scoreView = new TextView(ctx);
        scoreView.setText(String.valueOf(finalScore));
        scoreView.setTextSize(48);
        scoreView.setTextColor(0xFF44FF44);
        scoreView.setGravity(Gravity.CENTER);
        scoreView.setPadding(0, 10, 0, 20);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        scoreView.setLayoutParams(lp);
        layout.addView(scoreView);

        setView(layout);

        setButton(DialogInterface.BUTTON_POSITIVE, ctx.getString(R.string.restart),
                new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (restartListener != null) {
                            restartListener.onRestart();
                        }
                        dismiss();
                    }
                });
    }
}
