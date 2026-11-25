"""Fit each prayer label and time to a common width, then apply its row's rank.

Run with uharfbuzz installed: python watchface/fonts/fit_prayer_rows.py
"""
from pathlib import Path
import re
from xml.sax.saxutils import escape

import uharfbuzz as hb


RES = Path(__file__).resolve().parents[1] / "src/main/res"
LABELS = ("الفجر", "الشروق", "الظهر", "الغروب", "المغرب", "نصف الليل")
RANKS = {"featured": 1.24, "following": 1.02, "third": 0.80}
BASE_WIDTH = 100
DIVIDER = 225
HALF_GUTTER = 11
TEXT_BOX = 260
DIGITS = "0123456789"
ROW = re.compile(
    r'<Compare expression="(splitPrayerTime|followingPrayer|thirdPrayer)">'
    r'.*?</Compare>',
    re.S,
)
PART_TEXT = re.compile(r"<PartText [^\n]+>.*?</PartText>", re.S)


def measure(font, text):
    """Return the shaped advance and ink bounds in font units."""
    buf = hb.Buffer()
    buf.add_str(text)
    buf.guess_segment_properties()
    hb.shape(font, buf)
    x = 0
    left = float("inf")
    right = -float("inf")
    for glyph, position in zip(buf.glyph_infos, buf.glyph_positions):
        bounds = font.get_glyph_extents(glyph.codepoint)
        if bounds and bounds.width:
            glyph_left = x + position.x_offset + bounds.x_bearing
            left = min(left, glyph_left)
            right = max(right, glyph_left + bounds.width)
        x += position.x_advance
    return x, left, right


def choose(expression, values, fallback):
    return "(" + " : ".join(
        f'{expression} == "{key}" ? {value}' for key, value in values.items()
    ) + f" : {fallback})"


def time_width(font, field):
    digits = {d: measure(font, d) for d in DIGITS}
    advance = {d: a for d, (a, _, _) in digits.items()}
    leading = {d: a - left for d, (a, left, _) in digits.items()}
    trailing = {d: right for d, (_, _, right) in digits.items()}
    colon = measure(font, ":")[0]

    # Both providers send hh:mm. The face suppresses the leading zero.
    def digit(index, widths):
        return choose(
            f"subText({field}, {index}, {index + 1})",
            {d: widths[d] for d in DIGITS[:-1]},
            widths["9"],
        )

    # Check every time against the real shaping engine, including kern pairs.
    worst_error = 0
    for hour in range(1, 13):
        hours = str(hour)
        head = leading[hours[0]] + (advance[hours[1]] if hour >= 10 else 0)
        for minute in range(60):
            minutes = f"{minute:02d}"
            predicted = head + colon + advance[minutes[0]] + trailing[minutes[1]]
            _, left, right = measure(font, f"{hours}:{minutes}")
            worst_error = max(worst_error, abs(predicted - (right - left)) / predicted)
    assert worst_error < 0.025, f"Unexpected clock shaping difference: {worst_error:.2%}"
    h1 = digit(0, leading)
    h2 = digit(1, advance)
    hour = f'(subText({field}, 0, 1) == "0" ? {digit(1, leading)} : {h1} + {h2})'
    return f"({hour} + {colon} + {digit(3, advance)} + {digit(4, trailing)})"


def fit_part(part, x, pivot, expression):
    opening, content = part.split(">", 1)
    opening = re.sub(r'\bx="[^"]+"', f'x="{x}"', opening)
    opening = re.sub(r'\bwidth="[^"]+"', f'width="{TEXT_BOX}"', opening)
    opening = re.sub(r' pivotX="[^"]+"', "", opening)
    content = re.sub(r'\n                            <Transform target="scaleX"[^\n]+', "", content)
    content = content.replace(' isAutoSize="TRUE"', "")
    scale = escape(expression, {'"': "&quot;"})
    return f'{opening} pivotX="{pivot}">\n                            <Transform target="scaleX" value="{scale}" />{content}'


def fit_row(row, rank, slot_x, field, label):
    font = hb.Font(hb.Face((RES / "font" / f"prayer_{rank}.ttf").read_bytes()))
    time, name = PART_TEXT.findall(row)
    time_size = int(re.search(r'<Font[^>]+size="(\d+)"', time)[1])
    name_size = int(re.search(r'<Font[^>]+size="(\d+)"', name)[1])
    # 100 units is the width shared by every name and number at the normalization
    # stage. Only the final multiplier is different across the three row ranks.
    def scale(width, size):
        return f"({BASE_WIDTH} * {font.face.upem} / ({size} * {width})) * {RANKS[rank]}"

    widths = {}
    for word in LABELS:
        advance, left, right = measure(font, word)
        assert right > left
        assert advance * name_size / font.face.upem < TEXT_BOX
        widths[word] = right - left

    name_scale = choose(label, {word: scale(str(width), name_size) for word, width in widths.items()}, 1)
    row = row.replace(time, fit_part(time, DIVIDER - HALF_GUTTER - slot_x - TEXT_BOX, 1, scale(time_width(font, field), time_size)))
    row = row.replace(name, fit_part(name, DIVIDER + HALF_GUTTER - slot_x, 0, name_scale))
    row = re.sub(r'(name="(?:primary_prayer|prayer_schedule)_divider" x=")\d+"', rf'\g<1>{DIVIDER - slot_x - 1}"', row)
    return row


def main():
    file = RES / "raw/watchface.xml"
    xml = file.read_text(encoding="utf-8")
    start = xml.index("<!-- Open-type next prayer")
    end = xml.index('            <Complication type="SHORT_TEXT">', xml.index("<!-- Following two prayer milestones"))
    def replace(match):
        row = match[0]
        if match[1] == "splitPrayerTime":
            is_short = '<Parameter expression="[COMPLICATION.TITLE]" />' in row
            if is_short:
                row = row.replace(
                    'subText([COMPLICATION.TEXT], 0, max(0, textLength([COMPLICATION.TEXT]) - 2))',
                    'subText([COMPLICATION.TEXT], 0, 1) == &quot;0&quot; ? subText([COMPLICATION.TEXT], 1, 5) : subText([COMPLICATION.TEXT], 0, 5)',
                )
            field = "[COMPLICATION.TEXT]" if is_short else "[COMPLICATION.TITLE]"
            label = "[COMPLICATION.TITLE]" if is_short else f"subText({field}, 8, textLength({field}))"
            return fit_row(row, "featured", 78, field, label)
        rank = "following" if match[1] == "followingPrayer" else "third"
        field = "[COMPLICATION.TEXT]" if rank == "following" else "[COMPLICATION.TITLE]"
        return fit_row(row, rank, 95, field, f"subText({field}, 8, textLength({field}))")

    rows, count = ROW.subn(replace, xml[start:end])
    assert count == 4
    file.write_text(xml[:start] + rows + xml[end:], encoding="utf-8", newline="\r\n")


if __name__ == "__main__":
    main()
