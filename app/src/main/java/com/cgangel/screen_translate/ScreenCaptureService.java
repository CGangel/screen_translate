package com.cgangel.screen_translate;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScreenCaptureService extends Service {
    public static final String ACTION_START = "com.cgangel.screen_translate.action.START";
    public static final String ACTION_STOP = "com.cgangel.screen_translate.action.STOP";
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    public static final String EXTRA_MODE = "mode";

    private static final String CHANNEL_ID = "screen_translate_capture";
    private static final int NOTIFICATION_ID = 24;
    private static final long CAPTURE_INTERVAL_MS = 1000;
    private static final long OVERLAY_HIDE_SETTLE_MS = 70;
    private static final int MAX_CAPTURE_LONG_EDGE = 1600;
    private static final int MAX_OCR_LONG_EDGE = 1600;
    private static volatile boolean running;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            r.run();
        }, "screen-translate-worker");
        thread.setDaemon(false);
        return thread;
    });
    private final AtomicBoolean processing = new AtomicBoolean(false);

    private MediaProjection mediaProjection;
    private MediaProjection.Callback projectionCallback;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private OverlayWindow overlayWindow;
    private FloatingTranslateWindow floatingTranslateWindow;
    private FloatingTranslateWindow floatingOriginalWindow;
    private OcrProcessor ocrProcessor;
    private OpenAiCompatClient openAiClient;
    private LocalMlKitTranslator localTranslator;
    private TranslationCache translationCache;
    private int captureWidth;
    private int captureHeight;
    private int screenDensity;
    private int lastBitmapWidth;
    private int lastBitmapHeight;
    private String lastFrameHash = "";
    private String lastTextHash = "";
    private String lastSettingsFingerprint = "";
    private String translationMode = TranslationMode.REALTIME;
    private volatile boolean forceTranslateCurrentCapture;
    private volatile boolean realtimeTranslationActive;
    private boolean stopping;

    private final Runnable captureRunnable = this::startCaptureWork;

    public static boolean isServiceRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        overlayWindow = new OverlayWindow(this);
        floatingTranslateWindow = new FloatingTranslateWindow(this);
        floatingOriginalWindow = new FloatingTranslateWindow(
                this,
                R.string.overlay_button_original,
                OverlayStyle.ORIGINAL_BUTTON_COLOR,
                286
        );
        ocrProcessor = new OcrProcessor();
        openAiClient = new OpenAiCompatClient();
        localTranslator = new LocalMlKitTranslator();
        translationCache = new TranslationCache(80);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_START.equals(action)) {
            translationMode = TranslationMode.normalize(intent.getStringExtra(EXTRA_MODE));
            startForegroundCompat();
            Intent resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            if (resultData == null || resultCode == 0) {
                overlayWindow.showStatus(getString(R.string.service_capture_permission_missing));
                stopSelf();
                return START_NOT_STICKY;
            }
            if (setupProjection(resultCode, resultData)) {
                running = true;
                if (TranslationMode.isClick(translationMode)) {
                    floatingTranslateWindow.show(v -> requestManualCapture());
                } else {
                    realtimeTranslationActive = false;
                    floatingTranslateWindow.show(v -> startRealtimeTranslation());
                    floatingOriginalWindow.show(v -> pauseRealtimeTranslation());
                    overlayWindow.showStatus(getString(R.string.service_realtime_waiting));
                }
            } else {
                overlayWindow.showStatus(getString(R.string.service_unable_to_start));
                stopSelf();
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        running = false;
        stopping = true;
        mainHandler.removeCallbacksAndMessages(null);
        releaseProjection();
        if (overlayWindow != null) {
            overlayWindow.dismiss();
        }
        if (floatingTranslateWindow != null) {
            floatingTranslateWindow.dismiss();
        }
        if (floatingOriginalWindow != null) {
            floatingOriginalWindow.dismiss();
        }
        if (ocrProcessor != null) {
            ocrProcessor.close();
        }
        if (localTranslator != null) {
            localTranslator.close();
        }
        worker.shutdownNow();
        super.onDestroy();
    }

    private void scheduleNextCapture(long delayMs) {
        if (!running
                || mediaProjection == null
                || stopping
                || TranslationMode.isClick(translationMode)
                || !realtimeTranslationActive) {
            return;
        }
        mainHandler.removeCallbacks(captureRunnable);
        mainHandler.postDelayed(captureRunnable, delayMs);
    }

    private void requestManualCapture() {
        if (!running || mediaProjection == null || stopping) {
            return;
        }
        if (processing.get()) {
            showProgressStatus(getString(R.string.service_processing));
            return;
        }
        forceTranslateCurrentCapture = true;
        startCaptureWork();
    }

    private void startCaptureWork() {
        if (!TranslationMode.isClick(translationMode) && !realtimeTranslationActive) {
            processing.set(false);
            return;
        }
        if (!processing.compareAndSet(false, true)) {
            scheduleNextCapture(CAPTURE_INTERVAL_MS);
            return;
        }
        if (!ensureProjectionMatchesDisplay()) {
            processing.set(false);
            showStatus(getString(R.string.service_unable_to_start));
            scheduleNextCapture(CAPTURE_INTERVAL_MS);
            return;
        }
        hideOverlaysForCapture();
        mainHandler.postDelayed(
                () -> worker.execute(this::captureOcrAndTranslate),
                OVERLAY_HIDE_SETTLE_MS
        );
    }

    private void startRealtimeTranslation() {
        if (!running || mediaProjection == null || stopping || TranslationMode.isClick(translationMode)) {
            return;
        }
        realtimeTranslationActive = true;
        forceTranslateCurrentCapture = true;
        showTransientStatus(getString(R.string.service_realtime_started));
        if (processing.get()) {
            showProgressStatus(getString(R.string.service_processing));
            return;
        }
        startCaptureWork();
    }

    private void pauseRealtimeTranslation() {
        if (TranslationMode.isClick(translationMode)) {
            return;
        }
        realtimeTranslationActive = false;
        forceTranslateCurrentCapture = false;
        mainHandler.removeCallbacks(captureRunnable);
        lastTextHash = "";
        mainHandler.post(() -> {
            if (overlayWindow != null) {
                overlayWindow.clear();
            }
        });
    }

    private boolean setupProjection(int resultCode, Intent resultData) {
        try {
            configureCaptureSize();
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            if (manager == null) {
                return false;
            }
            mediaProjection = manager.getMediaProjection(resultCode, resultData);
            if (mediaProjection == null) {
                return false;
            }
            projectionCallback = new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    if (stopping) {
                        return;
                    }
                    mainHandler.post(() -> {
                        if (overlayWindow != null) {
                            overlayWindow.showStatus(getString(R.string.service_screen_capture_stopped));
                        }
                        stopSelf();
                    });
                }
            };
            mediaProjection.registerCallback(projectionCallback, mainHandler);
            imageReader = ImageReader.newInstance(
                    captureWidth,
                    captureHeight,
                    PixelFormat.RGBA_8888,
                    2
            );
            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "ScreenTranslate",
                    captureWidth,
                    captureHeight,
                    screenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(),
                    null,
                    mainHandler
            );
            return true;
        } catch (OutOfMemoryError e) {
            System.gc();
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean ensureProjectionMatchesDisplay() {
        if (mediaProjection == null) {
            return false;
        }
        int oldWidth = captureWidth;
        int oldHeight = captureHeight;
        configureCaptureSize();
        if (imageReader != null
                && virtualDisplay != null
                && oldWidth == captureWidth
                && oldHeight == captureHeight) {
            return true;
        }
        try {
            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
            }
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
            imageReader = ImageReader.newInstance(
                    captureWidth,
                    captureHeight,
                    PixelFormat.RGBA_8888,
                    2
            );
            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "ScreenTranslate",
                    captureWidth,
                    captureHeight,
                    screenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(),
                    null,
                    mainHandler
            );
            lastFrameHash = "";
            lastTextHash = "";
            return true;
        } catch (OutOfMemoryError e) {
            System.gc();
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private void configureCaptureSize() {
        screenDensity = getResources().getConfiguration().densityDpi;
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        int realWidth = 1;
        int realHeight = 1;
        if (windowManager != null && windowManager.getDefaultDisplay() != null) {
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
            realWidth = metrics.widthPixels;
            realHeight = metrics.heightPixels;
        }
        CaptureSize captureSize = CaptureSize.fitWithin(realWidth, realHeight, MAX_CAPTURE_LONG_EDGE);
        captureWidth = captureSize.width;
        captureHeight = captureSize.height;
    }

    private void captureOcrAndTranslate() {
        boolean forceTranslate = forceTranslateCurrentCapture;
        forceTranslateCurrentCapture = false;
        boolean frameAcquired = false;
        Bitmap bitmap = null;
        try {
            bitmap = acquireBitmap();
            frameAcquired = true;
            restoreOverlay();
            if (bitmap == null) {
                showProgressStatus(getString(R.string.service_no_frame));
                return;
            }
            lastBitmapWidth = bitmap.getWidth();
            lastBitmapHeight = bitmap.getHeight();
            if (isMostlyBlack(bitmap)) {
                bitmap.recycle();
                showStatus(getString(R.string.service_capture_blocked));
                return;
            }

            ApiSettings settings = new AppConfig(this).load();
            String settingsFingerprint = settingsFingerprint(settings);
            boolean settingsChanged = !settingsFingerprint.equals(lastSettingsFingerprint);
            if (settingsChanged) {
                lastFrameHash = "";
                lastTextHash = "";
                lastSettingsFingerprint = settingsFingerprint;
            }
            if (!SourceLanguage.isAuto(settings.sourceLanguage)
                    && SourceLanguage.resolveLanguageTag(settings.sourceLanguage).isEmpty()) {
                bitmap.recycle();
                showStatus(getString(R.string.status_source_language_unsupported));
                return;
            }

            String frameHash = ImageFingerprint.hash(bitmap);
            if (frameHash.equals(lastFrameHash) && !forceTranslate) {
                bitmap.recycle();
                restoreOverlay();
                return;
            }
            lastFrameHash = frameHash;

            List<OcrLine> lines = recognizeLines(bitmap, settings.sourceLanguage);
            lines = filterOverlayLines(lines, bitmap.getWidth(), bitmap.getHeight());
            bitmap.recycle();
            bitmap = null;
            if (lines.isEmpty()) {
                lastTextHash = "";
                showProgressStatus(getString(R.string.service_no_text));
                return;
            }

            String textHash = TextHash.hashLines(lines);
            if (textHash.equals(lastTextHash) && !forceTranslate) {
                restoreOverlay();
                return;
            }
            lastTextHash = textHash;

            if (!settings.canTranslate()) {
                showStatus(TranslationEngine.isLocal(settings.translationEngine)
                        ? getString(R.string.status_local_target_unsupported)
                        : getString(R.string.service_missing_api_config));
                return;
            }
            String cacheKey = TranslationCache.key(settings, textHash);
            List<TranslationResult> cached = translationCache.get(cacheKey);
            if (cached != null) {
                if (!shouldShowCaptureResult()) {
                    return;
                }
                showTranslations(lines, cached);
                return;
            }

            showProgressStatus(getString(R.string.service_translating));
            List<TranslationResult> translated;
            if (TranslationEngine.isLocal(settings.translationEngine)) {
                showProgressStatus(getString(R.string.service_local_model_notice));
                translated = localTranslator.translate(settings.sourceLanguage, settings.targetLanguage, lines);
            } else {
                translated = openAiClient.translate(settings, lines);
            }
            translationCache.put(cacheKey, translated);
            if (!shouldShowCaptureResult()) {
                return;
            }
            showTranslations(lines, translated);
        } catch (OutOfMemoryError e) {
            System.gc();
            showTransientStatus(shortError(e));
        } catch (Exception e) {
            showTransientStatus(shortError(e));
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            if (!frameAcquired) {
                restoreOverlay();
            }
            processing.set(false);
            restoreFloatingButtons();
            scheduleNextCapture(CAPTURE_INTERVAL_MS);
        }
    }

    private Bitmap acquireBitmap() {
        if (imageReader == null) {
            return null;
        }
        Image image = null;
        try {
            for (int i = 0; i < 3; i++) {
                image = imageReader.acquireLatestImage();
                if (image != null) {
                    break;
                }
                Thread.sleep(35);
            }
            if (image == null) {
                return null;
            }
            Image.Plane[] planes = image.getPlanes();
            if (planes.length == 0) {
                return null;
            }
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = Math.max(0, rowStride - pixelStride * image.getWidth());
            int bitmapWidth = image.getWidth() + rowPadding / pixelStride;
            Bitmap padded = Bitmap.createBitmap(bitmapWidth, image.getHeight(), Bitmap.Config.ARGB_8888);
            padded.copyPixelsFromBuffer(buffer);
            if (bitmapWidth == image.getWidth()) {
                return padded;
            }
            Bitmap cropped = Bitmap.createBitmap(padded, 0, 0, image.getWidth(), image.getHeight());
            padded.recycle();
            return cropped;
        } catch (OutOfMemoryError e) {
            System.gc();
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    private List<OcrLine> recognizeLines(Bitmap bitmap, String sourceLanguage) {
        int sourceWidth = bitmap.getWidth();
        int sourceHeight = bitmap.getHeight();
        int longEdge = Math.max(sourceWidth, sourceHeight);
        Bitmap ocrBitmap = bitmap;
        if (longEdge > MAX_OCR_LONG_EDGE) {
            float scale = (float) MAX_OCR_LONG_EDGE / (float) longEdge;
            int targetWidth = Math.max(1, Math.round(sourceWidth * scale));
            int targetHeight = Math.max(1, Math.round(sourceHeight * scale));
            ocrBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
        }

        List<OcrLine> lines = ocrProcessor.recognize(ocrBitmap, sourceLanguage);
        if (ocrBitmap != bitmap) {
            float scaleX = (float) sourceWidth / (float) ocrBitmap.getWidth();
            float scaleY = (float) sourceHeight / (float) ocrBitmap.getHeight();
            for (int i = 0; i < lines.size(); i++) {
                lines.set(i, CoordinateMapper.scaleLine(lines.get(i), scaleX, scaleY));
            }
            ocrBitmap.recycle();
        }
        return lines;
    }

    private boolean shouldShowCaptureResult() {
        return TranslationMode.isClick(translationMode) || realtimeTranslationActive;
    }

    private List<OcrLine> filterOverlayLines(List<OcrLine> lines, int sourceWidth, int sourceHeight) {
        if (lines == null || lines.isEmpty()) {
            return lines;
        }
        ArrayList<OcrLine> exclusions = new ArrayList<>();
        if (floatingTranslateWindow != null) {
            OcrLine translateExclusion =
                    floatingTranslateWindow.captureExclusionLine(sourceWidth, sourceHeight);
            if (translateExclusion != null && translateExclusion.area() > 0) {
                exclusions.add(translateExclusion);
            }
        }
        if (floatingOriginalWindow != null && !TranslationMode.isClick(translationMode)) {
            OcrLine originalExclusion =
                    floatingOriginalWindow.captureExclusionLine(sourceWidth, sourceHeight);
            if (originalExclusion != null && originalExclusion.area() > 0) {
                exclusions.add(originalExclusion);
            }
        }
        if (exclusions.isEmpty()) {
            return lines;
        }
        return OcrLineFilter.excludeOverlayLines(
                lines,
                exclusions,
                getString(R.string.overlay_button_translate)
        );
    }

    private String settingsFingerprint(ApiSettings settings) {
        return settings.translationEngine
                + "\n"
                + settings.normalizedBaseUrl
                + "\n"
                + settings.model
                + "\n"
                + settings.sourceLanguage
                + "\n"
                + settings.targetLanguage;
    }

    private boolean isMostlyBlack(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int samples = 0;
        int dark = 0;
        int stepX = Math.max(1, width / 20);
        int stepY = Math.max(1, height / 20);
        for (int y = 0; y < height; y += stepY) {
            for (int x = 0; x < width; x += stepX) {
                int color = bitmap.getPixel(x, y);
                int brightness = (Color.red(color) + Color.green(color) + Color.blue(color)) / 3;
                if (brightness < 8) {
                    dark++;
                }
                samples++;
            }
        }
        return samples > 0 && ((double) dark / samples) > 0.985;
    }

    private void showStatus(String message) {
        mainHandler.post(() -> {
            if (overlayWindow != null) {
                overlayWindow.showStatus(message);
            }
        });
    }

    private void showTransientStatus(String message) {
        mainHandler.post(() -> {
            if (overlayWindow != null) {
                overlayWindow.showTransientStatus(message);
            }
        });
    }

    private void showProgressStatus(String message) {
        if (!TranslationMode.isClick(translationMode)
                && overlayWindow != null
                && overlayWindow.hasTranslations()) {
            return;
        }
        showTransientStatus(message);
    }

    private void restoreOverlay() {
        mainHandler.post(() -> {
            if (overlayWindow != null) {
                overlayWindow.restore();
            }
            restoreFloatingButtonsOnMainThread();
        });
    }

    private void restoreFloatingButtons() {
        mainHandler.post(this::restoreFloatingButtonsOnMainThread);
    }

    private void restoreFloatingButtonsOnMainThread() {
        if (floatingTranslateWindow != null) {
            floatingTranslateWindow.restore();
        }
        if (floatingOriginalWindow != null && !TranslationMode.isClick(translationMode)) {
            floatingOriginalWindow.restore();
        }
    }

    private void hideOverlaysForCapture() {
        mainHandler.post(() -> {
            if (overlayWindow != null) {
                overlayWindow.hideForCapture();
            }
            if (floatingTranslateWindow != null) {
                floatingTranslateWindow.hideForCapture();
            }
            if (floatingOriginalWindow != null && !TranslationMode.isClick(translationMode)) {
                floatingOriginalWindow.hideForCapture();
            }
        });
    }

    private void showTranslations(List<OcrLine> lines, List<TranslationResult> translations) {
        mainHandler.post(() -> {
            if (overlayWindow != null) {
                overlayWindow.showTranslations(lines, translations, lastBitmapWidth, lastBitmapHeight);
            }
        });
    }

    private void releaseProjection() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (mediaProjection != null) {
            if (projectionCallback != null) {
                try {
                    mediaProjection.unregisterCallback(projectionCallback);
                } catch (Exception ignored) {
                }
                projectionCallback = null;
            }
            mediaProjection.stop();
            mediaProjection = null;
        }
    }

    private void startForegroundCompat() {
        createNotificationChannel();
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(TranslationMode.isClick(translationMode)
                        ? R.string.notification_click_text
                        : R.string.notification_realtime_text))
                .setOngoing(true)
                .addAction(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        getString(R.string.notification_stop_action),
                        stopPendingIntent()
                );
        Notification notification = builder.build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private PendingIntent stopPendingIntent() {
        Intent stopIntent = new Intent(this, ScreenCaptureService.class);
        stopIntent.setAction(ACTION_STOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getService(this, 0, stopIntent, flags);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
        );
        manager.createNotificationChannel(channel);
    }

    private String shortError(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = throwable.getClass().getSimpleName();
        }
        if (message.length() > 180) {
            return message.substring(0, 180) + "...";
        }
        return message;
    }
}
