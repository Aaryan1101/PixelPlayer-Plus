# PixelPlayer Discord Bot

PixelPlayer Bot is a separate Java 17 application. It does not package the Android app or the desktop app, and it does not use NewPipe Extractor. JDA handles Discord slash commands while a Lavalink 4 node handles voice playback and YouTube loading.

## Features

- `/play` accepts a song search, direct URL, or playlist URL
- Per-server queues with `/queue` and `/nowplaying`
- Related-song autoplay is enabled by default and continues when the manual queue ends
- `/autoplay` shows its status; `/autoplay enabled:true|false` changes it per server
- `/pause`, `/resume`, `/skip`, `/stop`, `/volume`, and `/leave`
- Only members in the bot's voice channel can control playback
- Automatic disconnect after a configurable idle period
- No privileged Message Content intent
- Credentials are read from environment variables or an ignored local `.env` file

## 1. Create the Discord application

1. Open the Discord Developer Portal and create an application.
2. Upload `discordBot/branding/pixelplayer-discord-icon.png` as both the application and bot icon.
3. On **Bot**, create the bot and copy its token. Do not paste the token into source control or chat.
4. Keep **Message Content Intent** disabled; this bot uses slash commands.
5. In **OAuth2 > URL Generator**, select `bot` and `applications.commands`.
6. Grant only **View Channels**, **Connect**, and **Speak**, then use the generated URL to invite it.

For fast command updates during development, copy the server ID and set `DISCORD_GUILD_ID`. Remove it for global command registration when publishing.

## 2. Configure Lavalink

Copy the examples without committing the copies:

```bash
cp discordBot/lavalink/application.yml.example discordBot/lavalink/application.yml
cp discordBot/.env.example discordBot/.env
```

Put the same long random value in `LAVALINK_PASSWORD` for both processes. The included configuration disables Lavalink's removed built-in YouTube source and installs the maintained `youtube-source` plugin.

If Docker or Podman Compose is available:

```bash
cd discordBot/lavalink
LAVALINK_PASSWORD='your-random-password' docker compose up -d
```

The node is bound to `127.0.0.1:2333`, so it is not exposed to other computers. Its Java heap is capped at 512 MB for an 8 GB development machine.

## 3. Configure and run the bot

Edit `discordBot/.env`:

```dotenv
DISCORD_BOT_TOKEN=your-token
DISCORD_GUILD_ID=your-test-server-id
LAVALINK_URI=ws://localhost:2333
LAVALINK_PASSWORD=your-random-password
BOT_IDLE_MINUTES=5
```

Then run only the bot module with conservative Gradle limits:

```bash
./gradlew :discordBot:run --no-daemon --max-workers=1 --no-parallel
```

This command does not compile the Android or desktop applications. The bot JVM is capped at 256 MB by its Gradle application configuration. Stop it with `Ctrl+C`.

## Deployment notes

For continuous use, run the bot and Lavalink on an always-on server rather than a laptop. Keep port 2333 private, use a unique Lavalink password, and inject both secrets through the host's secret/environment settings. One Lavalink node can serve the bot in many Discord servers; the bot maintains a separate queue per server.

YouTube playback can change when YouTube changes its clients. Update the `youtube-source` plugin independently of the Android NewPipe dependency when that happens.

Autoplay first requests the finished video's YouTube Mix and rejects recently played video IDs. If a Mix is unavailable, it performs a related artist/title search. Manually queued tracks always play before an autoplay selection.
