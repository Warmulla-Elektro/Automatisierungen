#!/bin/bash

if [ ! -e '../nc_api_bot_password.cred' ]; then
    >&2 echo "ERROR: ../nc_api_bot_password.cred file not found"
    exit 1
fi

if [ -z "$1" ]; then
    >&2 echo 'No user defined'
    exit 1
else
    declare -g user="$1"
fi

declare -g week=$(echo "from datetime import datetime; print(datetime.today().isocalendar().week)" | python)
if [ ! -z "$2" ]; then
    if [ $(echo "$2" | dd bs=1 count=1 2> /dev/null) == "-" ]; then
        declare -g week=$(echo "$week $2" | bc)
    else
        declare -g week="$2"
    fi
fi
declare -g year=$(echo "from datetime import datetime; print(datetime.today().year)" | python)
declare -g month=$(echo "from datetime import datetime; print(datetime.today().month)" | python)

monthStr=$(echo "print(['','Januar','Feburar','März','April','Mai','Juni','Juli','August','September','Oktober','November','Dezember'][$month])" | python)

# load database
data=$(((eval "$(find '.cache/data.json' -amin -1 | grep -q .)" && cat '.cache/data.json')\
    || (>&2 echo 'WARN: Could not load cached data, refreshing cache...'\
        && curl -u "bot:$(cat ../nc_api_bot_password.cred)" https://warmulla.kaleidox.de/index.php/apps/tables/api/1/tables/2/rows\
            | tee '.cache/data.json'))\
    | jq "[.[] | select(.createdBy == \"$user\")]"\
    | jq 'sort_by((.data[] | select(.columnId == 7) | .value),(.data[] | select(.columnId == 9) | .value))')

if [ $(echo "$data" | jq -rc '.[]' | wc -l) == "0" ]; then
    >&2 echo 'INFO: No data found for the specified user and week'
    exit 1
fi

>&2 echo "INFO: Converting table to CSV..."

# print csv table header
echo "$user,$monthStr $year Woche $week,Von,Bis,Gesamt,Tag Gesamt,Dezimal"
echo ','

declare -g any=0
declare -g count=0
declare -g last='0-0-0'
declare -g dayTotalDec='0.0'
declare -g totalHoursDec='0.0'

format() {
    timeDec="$1"
    if [ "$DEBUG" == "true" ]; then >&2 echo "DEBUG: format $timeDec"; fi
    echo "decimal_hours = $timeDec;" 'hours = int(decimal_hours); minutes = int((decimal_hours - hours) * 60); print(f"{hours:02}:{minutes:02}")' | python
}

while read -r item; do
    date=$(echo "$item" | jq -r '.data[] | select(.columnId == 7) | .value')
    #if [ "$month" != "$(echo "$date" | sed 's/.*-\(.*\)-\(.*\)/\1/' | sed 's/^0*//')" ]; then continue; fi
    day=$(echo "$date" | sed 's/.*-.*-\(.*\)/\1/' | sed 's/^0*//')
    year=$(echo "$date" | sed 's/\(.*\)-.*-.*/\1/' | sed 's/^0*//')
    if [ "$week" != $(echo "from datetime import datetime; print(datetime($year, $month, $day).isocalendar().week)" | python) ]; then continue; fi

    # if date changed:
    if [ "$date" != "$last" ]; then
        dayTotal=$(format "$dayTotalDec")

        if [ "$any" == 0 ]; then
            export any=1
        else
            echo ",$dayTotal,$dayTotalDec"
            declare -g dayTotalDec='0.00'
            if [ "$DEBUG" == "true" ]; then >&2 echo "DEBUG: Resetting dayTotalDec to 0.00"; fi
        fi

        # todo: on newline, append wday combination
        wday=$(echo "from datetime import datetime; print(['Mo','Di','Mi','Do','Fr','Sa','So'][datetime($year,$month,$day).weekday()])" | python)
        echo -n "$wday $day,"
    else
        # else empty newline without wday combination
        echo ''
        echo -n ','
    fi
    export last="$date"

    customer=$(echo "$item" | jq -r '.data[] | select(.columnId == 8) | .value')
    start=$(echo "$item" | jq -r '.data[] | select(.columnId == 9) | .value')
    end=$(echo "$item" | jq -r '.data[] | select(.columnId == 10) | .value')

    echo -n "$customer,"

    calcDecimal() {
        if [ "$DEBUG" == "true" ]; then >&2 echo "DEBUG: decimal $1"; fi
        # obtain hours and minutes
        hours=$(echo "$1" | sed 's/\(.*\):.*/\1/')
        minutes=$(echo "$1" | sed 's/.*:\(.*\)/\1/')
        echo "scale=2; $hours + (0.25 * ($minutes / 15))" | bc
    }

    appendTimeblock() {
        if [ "$DEBUG" == "true" ]; then >&2 echo "DEBUG: block $1 - $2"; fi

        # calculate decimal hours each
        startDec=$(calcDecimal "$1")
        endDec=$(calcDecimal "$2")

        if [ "$DEBUG" == "true" ]; then >&2 echo "DEBUG: end $endDec start $startDec"; fi

        # calculate delta time (as total time)
        totalDec=$(echo "scale=2; $endDec - $startDec" | bc)
        if [ "$DEBUG" == "true" ]; then >&2 echo "DEBUG: dayTotalDec before update=$dayTotalDec"; fi
        declare -g dayTotalDec=$(echo "scale=2; $dayTotalDec + $totalDec" | bc)
        declare -g totalHoursDec=$(echo "scale=2; $totalHoursDec + $totalDec" | bc)
        if [ "$DEBUG" == "true" ]; then >&2 echo "DEBUG: dayTotalDec after update=$dayTotalDec"; fi
        totalFormatted=$(format "$totalDec")

        echo -n "$1,$2,$totalFormatted"
    }

    # split at break and foreach before and after, do the following:
    breakMultiplier=$(echo "$item" | jq -r '.data[] | select(.columnId == 13) | .value')
    if [ "$DEBUG" == "true" ]; then >&2 echo "DEBUG: breakMultiplier $breakMultiplier"; fi

    if [ -z "$breakMultiplier" ] || [ "$breakMultiplier" -eq "0" ]; then
        # if muli is 0, just have one entry
        appendTimeblock "$start" "$end"
    else
        # else split into two entries
        breakStart=$(echo "$item" | jq -r '.data[] | select(.columnId == 22) | .value')
        breakStartDec=$(calcDecimal "$breakStart")

        appendTimeblock "$start" "$breakStart"

        echo ''
        echo -n ",,"
        breakEndDec=$(echo "scale=2; $breakStartDec + (0.25 * $breakMultiplier)" | bc)
        breakEndFormatted=$(format "$breakEndDec")
        appendTimeblock "$breakEndFormatted" "$end"
    fi
    declare -g count=$(echo "$count + 1" | bc)
done < <(echo "$data" | jq -c '.[]')

dayTotal=$(format "$dayTotalDec")
echo ",$dayTotal,$dayTotalDec"

echo ','
echo ",,,,,,$totalHoursDec"

echo ''
>&2 echo "INFO: Parsing CSV for user $user and week $week successful; processed $count out of $(echo "$data" | jq -c '.[]' | wc -l) entries"