#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Backpack package.yml skelet: secties, entries per sectie, sleutels compact."""
import io, re, sys

P = r'pluginset-aangepast\BetonQuest\QuestPackages\Backpack\package.yml'
t = io.open(P, encoding='utf-8').read()
lines = t.splitlines()

top = None
counts = {}
keys = {}
for i, l in enumerate(lines):
    m = re.match(r'^([a-z_]+):', l)
    if m:
        top = m.group(1)
        counts.setdefault(top, 0)
        keys.setdefault(top, [])
        continue
    if top and re.match(r'^  [\w.\'"]+:', l):
        counts[top] += 1
        k = l.strip().split(':')[0].strip("'")
        if len(keys[top]) < 400:
            keys[top].append(k)

for s, n in counts.items():
    print('==', s, '=', n, 'entries')
    if s in ('items', 'npcs', 'npc_conversations'):
        print('   ', keys[s])
    elif s == 'conversations':
        print('   ', keys[s])
    elif s in ('actions', 'objectives', 'conditions', 'journal', 'global_tasks', 'variables'):
        # alleen de eerste 60 namen, de rest is herkenbaar aan prefix
        print('   ', keys[s][:60])
