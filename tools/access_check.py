#!/usr/bin/env python3
"""End-to-end access check against a running cmo_server (google mode) and its uploads folder.

For each test user it works out from the files on disk exactly which clips that user's grants allow, then pages
through the server's searches (all cameras, each camera, every label, AND combinations, without=person, latest)
and checks the server shows exactly those, refuses forbidden cameras, and answers 403/404 for direct links to a
sample of forbidden clips, pictures and movies. Run 2026-11-07 against ada's copy of the clips (6,274) with seven
users (admin, full, friends, cats-only, porch full + pet door animals, world animals, porch only): 0 failures.

Usage: access_check.py DIR  where DIR/hard/cookies.txt holds "who: <cmo_session cookie>" lines for the users in
RULES, and the server runs at http://127.0.0.1:9191 with the matching access.json. ROOT is the uploads folder.
"""
import json, os, glob, sys, urllib.request, urllib.error, random
S = sys.argv[1]; B = "http://127.0.0.1:9191"; ROOT = "/home/mm/cmo_server_ada/local/uploads"
cookies = dict(l.split(": ", 1) for l in open(S + "/hard/cookies.txt").read().split("\n") if l)
PEOPLE = ("person", "legs", "delivery")
def low(o): return {k.lower(): v for k, v in (o or {}).items() if not k.startswith("me_")}
ALL = lambda o: True
def ANIMALS(o):
    o = low(o)
    return o.get("person") is False and not any(o.get(p) is True for p in PEOPLE) and any(o.get(a) is True for a in ("cat", "dog", "raccoon"))
def CATS(o): return low(o).get("cat") is True
RULES = {
    "admin": {"pet_door": ALL, "porch_cam": ALL}, "full": {"pet_door": ALL, "porch_cam": ALL},
    "friend": {"pet_door": ANIMALS}, "cats": {"pet_door": CATS}, "mixed": {"porch_cam": ALL, "pet_door": ANIMALS},
    "fan": {"pet_door": ANIMALS, "porch_cam": ANIMALS}, "porch": {"porch_cam": ALL},
}
# every clip on disk the search can list: me_data.json readable with me_time > 0
clips = {}
for cam in ("pet_door", "porch_cam"):
    for d in glob.glob(f"{ROOT}/{cam}/*/*/"):
        try: md = json.load(open(d + "me_data.json"))
        except Exception: continue
        if not (isinstance(md.get("me_time"), (int, float)) and md["me_time"] > 0): continue
        try: ol = json.load(open(d + "me_ollama.json"))
        except Exception: ol = None
        clips[(cam, os.path.basename(d.rstrip("/")))] = ol
print("clips on disk:", len(clips))
def get(who, q):
    r = urllib.request.Request(B + q, headers={"Cookie": "cmo_session=" + cookies[who]})
    try:
        with urllib.request.urlopen(r, timeout=60) as resp: return resp.status, json.loads(resp.read() or b"null")
    except urllib.error.HTTPError as e: return e.code, None
def page_all(who, base, max_pages=500):
    out, before = [], None
    for _ in range(max_pages):
        st, page = get(who, base + "&howMayRecords=100&minimumDuration=0&brief=true" + (f"&before={before}" if before else ""))
        if st != 200: return st, out
        out += page
        if len(page) < 100: break
        before = page[-1]["me_name"]
    return 200, out
def has(o, l):
    o = low(o); return o.get(l) is True or (l == "person" and o.get("legs") is True)
fails = []
def check(cond, msg):
    if not cond: fails.append(msg)
for who, rules in RULES.items():
    allowed = lambda cam, ol: cam in rules and rules[cam](ol)
    expected = {k for k, ol in clips.items() if allowed(k[0], ol)}
    st, got = page_all(who, "/catcam/search?x=1")
    gotset = {(e["me_group"], e["me_name"]) for e in got}
    check(st == 200, f"{who}: all-cameras search {st}")
    check(gotset == expected, f"{who}: all-cameras set differs: missing {len(expected - gotset)} extra {len(gotset - expected)} e.g. extra {list(gotset - expected)[:2]} missing {list(expected - gotset)[:2]}")
    check(len(got) == len(gotset), f"{who}: duplicates in paging")
    names = [e["me_name"] for e in got]; check(names == sorted(names, reverse=True), f"{who}: not newest first")
    summary = [f"{who}: sees {len(gotset)} (expected {len(expected)})"]
    # each camera on its own
    for cam in ("pet_door", "porch_cam"):
        st, got = page_all(who, f"/catcam/search/{cam}?x=1", 60)
        if cam not in rules:
            check(st == 403, f"{who}: {cam} search should be refused, got {st}")
        else:
            check(st == 200 and all(allowed(cam, e.get("me_ollama")) and e["me_group"] == cam for e in got), f"{who}: {cam} search shows a clip it shouldn't")
        st2, _ = get(who, f"/catcam/latest/{cam}?n=20")
        check((st2 == 200) == (cam in rules), f"{who}: latest/{cam} gave {st2}")
        st3, got3 = page_all(who, f"/catcam/search?cameras={cam}", 60)
        check(st3 == 200 and all(e["me_group"] == cam and allowed(cam, e.get("me_ollama")) for e in got3), f"{who}: cameras={cam} leaks")
        if cam not in rules: check(len(got3) == 0, f"{who}: cameras={cam} should be empty, got {len(got3)}")
    # every label, and AND combinations
    for labels in (["cat"], ["dog"], ["raccoon"], ["person"], ["legs"], ["delivery"], ["animal"], ["cat", "dog"], ["cat", "raccoon"], ["dog", "person"], ["cat", "dog", "raccoon"]):
        st, got = page_all(who, "/catcam/search?labels=" + ",".join(labels) + "&match=all", 30)
        bad = [e["me_name"] for e in got if not allowed(e["me_group"], e.get("me_ollama")) or not all(has(e.get("me_ollama"), l) for l in labels)]
        check(st == 200 and not bad, f"{who}: labels={labels} shows wrong clips {bad[:3]}")
        exp = {k for k, ol in clips.items() if allowed(k[0], ol) and all(has(ol, l) for l in labels)}
        if len(got) < 3000: check({(e['me_group'], e['me_name']) for e in got} == exp, f"{who}: labels={labels}: {len(got)} found, {len(exp)} expected")
    st, got = page_all(who, "/catcam/search?without=person", 30)
    check(all(not has(e.get("me_ollama"), "person") and allowed(e["me_group"], e.get("me_ollama")) for e in got), f"{who}: without=person leaks")
    st, got = get(who, "/catcam/latest?n=50")
    check(st == 200 and all(allowed(e["me_group"], e.get("me_ollama")) for e in got), f"{who}: latest leaks")
    # direct links: a sample of clips this user must not see, and some they may
    forbidden = [k for k, ol in clips.items() if not allowed(k[0], ol)]
    permitted = [k for k, ol in clips.items() if allowed(k[0], ol)]
    random.seed(1)
    for (cam, name) in random.sample(forbidden, min(40, len(forbidden))):
        date = name[:8]
        for path in (f"/catcam/event/{cam}/{date}/{name}", f"/catcam/data/{cam}/{date}/{name}/me_rep_image.jpg",
                     f"/catcam/data/{cam}/{date}/{name}/me_movie.mp4", f"/catcam/data/{cam}/{date}/{name}/me_ollama.json"):
            st, _ = get(who, path)
            check(st in (403, 404), f"{who}: forbidden {path} gave {st}")
    for (cam, name) in random.sample(permitted, min(10, len(permitted))):
        st, _ = get(who, f"/catcam/event/{cam}/{name[:8]}/{name}")
        check(st == 200, f"{who}: permitted event {cam}/{name} gave {st}")
    # what the web app is told: per-camera labels/hidden
    st, groups = get(who, "/catcam/megroups")
    summary.append("cameras " + ", ".join(f"{g['meGroup']}(labels={g['labels']}, hidden={g['hidden']})" for g in groups))
    check(sorted(g["meGroup"] for g in groups) == sorted(rules), f"{who}: megroups {groups}")
    # changing things
    if permitted:
        cam, name = permitted[0]
        st, _ = get(who, f"/auth/admin/requests")
        check((st == 200) == (who == "admin"), f"{who}: admin data gave {st}")
    print(" | ".join(summary))
print()
print("FAILURES:", len(fails))
for f in fails[:40]: print("  ", f)
