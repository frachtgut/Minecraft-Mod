"""Procedural helpers that produce ASCII sprite rows for tool heads drawn along the handle diagonal."""
import math


def blank():
    return [["."] * 16 for _ in range(16)]


def handle(g, t0=1, t1=12):
    for t in range(t0, t1 + 1):
        x, y = t, 15 - t
        g[y][x] = "H"
        if y + 1 < 16:
            if g[y + 1][x] == ".":
                g[y + 1][x] = "h"


def outline(g):
    src = [row[:] for row in g]
    for y in range(16):
        for x in range(16):
            if src[y][x] != ".":
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and src[ny][nx] not in ".o":
                    g[y][x] = "o"
                    break


def rows(g):
    return ["".join(r) for r in g]


def axe():
    g = blank()
    handle(g, 1, 11)
    ux, uy = 1 / math.sqrt(2), -1 / math.sqrt(2)      # along the handle (towards the head)
    px, py = -1 / math.sqrt(2), -1 / math.sqrt(2)     # perpendicular, towards the upper-left
    ox, oy = 10.0, 5.0                                # where the head grips the handle
    for y in range(16):
        for x in range(16):
            dx, dy = x - ox, y - oy
            a = dx * ux + dy * uy
            b = dx * px + dy * py
            if 0.3 <= b <= 5.2 and abs(a - 0.4) <= 1.3 + 0.55 * b:
                edge = b > 4.3
                shade = "5" if edge else "4" if b > 3.2 else "3" if b > 1.8 else "2"
                if a < -1.2 and not edge:
                    shade = "2"
                g[y][x] = shade
    outline(g)
    return rows(g)


def shovel():
    g = blank()
    handle(g, 1, 9)
    cx, cy = 11.5, 3.5
    ux, uy = 1 / math.sqrt(2), -1 / math.sqrt(2)      # along handle
    vx, vy = 1 / math.sqrt(2), 1 / math.sqrt(2)       # across
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            a = dx * ux + dy * uy
            b = dx * vx + dy * vy
            if (a / 3.3) ** 2 + (b / 2.4) ** 2 <= 1.0:
                shade = "5" if b < -1.2 else "4" if b < 0 else "3" if b < 1.2 else "2"
                g[y][x] = shade
    outline(g)
    return rows(g)


def tether():
    g = blank()
    # rope: a gentle wave from the grapple down to the lower-left
    pts = []
    for i in range(0, 41):
        t = i / 40.0
        x = 9.2 - t * 8.0
        y = 7.0 + t * 7.6 + math.sin(t * math.pi * 2.2) * 1.3
        pts.append((int(round(x)), int(round(y))))
    for (x, y) in pts:
        if 0 <= x < 16 and 0 <= y < 16:
            g[y][x] = "3"
    # grapple star
    cx, cy, ro, ri = 11.5, 4.0, 4.2, 1.7
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            ang = math.atan2(dy, dx) + math.pi / 2
            r = math.hypot(dx, dy)
            k = (ang % (2 * math.pi / 5)) / (2 * math.pi / 5)
            edge = ri + (ro - ri) * (1 - abs(k - 0.5) * 2) if False else None
            # distance to the star outline: interpolate radius between spikes
            frac = abs(((ang / (2 * math.pi / 5)) % 1.0) - 0.5) * 2    # 0 at spike, 1 between spikes
            limit = ro * (1 - frac) + ri * frac
            if r <= limit:
                g[y][x] = "5" if r < 1.2 else "4" if r < 2.4 else "3"
    outline(g)
    return rows(g)
