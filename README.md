# DiscordBot

Minimal JDA bot starter using Java 17 and Maven Wrapper.

## Prerequisites

- Java 17 installed
- No global Maven install required (project uses `./mvnw`)

## Local setup

1. Set your Java 17 runtime in shell:

```zsh
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"
```

2. Verify Java version:

```zsh
java -version
javac -version
```

3. Configure your token locally (never commit it):

```zsh
cp .env.example .env
export DISCORD_TOKEN="your_real_token_here"
```

## Build and run

```zsh
./mvnw -DskipTests compile
./mvnw -DskipTests exec:java -Dexec.mainClass=com.bot.Main
```

## Optional: global Maven

If you prefer global Maven, these equivalent commands also work:

```zsh
mvn -DskipTests compile
mvn -DskipTests exec:java -Dexec.mainClass=com.bot.Main
```

## IntelliJ run configuration

- Main class: `com.bot.Main`
- Environment variable: `DISCORD_TOKEN=...`
- Project SDK: Java 17
- Enable the Discord Server Members Intent for moderation commands.

## Security notes

- Do not paste your token into source files.
- Do not commit `.env` or secret config files.
- If token is ever exposed, rotate it in the Discord Developer Portal.


