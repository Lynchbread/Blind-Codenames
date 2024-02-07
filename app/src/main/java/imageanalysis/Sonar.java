package imageanalysis;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import com.example.android_codenames_repo.R;


public class Sonar
{
    private double poll;
    private boolean polled;
    private int ping;
    private AudioAttributes audio_attributes;
    private SoundPool sound_pool;

    public Sonar(Context con)
    {
        polled = false;
        poll = 9999;

        audio_attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        sound_pool = new SoundPool.Builder()
                .setAudioAttributes(audio_attributes)
                .setMaxStreams(6)
                .build();

        ping = sound_pool.load(con, R.raw.ping,1);
    }

    //Returns sound based on position
    public void ping_sonar(double dist)
    {
        float volume = (float)(1.0 - dist * 3);

        if (volume < -1.0f)
            volume = 0.00f;
        else if (volume < 0.0f)
            volume = 0.01f;

        if (dist < 0.03)
            sound_pool.play(ping, 1.0f, 1.0f, 1, 0, 1.0f);

        if (polled == false && dist < 0.5)
        {
            polled = true;
            sound_pool.play(ping, volume, volume, 1, 0, 1.0f);
            poll = (int)(dist * 50 + 3);
        }

        if (poll == 0)
            polled = false;

        if(polled == true && dist < 0.5)
            poll--;
    }
}


