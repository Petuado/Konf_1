#!/usr/bin/env bash
# Запуск эмулятора VFS на Kotlin (Swing GUI)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [[ -x "./gradlew" ]]; then
    exec ./gradlew run
elif command -v gradle >/dev/null 2>&1; then
    exec gradle run
else
    echo "Ошибка: не найден ни ./gradlew, ни gradle в PATH" >&2
    exit 1
fi