"""Select a release version without committing generated version bumps to main."""

import re
import subprocess
import sys
import xml.etree.ElementTree as ET


VERSION = re.compile(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)")


def parse_version(value):
    match = VERSION.fullmatch(value)
    if not match:
        raise ValueError(f"Expected a stable major.minor.patch version, got {value!r}")
    return tuple(map(int, match.groups()))


def git(*arguments):
    return subprocess.check_output(["git", *arguments], text=True).strip()


def is_ancestor(ancestor, descendant):
    result = subprocess.run(["git", "merge-base", "--is-ancestor", ancestor, descendant])
    if result.returncode not in (0, 1):
        raise RuntimeError("Could not verify release ancestry")
    return result.returncode == 0


def resolve_version(requested_tag=""):
    commit = git("rev-parse", "HEAD")
    if not is_ancestor(commit, "origin/main"):
        raise ValueError("Only commits already merged into main can be released")

    base_text = ET.parse("pom.xml").getroot().findtext("{*}version")
    base = parse_version(base_text or "")
    tags = {
        tag: git("rev-parse", f"refs/tags/{tag}^{{commit}}")
        for tag in git("tag", "--list", "v*").splitlines()
        if VERSION.fullmatch(tag[1:])
    }
    if requested_tag:
        if requested_tag not in tags or tags[requested_tag] != commit:
            raise ValueError("Retry tag must be an existing v<major.minor.patch> tag at HEAD")
        return requested_tag[1:], True, commit

    existing = [tag for tag, target in tags.items() if target == commit]
    if len(existing) > 1:
        raise ValueError("Multiple release tags point to this commit; specify the retry tag")
    if existing:
        return existing[0][1:], True, commit

    version = base
    if tags:
        latest = max(tags, key=lambda tag: parse_version(tag[1:]))
        if not is_ancestor(tags[latest], commit):
            raise ValueError("A new release must include the latest release commit")
        major, minor, patch = parse_version(latest[1:])
        version = max(base, (major, minor, patch + 1))
    return ".".join(map(str, version)), False, commit


if __name__ == "__main__":
    try:
        version, exists, commit = resolve_version(sys.argv[1] if len(sys.argv) > 1 else "")
    except (ValueError, RuntimeError, subprocess.CalledProcessError) as error:
        sys.exit(str(error))
    print(f"version={version}\ntag=v{version}\ntag_exists={str(exists).lower()}\ncommit={commit}")
