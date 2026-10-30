#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Print de bron van specifieke Backpack-acties (coordinaten, takes, satchel, shops)."""
import io, re

P = r'pluginset-aangepast\BetonQuest\QuestPackages\Backpack\package.yml'
t = io.open(P, encoding='utf-8').read()
# genormaliseerd: multi-line folding oplossen
lines = t.splitlines()

WANT = [
    'bp_msg_explore_coords', 'bp_msg_explore_coords_delayed',
    'bp_start_first', 'bp_start_repeat', 'bp_finish_first',
    'bp_start_canvas_phase', 'bp_canvas_complete', 'bp_start_frame_phase',
    'bp_frame_complete', 'bp_start_combat_phase', 'bp_vindicator_done',
    'bp_witch_done', 'bp_skeleton_done', 'bp_start_final_phase',
    'bp_final_materials_done_first', 'bp_final_materials_done_repeat',
    'bp_q1_explorer', 'bp_q2_prepared', 'bp_q3_knowledge',
    'bp_explore_found_forest', 'bp_explore_found_river',
    'bp_explore_found_plains', 'bp_explore_found_mountain',
    'bp_all_locations_found', 'bp_combat_complete',
    'bp2_start', 'bp2_start_repeat', 'bp2_take_prepared', 'bp2_take_teamwork',
    'bp2_take_knowledge', 'bp2_take_strength', 'bp2_take_hope',
    'bp2_trial1_complete', 'bp2_enderman_done', 'bp2_cavespider_done',
    'bp2_questions_complete', 'bp2_finish',
    'bp3_start_first', 'bp3_start_repeat', 'bp3_begin_questions',
    'bp3_assign_random', 'bp3_take_request_1', 'bp3_finish_first',
    'orion_shop_say_hi_1', 'orion_shop_say_hi_2', 'orion_shop_say_hi_3',
    'bp2_found_castle_explorer_1',
]

# acties sectie: top-level 'actions:' tot volgende top-level
sec = re.search(r'(?ms)^actions:\n(.*?)^(?=[a-z_]+:)', t)
body = sec.group(1)
# entries: 2-spatie inspringing
entries = re.split(r'(?m)^(?=  \w)', body)
for e in entries:
    m = re.match(r'^  ([\w.]+): (.*)', e.replace('\n    ', ' '))
    if not m:
        continue
    if m.group(1) in WANT:
        print(m.group(1), '=>', m.group(2).strip()[:300])
