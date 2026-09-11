"""Measure the actual Swift history palette; works without Xcode."""
from pathlib import Path
import re

source = Path(__file__).resolve().parents[1] / 'Sources/HistoryPalette.swift'
colors = {name: int(value, 16) for name, value in re.findall(
    r'static let (\w+): UInt32 = 0x([0-9A-Fa-f]{6})', source.read_text(encoding='utf-8'))}


def luminance(hex_value):
    channels = [(hex_value >> shift & 255) / 255 for shift in (16, 8, 0)]
    linear = [c / 12.92 if c <= .04045 else ((c + .055) / 1.055) ** 2.4 for c in channels]
    return sum(c * weight for c, weight in zip(linear, (.2126, .7152, .0722)))


ratios = []
for text in ('primaryText', 'secondaryText', 'rapidText', 'basalText', 'deleteText'):
    for background in ('rapidBackground', 'basalBackground', 'sensorBackground'):
        a, b = luminance(colors[text]), luminance(colors[background])
        ratio = (max(a, b) + .05) / (min(a, b) + .05)
        assert ratio >= 4.5, (text, background, ratio)
        ratios.append(ratio)
        print(f'{text} / {background}: {ratio:.2f}:1 PASS')
assert colors['rapidBackground'] == 0xE5F5E9
assert colors['basalBackground'] == 0xF0E6FA
assert colors['sensorBackground'] == 0xE5E7EB
print(f'PASS: all 15 text/background combinations >= 4.5:1; minimum {min(ratios):.2f}:1')
