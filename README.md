# stadia-games-api

## Data source
- Full public list: https://stadia.google.com/games (used by this bot)
- Full list for registered users on Stadia: https://stadia.google.com/store/list/3

## Features
- `data/games.json` - Full list in Json: [View in GitHub](data/games.json) or [GitHub Raw](https://raw.githubusercontent.com/omarmiatello/stadia-games-api/main/data/games.json)
- `data/games.md` - Full list in Markdown: [View in GitHub](data/games.md) or [GitHub Raw](https://raw.githubusercontent.com/omarmiatello/stadia-games-api/main/data/games.md)
- `data/changelog-2022.md` - Changelog by year in Markdown: [View in GitHub](data/changelog-2022.md) or [GitHub Raw](https://raw.githubusercontent.com/omarmiatello/stadia-games-api/main/data/changelog-2022.md)
- Daily automatic update of this repository
- Notification: If there are new games
- Notification: If there are new demos

## Upcoming features
- Add Twitter support

## Notes
- This repository helped me (by accident) to discover new timed demos - https://www.reddit.com/r/Stadia/comments/vjypss/35_new_timed_demos_were_released_today/

## Format example:
### Json
```json5
{
  "api_version": 1,
  "count": 376,
  "games": [
    {
      "title": "A Place for the Unwilling",
      "url": "https://stadia.google.com/game/a-place-for-the-unwilling",
      "img": "https://lh3.googleusercontent.com/4YcX0ToJSlermUr3ZjVEG3g2-RNVD8X4KFVlLjHiQdl6jYC8bWVyZRlpYpY4LUB_PcmX6C4x7-DlmDUjDbiEm0aWHUfCqqJli-x4aJT1Lujle0vcRuXJDvZdTic\u003dw300-h400-rw-no-v1-nu"
    },
    // ...
    {
      "title": "Assassin\u0027s Creed Valhalla",
      "url": "https://stadia.google.com/game/assassins-creed-valhalla",
      "img": "https://lh3.googleusercontent.com/7al3Mqv3RkYu-v4DL9vhiWHfsMLpc_IHHpthd4r5KlwBQohCCND1jac6Bsw9tjuIBpWA0M1f2S7plurbPWkrrfa16bBGD3kyBs35gASW94ecVHvLFgs0E7CkyiPk\u003dw300-h400-rw-no-v1-nu",
      "button": "Play for 120 min"
    },
    // ...
  ]
}
```

### Kotlin data classes
```kotlin
data class Game(
    val title: String,
    val url: String,
    val img: String,
    val button: String?,
)

data class GameListResponse(
    val api_version: Int,
    val count: Int,
    val games: List<Game>,
)
```
