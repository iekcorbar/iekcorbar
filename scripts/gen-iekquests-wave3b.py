#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Golf 3b: genereer Rod_merchant + 24 guard-menu's + Backpack I-ketting in
IekQuests/src/main/resources/quests.yml.

Bron: pluginset-aangepast/BetonQuest/QuestPackages/{Rod_merchant,NPCGuideGuards,Backpack}.
Aanpas-regels (eigenaar): geen BQ willekeurige-daily-contracten - panel met
knoppen (12 viscontracten, 10 Orion-verzoeken); teleport-besturing staff-only;
spelerautonomie: guides lopen NAAR de speler toe via /npcguide.

Idempotent: blok tussen de wave3b-markeringen wordt vervangen.
"""
import io, os, re

HERE = os.path.dirname(os.path.abspath(__file__))
YML = os.path.join(HERE, '..', 'IekQuests', 'src', 'main', 'resources', 'quests.yml')
BEGIN = '  # >>> wave3b gegenereerd (scripts/gen-iekquests-wave3b.py) >>>'
END = '  # <<< wave3b <<<'

L = []          # output-regels
def w(s=''):
    L.append(s)

# ---------------------------------------------------------------- helper
def block(qid, title, subtitle, icon, order, npc_line, start, journal, stages, repeats=None):
    w(f'  {qid}:')
    w(f'    title: "{title}"')
    w(f'    subtitle: "{subtitle}"')
    w(f'    icon: {icon}')
    w(f'    {npc_line}')
    w(f'    order: {order}')
    if start:
        w(f'    start: "{start}"')
    w('    journal:')
    for j in journal:
        w(f'      - "{j}"')
    w('    stages:')
    for key, st in stages:
        w(f'      {key}:')
        w(f'        type: {st["type"]}')
        if 'label' in st: w(f'        label: "{st["label"]}"')
        if 'material' in st: w(f'        material: {st["material"]}')
        if 'name' in st: w(f'        name: "{st["name"]}"')
        if 'amount' in st: w(f'        amount: {st["amount"]}')
        if 'mob' in st: w(f'        mob: {st["mob"]}')
        if 'location' in st: w(f'        location: "{st["location"]}"')
        if 'radius' in st: w(f'        radius: {st["radius"]}')
        if st.get('flat'): w('        flat: true')
        if 'goto' in st: w(f'        goto: {st["goto"]}')
        if 'cond' in st: w(f'        cond: "{st["cond"]}"')
        if st.get('items'):
            w('        items:')
            for it in st['items']:
                w(f'          - "{it}"')
        if st.get('lines'):
            w('        lines:')
            for s in st['lines']:
                w(f'          - "{s}"')
        if st.get('rewards'):
            w('        rewards:')
            for r in st['rewards']:
                w(f'          - "{r}"')
        if st.get('choices'):
            w('        choices:')
            for ch in st['choices']:
                w(f'          - label: "{ch["label"]}"')
                if 'say' in ch: w(f'            say: "{ch["say"]}"')
                if 'goto' in ch: w(f'            goto: {ch["goto"]}')
                if 'cond' in ch: w(f'            cond: "{ch["cond"]}"')
                if ch.get('close'): w('            close: true')
                if ch.get('do'):
                    w('            do:')
                    for d in ch['do']:
                        w(f'              - "{d}"')
    w('    rewards: []')
    if repeats:
        w('    repeat:')
        for rid, rp in repeats:
            w(f'      {rid}:')
            if rp.get('type'): w(f'        type: {rp["type"]}')
            w(f'        label: "{rp["label"]}"')
            if 'items' in rp:
                w('        items:')
                for it in rp['items']:
                    w(f'          - "{it}"')
            else:
                w(f'        material: {rp["material"]}')
                if rp.get('name'): w(f'        name: "{rp["name"]}"')
                w(f'        amount: {rp["amount"]}')
            w(f'        cooldown: {rp.get("cooldown", 300)}')
            if 'money' in rp: w(f'        money: {rp["money"]}')
            w('        rewards:')
            for r in rp['rewards']:
                w(f'          - "{r}"')
    w('')

# ---------------------------------------------------------------- Rod_merchant
FISH = [
    ('c1',  'Tuna',          4, 400, 'Ocean, Deep Ocean, Beach or Stony Shore'),
    ('c2',  'Shrimp',        6, 400, 'Ocean, Deep Ocean, Beach or Stony Shore'),
    ('c3',  'Angelfish',     3, 450, 'Warm, Lukewarm or Deep Lukewarm Ocean'),
    ('c4',  'Catfish',       4, 400, 'River, Plains, Forest, Birch, Flower Forest, Meadow'),
    ('c5',  'Pike',          3, 450, 'River, Plains, Forest, Birch, Flower Forest, Meadow'),
    ('c6',  'Scorpio Fish',  2, 550, 'Jungle, Bamboo Jungle or Sparse Jungle'),
    ('c7',  'Glacierfish',   3, 550, 'Cold/Frozen Ocean, Frozen River or Snowy Beach'),
    ('c8',  'Slimejack',     3, 500, 'Swamp or Mangrove Swamp'),
    ('c9',  'Stonefish',     2, 650, 'Lush Caves, Dripstone Caves or Deep Dark'),
    ('c10', 'Herring',       5, 400, 'Ocean, Deep Ocean, Beach or Stony Shore'),
    ('c11', 'Perch',         5, 400, 'River, Plains, Forest, Birch, Flower Forest, Meadow'),
    ('c12', 'Greengill',     4, 500, 'Swamp or Mangrove Swamp'),
]
SHOP_SAY = "&7Rod Merchant: &8Handles first. Reels second. Patience above all."
rod_journal = ["Fish contracts: 22 min cooldown after each delivery.",
               "Reputation: rodmerchant_friendship"]
for _, n, a, m, b in FISH:
    rod_journal.append(f"&7{n} x{a} - {b} &8({m}c)")
block(
    'rod_merchant',
    '&bFishing Rod Merchant', 'Rods, lines & fish contracts', 'FISHING_ROD', 46,
    'npc-id: 81', '', rod_journal,
    [('intro', dict(
        type='talk', label='&aLet\'s do business.',
        lines=[
            '&fRod Merchant: &7I sell fishing rods, reinforced lines and tools',
            '&7for those who are not afraid to wait by the water.',
            '&7The best rods are not sold to the impatient.',
            '&7Bring me the fish I list and the coin follows.'],
        choices=[
            dict(label='&bBrowse the rod counter', say=SHOP_SAY,
                 do=['shop mi stations open shop-fishing-rod-1 %player%']),
            dict(label='&bPremium rod rack', say=SHOP_SAY,
                 do=['shop mi stations open shop-fishing-rod-2 %player%']),
            dict(label='&bReserved rod shelf', say=SHOP_SAY,
                 do=['shop mi stations open shop-fishing-rod-3 %player%'])]))],
    [(fid, dict(label=f'&a{a}x {n}', material='COD', name=f'={n}', amount=a,
                cooldown=1320, money=m,
                rewards=[
                    'point rodmerchant_friendship +1',
                    f'say &7+{m} coins and +1 reputation with the Rod Merchant',
                    '&aCONTRACT COMPLETE|&7The merchant pays up',
                    'sound steamworks:quest.complete']))
     for fid, n, a, m, _ in FISH])

# ---------------------------------------------------------------- 24 guards
PROFS = [
    ('miner', 'Mine'), ('woodcutter', 'Lumber yard'), ('fisherman', 'Fishing pier'),
    ('digger', 'Quarry'), ('farmer', 'Farmland'), ('chef', 'Kitchen'),
    ('weaponsmith', 'Weapon smithy'), ('tea_house_akito', 'Tea house (Akito)'),
    ('candy_shop_momo', 'Candy shop (Momo)'), ('vorkan_kross', 'Vorkan Kross'),
    ('brakka_stonewall', 'Brakka Stonewall'), ('old_guard_parkour', 'Guard parkour'),
    ('cleric', 'Cleric\'s chapel'), ('coffe_house', 'Coffee house'),
    ('fishing_rod_merchant', 'Rod merchant'), ('ice_cream_cart', 'Ice cream cart'),
    ('mad_doctor', 'Mad Doctor'), ('old_dungeon_1', 'Old dungeon'),
    ('petragemologist', 'Petragemologist'), ('postal_office', 'Postal office'),
    ('sake_brewery', 'Sake brewery'),
]
for g in range(1, 25):
    npc = 93 if g == 1 else 97 + g
    choices = [dict(label=f'&e{pretty}',
                    do=[f'npcguide start spawn_guard_{g} {key} %player%'],
                    close=True) for key, pretty in PROFS]
    block(
        f'guard_{g}',
        f'&6Guard Guide &8- post {g}', 'Ask, and a guide walks with you', 'IRON_BARS',
        200 + g, f'npc-id: {npc}', '',
        ['Pick a destination and the guard post sends a guide along.',
         'The guide walks you there - nothing is paid, nothing is owed.'],
        [('menu', dict(
            type='menu', label='&7Guide menu',
            lines=[
                '&fGuard: &7State where you want to go.',
                '&7A guide will walk you there - or come back later.'],
            choices=choices))])

# ---------------------------------------------------------------- Backpack one
bp1 = []
bp1.append(('intro', dict(
    type='talk', label='&aAccept the first trial',
    lines=[
        '&fElias: &7You travel light, friend. Too light.',
        '&7A real adventurer needs a proper backpack.',
        '&8Mine are earned, not gifted. Are you willing to work for your first one?'],
    choices=[
        dict(label='&aI want to earn my first backpack.', goto='explore_forest'),
        dict(label='&7Maybe another time.')])))
for key, name, loc, r, hint in [
    ('explore_forest', 'Forest Shrine', '2678;100;-98', 40, 'a quiet wood shrine'),
    ('explore_river', 'River Bend', '3162;100;-182', 60, 'where the water turns'),
    ('explore_plains', 'Open Plains', '3159;100;-1369', 30, 'wide grass under open sky'),
    ('explore_mountain', 'Old Mountain Pass', '2793;100;-182', 40, 'the old pass over the ridge')]:
    bp1.append((key, dict(
        type='walk', label=f'&eTravel to the {name}', flat=True,
        location=f'{loc};iekcorbarSeason2', radius=r,
        lines=[
            '&fElias: &7A backpack is for someone who has seen the world.',
            f'&7First: find the &6{name}&7 - {hint}.',
            f'&8X {loc.split(";")[0]}, Z {loc.split(";")[2]} - the compass points the way.'])))
bp1.extend([
    ('canvas', dict(
        type='deliverall', label='&aHand in the canvas materials',
        lines=[
            '&fMara: &7Elias sent you? Then we need travelling canvas.',
            '&7Bring me &624 Leather&7, &632 String&7 and &616 White Wool&7.',
            '&8It does not need to be pretty. It needs to hold.'],
        items=['LEATHER 24', 'STRING 32', 'WHITE_WOOL 16'])),
    ('frame', dict(
        type='deliverall', label='&aHand in the frame materials',
        lines=[
            '&fBrann: &7A backpack without a frame is just a sad sack.',
            '&7Bring me &612 Iron Ingots&7, &616 Copper Ingots&7 and &61 Chest&7.',
            '&8I will make it reliable. No unnecessary steam pistons. Probably.'],
        items=['IRON_INGOT 12', 'COPPER_INGOT 16', 'CHEST 1'])),
    ('hunt_vindicator', dict(
        type='kill', mob='VINDICATOR', amount=5, label='&cHunt 5 Vindicators',
        lines=[
            '&fElias: &7Anyone can gather supplies. A backpack belongs to someone',
            '&7who can survive the wild. Start with &65 Vindicators&7.'],
        goto='hunt_witch')),
    ('hunt_witch', dict(
        type='kill', mob='WITCH', amount=5, label='&cHunt 5 Witches',
        lines=['&7Next: &65 Witches&7. Each kill shows your progress.'],
        goto='hunt_skeleton')),
    ('hunt_skeleton', dict(
        type='kill', mob='SKELETON', amount=10, label='&cHunt 10 Skeletons',
        lines=['&7Last: &610 Skeletons&7. Then come back to me.'],
        goto='final')),
    ('final', dict(
        type='deliverall', label='&aHand in the final fittings',
        lines=[
            '&fElias: &7You have proven your courage. Now we finish the work.',
            '&7Final fittings: &616 Leather&7, &616 String&7, &68 Iron Ingots&7',
            '&7and &62 Gold Ingots&7.'],
        items=['LEATHER 16', 'STRING 16', 'IRON_INGOT 8', 'GOLD_INGOT 2'],
        goto='q1')),
    ('q1', dict(
        type='menu',
        lines=['&fElias: &7Before I finish it, answer me honestly.',
               '&7A backpack reflects the kind of adventurer carrying it.',
               '&8What kind of traveller are you?'],
        choices=[
            dict(label='&eI explore every corner of the world.',
                 do=['tag- path_wealth', 'tag- path_courage', 'tag+ path_explorer'], goto='q2'),
            dict(label='&6I seek treasure and riches.',
                 do=['tag- path_explorer', 'tag- path_courage', 'tag+ path_wealth'], goto='q2'),
            dict(label='&cI fight whatever stands before me.',
                 do=['tag- path_explorer', 'tag- path_wealth', 'tag+ path_courage'], goto='q2')])),
    ('q2', dict(
        type='menu',
        lines=['&fElias: &8And when the road becomes dangerous, what keeps you alive?'],
        choices=[
            dict(label='&aPreparation is everything.',
                 do=['tag- mind_instinct', 'tag- mind_teamwork', 'tag+ mind_prepared'], goto='q3'),
            dict(label='&eI trust my instincts.',
                 do=['tag- mind_prepared', 'tag- mind_teamwork', 'tag+ mind_instinct'], goto='q3'),
            dict(label='&bI rely on my friends.',
                 do=['tag- mind_prepared', 'tag- mind_instinct', 'tag+ mind_teamwork'], goto='q3')])),
    ('q3', dict(
        type='menu',
        lines=['&fElias: &8Last question. What do you believe gives an adventurer true power?'],
        choices=[
            dict(label='&dKnowledge is power.',
                 do=['tag- power_strength', 'tag- power_hope', 'tag+ power_knowledge'], goto='craft'),
            dict(label='&cStrength is power.',
                 do=['tag- power_knowledge', 'tag- power_hope', 'tag+ power_strength'], goto='craft'),
            dict(label='&eHope is power.',
                 do=['tag- power_knowledge', 'tag- power_strength', 'tag+ power_hope'], goto='craft')])),
    ('craft', dict(
        type='talk', label='&aLet Elias sew the backpack',
        lines=[
            '&fElias: &7You brought everything. Everything is ready.',
            '&7Let me craft something worthy of your journey.'],
        rewards=[
            'satchel give canvas_backpack %player%',
            'tag+ bp_done',
            'title &aBACKPACK COMPLETE|&7Your first backpack is yours',
            'sound steamworks:quest.complete']))])
block('backpack_one', '&2The First Backpack', 'Elias Packwright - earned, not gifted',
      'LEATHER', 40, 'npc-ids: "90,91,92"', '',
      ['Level 1: explore 4 landmarks, stitch canvas (Mara), build frame (Brann),',
       'survive the wild, then answer Elias three questions.',
       'Reward: canvas_backpack via the Satchel plugin.'],
      bp1)

# ---------------------------------------------------------------- Backpack two
bp2 = []
bp2.append(('intro', dict(
    type='talk', label='&aBegin the Level 2 trials',
    lines=[
        '&fElias: &7You have already earned your first backpack.',
        '&7Your three answers will now become three trials.',
        '&8Complete them and I will craft a Level 2 Backpack.'],
    choices=[
        dict(label='&aI am ready for the trials.', goto='branch1'),
        dict(label='&7Maybe another time.')])))
bp2.append(('branch1', dict(
    type='menu',
    lines=['&fElias: &7Your first trial follows the path you chose.'],
    choices=[
        dict(label='&eThe explorer\'s path: climb the Ancient Castle.',
             cond='qtag:backpack_one:path_explorer', goto='castle_tower'),
        dict(label='&6The seeker\'s path: open the hidden chest.',
             cond='qtag:backpack_one:path_wealth', goto='castle_chest'),
        dict(label='&cThe fighter\'s path: face what sleeps below.',
             cond='qtag:backpack_one:path_courage', goto='castle_warden')])))
bp2.append(('castle_tower', dict(
    type='walk', label='&eReach the lower tower', location='-725;98;-516;iekcorbarSeason2',
    radius=12, goto='castle_hall',
    lines=['&fElias: &7Travel to the Ancient Castle near X -792, Y 65, Z -501.',
           '&7Reach its &blower tower&7 first. The height of each stop matters.'])))
bp2.append(('castle_hall', dict(
    type='walk', label='&eClimb to the upper hall', location='-737;132;-513;iekcorbarSeason2',
    radius=12, goto='castle_summit',
    lines=['&7Good. Now the &bupper hall&7 - higher up the same stone.'])))
bp2.append(('castle_summit', dict(
    type='walk', label='&eStand at the summit', location='-758;171;-525;iekcorbarSeason2',
    radius=12, goto='branch2',
    lines=['&7And finally the &bsummit&7. From up there you can read the whole land.'])))
bp2.append(('castle_chest', dict(
    type='walk', label='&eOpen the empty chest', location='-791;119;-595;iekcorbarSeason2',
    radius=8, goto='branch2',
    lines=['&fElias: &7Find the empty chest high in the Ancient Castle.',
           '&8X -791, Y 119, Z -595 - and open it.',
           '&7The treasure was never its contents, but your ability to reach it.'])))
bp2.append(('castle_warden', dict(
    type='kill', mob='WARDEN', amount=1, label='&cDefeat a Warden', goto='branch2',
    lines=['&fElias: &7You chose the path of battle.',
           '&7Defeat a &cWarden&7 while you are inside the castle region.',
           '&8The dark listens. Bring armour and a friend if you can.'])))
bp2.append(('branch2', dict(
    type='menu',
    lines=['&fElias: &7The second trial follows what keeps you alive.'],
    choices=[
        dict(label='&aThe prepared mind: hand in supplies.',
             cond='qtag:backpack_one:mind_prepared', goto='t2_prepared'),
        dict(label='&eThe instinctive mind: hunt what punishes slow decisions.',
             cond='qtag:backpack_one:mind_instinct', goto='t2_enderman'),
        dict(label='&bThe team mind: provision a small group.',
             cond='qtag:backpack_one:mind_teamwork', goto='t2_teamwork')])))
bp2.extend([
    ('t2_prepared', dict(
        type='deliverall', label='&aHand in the prepared supplies', goto='branch3',
        lines=['&fElias: &7You believe preparation keeps you alive.',
               '&7Bring me &632 Iron Ingots&7, &632 String&7 and &62 Chests&7.'],
        items=['IRON_INGOT 32', 'STRING 32', 'CHEST 2'])),
    ('t2_enderman', dict(
        type='kill', mob='ENDERMAN', amount=8, label='&cDefeat 8 Endermen', goto='t2_spider',
        lines=['&fElias: &7You trust your instincts.',
               '&7Defeat &68 Endermen&7 and &612 Cave Spiders&7 -',
               '&7enemies that punish slow decisions.'])),
    ('t2_spider', dict(
        type='kill', mob='CAVE_SPIDER', amount=12, label='&cDefeat 12 Cave Spiders',
        goto='branch3')),
    ('t2_teamwork', dict(
        type='deliverall', label='&aHand in the group provisions', goto='branch3',
        lines=['&fElias: &7You rely on companions.',
               '&7Bring &632 Bread&7 and &64 Golden Apples&7,',
               '&7enough to keep a small group standing.'],
        items=['BREAD 32', 'GOLDEN_APPLE 4']))])
bp2.append(('branch3', dict(
    type='menu',
    lines=['&fElias: &7The last trial follows what you believe gives true power.'],
    choices=[
        dict(label='&dKnowledge: books and memory made solid.',
             cond='qtag:backpack_one:power_knowledge', goto='t3_knowledge'),
        dict(label='&cStrength: materials that will not bend.',
             cond='qtag:backpack_one:power_strength', goto='t3_strength'),
        dict(label='&eHope: light taken from a hostile place.',
             cond='qtag:backpack_one:power_hope', goto='t3_hope')])))
bp2.extend([
    ('t3_knowledge', dict(
        type='deliverall', label='&aHand in the knowledge materials', goto='q4',
        lines=['&fElias: &7You named knowledge as true power.',
               '&7Bring me &624 Books&7 and &616 Amethyst Shards&7.'],
        items=['BOOK 24', 'AMETHYST_SHARD 16'])),
    ('t3_strength', dict(
        type='deliverall', label='&aHand in the strength materials', goto='q4',
        lines=['&fElias: &7You named strength as true power.',
               '&7Bring me &616 Obsidian&7 and &632 Iron Ingots&7.'],
        items=['OBSIDIAN 16', 'IRON_INGOT 32'])),
    ('t3_hope', dict(
        type='deliverall', label='&aHand in the hope materials', goto='q4',
        lines=['&fElias: &7You named hope as true power.',
               '&7Bring me &632 Glowstone Dust&7 and &2 Ghast Tears&7,',
               '&7light taken from a hostile place.'],
        items=['GLOWSTONE_DUST 32', 'GHAST_TEAR 2']))])
QUESTIONS = [
    ('q4', 'When duty conflicts with mercy, which guides you?', [
        ('&aDuty must come first.', 'q5'),
        ('&dMercy and compassion must guide me.', 'q5'),
        ('&eNo rule should replace personal freedom.', 'q5')]),
    ('q5', 'You discover an ancient artifact that could change many lives. What should be done with it?', [
        ('&aPreserve it and study it carefully.', 'q6'),
        ('&dUse its power to improve lives.', 'q6'),
        ('&cDestroy it before its power corrupts anyone.', 'q6')]),
    ('q6', 'When a group loses its way, what role do you take?', [
        ('&aI step forward and lead.', 'q7'),
        ('&dI support whoever is best suited to lead.', 'q7'),
        ('&eI leave the group and find my own way.', 'q7')]),
    ('q7', 'When your carefully chosen path ends in failure, what do you do?', [
        ('&aI try again until I succeed.', 'q8'),
        ('&dI change my plan and adapt.', 'q8'),
        ('&eI accept the failure and learn from it.', 'q8')]),
    ('q8', 'Last question. What legacy would you most want your journey to leave behind?', [
        ('&6I want my name and deeds to be remembered.', 'reward'),
        ('&aI want others to be safer because I existed.', 'reward'),
        ('&dI want others to have the freedom to choose their own path.', 'reward')]),
]
for key, text, opts in QUESTIONS:
    bp2.append((key, dict(
        type='menu', lines=['&fElias: &8' + text],
        choices=[dict(label=l, goto=g) for l, g in opts])))
bp2.append(('reward', dict(
    type='talk', label='&aCraft the Level 2 Backpack',
    lines=[
        '&fElias: &7All three trials reflect the traveller you chose to become.',
        '&7Everything is ready.',
        '&8Let me craft a backpack worthy of that choice.'],
    rewards=[
        'satchel give copper_backpack %player%',
        'tag+ bp_done',
        'title &2LEVEL 2 BACKPACK|&7Copper and choices',
        'sound steamworks:quest.complete'])))
block('backpack_two', '&6The Traveller\'s Mirror', 'Elias Packwright - three trials, three answers',
      'BOOK', 41, 'npc-id: 90', 'qtag:backpack_one:bp_done',
      ['Level 2: one trial per answer you gave in the first backpack quest.',
       'Castle routes, big hand-ins, five personal questions.',
       'Reward: copper_backpack via the Satchel plugin.'],
      bp2)

# ---------------------------------------------------------------- Backpack three
REQ = [
    ('r1',  '&dOrion: 32 Leather + 32 String', ['LEATHER 32', 'STRING 32']),
    ('r2',  '&dOrion: 48 Iron + 32 Coal', ['IRON_INGOT 48', 'COAL 32']),
    ('r3',  '&dOrion: 32 Copper + 24 Redstone', ['COPPER_INGOT 32', 'REDSTONE 24']),
    ('r4',  '&dOrion: 12 Gold + 8 Emeralds', ['GOLD_INGOT 12', 'EMERALD 8']),
    ('r5',  '&dOrion: 24 Rotten Flesh + 24 Bones', ['ROTTEN_FLESH 24', 'BONE 24']),
    ('r6',  '&dOrion: 12 Ender Pearls + 8 Blaze Rods', ['ENDER_PEARL 12', 'BLAZE_ROD 8']),
    ('r7',  '&dOrion: 32 Prismarine + 4 Nautilus', ['PRISMARINE_SHARD 32', 'NAUTILUS_SHELL 4']),
    ('r8',  '&dOrion: 16 Amethyst + 32 Glowstone', ['AMETHYST_SHARD 16', 'GLOWSTONE_DUST 32']),
    ('r9',  '&dOrion: 4 Phantom Membranes + 2 Ghast Tears', ['PHANTOM_MEMBRANE 4', 'GHAST_TEAR 2']),
    ('r10', '&dOrion: 2 Diamonds + 1 Echo Shard', ['DIAMOND 2', 'ECHO_SHARD 1']),
]
bp3 = []
bp3.append(('intro', dict(
    type='menu',
    lines=[
        '&fElias: &7You have mastered both backpack trials.',
        '&7I cannot craft the final one - but I can tell you how to seek it.',
        '&8The entrance to the city below is beneath the capital. Arm yourself.'],
    choices=[
        dict(label='&dI want the final backpack.', goto='explain'),
        dict(label='&aAsk Orion about trade.', cond='qtag:backpack_one:bp_done,!qtag:backpack_two:bp_done',
             do=['chat hi 1']),
        dict(label='&aAsk Orion about trade.', cond='qtag:backpack_two:bp_done,!qtag:backpack_three:bp_done',
             do=['chat hi 2']),
        dict(label='&aAsk Orion to see everything.', cond='qtag:backpack_three:bp_done',
             do=['chat hi 3'])])))
bp3.append(('explain', dict(
    type='talk', label='&aDescend into the city below',
    lines=[
        '&fElias: &7Long ago, the passages beneath the city were built as a bunker.',
        '&7Terrible creatures claimed them. Now only the finest adventurers descend.',
        '&7Somewhere inside is &6Orion&7 - he alone can grant the final backpack.',
        '&7Everything clear? Then go.'],
    goto='descend')))
bp3.append(('descend', dict(
    type='walk', label='&eEnter the city below', location='2874;67;-589;iekcorbarSeason2',
    radius=20, goto='orion',
    lines=[
        '&6The City Below&7 - the entrance is at:',
        '&bX 2874, Y 67, Z -589&7.',
        '&8Arm yourself and prepare before you descend.'])))
bp3.append(('orion', dict(
    type='talk', label='&aAsk your questions',
    lines=[
        '&fOrion: &7So Elias sent someone into the darkness at last.',
        '&7These tunnels devour the unprepared. You reached me -',
        '&7that already says more than most promises.',
        '&8Before I give you the final backpack, answer five questions honestly.'],
    goto='oq1')))
OQ = [
    ('oq1', 'Why do you enter places that sensible people fear?', [
        ('&aI want to discover what others never found.', 'oq2'),
        ('&6I seek treasure worth the risk.', 'oq2'),
        ('&dI go where others need protection.', 'oq2')]),
    ('oq2', 'A companion falls behind while danger closes in. What do you do?', [
        ('&aI turn back and rescue them immediately.', 'oq3'),
        ('&dI create a plan that saves them without losing everyone.', 'oq3'),
        ('&cI continue. The mission cannot fail for one person.', 'oq3')]),
    ('oq3', 'You find a forbidden source of power. What should happen to it?', [
        ('&aSeal it where nobody can reach it.', 'oq4'),
        ('&dStudy it until its purpose is understood.', 'oq4'),
        ('&cUse it. Power is wasted when left untouched.', 'oq4')]),
    ('oq4', 'When a group faces uncertainty, which place do you take?', [
        ('&aI step forward and lead.', 'oq5'),
        ('&dI support the person best suited to lead.', 'oq5'),
        ('&eI trust my own path and move alone.', 'oq5')]),
    ('oq5', 'Last question. What should remain after your journey ends?', [
        ('&6A name that people will remember.', 'reward'),
        ('&aA safer world for those who follow.', 'reward'),
        ('&dThe freedom for others to choose their own path.', 'reward')]),
]
for key, text, opts in OQ:
    bp3.append((key, dict(
        type='menu', lines=['&fOrion: &8' + text],
        choices=[dict(label=l, goto=g) for l, g in opts])))
bp3.append(('reward', dict(
    type='talk', label='&aReceive the Level 3 Backpack',
    lines=[
        '&fOrion: &7Honest answers and a long walk in the dark.',
        '&7The final backpack is yours. Wear it well.'],
    rewards=[
        'satchel give iron_backpack %player%',
        'tag+ bp_done',
        'title &dLEVEL 3 BACKPACK|&7Orion keeps his word',
        'sound steamworks:quest.complete'])))
bp3_repeats = [(rid, dict(type='deliverall', label=lab, items=items, cooldown=300,
                          rewards=['satchel give iron_backpack %player%',
                                   'say &7Orion hands over another Level 3 Backpack.',
                                   'title &dORION PAYS UP|&7Another iron backpack is yours',
                                   'sound steamworks:quest.complete']))
               for rid, lab, items in REQ]
block('backpack_three', '&dThe City Below', 'Orion the Adventurer - the final backpack',
      'DIAMOND', 42, 'npc-ids: "90,97"',
      'qtag:backpack_one:bp_done,qtag:backpack_two:bp_done',
      ['Descend at X 2874, Y 67, Z -589. Answer Orion five honest questions.',
       'After the first Level 3: Orion\'s supply requests come back as contracts',
       'on this panel - deliver any set for another iron_backpack.',
       'Reputation with Orion: none. He pays in backpacks.'],
      bp3, bp3_repeats)

# ---------------------------------------------------------------- JobsRank 1-5
JOBS = [
    dict(rank=1, perm='costum.jobsrank.1', reg=49, regname='Registrar',
         title='&aWorkshop Apprentice', subtitle='City professions - rank 1 registration',
         icon='WHEAT',
         lines=['&fRegistrar: &7Hm. Another one looking for a place in this city.',
                '&7Nothing here belongs to you. Not yet.',
                '&7Register as a Workshop Apprentice and you may work. May earn.',
                '&8Here, your value is tested over time.'],
         fin='&7So... you are the new one. The land does not care who you are. You work, or you starve.',
         lp='lp user %player% parent add rank_1',
         masters=[('farmer', 'Farmer', 50, 'on the floating island'),
                  ('fisherman', 'Fisherman', 51, 'at the pier of the Ying Yang Pavilion'),
                  ('miner', 'Miner', 52, 'inside the mountain, towards the mines')]),
    dict(rank=2, perm='costum.jobsrank.2', reg=53, regname='Sensei Mechanical Craftsman',
         title='&eMechanical Craftsman', subtitle='Second trade - rank 2',
         icon='IRON_SHOVEL',
         lines=['&fSensei: &7A second rank opens a second trade.',
                '&7Work enough, survive long enough - and the paths widen.',
                '&8Choose where you continue.'],
         fin='&7Good. The tools remember every hand. Do not let them forget yours.',
         lp='',
         masters=[('digger', 'Digger', 54, 'out on the worksite road'),
                  ('chef', 'Chef', 55, 'in the kitchens'),
                  ('woodcutter', 'Woodcutter', 56, 'by the timber stacks')]),
    dict(rank=3, perm='costum.jobsrank.3', reg=57, regname='Sensei Iron Samurai',
         title='&2Iron Samurai', subtitle='Third trade - rank 3',
         icon='IRON_SWORD',
         lines=['&fSensei: &7Iron does not argue. It endures.',
                '&7The third rank opens three paths. Choose one.'],
         fin='&7The Sensei nods once. &fEndurance.',
         lp='',
         masters=[('explorer', 'Explorer', 58, 'on the long roads'),
                  ('builder', 'Builder', 59, 'at the scaffolding'),
                  ('hunter', 'Hunter', 60, 'at the tree line')]),
    dict(rank=4, perm='costum.jobsrank.4', reg=61, regname='Sensei Machine Lord',
         title='&bMachine Lord', subtitle='Fourth trade - rank 4',
         icon='BREWING_STAND',
         lines=['&fSensei: &7Machines punish hesitation and reward patience.',
                '&7The fourth rank. Choose your metal.'],
         fin='&7A valve hisses. The Sensei almost smiles. &fGood.',
         lp='',
         masters=[('brewer', 'Brewer', 62, 'among the brewing vats'),
                  ('crafter', 'Crafter', 63, 'at the crafting benches'),
                  ('enchanter', 'Enchanter', 64, 'in the enchanting tower')]),
    dict(rank=5, perm='costum.jobsrank.5', reg=65, regname='Sensei Emperor of Steam',
         title='&dEmperor of Steam', subtitle='Fifth trade - rank 5',
         icon='BLAZE_POWDER',
         lines=['&fSensei: &7The highest rank. Few ever stand here.',
                '&7Steel, armour, or the unseen path. Choose.'],
         fin='&7A burst of steam. &fGood.',
         lp='',
         masters=[('weaponsmith', 'Weaponsmith', 66, 'at the forge'),
                  ('shinobi', 'Shinobi', 44, 'somewhere in the shadows - they call him Jiraya'),
                  ('armorsmith', 'Armorsmith', 68, 'at the second forge')]),
]
for jd in JOBS:
    N = jd['rank']; R = jd['reg']; MS = jd['masters']
    st = []
    ch = []
    import re as _re
    base = _re.sub(r'&.', '', jd['title'])
    for job, pretty, m, hint in MS:
        ch.append(dict(label=f'&6Take the {pretty} path', cond=f'npc:{R},!tag:job_{job}',
                       goto=f'go_{job}'))
        ch.append(dict(label=f'&7I belong to the {pretty}s already.',
                       cond=f'npc:{R},tag:job_{job}',
                       say=f'&7The Registrar waves you on. &8The {pretty.lower()}s already know your hands.'))
    for job, pretty, m, hint in MS:
        for job2, pretty2, m2, hint2 in MS:
            if m2 == m:
                continue
            ch.append(dict(label=f'&8[{pretty2} Master]', cond=f'npc:{m2}',
                           say=f'&7You do not belong here. &8The {pretty2} Master is waiting for someone else.'))
    st.append(('choose', dict(type='menu', lines=jd['lines'], choices=ch)))
    for job, pretty, m, hint in MS:
        go = []
        doj = [f'joinjob {job}', f'tag+ job_{job}',
               f'title &a{base} &8| &7Welcome to the {pretty.lower()}s',
               'sound steamworks:quest.complete']
        if jd['lp']:
            go.append(dict(label=f'&aRegister with the {pretty} Master',
                           cond=f'npc:{m},!tag:reg',
                           do=[f'joinjob {job}', jd['lp'], 'tag+ reg', f'tag+ job_{job}',
                               f'title &aCITIZEN|&7You now belong to the {pretty.lower()}s',
                               'sound steamworks:quest.complete'],
                           goto=f'fin_{job}'))
        else:
            go.append(dict(label=f'&aRegister with the {pretty} Master',
                           cond=f'npc:{m},!tag:reg',
                           do=doj + ['tag+ reg'], goto=f'fin_{job}'))
        for job2, pretty2, m2, hint2 in MS:
            if m2 == m:
                continue
            go.append(dict(label=f'&eSwitch: leave the {pretty2}s for the {pretty}s',
                           cond=f'npc:{m},tag:reg,tag:job_{job2}',
                           do=[f'leavejob {job2}', f'joinjob {job}', f'tag- job_{job2}',
                               f'tag+ job_{job}',
                               f'title &ePATH CHANGED|&7Now working as {pretty.lower()}',
                               'sound steamworks:quest.complete'],
                           goto=f'fin_{job}'))
        for job2, pretty2, m2, hint2 in MS:
            if m2 == m:
                continue
            go.append(dict(label=f'&8Go to the {pretty2} Master instead', cond=f'npc:{m2}',
                           say=f'&7Wrong door. The {pretty2} Master waits {hint2}.'))
        go.append(dict(label='&8Back to the Registrar', cond=f'npc:{R}',
                       say=f'&7I already sent you to the {pretty} Master. &8Go. '
                           f'You will find them {hint}.'))
        st.append((f'go_{job}', dict(
            type='menu',
            lines=[f'&fThe {pretty} Master waits {hint}.',
                   '&7Click the right master to register - or switch your path here.',
                   '&8Wrong master? They will send you on.'],
            choices=go)))
        st.append((f'fin_{job}', dict(
            type='talk', label='&7To work.',
            lines=[f'&f{pretty} Master: ', jd['fin'],
                   '&7From this moment on, you belong to the ' + pretty.lower() + 's.'])))
    npcids = ','.join(str(x) for x in [R] + [m for _, _, m, _ in MS])
    block(f'jobs_rank{N}', f'{jd["title"]} &8(rank {N})', jd['subtitle'], jd['icon'],
          300 + N, f'npc-ids: "{npcids}"', f'perm:{jd["perm"]},!tag:reg',
          ['Register at the rank master, then report to a trade master of your choice.',
           'One trade per rank; switches leave the old trade behind.',
           'The Jobs plugin does the rest (max 5 trades per citizen).'],
          st)

# ---------------------------------------------------------------- inbouwen
new_block = BEGIN + '\n' + '\n'.join(L).rstrip('\n') + '\n' + END + '\n'
t = io.open(YML, encoding='utf-8').read()
pat = re.compile(r'(?ms)^[ \t]*# >>> wave3b.*?^[ \t]*# <<< wave3b <<<\n')
if pat.search(t):
    t = pat.sub(new_block, t, count=1)
    print('wave3b-blok VERVANGEN')
else:
    i = t.find('\nsequences:')
    if i < 0:
        t = t.rstrip('\n') + '\n' + new_block
        print('wave3b-blok toegevoegd aan einde')
    else:
        t = t[:i + 1] + new_block + t[i + 1:]
        print('wave3b-blok ingevoegd vóór sequences:')
io.open(YML, 'w', encoding='utf-8', newline='').write(t)
print('quests.yml totaal nu', t.count('\n'), 'regels')
