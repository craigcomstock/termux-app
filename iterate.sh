set -e
set -x
./gradlew build
bash reinstall.sh
adb shell logcat -c
adb shell logcat | grep -i termux
