"""NBT byte-preserving primitives and callback-based Anvil container rewriting.

String payloads remain raw Java modified-UTF8 bytes; only known ASCII registry IDs
are changed. Numeric payloads/arrays remain byte-for-byte intact unless a section
palette has to be split for mixed ordinary and physical brackets.
"""
from dataclasses import dataclass
from copy import deepcopy
import io
import struct
import zlib
import gzip

OLD = b'createfirefightingadd:'
NEW = b'createpowertrain:'
BRACKET = OLD + b'hose_bracket'
POWER_BRACKET = NEW + b'hose_bracket'
MOVED = set(x.encode() for x in '''rocker ball_coupling_base ball_coupling_top
stress_coupling ball_coupling fluid_coupling physical_fire_hose physical_fire_hose_4
physical_fire_hose_6 physical_fire_hose_8 smart_rope_connector rope_connector_master
rope_hook rope_latch copycat_door copycat_multidirectional_trapdoor
copycat_short_sliding_door copycat_short_folding_door'''.split())


class Unsupported(ValueError):
    pass


@dataclass
class Tag:
    kind: int
    value: object


def read_nbt(data):
    stream = io.BytesIO(data)

    def read(n):
        result = stream.read(n)
        if len(result) != n:
            raise Unsupported('Truncated NBT')
        return result

    def string():
        return read(struct.unpack('>H', read(2))[0])

    def payload(kind, depth=0):
        if depth > 256:
            raise Unsupported('NBT nesting exceeds supported depth')
        widths = {1: 1, 2: 2, 3: 4, 4: 8, 5: 4, 6: 8}
        if kind in widths:
            return Tag(kind, read(widths[kind]))
        if kind == 8:
            return Tag(kind, string())
        if kind == 10:
            result = {}
            while True:
                child = read(1)[0]
                if child == 0:
                    break
                name = string()
                if name in result:
                    raise Unsupported('Duplicate NBT compound key')
                result[name] = payload(child, depth + 1)
            return Tag(kind, result)
        if kind == 9:
            subtype = read(1)[0]
            count = struct.unpack('>i', read(4))[0]
            if count < 0 or count > len(data) or (subtype == 0 and count):
                raise Unsupported('Invalid NBT list length')
            return Tag(kind, (subtype, [payload(subtype, depth + 1) for _ in range(count)]))
        if kind in (7, 11, 12):
            count = struct.unpack('>i', read(4))[0]
            if count < 0:
                raise Unsupported('Invalid NBT array length')
            return Tag(kind, read(count * {7: 1, 11: 4, 12: 8}[kind]))
        raise Unsupported(f'Unsupported NBT type {kind}')

    kind = read(1)[0]
    if kind != 10:
        raise Unsupported('Root must be a named compound')
    name = string()
    root = payload(kind)
    if stream.read(1):
        raise Unsupported('Trailing NBT bytes')
    return name, root


def write_nbt(name, root):
    def string(value):
        return struct.pack('>H', len(value)) + value

    def payload(tag):
        k, v = tag.kind, tag.value
        if k in (1, 2, 3, 4, 5, 6):
            return v
        if k == 8:
            return string(v)
        if k == 10:
            return b''.join(bytes([t.kind]) + string(n) + payload(t) for n, t in v.items()) + b'\0'
        if k == 9:
            return bytes([v[0]]) + struct.pack('>i', len(v[1])) + b''.join(payload(t) for t in v[1])
        if k in (7, 11, 12):
            return struct.pack('>i', len(v) // {7: 1, 11: 4, 12: 8}[k]) + v
        raise Unsupported(f'Unsupported NBT type {k}')
    return bytes([root.kind]) + string(name) + payload(root)


def compound(tag):
    if tag.kind != 10:
        raise Unsupported('Expected compound')
    return tag.value


def listing(tag):
    if tag.kind != 9:
        raise Unsupported('Expected list')
    return tag.value[1]


def integer(tag):
    if tag.kind not in (1, 2, 3, 4):
        raise Unsupported('Expected integer')
    return int.from_bytes(tag.value, 'big', signed=True)


def number(value, kind=3):
    return Tag(kind, value.to_bytes({1: 1, 2: 2, 3: 4, 4: 8}[kind], 'big', signed=True))


def physical(be):
    endpoint = be.get(b'PhysicalEndpoint')
    if endpoint is None:
        return False
    if endpoint.kind != 11 or len(endpoint.value) != 16:
        raise Unsupported('Malformed physical bracket endpoint UUID')
    return True


def unpack_indices(palette, data, count):
    if len(palette) == 1:
        return [0] * count
    bits = max(4, (len(palette) - 1).bit_length())
    per_long = 64 // bits
    if data is None or data.kind != 12 or len(data.value) != ((count + per_long - 1) // per_long) * 8:
        raise Unsupported('Unsupported block-state storage layout')
    words = struct.unpack('>' + 'Q' * (len(data.value) // 8), data.value)
    indices = [(words[i // per_long] >> ((i % per_long) * bits)) & ((1 << bits) - 1) for i in range(count)]
    if any(i >= len(palette) for i in indices):
        raise Unsupported('Block-state palette index out of range')
    return indices


def pack_indices(palette, indices):
    bits = max(4, (len(palette) - 1).bit_length())
    per_long = 64 // bits
    words = [0] * ((len(indices) + per_long - 1) // per_long)
    for i, value in enumerate(indices):
        words[i // per_long] |= value << ((i % per_long) * bits)
    return Tag(12, struct.pack('>' + 'Q' * len(words), *words))


def transform_region(raw, transform):
    """Anvil region conversion in memory. External chunks/LZ4 are rejected."""
    if not raw:
        return raw  # RegionFile may be empty before its first write.
    if len(raw) < 8192:
        raise Unsupported('Invalid region length')
    changed = False
    headers = bytearray(raw[:8192])
    result = bytearray(b'\0' * 8192)
    occupied = set((0, 1))
    for index in range(1024):
        location = int.from_bytes(raw[index * 4:index * 4 + 4], 'big')
        offset, sectors = location >> 8, location & 255
        if location == 0:
            continue
        if offset < 2 or sectors == 0 or offset * 4096 + 5 > len(raw):
            raise Unsupported('Region chunk offset out of bounds')
        allocated = set(range(offset, offset + sectors))
        if occupied & allocated:
            raise Unsupported('Overlapping region sectors')
        occupied |= allocated
        start = offset * 4096
        size = int.from_bytes(raw[start:start + 4], 'big')
        if size < 1 or size + 4 > sectors * 4096 or start + size + 4 > len(raw):
            raise Unsupported('Region chunk size out of bounds')
        compression = raw[start + 4]
        payload = raw[start + 5:start + 4 + size]
        if compression == 1:
            decoded = gzip.decompress(payload)
        elif compression == 2:
            decoded = zlib.decompress(payload)
        elif compression == 3:
            decoded = payload
        else:
            raise Unsupported(f'Region compression {compression}: external/LZ4 chunks need an adapter')
        converted = transform(decoded)
        if converted == decoded:
            encoded = raw[start:start + 4 + size]
        else:
            changed = True
            compressed = zlib.compress(converted)
            encoded = struct.pack('>I', len(compressed) + 1) + b'\x02' + compressed
        new_sectors = (len(encoded) + 4095) // 4096
        if new_sectors > 255:
            raise Unsupported('Converted chunk requires external storage')
        headers[index * 4:index * 4 + 4] = ((len(result) // 4096 << 8) | new_sectors).to_bytes(4, 'big')
        result.extend(encoded)
        result.extend(b'\0' * (new_sectors * 4096 - len(encoded)))
    result[:8192] = headers
    return bytes(result) if changed else raw
