# stadia-games-api

## Games list:
- Open `data/games.json` [View in GitHub](data/games.json)
- Open `data/games.json` [GitHub Raw](https://raw.githubusercontent.com/omarmiatello/stadia-games-api/main/data/games.json)
- Full list for users with a Stadia account: https://stadia.google.com/store/list/3
- Full list for users without a Stadia account: https://stadia.google.com/games

## Features
- Daily auto update of this repository
- Telegram notification if there are new games

## Upcoming features
- Telegram notification if there are new timed demo
- ...

## Notes
- This repository helped me (by accident) to discover new timed demos - https://www.reddit.com/r/Stadia/comments/vjypss/35_new_timed_demos_were_released_today/

## Format example:
### Json

```json
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
