"""Build the offline single-file orbit preview with only Python's stdlib."""
from pathlib import Path
import re

root = Path(__file__).resolve().parent
document = (root / "index.html").read_text(encoding="utf-8")
document = document.replace(
    '<link rel="stylesheet" href="styles.css">',
    "<style>\n" + (root / "styles.css").read_text(encoding="utf-8") + "\n</style>",
)

def embed(match):
    source = (root / match.group(1)).read_text(encoding="utf-8")
    return "<script>\n" + source + "\n</script>"

document = re.sub(r'<script src="([a-z.-]+\.js)"></script>', embed, document)
target = root / "SevenDays-Orbit-Preview.html"
target.write_text(document, encoding="utf-8")
print(f"Built {target.name}: {target.stat().st_size:,} bytes")
