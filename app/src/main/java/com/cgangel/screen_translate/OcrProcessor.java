package com.cgangel.screen_translate;

import android.graphics.Bitmap;
import android.graphics.Rect;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class OcrProcessor {
    private final List<RecognizerEntry> recognizers = new ArrayList<>();

    public OcrProcessor() {
        recognizers.add(new RecognizerEntry(
                "en",
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        ));
        recognizers.add(new RecognizerEntry(
                "zh",
                TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build())
        ));
        recognizers.add(new RecognizerEntry(
                "ja",
                TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build())
        ));
        recognizers.add(new RecognizerEntry(
                "ko",
                TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build())
        ));
    }

    public List<OcrLine> recognize(Bitmap bitmap) {
        return recognize(bitmap, SourceLanguage.AUTO_LABEL);
    }

    public List<OcrLine> recognize(Bitmap bitmap, String sourceLanguage) {
        List<OcrLine> rawLines = new ArrayList<>();
        if (bitmap == null || bitmap.isRecycled()) {
            return rawLines;
        }
        String sourceTag = SourceLanguage.resolveLanguageTag(sourceLanguage);
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        for (RecognizerEntry entry : recognizers) {
            if (!sourceTag.isEmpty() && !sourceTag.equals(entry.languageTag)) {
                continue;
            }
            try {
                Text text = Tasks.await(entry.recognizer.process(image), 8, TimeUnit.SECONDS);
                extractLines(text, rawLines);
            } catch (Exception ignored) {
                // A single script recognizer can fail without making the whole capture useless.
            }
        }
        return OcrLineMerger.mergeAndSort(rawLines);
    }

    public void close() {
        for (RecognizerEntry entry : recognizers) {
            try {
                entry.recognizer.close();
            } catch (Exception ignored) {
            }
        }
        recognizers.clear();
    }

    private void extractLines(Text text, List<OcrLine> out) {
        if (text == null) {
            return;
        }
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                Rect box = line.getBoundingBox();
                if (box != null) {
                    out.add(new OcrLine("", line.getText(), box.left, box.top, box.right, box.bottom));
                }
            }
        }
    }

    private static class RecognizerEntry {
        final String languageTag;
        final TextRecognizer recognizer;

        RecognizerEntry(String languageTag, TextRecognizer recognizer) {
            this.languageTag = languageTag;
            this.recognizer = recognizer;
        }
    }
}
