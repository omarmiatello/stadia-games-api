# stadia-games-api

Open `data/games.json` - [View in GitHub](data/games.json) - [GitHub Raw](https://raw.githubusercontent.com/omarmiatello/stadia-games-api/main/data/games.json)

Example:
```json
{
  "api_version": 1,
  "count": 376,
  "games": [
    {
      "title": "A Place for the Unwilling",
      "img": "https://lh3.googleusercontent.com/3UbNVwfUXXEytzwlCgI7xDimGT_LcerieF_-HVKiCkIwKyRwWLQ1XiPymCd8APVFK9t4LaaRgNeb3KgNt9_xLlkcdfvs4SdKF5d7ROH-Rs-UNZp74aba1btCZbI\u003dw300-h400-rw-no-v1-nu"
    },
    // ...
    {
      "title": "Assassin\u0027s Creed Valhalla",
      "img": "https://lh3.googleusercontent.com/7al3Mqv3RkYu-v4DL9vhiWHfsMLpc_IHHpthd4r5KlwBQohCCND1jac6Bsw9tjuIBpWA0M1f2S7plurbPWkrrfa16bBGD3kyBs35gASW94ecVHvLFgs0E7CkyiPk\u003dw300-h400-rw-no-v1-nu",
      "button": "Play for 120 min"
    },
    // ...
  ]
}
```

Kotlin data classes:

```kotlin
data class Game(
    val title: String,
    val img: String,
    val button: String?,
)

data class GameListResponse(
    val api_version: Int,
    val count: Int,
    val games: List<Game>,
)
```

