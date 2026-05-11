package com.ballbattle;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.List;

/**
 * 游戏视图 - 基于 SurfaceView 的渲染层
 * 实现独立线程游戏循环，60FPS，带虚拟摇杆
 */
public class GameView extends SurfaceView implements SurfaceHolder.Callback {

    private static final int TARGET_FPS = 60;
    private static final float FRAME_TIME = 1f / TARGET_FPS;
    private static final int GRID_SIZE = 100;
    private static final int BORDER_WIDTH = 4;

    // 虚拟摇杆参数
    private static final float JOYSTICK_RADIUS = 80f;
    private static final float JOYSTICK_CENTER_RADIUS = 30f;
    private static final float JOYSTICK_MARGIN = 100f;

    // 按钮参数
    private static final float BUTTON_SIZE = 70f;
    private static final float BUTTON_MARGIN = 20f;

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
    private final Paint joystickBgPaint = new Paint();
    private final Paint joystickStickPaint = new Paint();
    private final Paint buttonPaint = new Paint();
    private final Paint buttonTextPaint = new Paint();

    // 屏幕尺寸
    private int screenWidth = 0;
    private int screenHeight = 0;

    // 虚拟摇杆状态
    private float joystickCenterX = 0;
    private float joystickCenterY = 0;
    private float joystickStickX = 0;
    private float joystickStickY = 0;
    private boolean joystickActive = false;
    private int joystickPointerId = -1;

    // 游戏结束回调
    private OnGameOverListener gameOverListener;

    // 粒子特效系统
    private static class Particle {
        float x, y, vx, vy, radius, life, maxLife;
        int color;
    }
    private final List<Particle> particles = new ArrayList<>();

    public interface OnGameOverListener {
        void onGameOver(int score, int killCount, float maxRadius, int eatFoodCount, float gameTime);
    }

    public GameView(Context context) {
        super(context);
        init();
    }

    public GameView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GameView(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
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

        // 摇杆画笔
        joystickBgPaint.setColor(0x40000000);
        joystickBgPaint.setStyle(Paint.Style.FILL);
        joystickBgPaint.setAntiAlias(true);

        joystickStickPaint.setColor(0x80FFFFFF);
        joystickStickPaint.setStyle(Paint.Style.FILL);
        joystickStickPaint.setAntiAlias(true);

        // 按钮画笔
        buttonPaint.setStyle(Paint.Style.FILL);
        buttonPaint.setAntiAlias(true);
        buttonTextPaint.setColor(0xFFFFFFFF);
        buttonTextPaint.setTextSize(36f);
        buttonTextPaint.setTextAlign(Paint.Align.CENTER);
        buttonTextPaint.setAntiAlias(true);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int pointerIndex = event.getActionIndex();
        int pointerId = event.getPointerId(pointerIndex);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                // 检查是否点击按钮
                float x = event.getX(pointerIndex);
                float y = event.getY(pointerIndex);
                
                if (isInSplitButton(x, y)) {
                    engine.split();
                    return true;
                }
                if (isInSpitButton(x, y)) {
                    engine.spit();
                    return true;
                }
                
                // 检查是否按在摇杆区域（左下角）
                if (isInJoystickArea(x, y) && !joystickActive) {
                    joystickActive = true;
                    joystickPointerId = pointerId;
                    joystickCenterX = x;
                    joystickCenterY = y;
                    updateJoystickStick(x, y);
                    return true;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                // 更新摇杆位置
                if (joystickActive) {
                    int idx = event.findPointerIndex(joystickPointerId);
                    if (idx >= 0) {
                        updateJoystickStick(event.getX(idx), event.getY(idx));
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                if (pointerId == joystickPointerId) {
                    joystickActive = false;
                    joystickPointerId = -1;
                    engine.stopPlayer();
                }
                break;

            case MotionEvent.ACTION_CANCEL:
                joystickActive = false;
                joystickPointerId = -1;
                engine.stopPlayer();
                break;
        }
        return true;
    }

    /**
     * 检查触摸点是否在摇杆区域
     */
    private boolean isInJoystickArea(float x, float y) {
        // 左下角区域
        float defaultCenterX = JOYSTICK_MARGIN + JOYSTICK_RADIUS;
        float defaultCenterY = screenHeight - JOYSTICK_MARGIN - JOYSTICK_RADIUS;
        float dist = (float) Math.sqrt((x - defaultCenterX) * (x - defaultCenterX) 
                + (y - defaultCenterY) * (y - defaultCenterY));
        return dist < JOYSTICK_RADIUS * 2;
    }

    /**
     * 检查触摸点是否在分裂按钮区域
     */
    private boolean isInSplitButton(float x, float y) {
        float splitX = screenWidth - BUTTON_MARGIN - BUTTON_SIZE;
        float splitY = screenHeight - BUTTON_MARGIN - BUTTON_SIZE;
        float dx = x - splitX;
        float dy = y - splitY;
        return Math.sqrt(dx*dx + dy*dy) < BUTTON_SIZE;
    }

    /**
     * 检查触摸点是否在吐球按钮区域
     */
    private boolean isInSpitButton(float x, float y) {
        float splitX = screenWidth - BUTTON_MARGIN - BUTTON_SIZE;
        float spitX = splitX - BUTTON_SIZE * 2 - 10f;
        float splitY = screenHeight - BUTTON_MARGIN - BUTTON_SIZE;
        float dx = x - spitX;
        float dy = y - splitY;
        return Math.sqrt(dx*dx + dy*dy) < BUTTON_SIZE;
    }

    /**
     * 更新摇杆把手位置
     */
    private void updateJoystickStick(float x, float y) {
        float dx = x - joystickCenterX;
        float dy = y - joystickCenterY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        
        float maxDist = JOYSTICK_RADIUS - JOYSTICK_CENTER_RADIUS;
        
        if (dist > maxDist) {
            float ratio = maxDist / dist;
            dx *= ratio;
            dy *= ratio;
        }
        
        joystickStickX = joystickCenterX + dx;
        joystickStickY = joystickCenterY + dy;
        
        // 设置玩家方向
        if (engine.gameRunning && dist > 10) {
            engine.setPlayerDirection(dx, dy);
        }
    }

    public void setOnGameOverListener(OnGameOverListener listener) {
        this.gameOverListener = listener;
    }

    public void startGame() {
        particles.clear();
        engine.setGameCallback(new GameEngine.GameCallback() {
            @Override
            public void onGameOver(int score, int killCount, float maxRadius, int eatFoodCount, float gameTime) {
                if (gameOverListener != null) {
                    post(() -> {
                        if (gameOverListener != null) {
                            gameOverListener.onGameOver(score, killCount, maxRadius, eatFoodCount, gameTime);
                        }
                    });
                }
            }
        });
        engine.eventCallback = new GameEngine.GameEventCallback() {
            @Override
            public void onFoodEaten(float x, float y, int color) {
                spawnParticles(x, y, color, 5 + (int)(Math.random() * 4), 150f);
            }
            @Override
            public void onBallEaten(float x, float y, int color, float radius) {
                spawnParticles(x, y, color, 10 + (int)(Math.random() * 6), 250f);
            }
        };
        engine.initGame();
    }

    public GameEngine getEngine() {
        return engine;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        screenWidth = getWidth();
        screenHeight = getHeight();

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

                if (deltaTime > 0.1f) {
                    deltaTime = 0.1f;
                }

                engine.update(deltaTime);

                // 更新粒子
                for (int i = particles.size() - 1; i >= 0; i--) {
                    Particle p = particles.get(i);
                    p.x += p.vx * deltaTime;
                    p.y += p.vy * deltaTime;
                    p.life -= deltaTime;
                    if (p.life <= 0) particles.remove(i);
                }

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

    private void render(Canvas canvas) {
        if (canvas == null || screenWidth <= 0 || screenHeight <= 0) return;

        // 获取视野缩放
        float viewScale = engine.getViewScale();
        
        // 计算视野范围（根据缩放调整）
        float viewWidth = screenWidth / viewScale;
        float viewHeight = screenHeight / viewScale;
        float cameraX = engine.cameraX - viewWidth / 2f;
        float cameraY = engine.cameraY - viewHeight / 2f;

        // 保存画布状态
        canvas.save();
        
        // 应用缩放
        canvas.scale(viewScale, viewScale);
        
        // 1. 背景
        canvas.drawColor(0xFF1a1a2e);

        // 2. 网格
        drawGrid(canvas, cameraX, cameraY, viewWidth, viewHeight);

        // 3. 边界
        drawBorder(canvas, cameraX, cameraY);

        // 4. 食物
        List<Food> foods = engine.getAliveFoods();
        for (Food food : foods) {
            food.draw(canvas, ballPaint, cameraX, cameraY, (int)viewWidth, (int)viewHeight);
        }

        // 5. 粒子特效
        drawParticles(canvas, cameraX, cameraY, viewWidth, viewHeight);

        // 6. AI球
        List<Ball> aiBalls = engine.getAliveAIBalls();
        for (Ball ai : aiBalls) {
            ai.draw(canvas, ballPaint, cameraX, cameraY, (int)viewWidth, (int)viewHeight);
        }

        // 7. 玩家球（包括分身）
        List<Ball> playerBalls = engine.getAlivePlayerBalls();
        for (Ball playerBall : playerBalls) {
            playerBall.draw(canvas, ballPaint, cameraX, cameraY, (int)viewWidth, (int)viewHeight);
        }
        
        // 恢复画布状态
        canvas.restore();

        // 8. 危险红边警告
        drawDangerWarning(canvas);

        // 9. HUD（不缩放）
        drawHUD(canvas);

        // 10. 排行榜（不缩放）
        drawLeaderboard(canvas);

        // 11. 小地图（不缩放）
        drawMinimap(canvas);

        // 12. 按钮（不缩放）
        drawButtons(canvas);

        // 13. 虚拟摇杆（不缩放）
        drawJoystick(canvas);
    }

    private void drawGrid(Canvas canvas, float cameraX, float cameraY, float viewWidth, float viewHeight) {
        float worldLeft = cameraX;
        float worldTop = cameraY;
        float worldRight = cameraX + viewWidth;
        float worldBottom = cameraY + viewHeight;

        int startX = (int) (worldLeft / GRID_SIZE) * GRID_SIZE;
        int startY = (int) (worldTop / GRID_SIZE) * GRID_SIZE;

        for (int x = startX; x <= worldRight; x += GRID_SIZE) {
            if (x < 0 || x > GameEngine.WORLD_WIDTH) continue;
            float screenX = x - cameraX;
            canvas.drawLine(screenX, 0, screenX, viewHeight, gridPaint);
        }

        for (int y = startY; y <= worldBottom; y += GRID_SIZE) {
            if (y < 0 || y > GameEngine.WORLD_HEIGHT) continue;
            float screenY = y - cameraY;
            canvas.drawLine(0, screenY, viewWidth, screenY, gridPaint);
        }
    }

    private void drawBorder(Canvas canvas, float cameraX, float cameraY) {
        float left = 0 - cameraX;
        float top = 0 - cameraY;
        float right = GameEngine.WORLD_WIDTH - cameraX;
        float bottom = GameEngine.WORLD_HEIGHT - cameraY;

        canvas.drawRect(left, top, right, bottom, borderPaint);
    }

    private void drawHUD(Canvas canvas) {
        // 分数
        hudPaint.setColor(0xFFFFFFFF);
        hudPaint.setTextSize(32f);
        hudPaint.setTextAlign(Paint.Align.LEFT);
        hudPaint.setFakeBoldText(false);
        canvas.drawText("分数: " + engine.totalScore, 20, 45, hudPaint);

        // 玩家体积
        if (engine.player != null && engine.player.alive) {
            hudPaint.setTextSize(22f);
            hudPaint.setColor(0xCCFFFFFF);
            canvas.drawText("体积: " + (int) engine.player.radius, 20, 72, hudPaint);
        }

        // 存活AI
        int aliveAI = engine.getAliveAIBalls().size();
        hudPaint.setTextSize(22f);
        hudPaint.setColor(0xCCFFFFFF);
        canvas.drawText("对手: " + aliveAI, 20, 97, hudPaint);

        // 游戏时间（居中显示）
        float timeLeft = Math.max(0, 300f - engine.getGameTime());
        int minutes = (int)(timeLeft / 60);
        int seconds = (int)(timeLeft % 60);
        String timeStr = String.format("%d:%02d", minutes, seconds);
        hudPaint.setTextSize(28f);
        hudPaint.setColor(timeLeft < 60 ? 0xFFFF4444 : 0xFFFFFFFF);
        hudPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(timeStr, screenWidth / 2f, 45, hudPaint);
        
        // 击杀数（右上角）
        hudPaint.setTextSize(22f);
        hudPaint.setColor(0xCCFFFFFF);
        hudPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("击杀: " + engine.killCount, screenWidth - 20, 45, hudPaint);
    }

    private void drawMinimap(Canvas canvas) {
        int minimapSize = 150;
        int padding = 15;
        int minimapX = screenWidth - minimapSize - padding;
        int minimapY = screenHeight - minimapSize - padding;

        // 背景
        minimapBgPaint.setColor(0x60000000);
        canvas.drawRect(minimapX, minimapY, minimapX + minimapSize, minimapY + minimapSize, minimapBgPaint);
        
        // 边框
        minimapPaint.setColor(0x80FFFFFF);
        minimapPaint.setStyle(Paint.Style.STROKE);
        minimapPaint.setStrokeWidth(2f);
        canvas.drawRect(minimapX, minimapY, minimapX + minimapSize, minimapY + minimapSize, minimapPaint);

        float scaleX = (float) minimapSize / GameEngine.WORLD_WIDTH;
        float scaleY = (float) minimapSize / GameEngine.WORLD_HEIGHT;

        // 食物密集区（用淡绿色小点表示，只画部分避免性能问题）
        minimapPaint.setColor(0x30004400);
        minimapPaint.setStyle(Paint.Style.FILL);
        List<Food> allFoods = engine.getAliveFoods();
        for (int i = 0; i < allFoods.size(); i += 3) {
            Food f = allFoods.get(i);
            float mx = minimapX + f.x * scaleX;
            float my = minimapY + f.y * scaleY;
            canvas.drawCircle(mx, my, 1f, minimapPaint);
        }

        // AI球
        minimapPaint.setStyle(Paint.Style.FILL);
        List<Ball> aiBalls = engine.getAliveAIBalls();
        for (Ball ai : aiBalls) {
            float mx = minimapX + ai.x * scaleX;
            float my = minimapY + ai.y * scaleY;
            minimapPaint.setColor(ai.color);
            float dotSize = Math.max(2f, ai.radius * scaleX * 0.5f);
            canvas.drawCircle(mx, my, dotSize, minimapPaint);
        }

        // 玩家球（包括分身）
        List<Ball> playerBalls = engine.getAlivePlayerBalls();
        for (Ball playerBall : playerBalls) {
            float px = minimapX + playerBall.x * scaleX;
            float py = minimapY + playerBall.y * scaleY;
            minimapPaint.setColor(playerBall.color);
            float dotSize = Math.max(3f, playerBall.radius * scaleX * 0.5f);
            canvas.drawCircle(px, py, dotSize, minimapPaint);
        }
    }

    /**
     * 生成粒子
     */
    private void spawnParticles(float x, float y, int color, int count, float speed) {
        for (int i = 0; i < count; i++) {
            Particle p = new Particle();
            p.x = x; p.y = y;
            float angle = (float)(Math.random() * Math.PI * 2);
            float spd = speed * (0.5f + (float)Math.random() * 0.5f);
            p.vx = (float)Math.cos(angle) * spd;
            p.vy = (float)Math.sin(angle) * spd;
            p.radius = 3f + (float)Math.random() * 5f;
            p.life = 0.3f + (float)Math.random() * 0.3f;
            p.maxLife = p.life;
            p.color = color;
            particles.add(p);
        }
    }

    /**
     * 绘制粒子
     */
    private void drawParticles(Canvas canvas, float cameraX, float cameraY, float viewWidth, float viewHeight) {
        for (Particle p : particles) {
            float screenX = p.x - cameraX;
            float screenY = p.y - cameraY;
            if (screenX < -20 || screenX > viewWidth + 20 || screenY < -20 || screenY > viewHeight + 20) continue;
            float alpha = p.life / p.maxLife;
            int a = (int)(alpha * 255);
            ballPaint.setColor((p.color & 0x00FFFFFF) | (a << 24));
            canvas.drawCircle(screenX, screenY, p.radius * alpha, ballPaint);
        }
    }

    /**
     * 绘制危险红边警告
     */
    private void drawDangerWarning(Canvas canvas) {
        List<Ball> playerBalls = engine.getAlivePlayerBalls();
        if (playerBalls.isEmpty()) return;
        
        float dangerLevel = 0f;
        for (Ball playerBall : playerBalls) {
            for (Ball ai : engine.getAliveAIBalls()) {
                if (ai.radius > playerBall.radius + 5f) {
                    float dx = ai.x - playerBall.x;
                    float dy = ai.y - playerBall.y;
                    float dist = (float)Math.sqrt(dx*dx + dy*dy);
                    float dangerDist = 300f;
                    if (dist < dangerDist) {
                        float level = 1f - dist / dangerDist;
                        if (level > dangerLevel) dangerLevel = level;
                    }
                }
            }
        }
        if (dangerLevel > 0.1f) {
            int alpha = (int)(dangerLevel * 100);
            Paint dangerPaint = new Paint();
            dangerPaint.setStyle(Paint.Style.FILL);
            dangerPaint.setColor((alpha << 24) | 0xFF0000);
            canvas.drawRect(0, 0, screenWidth, 30, dangerPaint); // 上
            canvas.drawRect(0, screenHeight - 30, screenWidth, screenHeight, dangerPaint); // 下
            canvas.drawRect(0, 0, 30, screenHeight, dangerPaint); // 左
            canvas.drawRect(screenWidth - 30, 0, screenWidth, screenHeight, dangerPaint); // 右
        }
    }

    /**
     * 绘制虚拟摇杆
     */
    private void drawJoystick(Canvas canvas) {
        float centerX, centerY;
        
        if (joystickActive) {
            centerX = joystickCenterX;
            centerY = joystickCenterY;
        } else {
            centerX = JOYSTICK_MARGIN + JOYSTICK_RADIUS;
            centerY = screenHeight - JOYSTICK_MARGIN - JOYSTICK_RADIUS;
        }

        // 绘制背景圆
        canvas.drawCircle(centerX, centerY, JOYSTICK_RADIUS, joystickBgPaint);

        // 绘制把手
        float stickX = joystickActive ? joystickStickX : centerX;
        float stickY = joystickActive ? joystickStickY : centerY;
        canvas.drawCircle(stickX, stickY, JOYSTICK_CENTER_RADIUS, joystickStickPaint);
    }

    /**
     * 绘制按钮（分裂和吐球）
     */
    private void drawButtons(Canvas canvas) {
        // 分裂按钮（右下角）
        float splitX = screenWidth - BUTTON_MARGIN - BUTTON_SIZE;
        float splitY = screenHeight - BUTTON_MARGIN - BUTTON_SIZE;
        buttonPaint.setColor(0x80000000);
        canvas.drawCircle(splitX, splitY, BUTTON_SIZE, buttonPaint);
        buttonPaint.setColor(0xFFFFFFFF);
        buttonPaint.setStyle(Paint.Style.STROKE);
        buttonPaint.setStrokeWidth(3f);
        canvas.drawCircle(splitX, splitY, BUTTON_SIZE, buttonPaint);
        buttonPaint.setStyle(Paint.Style.FILL);
        buttonTextPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("-", splitX, splitY + 12f, buttonTextPaint);

        // 吐球按钮（分裂按钮左边）
        float spitX = splitX - BUTTON_SIZE * 2 - 10f;
        buttonPaint.setColor(0x80000000);
        canvas.drawCircle(spitX, splitY, BUTTON_SIZE, buttonPaint);
        buttonPaint.setColor(0xFFFFFF00);
        buttonPaint.setStyle(Paint.Style.STROKE);
        canvas.drawCircle(spitX, splitY, BUTTON_SIZE, buttonPaint);
        buttonPaint.setStyle(Paint.Style.FILL);
        canvas.drawText("o", spitX, splitY + 12f, buttonTextPaint);
    }

    /**
     * 绘制排行榜
     */
    private void drawLeaderboard(Canvas canvas) {
        List<GameEngine.RankEntry> board = engine.getLeaderboard();
        if (board.isEmpty()) return;

        float boardX = screenWidth - 170f;
        float boardY = 60f;
        float lineHeight = 28f;

        // 背景
        Paint bgPaint = new Paint();
        bgPaint.setColor(0x60000000);
        bgPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(boardX - 10, boardY - 35, boardX + 160, boardY + board.size() * lineHeight + 10, bgPaint);

        // 标题
        Paint titlePaint = new Paint();
        titlePaint.setColor(0xFFFFFFFF);
        titlePaint.setTextSize(22f);
        titlePaint.setTextAlign(Paint.Align.LEFT);
        titlePaint.setAntiAlias(true);
        canvas.drawText("排行榜", boardX, boardY - 10, titlePaint);

        // 排名
        Paint rankPaint = new Paint();
        rankPaint.setTextSize(18f);
        rankPaint.setTextAlign(Paint.Align.LEFT);
        rankPaint.setAntiAlias(true);

        for (int i = 0; i < board.size(); i++) {
            GameEngine.RankEntry entry = board.get(i);
            float y = boardY + i * lineHeight + 15;

            if (entry.isPlayer) {
                rankPaint.setColor(0xFF44FF44);  // 玩家绿色高亮
            } else {
                rankPaint.setColor(0xCCFFFFFF);
            }

            String text = (i + 1) + ". " + entry.name + " " + (int)entry.radius;
            canvas.drawText(text, boardX, y, rankPaint);
        }
    }
}
