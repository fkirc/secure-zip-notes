#!/usr/bin/env bash
set -e
set -x

# This is just an experimental script to capture some commands.
# Real testing is performed via gradle tasks or Android Studio.

# Debug
TARGET_APP=com.ditronic.securezipnotes.dev
# Release
#TARGET_APP=com.ditronic.securezipnotes

WHICH_TESTS='com.ditronic.securezipnotes.tests.ITest#deleteLastNote'
ORCHESTRATOR_VERSION='1.2.0'

# Install app
adb push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/$TARGET_APP
adb shell pm install --full -t -r "/data/local/tmp/$TARGET_APP"

# Install androidTest app
adb push app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk /data/local/tmp/$TARGET_APP.test
adb shell pm install --full -t -r "/data/local/tmp/$TARGET_APP.test"

# Install orchestrator app
adb push ~/.gradle/caches/modules-2/files-2.1/androidx.test/orchestrator/$ORCHESTRATOR_VERSION/e3edf4e08d2d3db127c1b56bbe405b90b64daa5/orchestrator-$ORCHESTRATOR_VERSION.apk /data/local/tmp/androidx.test.orchestrator
adb shell pm install --full -t -r "/data/local/tmp/androidx.test.orchestrator"

# Install test services apk
adb push ~/.gradle/caches/modules-2/files-2.1/androidx.test.services/test-services/$ORCHESTRATOR_VERSION/58c9ac48a725a1a0437f4f6be352a42f60ed5a7d/test-services-$ORCHESTRATOR_VERSION.apk /data/local/tmp/androidx.test.services
adb shell pm install --full -t -r "/data/local/tmp/androidx.test.services"

# Run the tests
adb shell 'CLASSPATH=$(pm path androidx.test.services)' app_process / androidx.test.services.shellexecutor.ShellMain am instrument -r -w -e targetInstrumentation $TARGET_APP.test/androidx.test.runner.AndroidJUnitRunner -e debug false -e class $WHICH_TESTS -e clearPackageData true androidx.test.orchestrator/androidx.test.orchestrator.AndroidTestOrchestrator
