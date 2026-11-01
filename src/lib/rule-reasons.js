// Why each server rule exists. Matched on keywords in the rule title, so it keeps working
// when staff reword the rule itself. If staff add their own line starting with "Why:" to a
// rule's text (Staff Panel -> Settings -> Rules), that line wins over the default below.
const REASONS = [
  [/kind|respect/i, "Iekcorbar is a small, long-running community. People only stay and build together when it feels safe, and one toxic player can drive away ten good ones."],
  [/farm|grind/i, "The economy, the RPG progression and the loot are balanced around people actually playing. Infinite rewards from AFK machines devalue everyone else's work and make the server grind to a halt."],
  [/claim/i, "Land is limited. Over-claiming locks other players out of areas and ruins dungeons and structures that everyone should be able to explore."],
  [/elitemobs/i, "Bosses and dungeons are the main adventure content. Cheesing or farming them skips the challenge and floods the server with loot and coins."],
  [/betterstructures|structures/i, "Structures are meant to be found and explored once. Looping the same one gives endless loot and spoils the surprise for everyone else."],
  [/pyro|fishing|mining/i, "Fishing and mining rewards are tuned for active play. Automation turns them into free money and unbalances the economy."],
  [/brewing|garden/i, "Drinks and crops are crafted slowly on purpose: they are part of the economy and the seasonal events. Mass production would make them worthless."],
  [/grave/i, "Dying already costs you time. Camping a grave to take someone's items is a cheap way to ruin their evening."],
  [/quest/i, "Quest rewards are meant to be earned once per person. Repeating them or using extra accounts hands out unfair rewards."],
  [/shop/i, "Player shops only work on trust. Scams, price rigging and duping break that trust and damage the whole economy."],
  [/bedwars/i, "A match is only fun when everyone plays fair. Teaming up or abusing the map ruins the game for the other players."],
  [/discord|social/i, "Discord is where the community lives and where many members are young. We keep it safe, on topic and free of spam and ads."],
  [/mod|client/i, "Hacked clients and tricks like x-ray or autoclickers give an unfair advantage and make it impossible to keep the server fair."],
  [/alt/i, "Extra accounts are the usual way to cheat rewards or to dodge a ban, so they are not allowed for those purposes."],
  [/punish/i, "Consequences have to be predictable. Staff pick the lightest option that fixes the problem, and escalate only when needed."],
  [/final/i, "Rules can never cover every case. Asking first costs you ten seconds; guessing wrong can cost you an item, a build or an account."],
];

export function ruleReason(title) {
  const t = String(title || '');
  for (const [re, why] of REASONS) if (re.test(t)) return why;
  return '';
}

// A rule's own "Why: ..." line (set by staff) takes priority over the default.
export function splitWhy(content) {
  const lines = String(content || '').split('\n');
  let why = '';
  const rest = lines.filter((l) => {
    const m = l.trim().match(/^\*{0,2}why\s*:\*{0,2}\s*(.+)$/i);
    if (m) { why = m[1].trim(); return false; }
    return true;
  });
  return { body: rest.join('\n'), why };
}

export function whyFor(rule) {
  const { body, why } = splitWhy(rule.content);
  return { body, why: why || ruleReason(rule.title) };
}
