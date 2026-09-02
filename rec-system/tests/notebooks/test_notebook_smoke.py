"""Smoke test: executes rec_playground.ipynb via nbconvert.

Marked as @pytest.mark.slow so it is excluded from the normal test suite
(run with: uv run pytest -m slow tests/notebooks/test_notebook_smoke.py).
"""
from __future__ import annotations

import sys
from pathlib import Path

import pytest

_ROOT = Path(__file__).parent.parent.parent
if str(_ROOT) not in sys.path:
    sys.path.insert(0, str(_ROOT))


@pytest.mark.slow
def test_nbconvert_executes():
    """Runs the notebook via nbconvert ExecutePreprocessor. Must not error."""
    import nbformat
    from nbconvert.preprocessors import ExecutePreprocessor

    notebook_path = _ROOT / "notebooks" / "rec_playground.ipynb"
    assert notebook_path.exists(), f"Notebook not found at {notebook_path}"

    with open(notebook_path) as f:
        nb = nbformat.read(f, as_version=4)

    ep = ExecutePreprocessor(timeout=600, kernel_name="python3")
    ep.preprocess(nb)  # should not raise
