#!/usr/bin/env bash
set -e
set -x

# TODO: Remove hardcoded paths

# Debug
TARGET_APP=com.ditronic.securezipnotes.dev
# Release
#TARGET_APP=com.ditronic.securezipnotes

# Install app
adb push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/$TARGET_APP
adb shell pm install --full -t -r "/data/local/tmp/$TARGET_APP"

# Install androidTest app
adb push app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk /data/local/tmp/$TARGET_APP.test
adb shell pm install --full -t -r "/data/local/tmp/$TARGET_APP.test"

# Install orchestrator app
adb push /home/felix/.gradle/caches/modules-2/files-2.1/androidx.test/orchestrator/1.2.0/e3edf4e08d2d3db127c1b56bbe405b90b64daa5/orchestrator-1.2.0.apk /data/local/tmp/androidx.test.orchestrator
adb shell pm install --full -t -r "/data/local/tmp/androidx.test.orchestrator"

# Install test services apk
adb push /home/felix/.gradle/caches/modules-2/files-2.1/androidx.test.services/test-services/1.2.0/58c9ac48a725a1a0437f4f6be352a42f60ed5a7d/test-services-1.2.0.apk /data/local/tmp/androidx.test.services
adb shell pm install --full -t -r "/data/local/tmp/androidx.test.services"


# Run the tests
adb shell 'CLASSPATH=$(pm path androidx.test.services)' app_process / androidx.test.services.shellexecutor.ShellMain am instrument -r -w -e targetInstrumentation $TARGET_APP.test/androidx.test.runner.AndroidJUnitRunner   -e debug false -e class 'com.ditronic.securezipnotes.ITest' -e clearPackageData true androidx.test.orchestrator/androidx.test.orchestrator.AndroidTestOrchestrator

