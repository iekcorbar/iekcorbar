-- Seed "Tip of the Day" with tips from iekcorbar's own custom plugins and site
-- features (2026-10-26). Run ONCE in Plesk > databases > iekcorbar > SQL, like
-- shop-coins-migration.sql. Idempotent: fixed ids mean re-running changes
-- nothing; the two UPDATEs only fire while the old (pre-Gouw) wording is there.
-- Staff can toggle/delete any of these later via Settings > Tips without a redeploy.

INSERT IGNORE INTO daily_tips (id, content) VALUES
('seed_iek_gouw_1', 'Claim your builds: /gouw claim protects the chunk you stand in, /gouw trust <name> adds a friend, and the /gouw menu tunes exactly what members may do (build, open, mobs, pvp).'),
('seed_iek_gouw_2', 'Lost track of your land? /gouw list shows every chunk you own, /gouw near shows what''s around you and /gouw border draws your outline in particles.'),
('seed_iek_kura_1', 'Kura links your chests into one storage network: a single /kura terminal shows everything you own - searchable, with filters, hoppers and even wireless access.'),
('seed_iek_kitchen_1', 'Kitchen adds proper Japanese cooking: chop ingredients on a cutting board, simmer a cooking pot and serve animated meals - new crops included.'),
('seed_iek_sakagura_1', 'Brewing is a craft, not a gadget: build a cauldron, let it ferment in sake casks and watch quality roll - a great brew can even get you drunk.'),
('seed_iek_steamworks_1', 'Go kinetic with Steamworks: shafts, cogwheels, cranks, water wheels, millstones and steam hammers - real moving machines in steampunk style.'),
('seed_iek_wildlife_1', 'The herds beyond the vanilla animals are ours: Wildlife adds new species, a Field Journal to record them and its own advancement tab. Keepers can manage the /zoo too.'),
('seed_iek_zukan_1', 'Never remember recipes? /zukan is the built-in recipe browser - it knows every recipe of every iekcorbar plugin, and the website has the same browser on /recipes.'),
('seed_iek_satchel_1', 'A /satchel backpack rides with you in six tiers, with upgrade slots for pickup, magnet, feeding and everlasting - never haul chests across the map again.'),
('seed_iek_relics_1', 'The lore of iekcorbar is scattered across the world as Relics - placeable props from the story of the Guild. Around the seasons, gift boxes may even hand you something.'),
('seed_iek_site_ach', 'Website actions feed the achievement system: chatting, journal entries, brewing, shopping and voting all tick real advancements that pop up in-game the next time you join.'),
('seed_iek_site_app', 'Grab the iekcorbar Android app (or add the site to your iPhone home screen) and switch on push notifications under your profile - chat replies and events, delivered.'),
('seed_iek_site_news', 'Check News & Upcoming before you log in: staff post the roadmap and patch notes there, so the next season never surprises you.'),
('seed_iek_site_journal', 'Your in-game Field Journal and the website journal talk to each other - notes you make on the site show up as pages your fellow collectors can read.');

-- Vervang de twee verouderde Lands-tips (alleen zolang de oude tekst er nog staat):
UPDATE daily_tips SET content = 'Land claims support roles: members you trust can build and open, and the four rights (Build, Open, Mobs, Players) are individually toggleable per gouw.'
WHERE content LIKE 'Land claims support roles%' AND content LIKE '%Member and Co-Owner%';
