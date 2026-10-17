"""Make a recorded scene exactly SECONDS long: 30 fps, the phone's status bar cropped, 1080 x 2340.

screenrecord only writes a frame when the screen changes, so a calm ending is missing from the
file; tpad clones the last frame to fill it. The crop (162 px) fits the Nothing Phone 3 (1260 x 2800).
usage: norm.py NAME SECONDS          env: CLIPS (default ./clips), writes CLIPS/n_NAME.mp4
"""
import os
import subprocess
import sys

clips = os.environ.get('CLIPS', 'clips')
name, secs = sys.argv[1:3]
vf = (
    'crop=1260:2638:0:162,'
    'scale=1080:-2,pad=1080:2340:0:(oh-ih)/2:black,setsar=1,'
    'fps=30,tpad=stop_mode=clone:stop_duration=20'
)
subprocess.run([
    'ffmpeg', '-y', '-loglevel', 'error', '-i', f'{clips}/{name}.mp4', '-vf', vf, '-t', secs,
    '-an', '-c:v', 'libx264', '-crf', '20', '-pix_fmt', 'yuv420p', f'{clips}/n_{name}.mp4',
], check=True)
print('normalized', name, secs, 's')
