#!/usr/bin/env python3
"""
Self-test for tools/kotlin-sanity.py.

The scanner is only worth running if it actually catches the failure it was
written for. A gate that passes everything is worse than no gate, because it is
trusted. These cases are the ones that have bitten this project:

  - a KDoc mentioning a mime wildcard, where the */ inside it closes the comment
    early and the rest of the file is parsed as top-level declarations
  - a string template holding a nested string, e.g. "${a.joinToString("\n")}"
  - a char literal that contains a double quote
  - an elvis operator continuing onto the next line
  - a genuine brace imbalance

Every negative case below must be reported; every positive case must be clean.
"""
import os
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
SCAN = os.path.join(HERE, "kotlin-sanity.py")

MUST_FAIL = {
    "early-closing KDoc from a mime wildcard": '''package a

/**
 * 支持的格式包括 */* 这种通配。
 */
val mime = "*/*"
''',
    "unterminated block comment": '''package a

/* never closed
val x = 1
''',
    "missing closing brace": '''package a

fun f() {
    val x = 1
''',
    "prose dropped at top level": '''package a

val x = 1
这句话不该出现在这里
''',
}

MUST_PASS = {
    "mime wildcard written so it cannot close a comment": '''package a

/**
 * 支持的格式包括 {@code *}{@code /}{@code *} 这种通配。
 */
val mime = "*/*"
''',
    "nested string inside a template": '''package a

fun f(a: List<String>) = "head ${a.joinToString("\\n")} tail"
''',
    "char literal containing a double quote": '''package a

fun f() = "${'"'}" + "a\\"b"
''',
    "elvis and chained-call continuations": '''package a

fun f(v: String?) = v?.trim()
    ?: "fallback"

fun g(v: String) = v.trim()
    .uppercase()
    .length
''',
    "multiline raw string": '''package a

val q = """
    a "quoted" line
    { brace } and ( paren )
"""
''',
}


def run(source):
    with tempfile.NamedTemporaryFile("w", suffix=".kt", delete=False,
                                     encoding="utf-8") as fh:
        fh.write(source)
        path = fh.name
    try:
        proc = subprocess.run([sys.executable, SCAN, path],
                              capture_output=True, text=True)
        return proc.returncode, proc.stdout
    finally:
        os.unlink(path)


def main():
    failures = []
    for name, src in MUST_FAIL.items():
        code, out = run(src)
        if code == 0:
            failures.append(f"MISSED  {name} (reported clean)")
        else:
            print(f"ok    rejected: {name}")
    for name, src in MUST_PASS.items():
        code, out = run(src)
        if code != 0:
            detail = " / ".join(l.strip() for l in out.splitlines()[2:6])
            failures.append(f"FALSE POSITIVE  {name} -> {detail}")
        else:
            print(f"ok    accepted: {name}")

    if failures:
        print("\nself-test FAILED:")
        for f in failures:
            print("  " + f)
        return 1
    print("\nself-test passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())