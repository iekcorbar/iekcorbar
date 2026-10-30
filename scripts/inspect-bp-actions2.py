#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Tweede pass: take/ give/ msg-acties en items-lijst van Backpack."""
import io, re

P = r'pluginset-aangepast\BetonQuest\QuestPackages\Backpack\package.yml'
t = io.open(P, encoding='utf-8').read()

WANT = [
    'bp_canvas_take', 'bp_frame_take', 'bp_final_take',
    'bp_give_backpack', 'bp2_give_backpack', 'bp3_give_backpack',
    'bp2_take_prepared_iron', 'bp2_take_prepared_string', 'bp2_take_prepared_chests',
    'bp2_take_bread', 'bp2_take_golden_apples',
    'bp2_take_books', 'bp2_take_amethyst', 'bp2_take_obsidian', 'bp2_take_strength_iron',
    'bp2_take_glowstone', 'bp2_take_ghast_tears',
    'bp2_start_trial1', 'bp2_start_trial2', 'bp2_start_trial3',
    'bp2_trial2_complete', 'bp2_trial3_complete',
    'bp3_take_r1_leather', 'bp3_take_r1_string', 'bp3_finish_repeat',
    'bp_msg_canvas_start', 'bp_msg_canvas_done', 'bp_msg_frame_phase',
    'bp_msg_combat_start', 'bp_msg_final_phase', 'bp_msg_complete',
    'bp2_msg_start', 'bp2_msg_trial2_prepared', 'bp2_msg_trial2_instinct',
    'bp2_msg_trial2_teamwork', 'bp2_msg_trial3_knowledge', 'bp2_msg_trial3_strength',
    'bp2_msg_trial3_hope', 'bp2_msg_complete',
    'bp3_msg_start', 'bp3_msg_repeat', 'bp3_msg_complete',
    'bp2_reset_temp', 'bp3_clear_repeat', 'bp2_cleanup', 'bp_cleanup_after_finish',
]

sec = re.search(r'(?ms)^actions:\n(.*?)^(?=[a-z_]+:)', t)
entries = re.split(r'(?m)^(?=  \w)', sec.group(1))
for e in entries:
    m = re.match(r'^  ([\w.]+): (.*)', e.replace('\n    ', ' '))
    if m and m.group(1) in WANT:
        print(m.group(1), '=>', m.group(2).strip()[:220])

print()
print('== items sectie ==')
items = re.search(r'(?ms)^items:\n(.*?)^(?=[a-z_]+:)', t)
print(items.group(1).strip())
