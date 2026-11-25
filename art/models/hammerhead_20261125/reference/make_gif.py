"""Convert a rendered animation WebP loop into a GitHub-friendly GIF."""
import argparse
from pathlib import Path

from PIL import Image


def convert(source, output, width):
    loop = Image.open(source)
    frames = []
    durations = []
    for index in range(loop.n_frames):
        loop.seek(index)
        frame = loop.convert("RGB")
        height = round(frame.height * width / frame.width)
        frames.append(frame.resize((width, height), Image.LANCZOS))
        durations.append(loop.info.get("duration", 50))
    frames[0].save(output, save_all=True, append_images=frames[1:], duration=durations, loop=0, optimize=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--width", type=int, default=540)
    arguments = parser.parse_args()
    convert(arguments.source, arguments.output, arguments.width)
