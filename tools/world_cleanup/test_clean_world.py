import gzip
from pathlib import Path
import struct
import tempfile
import unittest

from clean_world import clean_copy, dimension_for, modified_utf8
from split_nbt import Tag, number, read_nbt, write_nbt, Unsupported, transform_region
from sable_container import transform_container


def comp(**items):
    return Tag(10, {k.encode(): v for k, v in items.items()})


def text(value):
    return Tag(8, value.encode())


def listing(*items):
    return Tag(9, (10, list(items)))


def world(path):
    path.mkdir()
    (path / 'session.lock').write_bytes(b'\xe2\x98\x83')
    (path / 'level.dat').write_bytes(gzip.compress(write_nbt(b'', comp(
        Data=comp(LevelName=text('fixture'), Player=comp(Inventory=listing(
            comp(id=text('createfirefightingadd:stress_coupling'), count=number(1)),
            comp(id=text('minecraft:diamond'), count=number(2)))))))))


def snapshot(path):
    return {p.relative_to(path): p.read_bytes() for p in path.rglob('*') if p.is_file()}


class WorldCopyTest(unittest.TestCase):
    def test_copy_source_unchanged_and_foreign_data_preserved(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / 'source'
            world(source)
            data = source / 'data'; data.mkdir()
            own = gzip.compress(write_nbt(b'', comp(data=comp(PendingRemovals=listing()))))
            (data / 'createfirefightingadd_couplings.dat').write_bytes(own)
            (data / 'other_mod.dat').write_bytes(own)
            before = snapshot(source)
            target = root / 'result'
            report = clean_copy(source, target)
            self.assertEqual(snapshot(source), before)
            self.assertFalse((target / 'data/createfirefightingadd_couplings.dat').exists())
            self.assertEqual((target / 'data/other_mod.dat').read_bytes(), own)
            self.assertFalse(report['runtime_tested'])
            self.assertGreater(report['changed_files'], 0)
            _, out = read_nbt(gzip.decompress((target / 'level.dat').read_bytes()))
            self.assertEqual(out.value[b'Data'].value[b'LevelName'].value, b'result')
            self.assertEqual(report['world_name'], target.name)
            inventory = out.value[b'Data'].value[b'Player'].value[b'Inventory'].value[1]
            self.assertTrue(any(item.value.get(b'id') == text('minecraft:diamond') for item in inventory))
            self.assertFalse(any(item.value.get(b'id') == text('createfirefightingadd:stress_coupling') for item in inventory))

    def test_bad_container_leaves_no_output(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); source = root / 'source'; world(source)
            (source / 'broken.slvlr').write_bytes(b'bad')
            before = snapshot(source)
            with self.assertRaises(Unsupported): clean_copy(source, root / 'result')
            self.assertEqual(snapshot(source), before)
            self.assertFalse((root / 'result').exists())
            self.assertFalse(list(root.glob('.world-cleanup-*')))

    def test_existing_output_and_nested_output_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); source = root / 'source'; world(source)
            existing = root / 'existing'; existing.mkdir()
            for target in (source, source / 'inside', existing):
                with self.assertRaises(Unsupported): clean_copy(source, target)

    def test_world_lock_is_respected(self):
        from clean_world import lock_world
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); source = root / 'source'; world(source)
            with lock_world(source):
                with self.assertRaises(Unsupported): clean_copy(source, root / 'result')

    def test_sable_last_sector_need_not_have_padding(self):
        payload = gzip.compress(write_nbt(b'', comp(value=text('old'))))
        record = struct.pack('>IB', len(payload) + 1, 0) + payload
        raw = bytearray(4096)
        struct.pack_into('>I', raw, 0, (32 << 8) | ((len(record) + 127) // 128))
        raw.extend(record)  # exactly what Sable writes before close/padding
        path = Path('r.0.0.slvlr')
        self.assertEqual(transform_container(path, bytes(raw), lambda data: data), bytes(raw))
        with self.assertRaises(Unsupported): transform_container(path, bytes(raw[:-1]), lambda data: data)

    def test_dimensions(self):
        self.assertEqual(dimension_for(Path('DIM-1/region/r.0.0.mca')), b'minecraft:the_nether')
        self.assertEqual(dimension_for(Path('dimensions/mod/deep/world/sublevels/r.0.0.slvlr')), b'mod:deep/world')

    def test_world_name_modified_utf8(self):
        self.assertEqual(modified_utf8('新世界'), '新世界'.encode('utf-8'))
        self.assertEqual(modified_utf8('\U0001f692'), bytes.fromhex('eda0bdedba92'))
        self.assertEqual(modified_utf8('\0'), bytes.fromhex('c080'))

    def test_empty_and_unpadded_anvil(self):
        self.assertEqual(transform_region(b'', lambda data: data), b'')
        payload = write_nbt(b'', comp(value=text('keep')))
        record = struct.pack('>IB', len(payload) + 1, 3) + payload
        raw = bytearray(8192)
        struct.pack_into('>I', raw, 0, (2 << 8) | 1)
        raw.extend(record)
        self.assertEqual(transform_region(bytes(raw), lambda data: data), bytes(raw))
        with self.assertRaises(Unsupported): transform_region(bytes(raw[:-1]), lambda data: data)


if __name__ == '__main__': unittest.main()
