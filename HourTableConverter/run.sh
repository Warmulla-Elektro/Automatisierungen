#!/bin/bash

declare -g targetWeek=$(python common.py targetWeekDate)
if [ ! -z "$1" ]; then
    if [ "$(echo "$1" | dd bs=1 count=1 2> /dev/null)" == "-" ]; then
        declare -g targetWeek=$(echo "$targetWeek $1" | bc)
    else
        declare -g targetWeek="$1"
    fi
fi
declare -g targetYear=$(python common.py targetYear)
if [ ! -z "$2" ]; then
    if [ "$(echo "$2" | dd bs=1 count=1 2> /dev/null)" == "-" ]; then
        declare -g targetYear=$(echo "$targetYear $2" | bc)
    else
        declare -g targetYear="$2"
    fi
fi

./createAllTables.sh reload "$week" "$year"
./uploadTables.sh "$week" "$year"
