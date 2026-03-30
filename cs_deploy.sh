#!/bin/bash

param="$1"

if [ -z "$param" ]; then
  echo "Usage: $0 <name>"
  exit 1
fi

currentdir="$(pwd)"

./gradlew ":$param:make"

adb push "$currentdir/$param/build/$param.cs3" /sdcard/cloudstream3/plugins

adb shell am force-stop com.lagradost.cloudstream3.prerelease
adb shell monkey -p com.lagradost.cloudstream3.prerelease -c android.intent.category.LAUNCHER 1