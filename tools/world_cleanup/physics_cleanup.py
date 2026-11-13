"""Remove identified legacy constraints and ropes, not unrelated Sable bodies.

Collect all source documents, resolve all documents, then clean. Coordinates are
dimension-scoped world/plot coordinates; Sable plot positions are already global.
No native physics snapshot is present in the inspected Sable 2.0.4 serializer.
"""
from copy import deepcopy
from dataclasses import dataclass, field
import struct
import uuid
from split_nbt import Tag, read_nbt, write_nbt, integer, MOVED, unpack_indices, pack_indices, Unsupported

OLD = b'createfirefightingadd:'
FEATURES = {OLD + b'ball_coupling', OLD + b'fluid_coupling'}


@dataclass
class PhysicsContext:
    removed_positions: set = field(default_factory=set)
    removed_rope_ids: set = field(default_factory=set)
    strands: list = field(default_factory=list)
    removed_bodies: set = field(default_factory=set)
    affected_bodies: set = field(default_factory=set)
    constraint_records: list = field(default_factory=list)
    provider_positions: set = field(default_factory=set)
    found_providers: set = field(default_factory=set)
    rope_providers: list = field(default_factory=list)


def compounds(root):
    if root.kind == 10:
        yield root.value
        for child in root.value.values():
            yield from compounds(child)
    elif root.kind == 9:
        for child in root.value[1]:
            yield from compounds(child)


def uuid_bytes(tag):
    if tag is None:
        return None
    if tag.kind == 11 and len(tag.value) == 16:
        return tag.value
    if tag.kind == 8:
        try:
            return uuid.UUID(tag.value.decode('ascii')).bytes
        except (ValueError, UnicodeError):
            return None
    return None


def rope_ids(data):
    value = uuid_bytes(data.get(b'HasRopeAttached'))
    if value is not None:
        yield value
    strand = data.get(b'Strand')
    if strand is not None and strand.kind == 10:
        value = uuid_bytes(strand.value.get(b'uuid'))
        if value is not None:
            yield value


def collect_physics_tree(root, context, dimension=b'minecraft:overworld', all_legacy=False, source_name=None):
    """Call on original NBT before deleting old block entities."""
    if isinstance(dimension, str):
        dimension = dimension.encode()
    if source_name in ('createfirefightingadd_rope_hook_attachments.dat',
                       'createfirefightingadd_rope_hook_attachments.dat_old'):
        data = root.value.get(b'data') if root.kind == 10 else None
        ropes = data.value.get(b'Ropes') if data is not None and data.kind == 10 else None
        if ropes is not None and ropes.kind == 9:
            for rope in ropes.value[1]:
                value = uuid_bytes(rope)
                if value is not None:
                    context.removed_rope_ids.add(value)
    for data in compounds(root):
        records = data.get(b'records')
        if records is not None and records.kind == 9:
            context.constraint_records.extend(records.value[1])
        strand = data.get(b'Strand')
        if strand is not None and strand.kind == 10:
            if all(k in data for k in (b'x',b'y',b'z')):
                context.rope_providers.append((tuple(rope_ids(data)),
                    (dimension,*(integer(data[k]) for k in (b'x',b'y',b'z')))))
            attachments = strand.value.get(b'attachments')
            if attachments is not None and attachments.kind == 9:
                positions = []
                bodies = []
                for entry in attachments.value[1]:
                    if entry.kind == 10:
                        pos = attachment_position(entry.value.get(b'blockAttachment'))
                        if pos is not None:
                            positions.append((dimension, *pos))
                        body = uuid_bytes(entry.value.get(b'subLevelID'))
                        if body is not None:
                            bodies.append((dimension, body))
                context.strands.append((tuple(rope_ids(data)), tuple(positions), tuple(bodies)))
        identity = data.get(b'id')
        if identity is None or identity.kind != 8 or not identity.value.startswith(OLD):
            continue
        if not all_legacy and identity.value[len(OLD):] not in MOVED | {b'hose_bracket'}:
            continue
        if all(data.get(k) is not None and data[k].kind in (1, 2, 3, 4) for k in (b'x', b'y', b'z')):
            context.removed_positions.add((dimension, *(integer(data[k]) for k in (b'x', b'y', b'z'))))
        for nested in compounds(Tag(10, data)):
            context.removed_rope_ids.update(rope_ids(nested))
        for key in (b'OwnedRopes', b'ExternalRopes'):
            values = data.get(key)
            if values is not None and values.kind == 9:
                for item in values.value[1]:
                    if item.kind == 10:
                        value = uuid_bytes(item.value.get(b'Id'))
                        if value is not None:
                            context.removed_rope_ids.add(value)


def resolve_context(context):
    for record in context.constraint_records:
        if own_constraint(record) or removed_spec_body(record, context):
            key = record.value.get(b'key') if record.kind == 10 else None
            owner = key.value.get(b'owner') if key is not None and key.kind == 10 else None
            if owner is not None and owner.kind == 10 and owner.value.get(b'type') == Tag(8,b'synaxis:block'):
                data = owner.value
                packed = integer(data[b'pos'])
                def signed(v,bits): return v-(1<<bits) if v & (1<<(bits-1)) else v
                context.provider_positions.add((data[b'dimension'].value,
                    signed((packed>>38)&0x3ffffff,26), signed(packed&0xfff,12),
                    signed((packed>>12)&0x3ffffff,26)))
    for ids, positions, bodies in context.strands:
        if (any(pos in context.removed_positions for pos in positions)
                or any(body in context.removed_bodies | context.affected_bodies for body in bodies)):
            context.removed_rope_ids.update(ids)
    for ids,position in context.rope_providers:
        if any(identity in context.removed_rope_ids for identity in ids):
            context.provider_positions.add(position)


def plot_has_blocks(plot):
    chunks = plot.value.get(b'chunks')
    if chunks is None or chunks.kind not in (9, 10):
        raise Unsupported('Unrecognized Sable plot chunks while checking removed bodies')
    for chunk in (chunks.value.values() if chunks.kind == 10 else chunks.value[1]):
        sections = chunk.value.get(b'sections') if chunk.kind == 10 else None
        if sections is None or sections.kind not in (9, 10):
            raise Unsupported('Unrecognized Sable plot sections while checking removed bodies')
        for section in (sections.value.values() if sections.kind == 10 else sections.value[1]):
            states = section.value.get(b'block_states') if section.kind == 10 else None
            if states is None:
                continue
            if states.kind != 10 or b'palette' not in states.value or states.value[b'palette'].kind != 9:
                raise Unsupported('Unrecognized Sable block states')
            palette = states.value[b'palette'].value[1]
            indices = unpack_indices(palette, states.value.get(b'data'), 4096)
            for index in set(indices):
                entry = palette[index]
                name = entry.value.get(b'Name') if entry.kind == 10 else None
                if name is None or name.kind != 8:
                    raise Unsupported('Unrecognized Sable palette state')
                if name.value not in (b'minecraft:air', b'minecraft:cave_air', b'minecraft:void_air'):
                    return True
    return False


def collect_empty_bodies(original, cleaned, context, dimension=b'minecraft:overworld'):
    if isinstance(dimension, str):
        dimension = dimension.encode()
    before = {}
    for data in compounds(original):
        body = uuid_bytes(data.get(b'uuid'))
        plot = data.get(b'plot')
        if body is not None and plot is not None and plot.kind == 10:
            before[body] = plot
    for data in compounds(cleaned):
        body = uuid_bytes(data.get(b'uuid'))
        plot = data.get(b'plot')
        if body in before and plot is not None and plot.kind == 10 and plot != before[body]:
            def state_names(tree):
                result = set()
                for obj in compounds(tree):
                    states = obj.get(b'block_states')
                    if states is not None and states.kind == 10:
                        for entry in states.value[b'palette'].value[1]:
                            result.add(entry.value[b'Name'].value)
                return result
            if state_names(plot) != state_names(before[body]):
                context.affected_bodies.add((dimension, body))
            if not plot_has_blocks(plot) and plot_has_blocks(before[body]):
                context.removed_bodies.add((dimension, body))


def attachment_position(tag):
    # BlockPos.CODEC uses Codec.INT_STREAM: NbtOps encodes as TAG_Int_Array.
    if tag is not None and tag.kind == 11 and len(tag.value) == 12:
        return struct.unpack('>iii', tag.value)
    if tag is not None and tag.kind == 9 and len(tag.value[1]) == 3:
        if all(t.kind in (1, 2, 3, 4) for t in tag.value[1]):
            return tuple(integer(t) for t in tag.value[1])
    return None


def resolve_physics_tree(root, context, dimension=b'minecraft:overworld'):
    """Second pass: find native ropes whose attachments touch removed blocks."""
    if isinstance(dimension, str):
        dimension = dimension.encode()
    for data in compounds(root):
        strand = data.get(b'Strand')
        if strand is None or strand.kind != 10:
            continue
        attachments = strand.value.get(b'attachments')
        if attachments is None or attachments.kind != 9:
            continue
        for entry in attachments.value[1]:
            if entry.kind != 10:
                continue
            pos = attachment_position(entry.value.get(b'blockAttachment'))
            if pos is not None and (dimension, *pos) in context.removed_positions:
                context.removed_rope_ids.update(rope_ids(data))
                break


def own_constraint(record):
    if record.kind != 10:
        return False
    key = record.value.get(b'key')
    if key is None or key.kind != 10:
        return False
    owner = key.value.get(b'owner')
    if owner is None or owner.kind != 10:
        return False
    data = owner.value
    return (data.get(b'type') == Tag(8, b'synaxis:system')
            and data.get(b'system', Tag(8, b'')).value in FEATURES)


def removed_body(body, context):
    if body is None or body.kind != 10:
        return False
    dimension = body.value.get(b'dimension')
    identity = uuid_bytes(body.value.get(b'id', body.value.get(b'uuid')))
    return (dimension is not None and dimension.kind == 8
            and (dimension.value, identity) in context.removed_bodies | context.affected_bodies)


def removed_spec_body(record, context):
    if record.kind != 10:
        return False
    spec = record.value.get(b'spec')
    return spec is not None and spec.kind == 10 and any(
        removed_body(spec.value.get(key), context) for key in (b'body_a', b'body_b'))


def clean_physics_tree(root, context, dimension=b'minecraft:overworld'):
    if isinstance(dimension, str):
        dimension = dimension.encode()
    result = deepcopy(root)
    for data in compounds(result):
        records = data.get(b'records')
        if records is not None and records.kind == 9:
            records.value = (records.value[0], [r for r in records.value[1]
                if not own_constraint(r) and not removed_spec_body(r, context)])
        entries = data.get(b'entries')
        if entries is not None and entries.kind == 9:
            entries.value = (entries.value[0], [entry for entry in entries.value[1]
                if not (entry.kind == 10 and b'weld_id' in entry.value and any(
                    removed_body(entry.value.get(key), context) for key in (b'body_a', b'body_b')))])
        dependencies = data.get(b'loading_dependencies')
        if dependencies is not None and dependencies.kind == 9 and b'uuid' in data:
            dependencies.value = (dependencies.value[0], [entry for entry in dependencies.value[1]
                if (dimension, uuid_bytes(entry)) not in context.removed_bodies])
        tickets = data.get(b'tickets')
        if tickets is not None and tickets.kind == 9:
            tickets.value = (tickets.value[0], [entry for entry in tickets.value[1]
                if not (entry.kind == 10 and b'entries' in entry.value
                    and (dimension, uuid_bytes(entry.value.get(b'uuid'))) in context.removed_bodies)])
        if any(value in context.removed_rope_ids for value in rope_ids(data)):
            for key in (b'OwnStrand', b'HasRopeAttached', b'Strand'):
                data.pop(key, None)
    remove_provider_blocks(result, context, dimension)
    return result


def remove_provider_blocks(root, context, dimension):
    """Remove exact constraint-provider blocks; never discard a plot or chunk.

    Vanilla chunks carry absolute coordinates. Sable chunk NBT carries global
    BE coordinates but indexes sections from the dimension's minimum section.
    For custom dimensions, reject ambiguous section indexing rather than erase
    the surrounding structure.
    """
    targets = {pos[1:] for pos in context.provider_positions if pos[0] == dimension}
    if not targets: return
    for obj in compounds(root):
        sections = obj.get(b'sections')
        if sections is None or sections.kind not in (9,10): continue
        entities = obj.get(b'block_entities')
        entity_values = entities.value[1] if entities is not None and entities.kind == 9 else []
        be_positions = set()
        for entry in entity_values:
            data = entry.value
            if all(k in data for k in (b'x',b'y',b'z')):
                be_positions.add(tuple(integer(data[k]) for k in (b'x',b'y',b'z')))
        if b'xPos' in obj and b'zPos' in obj:
            cx,cz=integer(obj[b'xPos']),integer(obj[b'zPos'])
            local = {pos for pos in targets if pos[0]>>4==cx and pos[2]>>4==cz}
        else:
            local = targets & be_positions
        if not local: continue
        minimum = {b'minecraft:overworld':-4,b'minecraft:the_nether':0,b'minecraft:the_end':0}.get(dimension)
        if sections.kind == 10:
            if minimum is None:
                raise Unsupported('Exact Sable provider deletion requires the custom dimension minimum section')
            section_entries = [(int(key)+minimum, value) for key,value in sections.value.items()]
        else:
            section_entries = [(integer(value.value[b'Y']),value) for value in sections.value[1]]
        for y,section in section_entries:
            chosen = {pos for pos in local if pos[1]>>4==y}
            if not chosen: continue
            states = section.value.get(b'block_states')
            if states is None: continue
            palette = states.value[b'palette'].value[1]
            indices = unpack_indices(palette,states.value.get(b'data'),4096)
            air = next((i for i,t in enumerate(palette) if t.value.get(b'Name')==Tag(8,b'minecraft:air')),None)
            if air is None:
                air=len(palette);palette.append(Tag(10,{b'Name':Tag(8,b'minecraft:air')}))
            for x,by,z in chosen:
                indices[((by&15)<<8)|((z&15)<<4)|(x&15)] = air
                context.found_providers.add((dimension,x,by,z))
            states.value[b'data']=pack_indices(palette,indices)
            section.value.pop(b'BlockLight',None)
            section.value.pop(b'SkyLight',None)
        for key in (b'block_entities',b'block_ticks',b'fluid_ticks'):
            entries=obj.get(key)
            if entries is not None and entries.kind==9:
                entries.value=(entries.value[0],[entry for entry in entries.value[1]
                    if not (all(k in entry.value for k in (b'x',b'y',b'z')) and
                    tuple(integer(entry.value[k]) for k in (b'x',b'y',b'z')) in local)])
        for key in (b'Heightmaps',b'heightmaps',b'isLightOn'):obj.pop(key,None)


def clean_physics_document(raw, context=None, dimension=b'minecraft:overworld'):
    name, root = read_nbt(raw)
    if context is None:
        context = PhysicsContext()
        collect_physics_tree(root, context, dimension)
        resolve_context(context)
    result = clean_physics_tree(root, context, dimension)
    return raw if result == root else write_nbt(name, result)
