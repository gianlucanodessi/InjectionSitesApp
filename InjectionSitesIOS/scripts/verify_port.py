"""Read-only structural checks available on Windows and macOS."""
import hashlib
import json
import struct
from PIL import Image
from pathlib import Path
from sync_android_assets import expected_geometry

root = Path(__file__).resolve().parents[2]
ios = root/'InjectionSitesIOS'
manifest = json.loads((ios/'asset-manifest.json').read_text())
assert len(manifest) == 6
for name, metadata in manifest.items():
    original = (root/metadata['source']).read_bytes()
    copied = (ios/f'Resources/Assets.xcassets/{name}.imageset/{name}.png').read_bytes()
    assert original == copied, name
    assert hashlib.sha256(copied).hexdigest() == metadata['sha256'], name
    assert list(struct.unpack('>II', copied[16:24])) == metadata['size'], name
    alpha = Image.open(root/metadata['source']).convert('RGBA').getchannel('A').tobytes()
    mask = (ios/f'Resources/{name}.mask').read_bytes()
    assert len(mask) == (len(alpha)+7)//8
    assert all(bool(mask[i//8] & (1 << (i%8))) == bool(value) for i,value in enumerate(alpha)), name
geometry = json.loads((ios/'Resources/geometry.json').read_text())
assert geometry == json.loads(json.dumps(expected_geometry()))
assert len(geometry) == 42
for key, points in geometry.items():
    assert len(points) >= 6, key
    assert all(len(p) == 2 for p in points)
for path in (ios/'Resources/Assets.xcassets').rglob('Contents.json'):
    json.loads(path.read_text())
print('PASS: 6 PNG identical to Android (SHA-256 and dimensions); 42 reference outlines identical; asset catalog JSON valid')
