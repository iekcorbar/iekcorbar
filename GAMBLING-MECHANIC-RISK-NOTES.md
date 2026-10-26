# Risk notes: purchase-based chance mechanic ("EliteMobs gambling")

**This is a risk-analysis note written by an AI assistant, not legal advice, and has not been reviewed by a lawyer.** Dutch gambling law carries real regulatory and (in serious cases) criminal exposure — if this mechanic is significant to the server, or ever changes shape, get an actual opinion from a lawyer familiar with Dutch gaming/gambling law (Wet op de kansspelen) before relying on anything below. This note exists so the reasoning and open questions survive, not to replace that review.

Not part of the deployed site — kept at the repo root purely as an internal note for staff/dev reference.

## What the mechanic is

Per the operator's own description: an in-game, purchase-style mechanic (not built into this website — it lives in the Minecraft server / plugin side) where a player spends something of in-game value (in-game currency, or similar) to receive a randomized reward. That's a wager-for-random-prize pattern, structurally the same shape as a loot box.

## Relevant framework

The Dutch **Wet op de kansspelen** (Gambling Act), enforced by the **Kansspelautoriteit (Ksa)**, prohibits offering a game of chance without a licence where all of these are present:
1. A game,
2. Chance (not skill) determines the outcome,
3. Players pay a **stake of value** to participate,
4. A **prize** is awarded.

Element 3 — whether what's being staked has "value" in the legally relevant sense — is where loot-box-style mechanics in games have actually been contested across the EU (the Ksa's 2018 loot box research, Belgium's 2018 loot-box ban, ongoing EU-level attention). The common thread in enforcement action has been mechanics where **real money** is either spent to acquire the stake, or can be recovered by cashing out / reselling the prize (an actual secondary market with real value).

## Facts that matter here, confirmed with the operator

- **No real-money purchase path exists anywhere** for the currency involved — not on this website (verified: no Stripe/PayPal/iDEAL/any payment integration exists in this codebase), and not through any donation shop, Tebex, Discord bot, or other channel outside this codebase either (operator confirmed, 2026-09-07).
- The currency being wagered is earned **only by playing** the server.
- No cash-out or official resale mechanism exists.

## Why this meaningfully reduces the risk — and what would change that

A closed-loop currency — earned only in-game, spendable only in-game, with no way in or out via real money — is the fact pattern regulators and courts have generally treated as ordinary game design rather than a licensable game of chance, because the "stake of value" element is the one actually in question, not the presence of randomness (nearly every game has RNG somewhere). This is a materially different position from, say, a loot box bought with a real-money currency, or an item that can be sold on a real marketplace.

That conclusion would need revisiting if any of the following becomes true later:
- A real-money path is added to *acquire* the wagered currency (a donation perk, a Tebex rank that grants it, etc.) — even indirectly.
- Players can trade the currency or its rewards for something of real value, including an unofficial player-run secondary market (e.g. selling rare drops for real money in Discord) that the server tolerates or is aware of.
- The mechanic's own presentation leans into gambling framing — language like "casino," "jackpot," "bet," "gambling" in the wiki or in-game descriptions increases regulatory attention even where the underlying mechanic is fine, because labeling is itself a factor Ksa guidance looks at. Framing it as a risk/reward loot feature rather than "gambling" is a cheap, real mitigation.
- The player base is understood to skew toward minors specifically *because* of this mechanic (as opposed to Minecraft servers generally) — not itself dispositive for the Kansspelwet's stake-based test, but relevant to how it'd be perceived if ever scrutinized, and to the age-related ToS clause below.

## What's already been done to reduce exposure

The new `/terms` page's section 3 states explicitly, in writing: none of the site's currencies or items have real-world value, none can be bought or sold for real money, they're earned only by playing, and nothing on the site is a game of chance for real-world stakes. That's a standard, real mitigation used by games with chance-based virtual-item mechanics — it doesn't make a genuinely gambling-shaped mechanic legal, but for a mechanic that's already closed-loop like this one, it documents the operator's own position clearly.

The Terms of Service also now carries an 18+ eligibility clause (disclaimer-only, not technically enforced — the operator's explicit choice, since the actual player base is not verified by age at registration).

## Bottom line

Based on the facts above, this reads as low regulatory risk under the Kansspelwet specifically because there is no real-money nexus anywhere in the loop. It is not a guarantee, the analysis is only as good as the "no real-money path, ever, anywhere" fact holding true, and it does not cover other countries' gambling law if the player base is meaningfully international — get a real legal read if this mechanic grows in importance, changes shape, or a real-money path is ever considered.
