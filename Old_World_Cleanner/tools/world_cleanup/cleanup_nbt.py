"""Remove legacy content in decoded Minecraft/Create/Sable NBT.

Palette slots stay in place: turning a slot into air never changes packed indices.
No namespace replacement is performed on user strings or constraint ownership.
"""
from split_nbt import (Tag, OLD, MOVED, BRACKET, Unsupported, read_nbt,
                       write_nbt, compound, listing, integer, number)


def target_id(value, all_legacy=False):
    return isinstance(value, bytes) and value.startswith(OLD) and (
        all_legacy or value == BRACKET or value[len(OLD):] in MOVED)


def clean_document(raw, all_legacy=False):
    name, root = read_nbt(raw)
    clean_tree(root, all_legacy)
    return write_nbt(name, root)


def clean_tree(root, all_legacy=False):
    changed_palettes = 0
    def target(value):
        return target_id(value, all_legacy)

    def ident(obj, key=b'id'):
        t = obj.get(key)
        return t.value if t is not None and t.kind == 8 else None

    def state_removed(state):
        return state.kind == 10 and target(ident(state.value, b'Name'))

    def air(state):
        state.value = {b'Name': Tag(8, b'minecraft:air')}

    def clean_palette(entries):
        nonlocal changed_palettes
        removed = set()
        for i, state in enumerate(entries):
            if state_removed(state):
                removed.add(i)
                air(state)
        changed_palettes += bool(removed)
        return removed

    def indexed_blocks(obj, palette_key, blocks_key, index_key, data_key):
        palette = listing(obj[palette_key])
        removed = clean_palette(palette)
        for block in listing(obj[blocks_key]):
            entry = compound(block)
            index = integer(entry[index_key])
            if not 0 <= index < len(palette):
                raise Unsupported('Block palette index out of range')
            if index in removed:
                entry.pop(data_key, None)

    def visit(tag):
        changes_before = changed_palettes
        if tag.kind == 9:
            for child in listing(tag):
                visit(child)
            return
        if tag.kind != 10:
            return
        obj = tag.value
        # ItemStack codecs use count (1.21) or Count (legacy component payloads).
        if target(ident(obj)) and (b'count' in obj or b'Count' in obj or
                set(obj).issubset({b'id', b'components', b'tag', b'Slot'})):
            tag.value = {}
            return
        # Modern Minecraft chunks, including Sable's nested plot chunks.
        if b'sections' in obj:
            sections_tag = obj[b'sections']
            sections = (list(sections_tag.value.values()) if sections_tag.kind == 10
                        else listing(sections_tag))
            removed_any = False
            for section in sections:
                states = compound(section).get(b'block_states')
                if states is not None:
                    removed_any |= bool(clean_palette(listing(compound(states)[b'palette'])))
            if removed_any:
                # Vanilla regenerates these after loading; stale solid/light data
                # must not survive turning whole palette entries into air.
                obj.pop(b'Heightmaps', None)
                obj.pop(b'heightmaps', None)
                obj.pop(b'isLightOn', None)
                for section in sections:
                    compound(section).pop(b'BlockLight', None)
                    compound(section).pop(b'SkyLight', None)
        if b'palette' in obj and b'blocks' in obj:
            indexed_blocks(obj, b'palette', b'blocks', b'state', b'nbt')
        if b'palettes' in obj and b'blocks' in obj:
            palettes = listing(obj[b'palettes'])
            removed = set()
            for palette in palettes:
                removed |= clean_palette(listing(palette))
            # A BE is shared across alternative palettes. Clear its NBT if any
            # variant is erased, avoiding an old BE on the air variant.
            for block in listing(obj[b'blocks']):
                entry = compound(block)
                if integer(entry[b'state']) in removed:
                    entry.pop(b'nbt', None)
        if b'Palette' in obj and b'BlockList' in obj:
            indexed_blocks(obj, b'Palette', b'BlockList', b'State', b'Data')
        if b'block_palette' in obj and b'blocks' in obj:
            indexed_blocks(obj, b'block_palette', b'blocks', b'palette_id', b'block_entity_data_id')
            # This table is indexed, not a removable list. Keep empty slots so
            # surviving blocks continue referencing the same entity data.
            if b'block_entities' in obj:
                for entry in listing(obj[b'block_entities']):
                    if target(ident(compound(entry))):
                        entry.value = {}
        # Create's older inline StructureBlockInfo encoding.
        if b'Block' in obj and state_removed(obj[b'Block']):
            air(obj[b'Block'])
            obj.pop(b'Data', None)
        # Falling block entities serialize a block state instead of palette.
        if b'BlockState' in obj and state_removed(obj[b'BlockState']):
            air(obj[b'BlockState'])
            obj.pop(b'TileEntityData', None)
        for key in (b'block_entities', b'TileEntities'):
            if key in obj and b'block_palette' not in obj:
                values = listing(obj[key])
                values[:] = [x for x in values if not target(ident(compound(x)))]
        for key in (b'block_ticks', b'fluid_ticks', b'TileTicks', b'LiquidTicks'):
            if key in obj:
                values = listing(obj[key])
                values[:] = [x for x in values if not target(ident(compound(x), b'i'))]
        for key in (b'entities', b'Entities', b'Passengers'):
            if key in obj and obj[key].kind == 9:
                values = listing(obj[key])
                def erase_entity(entry):
                    entity = compound(entry)
                    if target(ident(entity)):
                        return True
                    # Structure templates wrap their entity NBT in nbt.
                    if b'nbt' in entity:
                        entity = compound(entity[b'nbt'])
                    if ident(entity) == b'minecraft:item' and b'Item' in entity:
                        return target(ident(compound(entity[b'Item'])))
                    return b'BlockState' in entity and state_removed(entity[b'BlockState'])
                values[:] = [x for x in values if not erase_entity(x)]
        components = obj.get(b'components')
        if components is not None and components.kind == 10:
            # Selection components refer to deleted endpoints, even on retained
            # Create items. Other components and user/custom data remain intact.
            for key in list(components.value):
                if key == OLD + b'smart_rope_selection' or (all_legacy and key.startswith(OLD)):
                    del components.value[key]
        # Sparse inventories and component container lists reject empty stack
        # codecs. Remove the entry, preserving its surviving neighbours' slots.
        # Equipment arrays are positional and deliberately remain empty slots.
        for key in (b'Inventory', b'Items', b'EnderItems', b'items',
                    b'minecraft:container', b'minecraft:bundle_contents',
                    b'minecraft:charged_projectiles'):
            entries = obj.get(key)
            if entries is None or entries.kind != 9:
                continue
            def removed_stack(entry):
                if entry.kind != 10:
                    return False
                data = entry.value
                nested = data.get(b'item')
                if nested is not None and nested.kind == 10:
                    data = nested.value
                return target(ident(data))
            values = listing(entries)
            values[:] = [entry for entry in values if not removed_stack(entry)]
        for child in list(obj.values()):
            visit(child)
        if b'chunks' in obj and b'plot_x' in obj and changed_palettes != changes_before:
            obj[b'isLightOn'] = number(0, 1)
    visit(root)
    return root
