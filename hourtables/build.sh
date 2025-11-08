#!/bin/bash

./pull.sh
rm -rf build/
./gradlew installDist || (echo 'Project build failed' && exit 1)
(cd build/install/hourtables/bin && ./hourtables -up)
