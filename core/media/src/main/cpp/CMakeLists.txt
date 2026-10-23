cmake_minimum_required(VERSION 3.22.1)
project(ffmpeg_player LANGUAGES C CXX)

set(FFMPEG_ABI_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")
set(FFMPEG_INCLUDE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/ffmpeg/include")

foreach(component IN ITEMS avutil swresample swscale avcodec avformat)
    set(component_soname "lib${component}.so")

    add_library(${component} SHARED IMPORTED GLOBAL)
    set_target_properties(${component} PROPERTIES
        IMPORTED_LOCATION "${FFMPEG_ABI_DIR}/${component_soname}"
        IMPORTED_SONAME "${component_soname}"
    )
endforeach()

add_library(ffmpeg_player SHARED ffmpeg_player.cpp)
target_include_directories(ffmpeg_player PRIVATE "${FFMPEG_INCLUDE_DIR}")
target_compile_features(ffmpeg_player PRIVATE cxx_std_17)
target_compile_options(ffmpeg_player PRIVATE -Wall -Wextra -Werror=return-type)
target_link_options(ffmpeg_player PRIVATE -Wl,-z,max-page-size=16384)

find_library(android-lib android)
find_library(log-lib log)
find_library(z-lib z)
find_library(m-lib m)
find_library(dl-lib dl)

target_link_libraries(ffmpeg_player
    avformat
    avcodec
    swscale
    swresample
    avutil
    ${android-lib}
    ${log-lib}
    ${z-lib}
    ${m-lib}
    ${dl-lib}
)
