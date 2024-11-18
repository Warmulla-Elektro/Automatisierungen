#!/bin/bash

if [ ! -e '../nc_api_bot_password.cred' ]; then
    >&2 echo "ERROR: ../nc_api_bot_password.cred file not found"
    exit 1
fi

declare -g week=$(python common.py weekDate)
if [ ! -z "$1" ]; then
    if [ "$(echo "$1" | dd bs=1 count=1 2> /dev/null)" == "-" ]; then
        declare -g week=$(echo "$week $1" | bc)
    else
        declare -g week="$1"
    fi
fi
declare -g year=$(python common.py year)
if [ ! -z "$2" ]; then
    if [ "$(echo "$2" | dd bs=1 count=1 2> /dev/null)" == "-" ]; then
        declare -g year=$(echo "$year $2" | bc)
    else
        declare -g year="$2"
    fi
fi

>&2 echo "INFO: Creating DAV directories..."
curl -u "bot:$(cat '../nc_api_bot_password.cred')" -H OCS-APIRequest:true -X MKCOL "https://warmulla.kaleidox.de/remote.php/dav/files/bot/Stunden%20$year"\
  && (curl -u "bot:$(cat '../nc_api_bot_password.cred')" -H OCS-APIRequest:true -X MKCOL -X POST 'https://warmulla.kaleidox.de/ocs/v2.php/apps/files_sharing/api/v1/shares'\
     -d "path=/Stunden%20$year"\
     -d 'shareType=1'\
     -d 'shareWith=admin')\
  || >&2 echo "Doesnt matter, likely exists already"
curl -u "bot:$(cat '../nc_api_bot_password.cred')" -H OCS-APIRequest:true -X MKCOL "https://warmulla.kaleidox.de/remote.php/dav/files/bot/Stunden%20$year/Kalenderwoche%20$week"\
  || >&2 echo "Doesnt matter, likely exists already"

>&2 echo "INFO: Uploading PDF files..."

users=$( ( (eval "$(find '.cache/users.json' -amin -999 | grep -q .)" && cat '.cache/users.json')\
    || (>&2 echo 'WARN: Could not load cached data, refreshing cache...'\
        && curl -su "bot:$(cat ../nc_api_bot_password.cred)" -H OCS-APIRequest:true -H Accept:application/json -X get https://warmulla.kaleidox.de/ocs/v1.php/cloud/users\
            | tee '.cache/users.json'))\
    | jq '.ocs.data.users[]')

while read -r user; do
  curl -u "bot:$(cat '../nc_api_bot_password.cred')" -H OCS-APIRequest:true --upload-file ".out/$user.pdf" -X PUT "https://warmulla.kaleidox.de/remote.php/dav/files/bot/Stunden%20$year/Kalenderwoche%20$week/$user.pdf"
done < <(echo "$users" | jq -rc)
