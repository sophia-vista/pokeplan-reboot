#!/usr/bin/env python3
"""Render the sound of Pokémon Mystery Dungeon: Explorers of Sky (USA) from the ROM.

The ROM is not in the repo: pass one whose SHA-1 is 5fa96ca8d8dd6405d6cd2bad73ed68bc73a9d152 (pret/pmd-sky's
pmdsky.us.nds). The game's sound is Procyon Studio's DSE: `.swd` banks (wavetables, programs, splits), `.smd`
songs and `.sed` sound-effect files (sequences of tracks). Written from the community's documentation of the
format (PsyCommando's ppmdu `dse` sources), as an independent parser and a small numpy mixer. No SkyTemple code
is used (its library has no DSE support).

This is an approximation of the DS's sound engine, not an emulation: IMA-ADPCM and PCM samples play through a
simple per-note envelope and linear pitch shift; vibrato/LFO, reverb, pan and most rarely used events are ignored.
Listen before trusting a clip.

Usage: python3 tools/sprites/extract_sky_audio.py ROM.nds OUTDIR [--kinds se,me,bgm] [--ogg]
Output: OUTDIR/<kind>/<file>-<track>.wav|ogg, and sounds.tsv (file, track, seconds, preset(s)).
Needs numpy and `pip install ndspy`; --ogg needs ffmpeg. Nintendo / Chunsoft / Procyon audio: private use only.
"""
import argparse
import hashlib
import struct
import subprocess
import sys
import wave
from pathlib import Path

import ndspy.rom
import numpy as np

ROM_SHA1 = "5fa96ca8d8dd6405d6cd2bad73ed68bc73a9d152"
SR = 32000

IMA_STEP = [7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45, 50, 55, 60, 66, 73, 80, 88, 97, 107,
            118, 130, 143, 157, 173, 190, 209, 230, 253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658, 724, 796, 876, 963,
            1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066, 2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358, 5894,
            6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899, 15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767]
IMA_INDEX = [-1, -1, -1, -1, 2, 4, 6, 8]

# DSE envelope times, ppmdu's dse_common.cpp (Duration_Lookup_Table, in ms times the split's multiplier; and, for a multiplier of 0,
# Duration_Lookup_Table_NullMulti in ms)
DUR = [
    0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
    16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31,
    32, 35, 40, 45, 51, 57, 64, 72, 80, 88, 98, 109, 120, 131, 144, 158,
    172, 188, 204, 222, 240, 260, 281, 303, 327, 352, 378, 406, 435, 466, 498, 532,
    568, 606, 645, 686, 729, 775, 822, 871, 923, 977, 1030, 1090, 1150, 1220, 1280, 1350,
    1420, 1570, 1650, 1740, 1820, 1910, 2010, 2100, 2200, 2310, 2410, 2520, 2640, 2750, 2880, 3000,
    3130, 3260, 3400, 3550, 3690, 3840, 4000, 4160, 4330, 4500, 4670, 4850, 5040, 5230, 5430, 5630,
    5840, 6050, 6270, 6490, 6720, 6960, 7200, 7450, 7710, 7970, 8240, 8520, 8800, 9090, 10000, 32767,
]
DUR_NULL = [
    0, 4, 7, 10, 15, 21, 28, 36, 46, 58, 72, 87, 104, 123, 145, 168,
    389, 446, 508, 575, 648, 726, 810, 901, 997, 1100, 1210, 1326, 1449, 1580, 1717, 1862,
    3023, 3264, 3517, 3782, 4060, 4351, 4655, 4972, 5302, 5647, 6005, 6378, 6765, 7167, 7584, 8017,
    11286, 11904, 12544, 13205, 13889, 14594, 15323, 16074, 16848, 17646, 18468, 19315, 20185, 21081, 22002, 22948,
    29900, 31147, 32428, 33742, 35089, 36471, 37887, 39338, 40824, 42346, 43904, 45499, 47130, 48798, 50503, 52247,
    64834, 67019, 69250, 71528, 73854, 76228, 78651, 81122, 83643, 86213, 88834, 91506, 94229, 97003, 99829, 102707,
    123245, 126727, 130272, 133879, 137551, 141286, 145086, 148951, 152882, 156879, 160942, 165072, 169270, 173536, 177870, 182274,
    213424, 218616, 223888, 229241, 234676, 240193, 245793, 251476, 257242, 263093, 269029, 275050, 281157, 287351, 293632, 2147483647,
]
DELAY = [96, 72, 64, 48, 36, 32, 24, 18, 16, 12, 9, 8, 6, 4, 4, 3]  # 0x80..0x8F, in ticks at 48 per quarter note
PARAMS = {0x90: 0, 0x91: 1, 0x92: 1, 0x93: 2, 0x94: 3, 0x95: 0, 0x98: 0, 0x99: 0, 0x9C: 1, 0x9D: 0, 0x9E: 0, 0xA0: 1, 0xA1: 1,
          0xA4: 1, 0xA5: 1, 0xA8: 2, 0xA9: 1, 0xAA: 1, 0xAB: 1, 0xAC: 1, 0xAF: 3, 0xB0: 0, 0xB1: 1, 0xB2: 1, 0xB3: 1, 0xB4: 2,
          0xB5: 1, 0xB6: 1, 0xBC: 1, 0xBE: 1, 0xBF: 1, 0xC0: 0, 0xC3: 1, 0xCB: 2, 0xD0: 1, 0xD1: 1, 0xD2: 1, 0xD3: 2, 0xD4: 3,
          0xD5: 2, 0xD6: 2, 0xD7: 2, 0xD8: 2, 0xDB: 1, 0xDC: 5, 0xDD: 4, 0xDF: 1, 0xE0: 1, 0xE1: 1, 0xE2: 3, 0xE3: 1, 0xE4: 5,
          0xE5: 4, 0xE7: 1, 0xE8: 1, 0xE9: 1, 0xEA: 3, 0xEC: 5, 0xED: 4, 0xEF: 1, 0xF0: 5, 0xF1: 4, 0xF2: 2, 0xF3: 3, 0xF6: 1,
          0xF8: 2}


def u16(d, o):
    return struct.unpack_from("<H", d, o)[0]


def u32(d, o):
    return struct.unpack_from("<I", d, o)[0]


def chunks(d, start):
    """DSE chunks: 16-byte header (label, two params, data length) then the data, 4-byte aligned."""
    o = start
    while o + 16 <= len(d):
        label = bytes(d[o:o + 4])
        length = u32(d, o + 12)
        yield label, o + 16, length
        if label in (b"eod\x20", b"eoc\x20"):
            return
        o += 16 + ((length + 3) & ~3)


def decode_ima(raw):
    pred = struct.unpack_from("<h", raw, 0)[0]
    index = min(raw[2], 88)
    out = np.empty((len(raw) - 4) * 2, dtype=np.int16)
    k = 0
    for b in raw[4:]:
        for nib in (b & 15, b >> 4):
            step = IMA_STEP[index]
            diff = step >> 3
            if nib & 1:
                diff += step >> 2
            if nib & 2:
                diff += step >> 1
            if nib & 4:
                diff += step
            pred = max(-32768, min(32767, pred - diff if nib & 8 else pred + diff))
            index = max(0, min(88, index + IMA_INDEX[nib & 7]))
            out[k] = pred
            k += 1
    return out


class Sample:
    def __init__(self, info, pcmd):
        (self.id, self.ftune, self.ctune, self.rootkey, self.ktps, self.vol, self.pan) = info["head"]
        self.fmt, self.loop, self.rate = info["fmt"], info["loop"], info["rate"]
        pos, loopbeg, looplen = info["pos"], info["loopbeg"], info["looplen"]
        self.env = info["env"]
        nbytes = (loopbeg + looplen) * 4
        raw = bytes(pcmd[pos:pos + nbytes])
        if self.fmt == 0x200:  # IMA ADPCM, a 4-byte preamble then nibbles
            self.pcm = decode_ima(raw).astype(np.float32) / 32768.0
            self.loopstart = max(0, (loopbeg * 4 - 4) * 2)
        elif self.fmt == 0x100:  # PCM16
            self.pcm = np.frombuffer(raw[:len(raw) // 2 * 2], dtype="<i2").astype(np.float32) / 32768.0
            self.loopstart = loopbeg * 2
        elif self.fmt == 0x000:  # PCM8
            self.pcm = np.frombuffer(raw, dtype=np.int8).astype(np.float32) / 128.0
            self.loopstart = loopbeg * 4
        else:
            self.pcm = np.zeros(2, dtype=np.float32)
            self.loopstart = 0


class Bank:
    """A .swd: samples (wavi + pcmd) and programs (prgi) with their key/velocity splits."""

    def __init__(self, data, main=None):
        self.d = data
        self.main = main
        nwavi, nprg = u16(data, 0x46), u16(data, 0x48)
        self.pcmd = None
        self.wavi = {}
        self.programs = {}
        self.name = bytes(data[0x20:0x30]).split(b"\0")[0].decode("latin1")
        for label, o, length in chunks(data, 0x50):
            if label == b"wavi":
                for i in range(nwavi):
                    off = u16(data, o + 2 * i)
                    if off == 0:
                        continue
                    e = o + off
                    sid = u16(data, e + 2)
                    ftune, ctune, root, ktps, vol, pan = struct.unpack_from("<bbBbbb", data, e + 4)
                    fmt, loop = u16(data, e + 0x12), data[e + 0x15]
                    rate, pos, loopbeg, looplen = struct.unpack_from("<IIII", data, e + 0x20)
                    envon, envmult = data[e + 0x30], data[e + 0x31]
                    atkvol, attack, hold, decay, sustain, decay2, release = struct.unpack_from("<bbbbbbb", data, e + 0x34)
                    self.wavi[sid] = dict(head=(sid, ftune, ctune, root, ktps, vol, pan), fmt=fmt, loop=bool(loop), rate=rate,
                                          pos=pos, loopbeg=loopbeg, looplen=looplen,
                                          env=(envon, envmult, atkvol, attack, hold, decay, sustain, decay2, release))
            elif label == b"pcmd":
                self.pcmd = data[o:o + length]
            elif label == b"prgi":
                for i in range(nprg):
                    off = u16(data, o + 2 * i)
                    if off == 0:
                        continue
                    e = o + off
                    pid, nsplits, pvol, ppan = u16(data, e), u16(data, e + 2), data[e + 4], data[e + 5]
                    nlfo = data[e + 11]
                    s = e + 16 + 16 * nlfo + 16
                    splits = []
                    for k in range(nsplits):
                        p = s + 48 * k
                        lowkey, hikey, lovel, hivel = struct.unpack_from("<bbbb", data, p + 4)[0], struct.unpack_from("<b", data, p + 5)[0], data[p + 8], data[p + 9]
                        smplid = u16(data, p + 0x12)
                        ftune, ctune, root, ktps, svol, span = struct.unpack_from("<bbbbbb", data, p + 0x14)
                        envon, envmult = data[p + 0x20], data[p + 0x21]
                        atkvol, attack, decay, sustain, hold, decay2, release = struct.unpack_from("<bbbbbbb", data, p + 0x28)
                        splits.append(dict(lowkey=lowkey, hikey=hikey, lovel=lovel, hivel=hivel, smplid=smplid, ctune=ctune,
                                           root=root, ktps=ktps, vol=svol, pan=span, env=(envon, envmult, atkvol, attack, hold, decay, sustain, decay2, release)))
                    self.programs[pid] = dict(vol=pvol, pan=ppan, splits=splits)
        self.samples = {}

    def sample(self, sid):
        if sid in self.samples:
            return self.samples[sid]
        bank = self if (sid in self.wavi and self.pcmd) else self.main
        s = None
        if bank is not None and sid in bank.wavi and bank.pcmd is not None:
            s = Sample(bank.wavi[sid], bank.pcmd)
        self.samples[sid] = s
        return s

    def split_for(self, program, key, vel):
        prg = self.programs.get(program)
        if prg is None and self.main is not None:
            prg = self.main.programs.get(program)
        if not prg:
            return None, None
        for s in prg["splits"]:
            if s["lowkey"] <= key <= s["hikey"] and s["lovel"] <= vel <= s["hivel"]:
                return prg, s
        return prg, (prg["splits"][0] if prg["splits"] else None)


def env_ms(param, mult):
    p = max(0, min(127, abs(param)))
    return min(DUR_NULL[p], 30000) if mult == 0 else DUR[p] * mult


def render_note(bank, program, key, vel, n_on, volume):
    prg, split = bank.split_for(program, key, vel)
    if split is None:
        return None
    smp = bank.sample(split["smplid"])
    if smp is None or len(smp.pcm) < 2:
        return None
    semis = key + split["ktps"] - split["root"] + split["ctune"] / 100.0 * 0 if split["root"] else 0
    ratio = 2.0 ** (semis / 12.0)
    n_total = n_on + int(SR * 1.5)
    pos = np.arange(n_total, dtype=np.float64) * (smp.rate * ratio / SR)
    size = len(smp.pcm)
    if smp.loop and smp.loopstart < size - 1:
        pos = np.where(pos >= size, smp.loopstart + (pos - smp.loopstart) % (size - smp.loopstart), pos)
        alive = np.ones(n_total, dtype=bool)
    else:
        alive = pos < size - 1
    pos = np.minimum(pos, size - 2)
    i = pos.astype(np.int64)
    f = (pos - i).astype(np.float32)
    wav = np.where(alive, smp.pcm[i] * (1 - f) + smp.pcm[i + 1] * f, 0).astype(np.float32)
    envon, mult, atkvol, attack, hold, decay, sustain, decay2, release = split["env"]
    t = np.arange(n_total) / SR
    if envon:
        a = max(env_ms(attack, mult) / 1000.0, 0.002)
        h = env_ms(hold, mult) / 1000.0
        d = max(env_ms(decay, mult) / 1000.0, 0.01)
        sus = max(0.0, min(1.0, sustain / 127.0))
        env = np.where(t < a, t / a, 1.0)
        td = np.clip((t - a - h) / d, 0, 1)
        env = np.where(t >= a + h, 1.0 - (1.0 - sus) * td, env)
        rel = max(env_ms(release, mult) / 1000.0, 0.01)
    else:
        env = np.ones(n_total, dtype=np.float32)
        rel = 0.05
    off = n_on / SR
    gate = np.where(t < off, 1.0, np.clip(1.0 - (t - off) / rel, 0, 1))
    gain = volume * (vel / 127.0) * (split["vol"] / 127.0) * (prg["vol"] / 127.0) * (smp.vol / 127.0 if smp.vol > 0 else 1.0)
    return (wav * env * gate * gain).astype(np.float32)


def parse_track(data, start, length):
    """The events of a trk chunk: (type, ...)"""
    o = start + 4  # trkid, chanid, two unknowns
    end = start + length
    events = []
    lastpause = 0
    octave = 4
    while o < end:
        c = data[o]
        o += 1
        if c <= 0x7F:
            p = data[o]
            o += 1
            nbytes = (p >> 6) & 3
            octave += ((p >> 4) & 3) - 2
            k = p & 15
            dur = int.from_bytes(bytes(data[o:o + nbytes]), "big") if nbytes else 0
            o += nbytes
            if k < 12:
                events.append(("note", c, octave * 12 + k, dur if nbytes else None))
        elif c <= 0x8F:
            lastpause = DELAY[c - 0x80]
            events.append(("pause", lastpause))
        elif c == 0x90:
            events.append(("pause", lastpause))
        elif c == 0x91:
            lastpause += data[o]
            o += 1
            events.append(("pause", lastpause))
        elif c == 0x92:
            lastpause = data[o]
            o += 1
            events.append(("pause", lastpause))
        elif c == 0x93:
            lastpause = u16(data, o)
            o += 2
            events.append(("pause", lastpause))
        elif c == 0x94:
            lastpause = int.from_bytes(bytes(data[o:o + 3]), "little")
            o += 3
            events.append(("pause", lastpause))
        elif c == 0x98:
            events.append(("end",))
            break
        elif c == 0x99:
            events.append(("loop",))
        elif c == 0xA0:
            octave = data[o]
            o += 1
        elif c == 0xA4:
            events.append(("tempo", data[o]))
            o += 1
        elif c == 0xAC:
            events.append(("program", data[o]))
            o += 1
        elif c == 0xE0:
            events.append(("vol", data[o]))
            o += 1
        elif c == 0xE3:
            events.append(("expr", data[o]))
            o += 1
        elif c == 0xD7:
            o += 2
        else:
            o += PARAMS.get(c, 0)
    return events


def render_track(bank, events, tpqn, max_seconds, one_loop=True):
    """Mix a single track of events to a float array."""
    tempo = 120.0
    t = 0.0  # seconds
    program, vol, expr = 0, 100, 127
    notes = []
    for ev in events:
        if ev[0] == "tempo":
            tempo = max(1, ev[1])
        elif ev[0] == "program":
            program = ev[1]
        elif ev[0] == "vol":
            vol = ev[1]
        elif ev[0] == "expr":
            expr = ev[1]
        elif ev[0] == "pause":
            t += ev[1] * 60.0 / (tempo * tpqn)
        elif ev[0] == "note":
            _, vel, key, dur = ev
            seconds = (dur if dur else tpqn) * 60.0 / (tempo * tpqn)
            notes.append((t, seconds, program, key, vel, vol / 127.0 * expr / 127.0))
        elif ev[0] == "loop" and one_loop:
            pass
        if t > max_seconds:
            break
    return notes


def mix(bank, tracks, tpqn, max_seconds):
    allnotes = []
    for events in tracks:
        allnotes += render_track(bank, events, tpqn, max_seconds)
    if not allnotes:
        return np.zeros(1, dtype=np.float32)
    end = min(max_seconds, max(n[0] + n[1] for n in allnotes)) + 1.6
    out = np.zeros(int(end * SR) + SR, dtype=np.float32)
    cache = {}
    for start, dur, program, key, vel, volume in allnotes:
        if start > max_seconds:
            continue
        n_on = max(1, int(dur * SR))
        ck = (program, key, vel, n_on, round(volume, 2))
        if ck not in cache:
            cache[ck] = render_note(bank, program, key, vel, n_on, volume)
        w = cache[ck]
        if w is None:
            continue
        s = int(start * SR)
        e = min(len(out), s + len(w))
        out[s:e] += w[:e - s]
    last = np.nonzero(np.abs(out) > 1e-4)[0]
    return out[:last[-1] + 1] if len(last) else out[:1]


def write_wav(path, audio, gain=0.5):
    peak = float(np.abs(audio).max()) or 1.0
    pcm = np.clip(audio * min(gain, 0.98 / peak), -1, 1)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((pcm * 32767).astype("<i2").tobytes())


def trk_chunks(data, start, end):
    """The `trk ` chunks between two offsets: (events offset, length)."""
    o = start
    out = []
    while True:
        i = bytes(data[o:end]).find(b"trk\x20")
        if i < 0:
            return out
        o += i
        out.append((o + 16, u32(data, o + 12)))
        o += 16 + u32(data, o + 12)


def parse_sequence(data):
    """[(tpqn, [track events])] of a .smd (one song) or a .sed (one sound effect per entry of its table)."""
    songs = []
    if bytes(data[:4]) == b"sedl":
        base = 0x50
        offsets = []
        for i in range(1, 256):  # the table ends at its first zero
            o = u16(data, base + 2 * i)
            if o == 0:
                break
            offsets.append(o)
        for k, off in enumerate(offsets):
            end = base + offsets[k + 1] if k + 1 < len(offsets) else len(data)
            start = base + off
            songs.append((u16(data, start + 2) or 48, [parse_track(data, s, n) for s, n in trk_chunks(data, start, end)]))
    else:
        songs.append((u16(data, 0x52) or 48, [parse_track(data, s, n) for s, n in trk_chunks(data, 0x40, len(data))]))
    return songs


def main():
    p = argparse.ArgumentParser()
    p.add_argument("rom")
    p.add_argument("out")
    p.add_argument("--kinds", default="se,me")
    p.add_argument("--max-seconds", type=float, default=60.0)
    p.add_argument("--ogg", action="store_true")
    p.add_argument("--only")
    args = p.parse_args()
    data = Path(args.rom).read_bytes()
    if hashlib.sha1(data).hexdigest() != ROM_SHA1:
        sys.exit("Not the Explorers of Sky (USA) ROM (SHA-1 mismatch).")
    rom = ndspy.rom.NintendoDSRom(data)
    get = lambda name: bytes(rom.getFileByName(name))
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    rows = []
    main_bgm = Bank(get("SOUND/BGM/bgm.swd"))
    jobs = []
    kinds = args.kinds.split(",")
    if "se" in kinds:
        jobs.append(("se", "SOUND/SYSTEM/se_sys.sed", "SOUND/SYSTEM/se_sys.swd", main_bgm))
    if "me" in kinds:
        me = Bank(get("SOUND/ME/me.swd"))
        for i in range(30):
            nm = f"SOUND/ME/me{i:04d}.smd"
            try:
                get(nm)
            except Exception:
                continue
            jobs.append(("me", nm, None, me))
    if "bgm" in kinds:
        for i in range(400):
            nm = f"SOUND/BGM/bgm{i:04d}.smd"
            try:
                get(nm)
            except Exception:
                continue
            jobs.append(("bgm", nm, f"SOUND/BGM/bgm{i:04d}.swd", main_bgm))
    for kind, seq, swd, main in jobs:
        if args.only and args.only not in seq:
            continue
        try:
            bank = Bank(get(swd), main) if swd else main
            base = Path(seq).stem
            (out / kind).mkdir(exist_ok=True)
            units = parse_sequence(get(seq))  # a sound-effect file holds many sounds, a song file one
            for idx, (tpqn, trk) in enumerate(units):
                audio = mix(bank, trk, tpqn, args.max_seconds)
                if np.abs(audio).max() < 1e-4:
                    continue
                name = f"{base}-{idx:02d}" if len(units) > 1 else base
                wav = out / kind / f"{name}.wav"
                write_wav(wav, audio)
                if args.ogg:
                    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "2", str(wav.with_suffix(".ogg"))], check=True)
                    wav.unlink()
                rows.append((kind, name, f"{len(audio) / SR:.2f}", len(trk)))
                print(kind, name, f"{len(audio) / SR:.2f}s", flush=True)
        except Exception as e:
            print("SKIP", seq, repr(e)[:120], file=sys.stderr)
    with open(out / "sounds.tsv", "w", encoding="utf-8") as f:
        f.write("kind\tname\tseconds\ttracks\n")
        for r in rows:
            f.write("\t".join(map(str, r)) + "\n")
    print(len(rows), "sounds")


if __name__ == "__main__":
    main()
