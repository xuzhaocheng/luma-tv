#!/usr/bin/env python3
"""生成拾光桌面的应用图标:深色圆角方块 + 一轮升起的暖色光晕。"""
import struct
import zlib
import math
import os

BG = (0x0B, 0x0E, 0x13)
SUN_HI = (0xFF, 0xC2, 0x6B)
SUN_LO = (0xF0, 0x8A, 0x3C)
HORIZON = (0x0B, 0x0E, 0x13)

SS = 4  # 超采样倍数


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def rounded_alpha(x, y, size, radius):
    """点 (x,y) 是否在圆角矩形内,返回 0/1"""
    r = radius
    cx = min(max(x, r), size - r)
    cy = min(max(y, r), size - r)
    dx = x - cx
    dy = y - cy
    return 1 if dx * dx + dy * dy <= r * r else 0


def make(size):
    big = size * SS
    radius = big * 0.22
    sun_cx, sun_cy = big * 0.5, big * 0.60
    sun_r = big * 0.235
    horizon_y = big * 0.615

    # 超采样缓冲区 RGB + 覆盖率
    acc = [[[0, 0, 0, 0] for _ in range(size)] for _ in range(size)]

    for by in range(big):
        for bx in range(big):
            if not rounded_alpha(bx, by, big, radius):
                continue
            dx = bx - sun_cx
            dy = by - sun_cy
            d = math.sqrt(dx * dx + dy * dy)

            color = BG
            if d <= sun_r and by < horizon_y:
                # 太阳内部:上亮下暖
                t = max(0.0, min(1.0, (by - (sun_cy - sun_r)) / (2 * sun_r)))
                color = lerp(SUN_HI, SUN_LO, t)
            elif d <= sun_r + big * 0.035 and by < horizon_y:
                # 外圈光晕,淡一点
                color = lerp(BG, SUN_HI, 0.35)

            px, py = bx // SS, by // SS
            acc[py][px][0] += color[0]
            acc[py][px][1] += color[1]
            acc[py][px][2] += color[2]
            acc[py][px][3] += 1

    n = SS * SS
    rows = []
    for y in range(size):
        row = bytearray()
        for x in range(size):
            r, g, b, c = acc[y][x]
            if c == 0:
                row += bytes((0, 0, 0, 0))
            else:
                row += bytes((r // c, g // c, b // c, c * 255 // n))
        rows.append(bytes(row))
    return rows


def write_png(path, size, rows):
    raw = b''.join(b'\x00' + r for r in rows)

    def chunk(tag, data):
        c = struct.pack('>I', len(data)) + tag + data
        return c + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)

    ihdr = struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0)
    png = (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', ihdr)
           + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))
    with open(path, 'wb') as f:
        f.write(png)


SIZES = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}
base = 'app/src/main/res'
for dens, px in SIZES.items():
    d = os.path.join(base, 'mipmap-' + dens)
    os.makedirs(d, exist_ok=True)
    write_png(os.path.join(d, 'ic_launcher.png'), px, make(px))
    print('wrote', d, px)
