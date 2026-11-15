import unittest
import struct
import uuid
from split_nbt import Tag, number, write_nbt, read_nbt
from physics_cleanup import (PhysicsContext, collect_physics_tree, resolve_context,
                             clean_physics_tree, clean_physics_document, collect_empty_bodies)

def comp(**values):
    return Tag(10, {k.encode(): v for k, v in values.items()})

def text(s):
    return Tag(8, s.encode())

def ls(*items):
    return Tag(9, (10, list(items)))

class PhysicsCleanupTest(unittest.TestCase):
    def test_newly_empty_body_removes_only_its_references(self):
        removed, retained, rope = uuid.uuid4(), uuid.uuid4(), uuid.uuid4()
        def plot(block):
            section=comp(block_states=comp(palette=ls(comp(Name=text(block)))))
            return comp(uuid=Tag(11,removed.bytes),plot=comp(chunks=Tag(10,{
                b'0':comp(sections=Tag(10,{b'0':section}))})))
        before, after = plot('createfirefightingadd:fluid_coupling'), plot('minecraft:air')
        ctx = PhysicsContext()
        collect_empty_bodies(before,after,ctx)
        self.assertIn((b'minecraft:overworld',removed.bytes),ctx.removed_bodies)
        ref = comp(dimension=text('minecraft:overworld'),uuid=Tag(11,removed.bytes))
        other = comp(dimension=text('minecraft:overworld'),uuid=Tag(11,retained.bytes))
        dependent=comp(weld_id=Tag(11,uuid.uuid4().bytes),body_a=ref,body_b=other)
        unrelated=comp(weld_id=Tag(11,uuid.uuid4().bytes),body_a=other,body_b=other)
        root=comp(entries=ls(dependent,unrelated),records=ls(comp(spec=comp(body_a=ref,body_b=other))))
        rope_owner=comp(Strand=comp(uuid=text(str(rope)), attachments=ls(comp(subLevelID=text(str(removed))))))
        collect_physics_tree(rope_owner,ctx)
        resolve_context(ctx)
        cleaned=clean_physics_tree(root,ctx)
        self.assertEqual(cleaned.value[b'entries'],ls(unrelated))
        self.assertEqual(cleaned.value[b'records'],ls())
        self.assertNotIn(b'Strand',clean_physics_tree(rope_owner,ctx).value)
        metadata=comp(uuid=Tag(11,retained.bytes),loading_dependencies=Tag(9,(11,[Tag(11,removed.bytes),Tag(11,retained.bytes)])),
            tickets=ls(comp(uuid=Tag(11,removed.bytes),entries=ls()),comp(uuid=Tag(11,retained.bytes),entries=ls())))
        cleaned_metadata=clean_physics_tree(metadata,ctx)
        self.assertEqual(len(cleaned_metadata.value[b'loading_dependencies'].value[1]),1)
        self.assertEqual(len(cleaned_metadata.value[b'tickets'].value[1]),1)
        existing_empty=PhysicsContext()
        collect_empty_bodies(after,after,existing_empty)
        self.assertFalse(existing_empty.removed_bodies)

    def test_hook_saveddata_uses_explicit_file_identity(self):
        strand_id = uuid.uuid4()
        root = comp(data=comp(Ropes=Tag(9, (8, [text(str(strand_id))]))))
        ctx = PhysicsContext()
        collect_physics_tree(root, ctx, source_name='other_mod.dat')
        self.assertFalse(ctx.removed_rope_ids)
        collect_physics_tree(root, ctx, source_name='createfirefightingadd_rope_hook_attachments.dat')
        self.assertEqual(ctx.removed_rope_ids, {strand_id.bytes})

    def test_only_owned_constraints(self):
        owned = comp(key=comp(owner=comp(type=text('synaxis:system'),
                     system=text('createfirefightingadd:ball_coupling'))))
        other = comp(key=comp(owner=comp(type=text('synaxis:system'), system=text('other:coupling'))))
        weld = comp(weld_id=Tag(11, uuid.uuid4().bytes))
        root = comp(data=comp(records=ls(owned, other), entries=ls(weld)))
        cleaned = clean_physics_tree(root, PhysicsContext())
        self.assertEqual(cleaned.value[b'data'].value[b'records'], ls(other))
        self.assertEqual(cleaned.value[b'data'].value[b'entries'], ls(weld))
        self.assertEqual(len(root.value[b'data'].value[b'records'].value[1]), 2)

    def test_cross_document_external_owner_and_receiver(self):
        strand_id = uuid.uuid4()
        old = comp(id=text('createfirefightingadd:smart_rope_connector'), x=number(16), y=number(64), z=number(-2))
        owner = comp(id=text('simulated:rope_connector'), OwnStrand=number(1, 1),
                     HasRopeAttached=Tag(11, strand_id.bytes),
                     Strand=comp(uuid=text(str(strand_id)), attachments=ls(
                         comp(blockAttachment=Tag(11, struct.pack('>iii',16,64,-2))))))
        receiver = comp(id=text('simulated:rope_connector'), HasRopeAttached=Tag(11, strand_id.bytes),
                        OwnStrand=number(0,1), Keep=text('untouched'))
        ctx = PhysicsContext()
        # Native owner occurs first in file order; collector must not depend on order.
        for doc in (owner, receiver, old):
            collect_physics_tree(doc, ctx)
        resolve_context(ctx)
        self.assertEqual(ctx.removed_rope_ids, {strand_id.bytes})
        self.assertNotIn(b'Strand', clean_physics_tree(owner, ctx).value)
        self.assertEqual(clean_physics_tree(receiver, ctx), comp(id=text('simulated:rope_connector'), Keep=text('untouched')))

    def test_dimension_scope_and_unrelated_rope(self):
        strand_id = uuid.uuid4()
        old = comp(id=text('createfirefightingadd:fluid_coupling'), x=number(16), y=number(64), z=number(-2))
        rope = comp(OwnStrand=number(1,1), Strand=comp(uuid=text(str(strand_id)), attachments=ls(
            comp(blockAttachment=Tag(11,struct.pack('>iii',16,64,-2))))))
        ctx=PhysicsContext()
        collect_physics_tree(old,ctx,'minecraft:the_nether')
        collect_physics_tree(rope,ctx)
        resolve_context(ctx)
        self.assertEqual(clean_physics_tree(rope,ctx),rope)

    def test_nested_owned_rope_and_default_preserves_fire(self):
        strand_id=uuid.uuid4()
        old=comp(id=text('createfirefightingadd:smart_rope_connector'),
                 OwnedRopes=ls(comp(Id=Tag(11,strand_id.bytes),Behavior=comp(HasRopeAttached=Tag(11,strand_id.bytes)))))
        ctx=PhysicsContext()
        collect_physics_tree(old,ctx)
        self.assertIn(strand_id.bytes,ctx.removed_rope_ids)
        fire=comp(id=text('createfirefightingadd:fire_hose'),x=number(1),y=number(2),z=number(3))
        collect_physics_tree(fire,ctx)
        self.assertFalse(ctx.removed_positions)
        collect_physics_tree(fire,ctx,all_legacy=True)
        self.assertIn((b'minecraft:overworld',1,2,3),ctx.removed_positions)

    def test_no_change_bytes_and_idempotence(self):
        raw=write_nbt(b'name',comp(entries=ls(comp(weld_id=Tag(11,uuid.uuid4().bytes)))))
        self.assertEqual(clean_physics_document(raw),raw)
        owned=comp(data=comp(records=ls(comp(key=comp(owner=comp(type=text('synaxis:system'),
            system=text('createfirefightingadd:fluid_coupling')))))))
        cleaned=clean_physics_document(write_nbt(b'',owned))
        self.assertEqual(clean_physics_document(cleaned),cleaned)

if __name__=='__main__':
    unittest.main()
