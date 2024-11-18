import sys
from datetime import datetime, timedelta


def week(year, month, day):
    weekDate(datetime(int(year), int(month), int(day)))


def weekDate(date=datetime.today()):
    print(date.isocalendar().week)


def weekday(year, month, day):
    print(['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'][datetime(int(year), int(month), int(day)).weekday()])


def monthOfWeek(week, year=datetime.today().year):
    print((datetime(int(year), 1, 1) + timedelta(days=(int(week) - 1) * 7 + 3)).month)


def month():
    print(datetime.today().month)


def monthstr(month=datetime.today().month):
    print(
        ['', 'Januar', 'Feburar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November',
         'Dezember'][int(month)])


def year():
    print(datetime.today().year)


def format(timeDec):
    hours = int(float(timeDec))
    minutes = (float(timeDec) - hours) * 60
    print(f"{hours:02}:{int(minutes):02}")


def parseYear(date):
    print(date.split('-')[0])


def parseMonth(date):
    print(date.split('-')[1])


def parseDay(date):
    print(date.split('-')[2])


argc = len(sys.argv) - 2
call = locals()[sys.argv[1]]
if argc == 0:
    call()
if argc == 1:
    call(sys.argv[2])
if argc == 2:
    call(sys.argv[2], sys.argv[3])
if argc == 3:
    call(sys.argv[2], sys.argv[3], sys.argv[4])
