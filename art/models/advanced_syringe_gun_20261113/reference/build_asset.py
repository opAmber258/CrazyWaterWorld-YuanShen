"""Restore the current user-authored publish input; do not regenerate old geometry."""
from pathlib import Path
import runpy
runpy.run_path(str(Path(__file__).with_name("build_revision.py")),run_name="__main__")
