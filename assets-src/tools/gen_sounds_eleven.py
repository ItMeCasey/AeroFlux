"""Machine sounds from the ElevenLabs sound-effects API (same call pattern as Dust & Dominion's
tools/audio/render_sfx.py). Two takes per sound, written as raw mp3 to assets-src/aeroflux_sounds/eleven/,
then converted to mono Ogg Vorbis in eleven_ogg/. Existing files are skipped, so runs resume.
Key: ELEVENLABS_API_KEY (environment or the Windows user environment); never printed.

  python gen_sounds_eleven.py --dry-run
  python gen_sounds_eleven.py                # everything not yet made
  python gen_sounds_eleven.py --ids furnace_loop --force
"""
import argparse
import csv
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "aeroflux_sounds")
RAW = os.path.join(ROOT, "eleven")
OGG = os.path.join(ROOT, "eleven_ogg")
INFLUENCE = 0.6

# id, seconds, loop, prompt. Prompts stand alone and say what to leave out.
CUES = [
    ("furnace_loop", 6, True, "Close-up of a stone furnace burning steadily: a low fire roar with crackling embers and soft pops. No music, no voices."),
    ("grinder_loop", 6, True, "A heavy industrial grinder running continuously: gritty crushing of stone, a low motor whine, an occasional metal clink. No music."),
    ("smelter_loop", 6, True, "A big crucible of molten metal bubbling and hissing, a low furnace roar and slow thick bubbles popping. No music."),
    ("sawmill_loop", 6, True, "An electric circular saw spinning and cutting wood in repeated passes, whirring blade, chunks of wood. No music."),
    ("infuser_loop", 6, True, "A magical crystal infusion machine humming, soft ethereal shimmering tones with gentle high sparkling chimes, calm and mystical. No melody, no voices."),
    ("enchanter_loop", 6, True, "Enchanting magic: a warm glowing hum with floating sparkle chimes and a soft whispering swirl, mystical. No melody, no voices."),
    ("disenchanter_loop", 6, True, "A dark magic draining machine: a deep ominous low drone, reversed whooshes and eerie descending tones, subtle crackling. No melody, no voices."),
    ("grower_loop", 6, True, "A quiet greenhouse machine: soft electrical hum, gentle water drips and rustling leaves, calm and natural. No music."),
    ("start", 1.2, False, "A heavy machine powering up: an electric hum spinning up and rising quickly."),
    ("stop", 1.5, False, "A machine powering down: an electric whine winding down and falling away."),
    ("done", 1.0, False, "Two short bright chimes, a clean pleasant completion ding."),
]


def api_key():
    key = os.environ.get("ELEVENLABS_API_KEY")
    if key:
        return key
    try:
        import winreg
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, "Environment") as h:
            return winreg.QueryValueEx(h, "ELEVENLABS_API_KEY")[0]
    except Exception:
        sys.exit("ELEVENLABS_API_KEY is not set")


def generate(key, seconds, loop, prompt, retries=5):
    body = {"text": prompt, "duration_seconds": max(0.5, min(30.0, float(seconds))),
            "prompt_influence": INFLUENCE, "model_id": "eleven_text_to_sound_v2"}
    if loop:
        body["loop"] = True
    req = urllib.request.Request(
        "https://api.elevenlabs.io/v1/sound-generation?output_format=mp3_44100_128",
        data=json.dumps(body).encode(), headers={"xi-api-key": key, "Content-Type": "application/json"})
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(req, timeout=120) as f:
                return f.read(), f.headers.get("character-cost", "")
        except urllib.error.HTTPError as e:
            if e.code in (429, 500, 502, 503) and attempt < retries - 1:
                time.sleep(2 ** attempt * 2)
                continue
            raise RuntimeError(f"HTTP {e.code}: {e.read().decode()[:200]}")
    raise RuntimeError("retries exhausted")


def to_ogg(src, dst):
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", src, "-ac", "1", "-c:a", "libvorbis", "-q:a", "4", dst],
                   check=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ids")
    ap.add_argument("--variants", type=int, default=2)
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--force", action="store_true")
    a = ap.parse_args()
    want = set(a.ids.split(",")) if a.ids else None
    jobs = []
    for cid, secs, loop, prompt in CUES:
        if want and cid not in want:
            continue
        for n in range(1, a.variants + 1):
            dst = os.path.join(RAW, f"{cid}_{n}.mp3")
            if os.path.exists(dst) and not a.force:
                continue
            jobs.append((cid, secs, loop, prompt, dst))
    print(f"{len(jobs)} takes, {sum(j[1] for j in jobs):.0f} seconds of audio")
    if a.dry_run or not jobs:
        return
    key = api_key()
    os.makedirs(RAW, exist_ok=True)
    os.makedirs(OGG, exist_ok=True)
    logp = os.path.join(RAW, "render_log.csv")
    new = not os.path.exists(logp)
    logf = open(logp, "a", newline="", encoding="utf-8")
    log = csv.writer(logf)
    if new:
        log.writerow(["file", "seconds_requested", "character_cost"])

    def work(j):
        cid, secs, loop, prompt, dst = j
        audio, cost = generate(key, secs, loop, prompt)
        with open(dst, "wb") as f:
            f.write(audio)
        to_ogg(dst, os.path.join(OGG, os.path.basename(dst)[:-4] + ".ogg"))
        return os.path.basename(dst), secs, cost

    done = failed = 0
    with ThreadPoolExecutor(3) as ex:
        for fut in [ex.submit(work, j) for j in jobs]:
            try:
                log.writerow(fut.result())
                logf.flush()
                done += 1
            except Exception as e:
                failed += 1
                print("FAILED:", e)
    logf.close()
    print(f"done {done}, failed {failed}")


if __name__ == "__main__":
    main()
