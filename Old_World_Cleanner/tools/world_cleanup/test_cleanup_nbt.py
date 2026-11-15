import unittest
from cleanup_nbt import clean_document
from split_nbt import Tag, OLD, number, read_nbt, write_nbt


def c(**kw): return Tag(10, {k.encode(): v for k, v in kw.items()})
def s(x): return Tag(8, x)
def seq(*xs): return Tag(9, (10, list(xs)))
def clean(x, all_legacy=False):
    raw = clean_document(write_nbt(b'root', x), all_legacy)
    assert clean_document(raw, all_legacy) == raw
    return read_nbt(raw)[1]


class CleanupTests(unittest.TestCase):
    def test_chunk_palette_and_entities_ticks_light(self):
        packed = Tag(12, bytes(2048))
        data = c(sections=seq(c(Y=number(0, 1), block_states=c(
            palette=seq(c(Name=s(OLD+b'fluid_coupling'), Properties=c(facing=s(b'east'))),
                        c(Name=s(b'minecraft:stone'))), data=packed), BlockLight=Tag(7, b'abc'))),
            block_entities=seq(c(id=s(OLD+b'fluid_coupling')), c(id=s(b'other:machine'))),
            block_ticks=seq(c(i=s(OLD+b'fluid_coupling')), c(i=s(b'minecraft:water'))),
            Heightmaps=c(MOTION_BLOCKING=Tag(12, bytes(8))), isLightOn=number(1, 1))
        out = clean(data).value
        section = out[b'sections'].value[1][0].value
        states = section[b'block_states'].value
        self.assertEqual(states[b'palette'].value[1][0], c(Name=s(b'minecraft:air')))
        self.assertEqual(states[b'data'], packed)
        self.assertEqual(len(out[b'block_entities'].value[1]), 1)
        self.assertEqual(len(out[b'block_ticks'].value[1]), 1)
        self.assertNotIn(b'Heightmaps', out)
        self.assertNotIn(b'BlockLight', section)

    def test_indexed_structure_and_blueprint_keep_indices(self):
        for pk,bk,ik,dk in [('palette','blocks','state','nbt'),
                            ('Palette','BlockList','State','Data'),
                            ('block_palette','blocks','palette_id','block_entity_data_id')]:
            root = c(**{pk:seq(c(Name=s(OLD+b'hose_bracket')), c(Name=s(b'minecraft:stone'))),
                        bk:seq(c(**{ik:number(0), dk:number(0) if dk.endswith('_id') else c(id=s(OLD+b'hose_bracket'))}),
                               c(**{ik:number(1), dk:number(1) if dk.endswith('_id') else c(id=s(b'other:machine'))}))})
            if pk == 'block_palette':
                root.value[b'block_entities'] = seq(c(id=s(OLD+b'hose_bracket')), c(id=s(b'other:machine')))
            out = clean(root).value
            self.assertNotIn(dk.encode(), out[bk.encode()].value[1][0].value)
            self.assertIn(dk.encode(), out[bk.encode()].value[1][1].value)
            if pk == 'block_palette':
                self.assertEqual(out[b'block_entities'].value[1][0], c())
                self.assertEqual(len(out[b'block_entities'].value[1]), 2)

    def test_items_nested_components_and_user_strings(self):
        item = c(id=s(b'minecraft:chest'), count=number(1), components=c())
        item.value[b'components'].value[b'minecraft:container'] = seq(
            c(slot=number(0), item=c(id=s(OLD+b'stress_coupling'), count=number(1))))
        root = c(Inventory=seq(item, c(id=s(OLD+b'fire_hose'), count=number(1))),
                 user=c(Name=s(OLD+b'rocker'), message=s(OLD+b'fluid_coupling')))
        out = clean(root).value
        embedded = out[b'Inventory'].value[1][0].value[b'components'].value[b'minecraft:container']
        self.assertEqual(embedded, seq())
        self.assertEqual(out[b'user'], root.value[b'user'])
        self.assertEqual(out[b'Inventory'].value[1][1], root.value[b'Inventory'].value[1][1])
        self.assertEqual(len(clean(root, True).value[b'Inventory'].value[1]), 1)

    def test_inline_and_nested_sable_plot(self):
        inline = c(Block=c(Name=s(OLD+b'rocker')), Data=c(id=s(OLD+b'rocker')))
        out = clean(c(plot=c(chunk=c(Blocks=seq(inline))))).value
        block = out[b'plot'].value[b'chunk'].value[b'Blocks'].value[1][0].value
        self.assertEqual(block[b'Block'], c(Name=s(b'minecraft:air')))
        self.assertNotIn(b'Data', block)

    def test_entity_and_fixed_inventory_slots(self):
        root = c(Entities=seq(c(id=s(b'minecraft:item'), Item=c(id=s(OLD+b'rocker'), count=number(1))),
                             c(id=s(b'minecraft:zombie'), ArmorItems=seq(c(id=s(OLD+b'rocker'), Count=number(1,1)), c()))))
        out = clean(root).value[b'Entities'].value[1]
        self.assertEqual(len(out), 1)
        self.assertEqual(out[0].value[b'ArmorItems'], seq(c(), c()))

    def test_empty_document_unchanged(self):
        raw = write_nbt(b'', c(id=s(b'other:thing'), Name=s(OLD+b'rocker')))
        self.assertEqual(clean_document(raw), raw)

    def test_default_count_item_codec(self):
        root = c(Items=seq(c(id=s(OLD+b'rocker'), Slot=number(0, 1))))
        self.assertEqual(clean(root).value[b'Items'], seq())

    def test_surviving_inventory_and_component_slots_preserved(self):
        root = c(Inventory=seq(c(id=s(OLD+b'rocker'), Slot=number(0,1)),
                               c(id=s(b'minecraft:diamond'), Slot=number(7,1))),
                 components=c())
        root.value[b'components'].value[b'minecraft:container'] = seq(
            c(slot=number(1), item=c(id=s(OLD+b'rocker'))),
            c(slot=number(9), item=c(id=s(b'minecraft:diamond'))))
        out = clean(root).value
        self.assertEqual(out[b'Inventory'], seq(c(id=s(b'minecraft:diamond'), Slot=number(7,1))))
        self.assertEqual(out[b'components'].value[b'minecraft:container'],
                         seq(c(slot=number(9), item=c(id=s(b'minecraft:diamond')))))

    def test_actual_sable_map_shaped_sections(self):
        section=c(block_states=c(palette=seq(c(Name=s(OLD+b'rocker')))), SkyLight=Tag(7, bytes(2048)))
        chunk=c(sections=Tag(10,{b'12':section}), block_entities=seq(c(id=s(OLD+b'rocker'))), heightmaps=c())
        root=c(plot=c(plot_x=number(64), isLightOn=number(1,1), chunks=Tag(10,{b'274877907008':chunk})))
        out=clean(root).value[b'plot'].value
        self.assertEqual(out[b'isLightOn'], number(0,1))
        chunk=out[b'chunks'].value[b'274877907008'].value
        self.assertEqual(chunk[b'block_entities'], seq())
        self.assertEqual(chunk[b'sections'].value[b'12'].value[b'block_states'].value[b'palette'],seq(c(Name=s(b'minecraft:air'))))


if __name__ == '__main__': unittest.main()
