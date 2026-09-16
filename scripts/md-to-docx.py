"""Markdown to .docx, for documents that go to people who do not read GitHub.

Handles what our docs actually use: ATX headings, pipe tables, fenced code, bullet and
numbered lists, task lists, blockquotes, rules, and inline bold/italic/code. Deliberately
not a general Markdown implementation -- it covers this repository's docs and says so.

The one thing worth knowing: Markdown hard-wraps paragraphs at ~100 columns, so consecutive
non-blank lines must be JOINED into one Word paragraph. Emitting one paragraph per source
line is the obvious mistake and it makes the output unreadable.
"""
import re
import sys

from docx import Document
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Pt, RGBColor, Inches

BODY = "Calibri"
MONO = "Consolas"
INK = RGBColor(0x1A, 0x1A, 0x1A)
HEAD = RGBColor(0x1F, 0x30, 0x46)      # near-navy, still legible printed in greyscale
CODE_BG = "F4F4F6"
HEAD_BG = "1F3046"
CODE_INK = RGBColor(0xA3, 0x1D, 0x4B)

# Order matters: code spans are matched before emphasis so ** inside backticks is left alone.
INLINE = re.compile(r"(\*\*.+?\*\*|`[^`]+`|(?<!\*)\*[^*\n]+\*(?!\*))")


def shade_par(par, fill):
    """Paragraph background. Word exposes no API for this, only the underlying XML."""
    sh = OxmlElement("w:shd")
    sh.set(qn("w:val"), "clear")
    sh.set(qn("w:fill"), fill)
    par._p.get_or_add_pPr().append(sh)


def shade_cell(cell, fill):
    sh = OxmlElement("w:shd")
    sh.set(qn("w:val"), "clear")
    sh.set(qn("w:fill"), fill)
    cell._tc.get_or_add_tcPr().append(sh)


def add_inline(par, text):
    """Emit text into a paragraph, honouring bold, italic and code spans."""
    for tok in INLINE.split(text):
        if not tok:
            continue
        if tok.startswith("**") and tok.endswith("**") and len(tok) > 4:
            run = par.add_run(tok[2:-2])
            run.bold = True
        elif tok.startswith("`") and tok.endswith("`") and len(tok) > 2:
            run = par.add_run(tok[1:-1])
            run.font.name = MONO
            run.font.size = Pt(9.5)
            run.font.color.rgb = CODE_INK
        elif tok.startswith("*") and tok.endswith("*") and len(tok) > 2:
            run = par.add_run(tok[1:-1])
            run.italic = True
        else:
            par.add_run(tok)


def hrule(doc):
    par = doc.add_paragraph()
    par.paragraph_format.space_before = Pt(6)
    par.paragraph_format.space_after = Pt(10)
    borders = OxmlElement("w:pBdr")
    bottom = OxmlElement("w:bottom")
    for key, val in (("val", "single"), ("sz", "6"), ("space", "1"), ("color", "C8C8CE")):
        bottom.set(qn("w:" + key), val)
    borders.append(bottom)
    par._p.get_or_add_pPr().append(borders)


def code_block(doc, block):
    """One shaded, single-spaced run of lines, kept together across a page break."""
    for idx, line in enumerate(block):
        par = doc.add_paragraph()
        fmt = par.paragraph_format
        fmt.left_indent = Inches(0.22)
        fmt.space_before = Pt(8 if idx == 0 else 0)
        fmt.space_after = Pt(8 if idx == len(block) - 1 else 0)
        fmt.line_spacing = 1.0
        fmt.keep_together = True
        if idx < len(block) - 1:
            fmt.keep_with_next = True
        run = par.add_run(line if line.strip() else " ")
        run.font.name = MONO
        run.font.size = Pt(9)
        run.font.color.rgb = RGBColor(0x24, 0x24, 0x30)
        shade_par(par, CODE_BG)


def split_row(line):
    line = line.strip()
    if line.startswith("|"):
        line = line[1:]
    if line.endswith("|"):
        line = line[:-1]
    return [c.strip() for c in line.split("|")]


def is_divider(line):
    stripped = line.strip()
    return "-" in stripped and re.fullmatch(r"\|?[\s:|-]+\|?", stripped) is not None


def add_table(doc, rows):
    """First row is the header. Empty header cells are common in our docs and are fine."""
    width = max(len(r) for r in rows)
    rows = [r + [""] * (width - len(r)) for r in rows]
    table = doc.add_table(rows=len(rows), cols=width)
    table.style = "Table Grid"
    table.alignment = WD_TABLE_ALIGNMENT.LEFT
    for ri, row in enumerate(rows):
        for ci, text in enumerate(row):
            cell = table.cell(ri, ci)
            cell.text = ""
            par = cell.paragraphs[0]
            par.paragraph_format.space_before = Pt(3)
            par.paragraph_format.space_after = Pt(3)
            add_inline(par, text)
            for run in par.runs:
                run.font.size = Pt(9.5)
                if ri == 0:
                    run.bold = True
                    run.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
            if ri == 0:
                shade_cell(cell, HEAD_BG)
    doc.add_paragraph().paragraph_format.space_after = Pt(4)


def convert(src, dst):
    text = open(src, encoding="utf-8").read().replace("\r\n", "\n")
    lines = text.split("\n")

    doc = Document()
    normal = doc.styles["Normal"]
    normal.font.name = BODY
    normal.font.size = Pt(10.5)
    normal.font.color.rgb = INK
    normal.paragraph_format.space_after = Pt(7)
    normal.paragraph_format.line_spacing = 1.12

    for name, size, before in (("Title", 24, 0), ("Heading 1", 17, 20),
                               ("Heading 2", 13.5, 15), ("Heading 3", 11.5, 12)):
        style = doc.styles[name]
        style.font.name = BODY
        style.font.size = Pt(size)
        style.font.bold = True
        style.font.color.rgb = HEAD
        style.paragraph_format.space_before = Pt(before)
        style.paragraph_format.space_after = Pt(6)
        style.paragraph_format.keep_with_next = True

    section = doc.sections[0]
    section.top_margin = Inches(0.85)
    section.bottom_margin = Inches(0.85)
    section.left_margin = Inches(0.95)
    section.right_margin = Inches(0.95)

    buf = []
    idx = 0
    first_h1 = True

    def flush():
        if not buf:
            return
        joined = " ".join(x.strip() for x in buf).strip()
        del buf[:]
        if joined:
            add_inline(doc.add_paragraph(), joined)

    while idx < len(lines):
        line = lines[idx]
        stripped = line.strip()

        if stripped.startswith("```"):
            flush()
            idx += 1
            block = []
            while idx < len(lines) and not lines[idx].strip().startswith("```"):
                block.append(lines[idx])
                idx += 1
            idx += 1
            code_block(doc, block)
            continue

        if stripped.startswith("|") and idx + 1 < len(lines) and is_divider(lines[idx + 1]):
            flush()
            rows = [split_row(lines[idx])]
            idx += 2
            while idx < len(lines) and lines[idx].strip().startswith("|"):
                rows.append(split_row(lines[idx]))
                idx += 1
            add_table(doc, rows)
            continue

        if not stripped:
            flush()
            idx += 1
            continue

        if re.fullmatch(r"-{3,}|\*{3,}|_{3,}", stripped):
            flush()
            hrule(doc)
            idx += 1
            continue

        head = re.match(r"(#{1,6})\s+(.*)", stripped)
        if head:
            flush()
            level, title = len(head.group(1)), head.group(2).strip()
            if level == 1 and first_h1:
                add_inline(doc.add_paragraph(style="Title"), title)
                first_h1 = False
            else:
                add_inline(doc.add_paragraph(style="Heading %d" % min(level, 3)), title)
            idx += 1
            continue

        if stripped.startswith(">"):
            flush()
            quote = []
            while idx < len(lines) and lines[idx].strip().startswith(">"):
                quote.append(lines[idx].strip().lstrip(">").strip())
                idx += 1
            par = doc.add_paragraph()
            par.paragraph_format.left_indent = Inches(0.3)
            par.paragraph_format.space_before = Pt(6)
            par.paragraph_format.space_after = Pt(8)
            add_inline(par, " ".join(quote))
            for run in par.runs:
                run.italic = True
                run.font.color.rgb = RGBColor(0x44, 0x44, 0x52)
            continue

        task = re.match(r"[-*]\s+\[([ xX])\]\s+(.*)", stripped)
        if task:
            flush()
            done = task.group(1).lower() == "x"
            par = doc.add_paragraph()
            par.paragraph_format.left_indent = Inches(0.28)
            par.paragraph_format.space_after = Pt(3)
            box = par.add_run("☒  " if done else "☐  ")
            box.font.name = MONO
            add_inline(par, task.group(2))
            idx += 1
            continue

        item = re.match(r"([-*]|\d+\.)\s+(.*)", stripped)
        if item:
            flush()
            indent = len(line) - len(line.lstrip())
            marker, body = item.group(1), item.group(2)
            idx += 1
            # Fold indented continuation lines into this same item.
            while idx < len(lines):
                nxt = lines[idx]
                if not nxt.strip():
                    break
                nxt_indent = len(nxt) - len(nxt.lstrip())
                is_new_item = re.match(r"([-*]|\d+\.)\s+", nxt.strip()) is not None
                if nxt_indent > indent and not is_new_item \
                        and not nxt.strip().startswith(("|", "```", "#")):
                    body += " " + nxt.strip()
                    idx += 1
                else:
                    break
            style = "List Number" if marker.endswith(".") else "List Bullet"
            par = doc.add_paragraph(style=style)
            par.paragraph_format.left_indent = Inches(0.28 + 0.25 * (indent // 2))
            par.paragraph_format.space_after = Pt(3)
            add_inline(par, body)
            continue

        buf.append(line)
        idx += 1

    flush()
    doc.save(dst)


if __name__ == "__main__":
    convert(sys.argv[1], sys.argv[2])
    print("wrote " + sys.argv[2])
