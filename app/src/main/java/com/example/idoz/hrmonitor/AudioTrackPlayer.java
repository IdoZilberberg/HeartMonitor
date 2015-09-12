package com.example.idoz.hrmonitor;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by izilberberg on 8/28/15.
 * <p/>
 * Controls all sound playing in the app
 */
public class AudioTrackPlayer {

  private final static String TAG = AudioTrackPlayer.class.getSimpleName();

  public static enum HrAudioEnum {
    HI(0), LO(1), HIHI(2), NORMAL(3);

    private int index;

    HrAudioEnum(final int index) {
      this.index = index;
    }

    public int getIndex() {
      return index;
    }
  }

  private static final int SAMPLE_RATE = 8000;
  private static byte[] HI = generate(0.1, 2000, 2000, SAMPLE_RATE);
  private static byte[] LO = generate(0.1, 1000, 1000, SAMPLE_RATE);
  private static byte[] HIHI = generate(0.2, 2000, 3000, SAMPLE_RATE);
  private static byte[] NORMAL = generate(0.1, 2000, 1000, SAMPLE_RATE);
  private static List<byte[]> sounds;

  private boolean isInit = false;

  public AudioTrackPlayer() {
    init();
  }

  public void init() {

    if (isInit) {
      return;
    }

    sounds = new ArrayList<>(HrAudioEnum.values().length);
    sounds.add(0, HI);
    sounds.add(1, LO);
    sounds.add(2, HIHI);
    sounds.add(3, NORMAL);

    isInit = true;
  }

  public void play(final Context context, final HrAudioEnum hrAudioEnum) {
    final byte[] sound = sounds.get(hrAudioEnum.getIndex());
    Log.d(TAG, "Audio: asked to play audio track " + hrAudioEnum + ". arraylen=" + sound.length);
    final AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    final AudioTrack audioTrack = createAudioTrack(sound);
    audioTrack.setNotificationMarkerPosition(sound.length / 2);
    audioTrack.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
      @Override
      public void onMarkerReached(final AudioTrack track) {
        track.release();
      }

      @Override
      public void onPeriodicNotification(final AudioTrack track) {

      }
    });
    audioTrack.play();
  }

  AudioManager.OnAudioFocusChangeListener afChangeListener = new AudioManager.OnAudioFocusChangeListener() {
    public void onAudioFocusChange(final int focusChange) {
      if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
        // Lower the volume
      } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
        // Raise it back to normal
      }
    }
  };

  private AudioTrack createAudioTrack(final byte[] snd) {
    AudioTrack audioTrack;
    audioTrack = new AudioTrack(AudioManager.STREAM_SYSTEM,
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT, snd.length,
            AudioTrack.MODE_STATIC);
    audioTrack.write(snd, 0, snd.length);
    return audioTrack;
  }

  private static byte[] generate(final double durationsSec, final double frequencyHz1, final double frequencyHz2, final int sampleRate) {

    double dnumSamples = durationsSec * sampleRate;
    dnumSamples = Math.ceil(dnumSamples);
    int numSamples = (int) dnumSamples;
    double sample[] = new double[numSamples];
    byte generatedSnd[] = new byte[2 * numSamples];

    for (int i = 0; i < numSamples/2; ++i) {      // Fill the sample array
      sample[i] = Math.sin(frequencyHz1 * 2 * Math.PI * i / (sampleRate));
    }
    for (int i = numSamples/2; i < numSamples; ++i) {      // Fill the sample array
      sample[i] = Math.sin(frequencyHz2 * 2 * Math.PI * i / (sampleRate));
    }

    // convert to 16 bit pcm sound array
    // assumes the sample buffer is normalized.
    // convert to 16 bit pcm sound array
    // assumes the sample buffer is normalised.
    int idx = 0;
    int i = 0;

    int ramp = numSamples / 5;                                    // Amplitude ramp as a percent of sample count


    for (i = 0; i < ramp; ++i) {                                     // Ramp amplitude up (to avoid clicks)
      double dVal = sample[i];
      // Ramp up to maximum
      final short val = (short) ((dVal * 32767 * i / ramp));
      // in 16 bit wav PCM, first byte is the low order byte
      generatedSnd[idx++] = (byte) (val & 0x00ff);
      generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);
    }


    for (i = i; i < numSamples - ramp; ++i) {                        // Max amplitude for most of the samples
      double dVal = sample[i];
      // scale to maximum amplitude
      final short val = (short) ((dVal * 32767));
      // in 16 bit wav PCM, first byte is the low order byte
      generatedSnd[idx++] = (byte) (val & 0x00ff);
      generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);

    }

    for (i = i; i < numSamples; ++i) {                               // Ramp amplitude down
      double dVal = sample[i];
      // Ramp down to zero
      final short val = (short) ((dVal * 32767 * (numSamples - i) / ramp));
      // in 16 bit wav PCM, first byte is the low order byte
      generatedSnd[idx++] = (byte) (val & 0x00ff);
      generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);
    }

    Log.i(TAG, "Audio: generated audio buffer with size " + generatedSnd.length + ", dur=" + durationsSec + "s, freq=" +
            frequencyHz1+"->"+frequencyHz2+"Hz, sampleRate=" + sampleRate);

    return generatedSnd;
  }


}
