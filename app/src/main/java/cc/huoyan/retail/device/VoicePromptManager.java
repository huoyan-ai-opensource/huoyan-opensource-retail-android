package cc.huoyan.retail.device;

import cc.huoyan.retail.common.LocalePreferenceUtil;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;

/** Lightweight, best-effort voice prompt with per-message throttling. */
public class VoicePromptManager {
    private static final String LOG_TAG = "VoicePromptManager";
    private static final long MIN_PROMPT_INTERVAL_MS = 5000L;
    private TextToSpeech textToSpeech;
    private long lastPromptAt;
    private String lastPrompt;

    public void init(Context context) {
        if (textToSpeech != null) {
            return;
        }
        textToSpeech = new TextToSpeech(context.getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS && textToSpeech != null) {
                textToSpeech.setLanguage(LocalePreferenceUtil.getCurrentLocale(context));
            } else {
                Log.w(LOG_TAG, "TextToSpeech initialization failed: " + status);
            }
        });
    }

    public void speak(String message) {
        long now = System.currentTimeMillis();
        if (textToSpeech == null || (message.equals(lastPrompt) && now - lastPromptAt < MIN_PROMPT_INTERVAL_MS)) {
            return;
        }
        lastPrompt = message;
        lastPromptAt = now;
        textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "weighted_sale_warning");
    }

    public void release() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
    }
}
