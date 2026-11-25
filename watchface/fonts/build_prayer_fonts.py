"""Build the watch face's prayer font instances from pinned variable fonts.

Run with fontTools installed: python watchface/fonts/build_prayer_fonts.py
Then regenerate the optical widths with fit_prayer_rows.py.
"""
from io import BytesIO
from pathlib import Path
from urllib.request import urlopen

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

GOOGLE_FONTS = "https://raw.githubusercontent.com/google/fonts"
RES = Path(__file__).resolve().parents[1] / "src/main/res"
FONTS = (
    (
        "prayer_time",
        "Tehran Prayer Time",
        "8426394c2031cb9b07b7e4ffe6fd3953fcf9db08/ofl/robotoflex",
        "RobotoFlex%5BGRAD%2CXOPQ%2CXTRA%2CYOPQ%2CYTAS%2CYTDE%2CYTFI%2CYTLC%2CYTUC%2Copsz%2Cslnt%2Cwdth%2Cwght%5D.ttf",
        {"wdth": 142, "wght": 600, "opsz": 16},
        "robotoflex_ofl",
        tuple(map(ord, "0123456789:")),
    ),
    (
        "prayer_name",
        "Tehran Prayer Name",
        "e5ee31305efa101c2cae65f44d9642df2c8f0b4d/ofl/notosansarabic",
        "NotoSansArabic%5Bwdth%2Cwght%5D.ttf",
        {"wdth": 87.5, "wght": 660},
        "notosansarabic_ofl",
        (*range(0x20, 0x7F), *range(0x600, 0x700), 0xA0, 0x200C, 0x200D, 0x2026),
    ),
)


def read(url):
    with urlopen(url, timeout=30) as response:
        return response.read()


def main():
    for resource, family, source_dir, filename, settings, license_name, codepoints in FONTS:
        source = f"{GOOGLE_FONTS}/{source_dir}"
        original = TTFont(BytesIO(read(f"{source}/{filename}")), recalcTimestamp=False)
        axes = {axis.axisTag: axis.defaultValue for axis in original["fvar"].axes}
        font = instantiateVariableFont(original, axes | settings)
        options = subset.Options()
        options.layout_features = ["*"]
        options.name_IDs = ["*"]
        sub = subset.Subsetter(options=options)
        sub.populate(unicodes=codepoints)
        sub.subset(font)

        names = font["name"]
        style_names = {
            1: family, 2: "Regular", 3: family, 4: family,
            6: family.replace(" ", ""), 16: family, 17: "Regular",
        }
        for id_, value in style_names.items():
            names.removeNames(nameID=id_)
            names.setName(value, id_, 3, 1, 0x409)
        font.save(RES / "font" / f"{resource}.ttf")
        (RES / "raw" / f"{license_name}.txt").write_bytes(read(f"{source}/OFL.txt"))


if __name__ == "__main__":
    main()
