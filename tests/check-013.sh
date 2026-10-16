#!/data/data/com.termux/files/usr/bin/sh
# Compatibility entrypoint: checks the current source tree.
set -eu
exec sh "$(dirname "$0")/check-015.sh"
