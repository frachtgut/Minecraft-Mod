"""Shared pixel-art helpers for the Starfallen texture generators."""
import math
import random

from PIL import Image


def clamp(v, lo=0, hi=255):
    return max(lo, min(hi, int(round(v))))


def lerp(a, b, t):
    return a + (b - a) * t


def mix(c1, c2, t):
    """Blend two RGB(A) tuples."""
    n = max(len(c1), len(c2))
    c1 = tuple(c1) + (255,) * (n - len(c1))
    c2 = tuple(c2) + (255,) * (n - len(c2))
    return tuple(clamp(lerp(a, b, t)) for a, b in zip(c1, c2))


def ramp(colors, t):
    """Sample a colour ramp at t in [0, 1]."""
    t = max(0.0, min(0.9999, t))
    f = t * (len(colors) - 1)
    i = int(f)
    return mix(colors[i], colors[i + 1], f - i)


def shade(c, f):
    return tuple(clamp(v * f) for v in c[:3]) + tuple(c[3:])


def rgba(c, a=255):
    return (c[0], c[1], c[2], a if len(c) < 4 else c[3])


class Noise:
    """Deterministic 2D value noise with fractal sum."""

    def __init__(self, seed):
        self.seed = seed

    def _h(self, x, y):
        h = (x * 374761393 + y * 668265263 + self.seed * 1442695041) & 0xFFFFFFFF
        h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
        return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0

    def value(self, x, y):
        xi, yi = math.floor(x), math.floor(y)
        fx, fy = x - xi, y - yi
        sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        a = self._h(xi, yi)
        b = self._h(xi + 1, yi)
        c = self._h(xi, yi + 1)
        d = self._h(xi + 1, yi + 1)
        return lerp(lerp(a, b, sx), lerp(c, d, sx), sy)

    def fbm(self, x, y, octaves=3):
        v, amp, freq, total = 0.0, 1.0, 1.0, 0.0
        for _ in range(octaves):
            v += self.value(x * freq, y * freq) * amp
            total += amp
            amp *= 0.5
            freq *= 2.0
        return v / total


class Canvas:
    """A tiny RGBA canvas with pixel-art drawing helpers."""

    def __init__(self, w, h, fill=(0, 0, 0, 0)):
        self.w, self.h = w, h
        self.img = Image.new("RGBA", (w, h), fill)
        self.px = self.img.load()

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[x, y] = rgba(c)

    def get(self, x, y):
        return self.px[x, y]

    def blend(self, x, y, c, a):
        if 0 <= x < self.w and 0 <= y < self.h:
            old = self.px[x, y]
            if old[3] == 0:
                self.px[x, y] = (c[0], c[1], c[2], clamp(255 * a))
            else:
                self.px[x, y] = mix(old, rgba(c), a)

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.set(x, y, c)

    def line(self, x0, y0, x1, y1, c):
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        while True:
            self.set(x0, y0, c)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy

    def paste(self, other, x, y):
        self.img.alpha_composite(other.img, (x, y))

    def save(self, path):
        self.img.save(path)


def from_art(rows, palette):
    """Build a canvas from ASCII art rows using a char -> colour palette ('.' = transparent)."""
    h = len(rows)
    w = max(len(r) for r in rows)
    cv = Canvas(w, h)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in palette and palette[ch] is not None:
                cv.set(x, y, palette[ch])
    return cv


def outline(cv, color, only_transparent=True):
    """Add a 1px outline around opaque pixels."""
    src = [[cv.get(x, y) for x in range(cv.w)] for y in range(cv.h)]
    for y in range(cv.h):
        for x in range(cv.w):
            if src[y][x][3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < cv.w and 0 <= ny < cv.h and src[ny][nx][3] > 0:
                    cv.set(x, y, color)
                    break


def random_walk_cracks(cv, rng, count, length, color, glow=None, bounds=None):
    x0, y0, x1, y1 = bounds or (0, 0, cv.w, cv.h)
    for _ in range(count):
        x = rng.randint(x0, max(x0, x1 - 1))
        y = rng.randint(y0, max(y0, y1 - 1))
        for _ in range(length):
            if x0 <= x < x1 and y0 <= y < y1:
                cv.set(x, y, color)
                if glow is not None:
                    glow.set(x, y, color)
            d = rng.random()
            if d < 0.4:
                x += rng.choice((-1, 1))
            elif d < 0.8:
                y += rng.choice((-1, 1))
            else:
                x += rng.choice((-1, 1))
                y += rng.choice((-1, 1))
