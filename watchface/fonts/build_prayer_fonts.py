"""Generate the three prayer type styles from Readex Pro's weight and expansion axes.

Run with fontTools installed: python watchface/fonts/build_prayer_fonts.py
"""
from io import BytesIO
from pathlib import Path
from urllib.request import urlopen

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont


SOURCE = (
    "https://raw.githubusercontent.com/google/fonts/"
    "d81dbc630ed0e3cfd8d1af36893674157c2e6b88/"
    "ofl/readexpro/ReadexPro%5BHEXP%2Cwght%5D.ttf"
)
FONT_DIR = Path(__file__).resolve().parents[1] / "src/main/res/font"
STYLES = (
    ("featured", 38, 680),
    ("following", 17, 655),
    ("third", 0, 625),
)


def main():
    with urlopen(SOURCE, timeout=30) as response:
        data = response.read()
    for rank, expansion, weight in STYLES:
        font = instantiateVariableFont(
            TTFont(BytesIO(data), recalcTimestamp=False),
            {"HEXP": expansion, "wght": weight},
        )
        options = subset.Options()
        options.layout_features = ["*"]
        options.name_IDs = ["*"]
        sub = subset.Subsetter(options=options)
        sub.populate(unicodes=(
            *range(0x20, 0x7F),
            *range(0x600, 0x700),
            0xA0, 0x200C, 0x200D, 0x200E, 0x200F, 0x2026,
            0x2066, 0x2067, 0x2069,
        ))
        sub.subset(font)
        family = f"Tehran Prayer {rank.title()}"
        names = font["name"]
        style_names = {
            1: family, 2: "Regular", 3: family, 4: family,
            6: family.replace(" ", ""), 16: family, 17: "Regular",
        }
        for id_, value in style_names.items():
            names.removeNames(nameID=id_)
            names.setName(value, id_, 3, 1, 0x409)
        font.save(FONT_DIR / f"prayer_{rank}.ttf")


if __name__ == "__main__":
    main()
