#!/usr/bin/env python3
"""Generate detailed 128x128 RGBA CCTV hardware textures using only the Python standard library."""
from pathlib import Path
import random, struct, zlib

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/immersivecctv/textures'
BLOCK = ROOT / 'block'; ITEM = ROOT / 'item'
BLOCK.mkdir(parents=True, exist_ok=True); ITEM.mkdir(parents=True, exist_ok=True)
W = H = 128
GRID = 64
SCALE = W // GRID

class Canvas:
    def __init__(self, color=(0,0,0,255), seed=1):
        self.p = bytearray(color * (W*H)); self.r = random.Random(seed)
    def rect(self, x1,y1,x2,y2,c):
        x1, y1, x2, y2 = (int(round(v * SCALE)) for v in (x1, y1, x2, y2))
        for y in range(max(0,y1),min(H,y2)):
            for x in range(max(0,x1),min(W,x2)):
                i=(y*W+x)*4; self.p[i:i+4]=bytes(c)
    def line(self,x1,y1,x2,y2,c):
        x1, y1, x2, y2 = (int(round(v * SCALE)) for v in (x1, y1, x2, y2))
        dx=abs(x2-x1); sx=1 if x1<x2 else -1; dy=-abs(y2-y1); sy=1 if y1<y2 else -1; err=dx+dy
        while True:
            if 0<=x1<W and 0<=y1<H:
                i=(y1*W+x1)*4; self.p[i:i+4]=bytes(c)
            if x1==x2 and y1==y2: break
            e2=2*err
            if e2>=dy: err+=dy; x1+=sx
            if e2<=dx: err+=dx; y1+=sy
    def noise(self, amount=6):
        for y in range(H):
            for x in range(W):
                i=(y*W+x)*4
                n=self.r.randint(-amount,amount)
                for k in range(3): self.p[i+k]=max(0,min(255,self.p[i+k]+n))
    def save(self,path):
        raw=b''.join(b'\x00'+self.p[y*W*4:(y+1)*W*4] for y in range(H))
        def chunk(t,d): return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
        data=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>2I5B',W,H,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b'')
        path.write_bytes(data)

# Small 3x5 screen-printed font, authored on the 64-unit grid and scaled cleanly to 128px.
FONT = {
    '0': ('111','101','101','101','111'), '1': ('010','110','010','010','111'),
    '2': ('111','001','111','100','111'), '3': ('111','001','111','001','111'),
    '4': ('101','101','111','001','001'), '5': ('111','100','111','001','111'),
    '6': ('111','100','111','101','111'), '7': ('111','001','010','010','010'),
    '8': ('111','101','111','101','111'), '9': ('111','101','111','001','111'),
    'A': ('010','101','111','101','101'), 'B': ('110','101','110','101','110'),
    'C': ('011','100','100','100','011'), 'D': ('110','101','101','101','110'),
    'E': ('111','100','110','100','111'), 'F': ('111','100','110','100','100'),
    'G': ('011','100','101','101','011'), 'H': ('101','101','111','101','101'),
    'I': ('111','010','010','010','111'), 'J': ('001','001','001','101','010'),
    'K': ('101','101','110','101','101'), 'L': ('100','100','100','100','111'),
    'M': ('101','111','111','101','101'), 'N': ('101','111','111','111','101'),
    'O': ('010','101','101','101','010'), 'P': ('110','101','110','100','100'),
    'R': ('110','101','110','101','101'), 'S': ('011','100','010','001','110'),
    'T': ('111','010','010','010','010'), 'U': ('101','101','101','101','111'),
    'V': ('101','101','101','101','010'), 'W': ('101','101','111','111','101'),
    'X': ('101','101','010','101','101'), 'Y': ('101','101','010','010','010'),
}
def text(c, x, y, value, color=(190, 205, 212, 255), spacing=1):
    cursor = x
    for char in value.upper():
        if char == ' ':
            cursor += 3 + spacing
            continue
        glyph = FONT.get(char)
        if glyph:
            for gy, row in enumerate(glyph):
                for gx, bit in enumerate(row):
                    if bit == '1':
                        c.rect(cursor + gx, y + gy, cursor + gx + 1, y + gy + 1, color)
        cursor += 3 + spacing

# Flat white texture used as the unshaded base for dynamic coloured 3D cable/LED geometry.
Canvas((255,255,255,255), 1).save(BLOCK/'white.png')

# Fine brushed metal: anisotropic horizontal grain, tiny highlights.
c=Canvas((92,101,110,255),12)
for y in range(64):
    shade=c.r.randint(-14,14)
    c.line(0,y,63,y,(max(0,92+shade),max(0,101+shade),max(0,110+shade),255))
    if y%7==0: c.line(0,y,63,y,(125,133,141,255))
c.noise(3); c.save(BLOCK/'brushed_steel.png')

# Dark vent grille.
c=Canvas((24,29,34,255),2)
for x in range(5,64,7):
    c.rect(x,5,x+3,59,(7,10,13,255)); c.line(x,5,x,59,(51,58,65,255))
c.save(BLOCK/'vents.png')

# Rubber feet.
c=Canvas((27,29,31,255),3)
for _ in range(160):
    x=c.r.randrange(64); y=c.r.randrange(64); v=c.r.randrange(20,45); c.rect(x,y,x+1,y+1,(v,v,v,255))
c.save(BLOCK/'rubber.png')

# Frontal XVR: 8 distinct RJ45 ports with copper contacts and LEDs.
def make_front(on):
    c=Canvas((20,25,30,255),77 if on else 78)
    c.rect(0,0,64,7,(38,45,52,255)); c.line(0,7,63,7,(92,101,109,255))
    # Eight recessed Ethernet jacks.
    for i in range(8):
        x=2+i*7
        c.rect(x,13,x+6,32,(5,8,11,255)); c.rect(x+1,14,x+5,26,(21,28,32,255))
        c.rect(x+1,26,x+5,29,(119,78,34,255))
        for pin in range(4): c.rect(x+1+pin,26,x+2+pin,29,(224,170,72,255))
        c.rect(x+1,10,x+2,12,(72,220,115,255) if on else (35,44,38,255))
        c.rect(x+4,10,x+5,12,(250,157,40,255) if on else (56,42,27,255))
        text(c, x+1, 33, str(i+1), (193, 205, 211, 255) if on else (105, 113, 118, 255), 0)
    # USB sockets and hard power switch.
    c.rect(4,39,16,51,(4,5,6,255)); c.rect(6,41,14,47,(105,117,124,255)); c.rect(6,48,14,49,(28,35,40,255))
    c.rect(20,39,32,51,(4,5,6,255)); c.rect(22,41,30,47,(126,137,143,255)); c.rect(22,48,30,49,(28,35,40,255))
    c.rect(45,36,61,55,(7,10,12,255)); c.rect(47,38,59,53,(62,69,74,255)); c.rect(49,40,57,51,(26,32,37,255))
    c.rect(51,42,55,49,(82,90,96,255) if on else (38,28,28,255))
    c.line(0,61,63,61,(75,84,92,255)); text(c, 4, 54, 'USB', (165, 178, 185, 255), 1); text(c, 35, 56, 'PWR', (188, 198, 203, 255), 1); c.noise(2)
    return c
make_front(True).save(BLOCK/'xvr_front_on.png')
make_front(False).save(BLOCK/'xvr_front_off.png')

# Preserve the original round red/blue/green status lamps. The newer rectangular
# overlays duplicated those indicators, so only add one round amber lamp beside them.
def make_front_status(powered, poe_link, wan_link):
    c = make_front(powered)
    # Circular amber PoE/activity indicator, drawn as a ring and a bright core.
    cx, cy = 57, 4
    for y in range(0, 9):
        for x in range(53, 62):
            d2 = (x - cx) ** 2 + (y - cy) ** 2
            if 5 <= d2 <= 10:
                c.rect(x, y, x + 1, y + 1, (105, 75, 18, 255) if not poe_link else (255, 180, 35, 255))
            elif d2 <= 4:
                c.rect(x, y, x + 1, y + 1, (56, 42, 18, 255) if not poe_link else (255, 224, 94, 255))
    c.noise(1)
    suffix = ('on' if powered else 'off') + '_p' + str(int(poe_link)) + '_w' + str(int(wan_link))
    c.save(BLOCK / ('xvr_front_' + suffix + '.png'))

for _powered in (False, True):
    for _poe in (False, True):
        for _wan in (False, True):
            make_front_status(_powered, _poe, _wan)

# Separate physical socket-face textures used by the XVR, monitor and camera models.
def make_hdmi_socket():
    c=Canvas((20,25,29,255), 201)
    c.rect(2,2,62,62,(36,43,49,255)); c.rect(4,4,60,60,(70,79,86,255))
    c.rect(7,7,57,57,(18,23,28,255)); c.rect(10,13,54,44,(8,11,14,255))
    # Bevelled HDMI mouth and dark inner cavity.
    c.line(13,16,51,16,(149,159,165,255)); c.line(13,16,18,22,(164,174,180,255))
    c.line(51,16,46,22,(164,174,180,255)); c.line(18,22,46,22,(117,129,136,255))
    c.rect(18,23,46,39,(3,5,7,255)); c.rect(20,25,44,27,(48,54,57,255))
    for i in range(8):
        x=21+i*3
        c.rect(x,29,x+1,34,(207,158,67,255)); c.line(x,29,x,34,(247,205,114,255))
    c.line(20,38,44,38,(52,60,65,255)); c.line(8,8,14,8,(178,188,194,255))
    for x,y in ((5,5),(59,5),(5,59),(59,59)):
        c.rect(x-1,y-1,x+2,y+2,(13,16,19,255)); c.rect(x,y,x+1,y+1,(198,203,205,255))
    text(c, 21, 47, 'HDMI', (211, 219, 223, 255), 1)
    c.noise(2)
    return c

def make_rj45_socket():
    c=Canvas((25,31,35,255), 202)
    c.rect(2,2,62,62,(37,45,50,255)); c.rect(4,4,60,60,(77,86,91,255))
    c.rect(10,8,54,48,(9,13,16,255)); c.rect(13,10,51,44,(3,6,8,255))
    c.line(13,10,51,10,(159,169,174,255)); c.line(13,10,18,16,(144,154,160,255)); c.line(51,10,46,16,(144,154,160,255))
    c.rect(17,17,47,35,(17,23,26,255)); c.rect(18,17,46,20,(62,73,78,255))
    for i in range(8):
        x=19+i*3
        c.rect(x,21,x+1,27,(218,170,77,255)); c.line(x,21,x,27,(250,214,130,255))
    c.rect(18,31,46,33,(34,42,46,255)); text(c, 23, 49, 'LAN', (206, 216, 220, 255), 1)
    for x,y in ((5,5),(59,5),(5,59),(59,59)):
        c.rect(x-1,y-1,x+2,y+2,(12,15,17,255)); c.rect(x,y,x+1,y+1,(184,192,196,255))
    c.noise(2)
    return c

make_hdmi_socket().save(BLOCK/'hdmi_socket.png')
make_rj45_socket().save(BLOCK/'rj45_socket.png')

def make_usb_socket():
    c=Canvas((23,29,34,255), 204)
    c.rect(3,3,61,61,(48,57,63,255)); c.rect(7,9,57,53,(7,10,12,255)); c.rect(12,17,52,43,(4,6,7,255))
    c.rect(14,19,50,24,(151,161,166,255)); c.line(14,19,50,19,(215,221,224,255))
    c.rect(18,27,46,37,(26,33,37,255)); c.line(19,28,45,28,(94,105,112,255)); text(c, 23, 45, 'USB', (211,220,223,255), 1)
    for x,y in ((6,6),(58,6),(6,58),(58,58)):
        c.rect(x-1,y-1,x+2,y+2,(11,14,16,255)); c.rect(x,y,x+1,y+1,(174,184,190,255))
    c.noise(2); return c

def make_dc_socket():
    c=Canvas((23,29,34,255), 205)
    c.rect(3,3,61,61,(48,57,63,255)); c.rect(8,8,56,56,(10,14,17,255))
    for rad,col in ((21,(119,129,135,255)),(17,(25,31,35,255)),(12,(6,9,11,255)),(6,(87,99,106,255)),(3,(4,7,8,255))):
        for yy in range(64):
            for xx in range(64):
                if (xx-32)**2+(yy-32)**2 <= rad*rad:
                    c.rect(xx, yy, xx+1, yy+1, col)
    c.line(17,15,27,12,(202,210,214,255)); text(c, 26, 51, '12V', (206,216,220,255), 1)
    c.noise(2); return c

make_usb_socket().save(BLOCK/'usb_socket.png')
make_dc_socket().save(BLOCK/'dc_socket.png')

# The XVR rear panel is separate from the brushed-metal chassis, with legible connector labels.
def make_rear_panel():
    c=Canvas((22,28,33,255), 203)
    c.rect(0,0,64,64,(31,38,43,255)); c.line(1,5,62,5,(87,97,104,255)); c.line(1,59,62,59,(8,12,15,255))
    for y in range(8,58,5): c.line(2,y,62,y,(35,42,47,255))
    # Dedicated WAN RJ45 input above the HDMI/USB/DC connectors.
    c.rect(21,3,43,14,(5,8,10,255)); c.rect(23,4,41,11,(15,24,28,255))
    for x in range(25,40,2): c.rect(x,8,x+1,10,(221,173,78,255))
    text(c, 24, 11, 'WAN', (201, 216, 221, 255), 0)
    # Rear HDMI output, USB-A and 12V DC power.
    c.rect(21,13,44,38,(8,11,14,255)); c.rect(23,15,42,36,(83,91,96,255)); c.rect(25,18,40,33,(4,7,9,255))
    c.line(26,19,39,19,(212,219,221,255)); c.line(26,19,29,22,(212,219,221,255)); c.line(39,19,36,22,(212,219,221,255))
    for x in range(27,39,2): c.rect(x,22,x+1,25,(225,177,83,255))
    text(c, 23, 40, 'HDMI', (213,222,226,255), 1)
    c.rect(3,14,17,38,(14,18,21,255)); c.rect(4,15,16,37,(29,36,40,255))
    for y in range(17,36,3): c.line(5,y,15,y,(72,81,87,255))
    c.rect(43,14,52,37,(5,7,9,255)); c.rect(45,16,50,34,(133,145,151,255)); text(c, 43, 40, 'USB', (184,194,199,255), 1)
    c.rect(55,14,62,37,(5,7,9,255)); c.rect(57,16,60,34,(106,119,125,255)); c.rect(58,19,59,29,(19,23,25,255)); text(c, 55, 40, 'DC', (184,194,199,255), 1)
    for x,y in ((3,7),(60,7),(3,55),(60,55)):
        c.rect(x-1,y-1,x+2,y+2,(8,11,13,255)); c.rect(x,y,x+1,y+1,(173,182,187,255))
    c.noise(2)
    return c
make_rear_panel().save(BLOCK/'xvr_rear.png')

# Monitor bezel and anti-reflective blue glass.
c=Canvas((9,12,16,255),9); c.rect(1,1,62,62,(18,23,28,255)); c.rect(3,3,60,60,(3,6,10,255)); c.line(4,4,59,4,(68,79,88,255)); c.save(BLOCK/'monitor_bezel.png')
c=Canvas((5,17,26,255),10)
for y in range(64):
    base=max(0,24-int(y*.25)); c.line(0,y,63,y,(3,base,base+9,255))
c.line(5,6,53,2,(23,65,81,180)); c.line(9,12,58,8,(14,45,58,170)); c.rect(4,4,60,5,(32,75,88,255)); c.line(5,58,59,58,(8,28,36,255)); c.noise(2); c.save(BLOCK/'monitor_glass.png')

# Matte camera barrel and rubberised visor.
c=Canvas((38,45,51,255),11); c.noise(8); c.rect(0,0,63,5,(68,78,85,255)); c.rect(0,58,63,63,(18,22,25,255));
for y in (12,18,24,30,36,42,48): c.line(4,y,59,y,(21,26,30,255))
c.save(BLOCK/'camera_body.png')
c=Canvas((8,15,23,255),13); c.rect(0,0,63,63,(10,17,25,255));
for k in range(28):
    x=32+int(26*__import__('math').cos(k*6.283/28)); y=32+int(26*__import__('math').sin(k*6.283/28)); c.rect(x-1,y-1,x+2,y+2,(160,47,29,255))
for rad,col in ((23,(130,42,28,255)),(18,(38,92,130,255)),(13,(12,33,52,255)),(8,(38,112,154,255)),(4,(3,8,14,255))):
    for y in range(64):
        for x in range(64):
            if (x-31.5)**2+(y-31.5)**2 <= rad*rad:
                c.rect(x, y, x+1, y+1, col)
c.line(15,14,26,10,(180,226,255,255)); c.line(17,16,29,12,(85,172,220,255)); c.save(BLOCK/'camera_lens.png')
c=Canvas((56,64,69,255),14); c.noise(4); c.rect(4,4,60,60,(75,83,87,255)); c.rect(9,9,55,55,(46,53,58,255)); c.rect(13,13,51,51,(82,90,95,255));
for x,y in ((7,7),(57,7),(7,57),(57,57)):
    c.rect(x-2,y-2,x+2,y+2,(10,13,15,255)); c.rect(x-1,y-1,x+1,y+1,(162,171,176,255)); c.rect(x,y,x+1,y+1,(226,231,232,255))
c.line(11,13,52,13,(126,136,141,255)); c.line(11,51,52,51,(25,30,34,255)); c.save(BLOCK/'camera_mount.png')

# PTZ dome camera: powder-coated turret, optical dome and infrared emitter ring.
c=Canvas((31,38,44,255), 601); c.noise(5)
c.rect(0,0,63,7,(70,79,85,255)); c.rect(0,55,63,63,(15,19,22,255))
for y in (12,18,24,30,36,42,48): c.line(3,y,60,y,(23,29,33,255))
c.save(BLOCK/'ptz_camera_body.png')
c=Canvas((6,11,16,255), 602); c.rect(3,3,60,60,(10,17,24,255))
for rad,col in ((27,(71,82,90,255)),(24,(18,32,43,255)),(20,(7,17,27,255)),(15,(25,84,119,255)),(10,(8,34,52,255)),(6,(70,167,210,255)),(3,(2,8,13,255))):
    for yy in range(64):
        for xx in range(64):
            if (xx-31.5)**2+(yy-31.5)**2 <= rad*rad: c.rect(xx,yy,xx+1,yy+1,col)
for k in range(24):
    import math as _math
    xx=32+int(28*_math.cos(k*6.283/24)); yy=32+int(28*_math.sin(k*6.283/24))
    c.rect(xx-1,yy-1,xx+2,yy+2,(174,48,35,255))
c.line(12,13,27,9,(190,228,255,255)); c.line(14,16,30,11,(98,181,225,255)); c.noise(1); c.save(BLOCK/'ptz_camera_lens.png')

# Inventory thumbnails are transparent and intentionally legible at 16px.
c=Canvas((0,0,0,0),21); c.rect(7,7,57,57,(21,25,30,255)); c.rect(4,11,16,53,(184,190,194,255)); c.rect(48,11,60,53,(184,190,194,255)); c.rect(9,9,55,55,(9,13,18,255)); c.rect(11,11,53,53,(31,38,44,255)); c.line(15,16,48,16,(215,175,75,255)); c.save(ITEM/'hdmi_cable.png')
c=Canvas((0,0,0,0),22); c.rect(20,8,44,56,(13,71,119,255)); c.rect(23,7,41,12,(35,145,213,255)); c.rect(23,52,41,57,(35,145,213,255)); c.rect(24,12,40,20,(89,189,231,255));
for x in range(25,40,3): c.rect(x,15,x+1,19,(226,190,91,255))
c.line(27,22,27,48,(83,179,229,255)); c.line(37,22,37,48,(83,179,229,255)); c.save(ITEM/'poe_cat6_cable.png')
print('Generated 128x128 detailed textures in', ROOT)


# New mains-power hardware: grounded wall outlet, moulded 12V adapter, and a thick AC lead.
def make_wall_outlet():
    c=Canvas((29,34,39,255), 501)
    c.rect(1,1,63,63,(20,25,29,255)); c.rect(3,3,61,61,(76,84,89,255))
    c.rect(5,5,59,59,(151,157,159,255)); c.rect(7,7,57,57,(190,194,195,255))
    c.line(8,8,56,8,(235,238,238,255)); c.line(8,8,8,56,(236,238,238,255))
    c.line(8,56,56,56,(70,77,81,255)); c.line(56,8,56,56,(78,85,89,255))
    # Child-safe socket wells with dark bevels, brass earth/contact details and screws.
    for cx,cy in ((24,26),(40,26)):
        c.rect(cx-8,cy-12,cx+8,cy+12,(89,98,101,255))
        c.rect(cx-6,cy-10,cx+6,cy+10,(26,32,35,255))
        c.rect(cx-4,cy-7,cx-2,cy+1,(7,10,12,255)); c.rect(cx+2,cy-7,cx+4,cy+1,(7,10,12,255))
        c.line(cx-4,cy-7,cx-2,cy-7,(207,166,79,255)); c.line(cx+2,cy-7,cx+4,cy-7,(207,166,79,255))
        c.rect(cx-3,cy+4,cx+3,cy+7,(6,8,9,255)); c.line(cx-3,cy+4,cx+3,cy+4,(189,151,71,255))
    c.rect(22,43,42,53,(32,40,44,255)); text(c,24,45,'230V',(203,216,221,255),1)
    for x,y in ((5,5),(59,5),(5,59),(59,59)):
        c.rect(x-2,y-2,x+2,y+2,(36,42,45,255)); c.rect(x-1,y-1,x+1,y+1,(201,207,209,255)); c.line(x-1,y,x+1,y,(52,59,63,255))
    c.noise(2); return c

def make_ac_charger():
    c=Canvas((0,0,0,0), 502)
    c.rect(12,10,52,54,(15,19,22,255)); c.rect(14,8,50,52,(57,65,70,255)); c.rect(16,10,48,49,(29,35,39,255))
    c.rect(19,13,45,18,(89,99,104,255)); c.line(20,13,44,13,(190,197,200,255))
    c.rect(23,20,41,39,(8,12,15,255)); c.rect(25,22,39,36,(33,43,49,255))
    c.rect(26,25,38,27,(74,181,217,255)); c.rect(26,30,38,32,(74,181,217,255)); text(c,25,40,'12V',(202,215,220,255),1)
    c.rect(22,50,27,61,(157,164,168,255)); c.rect(37,50,42,61,(157,164,168,255)); c.rect(21,58,28,61,(216,185,105,255)); c.rect(36,58,43,61,(216,185,105,255))
    c.noise(1); return c

def make_power_cable():
    c=Canvas((0,0,0,0), 503)
    # thick black lead with two distinct moulded plugs and yellow mains warning stripe
    c.rect(26,16,38,47,(16,20,23,255)); c.rect(28,13,36,50,(45,51,55,255)); c.line(29,18,29,45,(88,95,99,255)); c.line(34,18,34,45,(24,29,32,255))
    c.rect(19,6,45,18,(13,17,20,255)); c.rect(21,5,43,15,(64,71,76,255)); c.rect(25,6,39,11,(181,187,190,255))
    c.rect(22,46,42,57,(12,16,18,255)); c.rect(24,46,40,54,(60,68,72,255)); c.rect(28,49,36,55,(208,174,76,255))
    c.rect(30,17,32,46,(208,174,76,255)); c.line(31,18,31,45,(245,220,141,255))
    c.noise(1); return c

make_wall_outlet().save(BLOCK/'wall_outlet.png')
make_ac_charger().save(ITEM/'ac_charger.png')
make_power_cable().save(ITEM/'power_cable.png')

# Handheld CCTV Service Tool: a dark diagnostic probe with a cyan camera-ID indicator.
c=Canvas((0,0,0,0), 504)
c.rect(20,8,44,56,(29,35,39,255)); c.rect(22,10,42,54,(73,84,91,255))
c.rect(24,12,40,28,(7,14,19,255)); c.rect(26,14,38,26,(15,50,65,255))
c.rect(28,16,36,18,(60,205,233,255)); c.rect(28,20,36,22,(60,205,233,255)); c.rect(28,24,36,25,(60,205,233,255))
c.line(25,31,39,31,(195,202,205,255)); c.line(25,35,39,35,(33,42,47,255))
c.rect(25,39,39,48,(16,22,26,255)); c.rect(28,41,36,46,(216,177,82,255))
c.rect(29,53,35,60,(33,41,46,255)); c.rect(27,58,37,62,(16,20,23,255))
c.noise(1); c.save(ITEM/'cctv_service_tool.png')

# Internet/router network hardware introduced for the router + optical fibre system.
def make_router_front(mode):
    c = Canvas((18, 24, 29, 255), 811 if mode == 'off' else 812 if mode == 'signal_loss' else 813)
    c.rect(1, 1, 63, 63, (26, 32, 37, 255)); c.rect(2, 2, 62, 8, (41, 49, 55, 255))
    c.line(2, 8, 62, 8, (102, 112, 118, 255)); text(c, 4, 11, 'ROUTER', (194, 210, 216, 255), 1)
    # Four green activity indicators and one red SIGNAL LOSS indicator.
    for i in range(4):
        x = 4 + i * 7
        c.rect(x, 25, x + 4, 30, (16, 25, 21, 255))
        color = (63, 245, 122, 255) if mode == 'online' else (37, 98, 61, 255) if mode == 'signal_loss' else (23, 42, 31, 255)
        c.rect(x + 1, 26, x + 3, 29, color)
    c.rect(35, 25, 40, 30, (37, 18, 18, 255))
    red = (255, 50, 48, 255) if mode == 'signal_loss' else (60, 26, 27, 255)
    c.rect(36, 26, 39, 29, red)
    # Front power button.
    c.rect(45, 39, 60, 57, (7, 10, 12, 255)); c.rect(47, 41, 58, 55, (54, 62, 68, 255))
    c.rect(50, 44, 55, 52, (70, 235, 115, 255) if mode != 'off' else (52, 31, 30, 255))
    text(c, 4, 47, 'LINK', (136, 157, 166, 255), 1)
    c.noise(2)
    return c

make_router_front('off').save(BLOCK / 'router_front_off.png')
make_router_front('signal_loss').save(BLOCK / 'router_front_signal_loss.png')
make_router_front('online').save(BLOCK / 'router_front_online.png')

c = Canvas((26, 32, 37, 255), 814); c.rect(1, 1, 63, 63, (31, 39, 45, 255))
# Rear panel: four Ethernet LAN jacks, one optical input, and 12V barrel jack.
for i in range(4):
    x = 2 + i * 12
    c.rect(x, 5, x + 10, 22, (4, 7, 9, 255)); c.rect(x + 1, 6, x + 9, 17, (18, 24, 27, 255))
    for pin in range(4): c.rect(x + 2 + pin * 1.5, 15, x + 3 + pin * 1.5, 18, (220, 172, 77, 255))
    text(c, x + 1, 24, str(i + 1), (206, 218, 222, 255), 0)
# Optical fibre SFP-style port on the right.
c.rect(51, 5, 62, 23, (5, 7, 10, 255)); c.rect(53, 7, 60, 19, (7, 34, 43, 255)); c.rect(54, 8, 59, 10, (67, 204, 245, 255))
text(c, 51, 25, 'F', (162, 205, 219, 255), 0)
# 12V jack.
c.rect(52, 39, 62, 52, (5, 7, 9, 255)); c.rect(54, 41, 60, 50, (123, 135, 141, 255)); c.rect(56, 43, 58, 48, (8, 12, 15, 255))
text(c, 37, 54, '12V', (195, 207, 213, 255), 1); c.noise(2); c.save(BLOCK / 'router_rear.png')

c = Canvas((69, 79, 86, 255), 815); c.noise(3)
for y in range(5, 60, 7): c.line(3, y, 61, y, (89, 99, 106, 255))
c.rect(2, 2, 62, 62, (65, 75, 82, 255)); c.rect(4, 4, 60, 60, (75, 85, 92, 255)); c.noise(2); c.save(BLOCK / 'router_top.png')

c = Canvas((30, 36, 41, 255), 816); c.rect(0, 0, 64, 64, (27, 34, 39, 255)); c.rect(2, 2, 62, 62, (48, 57, 63, 255))
for i in range(5):
    x = 3 + i * 12
    c.rect(x, 9, x + 10, 35, (6, 10, 13, 255)); c.rect(x + 1, 10, x + 9, 32, (11, 21, 25, 255))
    c.rect(x + 2, 12, x + 8, 15, (29, 99, 125, 255)); c.rect(x + 3, 13, x + 7, 14, (105, 218, 247, 255))
    for pin in range(3): c.rect(x + 2 + pin * 2, 28, x + 3 + pin * 2, 31, (149, 160, 165, 255))
    text(c, x + 2, 39, str(i + 1), (213, 225, 230, 255), 0)
text(c, 8, 50, 'INTERNET', (166, 194, 204, 255), 1); c.noise(1); c.save(BLOCK / 'internet_ports.png')
c = Canvas((60, 70, 77, 255), 817); c.rect(2, 2, 62, 62, (49, 58, 64, 255)); c.line(2, 5, 61, 5, (104, 115, 122, 255)); c.line(2, 59, 61, 59, (25, 31, 36, 255)); c.noise(3); c.save(BLOCK / 'internet_side.png')

c = Canvas((27, 68, 40, 255), 818); c.rect(1, 1, 63, 63, (8, 21, 13, 255)); c.rect(5, 5, 59, 59, (22, 100, 47, 255)); c.rect(9, 9, 55, 55, (68, 240, 120, 255)); c.noise(1); c.save(BLOCK / 'led_green.png')

c = Canvas((0, 0, 0, 0), 819); c.rect(18, 8, 46, 56, (17, 40, 57, 255)); c.rect(21, 7, 43, 13, (39, 120, 177, 255)); c.rect(22, 50, 42, 58, (38, 123, 182, 255)); c.rect(24, 15, 40, 25, (70, 179, 222, 255)); c.line(26, 28, 26, 46, (79, 191, 234, 255)); c.line(38, 28, 38, 46, (79, 191, 234, 255)); c.save(ITEM / 'ethernet_cable.png')
c = Canvas((0, 0, 0, 0), 820); c.rect(18, 8, 46, 56, (226, 226, 221, 255)); c.rect(22, 7, 42, 14, (181, 182, 178, 255)); c.rect(22, 50, 42, 58, (181, 182, 178, 255)); c.line(25, 16, 25, 48, (113, 223, 255, 255)); c.line(32, 16, 32, 48, (74, 182, 235, 255)); c.line(39, 16, 39, 48, (113, 223, 255, 255)); c.save(ITEM / 'fibre_optic_cable.png')

# Handheld Cloud Access Remote: rugged black terminal with a cyan cloud/link icon.
c = Canvas((0, 0, 0, 0), 821)
c.rect(13, 7, 51, 57, (18, 25, 31, 255)); c.rect(16, 9, 48, 55, (52, 65, 74, 255))
c.rect(19, 13, 45, 34, (4, 15, 22, 255)); c.rect(21, 15, 43, 32, (9, 33, 42, 255))
c.rect(24, 19, 40, 20, (92, 220, 235, 255)); c.line(25, 23, 39, 23, (92, 220, 235, 255))
c.line(25, 27, 34, 27, (42, 155, 182, 255)); c.rect(23, 38, 41, 40, (67, 213, 236, 255))
c.rect(23, 43, 41, 45, (42, 119, 145, 255)); c.rect(22, 49, 42, 52, (11, 17, 21, 255))
c.noise(1); c.save(ITEM / 'cloud_access_remote.png')
