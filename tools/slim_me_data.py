#!/usr/bin/env python3
"""Rewrite old me_data.json files in the slim form new events use (cmo_client cmo_watch.write_event):
me_delta_array keeps only its first and last entries, me_frame_count holds the old entry count (one per frame),
me_image_array is emptied. A "me_slimmed" note records what was removed. Each file is written to a temp file,
fsynced and renamed over the old one (its time kept), so a crash never leaves half a file.

Usage: slim_me_data.py UPLOADS [--apply] [--list FILE]   (without --apply: a dry run that changes nothing)
       --list FILE writes the paths it changed (or would change), relative to UPLOADS, e.g. for a backup:
       tar czf before_slim.tar.gz -C UPLOADS -T FILE

Why: old pet door events kept one me_delta_array entry per frame, plus a me_image_array naming frames that no
longer exist, up to ~650 KB per long night clip. Search results carried all of it, so a raccoon search (long
night clips) sent ~2.3 MB and took 4-5 seconds. Nothing reads the middle entries: the web app and the server's
brief listing use only the first and last entries (start and end time) and the frame count.

History: run on cmoserver01 on 2026-11-07: 3,891 files, 762.9 MB -> 4.1 MB, none unreadable. Originals kept in
~/me_data_before_slim_20261107.tar.gz on cmoserver01 (59 MB), and ada's copy
(~/cmo_server_ada/local/uploads) was left unconverted. Not yet run on ada or on cmo_test_data.

To undo on a server: tar xzf the archive inside its uploads folder (it restores only the files it holds).
"""
import json, os, sys, time
from datetime import datetime, timezone

root = sys.argv[1]
apply = "--apply" in sys.argv
list_file = sys.argv[sys.argv.index("--list") + 1] if "--list" in sys.argv else None
todo, before, after, bad = [], 0, 0, []

for dirpath, dirs, files in os.walk(root):
    dirs.sort()
    if "me_data.json" not in files:
        continue
    p = os.path.join(dirpath, "me_data.json")
    try:
        with open(p) as f:
            d = json.load(f)
    except Exception as e:
        bad.append((p, str(e)))
        continue
    deltas = d.get("me_delta_array")
    images = d.get("me_image_array")
    if not isinstance(deltas, list) or (len(deltas) <= 2 and not images):
        continue
    new = dict(d)
    new["me_delta_array"] = deltas[:1] + deltas[1:][-1:]
    new.setdefault("me_frame_count", len(deltas))
    new["me_image_array"] = []
    new["me_slimmed"] = {"at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
                         "delta_count": len(deltas), "image_count": len(images or []), "by": "slim_me_data.py"}
    text = json.dumps(new, indent=2)
    json.loads(text)  # sanity
    old_size = os.path.getsize(p)
    before += old_size
    after += len(text.encode())
    todo.append(p)
    if apply:
        st = os.stat(p)
        tmp = p + ".slim.tmp"
        with open(tmp, "w") as f:
            f.write(text)
            f.flush()
            os.fsync(f.fileno())
        os.utime(tmp, ns=(st.st_atime_ns, st.st_mtime_ns))
        os.replace(tmp, p)
        if len(todo) % 200 == 0:
            time.sleep(0.2)  # gentle on the live server

if list_file:
    with open(list_file, "w") as f:
        f.write("\n".join(os.path.relpath(p, root) for p in todo) + "\n")
print(f"{'changed' if apply else 'would change'} {len(todo)} files: {before/1048576:.1f} MB -> {after/1048576:.1f} MB")
print(f"unreadable (left alone): {len(bad)}")
for p, e in bad[:5]:
    print("  ", p, e)
