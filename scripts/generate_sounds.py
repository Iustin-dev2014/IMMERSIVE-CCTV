#!/usr/bin/env python3
"""Generate 200 custom synthetic CCTV sound variations (requires ffmpeg/libvorbis)."""
from pathlib import Path
import math, random, struct, subprocess, tempfile, concurrent.futures

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/immersivecctv'
OUT = ROOT / 'sounds'
OUT.mkdir(parents=True, exist_ok=True)
RATE = 22050
CORE = [
    'xvr_power_on','xvr_power_off','ac_adapter_connect','mains_power_connect',
    'hdmi_connect','poe_connect','camera_restart','camera_enable','camera_disable',
    'monitor_power_on','boot_ready','boot_fail','no_signal','alarm_on','alarm_off','button_click'
]
NAMES = CORE + [f'startup_beep_{i:02d}' for i in range(1,49)] + [f'camera_link_{i:02d}' for i in range(1,49)] + [f'drive_seek_{i:02d}' for i in range(1,45)] + [f'relay_click_{i:02d}' for i in range(1,45)]
assert len(NAMES) == 200

def build_samples(name):
    seed = sum((i+1)*ord(c) for i,c in enumerate(name))
    rng = random.Random(seed)
    if name.startswith('startup_beep'):
        duration = 0.24 + rng.random()*0.16; f0 = 580 + rng.randrange(0,600); f1 = f0 * (0.78 + rng.random()*0.55); kind='beep'
    elif name.startswith('camera_link'):
        duration = 0.32 + rng.random()*0.12; f0 = 720 + rng.randrange(0,700); f1 = f0*1.34; kind='link'
    elif name.startswith('drive_seek'):
        duration = 0.45 + rng.random()*0.25; f0 = 95+rng.randrange(0,95); f1=f0*(1.6+rng.random()); kind='drive'
    elif name.startswith('relay_click'):
        duration=0.09+rng.random()*0.08; f0=400+rng.randrange(0,1500); f1=f0*0.6; kind='click'
    else:
        presets={
            'xvr_power_on':('powerup',110,480,0.78),'xvr_power_off':('powerdown',440,105,0.55),
            'ac_adapter_connect':('click',700,330,0.18),'mains_power_connect':('link',620,1080,0.35),
            'hdmi_connect':('link',800,1400,0.27),'poe_connect':('link',690,1230,0.32),
            'camera_restart':('powerdown',650,220,0.5),'camera_enable':('beep',650,980,0.28),
            'camera_disable':('powerdown',520,180,0.32),'monitor_power_on':('powerup',150,570,0.45),
            'boot_ready':('beep',900,1400,0.26),'boot_fail':('powerdown',410,190,0.42),
            'no_signal':('beep',240,180,0.28),'alarm_on':('alarm',720,1020,0.75),
            'alarm_off':('powerdown',500,240,0.22),'button_click':('click',1100,430,0.08)}
        kind,f0,f1,duration=presets[name]
    n=max(1,int(RATE*duration)); samples=[]
    phase=0.0
    prev_noise=0.0
    for i in range(n):
        t=i/RATE; p=i/(n-1 if n>1 else 1)
        attack=min(1.0,p*18+0.02); release=min(1.0,(1-p)*13+0.02); env=max(0.0,min(attack,release))
        freq=f0+(f1-f0)*(p if kind in ('powerup','powerdown','drive','link','alarm','beep') else p*p)
        phase += 2*math.pi*freq/RATE
        tone=math.sin(phase)
        tone += 0.28*math.sin(phase*2.03+0.3) + 0.09*math.sin(phase*3.7)
        noise=rng.uniform(-1,1)
        # A little filtered noise gives clicks and HDD mechanics texture rather than a pure oscillator.
        prev_noise=prev_noise*0.72+noise*0.28
        if kind=='click':
            value=(tone*0.30 + noise*(1-p)*0.9 + math.sin(phase*0.47)*(1-p)*0.18)*env
        elif kind=='drive':
            pulse=1.0 if (int(t*9 + seed%5)%5) < 2 else 0.42
            value=(tone*0.38 + prev_noise*0.26 + math.sin(phase*0.19)*0.32)*env*pulse
        elif kind=='alarm':
            pulse=1.0 if (t*4)%1 < 0.48 else 0.20
            value=(tone*0.54+prev_noise*0.035)*env*pulse
        elif kind=='link':
            pulse=1.0 if p<0.44 or p>0.60 else 0.0
            value=(tone*0.52+prev_noise*0.025)*env*pulse
        else:
            value=(tone*0.55+prev_noise*0.07)*env
        # tiny deterministic ambience for synthetic electronic-mechanical detail
        value=max(-0.98,min(0.98,value*0.72))
        samples.append(int(value*32767))
    return samples

def make_wav(name, path):
    samples=build_samples(name)
    with open(path,'wb') as f:
        f.write(b'RIFF'); f.write(struct.pack('<I',36+len(samples)*2)); f.write(b'WAVEfmt ')
        f.write(struct.pack('<IHHIIHH',16,1,1,RATE,RATE*2,2,16)); f.write(b'data'); f.write(struct.pack('<I',len(samples)*2))
        f.write(struct.pack('<'+'h'*len(samples),*samples))

def convert(args):
    name,wav_path=args; out=OUT/(name+'.ogg')
    subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(wav_path),'-c:a','libvorbis','-q:a','2',str(out)],check=True)
    return out.stat().st_size

with tempfile.TemporaryDirectory(prefix='cctv-sounds-') as tmp:
    tmp=Path(tmp); wavs=[]
    for name in NAMES:
        wav=tmp/(name+'.wav'); make_wav(name,wav); wavs.append((name,wav))
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        sizes=list(pool.map(convert,wavs))
print(f'Generated {len(NAMES)} custom synthetic CCTV sound effects ({sum(sizes)//1024} KiB OGG).')
