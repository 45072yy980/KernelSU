#!/usr/bin/env python3
"""Catch the comment bug that cost us 575 compiler errors twice.

Kotlin's block comments **nest**, so writing a path such as ``ui/screen/*`` inside
a KDoc opens a second comment level; the file then swallows the rest of itself and
the compiler reports the damage somewhere else entirely (we saw "unresolved
reference UiMode" 575 times because UiMode.kt itself failed to compile).

A full lexer is needed to find every case, and a raw-string delimiter
ambiguous: two empty quotes followed by a quote look like a raw-string opener.
This check targets the shape
that actually bit us and has no false positives: a documentation continuation line
(starting with ``*``) that contains ``/*``.

Usage: python3 scripts/check-kdoc-nesting.py [root ...]
Exit code 1 when such a line is found.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

SUFFIXES = {".kt", ".kts", ".java"}
COMMENT_LINE = re.compile(r"^\s*\*.*/\*")


def main(argv: list[str]) -> int:
    roots = [Path(a) for a in argv[1:]] or [Path("manager")]
    hits = 0
    checked = 0
    for root in roots:
        paths = [root] if root.is_file() else sorted(root.rglob("*"))
        for path in paths:
            if not path.is_file() or path.suffix not in SUFFIXES:
                continue
            checked += 1
            with path.open(encoding="utf-8", errors="replace") as handle:
                for number, line in enumerate(handle, 1):
                    if COMMENT_LINE.match(line):
                        hits += 1
                        print(f"{path}:{number}: '/*' inside a block comment nests it - use '...' instead")
    if hits:
        print(f"\n{hits} nested-comment line(s) in {checked} file(s).")
        return 1
    print(f"{checked} file(s) checked, no '/*' inside documentation lines.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
