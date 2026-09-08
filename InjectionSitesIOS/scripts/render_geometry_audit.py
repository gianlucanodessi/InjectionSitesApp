"""QA contact sheet from original pixels and dashboard polygons; never changes assets."""
import json
from pathlib import Path
from PIL import Image, ImageDraw

ios = Path(__file__).resolve().parents[1]
geometry = json.loads((ios/'Resources/geometry.json').read_text())
manifest = json.loads((ios/'asset-manifest.json').read_text())
areas = ['LEFT_ARM','RIGHT_ARM','ABDOMEN','LEFT_THIGH','RIGHT_THIGH','LEFT_GLUTE','RIGHT_GLUTE']
sheet = Image.new('RGB', (7*240, 3*310), 'white')
for row, (avatar, prefix) in enumerate([('UOMO','man'),('DONNA','woman'),('YETI','yeti')]):
    for column, area in enumerate(areas):
        name = f'avatar_{prefix}_{"back" if "GLUTE" in area else "front"}'
        source = Image.open(ios/f'Resources/Assets.xcassets/{name}.imageset/{name}.png').convert('RGBA')
        points = [(x*source.width,y*source.height) for x,y in geometry[f'{avatar}/{area}/dashboard']]
        xs,ys = zip(*points); x0,y0,x1,y1 = min(xs),min(ys),max(xs),max(ys)
        dx,dy = (x1-x0)*.4,(y1-y0)*.25
        viewport = (max(0,x0-dx),max(0,y0-dy),min(source.width,x1+dx),min(source.height,y1+dy))
        overlay = Image.new('RGBA',source.size)
        ImageDraw.Draw(overlay).polygon(points,fill=(22,163,74,140),outline=(255,255,255,255))
        preview = Image.alpha_composite(source,overlay).crop(viewport)
        preview.thumbnail((230,275))
        tile = Image.new('RGB',(240,310),'#f6f8fc')
        tile.paste(preview,((240-preview.width)//2,25+(275-preview.height)//2),preview)
        ImageDraw.Draw(tile).text((6,6), f'{avatar} {area}',fill='black')
        sheet.paste(tile,(column*240,row*310))
(ios/'build').mkdir(exist_ok=True)
sheet.save(ios/'build/geometry-audit.png')
print('Rendered build/geometry-audit.png (QA only, not an app asset)')
