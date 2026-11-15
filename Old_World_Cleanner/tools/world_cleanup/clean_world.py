"""Remove legacy components in a NEW world copy, without loading Minecraft.

Default: discard components moved to Powertrain, including shared hose brackets.
--all-legacy: discard all old Firefighting blocks/items instead.
"""
import argparse
from copy import deepcopy
from contextlib import contextmanager
import gzip
import hashlib
import json
import os
from pathlib import Path
import shutil
import sys
import tempfile
import zlib

from split_nbt import Tag, Unsupported, read_nbt, write_nbt, transform_region
from sable_container import transform_container, transform_payload

NBT_SUFFIXES = {'.dat', '.dat_old', '.nbt'}
COPY_SUFFIXES = {'.json', '.toml', '.txt', '.png', '.jpg', '.jpeg', '.log', '.lock'}


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def modified_utf8(value):
    """NBT strings use Java's UTF-16 code units encoded as modified UTF-8."""
    encoded = bytearray()
    units = value.encode('utf-16-be', errors='surrogatepass')
    for index in range(0, len(units), 2):
        unit = int.from_bytes(units[index:index+2], 'big')
        if 0 < unit < 0x80:
            encoded.append(unit)
        elif unit < 0x800:
            encoded.extend((0xc0 | (unit >> 6), 0x80 | (unit & 63)))
        else:
            encoded.extend((0xe0 | (unit >> 12), 0x80 | ((unit >> 6) & 63), 0x80 | (unit & 63)))
    if len(encoded) > 65535: raise Unsupported('World display name is too long')
    return bytes(encoded)


def dimension_for(relative):
    parts = relative.parts
    if parts[0] == 'DIM-1': return b'minecraft:the_nether'
    if parts[0] == 'DIM1': return b'minecraft:the_end'
    if len(parts) > 3 and parts[0] == 'dimensions':
        # Region/entities/poi/sublevels/data delimit the dimension's path.
        end = next((i for i in range(3, len(parts)) if parts[i] in
                    {'region', 'entities', 'poi', 'sublevels', 'data'}), None)
        if end is None: raise Unsupported(f'Cannot determine custom dimension: {relative}')
        return (parts[1] + ':' + '/'.join(parts[2:end])).encode('utf-8')
    return b'minecraft:overworld'


def files_in(source):
    paths = []
    for path in sorted(source.rglob('*')):
        # Includes Windows junctions on Python versions without is_junction().
        if path.is_symlink() or getattr(path.lstat(), 'st_file_attributes', 0) & 0x400:
            raise Unsupported(f'Linked/reparse path is not supported: {path}')
        if path.is_file() and path != source / 'session.lock': paths.append(path)
    return paths


@contextmanager
def lock_world(source):
    """Honor Minecraft's session.lock without creating or changing any file."""
    lock = source / 'session.lock'
    if not lock.exists():
        raise Unsupported('Missing session.lock; select a closed Minecraft world directory')
    with lock.open('rb') as stream:
        try:
            lock_bytes = stream.read()
            stream.seek(0)
            if os.name == 'nt':
                import msvcrt
                msvcrt.locking(stream.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(stream.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError as error:
            raise Unsupported('World is open or locked. Close the game/server first.') from error
        try:
            yield lock_bytes
        finally:
            if os.name == 'nt':
                stream.seek(0)
                msvcrt.locking(stream.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                fcntl.flock(stream.fileno(), fcntl.LOCK_UN)


def rewrite_file(path, raw, transform):
    try:
        return _rewrite_file(path, raw, transform)
    except (OSError, ValueError, KeyError, EOFError, zlib.error) as error:
        raise Unsupported(f'{path}: {error}') from error


def _rewrite_file(path, raw, transform):
    suffix = path.suffix.lower()
    if suffix == '.mca': return transform_region(raw, transform)
    if suffix in {'.slvlr', '.slvls'}: return transform_container(path, raw, transform)
    if suffix == '.slvl' or suffix in NBT_SUFFIXES:
        return transform_payload(raw, transform)
    if suffix in COPY_SUFFIXES: return raw
    raise Unsupported(f'Unsupported file container: {path.name}')


def owned_saved_data(relative):
    return ('data' in relative.parts[:-1] and
            relative.name.startswith('createfirefightingadd_') and
            relative.suffix.lower() in {'.dat', '.dat_old'})


def clean_copy(source, destination, all_legacy=False, progress=None):
    from cleanup_nbt import clean_document, clean_tree
    from physics_cleanup import (PhysicsContext, collect_physics_tree, resolve_context,
                                 clean_physics_tree, collect_empty_bodies)

    original_path = Path(source).absolute()
    if original_path.is_symlink() or getattr(original_path.lstat(), 'st_file_attributes', 0) & 0x400:
        raise Unsupported('Source must not be a linked directory')
    source = original_path.resolve(strict=True)
    destination = Path(destination).resolve()
    if not source.is_dir() or not (source / 'level.dat').is_file():
        raise Unsupported('Select a Minecraft world containing level.dat')
    if destination.exists(): raise Unsupported('Destination already exists; choose a new folder')
    if destination.is_relative_to(source) or source.is_relative_to(destination):
        raise Unsupported('Source and destination must be separate directory trees')
    if not destination.parent.is_dir(): raise Unsupported('Destination parent does not exist')
    report = {'mode': 'all-legacy' if all_legacy else 'discard-powertrain',
              'source': str(source), 'destination': str(destination),
              'world_name': destination.name,
              'runtime_tested': False, 'cleaner_version': 2, 'files': []}
    with lock_world(source) as lock_bytes:
        paths = files_in(source)
        context = PhysicsContext()
        hashes = {}
        for index, path in enumerate(paths):
            relative = path.relative_to(source)
            dimension = dimension_for(relative)
            raw = path.read_bytes()
            hashes[relative] = digest(raw)
            def collect(data):
                _, root = read_nbt(data)
                collect_physics_tree(root, context, dimension=dimension,
                                     all_legacy=all_legacy, source_name=path.name)
                cleaned = clean_tree(deepcopy(root), all_legacy=all_legacy)
                collect_empty_bodies(root, cleaned, context, dimension=dimension)
                return data
            rewrite_file(path, raw, collect)
            if progress and index % 500 == 0: progress('Inspect', index, len(paths))
        resolve_context(context)
        stage = Path(tempfile.mkdtemp(prefix='.world-cleanup-', dir=destination.parent))
        try:
            (stage / 'session.lock').write_bytes(lock_bytes)
            for index, path in enumerate(paths):
                relative = path.relative_to(source)
                dimension = dimension_for(relative)
                raw = path.read_bytes()
                if digest(raw) != hashes[relative]: raise Unsupported(f'Source changed: {relative}')
                remove = owned_saved_data(relative)
                def transform(data):
                    name, root = read_nbt(data)
                    cleaned = clean_physics_tree(root, context, dimension=dimension)
                    if relative.as_posix() in ('level.dat', 'level.dat_old'):
                        level_data = cleaned.value.get(b'Data')
                        if level_data is not None and level_data.kind == 10:
                            level_data.value[b'LevelName'] = Tag(8, modified_utf8(destination.name))
                    return clean_document(write_nbt(name, cleaned), all_legacy=all_legacy)
                after = None if remove else rewrite_file(path, raw, transform)
                if after is not None:
                    output = stage / relative
                    output.parent.mkdir(parents=True, exist_ok=True)
                    output.write_bytes(after)
                report['files'].append({'path': relative.as_posix(),
                                        'source_sha256': hashes[relative],
                                        'output_sha256': None if remove else digest(after),
                                        'action': 'remove-owned-data' if remove else
                                        ('rewrite' if after != raw else 'copy')})
                if progress and index % 500 == 0: progress('Clean', index, len(paths))
            # Minecraft's lock protects the source; also detect edits by other tools.
            if paths != files_in(source): raise Unsupported('Source file list changed')
            for path in paths:
                if digest(path.read_bytes()) != hashes[path.relative_to(source)]:
                    raise Unsupported(f'Source changed: {path.relative_to(source)}')
            report['changed_files'] = sum(entry['action'] != 'copy' for entry in report['files'])
            report['emptied_by_block_removal'] = len(context.removed_bodies)
            report['disconnected_rope_ids'] = len(context.removed_rope_ids)
            report['removed_constraint_providers'] = [
                {'dimension': dim.decode('utf-8'), 'position': [x,y,z]}
                for dim,x,y,z in sorted(context.found_providers)]
            (stage / 'world-cleanup-report.json').write_text(
                json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
            if destination.exists(): raise Unsupported('Destination appeared during cleanup')
            os.rename(stage, destination)
        finally:
            if stage.exists():
                resolved = stage.resolve(strict=True)
                if resolved.parent != destination.parent or not resolved.name.startswith('.world-cleanup-'):
                    raise Unsupported('Unexpected temporary directory path')
                shutil.rmtree(resolved)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('--output', type=Path, required=True, help='New copy; never the source directory')
    parser.add_argument('--all-legacy', action='store_true', help='Also discard old Firefighting content')
    args = parser.parse_args()
    print('Creates a separate copy. Discarded components and connections cannot be recovered IN THAT COPY.')
    try:
        report = clean_copy(args.source, args.output, args.all_legacy,
                            lambda phase, index, count: print(f'{phase}: {index}/{count}', flush=True))
    except (OSError, ValueError, EOFError, KeyError, gzip.BadGzipFile) as error:
        print(f'FAILED; source untouched, no completed output: {error}', file=sys.stderr)
        return 2
    print(f"Created {report['destination']}; changed {report['changed_files']} files. Runtime test still required.")
    print(f"Select this world in Minecraft: {report['world_name']}")
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
