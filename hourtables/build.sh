#!/bin/bash

./pull.sh
rm -rf build/
./gradlew installDist || (echo 'Project build failed' && exit 1)
./build/install/hourtables/bin/hourtables -up &> latest.log
