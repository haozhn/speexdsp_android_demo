cmake_minimum_required(VERSION 3.4.1)

add_library(myspeex SHARED
        src/main/cpp/myspeex.cpp
        src/main/cpp/delay_estimator.cc
        src/main/cpp/delay_estimator_wrapper.cc
        src/main/cpp/ring_buffer.c
        src/main/cpp/aecm_core.cc
        src/main/cpp/real_fft.c
        src/main/cpp/echo_control_mobile.cc
        src/main/cpp/dot_product_with_scale.cc
        src/main/cpp/spl_sqrt_floor.c
        src/main/cpp/complex_fft.c
        src/main/cpp/min_max_operations.c
        src/main/cpp/spl_init.c
        src/main/cpp/division_operations.c
        src/main/cpp/complex_bit_reverse.c
        src/main/cpp/cross_correlation.c
        src/main/cpp/downsample_fast.c
        src/main/cpp/vector_scaling_operations.c
        src/main/cpp/aecm_core_c.cc
        src/main/cpp/randomization_functions.c
        src/main/cpp/echo_control_mobile_impl.cc
        src/main/cpp/audio_util.cc
        src/main/cpp/channel_buffer.cc
        src/main/cpp/audio_buffer.cc
        src/main/cpp/aligned_malloc.cc
        src/main/cpp/sinc_resampler.cc
        src/main/cpp/push_sinc_resampler.cc
        src/main/cpp/three_band_filter_bank.cc
        src/main/cpp/splitting_filter.cc
        src/main/cpp/splitting_filter.c
        src/main/cpp/audio_converter.cc
        )

target_include_directories(myspeex PRIVATE ${CMAKE_SOURCE_DIR}/src/main/cpp/include/ ${CMAKE_SOURCE_DIR}/src/main/cpp/)

add_library(speexdsp SHARED IMPORTED)
set_target_properties(speexdsp PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/src/main/jniLibs/${CMAKE_ANDROID_ARCH_ABI}/libspeexdsp.so)

find_library(log-lib log)

target_link_libraries(myspeex ${log-lib} speexdsp)