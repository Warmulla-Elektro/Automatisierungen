import argparse
import json
import os
import sys
import time
from datetime import datetime, timedelta
from enum import Enum
from types import NoneType
from typing import IO

import pandas
from odf.opendocument import OpenDocumentSpreadsheet
from odf.table import Table, TableRow, TableCell
from odf.text import P

from Nextcloud import ApiWrapper

global dayTotalDec
dayTotalDec = 0.0

if not os.path.isdir('.cache'): os.mkdir('.cache')
if not os.path.isdir('.out'): os.mkdir('.out')


def weekday(datetime=datetime.today()):
    return ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'][datetime.weekday()]


def parse_timedelta(str: str):
    t = time.strptime(str, '%H:%M')
    return timedelta(hours=t.tm_hour, minutes=t.tm_min)


def parse_arguments():
    def parseDatetime(str):
        try:
            return datetime.strptime(str, '%d.%m.%Y')
        except ValueError:
            raise argparse.ArgumentTypeError('Date format must be DD.MM.YYYY')

    parser = argparse.ArgumentParser()
    parser.add_argument('--users', type=str, nargs='+')
    parser.add_argument('--year', type=int)
    parser.add_argument('--month', type=int)
    parser.add_argument('--week', type=int)
    parser.add_argument('--since', type=parseDatetime)
    parser.add_argument('--customer', type=str)
    parser.add_argument('--detail', type=str)
    parser.add_argument('-S', action='store_true', help='Force Reload')
    parser.add_argument('-C', action='store_true', help='Cleanup outputs')  # todo
    parser.add_argument('-c', action='store_true', help='Generate CSV')
    parser.add_argument('-o', action='store_true', help='Convert to ODS format; implies -c')
    parser.add_argument('-u', action='store_true', help='Upload File; implies -o')
    parser.add_argument('-p', action='store_true', help='Print everything to STDOUT as well as to file')
    parser.add_argument('-v', action='store_true', help='Verbose Logging')
    return parser.parse_args()


def load_data(force_reload: bool = False, cache_file='.cache/data.json'):
    if (not force_reload
            and (os.path.exists(cache_file)
                 and datetime.fromtimestamp(os.stat(cache_file).st_mtime) > (
                         datetime.now() - timedelta(hours=1)))):
        with open(cache_file, "r") as cache:
            data = json.loads(cache.read())
    else:
        response = nc.fetchTableData(2)
        if response.status_code / 100 != 2:
            sys.stderr.writelines(
                f'Unable to parse response: Invalid response code {response.status_code}\n\t{response.text}')
            return []
        with open(cache_file, 'w') as cache:
            cache.write(response.text)
        data = json.loads(response.text)

    # convert from table data todo
    class TableColumn(Enum):
        date = 7
        customer = 8
        startTime = 9
        endTime = 10
        breakMultiplier = 13
        breakStart = 22
        details = 23
        vacation = 75
        colleagues = 76

    entries = []
    for entry in data:
        convert = {'user': entry['createdBy']}

        for column in TableColumn:
            f0 = filter(lambda p: p['columnId'] == column.value, entry['data'])
            f1 = map(lambda p: p['value'], f0)
            f2 = list(f1)
            if len(f2) == 0 or isinstance(f2[0], NoneType) or f2[0] == "":
                convert[column.name] = ''
                continue
            value = f2[0]
            parse = lambda it: it
            if column == TableColumn.date: parse = lambda str: datetime.strptime(str, '%Y-%m-%d')
            if column == TableColumn.startTime: parse = parse_timedelta
            if column == TableColumn.endTime: parse = parse_timedelta
            if column == TableColumn.breakStart: parse = parse_timedelta
            if column == TableColumn.vacation: parse = lambda str: str == 'true'
            convert[column.name] = parse(value)

        entries.append(convert)

    return entries


def generate_csv(data: any, out: IO):
    global dayTotalDec

    out.write(f'{user},Rg.Nr.,{year} Woche {week},Von,Bis,Gesamt,Tag Gesamt,Dezimal,Bemerkungen\n')
    out.write(',\n')

    def write_timeblock(entry, start: timedelta, end: timedelta, print_customer=True, first=True, last=True):
        global dayTotalDec

        def format(timeDec):
            hours = int(float(timeDec))
            minutes = (float(timeDec) - hours) * 60
            return f"{hours:02}:{int(minutes):02}"

        def convert(td: timedelta):
            return round(td.total_seconds() + 3600, 2)

        total = end - start
        dayTotal = ''
        dayTotalDec += convert(total)
        if last: dayTotal = format(dayTotalDec)
        if first:
            date_str = f'{weekday(entry['date'])} {entry['date'].strftime('%d.%m.')}'
        else:
            date_str = ''
        if print_customer:
            customer = entry['customer']
        else:
            customer = ''
        out.write(f'{date_str},,{customer},{start},{end},{total},{dayTotal},{dayTotalDec},{entry['details']}\n')

    data = list(data)
    sorted(data, key=lambda it: (it['date'], it['startTime']))

    for i in range(0, len(data)):
        entry = data[i]
        if entry['vacation']:
            if entry['customer']:
                reason = entry['customer']
            elif entry['details']:
                reason = entry['details']
            else:
                reason = 'Freier Tag (kein Grund angegeben)'
            out.write(f'{weekday(entry['date'])} {entry['date'].strftime('%d.%m.')},,{reason}\n')
            continue
        last = False
        if i + 1 < len(data):
            next = data[i + 1]
        if next and next['date'] != entry['date']: last = True
        if entry['breakMultiplier'] == 0 or entry['breakMultiplier'] == '':
            write_timeblock(entry, entry['startTime'], entry['endTime'], last=last)
        else:
            breakEnd = entry['breakStart'] + timedelta(minutes=15 * int(entry['breakMultiplier']))
            write_timeblock(entry, entry['startTime'], entry['breakStart'], last=last)
            write_timeblock(entry, breakEnd, entry['endTime'], False, last)
        if last: dayTotalDec = 0.0


def convert_to_ods(input: IO, output: IO):
    dataframe = pandas.read_csv(input, header=0)
    dataframe.fillna("", inplace=True)

    ods = OpenDocumentSpreadsheet()
    table = Table(name=f'Kalenderwoche {week}')
    ods.spreadsheet.addElement(table)

    header_row = TableRow()
    table.addElement(header_row)
    for column_name in dataframe.columns:
        tc = TableCell()
        header_row.addElement(tc)
        tc.addElement(P(text=str(column_name)))

    for row in dataframe.itertuples(index=False):
        tr = TableRow()

        table.addElement(tr)
        for cell in row:
            tc = TableCell()
            tr.addElement(tc)
            tc.addElement(P(text=str(cell)))

    ods.write(output)


nc = ApiWrapper()

# load data
args = parse_arguments()
data = load_data(args.S)

# for each user
if args.users:
    users = args.users
else:
    users = nc.fetchUsers()
for user in users:
    userdata = filter(lambda e: e['user'] == user, data)

    if args.year:
        userdata = filter(lambda e: e['date'].year == args.year, userdata)
        year = args.year
    else:
        year = datetime.today().year
    if args.month:
        userdata = filter(lambda e: e['month'] >= args.month, userdata)
    elif args.since:
        # week range
        start = datetime.strptime(args.since, '%d-%m-%Y').isocalendar().week
        weeks = map(lambda x: x + start, range(datetime.today().isocalendar().week - start))
    elif args.week:
        weeks = [args.week]
    else:
        weeks = [datetime.today().isocalendar().week]

    userdata = list(userdata)

    for week in weeks:
        weekdata = filter(lambda e: e['date'].isocalendar().week == week, userdata)

        # apply filters
        if args.since:
            weekdata = filter(
                lambda e: (datetime(year=e['year'], month=1, day=1) + timedelta(weeks=e['week'])) >= args.since,
                weekdata)
        if args.customer:
            weekdata = filter(lambda e: e['customer'].search(args.customer), weekdata)
        if args.detail:
            weekdata = filter(lambda e: e['detail'].search(args.detail), weekdata)

        # run per-user tasks
        if args.c or args.o or args.u:
            with open(f'.cache/{user}.csv', 'w') as user_csv:
                generate_csv(weekdata, user_csv)
        if args.p and os.path.exists(f'.cache/{user}.csv'):
            with open(f'.cache/{user}.csv', 'r') as buf:
                print(buf.read())
        elif args.p:
            sys.stderr.writelines('Could not read generated CSV data')
        if args.o or args.u:
            with open(f'.cache/{user}.csv', 'r') as user_csv, open(f'.out/{user}.ods', 'wb') as user_ods:
                convert_to_ods(user_csv, user_ods)
        if args.u:
            with open(f'.out/{user}.ods', 'rb') as user_ods:
                nc.upload(f'Stunden {year}/Kalenderwoche {week}/{user}.ods', user_ods)
                nc.share(f'Stunden {year}', 'Stundeneinsicht')
