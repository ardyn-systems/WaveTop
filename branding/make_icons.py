"""Regenerate WaveTop's app icons and doc images from the master logos in this folder.

    python branding/make_icons.py

- wavetop-simple.png   → launcher icons (every density), adaptive-icon foreground, splash icon,
                         and the in-app brand mark (title bar, Updates, About).
- wavetop-detailed.png → README / website artwork (docs/images/).

Both masters are square PNGs on black. Needs Pillow (pip install pillow).
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
BRANDING = ROOT / "branding"
RES = ROOT / "app" / "src" / "main" / "res"
DOCS = ROOT / "docs" / "images"

# Android density buckets: scale factor from mdpi.
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}

# Adaptive icons are a 108dp canvas whose middle 66dp is always visible; launchers crop the rest
# to a circle/squircle. The logo's own rounded frame is shrunk into that zone (with a little
# room for the glow) so no launcher mask clips it.
ADAPTIVE_FRACTION = 0.74


def resized(img: Image.Image, px: int) -> Image.Image:
    return img.resize((px, px), Image.LANCZOS)


def on_black_canvas(img: Image.Image, canvas_px: int, fraction: float) -> Image.Image:
    canvas = Image.new("RGB", (canvas_px, canvas_px), (0, 0, 0))
    inner = round(canvas_px * fraction)
    offset = (canvas_px - inner) // 2
    canvas.paste(resized(img, inner), (offset, offset))
    return canvas


def save(img: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, optimize=True)
    print(f"{path.relative_to(ROOT)}  {img.width}x{img.height}")


def main() -> None:
    simple = Image.open(BRANDING / "wavetop-simple.png").convert("RGB")
    detailed = Image.open(BRANDING / "wavetop-detailed.png").convert("RGB")

    for bucket, scale in DENSITIES.items():
        # Legacy square icon (Android 7.x and launchers without adaptive icons): 48dp.
        save(resized(simple, round(48 * scale)), RES / f"mipmap-{bucket}" / "ic_launcher.png")
        # Adaptive foreground: 108dp canvas, logo inside the safe zone.
        save(on_black_canvas(simple, round(108 * scale), ADAPTIVE_FRACTION), RES / f"mipmap-{bucket}" / "ic_launcher_foreground.png")

    # Splash: Android shows the icon inside a 160dp circle of a 240dp canvas (no icon background).
    save(on_black_canvas(simple, 960, 0.62), RES / "drawable-nodpi" / "splash_icon.png")
    # In-app brand mark, drawn at 32–72dp.
    save(resized(simple, 288), RES / "drawable-nodpi" / "brand_mark.png")

    save(resized(detailed, 640), DOCS / "wavetop-logo.png")
    save(resized(simple, 256), DOCS / "wavetop-icon.png")


if __name__ == "__main__":
    main()
