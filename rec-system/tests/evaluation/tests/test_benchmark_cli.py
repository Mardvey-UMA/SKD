"""CLI smoke tests for scripts/benchmark.py.

@pytest.mark.slow: these tests invoke subprocesses and may be slow.
"""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

import pytest

_PROJECT_ROOT = Path(__file__).parent.parent.parent.parent
_BENCHMARK_SCRIPT = _PROJECT_ROOT / "scripts" / "benchmark.py"


@pytest.mark.slow
def test_cli_entry_point():
    """benchmark.py --help must exit 0 and include persona-related usage text."""
    result = subprocess.run(
        [sys.executable, str(_BENCHMARK_SCRIPT), "--help"],
        capture_output=True,
        text=True,
        timeout=30,
    )
    assert result.returncode == 0, (
        f"--help returned non-zero exit code {result.returncode}.\n"
        f"stdout: {result.stdout[:500]}\nstderr: {result.stderr[:500]}"
    )
    combined = result.stdout + result.stderr
    assert "--personas" in combined or "personas" in combined.lower(), (
        f"Expected '--personas' in help output. Got:\n{combined[:1000]}"
    )


@pytest.mark.slow
def test_cli_missing_required_args():
    """benchmark.py without required args must exit non-zero."""
    result = subprocess.run(
        [sys.executable, str(_BENCHMARK_SCRIPT)],
        capture_output=True,
        text=True,
        timeout=30,
    )
    assert result.returncode != 0, (
        "Expected non-zero exit code when required args are missing"
    )


@pytest.mark.slow
def test_cli_unknown_arg():
    """benchmark.py with an unknown flag must exit non-zero."""
    result = subprocess.run(
        [sys.executable, str(_BENCHMARK_SCRIPT), "--unknown-flag-xyz"],
        capture_output=True,
        text=True,
        timeout=30,
    )
    assert result.returncode != 0
