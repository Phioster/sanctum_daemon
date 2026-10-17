"""Record one scene of the demo video on a phone over adb: screenrecord runs for SECONDS while
timed taps and swipes play, so no pause of whoever drives the phone ends up in the video.

usage: scene.py NAME SECONDS ACTION...
  ACTION = T:tap:X,Y | T:swipe:X1,Y1,X2,Y2,MS | T:back     (T = seconds after the recording starts)
env:   ADB_SERIAL (default: the only device), CLIPS (output folder, default ./clips)

Navigate to the scene's first screen and read the coordinates beforehand (uiautomator dump);
then the recording itself is only the motion.
"""
import os
import subprocess
import sys
import time

adb = ['adb'] + (['-s', os.environ['ADB_SERIAL']] if os.environ.get('ADB_SERIAL') else [])
out = os.environ.get('CLIPS', 'clips')
os.makedirs(out, exist_ok=True)
name, secs, *actions = sys.argv[1:]

rec = subprocess.Popen(adb + ['shell', 'screenrecord', '--time-limit', secs, '--bit-rate', '16000000', f'/sdcard/{name}.mp4'])
start = time.time()
for a in actions:
    t, kind, *rest = a.split(':')
    wait = float(t) - (time.time() - start)
    if wait > 0:
        time.sleep(wait)
    if kind == 'tap':
        subprocess.run(adb + ['shell', 'input', 'tap', *rest[0].split(',')])
    elif kind == 'swipe':
        subprocess.run(adb + ['shell', 'input', 'swipe', *rest[0].split(',')])
    elif kind == 'back':
        subprocess.run(adb + ['shell', 'input', 'keyevent', '4'])
rec.wait()
time.sleep(1)
subprocess.run(adb + ['pull', f'/sdcard/{name}.mp4', f'{out}/{name}.mp4'], capture_output=True)
subprocess.run(adb + ['shell', 'rm', f'/sdcard/{name}.mp4'])
print('recorded', name)
