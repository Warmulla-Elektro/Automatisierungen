#!/bin/bash

(echo 'Running hourtable conversion script...' && cd hourtables && ./build.sh) || (echo 'Could not run hourtable conversion script')
