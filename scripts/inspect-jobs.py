#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Print per JobsRankN: quester-namen, npc-conversatiemap en send/locatie-zinnen."""
import io, re, glob, os

for p in sorted(glob.glob(r'pluginset-aangepast\BetonQuest\QuestPackages\JobsRank*\package.yml')):
    t = io.open(p, encoding='utf-8').read()
    print('='*20, p.split('\\')[2], '='*20)
    # questers
    for m in re.finditer(r'(\w+_conv):\n\s+quester: ([-\w ]+)', t):
        print(' quester:', m.group(1), '->', m.group(2).strip())
    # npc map
    m = re.search(r'(?ms)^npc_conversations:\n(.*?)^(?=[a-z_]+:)', t)
    if m:
        for line in m.group(1).strip().splitlines():
            print('  map', line.strip())
    # zinnen met vindplaatsen
    for kw in ['You can find', 'find them', 'find him', 'find her', 'Find the', 'Go to the', 'is waiting']:
        for m in re.finditer(r'.{0,10}(' + kw + r'.{0,140})', t):
            s = ' '.join(m.group(1).split())
            print('   loc:', s[:150])
    # welcome/registraat zinnen eerste regels
    m = re.search(r'reg_welcome:\n\s+text: (.+)', t)
    if m:
        print('   welcome:', ' '.join(m.group(1).split())[:200])
