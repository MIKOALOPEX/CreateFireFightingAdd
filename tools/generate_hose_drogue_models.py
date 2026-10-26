"""Export UUID-matched rigid parts; never modify the user's source models/textures."""
import argparse
import copy
import json
import math
from pathlib import Path
import shutil

parser = argparse.ArgumentParser()
parser.add_argument('source', type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
assets = root / 'src/main/resources/assets/createfirefightingadd'
models = assets / 'models/block/fluid_coupling'
textures = assets / 'textures/block/fluid_coupling'
models.mkdir(parents=True, exist_ok=True)
textures.mkdir(parents=True, exist_ok=True)

def load(path):
    return json.loads(path.read_text(encoding='utf-8'))

def order(nodes):
    for node in nodes:
        if isinstance(node, str):
            yield node
        else:
            yield from order(node['children'])

def write_model(name, elements, texture):
    data = {'credit': 'User model, split for rigid-part animation', 'textures': {
        '1': 'createfirefightingadd:block/fire_hose_end_black',
        '2': 'createfirefightingadd:block/fluid_coupling/' + texture,
        'particle': 'createfirefightingadd:block/fluid_coupling/' + texture}, 'elements': elements}
    (models / (name + '.json')).write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')

source = args.source / 'Drogue'
open_bb = load(source / 'Drogue.bbmodel')
closed_bb = load(source / 'Drogue_stowed.bbmodel')
opened = dict(zip(order(open_bb['outliner']), load(source / 'Drogue.json')['elements'], strict=True))
closed = dict(zip(order(closed_bb['outliner']), load(source / 'Drogue_stowed.json')['elements'], strict=True))
assert opened.keys() == closed.keys(), 'Deployment must preserve every element UUID'
fixed, parts, checks = [], [], 0

def transform(point, delta, pivot, axis, angle, p):
    v = [point[i] + delta[i] * p - pivot[i] for i in range(3)]
    a = math.radians(angle * p)
    c, s = math.cos(a), math.sin(a)
    x, y, z = v
    if axis == 'x': v = [x, c*y-s*z, s*y+c*z]
    if axis == 'y': v = [c*x+s*z, y, -s*x+c*z]
    if axis == 'z': v = [c*x-s*y, s*x+c*y, z]
    return [v[i] + pivot[i] for i in range(3)]

for uuid, start in closed.items():
    end = opened[uuid]
    assert start.get('rotation', {}).get('angle', 0) == 0, 'Expected unrotated stowed part'
    delta = [end['from'][i] - start['from'][i] for i in range(3)]
    assert all(abs(end['to'][i] - start['to'][i] - delta[i]) < 1e-8 for i in range(3)), 'Parts must not stretch'
    rotation = end.get('rotation', {})
    axis, angle, pivot = rotation.get('axis', 'y'), rotation.get('angle', 0), rotation.get('origin', [0, 0, 0])
    element = copy.deepcopy(start)
    # Keep a stable UV map throughout deployment instead of switching texture faces mid-animation.
    element['faces'] = copy.deepcopy(end['faces'])
    if not any(delta) and angle == 0:
        fixed.append(element)
        continue
    name = 'hose_drogue_part_' + str(len(parts))
    write_model(name, [element], 'hose_drogue')
    parts.append((name, delta, pivot, axis, angle))
    # Compare all eight corners against both original model poses, not screenshots or a new shape.
    for mask in range(8):
        a = [start['to' if mask & (1 << i) else 'from'][i] for i in range(3)]
        b = [end['to' if mask & (1 << i) else 'from'][i] for i in range(3)]
        assert max(abs(x-y) for x,y in zip(transform(a, delta, pivot, axis, angle, 0), a)) < 1e-8
        expected = transform(b, [0,0,0], pivot, axis, angle, 1)
        assert max(abs(x-y) for x,y in zip(transform(a, delta, pivot, axis, angle, 1), expected)) < 1e-8
        checks += 2

write_model('hose_drogue_fixed', fixed, 'hose_drogue')
write_model('hose_probe', load(args.source / 'Probe/Probe.json')['elements'], 'hose_probe')
shutil.copyfile(source / 'Probe.png', textures / 'hose_drogue.png')
shutil.copyfile(args.source / 'Probe/Probe.png', textures / 'hose_probe.png')

rows = []
for name, delta, pivot, axis, angle in parts:
    numbers = ', '.join(str(float(v)) for v in delta + pivot)
    rows.append(f'        new Part("{name}", {numbers}, \'{axis}\', {float(angle)}f)')
java = '''package com.mikoalopex.createfirefightingadd.content.fluids.coupling;

import java.util.List;

/** Generated from user Drogue model UUIDs by tools/generate_hose_drogue_models.py.
 * Coordinates are pixels in the original -X model frame. No mesh stretching. */
public final class HoseDrogueParts {
    private HoseDrogueParts() {}
    public record Part(String model, double dx, double dy, double dz,
        double px, double py, double pz, char axis, float angle) {}
    public static final List<Part> PARTS = List.of(
''' + ',\n'.join(rows) + ');\n}\n'
(root / 'src/main/java/com/mikoalopex/createfirefightingadd/content/fluids/coupling/HoseDrogueParts.java').write_text(java, encoding='utf-8')
print(f'PASS: {len(fixed)} fixed elements, {len(parts)} rigid moving elements, {checks} endpoint corner comparisons')
