"""Plot observed FULL-chunk ground surfaces; these are data plots, not native client screenshots.

Usage: python tools/render_tutorial_observations.py [run/tutorial-verification-server]
Optional dev dependencies: matplotlib, numpy. Output stays under ignored build/.
"""
import json
import sys
from pathlib import Path
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

root = Path(sys.argv[1] if len(sys.argv) > 1 else "run/tutorial-verification-server")
output = Path("build/tutorial-verification/observations")
output.mkdir(parents=True, exist_ok=True)
for path in sorted(root.glob("seed-*/tutorial-report.json")):
    report = json.loads(path.read_text(encoding="utf-8"))
    if report.get("status") != "PASS":
        continue
    for observed in report["observations"]:
        candidate = observed["candidate"]
        x, z = candidate["x"], candidate["z"]
        xx, zz = np.meshgrid(np.arange(x-16,x+17), np.arange(z-16,z+17))
        heights = [np.asarray(observed[p]["groundHeights"]) for p in ("FRACTURE", "SMOOTH")]
        low, high = min(h.min() for h in heights)-2, max(h.max() for h in heights)+2
        fig = plt.figure(figsize=(13, 8), layout="constrained")
        for i, phase in enumerate(("FRACTURE", "SMOOTH")):
            ax = fig.add_subplot(2,2,i+1,projection="3d")
            ax.plot_surface(xx,zz,heights[i],cmap="terrain",vmin=low,vmax=high,
                            rstride=1,cstride=1,linewidth=.08,edgecolor="#555555",antialiased=True)
            ax.set_zlim(low,high)
            ax.view_init(elev=30,azim=-60)
            ax.set_xlabel("x (blocks)"); ax.set_ylabel("z (blocks)"); ax.set_zlabel("actual ground y")
            ax.set_title(f"{phase} | center landing safe={observed[phase]['centerLandingSafe']}")
        ax = fig.add_subplot(2,2,3)
        for phase,h,color in zip(("Fracture","Smooth"),heights,("#b24b3e","#28763d")):
            ax.plot(np.arange(-16,17),h[16,:],label=phase,color=color)
        ax.set_title("Actual east/west ground section through center")
        ax.set_xlabel("x offset (blocks)"); ax.set_ylabel("ground y"); ax.legend(); ax.grid(alpha=.2)
        ax = fig.add_subplot(2,2,4)
        for phase,h,color in zip(("Fracture","Smooth"),heights,("#b24b3e","#28763d")):
            ax.plot(np.arange(-16,17),h[:,16],label=phase,color=color)
        ax.set_title("Actual north/south ground section through center")
        ax.set_xlabel("z offset (blocks)"); ax.set_ylabel("ground y"); ax.legend(); ax.grid(alpha=.2)
        actual=observed["actualScore"]
        fig.suptitle(f"Seed {report['seed']} | {observed['label']} ({x}, {z}) | useful actual={observed['usefulActual']}\n"
                     f"Actual max slope F={actual['fractureSlope']:.3f}, S={actual['smoothSlope']:.3f}; "
                     f"contrast={actual['heightContrast']:.0f}; hazard={actual['hazardPenalty']:.0f}\n"
                     "FULL worldgen observations, logs/leaves skipped. No edits, smoothing or path/shift guarantee.", fontsize=12)
        target=output/f"seed-{report['seed']}-{observed['label']}.png"
        fig.savefig(target,dpi=130); plt.close(fig)
        print(target.resolve())
