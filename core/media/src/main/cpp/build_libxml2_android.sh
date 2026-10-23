#!/usr/bin/env bash
set -euo pipefail
ABI="$1"
BUILD_ROOT="$2"
XML_VERSION="2.15.4"
XML_SHA256="98087fd181d9070724f3fbc65c7377db03038eb92bd882374daff44940138821"
XML_SOURCE="$BUILD_ROOT/libxml2-$XML_VERSION"
XML_ARCHIVE="$BUILD_ROOT/libxml2-$XML_VERSION.tar.xz"
XML_PREFIX="$BUILD_ROOT/libxml2-install/$ABI"
CMAKE_BIN="${PLAYER_CMAKE:-cmake}"
mkdir -p "$BUILD_ROOT"
if [[ ! -d "$XML_SOURCE" ]]; then
    if [[ ! -f "$XML_ARCHIVE" ]]; then
        curl -fsSL "https://download.gnome.org/sources/libxml2/2.15/libxml2-$XML_VERSION.tar.xz" -o "$XML_ARCHIVE"
    fi
    echo "$XML_SHA256  $XML_ARCHIVE" | shasum -a 256 -c -
    tar -xf "$XML_ARCHIVE" -C "$BUILD_ROOT"
fi
"$CMAKE_BIN" -S "$XML_SOURCE" -B "$BUILD_ROOT/libxml2-build/$ABI" \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_ROOT/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$ABI" -DANDROID_PLATFORM=android-23 \
    -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX="$XML_PREFIX" \
    -DCMAKE_POSITION_INDEPENDENT_CODE=ON -DBUILD_SHARED_LIBS=OFF \
    -DLIBXML2_WITH_PROGRAMS=OFF -DLIBXML2_WITH_PYTHON=OFF -DLIBXML2_WITH_TESTS=OFF \
    -DLIBXML2_WITH_ICONV=OFF -DLIBXML2_WITH_ZLIB=OFF -DLIBXML2_WITH_LZMA=OFF \
    -DLIBXML2_WITH_HTTP=OFF -DLIBXML2_WITH_FTP=OFF \
    > "$BUILD_ROOT/libxml2-configure-$ABI.log" 2>&1
"$CMAKE_BIN" --build "$BUILD_ROOT/libxml2-build/$ABI" -j "${PLAYER_FFMPEG_JOBS:-4}" \
    > "$BUILD_ROOT/libxml2-build-$ABI.log" 2>&1
"$CMAKE_BIN" --install "$BUILD_ROOT/libxml2-build/$ABI" \
    >> "$BUILD_ROOT/libxml2-build-$ABI.log" 2>&1
