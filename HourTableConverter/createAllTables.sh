#!/bin/bash

if [ ! -e '../nc_api_bot_password.cred' ]; then
    >&2 echo "ERROR: ../nc_api_bot_password.cred file not found"
    exit 1
fi

users=$(curl -su "bot:$(cat ../nc_api_bot_password.cred)" -H OCS-APIRequest:true -H Accept:application/json -X get https://warmulla.kaleidox.de/ocs/v1.php/cloud/users | jq '.ocs.data.users[]')

while read -r user; do
    >&2 echo "INFO: Converting table for $user"...
done < <(echo "$users" | jq -rc '.[]')
