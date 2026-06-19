package com.cgangel.screen_translate;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQUEST_MEDIA_PROJECTION = 1001;
    private static final int REQUEST_NOTIFICATIONS = 1002;

    private AppConfig appConfig;
    private EditText apiBaseInput;
    private EditText modelInput;
    private EditText apiKeyInput;
    private AutoCompleteTextView sourceLanguageInput;
    private AutoCompleteTextView targetLanguageInput;
    private Spinner translationEngineSpinner;
    private Spinner translationModeSpinner;
    private Spinner textLayoutModeSpinner;
    private TextView normalizedUrlText;
    private TextView permissionStatusText;
    private TextView statusText;
    private TextView modelPackEnZhStatus;
    private TextView modelPackJaZhStatus;
    private Button overlayPermissionButton;
    private Button startStopButton;
    private Button refreshModelPacksButton;
    private Button downloadEnZhButton;
    private Button deleteEnZhButton;
    private Button downloadJaZhButton;
    private Button deleteJaZhButton;
    private final LocalModelPackManager modelPackManager = new LocalModelPackManager();
    private final ExecutorService modelPackWorker = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        appConfig = new AppConfig(this);
        bindViews();
        loadSettings();
        wireEvents();
        updateNormalizedUrl();
        updatePermissionStatus();
        updateStartStopButton();
        refreshModelPackStatuses();
    }

    @Override
    protected void onDestroy() {
        modelPackWorker.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionStatus();
        updateStartStopButton();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
                serviceIntent.setAction(ScreenCaptureService.ACTION_START);
                serviceIntent.putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode);
                serviceIntent.putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data);
                serviceIntent.putExtra(ScreenCaptureService.EXTRA_MODE, appConfig.load().translationMode);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
                statusText.setText(R.string.status_translation_starting);
            } else {
                statusText.setText(R.string.status_screen_capture_denied);
            }
            updateStartStopButton();
        }
    }

    private void bindViews() {
        apiBaseInput = findViewById(R.id.apiBaseInput);
        modelInput = findViewById(R.id.modelInput);
        apiKeyInput = findViewById(R.id.apiKeyInput);
        sourceLanguageInput = findViewById(R.id.sourceLanguageInput);
        targetLanguageInput = findViewById(R.id.targetLanguageInput);
        translationEngineSpinner = findViewById(R.id.translationEngineSpinner);
        translationModeSpinner = findViewById(R.id.translationModeSpinner);
        textLayoutModeSpinner = findViewById(R.id.textLayoutModeSpinner);
        normalizedUrlText = findViewById(R.id.normalizedUrlText);
        permissionStatusText = findViewById(R.id.permissionStatusText);
        statusText = findViewById(R.id.statusText);
        modelPackEnZhStatus = findViewById(R.id.modelPackEnZhStatus);
        modelPackJaZhStatus = findViewById(R.id.modelPackJaZhStatus);
        overlayPermissionButton = findViewById(R.id.overlayPermissionButton);
        startStopButton = findViewById(R.id.startStopButton);
        refreshModelPacksButton = findViewById(R.id.refreshModelPacksButton);
        downloadEnZhButton = findViewById(R.id.downloadEnZhButton);
        deleteEnZhButton = findViewById(R.id.deleteEnZhButton);
        downloadJaZhButton = findViewById(R.id.downloadJaZhButton);
        deleteJaZhButton = findViewById(R.id.deleteJaZhButton);
    }

    private void loadSettings() {
        setupSourceLanguageInput();
        setupTargetLanguageInput();
        setupTranslationEngineSpinner();
        setupTranslationModeSpinner();
        setupTextLayoutModeSpinner();
        ApiSettings settings = appConfig.load();
        apiBaseInput.setText(settings.apiBaseUrl);
        modelInput.setText(settings.model);
        apiKeyInput.setText(settings.apiKey);
        sourceLanguageInput.setText(settings.sourceLanguage);
        targetLanguageInput.setText(settings.targetLanguage);
        translationEngineSpinner.setSelection(TranslationEngine.isLocal(settings.translationEngine) ? 1 : 0);
        translationModeSpinner.setSelection(TranslationMode.isClick(settings.translationMode) ? 1 : 0);
        textLayoutModeSpinner.setSelection(TextLayoutMode.isMultiLine(settings.textLayoutMode) ? 1 : 0);
        updateApiInputsForEngine();
    }

    private void wireEvents() {
        apiBaseInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateNormalizedUrl();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        translationEngineSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateApiInputsForEngine();
                updateNormalizedUrl();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        findViewById(R.id.saveButton).setOnClickListener(v -> {
            saveSettings();
            statusText.setText(R.string.status_settings_saved);
        });

        overlayPermissionButton.setOnClickListener(v -> {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(intent);
        });

        startStopButton.setOnClickListener(v -> {
            if (ScreenCaptureService.isServiceRunning()) {
                stopTranslationService();
            } else {
                startTranslationFlow();
            }
        });

        refreshModelPacksButton.setOnClickListener(v -> refreshModelPackStatuses());
        downloadEnZhButton.setOnClickListener(v -> downloadModelPack(
                LocalModelPackManager.EN_ZH,
                getString(R.string.pack_en_zh)
        ));
        deleteEnZhButton.setOnClickListener(v -> deleteModelPack(
                LocalModelPackManager.EN_ZH,
                getString(R.string.pack_en_zh)
        ));
        downloadJaZhButton.setOnClickListener(v -> downloadModelPack(
                LocalModelPackManager.JA_ZH,
                getString(R.string.pack_ja_zh)
        ));
        deleteJaZhButton.setOnClickListener(v -> deleteModelPack(
                LocalModelPackManager.JA_ZH,
                getString(R.string.pack_ja_zh)
        ));
    }

    private ApiSettings currentSettingsFromInputs() {
        return new ApiSettings(
                apiBaseInput.getText().toString(),
                modelInput.getText().toString(),
                apiKeyInput.getText().toString(),
                sourceLanguageInput.getText().toString(),
                targetLanguageInput.getText().toString(),
                translationModeSpinner.getSelectedItemPosition() == 1
                        ? TranslationMode.CLICK
                        : TranslationMode.REALTIME,
                translationEngineSpinner.getSelectedItemPosition() == 1
                        ? TranslationEngine.LOCAL
                        : TranslationEngine.API,
                textLayoutModeSpinner.getSelectedItemPosition() == 1
                        ? TextLayoutMode.MULTI_LINE
                        : TextLayoutMode.SINGLE_LINE
        );
    }

    private void saveSettings() {
        ApiSettings settings = currentSettingsFromInputs();
        appConfig.save(settings);
        if (targetLanguageInput.getText().toString().trim().isEmpty()) {
            targetLanguageInput.setText(settings.targetLanguage);
        }
        if (sourceLanguageInput.getText().toString().trim().isEmpty()) {
            sourceLanguageInput.setText(settings.sourceLanguage);
        }
        updateNormalizedUrl();
    }

    private void startTranslationFlow() {
        saveSettings();
        ApiSettings settings = currentSettingsFromInputs();
        if (settings.targetLanguage.trim().isEmpty()) {
            statusText.setText(R.string.status_target_language_required);
            return;
        }
        if (!SourceLanguage.isAuto(settings.sourceLanguage)
                && SourceLanguage.resolveLanguageTag(settings.sourceLanguage).isEmpty()) {
            statusText.setText(R.string.status_source_language_unsupported);
            return;
        }
        if (TranslationEngine.isLocal(settings.translationEngine)) {
            if (!LocalTranslationLanguage.hasLanguageTag(settings.targetLanguage)) {
                statusText.setText(R.string.status_local_target_unsupported);
                return;
            }
        } else {
            if (settings.normalizedBaseUrl.isEmpty()) {
                statusText.setText(R.string.status_api_base_required);
                return;
            }
            if (settings.model.trim().isEmpty()) {
                statusText.setText(R.string.status_model_required);
                return;
            }
        }
        if (!canDrawOverlays()) {
            statusText.setText(R.string.status_overlay_required);
            updatePermissionStatus();
            return;
        }
        requestNotificationPermissionIfNeeded();

        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (manager == null) {
            statusText.setText(R.string.status_projection_unavailable);
            return;
        }
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
    }

    private void stopTranslationService() {
        Intent intent = new Intent(this, ScreenCaptureService.class);
        intent.setAction(ScreenCaptureService.ACTION_STOP);
        startService(intent);
        statusText.setText(R.string.status_translation_stopped);
        updateStartStopButton();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void updateNormalizedUrl() {
        if (translationEngineSpinner != null && translationEngineSpinner.getSelectedItemPosition() == 1) {
            normalizedUrlText.setText(R.string.status_local_engine_selected);
            return;
        }
        String normalized = ApiUrlNormalizer.chatCompletionsUrl(apiBaseInput.getText().toString());
        normalizedUrlText.setText(normalized.isEmpty()
                ? ""
                : getString(R.string.status_request_url, normalized));
    }

    private void updateApiInputsForEngine() {
        boolean local = translationEngineSpinner != null
                && translationEngineSpinner.getSelectedItemPosition() == 1;
        apiBaseInput.setEnabled(!local);
        modelInput.setEnabled(!local);
        apiKeyInput.setEnabled(!local);
    }

    private void updatePermissionStatus() {
        boolean overlay = canDrawOverlays();
        permissionStatusText.setText(getString(
                R.string.permission_status,
                overlay ? getString(R.string.permission_granted) : getString(R.string.permission_missing)
        ));
        overlayPermissionButton.setEnabled(!overlay);
    }

    private void updateStartStopButton() {
        startStopButton.setText(
                ScreenCaptureService.isServiceRunning()
                        ? R.string.button_stop_translation
                        : R.string.button_start_translation
        );
    }

    private void refreshModelPackStatuses() {
        setModelPackButtonsEnabled(false);
        modelPackWorker.execute(() -> {
            try {
                boolean enZh = modelPackManager.isPackDownloaded(LocalModelPackManager.EN_ZH);
                boolean jaZh = modelPackManager.isPackDownloaded(LocalModelPackManager.JA_ZH);
                runOnUiThread(() -> {
                    updateModelPackStatus(
                            modelPackEnZhStatus,
                            getString(R.string.pack_en_zh),
                            enZh
                    );
                    updateModelPackStatus(
                            modelPackJaZhStatus,
                            getString(R.string.pack_ja_zh),
                            jaZh
                    );
                    setModelPackButtonsEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    statusText.setText(getString(R.string.status_model_pack_failed, shortError(e)));
                    setModelPackButtonsEnabled(true);
                });
            }
        });
    }

    private void downloadModelPack(TranslationModelPack pack, String label) {
        setModelPackButtonsEnabled(false);
        statusText.setText(R.string.status_model_pack_busy);
        modelPackWorker.execute(() -> {
            try {
                modelPackManager.downloadPack(pack);
                runOnUiThread(() -> {
                    statusText.setText(getString(R.string.status_model_pack_downloaded));
                    refreshModelPackStatuses();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    statusText.setText(getString(R.string.status_model_pack_failed, label + " " + shortError(e)));
                    setModelPackButtonsEnabled(true);
                });
            }
        });
    }

    private void deleteModelPack(TranslationModelPack pack, String label) {
        setModelPackButtonsEnabled(false);
        statusText.setText(R.string.status_model_pack_busy);
        modelPackWorker.execute(() -> {
            try {
                modelPackManager.deletePack(pack);
                runOnUiThread(() -> {
                    statusText.setText(getString(R.string.status_model_pack_deleted));
                    refreshModelPackStatuses();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    statusText.setText(getString(R.string.status_model_pack_failed, label + " " + shortError(e)));
                    setModelPackButtonsEnabled(true);
                });
            }
        });
    }

    private void updateModelPackStatus(TextView view, String label, boolean downloaded) {
        view.setText(getString(
                R.string.status_model_pack_line,
                label,
                getString(downloaded
                        ? R.string.status_model_pack_ready
                        : R.string.status_model_pack_missing)
        ));
    }

    private void setModelPackButtonsEnabled(boolean enabled) {
        refreshModelPacksButton.setEnabled(enabled);
        downloadEnZhButton.setEnabled(enabled);
        deleteEnZhButton.setEnabled(enabled);
        downloadJaZhButton.setEnabled(enabled);
        deleteJaZhButton.setEnabled(enabled);
    }

    private String shortError(Exception e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = e.getClass().getSimpleName();
        }
        if (message.length() > 120) {
            return message.substring(0, 120) + "...";
        }
        return message;
    }

    private void setupSourceLanguageInput() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                getResources().getStringArray(R.array.source_language_options)
        );
        sourceLanguageInput.setAdapter(adapter);
        sourceLanguageInput.setThreshold(0);
        sourceLanguageInput.setOnClickListener(v -> sourceLanguageInput.showDropDown());
        sourceLanguageInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                sourceLanguageInput.showDropDown();
            }
        });
    }

    private void setupTargetLanguageInput() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                getResources().getStringArray(R.array.target_language_options)
        );
        targetLanguageInput.setAdapter(adapter);
        targetLanguageInput.setThreshold(0);
        targetLanguageInput.setOnClickListener(v -> targetLanguageInput.showDropDown());
        targetLanguageInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                targetLanguageInput.showDropDown();
            }
        });
    }

    private void setupTranslationModeSpinner() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.translation_mode_options,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        translationModeSpinner.setAdapter(adapter);
    }

    private void setupTextLayoutModeSpinner() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.text_layout_mode_options,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        textLayoutModeSpinner.setAdapter(adapter);
    }

    private void setupTranslationEngineSpinner() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.translation_engine_options,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        translationEngineSpinner.setAdapter(adapter);
    }

}
