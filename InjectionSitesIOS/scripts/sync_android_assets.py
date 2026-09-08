"""Copy original bytes and extract approved coordinates, without changing Android."""
import hashlib
import json
import re
import shutil
import struct
from PIL import Image
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ANDROID = ROOT / 'InjectionSitesAndroid_v2/app/src/main'
IOS = ROOT / 'InjectionSitesIOS'

def expected_geometry():
    source = (ANDROID / 'java/com/example/injectionsites/MainActivity.kt').read_text(encoding='utf-8')
    geometry = {}
    front = source.split('private fun frontOutline(')[1].split('private fun backOutline(')[0]
    for area, marker in [('LEFT_ARM', 'BodyArea.LEFT_ARM ->'), ('ABDOMEN', 'BodyArea.ABDOMEN ->'), ('LEFT_THIGH', 'BodyArea.LEFT_THIGH ->')]:
        section = front.split(marker)[1].split('\n        }')[0]
        for avatar, coords in re.findall(r'AvatarStyle\.(\w+) -> listOf\((.*?)\)(?:\r?\n|$)', section):
            points = [[float(x), float(y)] for x, y in re.findall(r'Offset\((\.[0-9]+)f,(\.[0-9]+)f\)', coords)]
            geometry[f'{avatar}/{area}/dashboard'] = points
            if area != 'ABDOMEN':
                geometry[f'{avatar}/{area.replace("LEFT", "RIGHT")}/dashboard'] = [[1-x, y] for x,y in points]
    back = source.split('private fun backOutline(')[1].split('private fun frontZones(')[0]
    for avatar, coords in re.findall(r'AvatarStyle\.(\w+) -> listOf\((.*?)\)(?:\r?\n|$)', back):
        points = [[float(x), float(y)] for x,y in re.findall(r'Offset\((\.[0-9]+)f,(\.[0-9]+)f\)', coords)]
        geometry[f'{avatar}/LEFT_GLUTE/dashboard'] = points
        geometry[f'{avatar}/RIGHT_GLUTE/dashboard'] = [[1-x,y] for x,y in points]
    zoom = source.split('private fun zoomOutline(')[1].split('private fun armOutline(')[0]
    for avatar, coords in re.findall(r'AvatarStyle\.(\w+)->p\(([0-9,]+)\)', zoom):
        values = list(map(int, coords.split(',')))
        geometry[f'{avatar}/ABDOMEN/detail'] = list(zip(values[::2],values[1::2]))
    for part in ['arm', 'thigh', 'glute']:
        section = source.split(f'private fun {part}Outline(')[1].split('\n}')[0]
        for avatar, left, right in re.findall(r'AvatarStyle\.(\w+)->if\(left\)p\(([0-9,]+)\) else p\(([0-9,]+)\)', section):
            for side, coords in [('LEFT', left), ('RIGHT', right)]:
                values = list(map(int, coords.split(',')))
                geometry[f'{avatar}/{side}_{part.upper()}/detail'] = list(zip(values[::2],values[1::2]))
    assert len(geometry) == 42, len(geometry)
    return geometry

def main():
    catalog = IOS / 'Resources/Assets.xcassets'
    catalog.mkdir(parents=True, exist_ok=True)
    info = {'version': 1, 'author': 'xcode'}
    (catalog/'Contents.json').write_text(json.dumps({'info': info})+'\n')
    manifest = {}
    for original in sorted((ANDROID/'res/drawable-nodpi').glob('avatar_*.png')):
        name = original.stem
        asset = catalog / f'{name}.imageset'
        asset.mkdir(exist_ok=True)
        shutil.copyfile(original, asset/original.name)
        (asset/'Contents.json').write_text(json.dumps({'images':[{'filename': original.name, 'idiom':'universal','scale':'1x'}], 'info':info})+'\n')
        data = original.read_bytes()
        manifest[name] = {'source': original.relative_to(ROOT).as_posix(), 'sha256':hashlib.sha256(data).hexdigest(), 'size':list(struct.unpack('>II',data[16:24]))}
        # Hit-test alpha mask only: originals and anatomical coordinates stay untouched.
        alpha = Image.open(original).convert('RGBA').getchannel('A').tobytes()
        mask = bytearray((len(alpha)+7)//8)
        for i, value in enumerate(alpha):
            if value: mask[i//8] |= 1 << (i%8)
        (IOS/f'Resources/{name}.mask').write_bytes(mask)
    icon = catalog/'AppIcon.appiconset'
    icon.mkdir(exist_ok=True)
    shutil.copyfile(ANDROID/'res/mipmap-xxxhdpi/app_icon_yeti_1024.png', icon/'AppIcon.png')
    (icon/'Contents.json').write_text(json.dumps({'images':[{'filename':'AppIcon.png','idiom':'universal','platform':'ios','size':'1024x1024'}], 'info':info})+'\n')
    (IOS/'Resources/geometry.json').write_text(json.dumps(expected_geometry(), indent=2)+'\n')
    (IOS/'asset-manifest.json').write_text(json.dumps(manifest, indent=2)+'\n')

if __name__ == '__main__':
    main()

