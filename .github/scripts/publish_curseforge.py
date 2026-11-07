#!/usr/bin/env python3
"""Uploads the built AeroFlux jars to CurseForge.

Usage:
  publish_curseforge.py --artifacts DIR --version 2026.11.23 --changelog-file notes.md [--release-type beta] [--dry-run]
  publish_curseforge.py --list-versions

DIR holds one folder per build, named like the CI artifacts (aeroflux-forge-1.21.1, aeroflux-neoforge-26.1.2, ...), each with the
jars Gradle produced. The upload token is read from the CURSEFORGE_TOKEN environment variable and is never printed.
Only the Python standard library is used.
"""
import argparse
import json
import os
import sys
import urllib.error
import urllib.request
import uuid
from pathlib import Path

PROJECT_ID = 1778368
API = "https://minecraft.curseforge.com/api"

# Optional mods AeroFlux works with, as CurseForge slugs. Which ones exist depends on the Minecraft version and loader.
RELATIONS_26_NEO = ["jade", "the-one-probe", "jei", "roughly-enough-items", "kubejs", "applied-energistics-2"]
RELATIONS_26_2_NEO = ["jade", "the-one-probe", "jei", "roughly-enough-items", "applied-energistics-2", "wthit"]
RELATIONS_26_3_NEO = ["jade", "the-one-probe", "jei", "roughly-enough-items", "applied-energistics-2"]
RELATIONS_OLDER = ["jade", "the-one-probe", "jei", "roughly-enough-items", "emi", "kubejs", "applied-energistics-2", "wthit", "crafttweaker"]

# module -> (Minecraft version, loader name on CurseForge, Java version name on CurseForge, optional mods)
BUILDS = {
    "forge-26.3": ("26.3", "Forge", "Java 25", []),
    "neoforge-26.3": ("26.3", "NeoForge", "Java 25", RELATIONS_26_3_NEO),
    "forge-26.2": ("26.2", "Forge", "Java 25", ["wthit"]),
    "neoforge-26.2": ("26.2", "NeoForge", "Java 25", RELATIONS_26_2_NEO),
    "forge-26.1.2": ("26.1.2", "Forge", "Java 25", ["wthit"]),
    "neoforge-26.1.2": ("26.1.2", "NeoForge", "Java 25", RELATIONS_26_NEO),
    "forge-1.21.1": ("1.21.1", "Forge", "Java 21", ["jei"]),
    "neoforge-1.21.1": ("1.21.1", "NeoForge", "Java 21", RELATIONS_OLDER),
    "forge-1.20.1": ("1.20.1", "Forge", "Java 17", RELATIONS_OLDER),
    "neoforge-1.20.1": ("1.20.1", "NeoForge", "Java 17", RELATIONS_OLDER),
}
RELEASE_TYPES = ("alpha", "beta", "release")


def token():
    value = os.environ.get("CURSEFORGE_TOKEN", "")
    if not value:
        sys.exit("CURSEFORGE_TOKEN is not set")
    return value


def request(url, data=None, headers=None, fail=True):
    req = urllib.request.Request(url, data=data, headers={"X-Api-Token": token(), **(headers or {})})
    try:
        with urllib.request.urlopen(req, timeout=120) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", "replace")[:500]
        if not fail:
            return None
        sys.exit(f"CurseForge answered {error.code} for {url}: {body}")


def game_versions():
    return request(f"{API}/game/versions")


def resolve(versions, name):
    matches = [v for v in versions if v["name"] == name]
    if not matches:
        close = sorted({v["name"] for v in versions if name.split(".")[0] in v["name"]})[:15]
        sys.exit(f"CurseForge has no game version named '{name}'. Similar names: {close}")
    return matches[0]["id"]


def pick_jar(folder, module, version):
    jars = [p for p in sorted(folder.glob("*.jar")) if version in p.name and not any(p.name.endswith(s) for s in ("-sources.jar", "-slim.jar", "-javadoc.jar"))]
    if len(jars) != 1:
        sys.exit(f"Expected exactly one jar to upload for {module} in {folder}, found {[p.name for p in jars]}")
    return jars[0]


def clean_name(module, version):
    """The file name people download: aeroflux-<loader>-mc<minecraft>-<version>.jar."""
    minecraft, loader, _, _ = BUILDS[module]
    return f"aeroflux-{loader.lower()}-mc{minecraft}-{version}.jar"


def multipart(fields, file_field, path, name):
    boundary = uuid.uuid4().hex
    lines = []
    for field, value in fields.items():
        lines.append((f"--{boundary}\r\nContent-Disposition: form-data; name=\"{field}\"\r\n"
                      f"Content-Type: application/json\r\n\r\n{value}\r\n").encode("utf-8"))
    lines.append((f"--{boundary}\r\nContent-Disposition: form-data; name=\"{file_field}\"; filename=\"{name}\"\r\n"
                  "Content-Type: application/java-archive\r\n\r\n").encode("utf-8"))
    lines.append(path.read_bytes())
    lines.append(f"\r\n--{boundary}--\r\n".encode("utf-8"))
    return b"".join(lines), f"multipart/form-data; boundary={boundary}"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--artifacts", type=Path)
    parser.add_argument("--version")
    parser.add_argument("--changelog-file", type=Path)
    parser.add_argument("--release-type", default="beta", choices=RELEASE_TYPES)
    parser.add_argument("--dry-run", action="store_true", help="resolve everything and show the plan without uploading")
    parser.add_argument("--collect", type=Path, help="copy the jars into this folder under their download names and stop (no token needed)")
    parser.add_argument("--list-versions", action="store_true", help="print the game versions CurseForge knows (read only)")
    args = parser.parse_args()

    if args.list_versions:
        for v in game_versions():
            print(v["id"], v["name"])
        return
    if args.collect:
        if not (args.artifacts and args.version):
            parser.error("--collect needs --artifacts and --version")
        args.collect.mkdir(parents=True, exist_ok=True)
        for module in BUILDS:
            jar = pick_jar(args.artifacts / f"aeroflux-{module}", module, args.version)
            target = args.collect / clean_name(module, args.version)
            target.write_bytes(jar.read_bytes())
            print(f"{module}: {jar.name} -> {target.name}")
        return
    if not (args.artifacts and args.version and args.changelog_file):
        parser.error("--artifacts, --version and --changelog-file are required")

    versions = game_versions()
    changelog = args.changelog_file.read_text(encoding="utf-8")
    plan = []
    for module, (minecraft, loader, java, relations) in BUILDS.items():
        folder = args.artifacts / f"aeroflux-{module}"
        if not folder.is_dir():
            sys.exit(f"Missing build folder {folder}")
        jar = pick_jar(folder, module, args.version)
        # CurseForge also wants an environment (Client and Server): AeroFlux runs on both.
        ids = [resolve(versions, minecraft), resolve(versions, loader), resolve(versions, "Client"), resolve(versions, "Server")]
        try:
            ids.append(resolve(versions, java))
        except SystemExit:
            print(f"note: CurseForge has no '{java}', uploading {module} without a Java version")
        plan.append((module, jar, ids, f"AeroFlux {args.version} ({loader} {minecraft})", clean_name(module, args.version), relations))

    for module, jar, ids, title, name, relations in plan:
        print(f"{title}: {name} ({jar.stat().st_size // 1024} KiB) game version ids {ids}, {args.release_type}")
    if args.dry_run:
        print("dry run: nothing uploaded")
        return

    for module, jar, ids, title, name, relations in plan:
        fields = {
            "changelog": changelog,
            "changelogType": "markdown",
            "displayName": title,
            "gameVersions": ids,
            "releaseType": args.release_type,
        }
        # A slug CurseForge does not know fails the whole upload, so the optional mods are tried first
        # and left out on a second attempt.
        for attempt in (relations, []):
            metadata = json.dumps({**fields, "relations": {"projects": [{"slug": slug, "type": "optionalDependency"} for slug in attempt]}})
            body, content_type = multipart({"metadata": metadata}, "file", jar, name)
            result = request(f"{API}/projects/{PROJECT_ID}/upload-file", data=body, headers={"Content-Type": content_type}, fail=not attempt)
            if result is not None:
                break
            print(f"note: {name} was refused with the optional mods listed; trying again without them")
        print(f"uploaded {name} as file id {result.get('id')}")

if __name__ == "__main__":
    main()
