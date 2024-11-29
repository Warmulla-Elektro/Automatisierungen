#!/bin/bash

if [ ! -e '../nc_api_bot_password.cred' ]; then
    >&2 echo "ERROR: ../nc_api_bot_password.cred file not found"
    exit 1
fi

if [ "$1" == "reload" ]; then
    rm -r .cache
fi

declare -g targetWeek="$(python common.py weekDate)"
if [ ! -z "$2" ]; then
    if [ "$(echo "$2" | dd bs=1 count=1 2> /dev/null)" == "-" ]; then
        declare -g targetWeek="$(echo "$targetWeek $2" | bc)"
    else
        declare -g targetWeek="$2"
    fi
fi

declare -g targetYear="$(python common.py year)"
if [ ! -z "$3" ]; then
    if [ "$(echo "$3" | dd bs=1 count=1 2> /dev/null)" == "-" ]; then
        declare -g targetYear="$(echo "$targetYear $3" | bc)"
    else
        declare -g targetYear="$3"
    fi
fi

if [ ! -d '.cache' ]; then
    mkdir '.cache'
    rm '.cache/**'
fi
if [ -d '.out' ]; then
    rm -rf '.out'
fi
mkdir '.out'

users=$( ( (eval "$(find '.cache/users.json' -amin -999 | grep -q .)" && cat '.cache/users.json')\
    || (>&2 echo 'WARN: Could not load cached users, refreshing cache...'\
        && curl -su "bot:$(cat ../nc_api_bot_password.cred)" -H OCS-APIRequest:true -H Accept:application/json -X get https://warmulla.kaleidox.de/ocs/v1.php/cloud/users\
            | tee '.cache/users.json'))\
    | jq '.ocs.data.users[]')

while read -r user; do
    >&2 echo "INFO: User $user"
    ./createUserCsv.sh "$user" "$targetWeek" "$targetYear" >> ".cache/$user.csv" || rm -f ".cache/$user.csv"

    if [ -f ".cache/$user.csv" ]; then
      >&2 echo "INFO: Creating XLSX..."
      libreoffice --headless --convert-to ods ".cache/$user.csv" --outdir .out
      #column -s, -t < ".cache/$user.csv" > ".cache/$user.txt"
      #enscript -B -p ".cache/$user.ps" -r ".cache/$user.txt"
      #ps2pdf ".cache/$user.ps" ".out/$user.pdf"
    fi
done < <(echo "$users" | jq -rc)
