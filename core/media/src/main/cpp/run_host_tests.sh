#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"
TEST_BUILD_DIR="$(mktemp -d "${TMPDIR:-/tmp}/ffmpeg-player-tests.XXXXXX")"
trap 'rm -rf "$TEST_BUILD_DIR"' EXIT
"${CXX:-c++}" -std=c++17 -pthread -Wall -Wextra -Werror -fsanitize=address,undefined -g \
    "$SCRIPT_DIR/tests/playback_support_test.cpp" -o "$TEST_BUILD_DIR/playback-tests"
"$TEST_BUILD_DIR/playback-tests"
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
"$JAVAC" -d "$TEST_BUILD_DIR/java" \
    "$MODULE_DIR/src/main/java/com/will/mvvmbase/core/media/internal/AudioOutputState.java" \
    "$MODULE_DIR/src/test/java/com/will/mvvmbase/core/media/internal/AudioOutputStateTest.java"
"$JAVA" -ea -cp "$TEST_BUILD_DIR/java" com.will.mvvmbase.core.media.internal.AudioOutputStateTest
