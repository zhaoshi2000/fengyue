"""Small stdout bridge for the Edge online speech service."""

import asyncio
import sys

import edge_tts


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
    asyncio.run(synthesize())
