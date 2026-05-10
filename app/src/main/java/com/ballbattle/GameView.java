package com.ballbattle;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import java.util.List;

/**
 * 游戏视图 - 基于 SurfaceView 的渲染层
 * 实现独立线程游戏循环，60FPS
 */
public class GameView extends SurfaceView implements SurfaceHolder.Callback {

    private static final int TARGET_FPS = 60;
    private static final float FRAME_TIME = 1f / TARGET_FPS;
    private static final int GRID_SIZE = 100; // 网格间距
    private static final int BORDER_WIDTH = 4; // 边界线宽度

    private SurfaceHolder holder;
    private GameEngine engine;
    private GameThread gameThread;

    // 画笔
    private final Paint bgPaint = new Paint();
    private final Paint gridPaint = new Paint();
    private final Paint borderPaint = new Paint();
    private final Paint hudPaint = new Paint();
    private final Paint minimapPaint = new Paint();
    private final Paint minimapBgPaint = new Paint();
    private final Paint ballPaint = new Paint();

    // 屏幕尺寸
    private int screenWidth = 0;
    private int screenHeight = 0;

    // 游戏结束回调
    private OnGameOverListener gameOverListener;

    public interface OnGameOverListener {
        void onGameOver(int score);
    }

    public GameView(Context context) {
        super(context);
        init();
    }

    private void init() {
        holder = getHolder();
        holder.addCallback(this);

        engine = new GameEngine();

        // 初始化画笔
        bgPaint.setColor(0xFF1a1a2e);
        bgPaint.setStyle(Paint.Style.FILL);

        gridPaint.setColor(0xFF2a2a4e);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f);

        borderPaint.setColor(0xFFFF4444);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(BORDER_WIDTH);

        hudPaint.setColor(0xFFFFFFFF);
        hudPaint.setTextSize(40f);
        hudPaint.setTextAlign(Paint.Align.LEFT);
        hudPaint.setAntiAlias(true);

        minimapBgPaint.setColor(0x40000000);
        minimapBgPaint.setStyle(Paint.Style.FILL);

        minimapPaint.setColor(0xFFFFFFFF);
        minimapPaint.setStyle(Paint.Style.STROKE);
        minimapPaint.setStrokeWidth(2f);
        minimapPaint.setAntiAlias(true);

        ballPaint.setAntiAlias(true);

        // 设置触摸事件
        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return handleTouchEvent(event);
            }
        });
    }

    /**
     * 处理触摸事件
     */
    private boolean handleTouchEvent(MotionEvent event) {
        if (engine == null || !engine.gameRunning) return false;

        int action = event.getAction();
        int pointerCount = event.getPointerCount();

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            // 使用第一个触摸点
            float touchX = event.getX(0);
            float touchY = event.getY(0);
            engine.setPlayerDirection(touchX, touchY, screenWidth, screenHeight);
            return true;
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            engine.stopPlayer();
            return true;
        }

        // 多点触控处理
        if (action == MotionEvent.ACTION_POINTER_UP) {
            // 如果还有其他触摸点，使用最后一个
            if (pointerCount > 1) {
                int remainingIndex = (event.getActionIndex() == 0) ? 1 : 0;
                float touchX = event.getX(remainingIndex);
                float touchY = event.getY(remainingIndex);
                engine.setPlayerDirection(touchX, touchY, screenWidth, screenHeight);
            } else {
                engine.stopPlayer();
            }
            return true;
        }

        return false;
    }

    /**
     * 设置游戏结束监听
     */
    public void setOnGameOverListener(OnGameOverListener listener) {
        this.gameOverListener = listener;
    }

    /**
     * 开始游戏
     */
    public void startGame() {
        engine.setGameCallback(new GameEngine.GameCallback() {
            @Override
            public void onGameOver(int score) {
                if (gameOverListener != null) {
                    post(new Runnable() {
                        @Override
                        public void run() {
                            if (gameOverListener != null) {
                                gameOverListener.onGameOver(score);
                            }
                        }
                    });
                }
            }
        });
        engine.initGame();
    }

    /**
     * 获取游戏引擎
     */
    public GameEngine getEngine() {
        return engine;
    }

    // ==================== SurfaceHolder.Callback ====================

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        screenWidth = getWidth();
        screenHeight = getHeight();

        // 启动游戏线程
        if (gameThread == null || !gameThread.isRunning()) {
            gameThread = new GameThread();
            gameThread.setRunning(true);
            gameThread.start();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        screenWidth = width;
        screenHeight = height;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (gameThread != null) {
            gameThread.setRunning(false);
            boolean retry = true;
            while (retry) {
                try {
                    gameThread.join();
                    retry = false;
                } catch (InterruptedException e) {
                    // 重试
                }
            }
            gameThread = null;
        }
    }

    // ==================== 游戏线程 ====================

    /**
     * 独立游戏循环线程
     */
    private class GameThread extends Thread {
        private volatile boolean running = false;

        public void setRunning(boolean running) {
            this.running = running;
        }

        public boolean isRunning() {
            return running;
        }

        @Override
        public void run() {
            long lastTime = System.nanoTime();

            while (running) {
                long currentTime = System.nanoTime();
                float deltaTime = (currentTime - lastTime) / 1_000_000_000f;
                lastTime = currentTime;

                // 限制 deltaTime 防止跳帧过大
                if (deltaTime > 0.1f) {
                    deltaTime = 0.1f;
                }

                // 更新游戏逻辑
                engine.update(deltaTime);

                // 渲染
                Canvas canvas = null;
                try {
                    canvas = holder.lockCanvas();
                    if (canvas != null) {
                        render(canvas);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (canvas != null) {
                        try {
                            holder.unlockCanvasAndPost(canvas);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }

                // 帧率控制
                long elapsed = System.nanoTime() - currentTime;
                long sleepTime = (long) (FRAME_TIME * 1_000_000_000f) - elapsed;
                if (sleepTime > 0) {
                    try {
                        Thread.sleep(sleepTime / 1_000_000, (int) (sleepTime % 1_000_000));
                    } catch (InterruptedException e) {
                        // 忽略
                    }
                }
            }
        }
    }

    // ==================== 渲染 ====================

    /**
     * 渲染一帧
     */
    private void render(Canvas canvas) {
        if (canvas == null || screenWidth <= 0 || screenHeight <= 0) return;

        // 计算摄像机偏移（使玩家居中）
        float cameraX = engine.cameraX - screenWidth / 2f;
        float cameraY = engine.cameraY - screenHeight / 2f;

        // 1. 绘制背景
        canvas.drawColor(0xFF1a1a2e);

        // 2. 绘制网格
        drawGrid(canvas, cameraX, cameraY);

        // 3. 绘制世界边界
        drawBorder(canvas, cameraX, cameraY);

        // 4. 绘制食物
        List<Food> foods = engine.getAliveFoods();
        for (Food food : foods) {
            food.draw(canvas, ballPaint, cameraX, cameraY, screenWidth, screenHeight);
        }

        // 5. 绘制AI球
        List<Ball> aiBalls = engine.getAliveAIBalls();
        for (Ball ai : aiBalls) {
            ai.draw(canvas, ballPaint, cameraX, cameraY, screenWidth, screenHeight);
        }

        // 6. 绘制玩家（最后绘制，确保在最上层）
        if (engine.player != null && engine.player.alive) {
            engine.player.draw(canvas, ballPaint, cameraX, cameraY, screenWidth, screenHeight);
        }

        // 7. 绘制HUD
        drawHUD(canvas);

        // 8. 绘制小地图
        drawMinimap(canvas);
    }

    /**
     * 绘制网格线
     */
    private void drawGrid(Canvas canvas, float cameraX, float cameraY) {
        // 计算可见区域在世界坐标中的范围
        float worldLeft = cameraX;
        float worldTop = cameraY;
        float worldRight = cameraX + screenWidth;
        float worldBottom = cameraY + screenHeight;

        // 计算起始网格线
        int startX = (int) (worldLeft / GRID_SIZE) * GRID_SIZE;
        int startY = (int) (worldTop / GRID_SIZE) * GRID_SIZE;

        // 绘制垂直线
        for (int x = startX; x <= worldRight; x += GRID_SIZE) {
            if (x < 0 || x > GameEngine.WORLD_WIDTH) continue;
            float screenX = x - cameraX;
            canvas.drawLine(screenX, 0, screenX, screenHeight, gridPaint);
        }

        // 绘制水平线
        for (int y = startY; y <= worldBottom; y += GRID_SIZE) {
            if (y < 0 || y > GameEngine.WORLD_HEIGHT) continue;
            float screenY = y - cameraY;
            canvas.drawLine(0, screenY, screenWidth, screenY, gridPaint);
        }
    }

    /**
     * 绘制世界边界
     */
    private void drawBorder(Canvas canvas, float cameraX, float cameraY) {
        float left = 0 - cameraX;
        float top = 0 - cameraY;
        float right = GameEngine.WORLD_WIDTH - cameraX;
        float bottom = GameEngine.WORLD_HEIGHT - cameraY;

        canvas.drawRect(left, top, right, bottom, borderPaint);
    }

    /**
     * 绘制HUD（抬头显示）
     */
    private void drawHUD(Canvas canvas) {
        // 分数
        hudPaint.setColor(0xFFFFFFFF);
        hudPaint.setTextSize(36f);
        hudPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("分数: " + engine.totalScore, 20, 50, hudPaint);

        // 玩家半径信息
        if (engine.player != null && engine.player.alive) {
            hudPaint.setTextSize(24f);
            hudPaint.setColor(0xCCFFFFFF);
            canvas.drawText("半径: " + (int) engine.player.radius, 20, 80, hudPaint);
        }

        // 存活AI数量
        int aliveAI = engine.getAliveAIBalls().size();
        hudPaint.setTextSize(24f);
        hudPaint.setColor(0xCCFFFFFF);
        canvas.drawText("对手: " + aliveAI, 20, 110, hudPaint);
    }

    /**
     * 绘制小地图（右下角）
     */
    private void drawMinimap(Canvas canvas) {
        int minimapSize = 120;
        int padding = 15;
        int minimapX = screenWidth - minimapSize - padding;
        int minimapY = screenHeight - minimapSize - padding;

        // 小地图背景
        canvas.drawRect(minimapX, minimapY,
                minimapX + minimapSize, minimapY + minimapSize, minimapBgPaint);

        // 小地图边框
        canvas.drawRect(minimapX, minimapY,
                minimapX + minimapSize, minimapY + minimapSize, minimapPaint);

        // 缩放比例
        float scaleX = (float) minimapSize / GameEngine.WORLD_WIDTH;
        float scaleY = (float) minimapSize / GameEngine.WORLD_HEIGHT;

        // 绘制AI球在小地图上的位置
        List<Ball> aiBalls = engine.getAliveAIBalls();
        for (Ball ai : aiBalls) {
            float mx = minimapX + ai.x * scaleX;
            float my = minimapY + ai.y * scaleY;
            minimapPaint.setColor(ai.color);
            minimapPaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(mx, my, 3f, minimapPaint);
        }

        // 绘制玩家在小地图上的位置
        if (engine.player != null && engine.player.alive) {
            float px = minimapX + engine.player.x * scaleX;
            float py = minimapY + engine.player.y * scaleY;
            minimapPaint.setColor(0xFF44FF44);
            minimapPaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(px, py, 4f, minimapPaint);
        }

        // 恢复画笔状态
        minimapPaint.setStyle(Paint.Style.STROKE);
        minimapPaint.setColor(0xFFFFFFFF);
    }
}
