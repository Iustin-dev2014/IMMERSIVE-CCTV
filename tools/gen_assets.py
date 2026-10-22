import os, json, zlib, struct, random, math

random.seed(2024)
MODID = 'immersivecctv'
RES = os.path.join('src', 'main', 'resources')
ASSETS = os.path.join(RES, 'assets', MODID)
DATA = os.path.join(RES, 'data', MODID)


def clamp(v):
    return max(0, min(255, int(v)))


def ensure(path):
    d = os.path.dirname(path)
    if d:
        os.makedirs(d, exist_ok=True)


def wjson(path, obj):
    ensure(path)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=2)


def wtext(path, text):
    ensure(path)
    with open(path, 'w') as f:
        f.write(text)


class Img:
    def __init__(self, w, h, base=(0, 0, 0), alpha=255):
        self.w = w
        self.h = h
        self.px = [[base[0], base[1], base[2], alpha] for _ in range(w * h)]

    def put(self, x, y, c):
        x = int(x)
        y = int(y)
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = [clamp(c[0]), clamp(c[1]), clamp(c[2]), 255]

    def blend(self, x, y, c, f):
        x = int(x)
        y = int(y)
        if 0 <= x < self.w and 0 <= y < self.h:
            p = self.px[y * self.w + x]
            for k in range(3):
                p[k] = clamp(p[k] + (c[k] - p[k]) * f)
            p[3] = 255

    def rect(self, x0, y0, x1, y1, c):
        for y in range(int(y0), int(y1)):
            for x in range(int(x0), int(x1)):
                self.put(x, y, c)

    def shade(self, x0, y0, x1, y1, top, bot):
        n = max(1, int(y1) - int(y0) - 1)
        for y in range(int(y0), int(y1)):
            t = (y - int(y0)) / n
            c = tuple(top[k] + (bot[k] - top[k]) * t for k in range(3))
            for x in range(int(x0), int(x1)):
                self.put(x, y, c)

    def circle(self, cx, cy, r, c):
        for y in range(int(cy - r - 1), int(cy + r + 2)):
            for x in range(int(cx - r - 1), int(cx + r + 2)):
                if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
                    self.put(x, y, c)

    def ring(self, cx, cy, r0, r1, c):
        for y in range(int(cy - r1 - 1), int(cy + r1 + 2)):
            for x in range(int(cx - r1 - 1), int(cx + r1 + 2)):
                d = (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2
                if r0 * r0 <= d <= r1 * r1:
                    self.put(x, y, c)

    def glow(self, cx, cy, r, c, strength):
        for y in range(int(cy - r - 1), int(cy + r + 2)):
            for x in range(int(cx - r - 1), int(cx + r + 2)):
                d = math.sqrt((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2)
                if d < r:
                    self.blend(x, y, c, (1 - d / r) * strength)

    def line(self, x0, y0, x1, y1, c, r=0.8):
        n = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
        for i in range(n + 1):
            t = i / n
            self.circle(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, r, c)

    def noise(self, amt, x0=0, y0=0, x1=None, y1=None):
        x1 = self.w if x1 is None else x1
        y1 = self.h if y1 is None else y1
        for y in range(y0, y1):
            for x in range(x0, x1):
                p = self.px[y * self.w + x]
                if p[3] == 0:
                    continue
                d = random.randint(-amt, amt)
                for k in range(3):
                    p[k] = clamp(p[k] + d)

    def save(self, path):
        ensure(path)
        raw = bytearray()
        for y in range(self.h):
            raw.append(0)
            for x in range(self.w):
                raw.extend(bytes(self.px[y * self.w + x]))

        def chunk(t, d):
            c = struct.pack('>I', len(d)) + t + d
            return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)

        png = b'\x89PNG\r\n\x1a\n'
        png += chunk(b'IHDR', struct.pack('>IIBBBBB', self.w, self.h, 8, 6, 0, 0, 0))
        png += chunk(b'IDAT', zlib.compress(bytes(raw), 9))
        png += chunk(b'IEND', b'')
        with open(path, 'wb') as f:
            f.write(png)


def brushed(im, base, amt=4, streak=2):
    im.rect(0, 0, im.w, im.h, base)
    for y in range(im.h):
        d = random.randint(-streak, streak)
        for x in range(im.w):
            p = im.px[y * im.w + x]
            n = random.randint(-amt, amt) + d
            for k in range(3):
                p[k] = clamp(p[k] + n)


def tex(name, im):
    im.save(os.path.join(ASSETS, 'textures', 'block', name + '.png'))


def itex(name, im):
    im.save(os.path.join(ASSETS, 'textures', 'item', name + '.png'))


METAL = (150, 152, 158)
DARK = (8, 8, 10)
GOLD = (205, 175, 60)

# ---------------------------------------------------------------- helpers in "uv units"
# Face regions use a 16x16 uv grid on a 128 px texture => 8 px per unit.
# XVR front/back: u = 0..16, y 0.5..5.5 (top at 5.5) maps to px rows 48..88.


def uvrect(im, u0, u1, y0, y1, c):
    im.rect(u0 * 8, 48 + (5.5 - y1) * 8, u1 * 8, 48 + (5.5 - y0) * 8, c)


def uvcirc(im, u, y, r, c):
    im.circle(u * 8, 48 + (5.5 - y) * 8, r * 8, c)


def xvr_base(top, bot):
    im = Img(128, 128, (22, 22, 25))
    brushed(im, (24, 24, 27), 3, 2)
    im.shade(8, 48, 120, 88, top, bot)
    im.rect(8, 48, 120, 49, (78, 78, 86))
    im.rect(8, 87, 120, 88, (10, 10, 12))
    im.rect(8, 48, 9, 88, (54, 54, 60))
    im.rect(119, 48, 120, 88, (14, 14, 16))
    return im


def xvr_front():
    im = xvr_base((44, 44, 49), (24, 24, 27))
    # brand badge
    uvrect(im, 1.8, 4.8, 3.9, 5.0, METAL)
    uvrect(im, 1.9, 4.7, 4.0, 4.9, (40, 40, 46))
    for k in range(3):
        uvrect(im, 2.2 + k * 0.8, 2.6 + k * 0.8, 4.2, 4.7, (170, 172, 178))
    # drive bay with vents
    uvrect(im, 5.6, 9.8, 3.2, 4.7, (6, 6, 7))
    uvrect(im, 5.6, 9.8, 3.05, 3.2, (72, 72, 78))
    for k in range(10):
        uvrect(im, 5.8 + k * 0.4, 5.9 + k * 0.4, 3.5, 4.4, (28, 28, 32))
    # two USB ports
    for ux in (1.8, 3.3):
        uvrect(im, ux, ux + 1.2, 1.3, 2.2, METAL)
        uvrect(im, ux + 0.1, ux + 1.1, 1.4, 2.1, DARK)
        uvrect(im, ux + 0.2, ux + 1.0, 1.6, 1.9, (40, 70, 170))
    # LED lenses (unlit; the lit state is drawn live by the game)
    for u, col in ((10.4, (70, 12, 12)), (11.1, (12, 28, 70)), (11.8, (12, 60, 20))):
        uvrect(im, u - 0.2, u + 0.2, 3.2, 3.6, col)
    # power button
    uvcirc(im, 13.2, 3.0, 1.0, (14, 14, 16))
    uvcirc(im, 13.2, 3.0, 0.85, (88, 90, 96))
    uvcirc(im, 13.2, 3.0, 0.62, (130, 132, 138))
    im.line(13.2 * 8, 48 + (5.5 - 3.5) * 8, 13.2 * 8, 48 + (5.5 - 3.0) * 8, (40, 40, 44), 0.8)
    return im


def xvr_back():
    im = xvr_base((36, 36, 40), (24, 24, 27))
    # power inlet
    uvcirc(im, 1.9, 1.7, 0.62, METAL)
    uvcirc(im, 1.9, 1.7, 0.45, DARK)
    uvcirc(im, 1.9, 1.7, 0.12, GOLD)
    # fan grille
    fu, fy = 4.2, 1.8
    uvcirc(im, fu, fy, 0.92, (50, 50, 55))
    uvcirc(im, fu, fy, 0.8, (10, 10, 12))
    for a in range(8):
        ang = a * math.pi / 4
        im.line(fu * 8, 48 + (5.5 - fy) * 8,
                fu * 8 + math.cos(ang) * 6.4, 48 + (5.5 - fy) * 8 + math.sin(ang) * 6.4, (70, 70, 76), 0.5)
    uvcirc(im, fu, fy, 0.18, (60, 60, 66))
    # VGA (decorative)
    uvrect(im, 1.2, 2.2, 3.55, 4.25, (40, 60, 150))
    for k in range(5):
        im.put(1.35 * 8 + k * 4, 48 + (5.5 - 3.9) * 8, (200, 200, 210))
    # HDMI OUT
    uvrect(im, 2.3, 4.3, 3.5, 4.3, METAL)
    uvrect(im, 2.4, 4.2, 3.6, 4.2, DARK)
    uvrect(im, 2.6, 4.0, 3.65, 3.8, GOLD)
    uvrect(im, 2.3, 4.3, 3.4, 3.5, (60, 60, 66))
    # 16 LAN (RJ45) ports in two rows of eight, with link LED lenses on top
    for i in range(16):
        cx = 5.6 + (i % 8) * 1.15
        cyy = 4.35 if i < 8 else 2.55
        uvrect(im, cx - 0.5, cx + 0.5, cyy - 0.625, cyy + 0.625, METAL)
        uvrect(im, cx - 0.42, cx + 0.42, cyy - 0.55, cyy + 0.25, DARK)
        uvrect(im, cx - 0.12, cx + 0.12, cyy + 0.0, cyy + 0.25, (28, 28, 32))
        uvrect(im, cx - 0.36, cx + 0.36, cyy - 0.5, cyy - 0.3, GOLD)
        for k in range(4):
            im.rect((cx - 0.34) * 8 + k * 1.9, 48 + (5.5 - (cyy - 0.3)) * 8,
                    (cx - 0.34) * 8 + k * 1.9 + 0.6, 48 + (5.5 - (cyy - 0.5)) * 8, (90, 70, 20))
        uvrect(im, cx - 0.42, cx - 0.1, cyy + 0.32, cyy + 0.58, (14, 50, 22))
        uvrect(im, cx + 0.1, cx + 0.42, cyy + 0.32, cyy + 0.58, (70, 38, 10))
    return im


def xvr_top():
    im = Img(128, 128, (34, 34, 38))
    brushed(im, (34, 34, 38), 3, 2)
    for x in range(20, 108, 5):
        im.rect(x, 36, x + 2, 76, (10, 10, 12))
        im.rect(x + 2, 36, x + 3, 76, (52, 52, 58))
    im.rect(80, 88, 114, 106, (205, 205, 208))
    im.rect(80, 88, 114, 89, (150, 150, 155))
    k = 84
    while k < 110:
        w = random.choice([1, 1, 2])
        im.rect(k, 92, k + w, 102, (30, 30, 30))
        k += w + random.choice([1, 2])
    for sx, sy in ((14, 22), (114, 22), (14, 106), (114, 106)):
        im.circle(sx, sy, 2.6, (92, 92, 98))
        im.line(sx - 1.6, sy, sx + 1.6, sy, (30, 30, 34), 0.5)
    return im


def xvr_side():
    im = Img(128, 128, (28, 28, 32))
    brushed(im, (28, 28, 32), 3, 2)
    im.rect(16, 48, 112, 49, (70, 70, 76))
    im.rect(16, 87, 112, 88, (10, 10, 12))
    for y in (56, 61, 66, 71, 76):
        im.rect(30, y, 98, y + 1, (10, 10, 12))
        im.rect(30, y + 1, 98, y + 2, (50, 50, 56))
    return im


# ---------------------------------------------------------------- monitor textures
# front (north face): u px = (16 - x) * 8, v px = (15 - y) * 8 ; back (south face): u px = x * 8


def monitor_front():
    im = Img(128, 128, (20, 20, 23))
    brushed(im, (20, 20, 23), 3, 2)
    im.rect(0, 0, 128, 1, (62, 62, 68))
    im.rect(0, 79, 128, 80, (8, 8, 9))
    im.rect(7, 7, 121, 73, (2, 2, 3))
    im.rect(8, 8, 120, 72, (4, 5, 7))
    for y in range(8, 72):
        for x in range(8, 120):
            v = x + y * 1.4
            if v < 60:
                im.blend(x, y, (34, 40, 54), 0.14 * (1 - (v - 8) / 52))
    im.rect(54, 75, 74, 77, METAL)
    # power button, bottom right of the bezel
    im.circle(116, 76, 3.4, (10, 10, 12))
    im.circle(116, 76, 2.7, (74, 76, 82))
    im.circle(116, 76, 1.8, (120, 122, 128))
    im.line(116, 74.6, 116, 76, (40, 40, 44), 0.5)
    # standby LED
    im.glow(106, 76, 4, (60, 255, 100), 0.3)
    im.rect(105, 75, 108, 77, (60, 235, 100))
    return im


def monitor_back():
    im = Img(128, 128, (34, 34, 37))
    brushed(im, (34, 34, 37), 3, 2)

    def mrect(u0, u1, y0, y1, c):
        im.rect(u0 * 8, (15 - y1) * 8, u1 * 8, (15 - y0) * 8, c)

    for y in range(14, 40, 8):
        mrect(4, 12, 15 - y / 8 - 0.25, 15 - y / 8, (12, 12, 14))
    for sx, sy in ((6.0, 10.5), (10.0, 10.5), (6.0, 7.5), (10.0, 7.5)):
        im.circle(sx * 8, (15 - sy) * 8, 2.6, (92, 94, 100))
        im.line(sx * 8 - 1.7, (15 - sy) * 8, sx * 8 + 1.7, (15 - sy) * 8, (24, 24, 28), 0.5)
    # HDMI input (centre 11.5, 7.0)
    mrect(10.5, 12.5, 6.6, 7.4, METAL)
    mrect(10.6, 12.4, 6.7, 7.3, DARK)
    mrect(10.9, 12.1, 6.75, 6.95, GOLD)
    # DisplayPort (decorative)
    mrect(13.0, 14.2, 6.65, 7.35, (150, 152, 158))
    mrect(13.1, 14.1, 6.75, 7.25, DARK)
    # VGA (decorative)
    mrect(1.5, 3.1, 6.6, 7.4, (40, 60, 150))
    for k in range(5):
        im.put(1.7 * 8 + k * 5, (15 - 7.0) * 8, (200, 200, 210))
    # power inlet
    mrect(1.5, 2.8, 5.5, 6.2, METAL)
    mrect(1.6, 2.7, 5.6, 6.1, DARK)
    return im


# ---------------------------------------------------------------- camera textures


def camera_body():
    im = Img(128, 128, (226, 228, 231))
    brushed(im, (226, 228, 231), 3, 2)
    im.rect(0, 62, 128, 64, (196, 198, 202))
    im.rect(30, 92, 100, 112, (205, 207, 211))
    for k in range(34, 96, 3):
        im.rect(k, 96, k + 1, 108, (120, 122, 126))
    return im


def camera_rear():
    im = camera_body()

    def crect(u0, u1, y0, y1, c):
        im.rect(u0 * 8, 56 + (9 - y1) * 8, u1 * 8, 56 + (9 - y0) * 8, c)

    # rubber ring + RJ45 socket (centre 8.0, 6.8)
    im.circle(8 * 8, 56 + (9 - 6.8) * 8, 12, (60, 60, 64))
    crect(7.3, 8.7, 6.2, 7.4, METAL)
    crect(7.4, 8.6, 6.3, 7.3, DARK)
    crect(7.5, 8.5, 6.35, 6.6, GOLD)
    for k in range(4):
        im.rect(7.5 * 8 + k * 2.5, 56 + (9 - 6.6) * 8, 7.5 * 8 + k * 2.5 + 0.7, 56 + (9 - 6.35) * 8, (90, 70, 20))
    return im


def camera_metal():
    im = Img(128, 128, (110, 114, 120))
    brushed(im, (110, 114, 120), 5, 3)
    return im


def camera_lens():
    im = Img(128, 128, (28, 30, 34))
    cx = cy = 64
    im.circle(cx, cy, 62, (58, 60, 66))
    im.circle(cx, cy, 56, (22, 23, 27))
    for a in range(12):
        ang = a * math.pi / 6
        lx, ly = cx + math.cos(ang) * 46, cy + math.sin(ang) * 46
        im.glow(lx, ly, 10, (200, 30, 30), 0.5)
        im.circle(lx, ly, 4.5, (150, 20, 20))
        im.circle(lx, ly, 2.4, (230, 70, 60))
    im.circle(cx, cy, 33, (46, 48, 54))
    im.circle(cx, cy, 30, (8, 10, 22))
    im.circle(cx, cy, 22, (14, 22, 50))
    im.ring(cx, cy, 12, 14, (30, 44, 90))
    im.circle(cx - 9, cy - 9, 6, (120, 140, 205))
    im.circle(cx + 10, cy + 11, 2.5, (80, 100, 165))
    return im


# ---------------------------------------------------------------- item textures


def bezier(p0, p1, p2, n=40):
    pts = []
    for i in range(n + 1):
        t = i / n
        x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0]
        y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]
        pts.append((x, y))
    return pts


def cable_item(body, light, plug_kind):
    im = Img(32, 32, (0, 0, 0), 0)
    pts = bezier((7, 25), (6, 7), (25, 7))
    for x, y in pts:
        im.circle(x, y, 1.6, body)
    for x, y in pts:
        im.circle(x - 0.5, y - 0.5, 0.7, light)
    im.rect(4, 24, 11, 28, (30, 30, 34))
    if plug_kind == 'hdmi':
        im.rect(4, 28, 11, 31, (190, 192, 198))
        im.rect(5, 29, 10, 30, (60, 60, 66))
        im.rect(6, 30, 9, 31, GOLD)
    else:
        im.rect(4, 28, 11, 31, (214, 224, 234))
        im.rect(5, 28, 10, 29, (170, 182, 196))
        for k in range(5, 10, 2):
            im.rect(k, 30, k + 1, 31, GOLD)
    im.rect(24, 4, 28, 11, (30, 30, 34))
    if plug_kind == 'hdmi':
        im.rect(28, 4, 31, 11, (190, 192, 198))
        im.rect(29, 5, 30, 10, (60, 60, 66))
        im.rect(30, 6, 31, 9, GOLD)
    else:
        im.rect(28, 4, 31, 11, (214, 224, 234))
        im.rect(28, 5, 29, 10, (170, 182, 196))
        for k in range(5, 10, 2):
            im.rect(30, k, 31, k + 1, GOLD)
    return im


def noise_tex():
    im = Img(64, 64, (0, 0, 0))
    for y in range(64):
        for x in range(64):
            v = random.randint(15, 215)
            im.put(x, y, (v, v, v))
    return im


# ---------------------------------------------------------------- generate textures

tex('xvr_front', xvr_front())
tex('xvr_back', xvr_back())
tex('xvr_top', xvr_top())
tex('xvr_side', xvr_side())
tex('monitor_front', monitor_front())
tex('monitor_back', monitor_back())
tex('camera_body', camera_body())
tex('camera_rear', camera_rear())
tex('camera_metal', camera_metal())
tex('camera_lens', camera_lens())
itex('hdmi_cable', cable_item((26, 26, 30), (80, 80, 88), 'hdmi'))
itex('ethernet_cable', cable_item((38, 108, 220), (120, 175, 255), 'eth'))
Img(4, 4, (255, 255, 255)).save(os.path.join(ASSETS, 'textures', 'misc', 'white.png'))
noise_tex().save(os.path.join(ASSETS, 'textures', 'misc', 'noise.png'))

# ---------------------------------------------------------------- models


def T(name):
    return MODID + ':block/' + name


def face(texture, uv):
    return {'uv': uv, 'texture': '#' + texture}


def box(frm, to, texture, uv=None):
    uv = uv or [0, 0, 16, 16]
    return {'from': frm, 'to': to,
            'faces': {d: face(texture, uv) for d in ('north', 'south', 'east', 'west', 'up', 'down')}}


def model(textures, elements):
    return {'parent': 'minecraft:block/block', 'textures': dict(textures), 'elements': elements}


xvr_body = {
    'from': [1, 0.5, 2], 'to': [15, 5.5, 14],
    'faces': {
        'north': face('front', [1, 6, 15, 11]),
        'south': face('back', [1, 6, 15, 11]),
        'up': face('top', [1, 2, 15, 14]),
        'down': face('side', [1, 2, 15, 14]),
        'east': face('side', [2, 6, 14, 11]),
        'west': face('side', [2, 6, 14, 11]),
    }}
feet = [box([x, 0, z], [x + 2, 0.5, z + 2], 'side') for x, z in
        ((1.5, 2.5), (12.5, 2.5), (1.5, 11.5), (12.5, 11.5))]
wjson(os.path.join(ASSETS, 'models', 'block', 'xvr.json'), model(
    {'front': T('xvr_front'), 'back': T('xvr_back'), 'top': T('xvr_top'),
     'side': T('xvr_side'), 'particle': T('xvr_side')}, [xvr_body] + feet))

panel = {
    'from': [0, 5, 7], 'to': [16, 15, 9],
    'faces': {
        'north': face('front', [0, 0, 16, 10]),
        'south': face('back', [0, 0, 16, 10]),
        'up': face('body', [0, 0, 16, 2]),
        'down': face('body', [0, 0, 16, 2]),
        'east': face('body', [0, 0, 2, 10]),
        'west': face('body', [0, 0, 2, 10]),
    }}
wjson(os.path.join(ASSETS, 'models', 'block', 'monitor.json'), model(
    {'front': T('monitor_front'), 'back': T('monitor_back'), 'body': T('monitor_back'),
     'particle': T('monitor_back')},
    [box([4, 0, 4], [12, 1, 12], 'body'),
     box([7, 1, 7], [9, 5, 9], 'body'),
     panel]))

cam_body = {
    'from': [5.5, 5, 2], 'to': [10.5, 9, 12],
    'faces': {
        'north': face('body', [0, 0, 16, 16]),
        'south': face('rear', [5.5, 7, 10.5, 11]),
        'east': face('body', [0, 0, 16, 16]),
        'west': face('body', [0, 0, 16, 16]),
        'up': face('body', [0, 0, 16, 16]),
        'down': face('body', [0, 0, 16, 16]),
    }}
wjson(os.path.join(ASSETS, 'models', 'block', 'camera.json'), model(
    {'body': T('camera_body'), 'rear': T('camera_rear'), 'metal': T('camera_metal'),
     'lens': T('camera_lens'), 'particle': T('camera_body')},
    [box([5, 0, 5], [11, 1, 11], 'metal'),
     box([7.25, 1, 7.25], [8.75, 4, 8.75], 'metal'),
     box([6.5, 4, 6], [9.5, 5, 10], 'metal'),
     cam_body,
     box([5, 9, 1], [11, 9.6, 12.5], 'body'),
     {'from': [6, 5, 1], 'to': [10, 9, 2],
      'faces': {'north': face('lens', [0, 0, 16, 16]),
                'south': face('metal', [0, 0, 16, 16]),
                'east': face('metal', [0, 0, 16, 16]),
                'west': face('metal', [0, 0, 16, 16]),
                'up': face('metal', [0, 0, 16, 16]),
                'down': face('metal', [0, 0, 16, 16])}}]))

for n in ('xvr', 'monitor', 'camera'):
    wjson(os.path.join(ASSETS, 'models', 'item', n + '.json'), {'parent': T(n)})
for n in ('hdmi_cable', 'ethernet_cable'):
    wjson(os.path.join(ASSETS, 'models', 'item', n + '.json'),
          {'parent': 'minecraft:item/generated', 'textures': {'layer0': MODID + ':item/' + n}})

ROT = {'north': 0, 'east': 90, 'south': 180, 'west': 270}


def blockstate(name, powered):
    v = {}
    for f, y in ROT.items():
        if powered:
            for p in ('false', 'true'):
                v['facing=%s,powered=%s' % (f, p)] = {'model': T(name), 'y': y}
        else:
            v['facing=%s' % f] = {'model': T(name), 'y': y}
    wjson(os.path.join(ASSETS, 'blockstates', name + '.json'), {'variants': v})


blockstate('xvr', True)
blockstate('monitor', False)
blockstate('camera', False)

wjson(os.path.join(ASSETS, 'lang', 'en_us.json'), {
    'block.immersivecctv.xvr': 'XVR Recorder (16 channels)',
    'block.immersivecctv.monitor': 'CCTV Monitor',
    'block.immersivecctv.camera': 'Bullet Camera',
    'item.immersivecctv.hdmi_cable': 'HDMI Cable',
    'item.immersivecctv.ethernet_cable': 'Ethernet Cable',
    'itemGroup.immersivecctv': 'Immersive CCTV',
})

# ---------------------------------------------------------------- data (loot + recipes)
for n in ('xvr', 'monitor', 'camera'):
    wjson(os.path.join(DATA, 'loot_tables', 'blocks', n + '.json'), {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0,
                   'entries': [{'type': 'minecraft:item', 'name': MODID + ':' + n}],
                   'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})


def shaped(name, pattern, key, count=1):
    wjson(os.path.join(DATA, 'recipes', name + '.json'), {
        'type': 'minecraft:crafting_shaped',
        'category': 'redstone',
        'pattern': pattern,
        'key': {k: {'item': v} for k, v in key.items()},
        'result': {'item': MODID + ':' + name, 'count': count}})


shaped('xvr', ['III', 'RCR', 'IBI'],
       {'I': 'minecraft:iron_ingot', 'R': 'minecraft:redstone',
        'C': 'minecraft:comparator', 'B': 'minecraft:black_dye'})
shaped('monitor', ['III', 'IGI', 'IRI'],
       {'I': 'minecraft:iron_ingot', 'G': 'minecraft:glass_pane', 'R': 'minecraft:redstone'})
shaped('camera', ['III', 'GRI', 'III'],
       {'I': 'minecraft:iron_ingot', 'G': 'minecraft:glass_pane', 'R': 'minecraft:redstone'})
shaped('hdmi_cable', ['IBI'], {'I': 'minecraft:iron_ingot', 'B': 'minecraft:black_dye'}, 2)
shaped('ethernet_cable', ['ILI'], {'I': 'minecraft:iron_ingot', 'L': 'minecraft:lapis_lazuli'}, 2)

# ---------------------------------------------------------------- mod metadata
wtext(os.path.join(RES, 'pack.mcmeta'),
      '{\n  "pack": {\n    "description": "Immersive CCTV resources",\n    "pack_format": 15\n  }\n}\n')

wtext(os.path.join(RES, 'META-INF', 'mods.toml'), '''modLoader="javafml"
loaderVersion="[47,)"
license="MIT"

[[mods]]
modId="immersivecctv"
version="1.6.0"
displayName="IMMERSIVE CCTV"
authors="Iustin Martac (Iustin-dev)"
displayURL="https://github.com/Iustin-dev2014/IMMERSIVE-CCTV"
description=\'\'\'Realistic CCTV: 16-channel XVR, monitor, cameras with real live view, HDMI and Ethernet cables.\'\'\'

[[dependencies.immersivecctv]]
modId="forge"
mandatory=true
versionRange="[47,)"
ordering="NONE"
side="BOTH"

[[dependencies.immersivecctv]]
modId="minecraft"
mandatory=true
versionRange="[1.20.1,1.21)"
ordering="NONE"
side="BOTH"
''')

print('assets generated')
