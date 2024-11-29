#!/bin/bash

if [ ! -f 'nc_api_bot_password.cred' ]; then
  echo -n "Authenticate as nextcloud://bot@warmulla.kaleidox.de: "
  read -r password
  echo "$password" >> nc_api_bot_password.cred
fi

(
  cd HourTableConverter || exit 1
  ./install.sh
)
