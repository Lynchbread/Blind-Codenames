package userinterface;

import android.content.Context;
import android.content.res.Resources;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.net.Uri;

import java.util.Locale;

public class WordAudio
{
    public static void play_word_audio(Context context, String word)
    {
        AudioAttributes audio_attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();

        SoundPool sound_pool = new SoundPool.Builder()
                .setAudioAttributes(audio_attributes)
                .setMaxStreams(6)
                .build();

        String refactored_word = word.toLowerCase();

        // If "switch", add a 1 since that's a reserved java word.
        if (refactored_word == "switch")
            refactored_word += "1";

        Resources resources = context.getResources();

        int resourceID = resources.getIdentifier(refactored_word, "raw", context.getPackageName());

        if (resourceID == 0)
            return;

        int sound = sound_pool.load(context, resourceID, 1);

        sound_pool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            public void onLoadComplete(SoundPool soundPool, int sampleId,int status) {
                sound_pool.play(sound, 1.0f, 1.0f, 1, 0, 1.0f);
            }
        });
    }
}