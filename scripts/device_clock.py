#!/usr/bin/python3
"""Bounded RTC operations for AeroJudge; stdout is exactly one JSON result.

Why Python instead of the usual Java/shell: the target Bullseye device's
hwclock cannot read the RTC invalid-time flag (--vl-read is unavailable).
Python's standard-library fcntl.ioctl can read RTC_VL_READ through the kernel
driver. Using the already-installed Python 3 avoids an additional native
library in Java or a compiled, architecture-specific C helper. This exception
is limited to the privileged device helper; application logic stays in Java.
No third-party Python packages or resident service are required.
"""

import calendar
import fcntl
import json
import math
import os
import re
import signal
import struct
import subprocess
import sys
import time
from pathlib import Path

RTC_NAME = Path('/sys/class/rtc/rtc0/name')
RTC_DEVICE = '/dev/rtc0'
LOCK_FILE = '/run/lock/aerojudge-clock.lock'
SYNC_MARKER = Path('/run/systemd/timesync/synchronized')
RTC_VL_READ = 0x80047013
RTC_RD_TIME = 0x80247009
SYSTEMCTL = '/bin/systemctl'
HWCLOCK = '/sbin/hwclock'
TIME_SERVICE = 'systemd-timesyncd.service'


def interrupted(signum, _frame):
    raise RuntimeError('Clock operation interrupted or timed out (signal %s).' % signum)


def run_command(arguments, deadline):
    remaining = deadline - time.monotonic()
    if remaining <= 0:
        raise RuntimeError('Clock operation timed out.')
    completed = subprocess.run(arguments, capture_output=True, text=True,
                               timeout=min(remaining, 5), check=False,
                               env={'PATH': '/usr/sbin:/usr/bin:/sbin:/bin', 'LC_ALL': 'C', 'TZ': 'UTC'})
    if completed.returncode != 0:
        raise RuntimeError('%s failed: %s' % (arguments[0], completed.stderr.strip()))
    return completed.stdout.strip()


def require_rtc():
    if not re.fullmatch(r'rtc-rv3028(?:\s.*)?', RTC_NAME.read_text().strip()):
        raise RuntimeError('rtc0 is not the RV-3028 driver.')


def rtc_flags():
    with open(RTC_DEVICE, 'rb', buffering=0) as rtc:
        data = bytearray(4)
        fcntl.ioctl(rtc, RTC_VL_READ, data, True)
        return struct.unpack('=I', data)[0]


def rtc_epoch():
    # RTC_RD_TIME returns nine native 32-bit integers, even on a 64-bit kernel.
    with open(RTC_DEVICE, 'rb', buffering=0) as rtc:
        data = bytearray(36)
        fcntl.ioctl(rtc, RTC_RD_TIME, data, True)
    second, minute, hour, day, month, year, weekday, yearday, isdst = struct.unpack('=9i', data)
    return calendar.timegm((year + 1900, month + 1, day, hour, minute, second))


def require_stopped(deadline):
    state = run_command([SYSTEMCTL, 'show', TIME_SERVICE, '--property=ActiveState',
                         '--property=MainPID'], deadline)
    fields = dict(line.split('=', 1) for line in state.splitlines() if '=' in line)
    if fields.get('ActiveState') not in ('inactive', 'failed') or fields.get('MainPID') != '0':
        raise RuntimeError('timesyncd is not confirmed stopped.')


def stop_timesyncd(deadline):
    run_command([SYSTEMCTL, 'stop', TIME_SERVICE], deadline)
    require_stopped(deadline)


def write_verify_rtc(epoch, before_set, after_set, deadline):
    run_command([HWCLOCK, '--rtc=' + RTC_DEVICE, '--systohc', '--utc'], deadline)
    # Compare readback against an advancing reference, not a frozen start value.
    # The set syscall lies between before_set/after_set; RTC has whole seconds.
    before_read = time.monotonic()
    system_time = time.time()
    after_read = time.monotonic()
    if not (math.floor(epoch + before_read - after_set) <= system_time
            <= math.ceil(epoch + after_read - before_set)):
        raise RuntimeError('System clock readback did not verify.')
    before_read = time.monotonic()
    rtc_time = rtc_epoch()
    after_read = time.monotonic()
    if not (math.floor(epoch + before_read - after_set) <= rtc_time
            <= math.ceil(epoch + after_read - before_set)):
        raise RuntimeError('RTC clock readback did not verify.')
    if rtc_flags() & 1:
        raise RuntimeError('RTC still reports invalid time after write.')


def sync_score(epoch, received_at, deadline):
    require_stopped(deadline)
    before_set = time.monotonic()
    target = epoch + before_set - received_at
    time.clock_settime(time.CLOCK_REALTIME, target)
    after_set = time.monotonic()
    write_verify_rtc(target, before_set, after_set, deadline)
    return 0, {'success': True, 'message': 'System clock and RTC synchronized with Score.'}


def sync_internet(started_at):
    # Reserve the final 15 seconds of the 60-second budget for explicit cleanup.
    work_deadline = started_at + 45
    cleanup_deadline = started_at + 60
    fresh_sync = False
    recovery_error = None
    cleanup_error = None
    try:
        stop_timesyncd(work_deadline)
        if SYNC_MARKER.exists():
            raise RuntimeError('Old timesyncd synchronization marker was not removed.')
        run_command([SYSTEMCTL, 'start', TIME_SERVICE], work_deadline)
        wait_until = min(time.monotonic() + 30, work_deadline)
        while time.monotonic() < wait_until:
            if SYNC_MARKER.exists():
                fresh_sync = True
                break
            time.sleep(min(1, max(0, wait_until - time.monotonic())))
        if not fresh_sync:
            raise RuntimeError('No fresh internet synchronization within the recovery window.')
    except Exception as error:
        recovery_error = str(error)
    finally:
        # Only the internet lock owner reaches this block. TERM/INT cannot skip
        # cleanup; the overall alarm and bounded subprocesses still apply.
        signal.signal(signal.SIGTERM, signal.SIG_IGN)
        signal.signal(signal.SIGINT, signal.SIG_IGN)
        try:
            stop_timesyncd(cleanup_deadline)
        except Exception as error:
            cleanup_error = str(error)

    if cleanup_error is not None:
        return 1, {'success': False, 'message': 'Internet recovery cleanup failed: %s' % cleanup_error}

    if recovery_error is None and fresh_sync:
        try:
            before_set = time.monotonic()
            epoch = time.time()
            after_set = time.monotonic()
            write_verify_rtc(epoch, before_set, after_set, cleanup_deadline)
            return 0, {'success': True, 'message': 'Internet time and RTC verified; timesyncd stopped.'}
        except Exception as error:
            recovery_error = str(error)
    return 2, {'success': False, 'message': 'Internet recovery incomplete; timesyncd stopped. %s'
               % recovery_error}


def execute(arguments):
    started_at = time.monotonic()
    if arguments == ['status']:
        command = 'status'
    elif arguments == ['sync-internet']:
        command = 'sync-internet'
    elif len(arguments) == 2 and arguments[0] == 'sync-score' and re.fullmatch(
            r'[0-9]+(?:\.[0-9]+)?', arguments[1]):
        command = 'sync-score'
        epoch = float(arguments[1])
        if not math.isfinite(epoch):
            raise ValueError('Epoch seconds must be finite.')
    else:
        raise ValueError('Usage: aerojudge-clock status | sync-score <epoch-seconds> | sync-internet')

    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    signal.signal(signal.SIGALRM, interrupted)
    signal.setitimer(signal.ITIMER_REAL, 60 if command == 'sync-internet' else 5)
    try:
        require_rtc()
        if command == 'status':
            state = 'invalid' if rtc_flags() & 1 else 'valid'
            return 0, {'success': True, 'message': 'RTC validity read.', 'state': state}

        # Never wait for a writer: a delayed Score sample must not be applied.
        with open(LOCK_FILE, 'a') as lock:
            try:
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                return 1, {'success': False, 'message': 'Clock operation busy. Please retry.'}
            if command == 'sync-internet':
                return sync_internet(started_at)
            return sync_score(epoch, started_at, started_at + 5)
    finally:
        signal.setitimer(signal.ITIMER_REAL, 0)


def main():
    try:
        code, result = execute(sys.argv[1:])
    except Exception as error:
        code, result = 1, {'success': False, 'message': str(error)}
    print(json.dumps(result), flush=True)
    return code


if __name__ == '__main__':
    sys.exit(main())
