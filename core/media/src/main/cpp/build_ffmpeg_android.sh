#!/usr/bin/env bash
set -euo pipefail

FFMPEG_VERSION="9.0.2"
FFMPEG_SHA256="8c3850283eb25fa026482078a04051e0be17347b09ef81a0849bec15a96e002e"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"
BUILD_ROOT="${PLAYER_FFMPEG_BUILD_DIR:-${TMPDIR:-/private/tmp}/mvvm-base-ai-ffmpeg}"
NDK_ROOT="${ANDROID_NDK_ROOT:-}"

if [[ -z "$NDK_ROOT" ]]; then
    echo "Set ANDROID_NDK_ROOT to an installed Android NDK." >&2
    exit 1
fi

HOST_TAG="darwin-x86_64"
TOOLCHAIN="$NDK_ROOT/toolchains/llvm/prebuilt/$HOST_TAG"
LLVM_BIN="$TOOLCHAIN/bin"
SYSROOT="$TOOLCHAIN/sysroot"
JOBS="${PLAYER_FFMPEG_JOBS:-4}"
ARCHIVE="$BUILD_ROOT/ffmpeg-$FFMPEG_VERSION.tar.xz"
SOURCE_DIR="${PLAYER_FFMPEG_SOURCE_DIR:-$BUILD_ROOT/ffmpeg-$FFMPEG_VERSION}"
JNI_LIBS_DIR="$MODULE_DIR/src/main/jniLibs"
INCLUDE_DIR="$SCRIPT_DIR/ffmpeg/include"

mkdir -p "$BUILD_ROOT"
if [[ ! -d "$SOURCE_DIR" ]]; then
    if [[ ! -f "$ARCHIVE" ]]; then
        curl -fsSL "https://ffmpeg.org/releases/ffmpeg-$FFMPEG_VERSION.tar.xz" -o "$ARCHIVE"
    fi
    echo "$FFMPEG_SHA256  $ARCHIVE" | shasum -a 256 -c -
    tar -xf "$ARCHIVE" -C "$BUILD_ROOT"
fi

build_abi() {
    local abi="$1"
    local arch="$2"
    local cpu="$3"
    local cc="$4"
    local extra_flags="$5"
    local prefix="$BUILD_ROOT/install/$abi"
    local xml_prefix="$BUILD_ROOT/libxml2-install/$abi"
    echo "Building libxml2 and FFmpeg for $abi"
    bash "$SCRIPT_DIR/build_libxml2_android.sh" "$abi" "$BUILD_ROOT"
    cp "$BUILD_ROOT/libxml2-2.15.4/Copyright" "$SCRIPT_DIR/ffmpeg/COPYING.libxml2"
    local log="$BUILD_ROOT/build-$abi.log"

    if [[ -f "$SOURCE_DIR/ffbuild/config.mak" ]]; then
        make -C "$SOURCE_DIR" distclean
    fi

    if ! (
        cd "$SOURCE_DIR"
        PKG_CONFIG_LIBDIR="$xml_prefix/lib/pkgconfig" PKG_CONFIG_PATH="$xml_prefix/lib/pkgconfig" ./configure \
            --prefix="$prefix" \
            --target-os=android \
            --arch="$arch" \
            --cpu="$cpu" \
            --enable-cross-compile \
            --cc="$LLVM_BIN/$cc" \
            --cxx="$LLVM_BIN/${cc}++" \
            --ar="$LLVM_BIN/llvm-ar" \
            --nm="$LLVM_BIN/llvm-nm" \
            --ranlib="$LLVM_BIN/llvm-ranlib" \
            --strip="$LLVM_BIN/llvm-strip" \
            --sysroot="$SYSROOT" \
            --enable-shared \
            --disable-static \
            --enable-pic \
            --disable-programs \
            --disable-doc \
            --disable-debug \
            --disable-autodetect \
            --disable-avdevice \
            --disable-filters \
            --disable-encoders \
            --disable-muxers \
            --enable-network \
            --enable-libxml2 \
            --pkg-config="${PLAYER_PKG_CONFIG:-pkg-config}" \
            --pkg-config-flags=--static \
            --extra-cflags="-fPIC -I$xml_prefix/include -I$xml_prefix/include/libxml2 $extra_flags" \
            --extra-ldflags="-L$xml_prefix/lib -Wl,-z,max-page-size=16384" \
            > "$BUILD_ROOT/configure-$abi.log" 2>&1
        make -j"$JOBS" > "$log" 2>&1
        make install >> "$log" 2>&1
    ); then
        tail -n 100 "$BUILD_ROOT/configure-$abi.log" "$log" 2>/dev/null || true
        return 1
    fi

    mkdir -p "$JNI_LIBS_DIR/$abi"
    for component in avutil swresample swscale avcodec avformat; do
        cp "$prefix/lib/lib$component.so" "$JNI_LIBS_DIR/$abi/lib$component.so"
    done
    if [[ ! -d "$INCLUDE_DIR" ]]; then
        mkdir -p "$INCLUDE_DIR"
        cp -R "$prefix/include/." "$INCLUDE_DIR/"
    fi
}

build_abi "arm64-v8a" "aarch64" "armv8-a" "aarch64-linux-android23-clang" ""
build_abi "armeabi-v7a" "arm" "armv7-a" "armv7a-linux-androideabi23-clang" "-DANDROID_ARM_NEON=TRUE -mthumb"

cp "$SOURCE_DIR/COPYING.LGPLv2.1" "$SCRIPT_DIR/ffmpeg/COPYING.LGPLv2.1"
echo "FFmpeg $FFMPEG_VERSION built for arm64-v8a and armeabi-v7a."
