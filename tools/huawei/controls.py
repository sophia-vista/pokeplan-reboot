#!/usr/bin/env python3
"""Which controls on the Huawei Health screen in front can be tapped or scrolled (INTEGRATIONS_AND_PRIVACY.md 5.2.2).

Run from a computer with `adb` and the phone on USB debugging, with Huawei Health open on the page to check:

    python3 -I tools/huawei/controls.py            # dump the screen now and print the report
    python3 -I tools/huawei/controls.py --out cap.txt
    python3 -I tools/huawei/controls.py --file dump.xml   # a saved `uiautomator dump`, no phone needed

It uses Android's `uiautomator dump`, not PokéLife's accessibility service, so nothing needs switching on and banking apps keep working. It only reads: it
never taps, types or scrolls. Like the app's own capture, every digit of Huawei's text is replaced by `#` (there is no option to keep them), and only
`com.huawei.health` elements are listed. Share the output the same way as "Save, numbers hidden".

The report has: the tree (indent = depth; tags tap / long / scroll / check / off), the controls that can be manipulated with the text each one is reached by
(a label is tapped through its nearest clickable ancestor within 8 levels, the way the walk does), the page's main scroller (the biggest scrollable element,
which the walk scrolls), and a check of every card the walk opens.
"""
import argparse
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

PKG = "com.huawei.health"
# The cards the walk opens (HuaweiWalk.CARDS): title element id function_set_items_title.
WALK_CARDS = ["Exercise records", "Heart", "Weight", "Sleep"]
OTHER_CARDS = ["Health Clovers", "Skin temperature", "Activity rings", "Emotional wellbeing", "SpO2", "Cycle Calendar"]
ANCESTOR_LEVELS = 8


def hide(s):
    return re.sub(r"\d", "#", s or "")


def bounds(b):
    m = re.match(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", b or "")
    return tuple(int(x) for x in m.groups()) if m else (0, 0, 0, 0)


class Node:
    def __init__(self, el, parent, depth):
        a = el.attrib
        self.text, self.desc = a.get("text", "").strip(), a.get("content-desc", "").strip()
        self.id = a.get("resource-id", "").split(":id/")[-1] if ":id/" in a.get("resource-id", "") else a.get("resource-id", "")
        self.cls = a.get("class", "").split(".")[-1]
        self.pkg, self.parent, self.depth = a.get("package", ""), parent, depth
        flag = lambda k: a.get(k) == "true"
        self.tap, self.long, self.scroll, self.check = flag("clickable"), flag("long-clickable"), flag("scrollable"), flag("checkable")
        self.enabled, self.password = a.get("enabled", "true") == "true", flag("password")
        self.b = bounds(a.get("bounds"))
        self.area = max(0, self.b[2] - self.b[0]) * max(0, self.b[3] - self.b[1])
        self.children = []

    @property
    def words(self):
        return hide(self.text) + (f" | {hide(self.desc)}" if self.desc and self.desc != self.text else "") if (self.text or self.desc) else ""

    def tags(self):
        t = [x for x, on in (("tap", self.tap), ("long", self.long), ("scroll", self.scroll), ("check", self.check), ("off", not self.enabled)) if on]
        return ", ".join(([self.id] if self.id else []) + ([self.cls] if self.cls else []) + t)

    def clickable_ancestor(self):
        n = self
        for _ in range(ANCESTOR_LEVELS):
            if n is None:
                return None
            if n.tap:
                return n
            n = n.parent
        return None


def parse(xml_text):
    root = ET.fromstring(xml_text)
    nodes = []

    def walk(el, parent, depth):
        for child in el:
            if child.tag != "node":
                continue
            n = Node(child, parent, depth)
            if parent is not None:
                parent.children.append(n)
            nodes.append(n)
            walk(child, n, depth + 1)

    walk(root, None, 0)
    return [n for n in nodes if n.pkg == PKG and not n.password]


def dump_from_phone():
    def adb(*args):
        return subprocess.run(["adb", *args], capture_output=True, text=True, timeout=60)

    try:
        r = adb("shell", "uiautomator", "dump", "/sdcard/pokelife-controls.xml")
    except FileNotFoundError:
        sys.exit("adb was not found. Install Android platform-tools and turn on USB debugging.")
    if r.returncode != 0 or "dumped" not in r.stdout.lower():
        sys.exit("uiautomator dump failed: " + (r.stdout + r.stderr).strip()[:300])
    p = adb("shell", "cat", "/sdcard/pokelife-controls.xml")
    adb("shell", "rm", "/sdcard/pokelife-controls.xml")
    return p.stdout


def report(nodes):
    out = []
    if not nodes:
        return "No com.huawei.health elements on this screen. Open Huawei Health on the page to check, then run again.\n"
    out.append(f"PokéLife Huawei Health controls ({len(nodes)} elements, digits hidden)\n\n== Tree ==")
    for n in nodes:
        if n.words or n.tap or n.scroll or n.long or n.check:
            out.append("  " * min(n.depth, 20) + (n.words or "-") + f"  [{n.tags()}]")

    out.append("\n== Can be manipulated ==")
    taps = [n for n in nodes if n.tap and n.enabled]
    out.append(f"Tappable ({len(taps)}); each line is the words inside it, then the element that takes the tap:")
    for n in taps:
        inside = [d for d in descendants(n) if d.words][:4]
        label = " / ".join(d.words for d in inside) or "(no words)"
        out.append(f"  tap  {label}  -> {n.id or n.cls}")
    labels = [n for n in nodes if n.words and not n.tap]
    via = [(n, n.clickable_ancestor()) for n in labels]
    reachable = [(n, a) for n, a in via if a is not None and a.enabled]
    out.append(f"Labels reached through a clickable ancestor ({len(reachable)}), which is how the walk taps a card by its title:")
    for n, a in reachable:
        out.append(f"  {n.words}  ({n.id or n.cls})  -> {a.id or a.cls}")
    scrollers = sorted((n for n in nodes if n.scroll), key=lambda n: -n.area)
    out.append(f"Scrollable ({len(scrollers)}), biggest first:")
    for i, n in enumerate(scrollers):
        out.append(f"  scroll{'  <- main scroller (the walk scrolls this)' if i == 0 else ''}  {n.id or n.cls}  {n.b[2]-n.b[0]}x{n.b[3]-n.b[1]}")
    for n in (n for n in nodes if n.check):
        out.append(f"  check  {n.words or '-'}  ({n.id or n.cls})")

    out.append("\n== Walk check (cards the walk opens) ==")
    titles = [n for n in nodes if n.id == "function_set_items_title"]
    is_health = bool(titles) and any(n.id == "id_recyclerview" for n in nodes)
    out.append("This is the Health page." if is_health else "This is not the Health page (the walk expects id_recyclerview and function_set_items_title).")
    for want in WALK_CARDS + OTHER_CARDS:
        hit = next((n for n in titles if n.text.strip() in (want, "SpO₂" if want == "SpO2" else want)), None)
        walked = want in WALK_CARDS
        if hit is None:
            out.append(f"  {'WALK ' if walked else 'other'}  {want}: not on screen (scroll the page and run again)")
        else:
            a = hit.clickable_ancestor()
            out.append(f"  {'WALK ' if walked else 'other'}  {want}: " + (f"tappable via {a.id or a.cls}" if a and a.enabled else "NOT tappable within 8 levels"))
    week = next((n for n in nodes if n.text.strip() == "Week" and n.cls == "TextView"), None)
    if week:
        a = week.clickable_ancestor()
        out.append("  Week tab: " + ("tappable via " + (a.id or a.cls) if a else "not tappable"))
    return "\n".join(out) + "\n"


def descendants(n):
    for c in n.children:
        yield c
        yield from descendants(c)


def main():
    ap = argparse.ArgumentParser(description="List which controls on the Huawei Health screen in front can be tapped or scrolled.")
    ap.add_argument("--file", help="a saved `uiautomator dump` XML instead of asking the phone")
    ap.add_argument("--out", help="also write the report to this file")
    args = ap.parse_args()
    xml_text = open(args.file, encoding="utf-8").read() if args.file else dump_from_phone()
    text = report(parse(xml_text))
    print(text, end="")
    if args.out:
        open(args.out, "w", encoding="utf-8").write(text)


if __name__ == "__main__":
    main()
