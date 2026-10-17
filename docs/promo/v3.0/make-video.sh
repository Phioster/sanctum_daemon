#!/usr/bin/env bash
# Builds the demo video: recording (or screenshots) + captions.ass + end card.
#   ./make-video.sh                 uses ../../screenshots/v3.0/demo-raw.mp4
#   ./make-video.sh --slides        a slideshow of the v3.0 screenshots instead
# Output: sanctumd-3.0-demo.mp4 (1080x2340, H.264, silent) next to this script.
set -euo pipefail
cd "$(dirname "$0")"
shots=../../screenshots/v3.0
card=graphics/end-card.png
out=sanctumd-3.0-demo.mp4
tmp=$(mktemp -d)

if [ "${1:-}" = "--slides" ]; then
  # The shots that tell the storyboard's story, each as long as its caption (captions.ass);
  # every one but the last gets the 0.4 s its cross-fade eats, so the captions stay in step.
  list=(01_home 02_services 04_radarr_service_menu 11_nzbget_news_servers 09_jellyfin_now_playing 08_jellyfin_transcoding 01_home)
  durs=(6.4 4.4 7.4 8.4 6.4 8.4 6)
  args=(); filt=""; i=0
  for n in "${list[@]}"; do
    f="$shots/$n.png"; [ -f "$f" ] || { echo "missing $f"; exit 1; }
    d=${durs[$i]}
    args+=(-loop 1 -t $d -i "$f")
    filt+="[$i:v]scale=1080:2340:force_original_aspect_ratio=decrease,pad=1080:2340:(ow-iw)/2:(oh-ih)/2,setsar=1,fps=30[v$i];"
    i=$((i+1))
  done
  chain="[v0]"; off=0
  for ((k=1;k<i;k++)); do
    off=$(echo "$off + ${durs[$((k-1))]} - 0.4" | bc)
    filt+="${chain}[v$k]xfade=transition=fade:duration=0.4:offset=$off[x$k];"; chain="[x$k]"
  done
  ffmpeg -y -loglevel error "${args[@]}" -filter_complex "${filt%;}" -map "$chain" -pix_fmt yuv420p "$tmp/body.mp4"
else
  ffmpeg -y -loglevel error -i "$shots/demo-raw.mp4" -vf "scale=1080:2340:force_original_aspect_ratio=decrease,pad=1080:2340:(ow-iw)/2:(oh-ih)/2,setsar=1,fps=30" -an -pix_fmt yuv420p "$tmp/body.mp4"
fi

ffmpeg -y -loglevel error -i "$tmp/body.mp4" -vf "ass=captions.ass" -c:v libx264 -crf 22 -pix_fmt yuv420p "$tmp/captioned.mp4"
ffmpeg -y -loglevel error -loop 1 -t 4 -i "$card" -vf "scale=1080:2340,fps=30,fade=in:st=0:d=0.5" -c:v libx264 -crf 22 -pix_fmt yuv420p "$tmp/card.mp4"
printf "file '%s'\nfile '%s'\n" "$tmp/captioned.mp4" "$tmp/card.mp4" > "$tmp/list.txt"
ffmpeg -y -loglevel error -f concat -safe 0 -i "$tmp/list.txt" -c copy -movflags +faststart "$out"
rm -rf "$tmp"
echo "wrote $out ($(du -h "$out" | cut -f1))"
