---
slug: sakagura-competitions
title: Brewing Competitions
category: Sakagura
parent: sakagura
---
## Brewing Competitions

From time to time, the server hosts a brewing competition. Players submit their best drinks, and the highest quality brews win prizes.

### How it works

1. **An admin starts a competition** with `/brew comp start <days> [theme]`. The theme is optional (e.g. "bloody_mary" for a themed competition, or "any" for all drinks).
2. **Players brew their best drink** and submit it while holding it: `/brew comp submit`.
3. **Each submission is scored** based on quality (1-10 stars) and difficulty (1-10). Score = quality × 10 + difficulty × 5.
4. **Only your best submission counts** — if you submit a better drink, it replaces your previous one.
5. **When the competition ends** (admin runs `/brew comp stop` or the timer runs out), the top 3 players receive rewards:
   - 1st place: $500 + 20 elite coins + "Master of the Still" title
   - 2nd place: $250 + 10 elite coins + "Skilled Brewer" title
   - 3rd place: $100 + 5 elite coins + "Rising Brewer" title
6. **The winner** also earns the "Master of the Still" advancement.

### Commands

| Command | Who | What |
|---|---|---|
| `/brew comp status` | Everyone | Show if a competition is running and time remaining |
| `/brew comp submit` | Everyone | Submit the drink you're holding |
| `/brew comp leaderboard` | Everyone | Show the current top 10 |
| `/brew comp start <days> [theme]` | Admin | Start a competition (1-30 days) |
| `/brew comp stop` | Admin | End the competition and give rewards |

### Tips

- **Quality matters most** — a 10-star drink scores 100 points, while a 5-star scores 50. Aim for perfect recipes.
- **Difficulty adds bonus** — harder recipes (difficulty 8-10) give up to 50 extra points.
- **Theme competitions** — when a theme is set, only that specific drink counts. This levels the playing field.
- **Practice first** — use `/brew recipes` to see all drinks and their difficulty, then brew the perfect one.
- **You can resubmit** — if you brew a better version, submit again. Only your best counts.
