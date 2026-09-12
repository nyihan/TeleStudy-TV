#!/usr/bin/env bash
# ==============================================================================
# TeleStudy TV — TDLib Native Build Script
# Builds libtdjni.so for Android ABIs: arm64-v8a, armeabi-v7a, x86, x86_64
# Requirements:
#   - Linux, macOS, or Windows WSL2 / Git Bash
#   - Android NDK r25+ ($ANDROID_NDK_ROOT or $ANDROID_NDK_HOME)
#   - CMake 3.22+, Ninja, gperf, OpenSSL, git, g++
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
BUILD_DIR="${ROOT_DIR}/third_party/td_build"
SRC_DIR="${ROOT_DIR}/third_party/td"
OUTPUT_DIR="${ROOT_DIR}/app/src/main/jniLibs"

echo "=== TeleStudy TV: TDLib Native Build ==="

if [ -z "${ANDROID_NDK_HOME:-}" ] && [ -z "${ANDROID_NDK_ROOT:-}" ]; then
    echo "ERROR: Neither ANDROID_NDK_HOME nor ANDROID_NDK_ROOT is set."
    echo "Please export ANDROID_NDK_HOME=/path/to/android-ndk"
    exit 1
fi

NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT}}"
echo "Using Android NDK: ${NDK}"

# Clone TDLib if not present
if [ ! -d "${SRC_DIR}" ]; then
    echo "Cloning official TDLib repository..."
    mkdir -p "${ROOT_DIR}/third_party"
    git clone --depth 1 https://github.com/tdlib/td.git "${SRC_DIR}"
fi

TARGET_ABIS=("arm64-v8a" "armeabi-v7a" "x86" "x86_64")

for ABI in "${TARGET_ABIS[@]}"; do
    echo "--- Building TDLib for ABI: ${ABI} ---"
    CURRENT_BUILD_DIR="${BUILD_DIR}/${ABI}"
    mkdir -p "${CURRENT_BUILD_DIR}"
    cd "${CURRENT_BUILD_DIR}"

    cmake -GNinja \
        -DCMAKE_TOOLCHAIN_FILE="${NDK}/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="${ABI}" \
        -DANDROID_PLATFORM=26 \
        -DCMAKE_BUILD_TYPE=Release \
        -DTD_ENABLE_JNI=ON \
        "${SRC_DIR}"

    ninja tdjni

    DEST_DIR="${OUTPUT_DIR}/${ABI}"
    mkdir -p "${DEST_DIR}"
    cp -v "${CURRENT_BUILD_DIR}/libtdjni.so" "${DEST_DIR}/"
    echo "Installed libtdjni.so to ${DEST_DIR}"
done

echo "=== TDLib Native Build Completed Successfully ==="
