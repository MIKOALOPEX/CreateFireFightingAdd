"""Sable 2.0.5 SubLevelStorageFile format (not Minecraft Anvil).

4096-byte span table, 24-bit sector offset + 8-bit sector count; sectors
are 128 bytes for .slvlr and 4096 for .slvls. Payload is a big-endian
length including one flag byte, then gzip NBT. Bit 0x10 selects an
external <index>.slvl file in <stem>.r / <stem>.s.
"""
import gzip
import struct
from split_nbt import Unsupported


def transform_payload(raw, transform):
    zipped = raw.startswith(b'\x1f\x8b')
    data = gzip.decompress(raw) if zipped else raw
    converted = transform(data)
    if converted == data:
        return raw
    return gzip.compress(converted, mtime=0) if zipped else converted


def transform_container(path, raw, transform):
    size = 128 if path.suffix == '.slvlr' else 4096
    if not raw:  # Newly created storage files can be empty before first close.
        return raw
    if len(raw) < 4096:
        raise Unsupported('Truncated Sable span table')
    used = set(range(4096 // size))
    entries = {}
    changed = False
    external_dir = path.with_suffix('.r' if size == 128 else '.s')
    for i, span in enumerate(struct.unpack('>1024I', raw[:4096])):
        if not span:
            continue
        offset, count = span >> 8, span & 255
        sectors = set(range(offset, offset + count))
        if not count or used.intersection(sectors) or offset * size + 5 > len(raw):
            raise Unsupported(f'Invalid/overlapping Sable span {i}')
        used.update(sectors)
        start = offset * size
        length = struct.unpack_from('>I', raw, start)[0]
        flag = raw[start + 4]
        if length < 1 or length + 4 > count * size or start + length + 4 > len(raw) or flag not in (0, 16):
            raise Unsupported(f'Invalid Sable payload header {i}')
        body = raw[start + 5:start + 4 + length]
        if flag == 16:
            if body:
                raise Unsupported('Sable record has both internal and external data')
            external = external_dir / f'{i}.slvl'
            if not external.is_file() or external.is_symlink():
                raise Unsupported(f'Missing/linked external Sable record: {external}')
            # Validation only: this file is converted by the normal file pass.
            transform_payload(external.read_bytes(), transform)
            converted = body
        else:
            converted = transform_payload(body, transform)
            changed |= converted != body
        entries[i] = struct.pack('>IB', len(converted) + 1, flag) + converted
    if not changed:
        return raw
    output = bytearray(4096)
    for i, record in entries.items():
        count = (len(record) + size - 1) // size
        if count > 255:
            raise Unsupported('Converted Sable record requires externalization; original is unchanged')
        offset = len(output) // size
        if offset > 0xffffff:
            raise Unsupported('Sable sector offset overflow')
        struct.pack_into('>I', output, i * 4, (offset << 8) | count)
        output.extend(record)
        output.extend(bytes(count * size - len(record)))
    return bytes(output)
