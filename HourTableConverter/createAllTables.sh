#!/bin/bash

if [ ! -e '../nc_api_bot_password.cred' ]; then
    >&2 echo "ERROR: ../nc_api_bot_password.cred file not found"
    exit 1
fi

if [ "$1" == "reload" ]; then
    rm -r .cache
fi

if [ ! -d '.cache' ]; then
    mkdir '.cache'
    rm '.cache/*.csv'
fi
if [ -d '.out' ]; then
    rm -rf '.out'
fi
mkdir '.out'

users=$(((eval "$(find '.cache/users.json' -amin -999 | grep -q .)" && cat '.cache/users.json')\
    || (>&2 echo 'WARN: Could not load cached data, refreshing cache...'\
        && curl -su "bot:$(cat ../nc_api_bot_password.cred)" -H OCS-APIRequest:true -H Accept:application/json -X get https://warmulla.kaleidox.de/ocs/v1.php/cloud/users\
            | tee '.cache/users.json'))\
    | jq '.ocs.data.users[]')

while read -r user; do
    >&2 echo "INFO: User $user"
    ./createUserCsv.sh "$user" >> ".cache/$user.csv" || continue

    >&2 echo "INFO: Creating PDF..."
    ssconvert ".cache/$user.csv" ".out/$user.pdf"
done < <(echo "$users" | jq -rc)
