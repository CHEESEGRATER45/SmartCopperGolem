#!/usr/bin/env python3
"""
Turn the item sheet into SmartCopperGolem's categories.yml.

1. In Google Sheets open the "Items 26.2" tab, then File > Download > Comma Separated Values (.csv).
2. Run:   python3 sheet_to_categories.py "Item Checklist Sorter - Items 26.2.csv" categories.yml
3. Copy categories.yml into plugins/SmartCopperGolem/ and run /scg reload.

The sheet uses display names ("Oak Stripped Log", "Birch Slabs"). This script maps them to item ids
using item_ids_26_2.txt (every item id of Minecraft 26.2, read from the server itself).
Rows it can't map are listed at the end so you can fix them by hand. Nothing is silently dropped.

No third-party packages needed.
"""
import csv
import difflib
import itertools
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
IDS = set((HERE / "item_ids_26_2.txt").read_text().split())

# words the sheet spells differently from the game
WORD_FIXES = {
    "slabs": "slab", "saplings": "sapling", "varients": "variants", "variant": "variants",
    "chiselled": "chiseled", "terracota": "terracotta", "amythest": "amethyst",
    "wood": "wood", "leaves": "leaves", "glass": "glass", "grass": "grass",
    "chisled": "chiseled", "chisiled": "chiseled", "crismson": "crimson", "amythyst": "amethyst",
    "lecturn": "lectern", "potery": "pottery", "armour": "armor", "creame": "cream",
}

# Sheet rows that don't spell an item id (group rows, old names, typos too far gone for matching).
# value = (entries, note). Entries are item ids or globs; None = nothing to add. Every entry is
# checked against item_ids_26_2.txt when the script runs.
ALIASES = {
    "Snow Layer": (["snow"], None),
    "Brick Block": (["bricks"], None),
    "Packed Mud Brick": (["mud_bricks"], None),
    "Packed Mud Brick Stairs": (["mud_brick_stairs"], None),
    "Packed Mud Brick Slab": (["mud_brick_slab"], None),
    "Packed Mud Brick Wall": (["mud_brick_wall"], None),
    "Dripstone": (["dripstone_block"], None),
    "Nether Quartz": (["quartz"], None),
    "Redstone Dust": (["redstone"], None),
    "Redstone Repeater": (["repeater"], None),
    "Redstone Comparator": (["comparator"], None),
    "Gold Pressure Plate": (["light_weighted_pressure_plate"], None),
    "Iron Pressure Plate": (["heavy_weighted_pressure_plate"], None),
    "Cut Copper Block": (["cut_copper"], None),
    "Ominous Banner": (None, "no separate item in 26.2; covered by the banner entries"),
    "Ye;;pw Stained Glass": (["yellow_stained_glass"], "typo in sheet, read as Yellow Stained Glass"),
    "Blackstone Bricks": (["polished_blackstone_bricks"], None),
    "Blackstone Brick Stairs": (["polished_blackstone_brick_stairs"], None),
    "Blackstone Brick Slab": (["polished_blackstone_brick_slab"], None),
    "Blackstone Brick Wall": (["polished_blackstone_brick_wall"], None),
    "Chisled Blackstone": (["chiseled_polished_blackstone"], None),
    "Cracked Blackstone Bricks": (["cracked_polished_blackstone_bricks"], None),
    "Chisiled Quartz": (["chiseled_quartz_block"], None),
    "Purpur Bricks": (["purpur_block"], "26.2 has no purpur bricks; assumed Purpur Block"),
    "Chisiled Purpur": (["purpur_pillar"], "assumed Purpur Pillar"),
    "Purpur Brick Stairs": (["purpur_stairs"], "assumed Purpur Stairs"),
    "Purpur Brick Slab": (["purpur_slab"], "assumed Purpur Slab"),
    "Grass": (["short_grass", "tall_grass"], None),
    "Both Dry Grasses": (["short_dry_grass", "tall_dry_grass"], None),
    "Rose/Poppy": (["poppy", "rose_bush"], None),
    "Eyeblossom": (["open_eyeblossom", "closed_eyeblossom"], None),
    "Hay Bale": (["hay_block"], None),
    "Glistering Melon": (["glistering_melon_slice"], None),
    "Stew": (["mushroom_stew", "rabbit_stew", "beetroot_soup", "suspicious_stew"], "assumed all stews/soups"),
    "Gapple": (["golden_apple", "enchanted_golden_apple"], None),
    "All Chicken Eggs": (["egg", "blue_egg", "brown_egg"], None),
    "Raw Chicken": (["chicken"], None),
    "Raw Beef": (["beef"], None),
    "Steak": (["cooked_beef"], None),
    "Raw Porkchop": (["porkchop"], None),
    "Raw Mutton": (["mutton"], None),
    "Raw Rabbit": (["rabbit"], None),
    "Raw Cod": (["cod"], None),
    "Raw Salmon": (["salmon"], None),
    "Eye of Ender": (["ender_eye"], None),
    "Enchanting Book": (["enchanted_book"], None),
    "Enchantment Table": (["enchanting_table"], None),
    "All Potery Sherds": (["*_pottery_sherd"], None),
    "All Paintings": (["painting"], None),
    "Boat/Saddle/Harness": (["*_boat", "*_raft", "saddle", "*_harness"], None),
    "Horse/Wolf/Nautilus Armour": (["*_horse_armor", "wolf_armor", "*_nautilus_armor"], None),
    "All music disks": (["music_disc_*"], None),
    "All Goat Horns": (["goat_horn"], None),
    "Book + Quill": (["writable_book"], None),
    "All Templates": (["*_smithing_template"], None),
    "Brush + Spyglass": (["brush", "spyglass"], None),
    "Name Tag/Lead": (["name_tag", "lead"], None),
    "All buckets": (["bucket", "*_bucket"], None),
    "All fireworks": (["firework_rocket", "firework_star"], None),
    "Helmets/Turtle Shell": (["*_helmet"], "includes turtle_helmet"),
    "Chestplates": (["*_chestplate"], None),
    "Leggings": (["*_leggings"], None),
    "Boots": (["*_boots"], None),
    "Shields/Totems": (["shield", "totem_of_undying"], None),
    "Arrow/Spectral Arrow": (["arrow", "spectral_arrow", "tipped_arrow"], None),
    "Bow/Crossbow/Trident": (["bow", "crossbow", "trident"], None),
    "Tools and Weapons": (["*_sword", "*_pickaxe", "*_axe", "*_shovel", "*_hoe", "*_spear", "mace"], "assumed swords, pickaxes, axes, shovels, hoes, spears, mace"),
    "Shears/Flint+Steel": (["shears", "flint_and_steel"], None),
}
UNCOUNTABLE = {"leaves", "glass", "grass", "moss", "wool", "ice", "bricks", "boots", "leggings", "shears", "stairs"}
# "Banner (And Variants)" style rows: any of these inside the brackets means "every colour/type"
MULTI_HINT = re.compile(r"\b(and\s+var\w*|all\s+\w+|any\s+\w+|colou?rs?|variants?|varients?)\b", re.I)


def tokens_of(text):
    text = text.lower().replace("'", "").replace("&", " and ")
    text = re.sub(r"[^a-z0-9 ]+", " ", text)
    return [WORD_FIXES.get(t, t) for t in text.split()]


def singular(word):
    if word in UNCOUNTABLE:
        return word
    if word.endswith("ies") and len(word) > 4:
        return word[:-3] + "y"
    if word.endswith("ses") or word.endswith("xes"):
        return word[:-2]
    if word.endswith("s") and not word.endswith("ss") and len(word) > 3:
        return word[:-1]
    return word


def candidates(tokens):
    """Every id the tokens could spell, in any word order, with singular/plural variants."""
    if not tokens or len(tokens) > 5:
        return []
    options = [sorted({t, singular(t)}) for t in tokens]
    found = []
    for combo in itertools.product(*options):
        for perm in itertools.permutations(combo):
            cand = "_".join(perm)
            if cand in IDS and cand not in found:
                found.append(cand)
    return found


def entry_exists(entry):
    if "*" in entry:
        rx = re.compile(entry.replace("*", ".*"))
        return any(rx.fullmatch(i) for i in IDS)
    return entry in IDS


ALIAS_KEYS = {" ".join(tokens_of(k)): v for k, v in ALIASES.items()}


def resolve(display):
    """-> (list of entries for categories.yml, note or None). Empty list = unresolved."""
    name = display.strip()
    key = " ".join(tokens_of(name))
    if key in ALIAS_KEYS:
        entries, note = ALIAS_KEYS[key]
        if entries is None:
            return [], note or "skipped"
        good = [e for e in entries if entry_exists(e)]
        bad = [e for e in entries if e not in good]
        extra = f"; not in 26.2, dropped: {', '.join(bad)}" if bad else ""
        return good, ((note or "alias") + extra) if (note or bad) else None
    multi = None
    m = re.match(r"^(.*?)\s*\((.*)\)\s*$", name)
    if m:
        name, inner = m.group(1), m.group(2)
        multi = bool(MULTI_HINT.search(inner))
    toks = tokens_of(name)

    exact = candidates(toks)
    if not exact and toks[-2:] == ["glass", "pane"] and "stained" not in toks:
        exact = candidates(toks[:-2] + ["stained", "glass", "pane"])
    if multi:
        base = "_".join(singular(t) if i == len(toks) - 1 else t for i, t in enumerate(toks))
        entries = []
        if exact:
            entries.append(exact[0])
        glob = f"*_{base}"
        if any(re.fullmatch(glob.replace("*", ".*"), i) for i in IDS):
            entries.append(glob)
        elif any(re.fullmatch(f"{base}_.*".replace("*", ".*"), i) for i in IDS):
            entries.append(f"{base}_*")
        if entries:
            return entries, "multi-item row -> " + ", ".join(entries)
        return [], None
    if exact:
        return [exact[0]], None

    guess = difflib.get_close_matches("_".join(toks), IDS, n=1, cutoff=0.88)
    if guess:
        return [guess[0]], f"fuzzy match -> {guess[0]} (check it)"
    return [], None


def main():
    if len(sys.argv) != 3:
        sys.exit("usage: sheet_to_categories.py <sheet.csv> <categories.yml>")
    src, dst = sys.argv[1], sys.argv[2]
    with open(src, newline="", encoding="utf-8-sig") as f:
        rows = list(csv.reader(f))
    header = [c.strip().lower() for c in rows[0]]
    try:
        i_item, i_cat = header.index("item"), header.index("category")
    except ValueError:
        sys.exit("The first row must have 'Item' and 'Category' columns.")

    cats, unresolved, notes = {}, [], []
    for line_no, row in enumerate(rows[1:], start=2):
        if len(row) <= max(i_item, i_cat):
            continue
        item, cat = row[i_item].strip(), row[i_cat].strip()
        if not item or not cat:
            continue
        entries, note = resolve(item)
        if not entries and note:
            notes.append(f"  line {line_no}: '{item}': {note}")
            continue
        if not entries:
            unresolved.append(f"  line {line_no}: '{item}' ({cat})")
            continue
        if note:
            notes.append(f"  line {line_no}: '{item}': {note}")
        bucket = cats.setdefault(cat, [])
        for e in entries:
            if e not in bucket:
                bucket.append(e)

    with open(dst, "w", encoding="utf-8") as out:
        out.write("# Generated by tools/sheet_to_categories.py\n")
        out.write("# Items in the same category are treated as 'the same kind of item' by the golems.\n")
        out.write("# Entries are item ids (oak_log) or globs (*_banner). An item can be in one category only.\n")
        out.write("categories:\n")
        for cat, entries in cats.items():
            out.write(f"  {cat}:\n")
            for e in entries:
                out.write(f'    - "{e}"\n')

    total = sum(len(v) for v in cats.values())
    print(f"{len(cats)} categories, {total} entries written to {dst}")
    if notes:
        print("\nReview these:")
        print("\n".join(notes))
    if unresolved:
        print(f"\nCould not map {len(unresolved)} row(s). Add them to {dst} by hand:")
        print("\n".join(unresolved))


if __name__ == "__main__":
    main()
