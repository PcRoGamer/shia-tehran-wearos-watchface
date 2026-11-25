"""Typeset prayer rows with kashida, font size and numeral tracking.

Run with uharfbuzz installed: python watchface/fonts/fit_prayer_rows.py
"""
from itertools import product
from pathlib import Path
import re
from xml.sax.saxutils import escape

from fontTools import subset
from fontTools.ttLib import TTFont

import uharfbuzz as hb


RES = Path(__file__).resolve().parents[1] / "src/main/res"
KASHIDA = {
    "الفجر": ("الف", "جر"),
    "الشروق": ("الش", "روق"),
    "الظهر": ("الظ", "هر"),
    "الغروب": ("الغ", "روب"),
    "المغرب": ("المغ", "رب"),
    "نصف الليل": ("نص", "ف الليل"),
}
# Rank-specific ink width, point sizes, and the existing vertical row geometry.
RANKS = {
    "featured": (124, 54, 44, 78, 294, 8, 76, 255),
    "following": (101, 42, 35, 95, 270, -8, 56, 246),
    "third": (78, 34, 28, 95, 270, 46, 40, 213),
}
DIVIDER = 225
HALF_GUTTER = 10
DIGITS = "0123456789"
# Offsets are in the Outfit font’s 1,000-unit em.
TRACKING = (-35, 0, 35, 70, 105, 140)
ROW = re.compile(
    r'^ {20}<Compare expression="(splitPrayerTime|followingPrayer|thirdPrayer)">'
    r'.*?^ {20}</Compare>',
    re.S | re.M,
)

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


def ink_width(font, text):
    _, left, right = measure(font, text)
    return (right - left) / font.face.upem


def arabic_style(font, word, target, reference):
    before, after = KASHIDA[word]
    variants = [(before + "ـ" * n + after, n) for n in range(4)]
    options = [
        (abs(size - reference) + n * 1.2, embellished, size)
        for embellished, n in variants
        for size in [target / ink_width(font, embellished)]
    ]
    _, embellished, size = min(options)
    return embellished, round(size, 2)


def tracking_family(units):
    if not units:
        return "outfit_bold"
    tag = f"m{-units}" if units < 0 else f"p{units}"
    return f"outfit_prayer_{tag}"


def build_tracking_fonts():
    source = RES / "font/outfit_bold.ttf"
    for units in TRACKING:
        if not units:
            continue
        font = TTFont(source, recalcTimestamp=False)
        cmap = font.getBestCmap()
        for ch in DIGITS + ":":
            glyph = cmap[ord(ch)]
            advance, bearing = font["hmtx"][glyph]
            font["hmtx"][glyph] = advance + units, bearing
        sub = subset.Subsetter()
        sub.populate(text=DIGITS + ":")
        sub.subset(font)
        resource = tracking_family(units)
        for id_ in (1, 2, 3, 4, 6, 16, 17):
            font["name"].removeNames(nameID=id_)
            font["name"].setName(
                "Regular" if id_ in (2, 17) else resource, id_, 3, 1, 0x409
            )
        font.save(RES / "font" / f"{resource}.ttf")


def number_style(target, reference, natural_width, gaps):
    options = []
    for units in TRACKING:
        tracking = units / 1000
        width = natural_width + tracking * gaps
        size = round(min(reference * 1.28, max(reference * 0.8, target / width)), 2)
        cost = abs(size - reference) + 58 * abs(tracking - 0.05)
        cost += 10 * abs(width * size - target)
        options.append((cost, size, units))
    _, size, units = min(options)
    return size, units


def text_part(offset, height, x, width, alpha, align, font, size, color, template):
    attrs = f'x="{x}" y="{offset}" width="{width}" height="{height}"'
    if alpha != 255:
        attrs += f' alpha="{alpha}"'
    return (
        f'<PartText {attrs}>\n'
        f'    <Text align="{align}" maxLines="1">\n'
        f'        <Font family="{font}" size="{size}" color="{color}">\n'
        f'            <Template>{template}</Template>\n'
        '        </Font>\n'
        '    </Text>\n'
        '</PartText>'
    )


def condition(entries, fallback=None):
    lines = ["<Condition>", "    <Expressions>"]
    for name, expression, _ in entries:
        lines.append(f'        <Expression name="{name}"><![CDATA[{expression}]]></Expression>')
    lines.append("    </Expressions>")
    for name, _, drawing in entries:
        lines.extend((
            f'    <Compare expression="{name}">',
            indent(drawing, 8),
            "    </Compare>",
        ))
    if fallback is not None:
        lines.extend(("    <Default>", indent(fallback, 8), "    </Default>"))
    return "\n".join((*lines, "</Condition>"))


def indent(text, spaces):
    return "\n".join(" " * spaces + line for line in text.splitlines())


def divider(rank, slot_x):
    if rank == "third":
        return ""
    primary = rank == "featured"
    name = "primary_prayer_divider" if primary else "prayer_schedule_divider"
    y = 31 if primary else 0
    height = 47 if primary else 79
    variant = '    <Variant mode="AMBIENT" target="alpha" value="0" />\n' if primary else ""
    return (
        f'<PartDraw name="{name}" x="{DIVIDER - slot_x - 1}" y="{y}" width="2" height="{height}">\n'
        f'{variant}'
        f'    <Line startX="1" startY="0" endX="1" endY="{height}">\n'
        '        <Stroke color="#55DCEAFF" thickness="1.2" />\n'
        '    </Line>\n'
        '</PartDraw>'
    )


def render_row(rank, field, label, time_font, name_font, short=False):
    target, time_size, name_size, slot_x, slot_width, y, height, alpha = RANKS[rank]
    row_id = rank + ("Short" if short else "Long")
    width_ref = row_id + "InkWidth"
    template = (
        '%s<Parameter expression="subText(' + field + ', 0, 1) == &quot;0&quot; ? '
        'subText(' + field + ', 1, 5) : subText(' + field + ', 0, 5)" />'
    )
    width_expression = time_width(time_font, field)
    measure_group = (
        f'<Group name="{row_id}Measure" x="0" y="0" width="1" height="1">\n'
        f'    <Transform target="scaleX" value="{escape(width_expression, {chr(34): "&quot;"})} / {time_font.face.upem}" />\n'
        f'    <Reference name="{width_ref}" source="scaleX" defaultValue="2" />\n'
        '</Group>'
    )

    times = []
    for hours in (range(1, 10), range(10, 13)):
        widths = [
            ink_width(time_font, f"{hour}:{minute:02}")
            for hour, minute in product(hours, range(60))
        ]
        low, high = min(widths), max(widths)
        bins = 10
        step = (high - low) / bins
        first_digit = '==' if 1 in hours else '!='
        count_check = f'subText({field}, 0, 1) {first_digit} "0"'
        reference = f"[REFERENCE.{width_ref}]"
        for i in range(bins):
            size, units = number_style(
                target, time_size, low + step * (i + 0.5), 3 if 1 in hours else 4
            )
            tests = [count_check]
            if i > 0:
                tests.append(f"{reference} >= {low + step * i:.5f}")
            if i < bins - 1:
                tests.append(f"{reference} < {low + step * (i + 1):.5f}")
            number_offset = y + (round(max(0, size - 46) * 0.35) if rank == "following" else 0)
            drawing = text_part(
                number_offset, height, 0, DIVIDER - HALF_GUTTER - slot_x + round(size * units / 1000), alpha,
                "END", tracking_family(units), size, "[CONFIGURATION.theme_color.2]",
                template,
            )
            times.append((f"timeFit{len(times)}", " && ".join(tests), drawing))

    names = []
    x = DIVIDER + HALF_GUTTER - slot_x
    color = "[CONFIGURATION.edge_color.0]" if rank == "featured" else "[CONFIGURATION.theme_color.2]"
    for word in KASHIDA:
        display, size = arabic_style(name_font, word, target, name_size)
        drawing = text_part(
            y, height, x, slot_width - x, alpha, "START",
            "ibm_plex_arabic_bold", size, color, f'%s<Parameter expression="&quot;{display}&quot;" />',
        )
        names.append((f"prayerName{len(names)}", f'{label} == "{word}"', drawing))
    label_template = escape(label, {'"': "&quot;"})
    default_name = text_part(
        y, height, x, slot_width - x, alpha, "START", "ibm_plex_arabic_bold",
        name_size, color, f'%s<Parameter expression="{label_template}" />',
    )
    parts = (measure_group, condition(times), divider(rank, slot_x), condition(names, default_name))
    return "\n".join(part for part in parts if part)


def main():
    build_tracking_fonts()
    file = RES / "raw/watchface.xml"
    xml = file.read_text(encoding="utf-8")
    start = xml.index("<!-- Open-type next prayer")
    end = xml.index('            <Complication type="SHORT_TEXT">', xml.index("<!-- Following two prayer milestones"))
    primary_short = xml.index('            <Complication type="SHORT_TEXT">', start)
    time_font = hb.Font(hb.Face((RES / "font/outfit_bold.ttf").read_bytes()))
    name_font = hb.Font(hb.Face((RES / "font/ibm_plex_arabic_bold.ttf").read_bytes()))

    def replace(match):
        role = match[1]
        short = role == "splitPrayerTime" and start + match.start() > primary_short
        if role == "splitPrayerTime":
            rank = "featured"
            field = "[COMPLICATION.TEXT]" if short else "[COMPLICATION.TITLE]"
        else:
            rank = "following" if role == "followingPrayer" else "third"
            field = "[COMPLICATION.TEXT]" if rank == "following" else "[COMPLICATION.TITLE]"
        label = "[COMPLICATION.TITLE]" if short else f"subText({field}, 8, textLength({field}))"
        drawing = render_row(rank, field, label, time_font, name_font, short)
        return f'                    <Compare expression="{role}">\n{indent(drawing, 24)}\n                    </Compare>'

    rows, count = ROW.subn(replace, xml[start:end])
    assert count == 4
    file.write_text(xml[:start] + rows + xml[end:], encoding="utf-8", newline="\r\n")


if __name__ == "__main__":
    main()