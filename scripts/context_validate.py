#!/usr/bin/env python3
"""Check that Afterglow's concise project context and audit notes are present."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = {
    "AGENTS.md": ["Product and source of truth", "Validation and handoff"],
    "README.md": ["Prototype 1 scope", "Build and run"],
    "docs/REPOSITORY_AUDIT.md": ["Feature inventory", "Checks and observed results"],
    "docs/ARCHITECTURE_DECISIONS.md": ["Current prototype architecture", "Open decisions"],
    "docs/IMPLEMENTATION_ROADMAP.md": ["Prototype 1", "Release preparation"],
    "context/PROJECT_OVERVIEW.md": ["native Android music player"],
    "context/REQUIREMENTS.md": ["Current status"],
    "context/ARCHITECTURE.md": ["Current source"],
    "context/DECISIONS.md": ["Prototype 1"],
    "context/SECURITY_AND_PRIVACY.md": ["INTERNET"],
    "context/WORK_STATE.md": ["Milestone", "Next action"],
    "context/SESSION_HANDOFF.md": ["Continue Afterglow"],
    "context/SESSION_LOG.md": ["2026-10-18"],
}

def main() -> int:
    problems = []
    for rel, phrases in REQUIRED.items():
        path = ROOT / rel
        if not path.is_file():
            problems.append(f"MISSING: {rel}")
            continue
        content = path.read_text(encoding="utf-8")
        for phrase in phrases:
            if phrase not in content:
                problems.append(f"MISSING TEXT: {rel} -> {phrase!r}")
    if problems:
        print("PROJECT CONTEXT VALIDATION FAIL")
        print("\n".join(f"- {problem}" for problem in problems))
        return 1
    print(f"PROJECT CONTEXT VALIDATION PASS: {len(REQUIRED)} project files and required headings found.")
    print("This structural check does not validate app behavior, Android compilation, or device operation.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
