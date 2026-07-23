cmake_minimum_required(VERSION 3.22.1)
project(quietstudio_native)

set(CMAKE_CXX_STANDARD 17)

# Vendored whisper.cpp (see vendor/README). Examples/tests are disabled;
# only libwhisper + ggml are built.
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

add_subdirectory(vendor/whisper.cpp EXCLUDE_FROM_ALL)

add_library(whisper_jni SHARED whisper_jni.cpp)

target_link_libraries(whisper_jni whisper log android)
