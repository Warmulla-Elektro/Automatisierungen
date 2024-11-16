#!/bin/bash

data=$(curl -su bot:RYV4463MpHAritiwwHpow7msQX2TJbkX https://warmulla.kaleidox.de/index.php/apps/tables/api/1/tables/2/rows | jq 'sort_by((.data[] | select(.columnId == 7) | .value),(.data[] | select(.columnId == 9) | .value))')

# print csv table header
echo "Tag,Kunde,Von,Bis,Gesamt,Tag Gesamt,Dezimal"

export any=0
export last=''
echo $data | jq -c '.[]' | while read -r item; do
    date=$(echo "$item" | jq -r '.data[] | select(.columnId == 7) | .value')
    day=$(echo "$date" | sed 's/.*-.*-\(.*\)/\1/' | sed 's/^0*//')
    month=$(echo "$date" | sed 's/.*-\(.*\)-\(.*\)/\1/')
    year=$(echo "$date" | sed 's/\(.*\)-.*-.*/\1/')

    # if date changed:
    if [ "$date" != "$last" ]; then
        dayTotal=

        if [ $any == 0 ]; then
            export any=1
        else
            echo ",$dayTotal,$dayTotalDecimal"
        fi
        dayTotalDecimal='0.00'

        # todo: on newline, append wday combination
        wday=$(echo "from datetime import datetime; print(['Mo','Di','Mi','Do','Fr','Sa','So'][datetime($year,$month,$day).weekday()])" | python)
        echo -n "$wday $day,"
    else
        # else empty newline without wday combination
        echo ''
        echo -n ','
    fi
    export last="$date"

    customer=$(echo $item | jq -r '.data[] | select(.columnId == 8) | .value')
    start=$(echo $item | jq -r '.data[] | select(.columnId == 9) | .value')
    end=$(echo $item | jq -r '.data[] | select(.columnId == 10) | .value')

    echo -n "$customer,"

    calcDecimal() {
        >&2 echo "### DEBUG: decimal $1"
        # obtain hours and minutes
        hours=$(echo "$1" | sed 's/\(.*\):.*/\1/')
        minutes=$(echo "$1" | sed 's/.*:\(.*\)/\1/')
        echo "scale=2; $hours + (0.25 * ($minutes / 15))" | bc
    }

    format() {
        >&2 echo "### DEBUG: format $1"

        local input="$1"
        local hours=${input%.*}
        local fraction=${input#*.}
        local minutes

        if [[ "$input" != *.* ]]; then
            fraction=0
        fi

        minutes=$(printf "%.0f" "$(echo "scale=2; ($fraction * 60) / 1" | bc)")

        printf "%02d:%02d\n" "$hours" "$minutes"
    }

    appendTimeblock() {
        >&2 echo "### DEBUG: block $1 - $2"

        # calculate decimal hours each
        startDec=$(calcDecimal "$1")
        endDec=$(calcDecimal "$2")

        >&2 echo "### DEBUG: end $endDec start $startDec"

        # calculate delta time (as total time)
        totalDec=$(echo "scale=2; $endDec - $startDec" | bc)
        dayTotalDecimal=$(echo "scale=2; $dayTotalDecimal + $totalDec")
        totalFormatted=$(format "$totalDec")

        echo -n "$1,$2,$totalFormatted"
    }

    # split at break and foreach before and after, do the following:
    breakMultiplier=$(echo "$item" | jq -r '.data[] | select(.columnId == 13) | .value')
    >&2 echo "### DEBUG: breakMultiplier $breakMultiplier"

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
done
echo ''
