#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Bemonster de 24 NPCGuideGuards packages: npc-id + professielijst per guard."""
import io, re, glob, os

root = os.path.join(os.path.dirname(__file__), '..', 'pluginset-aangepast',
                    'BetonQuest', 'QuestPackages', 'NPCGuideGuards')
for d in sorted(os.listdir(root)):
    p = os.path.join(root, d, 'package.yml')
    if not os.path.isfile(p):
        continue
    t = io.open(p, encoding='utf-8').read()
    npc = re.search(r"npcs:\s*\n\s*'(\d+)'", t)
    profs = re.findall(r"command npcguide start (\S+)\s*\n?\s*?(\S+) %player%", t.replace('\n    ', ' '))
    ids = re.findall(r"command npcguide start (spawn_guard_\d+) ([\w-]+) %player%", t.replace('\n    ', ' '))
    print(d, '| npc', npc.group(1) if npc else '?', '| actions', len(ids),
          '| profs:', ','.join(sorted({x[1] for x in ids})))
