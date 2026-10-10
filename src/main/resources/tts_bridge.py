"""Small stdout bridge for the Edge online speech service."""

import asyncio
import sys

import edge_tts


async def list_voices() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    for voice in await edge_tts.list_voices():
        fields = (voice.get("ShortName", ""), voice.get("Locale", ""), voice.get("Gender", ""), voice.get("FriendlyName", ""))
        sys.stdout.write("\t".join(str(value).replace("\t", " ").replace("\n", " ") for value in fields) + "\n")
    sys.stdout.flush()


async def synthesize() -> None:
    voice = sys.argv[1]
    text = sys.stdin.buffer.read().decode("utf-8")
    received = False
    async for chunk in edge_tts.Communicate(text, voice).stream():
        if chunk["type"] == "audio":
            sys.stdout.buffer.write(chunk["data"])
            received = True
    if not received:
        raise RuntimeError("Edge TTS did not return audio")
    sys.stdout.buffer.flush()


if __name__ == "__main__":
    asyncio.run(list_voices() if len(sys.argv) > 1 and sys.argv[1] == "--voices" else synthesize())
