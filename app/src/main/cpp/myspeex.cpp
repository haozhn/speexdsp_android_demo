#include <jni.h>
#include <string>
#include <speex/speex_preprocess.h>
#include <speex/speex_echo.h>
#include <android/log.h>
#include "echo_control_mobile_impl.h"
#include "audio_processing.h"
#include "audio_buffer.h"

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "haozhinan", __VA_ARGS__)
#define NUM_REVERSE_CHANNELS 1
#define NUM_OUTPUT_CHANNELS  1

#define RETURN_ON_ERR(expr) \
  do {                      \
    int err = (expr);       \
    if (err != webrtc::AudioProcessing::kNoError) {  \
      return err;           \
    }                       \
  } while (0)

SpeexPreprocessState *state = nullptr;
SpeexEchoState *echoState = nullptr;
std::unique_ptr<webrtc::EchoControlMobileImpl> echo_control_mobile;



//static void QueueNonbandedRenderAudio(webrtc::AudioBuffer* audio) {
////    ResidualEchoDetector::PackRenderAudioBuffer(audio, &red_render_queue_buffer_);
////    __android_log_print(ANDROID_LOG_ERROR, "haozhinan", "%s", "QueueNonbandedRenderAudio");
//    // Insert the samples into the queue.
//    if (!red_render_signal_queue_->Insert(&red_render_queue_buffer_)) {
//        // The data queue is full and needs to be emptied.
//        EmptyQueuedRenderAudio();
//
//        // Retry the insert (should always work).
//        bool result = red_render_signal_queue_->Insert(&red_render_queue_buffer_);
//        RTC_DCHECK(result);
//    }
//}

static int ProcessRenderStreamLocked() {
//    webrtc::AudioBuffer* render_buffer = render_.render_audio.get();  // For brevity.

//    HandleRenderRuntimeSettings();


//    QueueNonbandedRenderAudio(render_buffer);

//    if (submodule_states_.RenderMultiBandSubModulesActive() &&
//        SampleRateSupportsMultiBand(
//                formats_.render_processing_format.sample_rate_hz())) {
//        render_buffer->SplitIntoFrequencyBands();
//    }
//
//    if (submodule_states_.RenderMultiBandSubModulesActive()) {
//        QueueBandedRenderAudio(render_buffer);
//    }
//
//    // TODO(peah): Perform the queuing inside QueueRenderAudiuo().
//    if (submodules_.echo_controller) {
//        submodules_.echo_controller->AnalyzeRender(render_buffer);
//    }
//
//    if (submodule_states_.RenderMultiBandProcessingActive() &&
//        SampleRateSupportsMultiBand(
//                formats_.render_processing_format.sample_rate_hz())) {
//        render_buffer->MergeFrequencyBands();
//    }
//
//    return kNoError;
}

static int ProcessReverseStream(const int16_t* const src,
                                const webrtc::StreamConfig& input_config,
                                const webrtc::StreamConfig& output_config,
                                int16_t* const dest) {
    LOGE("%s", "ProcessReverseStream_AudioFrame");

    if (input_config.num_channels() <= 0) {
        return webrtc::AudioProcessing::Error::kBadNumberChannelsError;
    }

//    MutexLock lock(&mutex_render_);
//    ProcessingConfig processing_config = formats_.api_format;
//    processing_config.reverse_input_stream().set_sample_rate_hz(
//            input_config.sample_rate_hz());
//    processing_config.reverse_input_stream().set_num_channels(
//            input_config.num_channels());
//    processing_config.reverse_output_stream().set_sample_rate_hz(
//            output_config.sample_rate_hz());
//    processing_config.reverse_output_stream().set_num_channels(
//            output_config.num_channels());
//
//    RETURN_ON_ERR(MaybeInitializeRender(processing_config));
//    if (input_config.num_frames() !=
//        formats_.api_format.reverse_input_stream().num_frames()) {
//        return kBadDataLengthError;
//    }

//    if (aec_dump_) {
//        aec_dump_->WriteRenderStreamMessage(src, input_config.num_frames(),
//                                            input_config.num_channels());
//    }

    render_.render_audio->CopyFrom(src, input_config);
    RETURN_ON_ERR(ProcessRenderStreamLocked());
    if (submodule_states_.RenderMultiBandProcessingActive() ||
        submodule_states_.RenderFullBandProcessingActive()) {
        render_.render_audio->CopyTo(output_config, dest);
    }
    return webrtc::AudioProcessing::kNoError;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_gloomyer_myspeex_interfaces_SpeexJNIBridge_init(JNIEnv *env, jclass type,
                                                         jint frame_size, jint sampling_rate) {

    frame_size /= 2;
    int filterLength = frame_size * 20;

    //降噪初始化
    state = speex_preprocess_state_init(frame_size, sampling_rate);
    int i = 1;
    speex_preprocess_ctl(state, SPEEX_PREPROCESS_SET_DENOISE, &i); //降噪 建议1
    i = -25;
    speex_preprocess_ctl(state, SPEEX_PREPROCESS_SET_NOISE_SUPPRESS, &i);////设置噪声的dB
    i = 1;
    speex_preprocess_ctl(state, SPEEX_PREPROCESS_SET_AGC, &i);////增益
    i = 24000;
    speex_preprocess_ctl(state, SPEEX_PREPROCESS_SET_AGC_LEVEL, &i);
    i = 0;
    speex_preprocess_ctl(state, SPEEX_PREPROCESS_SET_DEREVERB, &i);
    float f = 0;
    speex_preprocess_ctl(state, SPEEX_PREPROCESS_SET_DEREVERB_DECAY, &f);
    f = 0;
    speex_preprocess_ctl(state, SPEEX_PREPROCESS_SET_DEREVERB_LEVEL, &f);

    //回音消除初始化
    echoState = speex_echo_state_init(frame_size, filterLength);
    speex_echo_ctl(echoState, SPEEX_ECHO_SET_SAMPLING_RATE, &sampling_rate);

    //关联
    speex_preprocess_ctl(state, SPEEX_PREPROCESS_SET_ECHO_STATE, echoState);

    echo_control_mobile.reset(new webrtc::EchoControlMobileImpl());
    echo_control_mobile->Initialize(sampling_rate,
                                    NUM_REVERSE_CHANNELS,
                                    NUM_OUTPUT_CHANNELS);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_gloomyer_myspeex_interfaces_SpeexJNIBridge_destory(JNIEnv *env, jclass type) {
    if (state) {
        speex_preprocess_state_destroy(state);
        state = nullptr;
    }

    if (echoState) {
        speex_echo_state_destroy(echoState);
        echoState = nullptr;
    }
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_gloomyer_myspeex_interfaces_SpeexJNIBridge_denoise(JNIEnv *env, jclass jcls,
                                                            jbyteArray buffer_) {
    jbyte *in_buffer = env->GetByteArrayElements(buffer_, nullptr);

    auto *in = (short *) in_buffer;
    int rcode = speex_preprocess_run(state, in);

    env->ReleaseByteArrayElements(buffer_, in_buffer, 0);
    return rcode;
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_gloomyer_myspeex_interfaces_SpeexJNIBridge_cancellation(JNIEnv *env, jclass jcls,
                                                                 jbyteArray inBuffer_,
                                                                 jbyteArray playBuffer_) {
    jbyte *inBuffer = env->GetByteArrayElements(inBuffer_, nullptr);
    jbyte *playBuffer = env->GetByteArrayElements(playBuffer_, nullptr);

    jsize length = env->GetArrayLength(playBuffer_);
    jbyteArray retBuffer_ = env->NewByteArray(length);
    jbyte *retBuffer = env->GetByteArrayElements(retBuffer_, nullptr);

    auto *in = (short *) inBuffer;
    auto *play = (short *) playBuffer;
    auto *ret = (short *) retBuffer;
    speex_echo_cancellation(echoState, in, play, ret); //消除回声
    speex_preprocess_run(state, ret);

    env->ReleaseByteArrayElements(inBuffer_, inBuffer, 0);
    env->ReleaseByteArrayElements(playBuffer_, playBuffer, 0);
    env->ReleaseByteArrayElements(retBuffer_, retBuffer, 0);
    return retBuffer_;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_gloomyer_myspeex_interfaces_SpeexJNIBridge_echoPlayback(JNIEnv *env, jclass clazz,
                                                                 jbyteArray play_buffer) {
    jbyte *playBuffer = env->GetByteArrayElements(play_buffer, nullptr);
    auto *play = (short *) playBuffer;
    speex_echo_playback(echoState, play);

    env->ReleaseByteArrayElements(play_buffer, playBuffer, 0);
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_gloomyer_myspeex_interfaces_SpeexJNIBridge_echoCapture(JNIEnv *env, jclass clazz,
                                                                jbyteArray capture_buffer) {
    jbyte *inBuffer = env->GetByteArrayElements(capture_buffer, nullptr);
    jsize length = env->GetArrayLength(capture_buffer);
    jbyteArray retBuffer_ = env->NewByteArray(length);
    jbyte *retBuffer = env->GetByteArrayElements(retBuffer_, nullptr);

    auto *in = (short *) inBuffer;
    auto *ret = (short *) retBuffer;
    speex_echo_capture(echoState, in, ret);

    env->ReleaseByteArrayElements(capture_buffer, inBuffer, 0);
    env->ReleaseByteArrayElements(retBuffer_, retBuffer, 0);
    return retBuffer_;
}