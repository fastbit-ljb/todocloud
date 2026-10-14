"""Align existing mascot artwork before running mascot_assets.py (requires Pillow).

    python mobile/tools/normalize_mascot_layout.py <source_root> <output_root>

The companion JSON records reviewed character bounds for the v2 paper artwork.
Bounds exclude floating snowflakes, confetti and alert bubbles, but include the
character's clothes, hair, hat and held paper. The complete artwork is uniformly
scaled and translated; no individual feature is redrawn, stretched or recoloured.
The source PNGs are only read. Output is a separate 1254x1254 RGBA PNG set.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from PIL import Image

from mascot_assets import STATES


def normalize(image: Image.Image, bounds: list[int], layout: dict) -> tuple[Image.Image, dict]:
    size = layout["canvas_size"]
    if image.mode != "RGBA" or image.size != (size, size):
        raise ValueError(f"Expected {size}x{size} RGBA artwork, got {image.size} {image.mode}")
    left, top, right, bottom = bounds
    if not (0 <= left < right <= size and 0 <= top < bottom <= size):
        raise ValueError(f"Invalid subject bounds: {bounds}")

    scale = layout["subject_height"] / (bottom - top)
    scaled_size = round(size * scale)
    # One scale on both axes preserves the character, paper and hand proportions.
    actual_scale = scaled_size / size
    offset_x = round(layout["subject_center_x"] - (left + right) / 2 * actual_scale)
    offset_y = round(layout["subject_bottom"] - bottom * actual_scale)
    scaled = image.resize((scaled_size, scaled_size), Image.Resampling.LANCZOS)

    # Include every ornament in the clipping check, even though it did not determine
    # the scale. Ignore near-transparent background residue from image generation.
    visible = scaled.getchannel("A").point(lambda alpha: 255 if alpha >= 32 else 0).getbbox()
    if visible is None:
        raise ValueError("Artwork is fully transparent")
    placed_visible = [visible[0] + offset_x, visible[1] + offset_y,
                      visible[2] + offset_x, visible[3] + offset_y]
    if not (0 <= placed_visible[0] < placed_visible[2] <= size
            and 0 <= placed_visible[1] < placed_visible[3] <= size):
        raise ValueError(f"Layout would clip the character or its ornaments: {placed_visible}")

    placed_subject = [round(left * actual_scale + offset_x), round(top * actual_scale + offset_y),
                      round(right * actual_scale + offset_x), round(bottom * actual_scale + offset_y)]
    height = placed_subject[3] - placed_subject[1]
    center_x = (placed_subject[0] + placed_subject[2]) / 2
    if abs(height - layout["subject_height"]) > 1:
        raise ValueError(f"Subject height is not aligned: {height}")
    if abs(center_x - layout["subject_center_x"]) > 1:
        raise ValueError(f"Subject centre is not aligned: {center_x}")
    if abs(placed_subject[3] - layout["subject_bottom"]) > 1:
        raise ValueError(f"Subject bottom is not aligned: {placed_subject[3]}")

    canvas = Image.new("RGBA", (size, size))
    canvas.alpha_composite(scaled, (offset_x, offset_y))
    return canvas, {
        "source_subject": bounds,
        "scale": actual_scale,
        "offset": [offset_x, offset_y],
        "output_subject": placed_subject,
        "subject_height": height,
        "subject_center_x": center_x,
        "subject_bottom": placed_subject[3],
        "visible_bounds": placed_visible,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("source_root", type=Path)
    parser.add_argument("output_root", type=Path)
    parser.add_argument("--layout", type=Path, default=Path(__file__).with_name("mascot_character_layout.json"))
    args = parser.parse_args()
    source_root, output_root = args.source_root.resolve(), args.output_root.resolve()
    # Preserve all sources, including earlier mascot sets stored beside these three.
    if source_root == output_root or source_root.is_relative_to(output_root):
        parser.error("output_root must not contain or equal source_root")
    layout = json.loads(args.layout.read_text(encoding="utf-8"))
    states = [stem for stem, _, _ in STATES]
    pending = []
    report = {}
    for set_id, config in layout["sets"].items():
        if set(config["subjects"]) != set(states):
            parser.error(f"{set_id}: layout must specify all eight states")
        source_folder = source_root / config["source_folder"]
        destination = output_root / set_id
        if destination.is_relative_to(source_folder) or source_folder.is_relative_to(destination):
            parser.error(f"{set_id}: output must not overlap source artwork")
        report[set_id] = {}
        for stem in states:
            source = source_folder / f"{stem}.png"
            source_hash = hashlib.sha256(source.read_bytes()).hexdigest()
            with Image.open(source) as image:
                image.load()
                canvas, geometry = normalize(image, config["subjects"][stem], layout)
            geometry["source_sha256"] = source_hash
            report[set_id][stem] = geometry
            pending.append((destination / source.name, canvas))

    # Check every frame before writing any output.
    for destination, canvas in pending:
        destination.parent.mkdir(parents=True, exist_ok=True)
        canvas.save(destination, "PNG")
    output_root.mkdir(parents=True, exist_ok=True)
    (output_root / "layout-report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    for set_id, frames in report.items():
        heights = [frame["subject_height"] for frame in frames.values()]
        centres = [frame["subject_center_x"] for frame in frames.values()]
        bottoms = [frame["subject_bottom"] for frame in frames.values()]
        print(f"{set_id}: {len(frames)} states; height={min(heights)}..{max(heights)}; "
              f"centre={min(centres)}..{max(centres)}; bottom={min(bottoms)}..{max(bottoms)}; no clipping")


if __name__ == "__main__":
    main()
