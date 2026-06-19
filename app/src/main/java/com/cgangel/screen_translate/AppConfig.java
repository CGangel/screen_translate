package com.cgangel.screen_translate;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class AppConfig {
    private static final String PREFS_NAME = "screen_translate_settings";
    private static final String KEY_API_BASE = "api_base_url";
    private static final String KEY_MODEL = "model";
    private static final String KEY_SOURCE_LANGUAGE = "source_language";
    private static final String KEY_TARGET_LANGUAGE = "target_language";
    private static final String KEY_TRANSLATION_MODE = "translation_mode";
    private static final String KEY_TRANSLATION_ENGINE = "translation_engine";
    private static final String KEY_TEXT_LAYOUT_MODE = "text_layout_mode";
    private static final String KEY_API_KEY_ENCRYPTED = "api_key_encrypted";
    private static final String KEYSTORE_ALIAS = "screen_translate_api_key";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";

    private final Context appContext;

    public AppConfig(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public ApiSettings load() {
        SharedPreferences prefs = prefs();
        return new ApiSettings(
                prefs.getString(KEY_API_BASE, ""),
                prefs.getString(KEY_MODEL, ""),
                decryptApiKey(prefs.getString(KEY_API_KEY_ENCRYPTED, "")),
                prefs.getString(KEY_SOURCE_LANGUAGE, SourceLanguage.AUTO_LABEL),
                prefs.getString(KEY_TARGET_LANGUAGE, "\u7b80\u4f53\u4e2d\u6587"),
                prefs.getString(KEY_TRANSLATION_MODE, TranslationMode.REALTIME),
                prefs.getString(KEY_TRANSLATION_ENGINE, TranslationEngine.API),
                prefs.getString(KEY_TEXT_LAYOUT_MODE, TextLayoutMode.MULTI_LINE)
        );
    }

    public void save(ApiSettings settings) {
        SharedPreferences.Editor editor = prefs().edit()
                .putString(KEY_API_BASE, settings.apiBaseUrl)
                .putString(KEY_MODEL, settings.model)
                .putString(KEY_SOURCE_LANGUAGE, settings.sourceLanguage)
                .putString(KEY_TARGET_LANGUAGE, settings.targetLanguage)
                .putString(KEY_TRANSLATION_MODE, settings.translationMode)
                .putString(KEY_TRANSLATION_ENGINE, settings.translationEngine)
                .putString(KEY_TEXT_LAYOUT_MODE, settings.textLayoutMode);
        if (settings.apiKey.isEmpty()) {
            editor.remove(KEY_API_KEY_ENCRYPTED);
        } else {
            editor.putString(KEY_API_KEY_ENCRYPTED, encryptApiKey(settings.apiKey));
        }
        editor.apply();
    }

    private SharedPreferences prefs() {
        return appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private String encryptApiKey(String value) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey());
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(iv, Base64.NO_WRAP)
                    + ":"
                    + Base64.encodeToString(encrypted, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to encrypt API key", e);
        }
    }

    private String decryptApiKey(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return "";
        }
        try {
            String[] parts = encoded.split(":", 2);
            if (parts.length != 2) {
                return "";
            }
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] encrypted = Base64.decode(parts[1], Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private SecretKey getOrCreateSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEYSTORE_ALIAS, null);
        }
        KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
        );
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setUnlockedDeviceRequired(false);
        }
        keyGenerator.init(builder.build());
        return keyGenerator.generateKey();
    }
}
