flower.mp4: MDN interactive example, CC0 sample video.
Source: https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4
Used only by native decoder instrumentation tests.

streams/: lossless stream-copy/remux of the same CC0 sample into fMP4 DASH/HLS.
Generated with FFmpeg 9.0.2:
ffmpeg -i flower.mp4 -map 0 -c copy -f dash -seg_duration 1 -use_template 1 -use_timeline 1 -hls_playlist 1 -hls_master_name master.m3u8 streams/manifest.mpd
