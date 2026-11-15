import unittest
from copy import deepcopy
from split_nbt import Tag,number,pack_indices,unpack_indices
from cleanup_nbt import clean_tree
from physics_cleanup import (PhysicsContext, collect_physics_tree,collect_empty_bodies,
                             resolve_context,clean_physics_tree)


def c(**data):return Tag(10,{k.encode():v for k,v in data.items()})
def s(value):return Tag(8,value.encode())
def ls(*items):return Tag(9,(10,list(items)))


class ProviderCleanupTest(unittest.TestCase):
    def test_remove_exact_provider_preserves_same_block_next_to_it(self):
        palette=[c(Name=s('synaxis:rotary_joint')),c(Name=s('minecraft:air'))]
        ids=[1]*4096;ids[1]=0;ids[2]=0
        chunk=c(xPos=number(0),zPos=number(0),sections=ls(c(Y=number(0,1),
            block_states=c(palette=ls(*palette),data=pack_indices(palette,ids)))),
            block_entities=ls(c(id=s('synaxis:rotary_joint'),x=number(1),y=number(0),z=number(0)),
                              c(id=s('synaxis:rotary_joint'),x=number(2),y=number(0),z=number(0))))
        ctx=PhysicsContext(provider_positions={(b'minecraft:overworld',1,0,0)})
        result=clean_physics_tree(chunk,ctx)
        states=result.value[b'sections'].value[1][0].value[b'block_states'].value
        after=unpack_indices(states[b'palette'].value[1],states[b'data'],4096)
        self.assertEqual((after[1],after[2]),(1,0))
        self.assertEqual(len(result.value[b'block_entities'].value[1]),1)
        self.assertEqual(len(chunk.value[b'block_entities'].value[1]),2)

    def test_remnant_lever_and_entire_plot_survive_constraint_cleanup(self):
        body=bytes(range(16))
        palette=[c(Name=s('createfirefightingadd:ball_coupling_base')),
                 c(Name=s('minecraft:lever')),c(Name=s('minecraft:air'))]
        ids=[0,1]+[2]*4094
        original=c(uuid=Tag(11,body),plot=c(chunks=Tag(10,{b'0':c(sections=Tag(10,{
            b'4':c(block_states=c(palette=ls(*palette),data=pack_indices(palette,ids)))}))})))
        cleaned=clean_tree(deepcopy(original))
        self.assertTrue(cleaned.value[b'plot'].value[b'chunks'].value)
        states=cleaned.value[b'plot'].value[b'chunks'].value[b'0'].value[b'sections'].value[b'4'].value[b'block_states'].value
        self.assertEqual(states[b'palette'].value[1][1].value[b'Name'],s('minecraft:lever'))
        # BlockPos(13,-49,-12), matching the failure's signed-coordinate case.
        packed=((13&0x3ffffff)<<38)|((-12&0x3ffffff)<<12)|(-49&0xfff)
        record=c(key=c(owner=c(type=s('synaxis:block'),dimension=s('minecraft:overworld'),pos=number(packed,4))),
                 spec=c(body_b=c(dimension=s('minecraft:overworld'),id=Tag(11,body))))
        mirror=c(records=ls(record));ctx=PhysicsContext()
        collect_physics_tree(mirror,ctx)
        collect_empty_bodies(original,cleaned,ctx)
        resolve_context(ctx)
        self.assertIn((b'minecraft:overworld',13,-49,-12),ctx.provider_positions)
        self.assertFalse(ctx.removed_bodies)
        self.assertEqual(clean_physics_tree(mirror,ctx).value[b'records'],ls())
        self.assertEqual(clean_physics_tree(cleaned,ctx),cleaned)

    def test_sable_exact_provider_does_not_remove_chunk_or_structure(self):
        pos=(20481032,129,20497416)
        # Sable sections use index 12 for world section Y=8 in overworld.
        chunk=c(sections=Tag(10,{b'12':c(block_states=c(palette=ls(c(Name=s('simulated:rope_connector')))))}),
                block_entities=ls(c(id=s('simulated:rope_connector'),x=number(pos[0]),y=number(pos[1]),z=number(pos[2]))))
        root=c(uuid=Tag(11,bytes(range(16))),plot=c(chunks=Tag(10,{b'274877907008':chunk})))
        ctx=PhysicsContext(provider_positions={(b'minecraft:overworld',*pos)})
        result=clean_physics_tree(root,ctx)
        out=result.value[b'plot'].value[b'chunks'].value[b'274877907008'].value
        states=out[b'sections'].value[b'12'].value[b'block_states'].value
        ids=unpack_indices(states[b'palette'].value[1],states[b'data'],4096)
        self.assertEqual(ids.count(1),1)
        self.assertEqual(ids.count(0),4095)
        self.assertEqual(out[b'block_entities'],ls())


if __name__=='__main__':unittest.main()
