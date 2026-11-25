"""Retime the pump loop without touching the original model rig or spin clip."""
import argparse
import base64
from copy import deepcopy
from hashlib import sha256
from io import BytesIO
import json
import math
from pathlib import Path
from uuid import NAMESPACE_URL, uuid5

from PIL import Image

from blockbench_delivery import DEST, NAME, load_adapter

REFERENCE = DEST / "reference"
PUMP = f"animation.{NAME}.running"
SPIN = f"animation.{NAME}.rotor_spin"
BEAT = 1.2
LOOP = 2 * BEAT
TIME_SCALE = .75
AMPLITUDE = .75
ROTORS = {"rotor", "inner_rotor"}
PROBES = ["pulse_1_09", "pulse_1_05_part_2", "pulse_1_02", "pulse_2_09", "pulse_2_05_part_2", "pulse_2_02"]


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def repeated_beats(frames):
    keys = []
    for beat_index in range(2):
        for frame in frames:
            if beat_index and frame["time"] == 0:
                continue  # The first beat's end is the second beat's start.
            updated = deepcopy(frame)
            updated["time"] = round(frame["time"] * TIME_SCALE + BEAT * beat_index, 5)
            if beat_index:
                updated["uuid"] = str(uuid5(NAMESPACE_URL, "engine-r15/" + frame["uuid"]))
            identity = 1 if frame["channel"] == "scale" else 0
            for point in updated["data_points"]:
                for axis, value in point.items():
                    amount = round(identity + (float(value) - identity) * AMPLITUDE, 5)
                    point[axis] = str(amount)
            keys.append(updated)
    return keys


def retain_rotation_speed(frames):
    keys = deepcopy(sorted(frames, key=lambda frame: frame["time"]))
    assert len(keys) == 9 and keys[-1]["time"] == 1.6
    quarter_turn = float(keys[1]["data_points"][0]["x"])
    for index in range(9, 13):
        updated = deepcopy(keys[-1])
        updated["uuid"] = str(uuid5(NAMESPACE_URL, "engine-r15/spin/" + keys[0]["uuid"] + f"/{index}"))
        updated["time"] = round(index * .2, 5)
        updated["data_points"][0]["x"] = str(index * quarter_turn)
        keys.append(updated)
    return keys


def retime(animation):
    original_louvers = [uid for uid, track in animation["animators"].items()
                        if track["name"] == "louver_access_doors"]
    assert len(original_louvers) == 1
    del animation["animators"][original_louvers[0]]
    animation["length"] = LOOP
    for track in animation["animators"].values():
        frames = track["keyframes"]
        if track["name"] in ROTORS:
            track["keyframes"] = retain_rotation_speed(frames)
        else:
            track["keyframes"] = repeated_beats(frames)


def validate_motion(result):
    checks = result["checks"]
    assert len(checks) == 129
    start, middle, end = (checks[i] for i in (0, 64, 128))
    assert max(start["motion"],end["motion"]) < 1e-5
    assert max(sample["fixed"] for sample in checks) < 1e-7
    assert abs(middle["roof_top"] - start["roof_top"]) < 1e-5
    assert middle["motion"] > 1, "Rotors must pass through the middle seam continuously."
    lift_peaks, wave_peaks = [], []
    for samples in (checks[:65], checks[64:]):
        lift_peaks.append(max(row["roof_top"] for row in samples) - start["roof_top"])
        peaks = {name: max(samples, key=lambda row: row["run"][name])["time"] for name in PROBES}
        rows = [[peaks[PROBES[line*3+i]] for i in range(3)] for line in range(2)]
        assert all(r[0] < r[1] < r[2] for r in rows)
        assert all(a < b for a, b in zip(*rows))
        wave_peaks.append({name: round(value, 5) for name, value in peaks.items()})
    assert all(1.25 < lift < 1.45 for lift in lift_peaks), lift_peaks
    assert abs(lift_peaks[0] - lift_peaks[1]) < 1e-5
    for sample in checks:
        for i, ((x0,y0,z0),(x,y,z)) in enumerate(zip(result["rotorStart"],sample["blades"])):
            angle = math.atan2(y0*z-z0*y, y0*y+z0*z)
            expected = (-1 if i == 0 else 1) * math.tau * sample["time"] / .8
            assert abs(math.remainder(angle - expected, math.tau)) < 1e-5, (sample["time"],angle,expected)
            assert abs(x - x0) < 1e-5
    return lift_peaks, wave_peaks


def save_gif(data):
    frames = []
    for pair in data:
        images = [Image.open(BytesIO(base64.b64decode(png))).convert("RGB") for png in pair]
        composite = Image.new("RGB", (sum(im.width for im in images) + 2, images[0].height), (36,44,49))
        composite.paste(images[0], (0,0))
        composite.paste(images[1], (images[0].width+2,0))
        frames.append(composite)
    assert len(frames) == 48
    strip = Image.new("RGB", (frames[0].width, frames[0].height * 8))
    for i, frame in enumerate(frames[::6]):
        strip.paste(frame, (0,frames[0].height*i))
    palette = strip.quantize(colors=256, dither=Image.Dither.NONE)
    images = [frame.quantize(palette=palette, dither=Image.Dither.NONE) for frame in frames]
    path = DEST / "preview_running.gif"
    images[0].save(path, save_all=True, append_images=images[1:], duration=50, loop=0, optimize=False)
    with Image.open(path) as gif:
        assert gif.n_frames == 48 and gif.info["loop"] == 0
        assert sum((gif.seek(i),gif.info["duration"])[1] for i in range(gif.n_frames)) == 2400


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adapter", type=Path, required=True)
    args = parser.parse_args()
    baseline = REFERENCE / "user_baseline.bbmodel"
    model = read_json(baseline)
    running, spin = model["animations"]
    assert running["name"] == PUMP and spin["name"] == SPIN
    assert running["length"] == 1.6 and running["selected"] and not spin["selected"]
    retime(running)
    project = DEST / f"{NAME}.bbmodel"
    texture = DEST / f"{NAME}_tex.png"
    project.write_text(json.dumps(model, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    texture.write_bytes(base64.b64decode(model["textures"][0]["source"].split(",", 1)[1]))
    adapter = load_adapter(args.adapter)
    with adapter.Blockbench() as app:
        native = app.open(project)
        assert not native["problems"]
        app.export("bedrock", DEST / f"{NAME}.geo.json")
        result = app.js((REFERENCE / "preview_running.js").read_text(encoding="utf-8-sig"),
                        run=PUMP, duration=LOOP, probes=PROBES)
    assert result["reimported"] == result["compiled"]
    clips = result["compiled"]["animations"]
    assert [*clips] == [PUMP, SPIN] and "louver_access_doors" not in clips[PUMP]["bones"]
    assert clips[PUMP]["animation_length"] == LOOP and clips[PUMP]["loop"]
    lift, peaks = validate_motion(result)
    (DEST / f"{NAME}.animation.json").write_text(
        json.dumps(result["compiled"], indent=2) + "\n", encoding="utf-8"
    )
    save_gif(result["frames"])
    report = {
        "revision": "R15",
        "baseline_sha256": sha256(baseline.read_bytes()).hexdigest(),
        "blockbench": native["version"], "bounds_bb": native["box"], "cubes": native["cubes"], "bones": native["groups"],
        "animated_clip": PUMP, "pump_beat_seconds": BEAT, "shared_loop_seconds": LOOP,
        "shell_and_cable_deviation_ratio_vs_r14": AMPLITUDE,
        "sampled_shell_peak_lift_bb": [round(x, 4) for x in lift],
        "sampled_cable_peaks_seconds": peaks,
        "sampled_times": len(result["checks"]),
        "stationary_louvers_and_fixed_parts": True,
        "rotor_revolution_seconds": .8,
        "native_animation_roundtrip": True,
        "minecraft_runtime": "not tested",
    }
    (DEST / "validation.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(report)


if __name__ == "__main__":
    main()
