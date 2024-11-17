#!/bin/bash

user="$1"

rm -f table.csv
./createUserMonthCsv.sh "$user" 2> /dev/null >> "$user.csv"
ssconvert "$user.csv" "$user.pdf"
