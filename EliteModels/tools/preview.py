"""Contact sheet of the elite models: python -I tools/preview.py out.png cell model1.bbmodel [model2 ...]
Uses Wildlife's isometric preview with the elite palette (make_elite replaces the palette in place before it runs)."""
import os
import runpy
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import make_elite  # noqa: F401,E402  (swaps the colours)

runpy.run_path(os.path.join(HERE, "..", "..", "Wildlife", "tools", "preview3d.py"), run_name="__main__")
