"""Builds every EliteMobs model (the pilot four + bipeds + beasts + floaters) as .bbmodel for FreeMinecraftModels and
writes models.json (entityType -> model name) next to them.

Usage: python -I tools/make_elite_all.py <out folder>        (default ../fmm-models/elitemobs)
Server: copy the .bbmodel files into plugins/FreeMinecraftModels/models/elitemobs/ (see assign_models.py for the boss files).
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # -I does not add the script folder

import make_elite as me
import elite_bipeds
import elite_beasts
import elite_floaters
import elite_extra
import elite_npcs
from make_elite import mk

# vanilla size correction for models drawn at their "natural" pixel size (ghast 4 blocks, dragon ~ 10, wither 3.5 ...)
mk.SIZE.update({"airship_ghast": 2.0, "zeppelin_dragon": 2.2, "forge_wither": 1.25, "elder_submarine": 2.2})

TYPE_TO_MODEL = {
    "ZOMBIE": "em_steam_zombie", "HUSK": "em_rust_husk", "SKELETON": "em_clockwork_skeleton", "PILLAGER": "em_steam_pillager",
    "DROWNED": "em_diver_drowned", "STRAY": "em_frost_stray", "BOGGED": "em_bog_bogged",
    "WITHER_SKELETON": "em_ember_wither_skeleton", "ZOMBIFIED_PIGLIN": "em_gilded_zombified_piglin", "PIGLIN": "em_brass_piglin",
    "VINDICATOR": "em_steam_vindicator", "EVOKER": "em_arcane_evoker", "ILLUSIONER": "em_clockwork_illusioner", "WITCH": "em_brew_witch",
    "IRON_GOLEM": "em_boiler_golem", "WARDEN": "em_sculk_warden", "ENDERMAN": "em_brass_enderman",
    "RAVAGER": "em_ram_ravager", "WOLF": "em_cog_wolf", "RABBIT": "em_wind_rabbit", "POLAR_BEAR": "em_iron_bear",
    "ZOGLIN": "em_cinder_zoglin", "HORSE": "em_clockwork_horse", "PIG": "em_pipe_pig",
    "SPIDER": "em_brass_spider", "CAVE_SPIDER": "em_lantern_cave_spider", "SILVERFISH": "em_screw_silverfish", "ENDERMITE": "em_cog_endermite",
    "BLAZE": "em_furnace_blaze", "GHAST": "em_airship_ghast", "VEX": "em_imp_vex", "GUARDIAN": "em_submarine_guardian",
    "ELDER_GUARDIAN": "em_elder_submarine", "BREEZE": "em_whirl_breeze", "SHULKER": "em_vault_shulker",
    "CREEPER": "em_steam_creeper", "GOAT": "em_ram_goat", "HOGLIN": "em_forge_hoglin", "LLAMA": "em_cargo_llama",
    "BEE": "em_brass_bee", "PHANTOM": "em_gas_phantom", "PIGLIN_BRUTE": "em_iron_brute",
    "SLIME": "em_gear_slime", "MAGMA_CUBE": "em_magma_cube_furnace", "ENDER_DRAGON": "em_zeppelin_dragon", "WITHER": "em_forge_wither",
}

# EliteMobs NPC file (npcs/<key>.yml) -> model, used by assign_npcs.py
NPC_MODELS = {
    "back_teleporter": "em_npc_hermes", "barkeep": "em_npc_bartley", "blackjack_dealer": "em_npc_vincent", "blacksmith": "em_npc_greg",
    "card_shark": "em_npc_ace", "class_trainer_adventurer": "em_npc_trainer_adventurer", "class_trainer_berserker": "em_npc_trainer_berserker",
    "class_trainer_cleric": "em_npc_trainer_cleric", "class_trainer_paladin": "em_npc_trainer_paladin", "class_trainer_ranger": "em_npc_trainer_ranger",
    "class_trainer_spellcaster": "em_npc_trainer_spellcaster", "coin_flipper": "em_npc_lucky_pete", "combat_instructor": "em_npc_charles",
    "enchanted": "em_npc_eden", "fletcher": "em_npc_robin", "gambling_den_owner": "em_npc_baron", "guide_0": "em_npc_dux", "guide_1": "em_npc_casus",
    "guild_attendant": "em_npc_gillian", "quest_giver": "em_npc_qelthuzad", "repairman_config": "em_npc_reggie", "santa": "em_npc_saint_nick",
    "scrapper_config": "em_npc_kelly", "scroll_applier_config": "em_npc_scotty", "slot_machine_operator": "em_npc_spinner_sam",
}


def all_mobs():
    mobs = dict(me.MOBS)
    for mod in (elite_bipeds, elite_beasts, elite_floaters, elite_extra, elite_npcs):
        mobs.update(mod.MOBS)
    return mobs


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "../fmm-models/elitemobs"
    os.makedirs(out, exist_ok=True)
    mobs = all_mobs()
    missing = sorted(set(TYPE_TO_MODEL.values()) - set(mobs))
    if missing:
        raise SystemExit("no builder for: " + ", ".join(missing))
    for name, build in mobs.items():
        m = mk.Model(name)
        build(m)
        for needed in ("idle", "walk", "attack", "death"):
            assert any(a["name"] == needed for a in m.anims), (name, needed)
        with open(os.path.join(out, name + ".bbmodel"), "w", encoding="utf-8") as f:
            json.dump(m.bbmodel(), f)
    missing = sorted(set(NPC_MODELS.values()) - set(mobs))
    if missing:
        raise SystemExit("no builder for npc: " + ", ".join(missing))
    json.dump(TYPE_TO_MODEL, open(os.path.join(out, "models.json"), "w"), indent=1)
    json.dump(NPC_MODELS, open(os.path.join(out, "npc_models.json"), "w"), indent=1)
    print("wrote", len(mobs), "models to", out)


if __name__ == "__main__":
    main()
