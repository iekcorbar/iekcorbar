package com.iekcorbar.gouw;

/** The "Gouw" tab of the advancement screen. Action-based: advancements are granted from command handlers. */
final class Achievements {
    private Achievements() {
    }

    static Adv create(GouwPlugin plugin) {
        Adv a = new Adv(plugin, "gouw", "Gouw", "Claim land, build towns, share with friends.", "grass_block", "minecraft:block/stone_bricks", true);
        a.add("first_claim", null, "grass_block", "First Footing", "Claim your first chunk.", "task");
        a.add("full_town", "first_claim", "map", "Growing Settlement", "Claim 8 chunks.", "goal");
        a.add("maxed", "full_town", "diamond", "Land Baron", "Reach your chunk limit.", "challenge");
        a.add("trusted", "first_claim", "player_head", "Open Doors", "Trust a player in your gouw.", "task");
        a.add("officer", "trusted", "iron_sword", "Right Hand", "Promote a resident to officer.", "goal");
        a.add("banned", "first_claim", "barrier", "Not Welcome", "Ban a player from your gouw.", "task");
        a.add("renamed", "first_claim", "name_tag", "New Name, Same Land", "Rename your gouw.", "task");
        a.add("home", "first_claim", "bed", "Home Sweet Home", "Set a home inside your gouw.", "task");
        a.add("bank", "first_claim", "gold_ingot", "Town Treasury", "Deposit money into the town bank.", "task");
        a.add("bought", "bank", "emerald", "Expanding Borders", "Buy extra chunks from the bank.", "goal");
        a.add("transferred", "first_claim", "paper", "Passing the Torch", "Transfer your gouw to another player.", "goal");
        a.add("sold", "first_claim", "gold_nugget", "Sold!", "Sell your gouw for money.", "goal");
        a.add("rented", "first_claim", "clock", "Landlord", "Rent access to your gouw.", "task");
        a.add("top", "first_claim", "gold_block", "Top of the Charts", "Check the gouw leaderboard.", "task");
        return a;
    }
}