package com.gloomyer.myspeex;

import android.Manifest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.gloomyer.myspeex.interfaces.SpeexJNIBridge;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    //来源：麦克风
    private final static int AUDIO_INPUT = MediaRecorder.AudioSource.MIC;
    // 采样率
    // 44100是目前的标准，但是某些设备仍然支持22050，16000，11025
    // 采样频率一般共分为22.05KHz、44.1KHz、48KHz三个等级
    private final static int AUDIO_SAMPLE_RATE = 16000;
    // 音频通道 单声道
    private final static int AUDIO_CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private final static int AUDIO_CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO;
    // 音频格式：PCM编码
    private final static int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    AudioRecord mAudioRecord;
    AudioTrack mAudioTrack;
    volatile boolean isRecord;
    static final int BUFFER_SIZE = 1024;
    int outBufferSize;
//    byte[] buffer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 123);

        int trackBufferSize = AudioTrack.getMinBufferSize(AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_OUT, AUDIO_FORMAT);
        int recordBufferSize = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE, AUDIO_CHANNEL, AUDIO_FORMAT);

        Log.e("haozhinan", "trackBufferSize:" + trackBufferSize + ",recordBufferSize" + recordBufferSize);
    }

    public void normal(View view) {
        record(false);
    }

    public void speex(View view) {
        record(true);
    }

    public void stop(View view) {
        isRecord = false;
        mAudioRecord.stop();
        mAudioRecord.release();
    }


    /**
     * 是否启用jni层处理功能
     *
     * @param denoise
     */
    private void record(boolean denoise) {
        if (isRecord) {
            Toast.makeText(this, "请先停止", Toast.LENGTH_SHORT).show();
            return;
        }
//        bufferSize = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE,
//                AUDIO_CHANNEL, AUDIO_FORMAT);
//        mAudioRecord = new AudioRecord(AUDIO_INPUT, AUDIO_SAMPLE_RATE,
//                AUDIO_CHANNEL, AUDIO_FORMAT, bufferSize);
//        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, AUDIO_SAMPLE_RATE,
//                AUDIO_CHANNEL, AUDIO_FORMAT, bufferSize, AudioTrack.MODE_STREAM);
//
//        if (denoise) {
//            SpeexJNIBridge.init(bufferSize, AUDIO_SAMPLE_RATE);
//        }
//
//        buffer = new byte[bufferSize];
//        if (mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
//            mAudioRecord = null;
//            Toast.makeText(this, "初始化失败!", Toast.LENGTH_SHORT).show();
//            return;
//        }
//
//        mAudioRecord.startRecording();
//        mAudioTrack.play();
//        new Thread(new RecordTask(denoise)).start();
    }

    public void aecFromFile(View view) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
//                    final int minBufferSize = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE, AUDIO_CHANNEL, AUDIO_FORMAT);
                    int processSize = 640;
                    SpeexJNIBridge.init(processSize, AUDIO_SAMPLE_RATE);
                    FileInputStream misFis = new FileInputStream(new File(getFilesDir(), "mix_1280.pcm"));
                    FileInputStream pureFis = new FileInputStream(new File(getFilesDir(), "tts_1280.pcm"));
                    FileOutputStream fos = new FileOutputStream(new File(getFilesDir(), "file_aec.pcm"));

                    byte[] mixBuffer = new byte[processSize];
                    byte[] pureBuffer = new byte[processSize];
                    int mixLen = -1;
                    int pureLen = -1;
                    while ((mixLen = misFis.read(mixBuffer)) != -1 && (pureLen = pureFis.read(pureBuffer)) != -1) {
                        if (mixLen == mixBuffer.length && pureLen == pureBuffer.length) {
                            byte[] out = SpeexJNIBridge.cancellation(mixBuffer, pureBuffer);
                            fos.write(out);
                        } else {
                            Log.e("haozhinan", "aec from file else");
                        }
                    }
                    fos.flush();
                    Log.e("haozhinan", "aec from file finish");
                } catch (Exception e) {
                    Log.e("haozhinan", "aec from file error");
                }
            }
        }).start();
    }


    public void aec(View view) {
        if (isRecord) {
            Toast.makeText(this, "请先停止", Toast.LENGTH_SHORT).show();
            return;
        }


        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final int processSize = 2048;
                    SpeexJNIBridge.init(processSize, AUDIO_SAMPLE_RATE);
                    FileInputStream pureFis = new FileInputStream(new File(getFilesDir(), "tts.pcm"));
                    FileOutputStream fos = new FileOutputStream(new File(getFilesDir(), "speex_aec_1.pcm"));
//                    FileOutputStream originFos = new FileOutputStream(new File(getFilesDir(), "origin_single.pcm"));
                    mAudioRecord = new AudioRecord(AUDIO_INPUT, AUDIO_SAMPLE_RATE,
                            AUDIO_CHANNEL, AUDIO_FORMAT, 2 * processSize);

                    AudioTrack trackPlayer = new AudioTrack(AudioManager.STREAM_MUSIC, 16000,
                            AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, 2 * processSize,
                            AudioTrack.MODE_STREAM);
                    if (mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                        mAudioRecord = null;
                        Log.e("haozhinan", "初始化失败!");
                        return;
                    }
                    byte[] recordBuffer = new byte[processSize];
                    byte[] playBuffer = new byte[processSize];
                    mAudioRecord.startRecording();
                    trackPlayer.play();
                    isRecord = true;
                    while (isRecord) {
                        int len = pureFis.read(playBuffer);
                        trackPlayer.write(playBuffer, 0, len);
                        mAudioRecord.read(recordBuffer, 0, recordBuffer.length);
                        if (len > 0) {
//                            originFos.write(recordBuffer);
                            byte[] outBuffer = SpeexJNIBridge.cancellation(recordBuffer, playBuffer);
                            fos.write(outBuffer);
                        } else {
                            fos.write(recordBuffer);
//                            originFos.write(recordBuffer);
                        }
                    }
                    fos.flush();
//                    originFos.flush();
                    trackPlayer.release();
                    trackPlayer = null;
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e("haozhinan", "record error");
                }
            }
        }).start();
    }

    public void aecAsync(View view) {
        if (isRecord) {
            Toast.makeText(this, "请先停止", Toast.LENGTH_SHORT).show();
            return;
        }
        final int processSize = 1280;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    FileOutputStream fos = new FileOutputStream(new File(getFilesDir(), "speex_aec_async.pcm"));
                    FileOutputStream originFos = new FileOutputStream(new File(getFilesDir(), "origin_async.pcm"));
                    SpeexJNIBridge.init(processSize, AUDIO_SAMPLE_RATE);
                    mAudioRecord = new AudioRecord(AUDIO_INPUT, AUDIO_SAMPLE_RATE,
                            AUDIO_CHANNEL, AUDIO_FORMAT, processSize);
                    if (mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                        mAudioRecord = null;
                        Log.e("haozhinan", "初始化失败!");
                        return;
                    }
                    byte[] recordBuffer = new byte[processSize];
                    mAudioRecord.startRecording();
                    isRecord = true;
                    while (isRecord) {
                        int len = mAudioRecord.read(recordBuffer, 0, recordBuffer.length);
                        if (len <= 0) {
                            Log.e("haozhinan", "read data error");
                        }
                        originFos.write(recordBuffer);
                        byte[] outBuffer = SpeexJNIBridge.echoCapture(recordBuffer);
                        fos.write(outBuffer);
                    }
                    fos.flush();
                    originFos.flush();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    FileInputStream pureFis = new FileInputStream(new File(getFilesDir(), "tts.pcm"));
                    AudioTrack trackPlayer = new AudioTrack(AudioManager.STREAM_MUSIC, 16000,
                            AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, processSize,
                            AudioTrack.MODE_STREAM);
                    byte[] playBuffer = new byte[processSize];
                    trackPlayer.play();
                    int len = -1;
                    while((len = pureFis.read(playBuffer)) != -1) {
                        trackPlayer.write(playBuffer, 0, len);
                        SpeexJNIBridge.echoPlayback(playBuffer);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void delay(View view) {
        final int processSize = 1024;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    FileInputStream pureFis = new FileInputStream(new File(getFilesDir(), "tts.pcm"));
                    FileOutputStream mixFos = new FileOutputStream(new File(getFilesDir(), "mix_1024.pcm"));
                    FileOutputStream ttsFos = new FileOutputStream(new File(getFilesDir(), "tts_1024.pcm"));
                    AudioRecord audioRecord = new AudioRecord(AUDIO_INPUT, AUDIO_SAMPLE_RATE,
                            AUDIO_CHANNEL, AUDIO_FORMAT, processSize);
                    if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                        audioRecord = null;
                        Log.e("haozhinan", "初始化失败!");
                        return;
                    }

                    AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 16000,
                            AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, processSize,
                            AudioTrack.MODE_STREAM);
                    byte[] recordBuffer = new byte[processSize];
                    byte[] playBuffer = new byte[processSize];

                    audioRecord.startRecording();
                    audioTrack.play();
                    int len = -1;
                    while ((len = pureFis.read(playBuffer)) != -1) {
                        if (len == playBuffer.length) {
                            audioTrack.write(playBuffer, 0, len);
                            audioRecord.read(recordBuffer, 0, recordBuffer.length);
                            ttsFos.write(playBuffer);
                            mixFos.write(recordBuffer);
                        }
                    }
                    mixFos.flush();
                    ttsFos.flush();
                    audioRecord.release();
                    audioRecord = null;
                    audioTrack.release();
                    audioTrack = null;
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e("haozhinan", "delay error");
                }
            }
        }).start();

    }


    private class RecordTask implements Runnable {

        private final boolean denoise;

        public RecordTask(boolean denoise) {
            this.denoise = denoise;

        }

        @Override
        public void run() {
//            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
//            isRecord = true;
//            while (isRecord) {
//                int read = mAudioRecord.read(buffer, 0, bufferSize);
//                if (read >= 2) {
//                    if (denoise) {
//                        SpeexJNIBridge.denoise(buffer);
//                    }
//                    mAudioTrack.write(buffer, 0, read);
//                }
//            }
//
//            if (denoise)
//                SpeexJNIBridge.destory();
        }
    }
}
