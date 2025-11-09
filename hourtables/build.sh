#!/bin/bash

./pull.sh
rm -rf build/
./gradlew installDist || (echo 'Project build failed' && exit 1)
./build/install/hourtables/bin/hourtables -up 2>&1 | tee latest.log
