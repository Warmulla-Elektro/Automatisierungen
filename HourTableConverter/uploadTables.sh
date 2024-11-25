#!/bin/bash

if [ ! -e '../nc_api_bot_password.cred' ]; then
    >&2 echo "ERROR: ../nc_api_bot_password.cred file not found"
    exit 1
fi

declare -g targetWeek=$(python common.py weekDate)
if [ ! -z "$1" ]; then
    if [ "$(echo "$1" | dd bs=1 count=1 2> /dev/null)" == "-" ]; then
        declare -g targetWeek=$(echo "$targetWeek $1" | bc)
    else
        declare -g targetWeek="$1"
    fi
fi
declare -g targetYear=$(python common.py year)
if [ ! -z "$2" ]; then
    if [ "$(echo "$2" | dd bs=1 count=1 2> /dev/null)" == "-" ]; then
        declare -g targetYear=$(echo "$targetYear $2" | bc)
    else
        declare -g targetYear="$2"
    fi
fi

>&2 echo "INFO: Creating shared DAV directories..."
curl -su "bot:$(cat '../nc_api_bot_password.cred')" -H OCS-APIRequest:true -X MKCOL "https://warmulla.kaleidox.de/remote.php/dav/files/bot/Stunden%20$targetYear" 2> /dev/null | xq\
  && (curl -su "bot:$(cat '../nc_api_bot_password.cred')" -H OCS-APIRequest:true -X MKCOL -X POST 'https://warmulla.kaleidox.de/ocs/v2.php/apps/files_sharing/api/v1/shares'\
     -d "path=/Stunden%20$targetYear"\
     -d 'shareType=1'\
     -d 'shareWith=Stundeneinsicht'\
     -d 'attributes=%5B%7B%22scope%22%3A%22permissions%22%2C%22key%22%3A%22download%22%2C%22value%22%3Atrue%7D%5D'\
     -d 'permissions=17' 1> /dev/null 2> /dev/null)\
  || >&2 echo "Doesnt matter, likely exists already"
curl -su "bot:$(cat '../nc_api_bot_password.cred')" -H OCS-APIRequest:true -X MKCOL "https://warmulla.kaleidox.de/remote.php/dav/files/bot/Stunden%20$targetYear/Kalenderwoche%20$targetWeek" 2> /dev/null | xq\
  || >&2 echo "Doesnt matter, likely exists already"

>&2 echo "INFO: Uploading PDF files..."

users=$( ( (eval "$(find '.cache/users.json' -amin -999 | grep -q .)" && cat '.cache/users.json')\
    || (>&2 echo 'WARN: Could not load cached users, refreshing cache...'\
        && curl -su "bot:$(cat ../nc_api_bot_password.cred)" -H OCS-APIRequest:true -H Accept:application/json -X get https://warmulla.kaleidox.de/ocs/v1.php/cloud/users\
            | tee '.cache/users.json'))\
    | jq '.ocs.data.users[]')

while read -r user; do
  if [ ! -f ".out/$user.pdf" ]; then continue; fi
  curl -u "bot:$(cat '../nc_api_bot_password.cred')" -H OCS-APIRequest:true --upload-file ".out/$user.pdf" -X PUT "https://warmulla.kaleidox.de/remote.php/dav/files/bot/Stunden%20$targetYear/Kalenderwoche%20$targetWeek/$user.pdf"
done < <(echo "$users" | jq -rc)
