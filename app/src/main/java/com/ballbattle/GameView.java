package com.ballbattle;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
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
    private static final int GRID_SIZE = 50;
    private static final int BORDER_WIDTH = 4;

    // 虚拟摇杆参数 - 跟随手指模式
    private static final float JOYSTICK_RADIUS = 65f;
    private static final float JOYSTICK_CENTER_RADIUS = 28f;
    private static final float JOYSTICK_MARGIN = 80f;
    private static final float JOYSTICK_STROKE_WIDTH = 3f;

    // 按钮参数
    private static final float BUTTON_SIZE = 55f;
    private static final float BUTTON_MARGIN = 15f;

    private SurfaceHolder holder;
    private GameEngine engine;
    private GameThread gameThread;
    private RankSystem rankSystem;

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

    // 残影系统
    private static class TrailPoint {
        float x, y, radius;
        float life;
        int color;
    }
    private final List<TrailPoint> trailPoints = new ArrayList<>();
    private float trailTimer = 0f;
    private static final float TRAIL_INTERVAL = 0.03f;  // 残影生成间隔

    // P4-2: 按钮动画状态
    private float splitButtonScale = 1f;
    private float spitButtonScale = 1f;

    // P4-3: 游戏开始淡入效果
    private float gameFadeAlpha = 0f;

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

        // 摇杆画笔 - 优化后更美观
        joystickBgPaint.setColor(0x40000000);
        joystickBgPaint.setStyle(Paint.Style.FILL);
        joystickBgPaint.setAntiAlias(true);

        joystickStickPaint.setColor(0xFFFFFFFF);
        joystickStickPaint.setStyle(Paint.Style.FILL);
        joystickStickPaint.setAntiAlias(true);
        // 移除 shadowLayer（可能在某些设备上不兼容）
        // joystickStickPaint.setShadowLayer(8f, 0f, 2f, 0x40000000);

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
                // 先检查按钮（优先）
                float x = event.getX(pointerIndex);
                float y = event.getY(pointerIndex);

                if (isInSplitButton(x, y)) {
                    engine.split();
                    splitButtonScale = 0.85f;
                    return true;
                }
                if (isInSpitButton(x, y)) {
                    engine.spit();
                    spitButtonScale = 0.85f;
                    return true;
                }

                // 左半屏幕触发摇杆
                if (x < screenWidth * 0.5f && !joystickActive) {
                    joystickActive = true;
                    joystickPointerId = pointerId;
                    joystickCenterX = x;  // 摇杆出现在手指位置
                    joystickCenterY = y;
                    joystickStickX = x;   // 把手也在手指位置
                    joystickStickY = y;
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
        // 屏幕左半边都是摇杆区域（排除按钮区域）
        return x < screenWidth * 0.5f;
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
        trailPoints.clear();
        trailTimer = 0f;
        // P4-3: 游戏开始时设置淡入
        gameFadeAlpha = 1f;
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
        engine.initGame(engine.currentMode);
    }

    public GameEngine getEngine() {
        return engine;
    }

    public void setRankSystem(RankSystem rs) {
        this.rankSystem = rs;
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

                // 更新残影
                SkinSystem skinSys = engine.skinSystem;
                if (skinSys != null && skinSys.hasTrail() && engine.player != null && engine.player.alive) {
                    trailTimer += deltaTime;
                    if (trailTimer >= TRAIL_INTERVAL) {
                        trailTimer = 0f;
                        TrailPoint tp = new TrailPoint();
                        tp.x = engine.player.x;
                        tp.y = engine.player.y;
                        tp.radius = engine.player.radius;
                        tp.life = 0.5f;
                        tp.color = skinSys.getSelectedTrailColor();
                        trailPoints.add(tp);
                    }
                }
                for (int i = trailPoints.size() - 1; i >= 0; i--) {
                    trailPoints.get(i).life -= deltaTime;
                    if (trailPoints.get(i).life <= 0) trailPoints.remove(i);
                }

                // P4-2: 更新按钮动画缩放
                splitButtonScale += (1f - splitButtonScale) * 0.15f;
                spitButtonScale += (1f - spitButtonScale) * 0.15f;

                // P4-3: 更新淡入效果
                if (gameFadeAlpha > 0) {
                    gameFadeAlpha -= 0.02f;
                    if (gameFadeAlpha < 0) gameFadeAlpha = 0;
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

        // 3.5 安全区（大逃杀模式）
        drawSafeZone(canvas, cameraX, cameraY, viewWidth, viewHeight);

        // 4. 食物
        List<Food> foods = engine.getAliveFoods();
        for (Food food : foods) {
            food.draw(canvas, ballPaint, cameraX, cameraY, (int)viewWidth, (int)viewHeight);
        }

        // 5. 刺球
        List<Virus> viruses = engine.getAliveViruses();
        for (Virus v : viruses) {
            v.draw(canvas, ballPaint, cameraX, cameraY, (int)viewWidth, (int)viewHeight);
        }

        // 6. 粒子特效
        drawParticles(canvas, cameraX, cameraY, viewWidth, viewHeight);

        // 7. 残影（在玩家球之前绘制）
        drawTrails(canvas, cameraX, cameraY, viewWidth, viewHeight);

        // 8. AI球
        List<Ball> aiBalls = engine.getAliveAIBalls();
        for (Ball ai : aiBalls) {
            ai.draw(canvas, ballPaint, cameraX, cameraY, (int)viewWidth, (int)viewHeight);
        }

        // 8. 玩家球（包括分身）
        List<Ball> playerBalls = engine.getAlivePlayerBalls();
        for (Ball playerBall : playerBalls) {
            // 无敌闪烁效果：每0.2秒切换
            if (playerBall.invincible) {
                if ((int)(engine.getGameTime() * 5) % 2 == 0) {
                    continue;  // 不绘制（透明效果）
                }
            }
            playerBall.draw(canvas, ballPaint, cameraX, cameraY, (int)viewWidth, (int)viewHeight);
        }

        // 9. 光环效果（在玩家球之后绘制）
        drawAuras(canvas, cameraX, cameraY, viewWidth, viewHeight);
        
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

        // 14. P4-3: 游戏开始淡入效果
        if (gameFadeAlpha > 0) {
            Paint fadePaint = new Paint();
            fadePaint.setColor((int)(gameFadeAlpha * 255) << 24 | 0x000000);
            fadePaint.setStyle(Paint.Style.FILL);
            canvas.drawRect(0, 0, screenWidth, screenHeight, fadePaint);
        }
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

    /**
     * 绘制安全区（大逃杀模式）
     */
    private void drawSafeZone(Canvas canvas, float cameraX, float cameraY, float viewWidth, float viewHeight) {
        if (engine.currentMode != GameEngine.GameMode.BATTLE_ROYALE) return;

        float screenCenterX = engine.safeZoneCenterX - cameraX;
        float screenCenterY = engine.safeZoneCenterY - cameraY;

        // 绘制安全区外区域（红色半透明遮罩）
        Paint dangerPaint = new Paint();
        dangerPaint.setColor(0x30FF0000);
        dangerPaint.setStyle(android.graphics.Paint.Style.FILL);
        canvas.drawRect(0, 0, viewWidth, viewHeight, dangerPaint);

        // 绘制安全区圆形区域（蓝色半透明覆盖，遮住红色）
        Paint safePaint = new Paint();
        safePaint.setColor(0x300044FF);
        safePaint.setStyle(android.graphics.Paint.Style.FILL);
        canvas.drawCircle(screenCenterX, screenCenterY, engine.safeZoneRadius, safePaint);

        // 绘制安全区边界线
        safePaint.setColor(0xFF0066FF);
        safePaint.setStyle(android.graphics.Paint.Style.STROKE);
        safePaint.setStrokeWidth(3f);
        canvas.drawCircle(screenCenterX, screenCenterY, engine.safeZoneRadius, safePaint);

        // 绘制下一次缩圈预览
        if (engine.targetSafeZoneRadius < engine.safeZoneRadius - 10f) {
            safePaint.setColor(0x300000FF);
            safePaint.setStyle(android.graphics.Paint.Style.STROKE);
            safePaint.setStrokeWidth(2f);
            canvas.drawCircle(screenCenterX, screenCenterY, engine.targetSafeZoneRadius, safePaint);
        }
    }

    private void drawHUD(Canvas canvas) {
        // P4-1: 左上角分数区域添加半透明黑色圆角背景
        Paint hudBgPaint = new Paint();
        hudBgPaint.setColor(0x60000000);
        hudBgPaint.setStyle(Paint.Style.FILL);
        hudBgPaint.setAntiAlias(true);
        RectF hudBgRect = new RectF(10, 10, 200, 130);
        canvas.drawRoundRect(hudBgRect, 12f, 12f, hudBgPaint);

        // 分数
        hudPaint.setColor(0xFFFFFFFF);
        hudPaint.setTextSize(30f);
        hudPaint.setTextAlign(Paint.Align.LEFT);
        hudPaint.setFakeBoldText(false);
        canvas.drawText("分数: " + engine.totalScore, 20, 42, hudPaint);

        // 玩家体积
        if (engine.player != null && engine.player.alive) {
            hudPaint.setTextSize(20f);
            hudPaint.setColor(0xCCFFFFFF);
            canvas.drawText("体积: " + (int) engine.player.radius, 20, 66, hudPaint);
        }

        // 存活AI
        int aliveAI = engine.getAliveAIBalls().size();
        hudPaint.setTextSize(20f);
        hudPaint.setColor(0xCCFFFFFF);
        canvas.drawText("对手: " + aliveAI, 20, 88, hudPaint);

        // 段位显示
        if (rankSystem != null) {
            hudPaint.setColor(rankSystem.getRankColor());
            hudPaint.setTextSize(18f);
            hudPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(rankSystem.getRankName() + " " + rankSystem.getStars() + "/" + rankSystem.getMaxStars() + "\u2605", 20, 112, hudPaint);
        }

        // 游戏时间（居中显示）- P4-1: 添加背景
        if (engine.currentMode == GameEngine.GameMode.FREE) {
            float timeLeft = Math.max(0, 300f - engine.getGameTime());
            int minutes = (int)(timeLeft / 60);
            int seconds = (int)(timeLeft % 60);
            String timeStr = String.format("%d:%02d", minutes, seconds);
            hudPaint.setTextSize(28f);
            hudPaint.setColor(timeLeft < 60 ? 0xFFFF4444 : 0xFFFFFFFF);
            hudPaint.setTextAlign(Paint.Align.CENTER);
            // 倒计时背景
            float timeTextWidth = hudPaint.measureText(timeStr);
            RectF timeBgRect = new RectF(screenWidth / 2f - timeTextWidth / 2f - 12, 14, screenWidth / 2f + timeTextWidth / 2f + 12, 50);
            Paint timeBgPaint = new Paint();
            timeBgPaint.setColor(timeLeft < 60 ? 0x60FF0000 : 0x60000000);
            timeBgPaint.setStyle(Paint.Style.FILL);
            timeBgPaint.setAntiAlias(true);
            canvas.drawRoundRect(timeBgRect, 10f, 10f, timeBgPaint);
            canvas.drawText(timeStr, screenWidth / 2f, 42, hudPaint);
        } else if (engine.currentMode == GameEngine.GameMode.SURVIVAL) {
            // 生存模式显示存活时间
            int minutes = (int)(engine.getGameTime() / 60);
            int seconds = (int)(engine.getGameTime() % 60);
            String timeStr = String.format("%d:%02d", minutes, seconds);
            hudPaint.setTextSize(28f);
            hudPaint.setColor(0xFFFFFFFF);
            hudPaint.setTextAlign(Paint.Align.CENTER);
            float timeTextWidth = hudPaint.measureText(timeStr);
            RectF timeBgRect = new RectF(screenWidth / 2f - timeTextWidth / 2f - 12, 14, screenWidth / 2f + timeTextWidth / 2f + 12, 50);
            Paint timeBgPaint = new Paint();
            timeBgPaint.setColor(0x60000000);
            timeBgPaint.setStyle(Paint.Style.FILL);
            timeBgPaint.setAntiAlias(true);
            canvas.drawRoundRect(timeBgRect, 10f, 10f, timeBgPaint);
            canvas.drawText(timeStr, screenWidth / 2f, 42, hudPaint);
        } else if (engine.currentMode == GameEngine.GameMode.BATTLE_ROYALE) {
            // 大逃杀模式显示存活人数
            String aliveStr = "存活: " + (aliveAI + 1);
            hudPaint.setTextSize(28f);
            hudPaint.setColor(0xFFFFFFFF);
            hudPaint.setTextAlign(Paint.Align.CENTER);
            float textWidth = hudPaint.measureText(aliveStr);
            RectF aliveBgRect = new RectF(screenWidth / 2f - textWidth / 2f - 12, 14, screenWidth / 2f + textWidth / 2f + 12, 50);
            Paint aliveBgPaint = new Paint();
            aliveBgPaint.setColor(0x60000000);
            aliveBgPaint.setStyle(Paint.Style.FILL);
            aliveBgPaint.setAntiAlias(true);
            canvas.drawRoundRect(aliveBgRect, 10f, 10f, aliveBgPaint);
            canvas.drawText(aliveStr, screenWidth / 2f, 42, hudPaint);
        }

        // 击杀数（右上角）
        hudPaint.setTextSize(20f);
        hudPaint.setColor(0xCCFFFFFF);
        hudPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("击杀: " + engine.killCount, screenWidth - 20, 42, hudPaint);

        // 生存模式 - 显示生命值
        if (engine.currentMode == GameEngine.GameMode.SURVIVAL) {
            hudPaint.setColor(0xFFFF4444);
            hudPaint.setTextSize(28f);
            hudPaint.setTextAlign(Paint.Align.CENTER);
            String livesStr = "";
            for (int i = 0; i < engine.lives; i++) {
                livesStr += "\u2665 ";  // 心形符号
            }
            canvas.drawText(livesStr.trim(), screenWidth / 2f, 80, hudPaint);
        }

        // 大逃杀模式 - 显示安全区倒计时
        if (engine.currentMode == GameEngine.GameMode.BATTLE_ROYALE) {
            float timeLeft = Math.max(0, 30f - engine.safeZoneTimer);
            if (timeLeft <= 5f) {
                String shrinkStr = "缩圈: " + (int)timeLeft + "s";
                hudPaint.setTextSize(20f);
                hudPaint.setColor(0xFFFF4444);
                hudPaint.setTextAlign(Paint.Align.CENTER);
                float textWidth = hudPaint.measureText(shrinkStr);
                RectF shrinkBgRect = new RectF(screenWidth / 2f - textWidth / 2f - 10, 56, screenWidth / 2f + textWidth / 2f + 10, 82);
                Paint shrinkBgPaint = new Paint();
                shrinkBgPaint.setColor(0x60FF0000);
                shrinkBgPaint.setStyle(Paint.Style.FILL);
                shrinkBgPaint.setAntiAlias(true);
                canvas.drawRoundRect(shrinkBgRect, 8f, 8f, shrinkBgPaint);
                canvas.drawText(shrinkStr, screenWidth / 2f, 76, hudPaint);
            }
        }
    }

    private void drawMinimap(Canvas canvas) {
        int minimapSize = 150;
        int padding = 15;
        int minimapX = screenWidth - minimapSize - padding;
        int minimapY = screenHeight - minimapSize - padding;

        // P4-1: 圆角边框背景
        Paint minimapRoundBgPaint = new Paint();
        minimapRoundBgPaint.setColor(0x60000000);
        minimapRoundBgPaint.setStyle(Paint.Style.FILL);
        minimapRoundBgPaint.setAntiAlias(true);
        RectF minimapRect = new RectF(minimapX, minimapY, minimapX + minimapSize, minimapY + minimapSize);
        canvas.drawRoundRect(minimapRect, 10f, 10f, minimapRoundBgPaint);

        // 圆角边框
        minimapPaint.setColor(0x80FFFFFF);
        minimapPaint.setStyle(Paint.Style.STROKE);
        minimapPaint.setStrokeWidth(2f);
        canvas.drawRoundRect(minimapRect, 10f, 10f, minimapPaint);

        // P4-1: 小地图标题"地图"
        Paint mapTitlePaint = new Paint();
        mapTitlePaint.setColor(0x80FFFFFF);
        mapTitlePaint.setTextSize(14f);
        mapTitlePaint.setTextAlign(Paint.Align.CENTER);
        mapTitlePaint.setAntiAlias(true);
        canvas.drawText("地图", minimapX + minimapSize / 2f, minimapY - 4, mapTitlePaint);

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
     * 绘制虚拟摇杆 - 跟随手指模式，不活跃时不绘制
     */
    private void drawJoystick(Canvas canvas) {
        if (!joystickActive) return;  // 不活跃时不绘制

        float centerX = joystickCenterX;
        float centerY = joystickCenterY;

        // 绘制外圈背景（半透明黑）
        canvas.drawCircle(centerX, centerY, JOYSTICK_RADIUS, joystickBgPaint);

        // 绘制外圈描边（白色半透明）
        Paint strokePaint = new Paint();
        strokePaint.setColor(0x80FFFFFF);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(JOYSTICK_STROKE_WIDTH);
        strokePaint.setAntiAlias(true);
        canvas.drawCircle(centerX, centerY, JOYSTICK_RADIUS, strokePaint);

        // 绘制内圈装饰线（虚线圆）
        Paint dashPaint = new Paint();
        dashPaint.setColor(0x30FFFFFF);
        dashPaint.setStyle(Paint.Style.STROKE);
        dashPaint.setStrokeWidth(2f);
        dashPaint.setAntiAlias(true);
        canvas.drawCircle(centerX, centerY, JOYSTICK_RADIUS * 0.6f, dashPaint);

        // 计算把手位置
        float stickX = joystickStickX;
        float stickY = joystickStickY;

        // 绘制方向指示线（从中心到把手）
        Paint linePaint = new Paint();
        linePaint.setColor(0x40FFFFFF);
        linePaint.setStrokeWidth(3f);
        linePaint.setAntiAlias(true);
        canvas.drawLine(centerX, centerY, stickX, stickY, linePaint);

        // 绘制把手外圈（灰色边框）
        Paint stickBorderPaint = new Paint();
        stickBorderPaint.setColor(0xFF888888);
        stickBorderPaint.setStyle(Paint.Style.FILL);
        stickBorderPaint.setAntiAlias(true);
        canvas.drawCircle(stickX, stickY, JOYSTICK_CENTER_RADIUS + 3f, stickBorderPaint);

        // 绘制把手主体（白色带渐变效果）
        canvas.drawCircle(stickX, stickY, JOYSTICK_CENTER_RADIUS, joystickStickPaint);

        // 绘制把手高光（左上角小圆）
        Paint highlightPaint = new Paint();
        highlightPaint.setColor(0x60FFFFFF);
        highlightPaint.setStyle(Paint.Style.FILL);
        highlightPaint.setAntiAlias(true);
        canvas.drawCircle(stickX - JOYSTICK_CENTER_RADIUS * 0.3f,
                stickY - JOYSTICK_CENTER_RADIUS * 0.3f,
                JOYSTICK_CENTER_RADIUS * 0.25f, highlightPaint);
    }

    /**
     * 绘制按钮（分裂和吐球）- P4-1优化：渐变背景+描边，P4-2：缩放动画
     */
    private void drawButtons(Canvas canvas) {
        // 分裂按钮（右下角）
        float splitX = screenWidth - BUTTON_MARGIN - BUTTON_SIZE;
        float splitY = screenHeight - BUTTON_MARGIN - BUTTON_SIZE;

        // P4-2: 应用缩放动画
        canvas.save();
        canvas.scale(splitButtonScale, splitButtonScale, splitX, splitY);

        // P4-1: 渐变背景
        buttonPaint.setColor(0x804444FF);
        canvas.drawCircle(splitX, splitY, BUTTON_SIZE, buttonPaint);
        // 描边
        buttonPaint.setColor(0xB0FFFFFF);
        buttonPaint.setStyle(Paint.Style.STROKE);
        buttonPaint.setStrokeWidth(3f);
        canvas.drawCircle(splitX, splitY, BUTTON_SIZE, buttonPaint);
        buttonPaint.setStyle(Paint.Style.FILL);
        buttonTextPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("-", splitX, splitY + 12f, buttonTextPaint);

        canvas.restore();

        // 吐球按钮（分裂按钮左边）
        float spitX = splitX - BUTTON_SIZE * 2 - 10f;

        // P4-2: 应用缩放动画
        canvas.save();
        canvas.scale(spitButtonScale, spitButtonScale, spitX, splitY);

        // P4-1: 渐变背景
        buttonPaint.setColor(0x80FFAA00);
        canvas.drawCircle(spitX, splitY, BUTTON_SIZE, buttonPaint);
        // 描边
        buttonPaint.setColor(0xB0FFFFFF);
        buttonPaint.setStyle(Paint.Style.STROKE);
        buttonPaint.setStrokeWidth(3f);
        canvas.drawCircle(spitX, splitY, BUTTON_SIZE, buttonPaint);
        buttonPaint.setStyle(Paint.Style.FILL);
        canvas.drawText("o", spitX, splitY + 12f, buttonTextPaint);

        canvas.restore();
    }

    /**
     * 绘制排行榜 - P4-1优化：圆角背景
     */
    private void drawLeaderboard(Canvas canvas) {
        List<GameEngine.RankEntry> board = engine.getLeaderboard();
        if (board.isEmpty()) return;

        float boardX = screenWidth - 170f;
        float boardY = 60f;
        float lineHeight = 28f;

        // P4-1: 圆角背景
        Paint bgPaint = new Paint();
        bgPaint.setColor(0x60000000);
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setAntiAlias(true);
        RectF boardRect = new RectF(boardX - 10, boardY - 35, boardX + 160, boardY + board.size() * lineHeight + 10);
        canvas.drawRoundRect(boardRect, 12f, 12f, bgPaint);

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

    /**
     * 绘制残影效果
     */
    private void drawTrails(Canvas canvas, float cameraX, float cameraY, float viewWidth, float viewHeight) {
        if (trailPoints.isEmpty()) return;
        for (TrailPoint tp : trailPoints) {
            float screenX = tp.x - cameraX;
            float screenY = tp.y - cameraY;
            if (screenX < -50 || screenX > viewWidth + 50 || screenY < -50 || screenY > viewHeight + 50) continue;
            float alpha = tp.life / 0.5f;
            int a = (int)(alpha * 100);
            ballPaint.setColor((tp.color & 0x00FFFFFF) | (a << 24));
            canvas.drawCircle(screenX, screenY, tp.radius * alpha, ballPaint);
        }
    }

    /**
     * 绘制光环效果
     */
    private void drawAuras(Canvas canvas, float cameraX, float cameraY, float viewWidth, float viewHeight) {
        if (engine.skinSystem == null || !engine.skinSystem.hasAura()) return;
        if (engine.player == null || !engine.player.alive) return;

        int auraColor = engine.skinSystem.getSelectedAuraColor();
        Paint auraPaint = new Paint();
        auraPaint.setColor(auraColor);
        auraPaint.setStyle(Paint.Style.STROKE);
        auraPaint.setStrokeWidth(4f);
        auraPaint.setAntiAlias(true);

        // 对所有存活的玩家球绘制光环
        List<Ball> playerBalls = engine.getAlivePlayerBalls();
        float time = engine.getGameTime();

        for (Ball playerBall : playerBalls) {
            float screenX = playerBall.x - cameraX;
            float screenY = playerBall.y - cameraY;

            // 视口剔除
            if (screenX + playerBall.radius + 30 < 0 || screenX - playerBall.radius - 30 > viewWidth
                    || screenY + playerBall.radius + 30 < 0 || screenY - playerBall.radius - 30 > viewHeight) {
                continue;
            }

            // 第一层光环 - 呼吸效果
            float auraRadius = playerBall.radius + 10f + (float)Math.sin(time * 2) * 3f;
            auraPaint.setStrokeWidth(4f);
            canvas.drawCircle(screenX, screenY, auraRadius, auraPaint);

            // 第二层光环 - 反向呼吸
            auraRadius = playerBall.radius + 18f + (float)Math.cos(time * 3) * 3f;
            auraPaint.setStrokeWidth(2f);
            canvas.drawCircle(screenX, screenY, auraRadius, auraPaint);
        }
    }
}
