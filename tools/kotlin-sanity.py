#!/usr/bin/env python3
"""
Pre-compile source sanity scan for the Oxide launcher.

Run before every compile or release build. It is a lexer-level pass, not a
compiler: it strips Kotlin comments, string literals (including raw strings and
${} templates) and character literals, then reports anything that cannot be part
of a well-formed source file.

The check that earns its keep is the top-level one. Kotlin block comments nest,
so `*/` inside a comment is legal and silently terminates it - which is exactly
how a KDoc mentioning a mime wildcard like */* once turned three lines of prose
into top-level declarations and produced 39 compiler errors that pointed at the
wrong line. A compiler reports the cascade; this reports the cause.

Usage:
    python3 tools/kotlin-sanity.py [root ...]

Exit status is 0 when clean, 1 when anything was reported.
"""
import os
import sys

# Tokens that may legitimately begin a line at nesting depth 0.
TOP_LEVEL = (
    "package ", "import ", "@", "class ", "object ", "interface ", "fun ",
    "val ", "var ", "enum ", "sealed ", "typealias ", "internal ", "public ",
    "private ", "data ", "abstract ", "open ", "expect ", "actual ",
    "annotation ", "value ", "companion ", "init ", "constructor ", "infix ",
    "operator ", "suspend ", "inline ", "tailrec ", "external ", "crossinline ",
    "get()", "get(", "set(", "provideDelegate", "invoke",
    "noinline ", "reified ", "const ", "lateinit ", "override ", "operator ",
    "}", ")", "]", ".",
)


def strip(src, path, problems):
    """Return (code_only, depth) with comments and literals removed."""
    out = []
    i, n = 0, len(src)
    depth = 0          # block-comment nesting
    line = 1
    block_start = 0
    while i < n:
        c = src[i]
        nxt = src[i + 1] if i + 1 < n else ""

        if c == "\n":
            line += 1

        if depth:
            # inside a block comment
            if c == "/" and nxt == "*":
                depth += 1
                i += 2
                continue
            if c == "*" and nxt == "/":
                depth -= 1
                i += 2
                if depth == 0:
                    out.append(" ")
                continue
            out.append("\n" if c == "\n" else " ")
            i += 1
            continue

        # not in a comment
        if c == "/" and nxt == "/":
            while i < n and src[i] != "\n":
                i += 1
            continue
        if c == "/" and nxt == "*":
            depth = 1
            block_start = line
            i += 2
            continue
        if src.startswith('"""', i):
            i += 3
            closed = False
            while i < n:
                if src[i] == "\n":
                    line += 1
                if src.startswith('"""', i):
                    # a run of more than three quotes ends the raw string
                    run = 0
                    while i + run < n and src[i + run] == '"':
                        run += 1
                    if run >= 3:
                        i += run
                        closed = True
                        break
                i += 1
            if not closed:
                problems.append(f"{path}:{block_start}: unterminated raw string")
            out.append('""')
            continue
        if c == '"':
            i += 1
            closed = False
            while i < n:
                if src[i] == "\\":
                    if i + 1 < n and src[i + 1] == "\n":
                        line += 1
                    i += 2
                    continue
                if src[i] == "\n":
                    break
                # a ${...} template may itself contain quoted strings
                if src.startswith("${", i):
                    tdepth, i = 1, i + 2
                    while i < n and tdepth:
                        if src.startswith("//", i):
                            while i < n and src[i] != "\n":
                                i += 1
                        elif src[i] == '"':
                            i += 1
                            while i < n and src[i] != '"':
                                i += 2 if src[i] == "\\" else 1
                            i += 1  # step past the closing quote, else it is
                                    # mistaken for the start of another string
                        elif src[i] == "'":
                            j = i + 1
                            while j < n and src[j] != "'" and src[j] != "\n":
                                j += 2 if src[j] == "\\" else 1
                            i = j + 1
                        elif src[i] == "{":
                            tdepth, i = tdepth + 1, i + 1
                        elif src[i] == "}":
                            tdepth, i = tdepth - 1, i + 1
                        else:
                            if src[i] == "\n":
                                line += 1
                            i += 1
                    continue
                if src[i] == '"':
                    i += 1
                    closed = True
                    break
                i += 1
            if not closed:
                problems.append(f"{path}:{line}: unterminated string literal")
            out.append('""')
            continue
        if c == "'":
            j = i + 1
            while j < n and src[j] != "'" and src[j] != "\n":
                if src[j] == "\\":
                    j += 1
                j += 1
            if j < n and src[j] == "'":
                out.append("'x'")
                i = j + 1
                continue
            i += 1
            continue

        out.append(c)
        i += 1

    if depth:
        problems.append(f"{path}:{block_start}: unterminated block comment")
    return "".join(out)


def check(path, src, problems):
    before = len(problems)
    code = strip(src, path, problems)
    lexed_cleanly = len(problems) == before
    if not lexed_cleanly:
        # A lexer that gave up makes every later count meaningless. Reporting a
        # brace imbalance here would be noise, and noise is how a gate stops
        # being trusted. Report the lexing failure alone.
        return

    # balance, ignoring string contents (already blanked)
    for open_c, close_c, name in (("{", "}", "braces"), ("(", ")", "parens"),
                                  ("[", "]", "brackets")):
        d = code.count(open_c) - code.count(close_c)
        if d:
            problems.append(f"{path}: {name} unbalanced by {d:+d}")

    # Top-level sanity. Only meaningful between complete declarations: a line
    # that continues the previous one (expression body, chained call, trailing
    # lambda) is legitimate, so continuation is tracked from both ends before a
    # line is judged.
    CONTINUES = ("=", ",", "(", "[", "{", "&&", "||", "?:", "+", "-", "*", "/",
                 ".", "->", "?", ":", "<", ">")
    # infix words: a line ending in one of these is still mid-expression
    INFIX = (" or", " and", " shl", " shr", " xor", " in", " to", " until",
             " step", " by", "?:")
    depth = 0
    prev_open = False          # previous statement is still awaiting more input
    for lineno, raw in enumerate(code.split("\n"), 1):
        stripped = raw.strip()
        if depth == 0 and stripped and not prev_open:
            # Only a leading dot is unconditionally allowed to continue: the
            # rest of the continuation cases are already covered by prev_open,
            # and allowing operators here would hide a stray `*` left behind by
            # a comment that closed early.
            if not stripped.startswith(TOP_LEVEL) and not stripped.startswith((".", "?.", "?:", "->", "else")):
                problems.append(
                    f"{path}:{lineno}: text at top level that is not a "
                    f"declaration -> {stripped[:60]!r}")
        depth += raw.count("{") + raw.count("(") + raw.count("[")
        depth -= raw.count("}") + raw.count(")") + raw.count("]")
        prev_open = (depth > 0 or stripped.endswith(CONTINUES)
                     or stripped.endswith(INFIX))


def main(roots):
    files = 0
    problems = []
    for root in roots:
        if os.path.isfile(root):
            check(root, open(root, encoding="utf-8").read(), problems)
            files += 1
            continue
        for dp, dirs, names in os.walk(root):
            dirs[:] = [d for d in dirs if d not in (".git", "build", ".gradle")]
            for name in names:
                if not name.endswith(".kt"):
                    continue
                p = os.path.join(dp, name)
                check(p, open(p, encoding="utf-8").read(), problems)
                files += 1

    print(f"scanned {files} Kotlin files")
    if problems:
        print(f"\n{len(problems)} problem(s):")
        for p in problems:
            print("  " + p)
        return 1
    print("clean")
    return 0


if __name__ == "__main__":
    args = sys.argv[1:] or ["OxideLauncher/src"]
    sys.exit(main(args))