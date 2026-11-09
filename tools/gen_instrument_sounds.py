"""合成乐器音效（原创，不复制任何游戏/维基音频）。

- 小丑喇叭：两声音高下滑的鼻音「叭叭」，带颤音与少量气声。
- 吉他：Karplus-Strong 拨弦的六弦扫和弦（E2 A2 D3 G3 B3 E4）。

依赖：python -m pip install -r tools/requirements.txt
用法：python tools/gen_instrument_sounds.py
输出：src/main/resources/assets/barotrauma/sounds/instrument/{clown_horn,guitar}.ogg
"""

from pathlib import Path

import numpy as np
import soundfile as sf

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src" / "main" / "resources" / "assets" / "barotrauma" / "sounds" / "instrument"

FS = 44100
RNG = np.random.default_rng(20261109)


def _envelope(n: int, attack: float, release: float) -> np.ndarray:
    env = np.ones(n, dtype=np.float64)
    a = max(1, int(attack * FS))
    r = max(1, int(release * FS))
    env[:a] = np.linspace(0.0, 1.0, a)
    env[-r:] *= np.linspace(1.0, 0.0, r)
    return env


def _honk(duration: float, f_start: float, f_end: float, nasal: float) -> np.ndarray:
    n = int(duration * FS)
    t = np.arange(n) / FS
    # 音高下滑 + 6 Hz 颤音
    freq = np.linspace(f_start, f_end, n) + 10.0 * np.sin(2.0 * np.pi * 6.0 * t)
    phase = 2.0 * np.pi * np.cumsum(freq) / FS
    # 鼻音：谐波堆叠，偶次谐波更弱
    wave = np.zeros(n, dtype=np.float64)
    for k in range(1, 9):
        amp = (1.0 / k) * (1.0 if k % 2 == 1 else nasal)
        wave += amp * np.sin(k * phase)
    # 一点气声
    wave += 0.04 * RNG.standard_normal(n)
    # 简单一阶低通，削掉刺耳的高频
    out = np.empty_like(wave)
    prev = 0.0
    for i, value in enumerate(wave):
        prev += 0.35 * (value - prev)
        out[i] = prev
    return out * _envelope(n, 0.015, 0.08)


def clown_horn() -> np.ndarray:
    first = _honk(0.26, 320.0, 225.0, 0.55)
    second = _honk(0.30, 285.0, 190.0, 0.55)
    n = int(0.62 * FS)
    out = np.zeros(n, dtype=np.float64)
    out[: len(first)] += first
    offset = int(0.28 * FS)
    out[offset: offset + len(second)] += second
    return out


def _pluck(freq: float, duration: float, damping: float) -> np.ndarray:
    n = int(duration * FS)
    period = max(2, int(FS / freq))
    buf = RNG.uniform(-1.0, 1.0, period)
    out = np.empty(n, dtype=np.float64)
    idx = 0
    for i in range(n):
        current = buf[idx]
        nxt = buf[(idx + 1) % period]
        buf[idx] = 0.5 * (current + nxt) * damping
        out[i] = current
        idx = (idx + 1) % period
    return out * _envelope(n, 0.002, duration * 0.55)


def guitar() -> np.ndarray:
    notes = [82.41, 110.00, 146.83, 196.00, 246.94, 329.63]
    duration = 2.2
    n = int(duration * FS)
    out = np.zeros(n, dtype=np.float64)
    for i, freq in enumerate(notes):
        start = int(i * 0.028 * FS)
        pluck = _pluck(freq, duration - start / FS, 0.996)
        out[start: start + len(pluck)] += pluck / (1.0 + i * 0.15)
    return out


def guitar_note() -> np.ndarray:
    """单音拨弦（C4）：长按演奏时靠 pitch 参数变调成曲目。"""
    return _pluck(261.63, 1.1, 0.996)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    tracks = {"clown_horn": clown_horn(), "guitar": guitar(), "guitar_note": guitar_note()}
    for name, data in tracks.items():
        data = data / max(1e-9, np.max(np.abs(data))) * 0.85
        path = OUT / f"{name}.ogg"
        sf.write(str(path), data.astype(np.float32), FS, format="OGG", subtype="VORBIS")
        print(f"wrote {path} ({len(data) / FS:.2f}s)")


if __name__ == "__main__":
    main()
