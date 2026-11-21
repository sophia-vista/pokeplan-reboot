from pathlib import Path
from PIL import Image

RES = Path("androidApp/src/main/res")


def monochrome(src: Image.Image) -> Image.Image:
    src = src.convert("RGBA")
    out = Image.new("RGBA", src.size, (0, 0, 0, 0))
    pixels, target = src.load(), out.load()
    for y in range(src.height):
        for x in range(src.width):
            r, g, b, a = pixels[x, y]
            if a == 0:
                continue
            luma = (0.299 * r + 0.587 * g + 0.114 * b) / 255
            weight = min(1.0, max(0.0, (0.93 - luma) / 0.45))
            target[x, y] = (0, 0, 0, round(a * weight))
    return out


for folder in sorted(RES.glob("mipmap-*dpi")):
    foreground = folder / "ic_launcher_foreground.png"
    if foreground.exists():
        monochrome(Image.open(foreground)).save(folder / "ic_launcher_monochrome.png")
        print("wrote", folder / "ic_launcher_monochrome.png")
