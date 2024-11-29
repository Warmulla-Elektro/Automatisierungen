#!/bin/bash

if [ -z "$3" ]; then
  declare -g year=$(python common.py year)
else
  declare -g year="$3"
fi

while read -r n; do ./run.sh "$n" "$year"; done < <(seq "$1" "$2")
