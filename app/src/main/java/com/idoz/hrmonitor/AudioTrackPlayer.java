package com.idoz.hrmonitor;

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

  // See http://abletonop.com/downloads/articles/abletonop-notes-and-freqs-table.pdf
  private static final int SAMPLE_RATE = 8000;

  private static final double NO = 0.0;
  private static final double B4 = 493.88;
  private static final double C5 = 523.25;
  private static final double D5 = 587.33;
  private static final double C6 = 1046.50;
  private static final double D6 = 1174.66;
  private static final double C7 = 2093.00;
  private static final double E6 = 1318.51;
  private static final double E7 = 2637.02;
  private static byte[] HI = generate(0.05, C6, NO, D6);
  private static byte[] LO = generate(0.05, D5, NO, C5);
  private static byte[] HIHI = generate(0.05, D6, E6, E7, D6, E6, E7);
  private static byte[] NORMAL = generate(0.05, C5, B4, C5);
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

  public void play(final HrAudioEnum hrAudioEnum) {
    final byte[] sound = sounds.get(hrAudioEnum.getIndex());
    Log.d(TAG, "Audio: asked to play audio track " + hrAudioEnum + ". arraylen=" + sound.length);
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

  private AudioTrack createAudioTrack(final byte[] snd) {
    AudioTrack audioTrack;
    audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT, snd.length,
            AudioTrack.MODE_STATIC);
    audioTrack.write(snd, 0, snd.length);
    return audioTrack;
  }

  private static byte[] generate(final double durationsInSecondsPerSection, final double ... frequencies) {

    final double dnumSamplesPerSection = Math.ceil(durationsInSecondsPerSection * SAMPLE_RATE);
    final int numSamplesPerSection = (int) dnumSamplesPerSection;
    final int totalSamples = numSamplesPerSection * frequencies.length;
    final double sample[] = new double[totalSamples];
    final byte generatedSnd[] = new byte[2 * totalSamples];


    int index=0;
    for (double frequency : frequencies) {
      Log.d(TAG, "Writing freq " + frequency + " for " + numSamplesPerSection + " samples.");
      for (int i = 0; i < numSamplesPerSection; i++) {      // Fill the sample array
        sample[index++] = Math.sin(frequency * 2 * Math.PI * i / (SAMPLE_RATE));
      }
    }

    // convert to 16 bit pcm sound array
    // assumes the sample buffer is normalised.
    int idx = 0;
    int i = 0;

//    int ramp = totalSamples / 20; // Amplitude ramp as a percent of sample count
    final int ramp = SAMPLE_RATE / 50; // 1/50s for ramp up

    for (i = 0; i < ramp; ++i) { // Ramp amplitude up (to avoid clicks)
      double dVal = sample[i];
      // Ramp up to maximum
      final short val = (short) ((dVal * 32767 * i / ramp));
      // in 16 bit wav PCM, first byte is the low order byte
      generatedSnd[idx++] = (byte) (val & 0x00ff);
      generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);
    }


    for (i = i; i < totalSamples - ramp; ++i) { // Max amplitude for most of the samples
      double dVal = sample[i];
      // scale to maximum amplitude
      final short val = (short) ((dVal * 32767));
      // in 16 bit wav PCM, first byte is the low order byte
      generatedSnd[idx++] = (byte) (val & 0x00ff);
      generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);

    }

    for (i = i; i < totalSamples; ++i) { // Ramp amplitude down
      double dVal = sample[i];
      // Ramp down to zero
      final short val = (short) ((dVal * 32767 * (totalSamples - i) / ramp));
      // in 16 bit wav PCM, first byte is the low order byte
      generatedSnd[idx++] = (byte) (val & 0x00ff);
      generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);
    }

    Log.d(TAG, "Audio: generated audio buffer with size " + generatedSnd.length);

    return generatedSnd;
  }


}
