"""Turn one mascot set's eight state PNGs into the app's launcher-icon resources.

    python mobile/tools/mascot_assets.py <set_id> <source_dir> [--alias-prefix PREFIX]

<source_dir> must hold 01_daily.png ... 08_frozen.png: square RGBA PNGs with a
transparent background. The source files are only read, never written.

For every state this writes, under mobile/app/src/main/res:
  drawable-nodpi/mascot_<set_id>_<state>.webp     in-app copy, see below
  drawable/ic_mascot_foreground_<set_id>_<state>.xml
  mipmap-anydpi-v26/ic_launcher_<set_id>_<state>.xml
  mipmap-anydpi-v33/ic_launcher_<set_id>_<state>.xml   (adds the themed-icon layer)

The in-app copy is the whole source canvas scaled proportionally to 512x512
(Lanczos, in premultiplied alpha so edges keep their colour) and stored as
lossless WebP with exact=True, so even fully transparent pixels keep their
values. Nothing is cropped, padded, recoloured or re-framed. Each file is
decoded again and must match the scaled pixels byte for byte.

Every set gets the same launcher container: the shared background, the same
@dimen/mascot_icon_inset, the same themed-icon layer.

Finally it prints the eight <activity-alias> entries for AndroidManifest.xml
(all disabled). Then add the set to MascotSets in branding/MascotSets.kt;
MascotSetsTest checks the manifest against that list. Needs Pillow.
"""
from __future__ import annotations

import argparse
import io
import re
import sys
from pathlib import Path

from PIL import Image

SIZE = 512
RES = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "res"

# (source file stem, resource key, launcher alias name) in the artwork's own order.
STATES = [
    ("01_daily", "daily", "Daily"),
    ("02_due_soon", "due_soon", "DueSoon"),
    ("03_overdue", "overdue", "Overdue"),
    ("04_progress", "progress", "Progress"),
    ("05_complete", "complete", "Complete"),
    ("06_no_tasks", "no_tasks", "NoTasks"),
    ("07_half_frozen", "half_frozen", "HalfFrozen"),
    ("08_frozen", "frozen", "Frozen"),
]

FOREGROUND = """<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:bottom="@dimen/mascot_icon_inset" android:drawable="@drawable/mascot_{set}_{key}" android:left="@dimen/mascot_icon_inset" android:right="@dimen/mascot_icon_inset" android:top="@dimen/mascot_icon_inset" />
</layer-list>
"""

ADAPTIVE_ICON = """<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_mascot_foreground_{set}_{key}" />
{monochrome}</adaptive-icon>
"""
MONOCHROME = '    <monochrome android:drawable="@drawable/ic_mascot_monochrome" />\n'

ALIAS = """        <activity-alias
            android:name=".launcher.{prefix}{alias}"
            android:enabled="false"
            android:exported="true"
            android:icon="@mipmap/ic_launcher_{set}_{key}"
            android:label="@string/app_name"
            android:roundIcon="@mipmap/ic_launcher_{set}_{key}"
            android:targetActivity=".MainActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity-alias>"""


def in_app_copy(source: Path) -> bytes:
    image = Image.open(source)
    image.load()
    if image.mode != "RGBA":
        sys.exit(f"{source.name}: expected an RGBA PNG with a transparent background, got {image.mode}")
    if image.width != image.height:
        sys.exit(f"{source.name}: expected a square canvas, got {image.width}x{image.height}")
    if image.size != (SIZE, SIZE):
        image = image.resize((SIZE, SIZE), Image.LANCZOS)
    out = io.BytesIO()
    image.save(out, "WEBP", lossless=True, quality=100, method=6, exact=True)
    decoded = Image.open(io.BytesIO(out.getvalue())).convert("RGBA")
    if decoded.size != image.size or decoded.tobytes() != image.tobytes():
        sys.exit(f"{source.name}: the WebP copy does not decode back to the same pixels")
    return out.getvalue()


def write(path: Path, data: bytes | str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if isinstance(data, str):
        path.write_text(data, encoding="utf-8", newline="\n")
    else:
        path.write_bytes(data)
    print(f"  wrote {path.relative_to(RES.parents[3])}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("set_id", help="stable lowercase id, e.g. mooncat")
    parser.add_argument("source_dir", type=Path)
    parser.add_argument(
        "--alias-prefix",
        help='launcher alias prefix under ".launcher."; default "<set_id>."',
    )
    args = parser.parse_args()

    if not re.fullmatch(r"[a-z][a-z0-9]*", args.set_id):
        sys.exit("set_id must be lowercase letters and digits, starting with a letter")
    source_dir = args.source_dir.resolve()
    if RES.resolve().is_relative_to(source_dir) or source_dir.is_relative_to(RES.resolve()):
        sys.exit("source_dir must not overlap the app's res directory")
    prefix = f"{args.set_id}." if args.alias_prefix is None else args.alias_prefix

    for stem, key, _ in STATES:
        source = source_dir / f"{stem}.png"
        if not source.is_file():
            sys.exit(f"missing {source}")
        fields = {"set": args.set_id, "key": key}
        write(RES / "drawable-nodpi" / f"mascot_{args.set_id}_{key}.webp", in_app_copy(source))
        write(RES / "drawable" / f"ic_mascot_foreground_{args.set_id}_{key}.xml", FOREGROUND.format(**fields))
        write(
            RES / "mipmap-anydpi-v26" / f"ic_launcher_{args.set_id}_{key}.xml",
            ADAPTIVE_ICON.format(monochrome="", **fields),
        )
        write(
            RES / "mipmap-anydpi-v33" / f"ic_launcher_{args.set_id}_{key}.xml",
            ADAPTIVE_ICON.format(monochrome=MONOCHROME, **fields),
        )

    print("\nAndroidManifest.xml entries (keep them disabled; the app enables one at a time):")
    for _, key, alias in STATES:
        print(ALIAS.format(prefix=prefix, alias=alias, set=args.set_id, key=key))


if __name__ == "__main__":
    main()
