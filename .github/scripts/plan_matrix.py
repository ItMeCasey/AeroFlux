#!/usr/bin/env python3
"""Pick the builds a workflow run needs, so unchanged versions are never built.

  plan_matrix.py build   [--before SHA] [--sha SHA] [--files F...]   builds whose files changed in the push or pull request
  plan_matrix.py release [--tag vYYYY.M.N] [--require-any]           builds whose own mod_version equals the tag

How AeroFlux shares code (checked in every build's build.gradle and in shared-core/):
  versions/mc<minecraft>/<loader>/   one self-contained gradle project per build (sources, libs, resources)
  shared-core/src/main/java          compiled into EVERY build as an extra source dir (no Minecraft classes)
  shared-core/ (tests, gradle files) only the shared-core unit test job uses them, no build compiles them
  lang/, assets-src/tools/           only the translation check uses them (translations are installed into each
                                     build's src/main/resources by lang_sync.py and committed there)
Nothing else is read by a build, so docs, notes, art sources and CI files select no build. Anything not
listed selects every build (fail safe).

Writes `matrix=<json>`, `any=true|false` and `core=true|false` (the shared-core unit tests and translation
check job) to $GITHUB_OUTPUT, plus `version=<release version>` in release mode (and prints them).
"""
import argparse
import json
import os
import re
import subprocess
import sys

# (module, folder, java)
ALL = [
    ("neoforge-26.3", "versions/mc26.3/neoforge", 25),
    ("forge-26.3", "versions/mc26.3/forge", 25),
    ("neoforge-26.2", "versions/mc26.2/neoforge", 25),
    ("forge-26.2", "versions/mc26.2/forge", 25),
    ("neoforge-26.1.2", "versions/mc26.1.2/neoforge", 25),
    ("forge-26.1.2", "versions/mc26.1.2/forge", 25),
    ("neoforge-1.21.1", "versions/mc1.21.1/neoforge", 21),
    ("forge-1.21.1", "versions/mc1.21.1/forge", 21),
    ("neoforge-1.20.1", "versions/mc1.20.1/neoforge", 17),
    ("forge-1.20.1", "versions/mc1.20.1/forge", 17),
]
ZERO = "0" * 40

SHARED_JAVA = "shared-core/src/main/java/"  # compiled into every build
# Used only by the shared-core unit test job and the translation check, never compiled into a build.
CORE_ONLY_PREFIXES = ("shared-core/", "lang/", "assets-src/tools/lang_sync.py")
# Files and folders no build and no check reads.
NONE_PREFIXES = (
    "assets-src/", "branding/", "curseforge/", "docs/", "wiki/", "dist/", "logs/", "reference/", "concept/",
    ".github/workflows/", ".github/scripts/", ".github/ISSUE_TEMPLATE/", ".claude/",
)
NONE_EXACT = {
    ".gitignore", ".gitattributes", "LICENSE", "PROJECT_STATUS.md", "PLAN.md", "RODIN_PROMPTS.md",
    ".github/pull_request_template.md",
}
NONE_SUFFIXES = (".md", ".log", ".err", ".out")
NONE_GLOBS = (re.compile(r"^versions/[^/]*-results\.txt$"),)
# Inside one build folder: run folders, logs and build output are not inputs of the jar.
MODULE_NONE = re.compile(r"^(run[^/]*|logs|build|\.gradle|devlibs)/")
# Inside one build folder: files the translation check reads.
MODULE_CORE = re.compile(r"^src/(generated|main)/resources/assets/aeroflux/lang/")
MODULE_PATH = re.compile(r"^versions/mc([^/]+)/(forge|neoforge)/(.*)$")


def changed_files(before, sha):
    """Files changed between two commits; None when git cannot say (the caller then builds everything)."""
    if not before or before == ZERO:
        # first push of a branch or a manual run: compare with the parent commit
        before = sha + "^"
    try:
        out = subprocess.run(["git", "diff", "--name-only", before, sha], capture_output=True, text=True)
    except OSError:
        return None
    if out.returncode != 0:
        return None
    return [line.strip() for line in out.stdout.splitlines() if line.strip()]


def module_version(folder):
    try:
        with open(os.path.join(folder, "gradle.properties"), encoding="utf-8") as fh:
            text = fh.read()
    except OSError:
        return None
    m = re.search(r"^mod_version=(\S+)", text, re.M)
    return m.group(1) if m else None


def plan_for_files(files):
    """(entries, core) for a list of changed paths. None for the list means unknown, so everything."""
    if files is None:
        return list(ALL), True
    chosen = set()
    core = False
    for raw in files:
        f = raw.replace("\\", "/")
        if f.startswith("./"):
            f = f[2:]
        if f.startswith(SHARED_JAVA):
            return list(ALL), True  # shared by every build and by the unit tests
        if f.startswith(CORE_ONLY_PREFIXES):
            core = True
            continue
        if f in NONE_EXACT or f.startswith(NONE_PREFIXES):
            continue
        m = MODULE_PATH.match(f)
        if m:
            folder = f"versions/mc{m.group(1)}/{m.group(2)}"
            if not any(entry[1] == folder for entry in ALL):
                return list(ALL), True  # a build folder this script does not know: be safe
            rest = m.group(3)
            if MODULE_NONE.match(rest):
                continue
            chosen.add(folder)
            if MODULE_CORE.match(rest):
                core = True
            continue
        if any(g.match(f) for g in NONE_GLOBS) or f.endswith(NONE_SUFFIXES):
            continue  # notes, logs and test result summaries never reach a jar
        return list(ALL), True  # unknown path: build everything
    return [entry for entry in ALL if entry[1] in chosen], core


def pick_for_files(files):
    return plan_for_files(files)[0]


def pick_for_release(version, root="."):
    """Entries whose own mod_version equals the release version."""
    return [entry for entry in ALL if module_version(os.path.join(root, entry[1])) == version]


def newest_version(root="."):
    found = {module_version(os.path.join(root, e[1])) for e in ALL} - {None}
    key = lambda v: [int(x) if x.isdigit() else 0 for x in v.split(".")]
    return sorted(found, key=key)[-1] if found else ""


def matrix_for(picked, release):
    include = []
    for name, folder, java in picked:
        item = {"name": name, "dir": folder}
        if release:
            item["java"] = java
        include.append(item)
    return {"include": include}


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("mode", choices=["build", "release"])
    ap.add_argument("--before")
    ap.add_argument("--sha", default="HEAD")
    ap.add_argument("--tag", help="release version, with or without the leading v; empty means the newest version")
    ap.add_argument("--files", nargs="*", help="changed files to classify instead of asking git (for tests)")
    ap.add_argument("--require-any", action="store_true", help="release mode: exit 1 when no build has the release version")
    args = ap.parse_args(argv)

    extra = []
    version = ""
    if args.mode == "release":
        version = (args.tag or "").lstrip("v") or newest_version()
        picked = pick_for_release(version)
        core = False
        extra.append(f"version={version}")
    else:
        files = args.files if args.files is not None else changed_files(args.before, args.sha)
        picked, core = plan_for_files(files)

    matrix = matrix_for(picked, args.mode == "release")
    lines = [
        f"matrix={json.dumps(matrix)}",
        f"any={'true' if picked else 'false'}",
        f"core={'true' if core else 'false'}",
        *extra,
    ]
    print("\n".join(lines))
    print("builds:", ", ".join(m[0] for m in picked) or "none", "| shared-core job:", "yes" if core else "no")
    out = os.environ.get("GITHUB_OUTPUT")
    if out:
        with open(out, "a", encoding="utf-8") as fh:
            fh.write("\n".join(lines) + "\n")
    if args.mode == "release" and args.require_any and not picked:
        print(f"No build has mod_version {version}, so there is nothing to release", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
