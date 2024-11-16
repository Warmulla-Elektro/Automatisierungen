#!/bin/bash

if [ -z "$1" ]; then
    month=$(echo "from datetime import datetime; print(datetime.today().month)" | python)
fi

data=$(curl -su bot:RYV4463MpHAritiwwHpow7msQX2TJbkX https://warmulla.kaleidox.de/index.php/apps/tables/api/1/tables/2/rows | jq 'sort_by((.data[] | select(.columnId == 7) | .value),(.data[] | select(.columnId == 9) | .value))')

# print csv table header
echo "Tag,Kunde,Von,Bis,Gesamt,Tag Gesamt,Dezimal"

export any=0
export last=''
echo "$data" | jq -c '.[]' | while read -r item; do
    date=$(echo "$item" | jq -r '.data[] | select(.columnId == 7) | .value')
    if [ "$month" != "$(echo "$date" | sed 's/.*-\(.*\)-\(.*\)/\1/' | sed 's/^0*//')" ]; then continue; fi
    day=$(echo "$date" | sed 's/.*-.*-\(.*\)/\1/' | sed 's/^0*//')
    year=$(echo "$date" | sed 's/\(.*\)-.*-.*/\1/' | sed 's/^0*//')

    # if date changed:
    if [ "$date" != "$last" ]; then
        dayTotal=$(format "$dayTotalDecimal")

        if [ "$any" == 0 ]; then
            export any=1
        else
            echo ",$dayTotal,$dayTotalDecimal"
            dayTotalDecimal='0.00'
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
        >&2 echo "### DEBUG: decimal $1"
        # obtain hours and minutes
        hours=$(echo "$1" | sed 's/\(.*\):.*/\1/')
        minutes=$(echo "$1" | sed 's/.*:\(.*\)/\1/')
        echo "scale=2; $hours + (0.25 * ($minutes / 15))" | bc
    }

    format() {
        >&2 echo "### DEBUG: format $1"
        echo "decimal_hours = $1;" 'hours = int(decimal_hours); minutes = int((decimal_hours - hours) * 60); print(f"{hours:02}:{minutes:02}")' | python
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
