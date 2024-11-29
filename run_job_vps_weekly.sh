git pull -f

(
  cd HourTableConverter || exit 1
  ./cleanup.sh
  ./run.sh
)
