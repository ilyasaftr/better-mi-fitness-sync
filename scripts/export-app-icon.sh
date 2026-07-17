#!/usr/bin/env bash
# Export branding/app-icon.svg → master PNG + Android mipmaps + iOS AppIcon.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SVG="$ROOT/branding/app-icon.svg"
OUT="$ROOT/branding/app-icon-1024.png"
IOS="$ROOT/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png"
RES="$ROOT/androidApp/src/main/res"

if [[ ! -f "$SVG" ]]; then
  echo "Missing $SVG" >&2
  exit 1
fi

export_png() {
  local size="$1" dest="$2"
  if command -v rsvg-convert >/dev/null 2>&1; then
    rsvg-convert -w "$size" -h "$size" "$SVG" -o "$dest"
  elif command -v magick >/dev/null 2>&1; then
    magick -background none -density 300 "$SVG" -resize "${size}x${size}" "$dest"
  else
    echo "Need rsvg-convert or ImageMagick magick" >&2
    exit 1
  fi
  echo "  wrote $dest (${size}x${size})"
}

echo "Exporting app icon from SVG…"
export_png 1024 "$OUT"
export_png 1024 "$IOS"

# Android launcher densities (legacy mipmap PNGs)
declare -A SIZES=(
  [mdpi]=48
  [hdpi]=72
  [xhdpi]=96
  [xxhdpi]=144
  [xxxhdpi]=192
)

for dens in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  s="${SIZES[$dens]}"
  dir="$RES/mipmap-${dens}"
  mkdir -p "$dir"
  export_png "$s" "$dir/ic_launcher.png"
  cp "$dir/ic_launcher.png" "$dir/ic_launcher_round.png"
done

echo "Done. Master: $OUT"
