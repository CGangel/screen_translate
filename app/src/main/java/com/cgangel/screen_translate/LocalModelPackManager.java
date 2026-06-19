package com.cgangel.screen_translate;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.TranslateRemoteModel;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class LocalModelPackManager {
    public static final TranslationModelPack EN_ZH =
            new TranslationModelPack("en_zh", "en", "zh");
    public static final TranslationModelPack JA_ZH =
            new TranslationModelPack("ja_zh", "ja", "zh");
    public static final List<TranslationModelPack> PRESET_PACKS =
            Arrays.asList(EN_ZH, JA_ZH);

    private static final int MODEL_OPERATION_TIMEOUT_SECONDS = 120;

    private final RemoteModelManager modelManager = RemoteModelManager.getInstance();

    public boolean isPackDownloaded(TranslationModelPack pack) throws Exception {
        return isLanguageDownloaded(pack.sourceTag) && isLanguageDownloaded(pack.targetTag);
    }

    public void downloadPack(TranslationModelPack pack) throws Exception {
        DownloadConditions conditions = new DownloadConditions.Builder().build();
        Tasks.await(
                modelManager.download(modelFor(pack.sourceTag), conditions),
                MODEL_OPERATION_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        );
        Tasks.await(
                modelManager.download(modelFor(pack.targetTag), conditions),
                MODEL_OPERATION_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        );
    }

    public void deletePack(TranslationModelPack pack) throws Exception {
        deleteLanguageIfDownloaded(pack.sourceTag);
        if (!isTargetUsedByAnotherDownloadedPack(pack.targetTag, pack.id)) {
            deleteLanguageIfDownloaded(pack.targetTag);
        }
    }

    private boolean isTargetUsedByAnotherDownloadedPack(String targetTag, String excludedPackId)
            throws Exception {
        for (TranslationModelPack pack : PRESET_PACKS) {
            if (!pack.id.equals(excludedPackId)
                    && pack.targetTag.equals(targetTag)
                    && isLanguageDownloaded(pack.sourceTag)) {
                return true;
            }
        }
        return false;
    }

    private boolean isLanguageDownloaded(String tag) throws Exception {
        return Tasks.await(
                modelManager.isModelDownloaded(modelFor(tag)),
                MODEL_OPERATION_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        );
    }

    private void deleteLanguageIfDownloaded(String tag) throws Exception {
        if (!isLanguageDownloaded(tag)) {
            return;
        }
        Tasks.await(
                modelManager.deleteDownloadedModel(modelFor(tag)),
                MODEL_OPERATION_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        );
    }

    private TranslateRemoteModel modelFor(String tag) {
        String language = TranslateLanguage.fromLanguageTag(tag);
        if (language == null) {
            throw new IllegalArgumentException("Unsupported local model language: " + tag);
        }
        return new TranslateRemoteModel.Builder(language).build();
    }
}
